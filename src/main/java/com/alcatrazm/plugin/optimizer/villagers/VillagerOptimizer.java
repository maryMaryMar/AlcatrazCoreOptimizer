package com.alcatrazm.plugin.optimizer.villagers;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.event.Listener;

/**
 * Optimizador de Aldeanos — DESACTIVADO COMPLETAMENTE.
 * Garantiza que todos los aldeanos mantengan su IA, reproducción, búsqueda de camas
 * y comportamiento vanilla al 100%.
 */
public class VillagerOptimizer implements Listener {

    private final AlcatrazPlugin plugin;

    public VillagerOptimizer(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        restoreAllVillagers();
    }

    public void loadConfig() {}
    public void reload() {
        restoreAllVillagers();
    }
    public void startTask() {}
    public void stopTask() {
        restoreAllVillagers();
    }

    public void restoreAllVillagers() {
        Runnable task = () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                    if (villager.isValid() && !villager.isAware()) {
                        villager.setAware(true);
                    }
                }
            }
        };

        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            task.run();
        }
    }
}
