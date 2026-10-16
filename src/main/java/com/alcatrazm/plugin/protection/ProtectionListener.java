package com.alcatrazm.plugin.protection;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class ProtectionListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final ProtectionManager protectionManager;
    private final Map<UUID, String> playerCurrentClaim = new HashMap<>();
    private final Set<Location> recentlyRemovedProtectionBlocks = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long> fallDamageImmunity = new java.util.concurrent.ConcurrentHashMap<>();

    public ProtectionListener(AlcatrazPlugin plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private boolean hasBypass(Player player) {
        return player != null && (player.hasPermission("alcatraz.claim.bypass") || player.hasPermission("alcatraz.admin"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!protectionManager.isEnabled()) return;
        Player player = event.getPlayer();
        ItemStack itemInHand = event.getItemInHand();

        if (protectionManager.isProtectionBlockItem(itemInHand)) {
            ProtectionBlockType blockType = protectionManager.getBlockTypeOfItem(itemInHand);
            if (blockType == null) return;
            boolean temporary = protectionManager.isTemporaryItem(itemInHand);

            // Verificar límite de claims
            if (!hasBypass(player)) {
                if (protectionManager.getPlayerClaims(player.getUniqueId()).size() >= protectionManager.getMaxClaimsPerPlayer()) {
                    event.setCancelled(true);
                    player.sendMessage(color("&cHas alcanzado el límite máximo de protecciones permitidas (&e" + protectionManager.getMaxClaimsPerPlayer() + "&c)."));
                    return;
                }
            }

            Block block = event.getBlockPlaced();
            int x = block.getX();
            int z = block.getZ();
            int radius = blockType.getRadius();

            // Verificación estricta de superposición con otras protecciones (PS)
            if (protectionManager.overlapsWithExistingClaim(block.getWorld().getName(), x, z, radius, null)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes colocar esta piedra aquí: su radio de &e" + radius + "b &ccolisiona con otra protección existente."));
                return;
            }

            // Verificar GriefPrevention (bloque central)
            if (!protectionManager.getGriefPreventionHook().canBuild(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes colocar una piedra aquí: el lugar pertenece a un claim de GriefPrevention."));
                return;
            }

            // Verificar superposición de área con claims de GriefPrevention
            me.ryanhamshire.GriefPrevention.Claim gpOverlap = protectionManager.getGriefPreventionHook().getOverlappingClaim(block.getWorld().getName(), x, z, radius);
            if (gpOverlap != null) {
                event.setCancelled(true);
                String gpOwner = gpOverlap.getOwnerName();
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes colocar esta piedra aquí: su radio de &e" + radius + "b &ccolisiona con una protección de GriefPrevention (" + (gpOwner != null ? gpOwner : "jugador") + ")."));
                return;
            }

            // Verificar WorldGuard / Spawn (centro y 4 esquinas del radio)
            Location centerLoc = block.getLocation();
            org.bukkit.World w = block.getWorld();
            Location[] checkLocs = new Location[]{
                centerLoc,
                new Location(w, x - radius, block.getY(), z - radius),
                new Location(w, x + radius, block.getY(), z - radius),
                new Location(w, x - radius, block.getY(), z + radius),
                new Location(w, x + radius, block.getY(), z + radius)
            };
            for (Location testLoc : checkLocs) {
                if (!protectionManager.getWorldGuardHook().canBuildWithWorldGuard(player, testLoc)) {
                    event.setCancelled(true);
                    player.sendMessage(color("&8[&cProtección&8] &cNo puedes proteger aquí: colisiona con una región protegida de WorldGuard."));
                    return;
                }
            }

            Claim claim = protectionManager.registerBlockClaim(player, block, blockType, temporary);
            block.setMetadata("alcatraz_protection", new FixedMetadataValue(plugin, true));
            player.playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);

            player.sendMessage(color("&8&m----------------------------------------"));
            player.sendMessage(color("&a&l¡PIEDRA DE PROTECCIÓN ACTIVADA!"));
            player.sendMessage(color("&7Mena: " + blockType.getDisplayName() + " &8| &7Tipo: " + (temporary ? "&cTemporal (1 Semana)" : "&aPermanente")));
            player.sendMessage(color("&7Radio: &e" + radius + " bloques &8| &7Área total: &b" + (radius * 2 + 1) + "x" + (radius * 2 + 1) + " bloques&7."));
            player.sendMessage(color("&7Cobertura: &aColumna completa (Bedrock a Cielo)&7."));
            player.sendMessage(color("&7Usa &e/ps trust <amigo> &7o &e/ps flags &7para configurar."));
            player.sendMessage(color("&8&m----------------------------------------"));
            return;
        }

        // Si es un bloque normal colocado dentro de una protección
        if (hasBypass(player)) return;
        Claim claim = protectionManager.getClaimAt(event.getBlock().getLocation());
        if (claim != null) {
            boolean canBuild = claim.isTrusted(player.getUniqueId()) || (AlcatrazPlugin.getTrustProvider() != null && AlcatrazPlugin.getTrustProvider().canBuild(claim, player));
            if (!canBuild && !claim.getFlag(ClaimFlag.PLACE)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cEsta zona pertenece a &e" + claim.getOwnerName() + "&c."));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protectionManager.isEnabled()) return;
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // 0. Si el evento proviene de una simulación (ej. comprobaciones de minado AOE / VeinMiner / BlastMiner)
        if (player.hasMetadata("alcatraz_simulating")) {
            Claim blockClaim = protectionManager.getClaimByBlockLocation(block.getLocation());
            if (blockClaim != null) {
                // Jamás permitir que un minado simulado o en área rompa un bloque de protección
                event.setCancelled(true);
                return;
            }
            if (hasBypass(player)) return;
            Claim claim = protectionManager.getClaimAt(block.getLocation());
            if (claim != null) {
                boolean canBuild = claim.isTrusted(player.getUniqueId()) || (AlcatrazPlugin.getTrustProvider() != null && AlcatrazPlugin.getTrustProvider().canBuild(claim, player));
                if (!canBuild && !claim.getFlag(ClaimFlag.BREAK)) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // 1. Si rompe el bloque mismo de la protección
        Claim blockClaim = protectionManager.getClaimByBlockLocation(block.getLocation());
        if (blockClaim != null && blockClaim.isProtectionBlockLocation(block.getLocation())) {
            if (blockClaim.isOwner(player.getUniqueId()) || hasBypass(player)) {
                Location blockLoc = block.getLocation();
                recentlyRemovedProtectionBlocks.add(blockLoc);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> recentlyRemovedProtectionBlocks.remove(blockLoc), 40L);

                protectionManager.removeClaim(blockClaim.getId());

                // Cancelar de raíz el evento original para evitar CUALQUIER drop vanilla,
                // orbes de experiencia o que otros plugins (AuraSkills, Quests, etc.) lo detecten como minería.
                event.setCancelled(true);
                event.setDropItems(false);
                event.setExpToDrop(0);

                // Remover el bloque del mundo inmediatamente
                block.removeMetadata("alcatraz_protection", plugin);
                block.setType(Material.AIR);

                // Entregar únicamente el ítem especial de la protección
                ItemStack drop = protectionManager.createProtectionBlockItem(blockClaim.getBlockType(), blockClaim.getType() == ClaimType.TEMPORARY, 1);
                if (player.getInventory().addItem(drop).size() > 0) {
                    block.getWorld().dropItemNaturally(blockLoc, drop);
                }

                // Desgastar la herramienta 1 punto si está en modo supervivencia
                if (player.getGameMode() == GameMode.SURVIVAL) {
                    ItemStack tool = player.getInventory().getItemInMainHand();
                    if (tool != null && tool.getType().getMaxDurability() > 0 && tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
                        damageable.setDamage(damageable.getDamage() + 1);
                        tool.setItemMeta(damageable);
                    }
                }

                player.playSound(blockLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                block.getWorld().spawnParticle(Particle.BLOCK, blockLoc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, blockClaim.getBlockType().getMaterial().createBlockData());
                player.sendMessage(color("&8[&aProtección&8] &aProtección retirada y guardada en tu inventario."));
                return;
            } else {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes romper la piedra de protección de &e" + blockClaim.getOwnerName() + "&c."));
                return;
            }
        }

        // 2. Si rompe un bloque normal dentro de un terreno protegido
        if (hasBypass(player)) return;
        Claim claim = protectionManager.getClaimAt(block.getLocation());
        if (claim != null) {
            boolean canBuild = claim.isTrusted(player.getUniqueId()) || (AlcatrazPlugin.getTrustProvider() != null && AlcatrazPlugin.getTrustProvider().canBuild(claim, player));
            if (!canBuild && !claim.getFlag(ClaimFlag.BREAK)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cEsta zona pertenece a &e" + claim.getOwnerName() + "&c."));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!protectionManager.isEnabled()) return;
        Location loc = event.getBlock().getLocation();
        if (protectionManager.getClaimByBlockLocation(loc) != null || recentlyRemovedProtectionBlocks.contains(loc)) {
            event.getItems().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!protectionManager.isEnabled()) return;
        Player player = event.getPlayer();

        // Pisoteo físico de cultivos
        if (event.getAction() == Action.PHYSICAL) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.FARMLAND) {
                Claim claim = protectionManager.getClaimAt(block.getLocation());
                if (claim != null && !claim.isTrusted(player.getUniqueId()) && !claim.getFlag(ClaimFlag.CROP_TRAMPLE)) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null) return;

            // Clic sobre la piedra de protección
            Claim blockClaim = protectionManager.getClaimByBlockLocation(block.getLocation());
            if (blockClaim != null) {
                if (event.getHand() == EquipmentSlot.HAND && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    protectionManager.showClaimParticles(player, blockClaim);
                    player.sendMessage(color("&8&m----------------------------------------"));
                    player.sendMessage(color("&6&lInformación de la Piedra de Protección"));
                    player.sendMessage(color("&7Dueño: &e" + blockClaim.getOwnerName() + " &8| &7Tipo: &b" + blockClaim.getType().getDisplay()));
                    player.sendMessage(color("&7Radio: &e" + blockClaim.getRadius() + " bloques &8| &7Duración: " + blockClaim.getRemainingTimeFormatted()));
                    player.sendMessage(color("&7Miembros de confianza: &a" + (blockClaim.isTrustAll() ? "&eTodos (Público)" : blockClaim.getTrustedPlayers().size() + " jugadores")));
                    player.sendMessage(color("&7Partículas de límites dibujadas durante 8s."));
                    player.sendMessage(color("&8&m----------------------------------------"));
                }
                return;
            }

            if (hasBypass(player)) return;

            Material mat = block.getType();
            Claim claim = protectionManager.getClaimAt(block.getLocation());
            if (claim == null) return;

            boolean isTrusted = claim.isTrusted(player.getUniqueId());

            // 1. Contenedores
            if (isContainer(mat)) {
                boolean canContainer = isTrusted || (AlcatrazPlugin.getTrustProvider() != null && AlcatrazPlugin.getTrustProvider().canContainer(claim, player));
                if (!canContainer && !claim.getFlag(ClaimFlag.CONTAINERS)) {
                    event.setCancelled(true);
                    player.sendMessage(color("&8[&cProtección&8] &cNo puedes abrir contenedores en la protección de &e" + claim.getOwnerName() + "&c."));
                    return;
                }
            }

            // 2. Redstone, Puertas y Mecanismos
            if (isRedstoneOrDoor(mat)) {
                boolean canAccess = isTrusted || (AlcatrazPlugin.getTrustProvider() != null && AlcatrazPlugin.getTrustProvider().canAccess(claim, player));
                if (!canAccess && !claim.getFlag(ClaimFlag.INTERACT)) {
                    event.setCancelled(true);
                    player.sendMessage(color("&8[&cProtección&8] &cNo puedes interactuar con mecanismos en la protección de &e" + claim.getOwnerName() + "&c."));
                    return;
                }
            }
        }
    }

    private boolean isContainer(Material mat) {
        String name = mat.name();
        return name.contains("CHEST") || name.contains("SHULKER") || name.contains("BARREL")
                || name.contains("FURNACE") || name.contains("SMOKER") || name.contains("HOPPER")
                || name.contains("DISPENSER") || name.contains("DROPPER") || name.contains("BREWING_STAND")
                || name.contains("ANVIL") || name.contains("BEACON") || name.contains("LECTERN")
                || name.contains("CHISELED_BOOKSHELF") || name.contains("CRAFTER") || name.contains("VAULT")
                || name.contains("DECORATED_POT");
    }

    private boolean isRedstoneOrDoor(Material mat) {
        String name = mat.name();
        return name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("GATE")
                || name.contains("BUTTON") || name.contains("LEVER") || name.contains("REPEATER")
                || name.contains("COMPARATOR") || name.contains("DAYLIGHT_DETECTOR")
                || name.contains("NOTE_BLOCK") || name.contains("JUKEBOX") || name.contains("BELL")
                || mat == Material.DRAGON_EGG || mat == Material.RESPAWN_ANCHOR;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!protectionManager.isEnabled()) return;
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        Claim claim = protectionManager.getClaimAt(event.getBlock().getLocation());
        if (claim != null && !claim.isTrusted(player.getUniqueId()) && !claim.getFlag(ClaimFlag.PLACE)) {
            event.setCancelled(true);
            player.sendMessage(color("&8[&cProtección&8] &cNo puedes vaciar cubos dentro de esta protección."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!protectionManager.isEnabled()) return;
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        Claim claim = protectionManager.getClaimAt(event.getBlock().getLocation());
        if (claim != null && !claim.isTrusted(player.getUniqueId()) && !claim.getFlag(ClaimFlag.BREAK)) {
            event.setCancelled(true);
            player.sendMessage(color("&8[&cProtección&8] &cNo puedes recoger líquidos dentro de esta protección."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!protectionManager.isEnabled()) return;

        Entity victim = event.getEntity();
        Entity damager = event.getDamager();
        Player attacker = null;

        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        Claim claim = protectionManager.getClaimAt(victim.getLocation());
        if (claim == null) return;

        if (attacker != null && hasBypass(attacker)) return;

        // 1. PvP
        if (victim instanceof Player && attacker != null) {
            if (!claim.getFlag(ClaimFlag.PVP)) {
                event.setCancelled(true);
                attacker.sendMessage(color("&8[&cProtección&8] &cEl PvP está desactivado en esta zona protegida."));
                return;
            }
        }

        // 2. Animales y Mascotas
        if ((victim instanceof Animals || victim instanceof Tameable) && attacker != null) {
            if (!claim.isTrusted(attacker.getUniqueId()) && !claim.getFlag(ClaimFlag.MOB_DAMAGE)) {
                event.setCancelled(true);
                attacker.sendMessage(color("&8[&cProtección&8] &cNo puedes dañar animales ni mascotas en esta protección."));
                return;
            }
        }

        // 3. Aldeanos
        if (victim instanceof Villager && attacker != null) {
            if (!claim.isTrusted(attacker.getUniqueId()) && !claim.getFlag(ClaimFlag.VILLAGER_DAMAGE)) {
                event.setCancelled(true);
                attacker.sendMessage(color("&8[&cProtección&8] &cNo puedes dañar aldeanos en esta protección."));
                return;
            }
        }

        // 4. Marcos de Items, Cuadros, Armor Stands, Cristales y Vehículos
        if ((victim instanceof ItemFrame || victim instanceof Painting) && attacker != null) {
            if (!claim.isTrusted(attacker.getUniqueId()) && !claim.getFlag(ClaimFlag.ITEM_FRAMES)) {
                event.setCancelled(true);
                attacker.sendMessage(color("&8[&cProtección&8] &cNo puedes dañar marcos ni cuadros en esta protección."));
                return;
            }
        }

        if (victim instanceof ArmorStand && attacker != null) {
            if (!claim.isTrusted(attacker.getUniqueId()) && !claim.getFlag(ClaimFlag.ARMOR_STANDS)) {
                event.setCancelled(true);
                attacker.sendMessage(color("&8[&cProtección&8] &cNo puedes dañar soportes de armadura en esta protección."));
                return;
            }
        }

        if ((victim instanceof Vehicle || victim instanceof EnderCrystal) && attacker != null) {
            if (!claim.isTrusted(attacker.getUniqueId()) && !claim.getFlag(ClaimFlag.VEHICLES)) {
                event.setCancelled(true);
                attacker.sendMessage(color("&8[&cProtección&8] &cNo puedes dañar vehículos ni cristales en esta protección."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!protectionManager.isEnabled()) return;
        Claim claim = protectionManager.getClaimAt(event.getEntity().getLocation());
        if (claim != null) {
            if (event instanceof HangingBreakByEntityEvent byEntity) {
                if (byEntity.getRemover() instanceof Player player) {
                    if (hasBypass(player) || claim.isTrusted(player.getUniqueId()) || claim.getFlag(ClaimFlag.ITEM_FRAMES)) {
                        return;
                    }
                }
            }
            if (!claim.getFlag(ClaimFlag.ITEM_FRAMES)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!protectionManager.isEnabled()) return;
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        Entity entity = event.getRightClicked();
        Claim claim = protectionManager.getClaimAt(entity.getLocation());
        if (claim == null) return;

        boolean isTrusted = claim.isTrusted(player.getUniqueId());

        if (entity instanceof Villager) {
            if (!isTrusted && !claim.getFlag(ClaimFlag.VILLAGER_TRADE)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cNo tienes permiso para comerciar con aldeanos aquí."));
                return;
            }
        }

        if (entity instanceof ItemFrame) {
            if (!isTrusted && !claim.getFlag(ClaimFlag.ITEM_FRAMES)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes modificar marcos en esta protección."));
                return;
            }
        }

        if (entity instanceof ArmorStand) {
            if (!isTrusted && !claim.getFlag(ClaimFlag.ARMOR_STANDS)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes modificar soportes de armadura en esta protección."));
                return;
            }
        }

        if (entity instanceof Vehicle) {
            if (!isTrusted && !claim.getFlag(ClaimFlag.VEHICLES)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes interactuar con vehículos en esta protección."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!protectionManager.isEnabled()) return;
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        Entity entity = event.getRightClicked();
        if (entity instanceof ArmorStand) {
            Claim claim = protectionManager.getClaimAt(entity.getLocation());
            if (claim != null && !claim.isTrusted(player.getUniqueId()) && !claim.getFlag(ClaimFlag.ARMOR_STANDS)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cNo puedes modificar soportes de armadura en esta protección."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!protectionManager.isEnabled()) return;
        Claim claim = protectionManager.getClaimAt(event.getLocation());
        if (claim == null) return;

        if (event.getEntity() instanceof Monster) {
            if (!claim.getFlag(ClaimFlag.HOSTILE_SPAWN)) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof Animals) {
            if (!claim.getFlag(ClaimFlag.PASSIVE_SPAWN)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!protectionManager.isEnabled()) return;

        // Proteger el bloque de claim y cualquier bloque dentro de un claim sin flag EXPLOSIONS
        event.blockList().removeIf(b -> {
            if (protectionManager.getClaimByBlockLocation(b.getLocation()) != null) {
                return true;
            }
            Claim claim = protectionManager.getClaimAt(b.getLocation());
            return claim != null && !claim.getFlag(ClaimFlag.EXPLOSIONS);
        });

        Claim claim = protectionManager.getClaimAt(event.getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.EXPLOSIONS)) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!protectionManager.isEnabled()) return;

        event.blockList().removeIf(b -> {
            if (protectionManager.getClaimByBlockLocation(b.getLocation()) != null) {
                return true;
            }
            Claim claim = protectionManager.getClaimAt(b.getLocation());
            return claim != null && !claim.getFlag(ClaimFlag.EXPLOSIONS);
        });

        Claim claim = protectionManager.getClaimAt(event.getBlock().getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.EXPLOSIONS)) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (!protectionManager.isEnabled()) return;
        if (event.getSource().getType() == Material.FIRE || event.getNewState().getType() == Material.FIRE) {
            Claim claim = protectionManager.getClaimAt(event.getBlock().getLocation());
            if (claim != null && !claim.getFlag(ClaimFlag.FIRE_SPREAD)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!protectionManager.isEnabled()) return;
        Claim claim = protectionManager.getClaimAt(event.getBlock().getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!protectionManager.isEnabled()) return;
        Claim claim = protectionManager.getClaimAt(event.getBlock().getLocation());
        if (claim != null && !claim.getFlag(ClaimFlag.FIRE_SPREAD)) {
            if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL || (event.getPlayer() != null && !claim.isTrusted(event.getPlayer().getUniqueId()))) {
                event.setCancelled(true);
            }
        }
    }

    // ==========================================================
    // ANTI-PISTONES EXTERNOS
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!protectionManager.isEnabled()) return;

        Block piston = event.getBlock();
        Claim pistonClaim = protectionManager.getClaimAt(piston.getLocation());

        for (Block b : event.getBlocks()) {
            // Proteger piedra de claim
            if (protectionManager.getClaimByBlockLocation(b.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }

            Block target = b.getRelative(event.getDirection());
            Claim targetClaim = protectionManager.getClaimAt(target.getLocation());

            if (targetClaim != null && (pistonClaim == null || !pistonClaim.getId().equals(targetClaim.getId()))) {
                if (!targetClaim.getFlag(ClaimFlag.PISTONS)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!protectionManager.isEnabled()) return;

        Block piston = event.getBlock();
        Claim pistonClaim = protectionManager.getClaimAt(piston.getLocation());

        for (Block b : event.getBlocks()) {
            if (protectionManager.getClaimByBlockLocation(b.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }

            Claim blockClaim = protectionManager.getClaimAt(b.getLocation());
            if (blockClaim != null && (pistonClaim == null || !pistonClaim.getId().equals(blockClaim.getId()))) {
                if (!blockClaim.getFlag(ClaimFlag.PISTONS)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ==========================================================
    // ANTI-FLUJO DE LÍQUIDOS EXTERNOS
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!protectionManager.isEnabled()) return;

        Block from = event.getBlock();
        Block to = event.getToBlock();

        Claim fromClaim = protectionManager.getClaimAt(from.getLocation());
        Claim toClaim = protectionManager.getClaimAt(to.getLocation());

        if (toClaim != null && (fromClaim == null || !fromClaim.getId().equals(toClaim.getId()))) {
            Material mat = from.getType();
            if (mat == Material.WATER || mat == Material.BUBBLE_COLUMN) {
                if (!toClaim.getFlag(ClaimFlag.WATER_FLOW)) {
                    event.setCancelled(true);
                }
            } else if (mat == Material.LAVA) {
                if (!toClaim.getFlag(ClaimFlag.LAVA_FLOW)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // ==========================================================
    // TELETRANSPORTE (Enderpearls y Chorus)
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!protectionManager.isEnabled()) return;
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        Location to = event.getTo();
        if (to == null) return;

        Claim claim = protectionManager.getClaimAt(to);
        if (claim == null) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            if (!claim.isTrusted(player.getUniqueId()) && !claim.getFlag(ClaimFlag.ENDERPEARL)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cLas perlas de ender están bloqueadas en este terreno."));
            }
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            if (!claim.isTrusted(player.getUniqueId()) && !claim.getFlag(ClaimFlag.CHORUS_FRUIT)) {
                event.setCancelled(true);
                player.sendMessage(color("&8[&cProtección&8] &cLa fruta coral está bloqueada en este terreno."));
            }
        }
    }

    // ==========================================================
    // NOTIFICACIONES DE ENTRADA / SALIDA Y MODO VUELO (FLY FLAG)
    // ==========================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!protectionManager.isEnabled()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;

        handleClaimTransition(event.getPlayer(), from, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportFlightCheck(PlayerTeleportEvent event) {
        if (!protectionManager.isEnabled()) return;
        handleClaimTransition(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void handleClaimTransition(Player player, Location from, Location to) {
        if (!protectionManager.isEnabled() || player == null || to == null) return;

        Claim claimFrom = from != null ? protectionManager.getClaimAt(from) : null;
        Claim claimTo = protectionManager.getClaimAt(to);

        String fromId = claimFrom != null ? claimFrom.getId() : null;
        String toId = claimTo != null ? claimTo.getId() : null;

        if (Objects.equals(fromId, toId)) return;

        boolean canFlyInTo = false;
        if (claimTo != null) {
            playerCurrentClaim.put(player.getUniqueId(), claimTo.getId());
            if (protectionManager.isShowEntryExitMessages()) {
                String msg = color("&8[&a✦ Entrada&8] &7Has entrado al terreno de &e" + claimTo.getOwnerName() + " &7(" + claimTo.getRadius() + "b)&7.");
                sendActionBar(player, msg);
            }

            if (claimTo.getFlag(ClaimFlag.FLY)) {
                canFlyInTo = claimTo.isTrusted(player.getUniqueId()) || claimTo.isTrustAll()
                        || (AlcatrazPlugin.getTrustProvider() != null && AlcatrazPlugin.getTrustProvider().canAccess(claimTo, player))
                        || player.hasPermission("alcatraz.claim.fly");

                if (canFlyInTo && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                    player.setAllowFlight(true);
                    sendActionBar(player, color("&a✦ [Vuelo Activado] &7Modo vuelo habilitado en esta protección."));
                }
            }
        } else {
            playerCurrentClaim.remove(player.getUniqueId());
            if (claimFrom != null && protectionManager.isShowEntryExitMessages()) {
                String msg = color("&8[&c✦ Salida&8] &7Has salido del terreno de &e" + claimFrom.getOwnerName() + "&7.");
                sendActionBar(player, msg);
            }
        }

        // Si venía de un terreno con FLY y en el nuevo destino NO tiene permitido volar:
        if (claimFrom != null && claimFrom.getFlag(ClaimFlag.FLY) && !canFlyInTo) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                    && !player.hasPermission("alcatraz.fly.bypass") && !player.hasPermission("essentials.fly")) {
                player.setFlying(false);
                player.setAllowFlight(false);
                // 8 segundos de inmunidad a daño por caída para descenso seguro
                fallDamageImmunity.put(player.getUniqueId(), System.currentTimeMillis() + 8000L);
                sendActionBar(player, color("&c⚠ [Vuelo Desactivado] &7Has salido de la protección (inmunidad de caída activa 8s)."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
                Long expire = fallDamageImmunity.get(player.getUniqueId());
                if (expire != null) {
                    if (System.currentTimeMillis() < expire) {
                        event.setCancelled(true);
                    } else {
                        fallDamageImmunity.remove(player.getUniqueId());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String currentClaimId = playerCurrentClaim.remove(uuid);
        fallDamageImmunity.remove(uuid);

        if (currentClaimId != null) {
            Claim claim = protectionManager.getClaim(currentClaimId);
            if (claim != null && claim.getFlag(ClaimFlag.FLY)) {
                if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                        && !player.hasPermission("alcatraz.fly.bypass") && !player.hasPermission("essentials.fly")) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
            }
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Throwable ignored) {
            player.sendMessage(message);
        }
    }
}
