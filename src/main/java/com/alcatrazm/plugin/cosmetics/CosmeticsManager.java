package com.alcatrazm.plugin.cosmetics;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.announcer.AutoAnnouncer;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.data.BukkitEntityData;
import com.ticxo.modelengine.api.entity.data.IEntityData;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticsManager {

    private final AlcatrazPlugin plugin;
    private final Map<UUID, CosmeticType> activeCosmetics = new ConcurrentHashMap<>();
    private boolean modelEngineAvailable = false;

    public CosmeticsManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        checkModelEngine();
    }

    private void checkModelEngine() {
        this.modelEngineAvailable = Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
    }

    private boolean isModelEngineReady() {
        if (!this.modelEngineAvailable) {
            checkModelEngine();
        }
        return this.modelEngineAvailable;
    }

    /**
     * Adjunta el modelo 3D de ModelEngine al jugador y lo fuerza en su propio tracking
     * para que el cliente propietario reciba los paquetes de ItemDisplay.
     */
    private boolean attachModel(Player player, CosmeticType type) {
        if (player == null || type == null || !isModelEngineReady()) {
            return false;
        }
        try {
            ModeledEntity me = ModelEngineAPI.getOrCreateModeledEntity(player);
            if (me == null) {
                return false;
            }
            me.restore();
            me.getBase().getBodyRotationController().setPlayerMode(true);
            me.setBaseEntityVisible(true);

            IEntityData entityData = me.getBase().getData();
            if (entityData instanceof BukkitEntityData data) {
                data.getTracked().addForcedPairing(player.getUniqueId());
            }

            me.removeModel(type.getModelId()).ifPresent(ActiveModel::destroy);
            ActiveModel model = ModelEngineAPI.createActiveModel(type.getModelId());
            if (model == null) {
                return false;
            }
            me.addModel(model, false);
            if (model.getAnimationHandler() != null) {
                model.getAnimationHandler().playAnimation("idle", 0.2, 0.2, 1.0, true);
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Error al adjuntar modelo ModelEngine de cosmético (" + type.getModelId() + "): " + t.getMessage());
            return false;
        }
    }

    private void detachModel(Player player, CosmeticType type, boolean cleanupEntity) {
        if (player == null || type == null || !isModelEngineReady()) {
            return;
        }
        try {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(player);
            if (me == null) {
                return;
            }
            me.removeModel(type.getModelId()).ifPresent(ActiveModel::destroy);
            if (cleanupEntity && me.getModels().isEmpty()) {
                IEntityData entityData = me.getBase().getData();
                if (entityData instanceof BukkitEntityData data) {
                    data.getTracked().removeForcedPairing(player.getUniqueId());
                }
                ModelEngineAPI.removeModeledEntity(player);
            }
        } catch (Throwable ignored) {}
    }

    public boolean equipCosmetic(Player player, CosmeticType type) {
        if (player == null || type == null) return false;

        // Si ya tiene este cosmético activo, se lo retira (comportamiento toggle)
        CosmeticType current = activeCosmetics.get(player.getUniqueId());
        if (current == type) {
            removeCosmetic(player, true);
            return false;
        }

        // Si tenía otro, lo retiramos primero manteniendo el ModeledEntity vivo para el intercambio
        if (current != null) {
            activeCosmetics.remove(player.getUniqueId());
            detachModel(player, current, false);
        }

        if (!attachModel(player, type)) {
            player.sendMessage(AutoAnnouncer.colorize("&8[&bCosméticos&8] &cNo se pudo cargar el modelo 3D del cosmético (&e" + type.getModelId() + "&c)."));
            return false;
        }

        activeCosmetics.put(player.getUniqueId(), type);
        player.sendMessage(AutoAnnouncer.colorize("&8[&bCosméticos&8] &a¡Has equipado tu cosmético &e" + type.getDisplayName() + "&a!"));
        return true;
    }

    public void removeCosmetic(Player player, boolean notify) {
        if (player == null) return;
        CosmeticType type = activeCosmetics.remove(player.getUniqueId());
        if (type != null) {
            detachModel(player, type, true);
        }
        if (notify) {
            player.sendMessage(AutoAnnouncer.colorize("&8[&bCosméticos&8] &cHas desequipado tu cosmético actual."));
        }
    }

    public void removeCosmeticByUUID(UUID uuid) {
        CosmeticType type = activeCosmetics.remove(uuid);
        if (type != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                detachModel(player, type, true);
            }
        }
    }

    public CosmeticType getActiveCosmetic(UUID uuid) {
        return activeCosmetics.get(uuid);
    }

    public boolean hasPermission(Player player, CosmeticType type) {
        if (player == null || type == null) return false;
        if (player.isOp()) return true;
        if (player.hasPermission("alcatraz.cosmetics.admin") || player.hasPermission("alcatraz.cosmetics.all")) return true;
        return player.hasPermission(type.getPermission());
    }

    public void restoreOnRespawn(Player player) {
        if (player == null) return;
        if (activeCosmetics.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                CosmeticType active = activeCosmetics.get(player.getUniqueId());
                if (active != null) {
                    attachModel(player, active);
                }
            }, 5L);
        }
    }

    public void cleanAll() {
        for (Map.Entry<UUID, CosmeticType> entry : activeCosmetics.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                detachModel(p, entry.getValue(), true);
            }
        }
        activeCosmetics.clear();
    }
}
