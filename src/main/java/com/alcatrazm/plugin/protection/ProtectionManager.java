package com.alcatrazm.plugin.protection;

import com.alcatrazm.plugin.AlcatrazPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ProtectionManager {

    private final AlcatrazPlugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final GriefPreventionHook griefPreventionHook;
    private final File file;
    private YamlConfiguration config;

    public final NamespacedKey KEY_PS_BLOCK;
    public final NamespacedKey KEY_PS_TYPE;
    public final NamespacedKey KEY_PS_TEMP;

    private final Map<String, Claim> claims = new HashMap<>(); // ID -> Claim
    private BukkitTask expirationTask;

    private boolean enabled;
    private int temporaryDurationDays;
    private int maxClaimsPerPlayer;
    private boolean fullHeightCoverage;
    private boolean showEntryExitMessages;

    public ProtectionManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.worldGuardHook = new WorldGuardHook();
        this.griefPreventionHook = new GriefPreventionHook();
        this.KEY_PS_BLOCK = new NamespacedKey(plugin, "ps_block");
        this.KEY_PS_TYPE = new NamespacedKey(plugin, "ps_type");
        this.KEY_PS_TEMP = new NamespacedKey(plugin, "ps_temp");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "protections.yml");

        loadConfig();
        loadClaims();
        startExpirationTask();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("protections.enabled", true);
        this.temporaryDurationDays = plugin.getConfig().getInt("protections.temporary-duration-days", 7);
        this.maxClaimsPerPlayer = plugin.getConfig().getInt("protections.max-claims-per-player", 5);
        this.fullHeightCoverage = plugin.getConfig().getBoolean("protections.full-height-coverage", true);
        this.showEntryExitMessages = plugin.getConfig().getBoolean("protections.show-entry-exit-messages", true);
    }

    public void reload() {
        loadConfig();
        loadClaims();
        startExpirationTask();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public ItemStack createProtectionBlockItem(ProtectionBlockType type, boolean temporary, int amount) {
        ItemStack item = new ItemStack(type.getMaterial(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String title = temporary 
                    ? color("&8[&c⏳ Mena Temporal&8] " + type.getDisplayName() + " &7(" + type.getRadiusFormatted() + ")")
                    : color("&8[&6✦ Mena Permanente&8] " + type.getDisplayName() + " &7(" + type.getRadiusFormatted() + ")");
            meta.setDisplayName(title);

            List<String> lore = new ArrayList<>();
            lore.add(color("&7¡Piedra de Protección oficial del servidor!"));
            lore.add(color(""));
            lore.add(color("&8▪ &7Tipo: " + (temporary ? "&cTemporal (&e" + temporaryDurationDays + " días&c)" : "&aPermanente")));
            lore.add(color("&8▪ &7Radio horizontal: &e" + type.getRadiusFormatted() + " &7alrededor"));
            lore.add(color("&8▪ &7Área protegida: &b" + (type.getRadius() * 2 + 1) + "x" + (type.getRadius() * 2 + 1) + " bloques"));
            lore.add(color("&8▪ &7Cobertura vertical: &aColumna Total (Bedrock a Cielo)"));
            lore.add(color(""));
            lore.add(color("&eColócala en el suelo para activar tu protección."));
            if (temporary) {
                lore.add(color("&cNota: Expirará automáticamente en 1 semana tras ser colocada."));
            }
            meta.setLore(lore);

            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_PS_BLOCK, PersistentDataType.STRING, "true");
            pdc.set(KEY_PS_TYPE, PersistentDataType.STRING, type.name());
            pdc.set(KEY_PS_TEMP, PersistentDataType.STRING, temporary ? "true" : "false");

            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isProtectionBlockItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(KEY_PS_BLOCK, PersistentDataType.STRING);
    }

    public ProtectionBlockType getBlockTypeOfItem(ItemStack item) {
        if (!isProtectionBlockItem(item)) return null;
        String typeStr = item.getItemMeta().getPersistentDataContainer().get(KEY_PS_TYPE, PersistentDataType.STRING);
        if (typeStr == null) return null;
        try {
            return ProtectionBlockType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isTemporaryItem(ItemStack item) {
        if (!isProtectionBlockItem(item)) return false;
        String tempStr = item.getItemMeta().getPersistentDataContainer().get(KEY_PS_TEMP, PersistentDataType.STRING);
        return "true".equalsIgnoreCase(tempStr);
    }

    public void startExpirationTask() {
        if (expirationTask != null && !expirationTask.isCancelled()) {
            expirationTask.cancel();
        }
        expirationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpirations, 200L, 1200L);
    }

    private void checkExpirations() {
        List<String> toRemove = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Claim claim : claims.values()) {
            if (claim.getType() == ClaimType.TEMPORARY && claim.getExpirationTimeMillis() > 0 && now >= claim.getExpirationTimeMillis()) {
                toRemove.add(claim.getId());

                World w = Bukkit.getWorld(claim.getWorldName());
                if (w != null) {
                    Location blockLoc = new Location(w, claim.getBlockX(), claim.getBlockY(), claim.getBlockZ());
                    blockLoc.getBlock().setType(Material.STONE);
                    w.spawnParticle(Particle.SMOKE, blockLoc.add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);

                    // Limpiar PDC del chunk
                    Chunk chunk = blockLoc.getChunk();
                    NamespacedKey key = new NamespacedKey(plugin, "ps_claim_" + claim.getBlockX() + "_" + claim.getBlockY() + "_" + claim.getBlockZ());
                    chunk.getPersistentDataContainer().remove(key);
                }

                if (claim.getOwnerUUID() != null) {
                    Player p = Bukkit.getPlayer(claim.getOwnerUUID());
                    if (p != null && p.isOnline()) {
                        p.sendMessage(color("&8[&c⏳ Protección Expirada&8] &cTu protección temporal &e" + claim.getName() + " &7(" + claim.getRadius() + "b) &cha expirado tras 1 semana y el terreno ha sido liberado."));
                    }
                }
            }
        }

        for (String id : toRemove) {
            claims.remove(id);
        }
        if (!toRemove.isEmpty()) {
            saveClaims();
        }
    }

    public void loadClaims() {
        claims.clear();
        if (!file.exists()) {
            try {
                if (file.getParentFile() != null && !file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear protections.yml: " + e.getMessage());
            }
            return;
        }

        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("claims");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String name = section.getString(id + ".name", id);
            String ownerStr = section.getString(id + ".owner-uuid", null);
            UUID ownerUUID = ownerStr != null ? UUID.fromString(ownerStr) : null;
            String ownerName = section.getString(id + ".owner-name", "Admin");
            String typeStr = section.getString(id + ".type", section.getString(id + ".claim-type", "NORMAL"));
            ClaimType type = ClaimType.fromString(typeStr);
            String blockTypeStr = section.getString(id + ".block-type", "DIAMOND_150");
            ProtectionBlockType blockType = ProtectionBlockType.fromString(blockTypeStr);
            if (blockType == null) blockType = ProtectionBlockType.DIAMOND_150;
            long expiration = section.getLong(id + ".expiration-time", 0L);
            String world = section.getString(id + ".world", "world");
            int x = section.getInt(id + ".x", 0);
            int y = section.getInt(id + ".y", 64);
            int z = section.getInt(id + ".z", 0);
            int radius = section.getInt(id + ".radius", blockType.getRadius());
            boolean trustAll = section.getBoolean(id + ".trust-all", section.getBoolean(id + ".is-public", false));

            Claim claim = new Claim(id, name, ownerUUID, ownerName, type, blockType, expiration, world, x, y, z, radius);
            claim.setTrustAll(trustAll);

            List<String> trusted = section.getStringList(id + ".trusted");
            for (String t : trusted) {
                try {
                    claim.addTrust(UUID.fromString(t));
                } catch (IllegalArgumentException ignored) {}
            }

            ConfigurationSection flagSec = section.getConfigurationSection(id + ".flags");
            if (flagSec != null) {
                for (String fName : flagSec.getKeys(false)) {
                    ClaimFlag flag = ClaimFlag.fromString(fName);
                    if (flag != null) {
                        claim.setFlag(flag, flagSec.getBoolean(fName));
                    }
                }
            }

            claims.put(id, claim);
        }
    }

    public void saveClaims() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set("claims", null);

        for (Claim claim : claims.values()) {
            String path = "claims." + claim.getId();
            config.set(path + ".name", claim.getName());
            config.set(path + ".owner-uuid", claim.getOwnerUUID() != null ? claim.getOwnerUUID().toString() : null);
            config.set(path + ".owner-name", claim.getOwnerName());
            config.set(path + ".type", claim.getType().name());
            config.set(path + ".block-type", claim.getBlockType() != null ? claim.getBlockType().name() : "DIAMOND_150");
            config.set(path + ".expiration-time", claim.getExpirationTimeMillis());
            config.set(path + ".world", claim.getWorldName());
            config.set(path + ".x", claim.getBlockX());
            config.set(path + ".y", claim.getBlockY());
            config.set(path + ".z", claim.getBlockZ());
            config.set(path + ".radius", claim.getRadius());
            config.set(path + ".trust-all", claim.isTrustAll());

            List<String> trusted = new ArrayList<>();
            for (UUID u : claim.getTrustedPlayers()) {
                trusted.add(u.toString());
            }
            config.set(path + ".trusted", trusted);

            for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
                config.set(path + ".flags." + entry.getKey().name(), entry.getValue());
            }

            // Actualizar PDC Híbrido en el Chunk del Mundo
            saveClaimPDC(claim);
        }

        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar protections.yml: " + e.getMessage());
        }
    }

    private void saveClaimPDC(Claim claim) {
        World world = Bukkit.getWorld(claim.getWorldName());
        if (world == null) return;
        int cx = claim.getBlockX() >> 4;
        int cz = claim.getBlockZ() >> 4;
        if (!world.isChunkLoaded(cx, cz)) {
            return; // Evita forzar la carga de chunks descargados a memoria
        }
        Chunk chunk = world.getChunkAt(cx, cz);

        NamespacedKey key = new NamespacedKey(plugin, "ps_claim_" + claim.getBlockX() + "_" + claim.getBlockY() + "_" + claim.getBlockZ());

        StringBuilder trustedSb = new StringBuilder();
        for (UUID u : claim.getTrustedPlayers()) {
            if (trustedSb.length() > 0) trustedSb.append(",");
            trustedSb.append(u.toString());
        }

        StringBuilder flagsSb = new StringBuilder();
        for (Map.Entry<ClaimFlag, Boolean> entry : claim.getFlags().entrySet()) {
            if (flagsSb.length() > 0) flagsSb.append(",");
            flagsSb.append(entry.getKey().name()).append("=").append(entry.getValue());
        }

        String raw = claim.getId() + ";;" + claim.getName() + ";;" + (claim.getOwnerUUID() != null ? claim.getOwnerUUID() : "null") + ";;"
                + claim.getOwnerName() + ";;" + (claim.getBlockType() != null ? claim.getBlockType().name() : "DIAMOND_150") + ";;"
                + claim.getType().name() + ";;" + claim.getExpirationTimeMillis() + ";;" + claim.getRadius() + ";;"
                + claim.isTrustAll() + ";;" + trustedSb + ";;" + flagsSb;

        chunk.getPersistentDataContainer().set(key, PersistentDataType.STRING, raw);
    }

    public Claim getClaimAt(Location loc) {
        if (loc == null) return null;
        for (Claim claim : claims.values()) {
            if (claim.contains(loc)) {
                return claim;
            }
        }

        // Auto-recuperación desde el PDC del Chunk si el archivo YML fue borrado
        Claim recovered = recoverClaimAtFromChunkPDC(loc);
        if (recovered != null) {
            claims.put(recovered.getId(), recovered);
            return recovered;
        }

        return null;
    }

    public Claim getClaimByBlockLocation(Location loc) {
        if (loc == null) return null;
        for (Claim claim : claims.values()) {
            if (claim.isProtectionBlockLocation(loc)) {
                return claim;
            }
        }

        Claim recovered = recoverClaimByBlockLocationFromChunkPDC(loc);
        if (recovered != null) {
            claims.put(recovered.getId(), recovered);
            saveClaims();
            return recovered;
        }

        return null;
    }

    public boolean isProtectionBlock(Location loc) {
        if (loc == null) return false;
        return getClaimByBlockLocation(loc) != null;
    }

    private Claim recoverClaimAtFromChunkPDC(Location loc) {
        if (loc.getWorld() == null) return null;
        Chunk chunk = loc.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getKey().startsWith("ps_claim_")) {
                Claim claim = parseClaimFromPDC(chunk, key, loc.getWorld().getName());
                if (claim != null && claim.contains(loc)) {
                    return claim;
                }
            }
        }
        return null;
    }

    private Claim recoverClaimByBlockLocationFromChunkPDC(Location loc) {
        if (loc.getWorld() == null) return null;
        Chunk chunk = loc.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        NamespacedKey exactKey = new NamespacedKey(plugin, "ps_claim_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ());
        if (pdc.has(exactKey, PersistentDataType.STRING)) {
            Claim claim = parseClaimFromPDC(chunk, exactKey, loc.getWorld().getName());
            if (claim != null && claim.isProtectionBlockLocation(loc)) {
                return claim;
            }
        }
        return null;
    }

    private Claim parseClaimFromPDC(Chunk chunk, NamespacedKey key, String worldName) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String raw = pdc.get(key, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return null;

        String[] parts = raw.split(";;");
        if (parts.length < 8) return null;

        String id = parts[0];
        String name = parts[1];
        UUID ownerUUID = !"null".equals(parts[2]) ? UUID.fromString(parts[2]) : null;
        String ownerName = parts[3];
        ProtectionBlockType blockType = ProtectionBlockType.fromString(parts[4]);
        if (blockType == null) blockType = ProtectionBlockType.DIAMOND_150;
        ClaimType type = ClaimType.fromString(parts[5]);
        long expiration = Long.parseLong(parts[6]);
        int radius = Integer.parseInt(parts[7]);
        boolean trustAll = parts.length > 8 && Boolean.parseBoolean(parts[8]);

        // Extraer coordenadas de la key
        String[] keyParts = key.getKey().split("_");
        if (keyParts.length < 5) return null;
        int x = Integer.parseInt(keyParts[2]);
        int y = Integer.parseInt(keyParts[3]);
        int z = Integer.parseInt(keyParts[4]);

        Claim claim = new Claim(id, name, ownerUUID, ownerName, type, blockType, expiration, worldName, x, y, z, radius);
        claim.setTrustAll(trustAll);

        if (parts.length > 9 && !parts[9].isEmpty()) {
            for (String uStr : parts[9].split(",")) {
                try {
                    claim.addTrust(UUID.fromString(uStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (parts.length > 10 && !parts[10].isEmpty()) {
            for (String flagPair : parts[10].split(",")) {
                String[] f = flagPair.split("=");
                if (f.length == 2) {
                    ClaimFlag flag = ClaimFlag.fromString(f[0]);
                    if (flag != null) {
                        claim.setFlag(flag, Boolean.parseBoolean(f[1]));
                    }
                }
            }
        }

        return claim;
    }

    public Claim getClaim(String id) {
        if (id == null) return null;
        return claims.get(id);
    }

    public Claim getClaimByName(String name) {
        for (Claim c : claims.values()) {
            if (c.getName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    public List<Claim> getPlayerClaims(UUID ownerUUID) {
        List<Claim> list = new ArrayList<>();
        if (ownerUUID == null) return list;
        for (Claim c : claims.values()) {
            if (c.isOwner(ownerUUID)) {
                list.add(c);
            }
        }
        return list;
    }

    public Collection<Claim> getAllClaims() {
        return claims.values();
    }

    public boolean overlapsWithExistingClaim(String world, int centerX, int centerZ, int radius, String excludeId) {
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;

        for (Claim c : claims.values()) {
            if (excludeId != null && c.getId().equalsIgnoreCase(excludeId)) continue;
            if (!c.getWorldName().equalsIgnoreCase(world)) continue;

            int cMinX = c.getBlockX() - c.getRadius();
            int cMaxX = c.getBlockX() + c.getRadius();
            int cMinZ = c.getBlockZ() - c.getRadius();
            int cMaxZ = c.getBlockZ() + c.getRadius();

            if (minX <= cMaxX && maxX >= cMinX && minZ <= cMaxZ && maxZ >= cMinZ) {
                return true;
            }
        }
        return false;
    }

    public Claim getOverlappingPSClaim(String world, int minX, int maxX, int minZ, int maxZ) {
        for (Claim c : claims.values()) {
            if (!c.getWorldName().equalsIgnoreCase(world)) continue;

            int cMinX = c.getBlockX() - c.getRadius();
            int cMaxX = c.getBlockX() + c.getRadius();
            int cMinZ = c.getBlockZ() - c.getRadius();
            int cMaxZ = c.getBlockZ() + c.getRadius();

            if (minX <= cMaxX && maxX >= cMinX && minZ <= cMaxZ && maxZ >= cMinZ) {
                return c;
            }
        }
        return null;
    }

    public Claim registerBlockClaim(Player player, Block block, ProtectionBlockType blockType, boolean temporary) {
        String worldName = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        String id = (temporary ? "temp_" : "ps_") + UUID.randomUUID().toString().substring(0, 8);
        String name = player.getName() + "_" + blockType.getSimpleName() + "_" + (getPlayerClaims(player.getUniqueId()).size() + 1);
        ClaimType type = temporary ? ClaimType.TEMPORARY : ClaimType.NORMAL;
        long expiration = temporary ? (System.currentTimeMillis() + (temporaryDurationDays * 24 * 60 * 60 * 1000L)) : 0L;

        Claim claim = new Claim(id, name, player.getUniqueId(), player.getName(), type, blockType, expiration, worldName, x, y, z, blockType.getRadius());
        claims.put(id, claim);
        saveClaims();

        showClaimParticles(player, claim);
        return claim;
    }

    public void removeClaim(String id) {
        Claim claim = claims.remove(id);
        if (claim != null) {
            World world = Bukkit.getWorld(claim.getWorldName());
            if (world != null) {
                Chunk chunk = world.getChunkAt(claim.getBlockX() >> 4, claim.getBlockZ() >> 4);
                NamespacedKey key = new NamespacedKey(plugin, "ps_claim_" + claim.getBlockX() + "_" + claim.getBlockY() + "_" + claim.getBlockZ());
                chunk.getPersistentDataContainer().remove(key);
            }
        }
        saveClaims();
    }

    public void showClaimParticles(Player player, Claim claim) {
        World world = Bukkit.getWorld(claim.getWorldName());
        if (world == null || player == null) return;

        int minX = claim.getBlockX() - claim.getRadius();
        int maxX = claim.getBlockX() + claim.getRadius();
        int minZ = claim.getBlockZ() - claim.getRadius();
        int maxZ = claim.getBlockZ() + claim.getRadius();
        int y = player.getLocation().getBlockY();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 8 || !player.isOnline()) {
                    cancel();
                    return;
                }
                ticks++;

                try {
                    for (int currentX = minX; currentX <= maxX; currentX += 4) {
                        player.spawnParticle(Particle.HAPPY_VILLAGER, currentX + 0.5, y + 1.2, minZ + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
                        player.spawnParticle(Particle.HAPPY_VILLAGER, currentX + 0.5, y + 1.2, maxZ + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                    for (int currentZ = minZ; currentZ <= maxZ; currentZ += 4) {
                        player.spawnParticle(Particle.HAPPY_VILLAGER, minX + 0.5, y + 1.2, currentZ + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
                        player.spawnParticle(Particle.HAPPY_VILLAGER, maxX + 0.5, y + 1.2, currentZ + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                } catch (Throwable ignored) {}
            }
        }.runTaskTimer(plugin, 0L, 25L);
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxClaimsPerPlayer() { return maxClaimsPerPlayer; }
    public boolean isFullHeightCoverage() { return fullHeightCoverage; }
    public boolean isShowEntryExitMessages() { return showEntryExitMessages; }
    public WorldGuardHook getWorldGuardHook() { return worldGuardHook; }
    public GriefPreventionHook getGriefPreventionHook() { return griefPreventionHook; }
}
