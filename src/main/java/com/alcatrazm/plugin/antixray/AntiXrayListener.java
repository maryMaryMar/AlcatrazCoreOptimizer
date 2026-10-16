package com.alcatrazm.plugin.antixray;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class AntiXrayListener implements Listener {

    private final AntiXrayManager manager;

    public AntiXrayListener(AntiXrayManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!manager.isEnabled()) return;

        Player player = event.getPlayer();
        if (player == null || player.getGameMode() != GameMode.SURVIVAL) return;

        Block block = event.getBlock();
        Material mat = block.getType();

        if (manager.isTrackedOre(mat) || manager.isStone(mat)) {
            manager.recordBlockBreak(player, mat, block.getLocation());
        }
    }
}
