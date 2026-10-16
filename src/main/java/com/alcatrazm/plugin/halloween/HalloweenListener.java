package com.alcatrazm.plugin.halloween;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Listener principal del módulo Halloween.
 *
 * Eventos manejados:
 *  1. PlayerInteractEvent — recolección de calabazas (clic derecho sobre PLAYER_HEAD del evento).
 *  2. BlockBreakEvent     — protección de cabezas contra rotura con pico.
 *  3. InventoryClickEvent — interacción con el GUI del Staff (HalloweenStaffGUI).
 *  4. EntityDeathEvent    — hook reservado para drops de mobs especiales (futuro).
 *
 * ── CORRECCIONES DE INCOMPATIBILIDAD ──────────────────────────────────────
 *
 * FIX 1 — ProtectionListener (interno, prioridad HIGH):
 *   onPlayerInteract usa HIGHEST + ignoreCancelled=false para ejecutarse DESPUÉS
 *   de ProtectionListener y poder descancelar el evento cuando es una cabeza del evento.
 *   PLAYER_HEAD no está en isContainer() de ProtectionListener, pero por seguridad
 *   igualmente descancelamos para garantizar la recolección en cualquier claim.
 *
 * FIX 2 — ProtectionListener.onBlockBreak (HIGH):
 *   onBlockBreak usa HIGHEST + ignoreCancelled=false. Si un admin con alcatraz.admin
 *   rompe la cabeza, se descancela y se desregistra. Para jugadores normales se cancela.
 *
 * FIX 3 — MythicMobs:
 *   onEntityDeath verifica entity.hasMetadata("MythicMobsEntity") antes de procesar drops.
 */
public class HalloweenListener implements Listener {

    private final HalloweenManager manager;

    public HalloweenListener(HalloweenManager manager) {
        this.manager = manager;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. RECOLECCIÓN DE CALABAZA (CLIC DERECHO O ROTURA CON PICO / MANO)
    // ══════════════════════════════════════════════════════════════════════

    private void collectHalloweenPumpkin(Player player, Block block) {
        Location loc = block.getLocation();
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        World w = loc.getWorld();

        // 1. Remover bloque y desregistrarlo de la memoria/YAML de forma atómica
        String removedHead = manager.removeHead(loc);
        if (removedHead == null) {
            return;
        }
        block.setType(Material.AIR, false);

        // 2. Acreditar saldo al jugador
        manager.addBalance(player.getUniqueId(), player.getName(), 1);
        int balance = manager.getBalance(player.getUniqueId());
        int total   = manager.getTotalFound(player.getUniqueId());

        // 3. Efectos de partículas y sonido temáticos
        if (w != null) {
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 30, 0.4, 0.4, 0.4, 0.05);
            w.spawnParticle(Particle.SMOKE,           center, 12, 0.3, 0.3, 0.3, 0.02);
            w.playSound(center, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.8f);
            w.playSound(center, Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 0.4f, 1.2f);
        }

        // 4. Entregar el ítem oficial con Lore y valor comercial
        ItemStack token = manager.createHalloweenToken(1);
        var leftover = player.getInventory().addItem(token);
        if (!leftover.isEmpty() && w != null) {
            w.dropItemNaturally(center, token);
        }

        // 5. Notificación visual (title + chat)
        player.sendTitle(
            color("&6&l🎃 ¡CALABAZA ENCONTRADA!"),
            color("&7Saldo: &e" + balance + " &7| Total histórico: &6" + total),
            5, 45, 10
        );
        player.sendMessage(color(
            "&8[&6Halloween&8] &e¡Calabaza Maldita recolectada! &7Saldo: &6" + balance + " &7| Histórico: &e" + total
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;
        if (!manager.isHalloweenHead(block.getLocation())) return;

        // Es una cabeza del evento → siempre permitir recolección (override de claims)
        event.setCancelled(false);
        collectHalloweenPumpkin(event.getPlayer(), block);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. ROTURA CON HERRAMIENTA O MANO (CLIC IZQUIERDO)
    // ══════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;
        if (!manager.isHalloweenHead(block.getLocation())) return;

        Player player = event.getPlayer();

        // Staff en Creativo: desregistrar del mapa sin entregar ítem
        if ((player.hasPermission("alcatraz.admin") || player.isOp()) && player.getGameMode() == GameMode.CREATIVE) {
            event.setCancelled(false);
            event.setDropItems(false);
            manager.removeHead(block.getLocation());
            player.sendMessage(color("&8[&6Halloween&8] &aCalabaza eliminada del registro (Modo Creativo)."));
            return;
        }

        // Cancelar el drop vanilla para que NUNCA suelte una cabeza sin lore ni skin
        event.setCancelled(true);
        event.setDropItems(false);

        // Recolectar con todas las recompensas, lore y valor comercial intactos
        collectHalloweenPumpkin(player, block);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2.1 PROTECCIÓN ANTI-COLOCACIÓN Y ANTI-PISTONES
    // ══════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (manager.isHalloweenToken(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&8[&6Halloween&8] &c¡No puedes colocar las Calabazas Malditas en el suelo! Guárdalas para comerciar con el Mercader."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if ((b.getType() == Material.PLAYER_HEAD || b.getType() == Material.PLAYER_WALL_HEAD) && manager.isHalloweenHead(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if ((b.getType() == Material.PLAYER_HEAD || b.getType() == Material.PLAYER_WALL_HEAD) && manager.isHalloweenHead(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. GUI DEL STAFF (HalloweenStaffGUI)
    // ══════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(HalloweenStaffGUI.GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        var pdc     = clicked.getItemMeta().getPersistentDataContainer();
        var uuidKey = new NamespacedKey(manager.getPlugin(), "hw_gui_uuid");
        String uuidStr = pdc.get(uuidKey, PersistentDataType.STRING);
        if (uuidStr == null) return;

        try {
            UUID   targetUUID = UUID.fromString(uuidStr);
            String targetName = manager.getSavedName(targetUUID);

            if (event.isShiftClick() && event.isRightClick()) {
                // Shift + Clic Derecho → Resetear saldo a 0
                manager.setBalance(targetUUID, 0);
                player.sendMessage(color("&8[&6Halloween&8] &cReseteado saldo de &e" + targetName + " &ca 0."));

            } else if (event.isLeftClick()) {
                // Clic Izquierdo → +1
                manager.addBalance(targetUUID, targetName, 1);
                player.sendMessage(color("&8[&6Halloween&8] &a+1 calabaza a &e" + targetName
                    + " &7(Saldo: &6" + manager.getBalance(targetUUID) + "&7)"));

            } else if (event.isRightClick()) {
                // Clic Derecho → -1
                int cur = manager.getBalance(targetUUID);
                if (cur > 0) {
                    manager.setBalance(targetUUID, cur - 1);
                    player.sendMessage(color("&8[&6Halloween&8] &c-1 calabaza a &e" + targetName
                        + " &7(Saldo: &6" + manager.getBalance(targetUUID) + "&7)"));
                } else {
                    player.sendMessage(color("&8[&6Halloween&8] &cEste jugador ya tiene 0 calabazas."));
                }
            }

            // Refrescar la GUI en el siguiente tick con datos actualizados
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> HalloweenStaffGUI.open(player, manager));

        } catch (IllegalArgumentException ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. DROPS DE ENTIDADES (hook reservado — futuro)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * FIX MYTHICMOBS: Excluir mobs con metadata "MythicMobsEntity" para no interferir
     * con los drops de los bosses de las mazmorras de PIPOM.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!manager.isEnabled()) return;

        // Excluir absolutamente todos los mobs de MythicMobs
        if (event.getEntity().hasMetadata("MythicMobsEntity")) return;

        // Hook reservado para mobs de Halloween en el futuro
        // Ejemplo: if (event.getEntity().getType() == EntityType.WITCH) { drops... }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. AUTO-APLICACIÓN DE SKINS AL CARGAR CHUNK (ON-THE-FLY)
    // ══════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (!manager.isEnabled()) return;
        org.bukkit.Chunk chunk = event.getChunk();
        org.bukkit.Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            if (chunk.isLoaded()) {
                manager.checkAndFixChunk(chunk, false);
            }
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
