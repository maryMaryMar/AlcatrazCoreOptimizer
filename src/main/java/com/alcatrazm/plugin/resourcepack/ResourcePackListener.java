package com.alcatrazm.plugin.resourcepack;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import java.util.UUID;

public class ResourcePackListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final ResourcePackManager manager;

    public ResourcePackListener(AlcatrazPlugin plugin, ResourcePackManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        UUID uuid = event.getUniqueId();

        // Anti-Spoofing de Bedrock: Si tiene prefijo de Bedrock ('.' o '*'), debe ser una sesión real de Bedrock
        if (BedrockDetector.hasBedrockPrefix(name)) {
            if (!BedrockDetector.isBedrock(uuid, name)) {
                event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&cAcceso denegado: Prefijo de Bedrock detectado.\n&eEse prefijo está reservado exclusivamente para usuarios de Bedrock."
                    )
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        // Validación secundaria Anti-Spoofing
        if (BedrockDetector.hasBedrockPrefix(name)) {
            if (!BedrockDetector.isBedrockPlayer(player)) {
                player.kick(LegacyComponentSerializer.legacyAmpersand().deserialize(
                    "&cAcceso denegado: Prefijo de Bedrock detectado.\n&eEse prefijo está reservado exclusivamente para usuarios de Bedrock."
                ));
                return;
            }
        }

        // 1. Detección y tratamiento de Bedrock Edition
        if (BedrockDetector.isBedrockPlayer(player)) {
            if (manager.isBedrockBypassJava()) {
                // Bedrock protegido al 100%: Nunca se le envía el paquete de Java
                // para evitar congelamiento, cuelgues o desconexiones de Geyser.
                return;
            }
        }

        // 2. Comprobar si el jugador es elegible (permiso / whitelist)
        if (!manager.isEligible(player)) {
            return;
        }

        // 3. Comprobar si ya tiene el paquete instalado (send-only-once)
        if (manager.isSendOnlyOnce() && manager.hasLoadedPack(player.getUniqueId())) {
            // ¡El jugador ya lo tiene! No le enviamos el paquete para que entre instantáneamente sin pantalla de recarga
            return;
        }

        // 4. Enviar paquete tras un breve retardo para asegurar que el cliente ha cargado el mundo
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                manager.sendResourcePack(player, false);
            }
        }, Math.max(10L, (long) manager.getDelayTicks()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        // Si es Bedrock, ignorar estados de pack de Java para evitar falsos kicks
        if (BedrockDetector.isBedrockPlayer(player)) {
            return;
        }

        switch (status) {
            case SUCCESSFULLY_LOADED:
                manager.recordSuccess(player);
                player.sendMessage(color("&8[&bAlcatraz&8] &a¡Paquete de texturas cargado e instalado con éxito! Ya no se te volverá a pedir al entrar."));
                break;

            case DECLINED:
                if (manager.isKickOnDecline() && !player.hasPermission("alcatraz.pack.bypass")) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.kick(LegacyComponentSerializer.legacyAmpersand().deserialize(manager.getDeclineKickMessage()));
                        }
                    });
                } else {
                    player.sendMessage(color("&8[&bAlcatraz&8] &eHas rechazado el paquete de texturas. Puedes cargarlo cuando desees con &b/pack download&e."));
                }
                break;

            case FAILED_DOWNLOAD:
                player.sendMessage(color("&8[&bAlcatraz&8] &cHubo un error al descargar el paquete de texturas. Usa &b/pack download &cpara reintentar."));
                break;

            case ACCEPTED:
                // El cliente aceptó y comenzó la descarga
                break;
        }
    }
}
