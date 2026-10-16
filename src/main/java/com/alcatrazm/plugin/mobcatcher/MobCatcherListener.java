package com.alcatrazm.plugin.mobcatcher;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.protection.Claim;
import com.alcatrazm.plugin.protection.ProtectionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class MobCatcherListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final MobCatcherManager manager;

    public MobCatcherListener(AlcatrazPlugin plugin, MobCatcherManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /**
     * Entidades que nunca pueden capturarse: bosses, NPCs, mobs de plugins, monturas y mascotas ajenas.
     */
    private boolean isForbidden(LivingEntity living, Player player) {
        if (living instanceof Player || living instanceof Boss || living instanceof Warden
                || living instanceof ElderGuardian || living instanceof ArmorStand || living instanceof Giant) {
            return true;
        }
        // NPCs (Citizens y similares)
        if (living.hasMetadata("NPC")) return true;
        // Mobs gestionados por plugins (MythicMobs / ModelEngine)
        for (NamespacedKey key : living.getPersistentDataContainer().getKeys()) {
            String ns = key.getNamespace();
            if (ns.equals("mythicmobs") || ns.equals("modelengine")) return true;
        }
        // Bosses detectados por el módulo BossDefense
        if (plugin.getBossDefenseManager() != null && plugin.getBossDefenseManager().isBoss(living)) return true;
        // Monturas o vehículos con jinete
        if (!living.getPassengers().isEmpty() || living.isInsideVehicle()) return true;
        // Mascotas domesticadas de otro jugador
        if (living instanceof Tameable tame && tame.isTamed() && tame.getOwner() != null
                && !tame.getOwner().getUniqueId().equals(player.getUniqueId())) {
            return true;
        }
        return false;
    }

    /**
     * Respeta los terrenos de Alcatraz: solo dueños/confiados (o bypass) pueden capturar/liberar dentro.
     */
    private boolean canUseAt(Player player, Location loc) {
        if (player.hasPermission("alcatraz.mobcatcher.bypass")) return true;
        ProtectionManager prot = plugin.getProtectionManager();
        if (prot == null || !prot.isEnabled()) return true;
        Claim claim = prot.getClaimAt(loc);
        return claim == null || claim.isTrusted(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCaptureEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (!manager.isEmptyCatcher(handItem)) return;

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity living) || isForbidden(living, player)) {
            event.setCancelled(true);
            player.sendMessage(color("&cNo puedes capturar esta entidad."));
            return;
        }

        if (!living.isValid() || living.isDead() || living.getHealth() <= 0.0) {
            event.setCancelled(true);
            return;
        }

        if (!canUseAt(player, living.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(color("&cNo puedes capturar criaturas en un terreno ajeno."));
            return;
        }

        event.setCancelled(true);

        ItemStack filled = manager.createFilledCatcher(living);

        // Consumir 1 esfera vacía
        handItem.setAmount(handItem.getAmount() - 1);

        // Dar esfera llena
        if (!player.getInventory().addItem(filled).isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), filled);
        }

        // Efectos y remover mob
        Location loc = living.getLocation();
        living.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.1);
        living.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        String displayName = living.getCustomName() != null ? living.getCustomName() : living.getType().name();
        living.remove();

        player.sendMessage(color("&8[&dEsfera Cuántica&8] &a¡Has capturado a &e" + displayName + " &acon éxito!"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReleaseEntity(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        // La esfera vacía es un Ojo de Ender: impedir que se lance o se inserte en marcos del End
        if (manager.isEmptyCatcher(handItem)) {
            event.setCancelled(true);
            return;
        }

        if (!manager.isFilledCatcher(handItem)) return;

        // Nunca lanzar la esfera llena como bola de nieve (se perdería la criatura)
        event.setCancelled(true);

        if (action == Action.RIGHT_CLICK_AIR || event.getClickedBlock() == null) {
            player.sendMessage(color("&eHaz click derecho sobre un bloque para liberar la criatura."));
            return;
        }

        org.bukkit.block.BlockFace face = event.getBlockFace();
        Location spawnLoc = event.getClickedBlock().getRelative(face != null ? face : org.bukkit.block.BlockFace.UP).getLocation().add(0.5, 0.0, 0.5);
        if (!canUseAt(player, spawnLoc)) {
            player.sendMessage(color("&cNo puedes liberar criaturas en un terreno ajeno."));
            return;
        }

        PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
        Entity spawned = null;

        // 1. Restauración completa desde snapshot (esferas nuevas)
        String snbt = pdc.get(manager.KEY_SNAPSHOT, PersistentDataType.STRING);
        if (snbt != null && !snbt.isEmpty()) {
            try {
                EntitySnapshot snapshot = Bukkit.getEntityFactory().createEntitySnapshot(snbt);
                spawned = snapshot.createEntity(spawnLoc);
            } catch (Throwable t) {
                plugin.getLogger().warning("[MobCatcher] Snapshot inválido, usando restauración básica: " + t.getMessage());
                spawned = null;
            }
        }

        // 2. Restauración básica (esferas antiguas sin snapshot)
        if (spawned == null) {
            spawned = spawnLegacy(player, pdc, spawnLoc);
            if (spawned == null) return;
        }

        // Efectos
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc, 25, 0.3, 0.5, 0.3, 0.05);
        player.getWorld().playSound(spawnLoc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 1.0f);

        // Consumir la esfera llena
        handItem.setAmount(handItem.getAmount() - 1);
        player.sendMessage(color("&8[&dEsfera Cuántica&8] &a¡Has liberado a la criatura exitosamente!"));
    }

    private Entity spawnLegacy(Player player, PersistentDataContainer pdc, Location spawnLoc) {
        String typeStr = pdc.get(manager.KEY_MOB_TYPE, PersistentDataType.STRING);
        if (typeStr == null) return null;

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(color("&cTipo de entidad no reconocido."));
            return null;
        }

        Entity spawned = player.getWorld().spawnEntity(spawnLoc, entityType);

        if (spawned instanceof LivingEntity living) {
            Double health = pdc.get(manager.KEY_MOB_HEALTH, PersistentDataType.DOUBLE);
            if (health != null && health > 0) {
                org.bukkit.attribute.AttributeInstance maxAttr = living.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                double max = maxAttr != null ? maxAttr.getValue() : health;
                living.setHealth(Math.min(health, max));
            }

            String customName = pdc.get(manager.KEY_MOB_NAME, PersistentDataType.STRING);
            if (customName != null) {
                living.setCustomName(customName);
                living.setCustomNameVisible(true);
            }

            if (living instanceof Villager v) {
                String profStr = pdc.get(manager.KEY_VILLAGER_PROF, PersistentDataType.STRING);
                Integer lvl = pdc.get(manager.KEY_VILLAGER_LEVEL, PersistentDataType.INTEGER);
                Integer exp = pdc.get(manager.KEY_VILLAGER_EXP, PersistentDataType.INTEGER);

                if (profStr != null) {
                    Villager.Profession prof = org.bukkit.Registry.VILLAGER_PROFESSION.get(
                            NamespacedKey.minecraft(profStr.toLowerCase()));
                    if (prof != null) v.setProfession(prof);
                }
                if (lvl != null) v.setVillagerLevel(lvl);
                if (exp != null) v.setVillagerExperience(exp);
            }
        }
        return spawned;
    }
}
