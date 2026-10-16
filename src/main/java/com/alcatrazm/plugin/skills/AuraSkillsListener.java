package com.alcatrazm.plugin.skills;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class AuraSkillsListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final AuraSkillsFixer fixer;

    public AuraSkillsListener(AlcatrazPlugin plugin, AuraSkillsFixer fixer) {
        this.plugin = plugin;
        this.fixer = fixer;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!fixer.isAuraSkillsEnabled()) return;

        String msg = event.getMessage().toLowerCase().trim();
        if (msg.startsWith("/")) msg = msg.substring(1);
        String[] parts = msg.split(" ");
        String root = parts[0];

        // Si el usuario ejecuta cualquier comando raíz de skills
        if (root.equals("skills") || root.equals("skill") || root.equals("auraskills")
                || root.equals("auraskills:skills") || root.equals("auraskills:skill")) {
            Player player = event.getPlayer();
            if (!fixer.isUserLoaded(player.getUniqueId())) {
                plugin.getLogger().info("[AuraSkillsListener] Interceptado comando /" + root + " para " + player.getName() + " sin perfil. Sincronizando antes de abrir menú...");
                fixer.forceLoadUser(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Verificación de respaldo 25 ticks después de entrar
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && fixer.isAuraSkillsEnabled()) {
                if (!fixer.isUserLoaded(player.getUniqueId())) {
                    plugin.getLogger().warning("[AuraSkillsListener] Jugador online sin perfil detectado (" + player.getName() + "). Auto-recuperando...");
                    fixer.forceLoadUser(player);
                }
            }
        }, 25L);
    }
}
