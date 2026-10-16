package com.alcatrazm.plugin.halloween;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.*;

/**
 * Comando principal del Módulo Halloween.
 *
 * Subcomandos disponibles:
 *  /halloween staff          — Abre el panel visual de jugadores (solo alcatraz.admin).
 *  /halloween spawn [tipo]   — Coloca una cabeza manual mirando un bloque.
 *  /halloween givehead [...] — Entrega cabeza decorativa a un jugador.
 *  /halloween give [...]     — Entrega calabazas canjeables (tokens) a un jugador.
 *  /halloween scatter [n]    — Inicia la siembra asíncrona en n regiones de 3k.
 *  /halloween clear          — Guarda y limpia el registro de spawns del caché.
 *  /halloween reload         — Recarga configuración y datos YAML del módulo.
 */
public class HalloweenCommand implements CommandExecutor, TabCompleter {

    private final HalloweenManager manager;

    public HalloweenCommand(HalloweenManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /halloween  →  abre el panel staff por defecto para staff, o la tienda del mercader para usuarios
        if (args.length == 0 || args[0].equalsIgnoreCase("staff") || args[0].equalsIgnoreCase("admin") || args[0].equalsIgnoreCase("tienda") || args[0].equalsIgnoreCase("shop") || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color("&cSolo jugadores pueden abrir el panel de Halloween."));
                return true;
            }
            if (!player.hasPermission("alcatraz.admin") && !player.isOp()) {
                player.performCommand("dm open halloween_aldeano");
                return true;
            }
            HalloweenStaffGUI.open(player, manager);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ── /halloween spawn [clasica|espectral] ──────────────────────────────
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color("&cSolo jugadores pueden usar /halloween spawn."));
                    return true;
                }
                if (!player.hasPermission("alcatraz.admin") && !player.isOp()) {
                    player.sendMessage(color("&8[&6Halloween&8] &cSin permiso."));
                    return true;
                }

                String texture = (args.length >= 2 && args[1].equalsIgnoreCase("espectral"))
                    ? HalloweenSkullTextures.SPECTRAL_SOUL
                    : HalloweenSkullTextures.CLASSIC_PUMPKIN;

                Block target = player.getTargetBlockExact(10);
                if (target == null || target.getType() == Material.AIR) {
                    player.sendMessage(color("&cMira directamente un bloque sólido para colocar la cabeza."));
                    return true;
                }

                Location loc = target.getLocation().add(0, 1, 0);
                String   id  = "manual_" + System.currentTimeMillis();
                boolean  ok  = manager.placeHalloweenHead(loc, texture, id);

                if (ok) {
                    player.sendMessage(color("&8[&6Halloween&8] &a¡Cabeza colocada en &e"
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&a!"));
                } else {
                    player.sendMessage(color("&8[&6Halloween&8] &cNo se pudo colocar: el destino no es aire o no tiene suelo sólido."));
                }
            }

            // ── /halloween givehead [jugador] [clasica|espectral] [cantidad] ─────
            case "givehead" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp()) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                Player target = resolvePlayer(sender, args, 1);
                if (target == null) {
                    sender.sendMessage(color("&cJugador no encontrado o desconectado."));
                    return true;
                }

                String texture = (args.length >= 3 && args[2].equalsIgnoreCase("espectral"))
                    ? HalloweenSkullTextures.SPECTRAL_SOUL
                    : HalloweenSkullTextures.CLASSIC_PUMPKIN;
                int amount = parseIntSafe(args, 3, 1);

                ItemStack skull = manager.createSkullItem(texture);
                skull.setAmount(Math.min(amount, 64));
                target.getInventory().addItem(skull);
                sender.sendMessage(color("&8[&6Halloween&8] &aEntregada cabeza decorativa (&e"
                    + amount + "x&a) a &e" + target.getName() + "&a."));
            }

            // ── /halloween give [jugador] [cantidad] ─────────────────────────────
            case "give" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp()) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                Player target = resolvePlayer(sender, args, 1);
                if (target == null) {
                    sender.sendMessage(color("&cJugador no encontrado o desconectado."));
                    return true;
                }

                int amount = parseIntSafe(args, 2, 1);
                target.getInventory().addItem(manager.createHalloweenToken(amount));
                manager.addBalance(target.getUniqueId(), target.getName(), amount);
                sender.sendMessage(color("&8[&6Halloween&8] &a+&e" + amount
                    + " &acalabaza(s) maldita(s) entregadas a &e" + target.getName() + "&a."));
            }

            // ── /halloween trade [jugador] [coste] [comando...] ───────────────────
            case "trade" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp() && !(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(color("&cUso: /halloween trade <jugador> <coste> <comando...>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(color("&cJugador no encontrado o desconectado."));
                    return true;
                }

                int cost = parseIntSafe(args, 2, 0);
                if (cost <= 0) {
                    sender.sendMessage(color("&cEl coste debe ser mayor a 0."));
                    return true;
                }

                int available = manager.countHalloweenTokens(target);
                if (available < cost) {
                    target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    target.sendMessage(color("&8[&6Halloween&8] &c¡No tienes suficientes Calabazas Malditas! &7(Tienes: &e" + available + "&7 / Requiere: &6" + cost + "&7)"));
                    return true;
                }

                boolean success = manager.removeHalloweenTokens(target, cost);
                if (!success) {
                    target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    target.sendMessage(color("&8[&6Halloween&8] &cError al procesar el intercambio. Intenta de nuevo."));
                    return true;
                }

                StringBuilder cmdBuilder = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    cmdBuilder.append(args[i]).append(" ");
                }
                String fullCmd = cmdBuilder.toString().trim()
                    .replace("%player_name%", target.getName())
                    .replace("%player%", target.getName());

                String[] subCmds = fullCmd.split(";;");
                for (String c : subCmds) {
                    String trimmed = c.trim();
                    if (!trimmed.isEmpty()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed);
                    }
                }

                target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                target.sendMessage(color("&8[&6Halloween&8] &a¡Intercambio realizado con éxito! &7(-" + cost + " 🎃 Calabazas Malditas)"));
                sender.sendMessage(color("&8[&6Halloween&8] &aTrade completado para &e" + target.getName() + " &a(-" + cost + " calabazas)."));
            }

            // ── /halloween giveboots [jugador] [rebound|flash] ───────────────────
            case "giveboots" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp() && !(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(color("&cUso: /halloween giveboots <jugador> <rebound|flash>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(color("&cJugador no encontrado: " + args[1]));
                    return true;
                }
                String type = args[2].toLowerCase();
                ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
                ItemMeta meta = boots.getItemMeta();
                if (meta == null) return true;

                if (type.equals("rebound")) {
                    meta.setDisplayName(color("&#e67e22&l✦ ʙᴏᴛᴀꜱ ᴅᴇʟ ꜱᴇɢᴀᴅᴏʀ &6&l🎃"));
                    List<String> lore = new ArrayList<>();
                    lore.add(color("&7Forjadas con magia espectral del inframundo."));
                    lore.add(color("&7Al caer absorben todo el impacto y rebotas como slime."));
                    lore.add("");
                    lore.add(color("&8▪ &eEfecto Especial: &aRebound I (Rebote de Slime)"));
                    lore.add(color("&8▪ &eEncantamientos: &bProtección IV&7, &bCaída de Pluma IV&7, &bIrrompibilidad III&7, &bMending"));
                    lore.add("");
                    lore.add(color("&#d35400&oEdición Exclusiva de Halloween 2026"));
                    meta.setLore(lore);

                    if (meta instanceof ArmorMeta armorMeta) {
                        armorMeta.setTrim(new ArmorTrim(TrimMaterial.COPPER, TrimPattern.SPIRE));
                    }
                    meta.setCustomModelData(1001);

                    meta.addEnchant(Enchantment.PROTECTION, 4, true);
                    meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);

                    Enchantment rebound = Registry.ENCHANTMENT.get(NamespacedKey.fromString("excellentenchants:rebound"));
                    if (rebound != null) {
                        meta.addEnchant(rebound, 1, true);
                    }
                } else if (type.equals("flash")) {
                    meta.setDisplayName(color("&#c0392b&l⚡ ʙᴏᴛᴀꜱ ᴅᴇ ꜰʟᴀꜱʜ &7(Speed Avanzado)"));
                    List<String> lore = new ArrayList<>();
                    lore.add(color("&7Canalizan la energía pura de la Fuerza de la Velocidad."));
                    lore.add(color("&7Otorga aceleración constante y ligereza extrema."));
                    lore.add("");
                    lore.add(color("&8▪ &eEfecto Especial: &cSpeed II Permanente &7(+ Velocidad Base)"));
                    lore.add(color("&8▪ &eEncantamientos: &bProtección IV&7, &bIrrompibilidad III&7, &bMending"));
                    lore.add("");
                    lore.add(color("&#e74c3c&oEdición Especial Relámpago Rojo"));
                    meta.setLore(lore);

                    if (meta instanceof ArmorMeta armorMeta) {
                        armorMeta.setTrim(new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.BOLT));
                    }
                    meta.setCustomModelData(1002);

                    meta.addEnchant(Enchantment.PROTECTION, 4, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);

                    Enchantment speed = Registry.ENCHANTMENT.get(NamespacedKey.fromString("excellentenchants:speed"));
                    if (speed != null) {
                        meta.addEnchant(speed, 2, true);
                    }

                    try {
                        AttributeModifier speedMod = new AttributeModifier(
                            NamespacedKey.fromString("alcatraz:flash_speed"),
                            0.06,
                            AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlotGroup.FEET
                        );
                        meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, speedMod);
                    } catch (Throwable ignored) {}
                } else {
                    sender.sendMessage(color("&cTipo de botas desconocido. Usa: rebound o flash"));
                    return true;
                }

                boots.setItemMeta(meta);
                HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(boots);
                for (ItemStack rem : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), rem);
                }
                target.sendMessage(color("&8[&6Halloween&8] &a¡Has recibido tus " + meta.getDisplayName() + "&a!"));
                sender.sendMessage(color("&8[&6Halloween&8] &aBotas entregadas a &e" + target.getName()));
            }

            // ── /halloween scatter [regiones] [reset] ─────────────────────────────
            case "scatter" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp() && !(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                if (HalloweenCaveSpawner.isRunning()) {
                    sender.sendMessage(color("&8[&6Halloween&8] &cYa hay una siembra activa. Usa &e/halloween stop &cpara pausarla."));
                    return true;
                }
                int regions = 3; // 3 = 49 regiones (14.700 calabazas)
                boolean reset = false;

                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("nuevo")) {
                        reset = true;
                    } else {
                        regions = parseIntSafe(args, 1, 3);
                    }
                }
                if (args.length >= 3) {
                    if (args[2].equalsIgnoreCase("reset") || args[2].equalsIgnoreCase("clear") || args[2].equalsIgnoreCase("nuevo")) {
                        reset = true;
                    }
                }
                HalloweenCaveSpawner.scatter(manager, regions, reset, sender);
            }

            // ── /halloween stop / pause / cancel ──────────────────────────────────
            case "stop", "pause", "cancel" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp() && !(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                boolean stopped = false;
                if (HalloweenCaveSpawner.isRunning()) {
                    HalloweenCaveSpawner.stop();
                    stopped = true;
                    sender.sendMessage(color("&8[&6Halloween&8] &e⏸ Siembra detenida correctamente. Checkpoint guardado en disco."));
                }
                if (manager.isFixerRunning()) {
                    manager.stopFixer();
                    stopped = true;
                    sender.sendMessage(color("&8[&6Halloween&8] &e⏸ Sincronización masiva de texturas detenida."));
                }
                if (stopped) {
                    manager.saveSpawnData();
                } else {
                    sender.sendMessage(color("&8[&6Halloween&8] &cNo hay ninguna tarea de Halloween en ejecución."));
                }
            }

            // ── /halloween stats ──────────────────────────────────────────────────
            case "stats" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp()) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                List<Location> heads = manager.getActiveHeadLocations();
                long surface = heads.stream().filter(l -> l.getY() > 50).count();
                long caves = heads.stream().filter(l -> l.getY() <= 50).count();

                sender.sendMessage(color("&8══════════════════════════════════════════════"));
                sender.sendMessage(color("&6&l🎃 ESTADÍSTICAS DEL EVENTO DE HALLOWEEN"));
                sender.sendMessage(color("&8══════════════════════════════════════════════"));
                sender.sendMessage(color("&7Total calabazas activas: &e" + heads.size()));
                sender.sendMessage(color("&7Superficie (Y > 50): &a" + surface + " &7(" + (heads.isEmpty() ? 0 : surface * 100 / heads.size()) + "%)"));
                sender.sendMessage(color("&7Profundidades (Y <= 50): &b" + caves + " &7(" + (heads.isEmpty() ? 0 : caves * 100 / heads.size()) + "%)"));
                sender.sendMessage(color("&7Jugadores registrados: &e" + manager.getAllBalances().size()));
                sender.sendMessage(color("&8══════════════════════════════════════════════"));
            }

            // ── /halloween near ───────────────────────────────────────────────────
            case "near" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color("&cSolo jugadores en el mundo pueden usar /halloween near."));
                    return true;
                }

                boolean isStaff = player.hasPermission("alcatraz.admin") || player.isOp() || player.hasPermission("alcatraz.halloween.near.bypass");
                if (!isStaff) {
                    long cooldownEnd = manager.getNearCooldown(player.getUniqueId());
                    long now = System.currentTimeMillis();
                    if (now < cooldownEnd) {
                        long remainingMs = cooldownEnd - now;
                        long minutes = (remainingMs / 1000) / 60;
                        long seconds = (remainingMs / 1000) % 60;
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(color("&8[&6Halloween&8] &c¡Debes esperar &e" + minutes + "m " + seconds + "s &cpara volver a rastrear calabazas! ¡Busca manualmente mientras tanto!"));
                        return true;
                    }
                }

                List<Location> heads = manager.getActiveHeadLocations();
                if (heads.isEmpty()) {
                    player.sendMessage(color("&8[&6Halloween&8] &cNo hay calabazas activas registradas en el mundo."));
                    return true;
                }

                Location pLoc = player.getLocation();
                Location closest = null;
                double minDistanceSq = Double.MAX_VALUE;

                for (Location hLoc : heads) {
                    if (hLoc.getWorld() != null && hLoc.getWorld().equals(pLoc.getWorld())) {
                        double distSq = pLoc.distanceSquared(hLoc);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            closest = hLoc;
                        }
                    }
                }

                if (closest == null) {
                    player.sendMessage(color("&8[&6Halloween&8] &cNo se encontraron calabazas en tu dimensión/mundo actual."));
                    return true;
                }

                if (!isStaff) {
                    manager.setNearCooldown(player.getUniqueId(), System.currentTimeMillis() + (15 * 60 * 1000L));
                }

                double dist = Math.sqrt(minDistanceSq);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                player.sendMessage(color("&8[&6Halloween&8] &a🎃 Calabaza más cercana: &e"
                    + closest.getBlockX() + ", " + closest.getBlockY() + ", " + closest.getBlockZ()
                    + " &7(Distancia: &6" + String.format("%.1f", dist) + "m&7 - " + (closest.getY() > 50 ? "&aSuperficie" : "&bCueva") + "&7)"));
                if (!isStaff) {
                    player.sendMessage(color("&8[&6Halloween&8] &7Radar en recarga: podrás volver a usarlo en &e15 minutos&7."));
                }
            }

            // ── /halloween test ───────────────────────────────────────────────────
            case "test" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color("&cSolo jugadores pueden usar /halloween test."));
                    return true;
                }
                if (!player.hasPermission("alcatraz.admin") && !player.isOp()) {
                    player.sendMessage(color("&cSin permiso."));
                    return true;
                }

                Location spawnLoc = null;
                // 1. Bloque exacto al que apunta la vista (hasta 5 bloques)
                Block targetBlock = player.getTargetBlockExact(5);
                if (targetBlock != null && targetBlock.getType().isSolid()) {
                    Block above = targetBlock.getRelative(org.bukkit.block.BlockFace.UP);
                    if (above.getType().isAir() || !above.getType().isSolid()) {
                        spawnLoc = above.getLocation();
                    }
                }

                // 2. Si no apunta a bloque cercano, colocar en el suelo a 2 bloques al frente
                if (spawnLoc == null) {
                    Location eye = player.getEyeLocation();
                    Location front = player.getLocation().add(eye.getDirection().setY(0).normalize().multiply(2));
                    Block b = front.getBlock();
                    while (b.getY() > player.getWorld().getMinHeight() && !b.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                        b = b.getRelative(org.bukkit.block.BlockFace.DOWN);
                    }
                    if (b.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                        spawnLoc = b.getLocation();
                    } else {
                        spawnLoc = player.getLocation().getBlock().getLocation();
                    }
                }

                String id = "test_" + System.currentTimeMillis();
                boolean ok = manager.placeHalloweenHead(spawnLoc, HalloweenSkullTextures.CLASSIC_PUMPKIN, id);
                if (ok) {
                    player.sendMessage(color("&8[&6Halloween&8] &a¡Calabaza de prueba colocada frente a ti en &e"
                        + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ() + "&a! Dale clic derecho para probarla."));
                } else {
                    player.sendMessage(color("&8[&6Halloween&8] &cNo se pudo colocar en &e" + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ()
                        + " &c(espacio ocupado o suelo no sólido)."));
                }
            }

            // ── /halloween fixnear [radio] ─────────────────────────────────────────
            case "fixnear" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color("&cSolo jugadores pueden usar /halloween fixnear."));
                    return true;
                }
                if (!player.hasPermission("alcatraz.admin") && !player.isOp()) {
                    player.sendMessage(color("&cSin permiso."));
                    return true;
                }
                int radius = parseIntSafe(args, 1, 3);
                Chunk center = player.getLocation().getChunk();
                World world = player.getWorld();
                int totalFixed = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int cx = center.getX() + dx;
                        int cz = center.getZ() + dz;
                        if (world.isChunkLoaded(cx, cz)) {
                            totalFixed += manager.checkAndFixChunk(world.getChunkAt(cx, cz), true);
                        }
                    }
                }
                player.sendMessage(color("&8[&6Halloween&8] &aActualizadas forzosamente &e" + totalFixed + " &acalabazas en un radio de &e" + radius + " &achunks."));
            }

            // ── /halloween fixall [loaded|async] ───────────────────────────────────
            case "fixall" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp() && !(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("loaded")) {
                    int totalFixed = 0;
                    for (World w : Bukkit.getWorlds()) {
                        for (Chunk chunk : w.getLoadedChunks()) {
                            totalFixed += manager.checkAndFixChunk(chunk, true);
                        }
                    }
                    sender.sendMessage(color("&8[&6Halloween&8] &aActualizadas &e" + totalFixed + " &acalabazas en todos los chunks cargados actualmente."));
                } else {
                    manager.startBackgroundFixer(sender);
                }
            }

            // ── /halloween clear ──────────────────────────────────────────────────
            case "clear" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp()) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                manager.saveSpawnData();
                sender.sendMessage(color("&8[&6Halloween&8] &aRegistros de spawns guardados y caché limpiado."));
            }

            // ── /halloween reload ─────────────────────────────────────────────────
            case "reload" -> {
                if (!sender.hasPermission("alcatraz.admin") && !sender.isOp()) {
                    sender.sendMessage(color("&cSin permiso."));
                    return true;
                }
                manager.loadConfig();
                manager.loadPlayerData();
                manager.loadSpawnData();
                sender.sendMessage(color("&8[&6Halloween&8] &aConfiguración y datos recargados."));
            }

            default -> sender.sendMessage(color(
                "&8[&6Halloween&8] &eSubcomandos disponibles: &fstaff, spawn, givehead, give, trade, scatter, stop, stats, near, test, fixnear, fixall, clear, reload"
            ));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("alcatraz.admin") && !sender.isOp()) {
            if (args.length == 1) {
                return filterStart(args[0], "near");
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStart(args[0], "staff", "spawn", "givehead", "give", "trade", "giveboots", "scatter", "stop", "stats", "near", "test", "fixnear", "fixall", "clear", "reload");
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("givehead")) {
                return filterStart(args[1], "clasica", "espectral");
            }
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givehead") || args[0].equalsIgnoreCase("trade") || args[0].equalsIgnoreCase("giveboots")) {
                return onlinePlayers(args[1]);
            }
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("givehead")) {
                return filterStart(args[2], "clasica", "espectral");
            }
            if (args[0].equalsIgnoreCase("giveboots")) {
                return filterStart(args[2], "rebound", "flash");
            }
        }
        return Collections.emptyList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Player resolvePlayer(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getPlayer(args[idx]);
        return (sender instanceof Player p) ? p : null;
    }

    private int parseIntSafe(String[] args, int idx, int def) {
        if (args.length <= idx) return def;
        try { return Integer.parseInt(args[idx]); }
        catch (NumberFormatException e) { return def; }
    }

    private List<String> filterStart(String prefix, String... options) {
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.startsWith(prefix.toLowerCase())) result.add(o);
        }
        return result;
    }

    private List<String> onlinePlayers(String prefix) {
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(prefix.toLowerCase())) result.add(p.getName());
        }
        return result;
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
