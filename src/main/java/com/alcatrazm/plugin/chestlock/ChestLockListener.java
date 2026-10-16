package com.alcatrazm.plugin.chestlock;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ChestLockListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final ChestLockManager lockManager;

    public ChestLockListener(AlcatrazPlugin plugin, ChestLockManager lockManager) {
        this.plugin = plugin;
        this.lockManager = lockManager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(message)));
        } catch (Throwable ignored) {
            player.sendMessage(color(message));
        }
    }

    private boolean isSignItem(Material mat) {
        String name = mat.name();
        return name.contains("SIGN");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!lockManager.isEnabled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();

            // 1. Bloqueo al agacharse con un cartel en la mano (Shift + Clic)
            if (player.isSneaking() && isSignItem(item.getType())) {
                if (lockManager.isLockable(block)) {
                    LockedBlock existingLock = lockManager.getLock(block);
                    if (existingLock != null) {
                        if (!existingLock.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.lock.admin")) {
                            event.setCancelled(true);
                            sendActionBar(player, "&8[&c🔒 Bloqueado&8] &cEste bloque ya pertenece a &e" + existingLock.getOwnerName() + "&c.");
                            player.playSound(block.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
                            return;
                        }
                    }

                    // A) Si es una PUERTA / TRAMPILLA / PUERTA DE VALLA -> Bloqueo con Cartel Invisible
                    if (lockManager.isDoor(block)) {
                        event.setCancelled(true);

                        if (existingLock == null) {
                            lockManager.lockBlock(block, player, LockType.PRIVATE, 3, true);

                            if (player.getGameMode() != GameMode.CREATIVE) {
                                item.setAmount(item.getAmount() - 1);
                            }

                            player.playSound(block.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);
                            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1.0, 0.5), 10, 0.3, 0.3, 0.3, 0.05);

                            player.sendMessage(color("&8&m----------------------------------------"));
                            player.sendMessage(color("&a&l¡PUERTA PRIVADA ACTIVADA!"));
                            player.sendMessage(color("&7Dueño: &e" + player.getName() + " &8| &7Tipo: &bPrivado"));
                            player.sendMessage(color("&7Auto-Cierre: &a3 segundos tras abrirse."));
                            player.sendMessage(color("&a✓ Cartel invisible integrado en la puerta (sin estorbar la estética)."));
                            player.sendMessage(color("&7Usa &e/lock add <amigo> &7o &e/lock timer <seg> &7para configurar."));
                            player.sendMessage(color("&8&m----------------------------------------"));
                            return;
                        }
                    }

                    // B) Si es un CONTENEDOR (Cofre, Barril, Horno, etc.)
                    if (existingLock == null) {
                        lockManager.lockBlock(block, player, LockType.PRIVATE, 0, false);

                        player.playSound(block.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);
                        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1.0, 0.5), 10, 0.3, 0.3, 0.3, 0.05);

                        player.sendMessage(color("&8&m----------------------------------------"));
                        player.sendMessage(color("&a&l¡CONTENEDOR PRIVADO BLOQUEADO!"));
                        player.sendMessage(color("&7Dueño: &e" + player.getName() + " &8| &7Tipo: &b[Privado]"));
                        player.sendMessage(color("&7Usa &e/lock add <amigo> &7para dar acceso a tus compañeros."));
                        player.sendMessage(color("&8&m----------------------------------------"));
                    }
                    return;
                }
            }

            // 2. Interacción normal (abrir cofre / abrir puerta)
            LockedBlock lock = lockManager.getLock(block);
            if (lock != null) {
                if (!lockManager.canAccess(block, player)) {
                    event.setCancelled(true);
                    sendActionBar(player, "&8[&c🔒 Bloqueado&8] &cEste bloque privado pertenece a &e" + lock.getOwnerName() + "&c.");
                    player.playSound(block.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
                    return;
                }

                // Si tiene acceso y es una puerta con timer -> Abrir y programar auto-cierre
                if (lockManager.isDoor(block)) {
                    event.setCancelled(true);
                    lockManager.toggleDoorWithTimer(block, player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!lockManager.isEnabled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();

        String line0 = event.getLine(0);
        if (line0 == null) return;

        if (line0.equalsIgnoreCase("[Privado]") || line0.equalsIgnoreCase("[Private]") 
                || line0.equalsIgnoreCase("[Priv]") || line0.equalsIgnoreCase("[Lock]")) {
            
            Block attached = getAttachedBlock(block);
            if (attached != null && lockManager.isLockable(attached)) {
                LockedBlock existing = lockManager.getLock(attached);
                if (existing != null && !existing.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.lock.admin")) {
                    event.setCancelled(true);
                    player.sendMessage(color("&cNo puedes proteger un bloque que ya pertenece a &e" + existing.getOwnerName() + "&c."));
                    return;
                }

                LockedBlock lock = lockManager.lockBlock(attached, player, LockType.PRIVATE, 0, false);
                event.setLine(0, color("&1[Privado]"));
                event.setLine(1, color("&0" + player.getName()));

                String line2 = event.getLine(2);
                if (line2 != null && !line2.trim().isEmpty()) {
                    lock.addTrust(line2.trim());
                    event.setLine(2, color("&0" + line2.trim()));
                }

                String line3 = event.getLine(3);
                if (line3 != null && !line3.trim().isEmpty()) {
                    lock.addTrust(line3.trim());
                    event.setLine(3, color("&0" + line3.trim()));
                }
                lockManager.saveLocks();

                player.playSound(block.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);
                player.sendMessage(color("&8[&a🔒 Privado&8] &a¡Contenedor protegido con cartel exitosamente!"));
            }
        }
    }

    private Block getAttachedBlock(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        if (data instanceof WallSign wallSign) {
            return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        }
        return signBlock.getRelative(BlockFace.DOWN);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!lockManager.isEnabled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();

        LockedBlock lock = lockManager.getLock(block);
        if (lock != null) {
            if (!lockManager.canBreak(block, player)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&c🔒 Bloqueado&8] &cNo puedes romper este bloque privado de &e" + lock.getOwnerName() + "&c."));
                return;
            }

            lockManager.unlockBlock(block);
            player.playSound(block.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
            player.sendMessage(color("&8[&a🔒 Candado&8] &aHas retirado la protección privada de este bloque."));
            return;
        }

        // Proteger bloque de soporte inferior de puertas o soporte de carteles bloqueados
        if (isProtectedSupportingBlock(block)) {
            Block above = block.getRelative(BlockFace.UP);
            LockedBlock supLock = lockManager.getLock(above);
            if (supLock != null && !lockManager.canBreak(above, player)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&c🔒 Bloqueado&8] &cNo puedes romper el bloque de soporte de un acceso privado de &e" + supLock.getOwnerName() + "&c."));
                return;
            }
        }
    }

    private boolean isProtectedSupportingBlock(Block block) {
        // Soporte inferior de puertas privadas
        Block above = block.getRelative(BlockFace.UP);
        if (lockManager.isDoor(above) && lockManager.getLock(above) != null) {
            return true;
        }

        // Soporte de carteles adosados (WallSign)
        BlockFace[] cardinal = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : cardinal) {
            Block adj = block.getRelative(face);
            if (adj.getBlockData() instanceof WallSign wallSign && wallSign.getFacing() == face) {
                if (lockManager.getLock(adj) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!lockManager.isEnabled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        // Anti-Cofre Trampa: Evitar conectar un cofre a un cofre privado ajeno
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (BlockFace face : faces) {
                Block neighbor = block.getRelative(face);
                if (neighbor.getType() == block.getType()) {
                    LockedBlock neighborLock = lockManager.getLock(neighbor);
                    if (neighborLock != null && !neighborLock.isOwner(player.getUniqueId()) && !player.hasPermission("alcatraz.lock.admin")) {
                        event.setCancelled(true);
                        player.sendMessage(color("&8[&c🔒 Bloqueado&8] &cNo puedes colocar un cofre junto al cofre privado de &e" + neighborLock.getOwnerName() + "&c."));
                        return;
                    }
                }
            }
        }
    }

    // ==========================================================
    // ANTI-EXTRACCIÓN CON TOLVAS (Anti-Hopper Steal)
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!lockManager.isEnabled()) return;

        Block sourceBlock = resolveHolderBlock(event.getSource().getHolder());
        if (sourceBlock == null) return;

        LockedBlock lock = lockManager.getLock(sourceBlock);
        if (lock == null) return;

        org.bukkit.inventory.InventoryHolder destHolder = event.getDestination().getHolder();

        // Vagonetas con tolva u otros extractores sin bloque: nunca pueden vaciar un contenedor privado
        if (destHolder instanceof org.bukkit.entity.Entity) {
            event.setCancelled(true);
            return;
        }

        Block destBlock = resolveHolderBlock(destHolder);
        if (destBlock == null) {
            event.setCancelled(true);
            return;
        }

        // Solo la extracción por tolvas es un riesgo de robo; empujar hacia otros contenedores es legítimo
        if (destBlock.getType() != Material.HOPPER) return;

        // La tolva extractora debe pertenecer al mismo dueño
        LockedBlock destLock = lockManager.getLock(destBlock);
        if (destLock == null || !destLock.getOwnerUUID().equals(lock.getOwnerUUID())) {
            event.setCancelled(true);
        }
    }

    /**
     * Obtiene el bloque real de un holder de inventario (incluye cofres dobles).
     */
    private Block resolveHolderBlock(org.bukkit.inventory.InventoryHolder holder) {
        if (holder instanceof org.bukkit.block.Container container) {
            return container.getBlock();
        }
        if (holder instanceof org.bukkit.block.DoubleChest doubleChest) {
            org.bukkit.inventory.InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof org.bukkit.block.Container c) {
                return c.getBlock();
            }
            return doubleChest.getLocation().getBlock();
        }
        return null;
    }

    // ==========================================================
    // ANTI-EXPLOSIÓN
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!lockManager.isEnabled()) return;
        event.blockList().removeIf(b -> lockManager.getLock(b) != null || isProtectedSupportingBlock(b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!lockManager.isEnabled()) return;
        event.blockList().removeIf(b -> lockManager.getLock(b) != null || isProtectedSupportingBlock(b));
    }

    // ==========================================================
    // ANTI-PISTONES
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!lockManager.isEnabled()) return;
        for (Block b : event.getBlocks()) {
            if (lockManager.getLock(b) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!lockManager.isEnabled()) return;
        for (Block b : event.getBlocks()) {
            if (lockManager.getLock(b) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
