package com.alcatrazm.plugin.halloween;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manager central del Módulo Evento de Halloween.
 *
 * Responsabilidades:
 *  - Persistencia de datos de jugadores (balance, encontradas, canjeadas) en data/halloween_players.yml
 *  - Persistencia de ubicaciones de cabezas activas en data/halloween_spawns.yml
 *  - Creación de ítems (token canjeable, cabeza decorativa) usando Paper 1.21 PlayerProfile API
 *  - Colocación de cabezas como bloque en el mundo (MAIN THREAD únicamente)
 */
public class HalloweenManager {

    private final AlcatrazPlugin plugin;

    // ── Datos de Jugadores ─────────────────────────────────────────────────
    private final Map<UUID, Integer> playerBalance  = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerFound    = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerRedeemed = new ConcurrentHashMap<>();
    private final Map<UUID, String>  playerNames    = new ConcurrentHashMap<>();

    // ── Cabezas activas en el mundo: "world,x,y,z" → ID interno ───────────
    private final Map<String, String> activeHeads = new ConcurrentHashMap<>();

    // ── Índice rápido de cabezas por chunk: "world:cx:cz" -> Set<"world,x,y,z"> ──
    private final Map<String, Set<String>> chunkHeadIndex = new ConcurrentHashMap<>();
    private final Set<String> fixedHeadKeys = ConcurrentHashMap.newKeySet();

    private void indexHead(String locKey) {
        String[] parts = locKey.split(",");
        if (parts.length == 4) {
            try {
                String world = parts[0];
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                String chunkKey = world + ":" + (x >> 4) + ":" + (z >> 4);
                chunkHeadIndex.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(locKey);
            } catch (NumberFormatException ignored) {}
        }
    }

    private void deindexHead(String locKey) {
        String[] parts = locKey.split(",");
        if (parts.length == 4) {
            try {
                String world = parts[0];
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);
                String chunkKey = world + ":" + (x >> 4) + ":" + (z >> 4);
                Set<String> set = chunkHeadIndex.get(chunkKey);
                if (set != null) {
                    set.remove(locKey);
                    if (set.isEmpty()) {
                        chunkHeadIndex.remove(chunkKey);
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // ── Archivos YAML ──────────────────────────────────────────────────────
    private File               playerDataFile;
    private YamlConfiguration  playerDataConfig;
    private File               spawnsFile;
    private YamlConfiguration  spawnsConfig;

    // ── Configuración ──────────────────────────────────────────────────────
    private boolean enabled;
    private String  worldName;
    private int     headsPerRegion;
    private int     regionSizeBlocks;
    private int     minDistanceBetweenHeads;
    private int     caveWeightPercent;

    // ── PDC key para el ítem canjeable (anti-falsificación / anti-UltimateShop dupe) ──
    private final NamespacedKey keyToken;

    // ── Cooldowns para /halloween near (UUID -> Expiración en ms) ─────────
    private final Map<UUID, Long> nearCooldowns = new ConcurrentHashMap<>();

    public long getNearCooldown(UUID uuid) {
        return nearCooldowns.getOrDefault(uuid, 0L);
    }

    public void setNearCooldown(UUID uuid, long expireTime) {
        nearCooldowns.put(uuid, expireTime);
    }

    public HalloweenManager(AlcatrazPlugin plugin) {
        this.plugin   = plugin;
        this.keyToken = new NamespacedKey(plugin, "halloween_pumpkin");
        loadConfig();
        loadPlayerData();
        loadSpawnData();

        // Auto-sincronización instantánea de chunks ya cargados al encender o recargar con PlugMan
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!enabled) return;
            int fixed = 0;
            for (World w : Bukkit.getWorlds()) {
                for (Chunk c : w.getLoadedChunks()) {
                    fixed += checkAndFixChunk(c, false);
                }
            }
            if (fixed > 0) {
                plugin.getLogger().info("[Halloween] Auto-sincronizadas " + fixed + " calabazas en chunks activos.");
            }
        }, 20L);
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONFIGURACIÓN
    // ══════════════════════════════════════════════════════════════════════

    public void loadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("halloween");
        if (sec == null) {
            plugin.getLogger().warning("[Halloween] Sección 'halloween' no encontrada en config.yml. Módulo desactivado.");
            this.enabled = false;
            return;
        }
        this.enabled                 = sec.getBoolean("enabled", false);
        this.worldName               = sec.getString("world", "world");
        this.headsPerRegion          = sec.getInt("grid.heads-per-region", 600);
        this.regionSizeBlocks        = sec.getInt("grid.region-size", 3000);
        this.minDistanceBetweenHeads = sec.getInt("grid.min-distance-between-heads", 12);
        this.caveWeightPercent       = sec.getInt("grid.weights.caves", 45);
    }

    // ══════════════════════════════════════════════════════════════════════
    // PERSISTENCIA — DATOS DE JUGADORES
    // ══════════════════════════════════════════════════════════════════════

    public void loadPlayerData() {
        playerBalance.clear();
        playerFound.clear();
        playerRedeemed.clear();
        playerNames.clear();

        playerDataFile = new File(plugin.getDataFolder(), "data/halloween_players.yml");
        playerDataFile.getParentFile().mkdirs();
        if (!playerDataFile.exists()) {
            try { playerDataFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().warning("[Halloween] No se pudo crear halloween_players.yml: " + e.getMessage());
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        for (String key : playerDataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                playerBalance .put(uuid, playerDataConfig.getInt(key + ".balance",  0));
                playerFound   .put(uuid, playerDataConfig.getInt(key + ".found",    0));
                playerRedeemed.put(uuid, playerDataConfig.getInt(key + ".redeemed", 0));
                playerNames   .put(uuid, playerDataConfig.getString(key + ".name", "Desconocido"));
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("[Halloween] Cargados " + playerBalance.size() + " jugadores.");
    }

    public void savePlayerData() {
        if (playerDataConfig == null || playerDataFile == null) return;

        // Limpiar y reescribir completamente
        for (String k : new ArrayList<>(playerDataConfig.getKeys(false))) {
            playerDataConfig.set(k, null);
        }
        for (UUID uuid : playerBalance.keySet()) {
            String k = uuid.toString();
            playerDataConfig.set(k + ".balance",  playerBalance .getOrDefault(uuid, 0));
            playerDataConfig.set(k + ".found",    playerFound   .getOrDefault(uuid, 0));
            playerDataConfig.set(k + ".redeemed", playerRedeemed.getOrDefault(uuid, 0));
            playerDataConfig.set(k + ".name",     playerNames   .getOrDefault(uuid, "Desconocido"));
        }
        try { playerDataConfig.save(playerDataFile); }
        catch (IOException e) {
            plugin.getLogger().warning("[Halloween] Error guardando halloween_players.yml: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PERSISTENCIA — UBICACIONES DE CABEZAS
    // ══════════════════════════════════════════════════════════════════════

    public void loadSpawnData() {
        activeHeads.clear();
        chunkHeadIndex.clear();
        fixedHeadKeys.clear();

        spawnsFile = new File(plugin.getDataFolder(), "data/halloween_spawns.yml");
        if (!spawnsFile.exists()) {
            File rootSpawns = new File(plugin.getDataFolder(), "halloween_spawns.yml");
            if (rootSpawns.exists()) {
                spawnsFile = rootSpawns;
            }
        }
        spawnsFile.getParentFile().mkdirs();
        if (!spawnsFile.exists()) {
            try { spawnsFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().warning("[Halloween] No se pudo crear halloween_spawns.yml: " + e.getMessage());
            }
        }
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);

        for (String key : spawnsConfig.getKeys(false)) {
            String id = spawnsConfig.getString(key + ".id", key);
            activeHeads.put(key, id);
            indexHead(key);
        }
        plugin.getLogger().info("[Halloween] Cargadas " + activeHeads.size() + " cabezas activas indexadas en " + chunkHeadIndex.size() + " chunks.");
    }

    public synchronized void saveSpawnData() {
        if (spawnsFile == null) return;
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, String> e : activeHeads.entrySet()) {
            cfg.set(e.getKey() + ".id", e.getValue());
        }
        try {
            cfg.save(spawnsFile);
            spawnsConfig = cfg;
        } catch (IOException e) {
            plugin.getLogger().warning("[Halloween] Error guardando halloween_spawns.yml: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // BALANCE DE JUGADORES
    // ══════════════════════════════════════════════════════════════════════

    public int    getBalance(UUID uuid)       { return playerBalance .getOrDefault(uuid, 0); }
    public int    getTotalFound(UUID uuid)    { return playerFound   .getOrDefault(uuid, 0); }
    public int    getTotalRedeemed(UUID uuid) { return playerRedeemed.getOrDefault(uuid, 0); }
    public String getSavedName(UUID uuid)     { return playerNames   .getOrDefault(uuid, "Desconocido"); }

    public void addBalance(UUID uuid, String name, int amount) {
        playerBalance .merge(uuid, amount, Integer::sum);
        playerFound   .merge(uuid, amount, Integer::sum);
        playerNames   .put(uuid, name);
    }

    public boolean deductBalance(UUID uuid, int amount) {
        int bal = playerBalance.getOrDefault(uuid, 0);
        if (bal < amount) return false;
        playerBalance .put(uuid, bal - amount);
        playerRedeemed.merge(uuid, amount, Integer::sum);
        return true;
    }

    public void setBalance(UUID uuid, int amount) {
        playerBalance.put(uuid, Math.max(0, amount));
    }

    // ══════════════════════════════════════════════════════════════════════
    // REGISTRO DE CABEZAS EN EL MUNDO
    // ══════════════════════════════════════════════════════════════════════

    public boolean isHalloweenHead(Location loc) {
        return activeHeads.containsKey(locKey(loc));
    }

    public String removeHead(Location loc) {
        String key = locKey(loc);
        deindexHead(key);
        fixedHeadKeys.remove(key);
        return activeHeads.remove(key);
    }

    public void registerHead(Location loc, String id) {
        String key = locKey(loc);
        activeHeads.put(key, id);
        indexHead(key);
    }

    public int getActiveHeadCount() {
        return activeHeads.size();
    }

    public void purgeAllSpawns() {
        activeHeads.clear();
        chunkHeadIndex.clear();
        fixedHeadKeys.clear();
        saveSpawnData();
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // ══════════════════════════════════════════════════════════════════════
    // CREACIÓN DE ÍTEM CANJEABLE — "Calabaza Maldita"
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Crea el ítem "Calabaza Maldita" (moneda del evento).
     * Lleva PDC tag "halloween_pumpkin" para:
     *  - Distinguirlo de heads normales en el inventario.
     *  - Prevenir que UltimateShop lo acepte como venta de PLAYER_HEAD genérico.
     */
    public ItemStack createHalloweenToken(int amount) {
        ItemStack item = createSkullItem(HalloweenSkullTextures.CLASSIC_PUMPKIN);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color("&6&l🎃 Calabaza Maldita"));
        meta.setLore(Arrays.asList(
            color("&7Artefacto espectral del Evento de Halloween."),
            color("&7Llévala al &e&lMercader del Inframundo&7 para"),
            color("&7intercambiarla por recompensas especiales."),
            color(""),
            color("&6/halloween shop &7para ver las recompensas.")
        ));
        meta.getPersistentDataContainer().set(keyToken, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        item.setAmount(Math.min(Math.max(amount, 1), 64));
        return item;
    }

    public boolean isHalloweenToken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyToken, PersistentDataType.BYTE);
    }

    public int countHalloweenTokens(Player player) {
        if (player == null) return 0;
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isHalloweenToken(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public boolean removeHalloweenTokens(Player player, int amount) {
        if (player == null || amount <= 0) return false;
        if (countHalloweenTokens(player) < amount) return false;

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isHalloweenToken(item)) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    player.getInventory().setItem(i, null);
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                    break;
                }
            }
        }
        player.updateInventory();
        deductBalance(player.getUniqueId(), amount);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // CREACIÓN DE SKULL ITEM CON TEXTURA PERSONALIZADA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Crea un ItemStack PLAYER_HEAD con textura Base64 personalizada.
     *
     * Usa exclusivamente la PlayerProfile API de Paper 1.21
     * (Server.createProfile() + PlayerTextures.setSkin(URL)).
     * NO usa el deprecated SkullMeta.setOwner(String) ni setOwningPlayer(OfflinePlayer).
     */
    public ItemStack createSkullItem(String base64) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        boolean isClassic = base64.equals(HalloweenSkullTextures.CLASSIC_PUMPKIN);
        UUID u = isClassic ? HalloweenSkullTextures.MHF_PUMPKIN_UUID : HalloweenSkullTextures.SPECTRAL_SOUL_UUID;
        String name = isClassic ? HalloweenSkullTextures.MHF_PUMPKIN_NAME : HalloweenSkullTextures.SPECTRAL_SOUL_NAME;
        PlayerProfile profile = buildProfile(base64, name, u);
        meta.setPlayerProfile(profile);
        skull.setItemMeta(meta);
        return skull;
    }

    public PlayerProfile buildProfile(String base64, String displayName, UUID uuid) {
        UUID profileId = (uuid != null) ? uuid : UUID.randomUUID();
        PlayerProfile profile = Bukkit.createProfile(profileId, displayName);
        try {
            String urlStr = null;
            if (base64 != null && base64.contains("b162d72d8d576d9bbb81c9aedc690617416dedc95ccf17f794580ca674942a65")) {
                urlStr = HalloweenSkullTextures.CLASSIC_PUMPKIN_URL;
            } else if (base64 != null && base64.contains("30c8745d404de6c2e9650fc9217f17236de50d54ee8e0ae949182f2ae9ead618")) {
                urlStr = HalloweenSkullTextures.SPECTRAL_SOUL_URL;
            } else if (base64 != null) {
                String decoded = new String(Base64.getDecoder().decode(base64), java.nio.charset.StandardCharsets.UTF_8);
                int uIdx = decoded.indexOf("http");
                if (uIdx != -1) {
                    int end = decoded.indexOf("\"", uIdx);
                    if (end != -1) {
                        urlStr = decoded.substring(uIdx, end);
                    }
                }
            }

            if (urlStr != null) {
                if (urlStr.startsWith("http://")) {
                    urlStr = "https://" + urlStr.substring(7);
                }
                org.bukkit.profile.PlayerTextures pt = profile.getTextures();
                pt.setSkin(new java.net.URI(urlStr).toURL());
                profile.setTextures(pt);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Halloween] Error resolviendo PlayerTextures skin: " + e.getMessage());
        }

        if (base64 != null && !base64.isEmpty()) {
            profile.setProperty(new ProfileProperty("textures", base64));
        }
        return profile;
    }

    public PlayerProfile buildProfile(String base64, String displayName) {
        return buildProfile(base64, displayName, null);
    }

    // ══════════════════════════════════════════════════════════════════════
    // COLOCACIÓN DE CABEZA COMO BLOQUE EN EL MUNDO
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Coloca una cabeza Halloween como bloque PLAYER_HEAD en la ubicación dada y la registra.
     *
     * ⚠️ DEBE llamarse SIEMPRE en el MAIN THREAD.
     *    La colocación de bloques en Paper es operación sincrónica.
     *    Llamar desde async causará IllegalStateException.
     *
     * @param loc    Ubicación destino (debe ser aire o bloque replaceable).
     * @param base64 Textura Base64 de la calabaza.
     * @param id     Identificador único para el registro interno.
     * @return true si la cabeza fue colocada exitosamente.
     */
    public boolean placeHalloweenHead(Location loc, String base64, String id) {
        Block block = loc.getBlock();

        // Solo colocar en bloques de aire o reemplazables
        if (!block.getType().isAir()
                && block.getType() != Material.SNOW
                && block.getType() != Material.SHORT_GRASS
                && block.getType() != Material.TALL_GRASS
                && block.getType() != Material.FERN
                && block.getType() != Material.LARGE_FERN
                && block.getType() != Material.DEAD_BUSH) {
            return false;
        }

        // Asegurarse de que el bloque de abajo sea sólido y no líquido
        Block below = loc.clone().subtract(0, 1, 0).getBlock();
        if (!below.getType().isSolid() || below.isLiquid()) return false;

        // 1. Asignar el tipo de bloque PLAYER_HEAD
        block.setType(Material.PLAYER_HEAD, false);

        // 2. Asignar rotación en el BlockData del bloque ANTES de obtener el Skull state
        if (block.getBlockData() instanceof org.bukkit.block.data.Rotatable rotatable) {
            org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.NORTH_NORTH_EAST,
                org.bukkit.block.BlockFace.NORTH_EAST, org.bukkit.block.BlockFace.EAST_NORTH_EAST,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.EAST_SOUTH_EAST,
                org.bukkit.block.BlockFace.SOUTH_EAST, org.bukkit.block.BlockFace.SOUTH_SOUTH_EAST,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.SOUTH_SOUTH_WEST,
                org.bukkit.block.BlockFace.SOUTH_WEST, org.bukkit.block.BlockFace.WEST_SOUTH_WEST,
                org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.WEST_NORTH_WEST,
                org.bukkit.block.BlockFace.NORTH_WEST, org.bukkit.block.BlockFace.NORTH_NORTH_WEST
            };
            rotatable.setRotation(faces[new Random().nextInt(faces.length)]);
            block.setBlockData(rotatable, false);
        }

        // 3. Obtener el estado Skull y aplicar perfil
        if (!(block.getState() instanceof Skull skull)) {
            plugin.getLogger().warning("[Halloween] setType(PLAYER_HEAD) no produjo un Skull válido en " + locKey(loc));
            block.setType(Material.AIR, false);
            return false;
        }

        boolean isClassic = base64.contains("b162d72d8d576d9bbb81c9aedc690617416dedc95ccf17f794580ca674942a65")
            || base64.equals(HalloweenSkullTextures.CLASSIC_PUMPKIN);
        UUID u = isClassic ? HalloweenSkullTextures.MHF_PUMPKIN_UUID : HalloweenSkullTextures.SPECTRAL_SOUL_UUID;
        String name = isClassic ? HalloweenSkullTextures.MHF_PUMPKIN_NAME : HalloweenSkullTextures.SPECTRAL_SOUL_NAME;
        String texture = isClassic ? HalloweenSkullTextures.CLASSIC_PUMPKIN : HalloweenSkullTextures.SPECTRAL_SOUL;

        PlayerProfile profile = buildProfile(texture, name, u);
        skull.setPlayerProfile(profile);

        // 4. Actualizar TileEntity en el chunk
        skull.update(true, false);

        registerHead(loc, id);
        fixedHeadKeys.add(locKey(loc));
        return true;
    }

    /**
     * Aplica el PlayerProfile a un bloque PLAYER_HEAD existente sin alterar su rotación.
     */
    public boolean applyProfileToBlock(Block block, String base64) {
        if (block == null || block.getType() != Material.PLAYER_HEAD) return false;
        if (!(block.getState() instanceof Skull skull)) return false;

        boolean isClassic = base64.contains("b162d72d8d576d9bbb81c9aedc690617416dedc95ccf17f794580ca674942a65")
            || base64.equals(HalloweenSkullTextures.CLASSIC_PUMPKIN);
        UUID u = isClassic ? HalloweenSkullTextures.MHF_PUMPKIN_UUID : HalloweenSkullTextures.SPECTRAL_SOUL_UUID;
        String name = isClassic ? HalloweenSkullTextures.MHF_PUMPKIN_NAME : HalloweenSkullTextures.SPECTRAL_SOUL_NAME;
        String texture = isClassic ? HalloweenSkullTextures.CLASSIC_PUMPKIN : HalloweenSkullTextures.SPECTRAL_SOUL;

        PlayerProfile profile = buildProfile(texture, name, u);
        skull.setPlayerProfile(profile);
        skull.update(true, false);
        return true;
    }

    /**
     * Revisa y actualiza las texturas de las cabezas registradas en un chunk.
     * @param chunk Chunk a revisar.
     * @param force Si es true, fuerza la actualización aunque ya esté en fixedHeadKeys.
     */
    public int checkAndFixChunk(Chunk chunk, boolean force) {
        if (chunk == null) return 0;
        String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        Set<String> headKeys = chunkHeadIndex.get(chunkKey);
        if (headKeys == null || headKeys.isEmpty()) return 0;

        int fixed = 0;
        World world = chunk.getWorld();
        for (String key : headKeys) {
            if (!force && fixedHeadKeys.contains(key)) continue;

            String[] parts = key.split(",");
            if (parts.length != 4) continue;
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);

                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.PLAYER_HEAD) {
                    String texture = (y <= 50)
                        ? HalloweenSkullTextures.SPECTRAL_SOUL
                        : HalloweenSkullTextures.CLASSIC_PUMPKIN;
                    if (applyProfileToBlock(block, texture)) {
                        fixed++;
                    }
                }
                fixedHeadKeys.add(key);
            } catch (Exception ignored) {}
        }
        return fixed;
    }

    public int checkAndFixChunk(Chunk chunk) {
        return checkAndFixChunk(chunk, false);
    }

    // ── Actualizador Masivo en Segundo Plano ───────────────────────────────
    private BukkitTask fixerTask;
    private final AtomicBoolean fixerRunning = new AtomicBoolean(false);

    public boolean isFixerRunning() {
        return fixerRunning.get() && fixerTask != null && !fixerTask.isCancelled();
    }

    public void stopFixer() {
        if (fixerTask != null) {
            fixerTask.cancel();
            fixerTask = null;
        }
        fixerRunning.set(false);
    }

    public void startBackgroundFixer(org.bukkit.command.CommandSender sender) {
        if (isFixerRunning()) {
            sender.sendMessage(color("&8[&6Halloween&8] &cYa hay una sincronización masiva en ejecución. Usa &e/halloween stop &cpara cancelarla."));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(color("&8[&6Halloween&8] &cMundo '&e" + worldName + "&c' no encontrado."));
            return;
        }

        List<String> chunkKeys = new ArrayList<>(chunkHeadIndex.keySet());
        int total = chunkKeys.size();
        if (total == 0) {
            sender.sendMessage(color("&8[&6Halloween&8] &cNo hay chunks con calabazas registradas."));
            return;
        }

        fixerRunning.set(true);
        AtomicInteger index = new AtomicInteger(0);
        AtomicInteger totalFixed = new AtomicInteger(0);

        sender.sendMessage(color("&8[&6Halloween&8] &aIniciando sincronización progresiva de texturas para &e" + total + " &achunks..."));

        fixerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!fixerRunning.get()) {
                if (fixerTask != null) fixerTask.cancel();
                return;
            }

            long freeMem = Runtime.getRuntime().freeMemory();
            long maxMem = Runtime.getRuntime().maxMemory();
            long totalMem = Runtime.getRuntime().totalMemory();
            if (maxMem - (totalMem - freeMem) < 400 * 1024 * 1024L) {
                System.gc();
                return;
            }

            int batchSize = 3;
            for (int b = 0; b < batchSize; b++) {
                int i = index.getAndIncrement();
                if (i >= total) {
                    stopFixer();
                    sender.sendMessage(color("&8[&6Halloween&8] &a✔ ¡Sincronización masiva completada! &e" + totalFixed.get() + " &acalabazas sincronizadas."));
                    saveSpawnData();
                    return;
                }

                String cKey = chunkKeys.get(i);
                String[] p = cKey.split(":");
                if (p.length < 3) continue;

                try {
                    int cx = Integer.parseInt(p[1]);
                    int cz = Integer.parseInt(p[2]);
                    boolean wasLoaded = world.isChunkLoaded(cx, cz);

                    world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (chunk != null && chunk.isLoaded()) {
                                int f = checkAndFixChunk(chunk, true);
                                totalFixed.addAndGet(f);
                                if (!wasLoaded) {
                                    world.unloadChunkRequest(cx, cz);
                                }
                            }
                        });
                    });
                } catch (Exception ignored) {}
            }

            int cur = index.get();
            if (cur > 0 && cur % 1500 == 0) {
                int pct = cur * 100 / total;
                sender.sendMessage(color("&8[&6Halloween&8] &7Sincronizando skins: &e" + cur + "/" + total + " &7chunks (&e" + pct + "%&7)..."));
            }
        }, 10L, 2L);
    }

    // ══════════════════════════════════════════════════════════════════════
    // GETTERS
    // ══════════════════════════════════════════════════════════════════════

    public boolean isEnabled()            { return enabled; }
    public String  getWorldName()         { return worldName; }
    public int     getHeadsPerRegion()    { return headsPerRegion; }
    public int     getRegionSizeBlocks()  { return regionSizeBlocks; }
    public int     getMinDistance()       { return minDistanceBetweenHeads; }
    public int     getCaveWeightPercent() { return caveWeightPercent; }
    public AlcatrazPlugin getPlugin()     { return plugin; }
    public NamespacedKey  getTokenKey()   { return keyToken; }

    public Map<UUID, Integer> getAllBalances() { return Collections.unmodifiableMap(playerBalance); }
    public Map<UUID, String>  getAllNames()    { return Collections.unmodifiableMap(playerNames); }

    public List<Location> getActiveHeadLocations() {
        List<Location> list = new ArrayList<>();
        for (String key : activeHeads.keySet()) {
            String[] parts = key.split(",");
            if (parts.length == 4) {
                World w = Bukkit.getWorld(parts[0]);
                if (w != null) {
                    try {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);
                        list.add(new Location(w, x, y, z));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return list;
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
