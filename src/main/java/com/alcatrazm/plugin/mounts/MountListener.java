package com.alcatrazm.plugin.mounts;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class MountListener implements Listener {

    private final MountManager manager;

    public MountListener(MountManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 1. Si el atacante es una montura
        if (event.getDamager().hasMetadata(ActiveMount.METADATA_TAG)) {
            // No dañar nunca a su propio dueño
            if (event.getEntity() instanceof Player victim) {
                ActiveMount mount = manager.getActiveMount(victim.getUniqueId());
                if (mount != null && mount.getEntity() != null && mount.getEntity().equals(event.getDamager())) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Respetar protecciones (/ps) y anti-grief
            ActiveMount sourceMount = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                ActiveMount am = manager.getActiveMount(p.getUniqueId());
                if (am != null && am.getEntity() != null && am.getEntity().equals(event.getDamager())) {
                    sourceMount = am;
                    break;
                }
            }

            if (sourceMount != null) {
                boolean isPvP = event.getEntity() instanceof Player;
                boolean isAnimal = event.getEntity() instanceof Animals || event.getEntity() instanceof Tameable;
                if (!sourceMount.isAttackAllowedAt(event.getEntity().getLocation(), isPvP, isAnimal)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // 2. Si un jugador es atacado por otro jugador o proyectil, activar la represalia del MiniDragón
        if (event.getEntity() instanceof Player victim) {
            ActiveMount mount = manager.getActiveMount(victim.getUniqueId());
            if (mount != null) {
                Player attacker = null;
                if (event.getDamager() instanceof Player p) {
                    attacker = p;
                } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                    attacker = p;
                }

                if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
                    mount.setRetaliationTarget(attacker);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();

        // Inmunidad total para las entidades montura
        if (entity.hasMetadata(ActiveMount.METADATA_TAG)) {
            event.setCancelled(true);
            return;
        }

        // Inmunidad a daño de caída para jugadores montados o que desmontaron recientemente
        if (entity instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                if (manager.hasFallProtection(player.getUniqueId())) {
                    event.setCancelled(true);
                } else if (player.getVehicle() != null && player.getVehicle().hasMetadata(ActiveMount.METADATA_TAG)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (event.getEntity().hasMetadata(ActiveMount.METADATA_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandTrample(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block != null && block.getType() == Material.FARMLAND) {
            if (event.getEntity().hasMetadata(ActiveMount.METADATA_TAG)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.FARMLAND) {
                Player player = event.getPlayer();
                if (player.getVehicle() != null && player.getVehicle().hasMetadata(ActiveMount.METADATA_TAG)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!clicked.hasMetadata(ActiveMount.METADATA_TAG)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        ActiveMount mount = manager.getActiveMount(player.getUniqueId());

        if (mount != null && clicked.equals(mount.getEntity())) {
            if (!mount.getType().isRideable()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&bMascotas&8] &b" + mount.getType().getDisplayName() + " &7es un hada protectora que te sigue y bendice. &e¡No se puede montar!"));
                return;
            }

            if (mount.getType() == MountType.DOLPHIN) {
                if (!clicked.getLocation().getBlock().isLiquid() && !player.getEyeLocation().getBlock().isLiquid()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&6Monturas&8] &c¡El delfín solo puede montarse dentro del agua!"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }

            clicked.addPassenger(player);
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_ARMOR, 1.0f, 1.2f);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Monturas&8] &a¡Te has subido a tu &e" + mount.getType().getDisplayName() + "&a!"));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Monturas&8] &cEsta criatura le pertenece a otro jugador."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && event.getDismounted().hasMetadata(ActiveMount.METADATA_TAG)) {
            manager.addFallProtection(player.getUniqueId(), manager.getFallProtectionSeconds());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&6Monturas&8] &eTe has bajado de tu montura. &7(Click derecho para volver a montar)"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.dismissMount(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        manager.dismissMount(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getWorld() != null && event.getTo() != null && !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            manager.dismissMount(player.getUniqueId(), false);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MountGUI.GUI_TITLE)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        MountType selectedType = switch (slot) {
            case 10 -> MountType.FOX;
            case 11 -> MountType.RABBIT;
            case 12 -> MountType.ARMADILLO;
            case 13 -> MountType.CAT;
            case 14 -> MountType.POLAR_BEAR;
            case 15 -> MountType.PANDA;
            case 16 -> MountType.SNIFFER;
            case 22 -> MountType.ALLAY;
            case 28 -> MountType.PHANTOM;
            case 29 -> MountType.MINIDRAGON;
            case 30 -> MountType.BAT;
            case 31 -> MountType.BEE;
            case 32 -> MountType.SALMON;
            case 33 -> MountType.DOLPHIN;
            case 34 -> MountType.AXOLOTL;
            default -> null;
        };

        if (selectedType != null) {
            if (!manager.canUseMount(player, selectedType)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&6Monturas&8] &cNo tienes desbloqueada la montura &e" + selectedType.getDisplayName() + "&c."));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7¡Adquiérela en la tienda del servidor! Permiso requerido: &e" + manager.getMountPermission(selectedType)));
                return;
            }
            manager.spawnMount(player, selectedType);
            player.closeInventory();
            return;
        }

        if (slot == 49) {
            manager.dismissMount(player.getUniqueId(), true);
            player.closeInventory();
        } else if (slot == 4 || slot == 47 || slot == 51) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEnderDragonChangePhase(org.bukkit.event.entity.EnderDragonChangePhaseEvent event) {
        if (event.getEntity().hasMetadata(ActiveMount.METADATA_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.getEntity().hasMetadata(ActiveMount.METADATA_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (event.getEntity().hasMetadata(ActiveMount.METADATA_TAG)) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }
}
