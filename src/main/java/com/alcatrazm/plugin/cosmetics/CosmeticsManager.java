package com.alcatrazm.plugin.cosmetics;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.announcer.AutoAnnouncer;
import com.ticxo.modelengine.api.ModelEngineAPI;
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

    public boolean equipCosmetic(Player player, CosmeticType type) {
        if (player == null || type == null) return false;

        // Si ya tiene este cosmético activo, se lo retira (comportamiento toggle)
        CosmeticType current = activeCosmetics.get(player.getUniqueId());
        if (current == type) {
            removeCosmetic(player, true);
            return false;
        }

        // Si tenía otro, lo retiramos primero
        if (current != null) {
            removeCosmetic(player, false);
        }

        activeCosmetics.put(player.getUniqueId(), type);

        if (modelEngineAvailable) {
            try {
                ModeledEntity me = ModelEngineAPI.getOrCreateModeledEntity(player);
                if (me != null) {
                    ActiveModel model = ModelEngineAPI.createActiveModel(type.getModelId());
                    if (model != null) {
                        me.addModel(model, true);
                        if (model.getAnimationHandler() != null) {
                            model.getAnimationHandler().playAnimation("idle", 0.2, 0.2, 1.0, true);
                        }
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Error al adjuntar modelo ModelEngine de cosmético: " + t.getMessage());
            }
        }

        player.sendMessage(AutoAnnouncer.colorize("&8[&bCosméticos&8] &a¡Has equipado tu cosmético &e" + type.getDisplayName() + "&a!"));
        return true;
    }

    public void removeCosmetic(Player player, boolean notify) {
        if (player == null) return;
        CosmeticType type = activeCosmetics.remove(player.getUniqueId());
        if (type != null && modelEngineAvailable) {
            try {
                ModeledEntity me = ModelEngineAPI.getModeledEntity(player);
                if (me != null) {
                    me.removeModel(type.getModelId());
                }
            } catch (Throwable ignored) {}
        }
        if (notify) {
            player.sendMessage(AutoAnnouncer.colorize("&8[&bCosméticos&8] &cHas desequipado tu cosmético actual."));
        }
    }

    public void removeCosmeticByUUID(UUID uuid) {
        CosmeticType type = activeCosmetics.remove(uuid);
        if (type != null && modelEngineAvailable) {
            try {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    ModeledEntity me = ModelEngineAPI.getModeledEntity(player);
                    if (me != null) {
                        me.removeModel(type.getModelId());
                    }
                }
            } catch (Throwable ignored) {}
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
        CosmeticType active = activeCosmetics.get(player.getUniqueId());
        if (active != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !player.isDead()) {
                    // Re-adjuntar modelo
                    if (modelEngineAvailable) {
                        try {
                            ModeledEntity me = ModelEngineAPI.getOrCreateModeledEntity(player);
                            if (me != null) {
                                me.removeModel(active.getModelId());
                                ActiveModel model = ModelEngineAPI.createActiveModel(active.getModelId());
                                if (model != null) {
                                    me.addModel(model, true);
                                    if (model.getAnimationHandler() != null) {
                                        model.getAnimationHandler().playAnimation("idle", 0.2, 0.2, 1.0, true);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }, 5L);
        }
    }

    public void cleanAll() {
        for (Map.Entry<UUID, CosmeticType> entry : activeCosmetics.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && modelEngineAvailable) {
                try {
                    ModeledEntity me = ModelEngineAPI.getModeledEntity(p);
                    if (me != null) {
                        me.removeModel(entry.getValue().getModelId());
                    }
                } catch (Throwable ignored) {}
            }
        }
        activeCosmetics.clear();
    }
}
