package com.alcatrazm.plugin.bossdefense;

import com.alcatrazm.plugin.AlcatrazPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BossDefenseManager {

    private final AlcatrazPlugin plugin;
    public final NamespacedKey KEY_BOSS;

    private boolean enabled;

    // Proyectiles
    private boolean projectileDefenseEnabled;
    private double projectileReductionPercentage;
    private boolean includePotions;

    // Espadas y Cuerpo a Cuerpo
    private boolean swordDefenseEnabled;
    private double swordReductionPercentage;
    private boolean applyToSwords;
    private boolean applyToAxes;
    private boolean applyToMaces;
    private boolean applyToAllMelee;

    // Notificaciones / Cartel
    private boolean notificationEnabled;
    private String notificationDisplayType;
    private String messageProjectile;
    private String messageSword;
    private String titleMain;
    private String titleSub;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;
    private double notificationCooldownSeconds;

    // Efectos de Audio y Visuales
    private boolean soundEnabled;
    private Sound soundType;
    private float soundVolume;
    private float soundPitch;
    private boolean particlesEnabled;
    private Particle particleType;
    private int particleCount;
    private double particleOffsetX;
    private double particleOffsetY;
    private double particleOffsetZ;
    private double particleSpeed;

    // Criterios de detección
    private final Set<String> scoreboardTags = new HashSet<>();
    private final List<String> nameKeywords = new ArrayList<>();
    private final Set<EntityType> autoBossEntityTypes = new HashSet<>();
    private double minHealthThreshold;
    private boolean requireMonsterForHealth;
    private boolean worldFilterEnabled;
    private final Set<String> dungeonWorlds = new HashSet<>();

    private final Map<UUID, Long> lastNotificationMap = new ConcurrentHashMap<>();

    public BossDefenseManager(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.KEY_BOSS = new NamespacedKey(plugin, "is_dungeon_boss");
        loadConfig();
    }

    public void reload() {
        loadConfig();
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid != null) {
            lastNotificationMap.remove(uuid);
        }
    }

    public void loadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("boss-defense");
        if (sec == null) {
            sec = plugin.getConfig().getConfigurationSection("boss-projectile-defense");
        }

        if (sec == null) {
            // Valores por defecto
            this.enabled = true;
            this.projectileDefenseEnabled = true;
            this.projectileReductionPercentage = 75.0;
            this.includePotions = false;

            this.swordDefenseEnabled = true;
            this.swordReductionPercentage = 75.0;
            this.applyToSwords = true;
            this.applyToAxes = true;
            this.applyToMaces = true;
            this.applyToAllMelee = false;

            this.notificationEnabled = true;
            this.notificationDisplayType = "ACTIONBAR";
            this.messageProjectile = "&c🛡 &e¡El Jefe absorbe el &6%percentage%%% &ede daño por proyectiles! &7(-%reduction%%%)";
            this.messageSword = "&c🛡 &e¡El Jefe absorbe el &6%percentage%%% &ede daño de tu espada! &7(-%reduction%%%)";
            this.titleMain = "&c🛡 &l¡ESCUDO DE JEFE!";
            this.titleSub = "&eAbsorbió el &6%percentage%%% &ede daño";
            this.titleFadeIn = 5;
            this.titleStay = 25;
            this.titleFadeOut = 5;
            this.notificationCooldownSeconds = 1.5;

            this.soundEnabled = true;
            this.soundType = Sound.ITEM_SHIELD_BLOCK;
            this.soundVolume = 1.0f;
            this.soundPitch = 1.25f;

            this.particlesEnabled = true;
            this.particleType = Particle.ENCHANTED_HIT;
            this.particleCount = 15;
            this.particleOffsetX = 0.4;
            this.particleOffsetY = 0.6;
            this.particleOffsetZ = 0.4;
            this.particleSpeed = 0.1;

            this.minHealthThreshold = 100.0;
            this.requireMonsterForHealth = true;
            this.worldFilterEnabled = false;

            scoreboardTags.clear();
            scoreboardTags.addAll(Arrays.asList("boss", "dungeon_boss", "dungeon", "jefe", "jefe_mazmorra", "miniboss", "boss_dungeon"));

            nameKeywords.clear();
            nameKeywords.addAll(Arrays.asList("boss", "jefe", "rey", "lord", "guardian", "guardián", "warden", "centinela", "dungeon", "mazmorra", "titán", "titan", "coloso", "devorador", "sombra", "lich", "dragón", "dragon"));

            autoBossEntityTypes.clear();
            addEntityTypeSafe("WARDEN");
            addEntityTypeSafe("WITHER");
            addEntityTypeSafe("ENDER_DRAGON");
            addEntityTypeSafe("ELDER_GUARDIAN");
            return;
        }

        this.enabled = sec.getBoolean("enabled", true);

        // 1. Proyectiles
        ConfigurationSection projSec = sec.getConfigurationSection("projectile");
        if (projSec != null) {
            this.projectileDefenseEnabled = projSec.getBoolean("enabled", true);
            this.projectileReductionPercentage = projSec.getDouble("reduction-percentage", 75.0);
            this.includePotions = projSec.getBoolean("include-potions", false);
        } else {
            this.projectileDefenseEnabled = true;
            this.projectileReductionPercentage = sec.getDouble("reduction-percentage", 75.0);
            this.includePotions = sec.getBoolean("include-potions", false);
        }

        // 2. Espadas y Cuerpo a cuerpo
        ConfigurationSection swordSec = sec.getConfigurationSection("sword");
        if (swordSec != null) {
            this.swordDefenseEnabled = swordSec.getBoolean("enabled", true);
            this.swordReductionPercentage = swordSec.getDouble("reduction-percentage", 75.0);
            this.applyToSwords = swordSec.getBoolean("apply-to-swords", true);
            this.applyToAxes = swordSec.getBoolean("apply-to-axes", true);
            this.applyToMaces = swordSec.getBoolean("apply-to-maces", true);
            this.applyToAllMelee = swordSec.getBoolean("apply-to-all-melee", false);
        } else {
            this.swordDefenseEnabled = true;
            this.swordReductionPercentage = sec.getDouble("reduction-percentage", 75.0);
            this.applyToSwords = true;
            this.applyToAxes = true;
            this.applyToMaces = true;
            this.applyToAllMelee = false;
        }

        // 3. Notificación / Cartel configurable
        ConfigurationSection notifSec = sec.getConfigurationSection("notification");
        if (notifSec != null) {
            this.notificationEnabled = notifSec.getBoolean("enabled", true);
            this.notificationDisplayType = notifSec.getString("display-type", "ACTIONBAR").toUpperCase();
            this.messageProjectile = notifSec.getString("message-projectile", sec.getString("actionbar-message", "&c🛡 &e¡El Jefe absorbe el &6%percentage%%% &ede daño por proyectiles! &7(-%reduction%%%)"));
            this.messageSword = notifSec.getString("message-sword", "&c🛡 &e¡El Jefe absorbe el &6%percentage%%% &ede daño de tu espada! &7(-%reduction%%%)");
            this.notificationCooldownSeconds = notifSec.getDouble("cooldown-seconds", 1.5);

            ConfigurationSection titleSec = notifSec.getConfigurationSection("title");
            if (titleSec != null) {
                this.titleMain = titleSec.getString("title", "&c🛡 &l¡ESCUDO DE JEFE!");
                this.titleSub = titleSec.getString("subtitle", "&eAbsorbió el &6%percentage%%% &ede daño");
                this.titleFadeIn = titleSec.getInt("fade-in-ticks", 5);
                this.titleStay = titleSec.getInt("stay-ticks", 25);
                this.titleFadeOut = titleSec.getInt("fade-out-ticks", 5);
            } else {
                this.titleMain = "&c🛡 &l¡ESCUDO DE JEFE!";
                this.titleSub = "&eAbsorbió el &6%percentage%%% &ede daño";
                this.titleFadeIn = 5;
                this.titleStay = 25;
                this.titleFadeOut = 5;
            }
        } else {
            this.notificationEnabled = sec.getBoolean("notify-attacker", true);
            this.notificationDisplayType = "ACTIONBAR";
            String legacyMsg = sec.getString("actionbar-message", "&c🛡 &e¡El Jefe absorbe el &6%percentage%%% &ede daño por proyectiles! &7(-%reduction%%%)");
            this.messageProjectile = legacyMsg;
            this.messageSword = "&c🛡 &e¡El Jefe absorbe el &6%percentage%%% &ede daño de tu espada! &7(-%reduction%%%)";
            this.titleMain = "&c🛡 &l¡ESCUDO DE JEFE!";
            this.titleSub = "&eAbsorbió el &6%percentage%%% &ede daño";
            this.titleFadeIn = 5;
            this.titleStay = 25;
            this.titleFadeOut = 5;
            this.notificationCooldownSeconds = 1.5;
        }

        // Sonido
        ConfigurationSection soundSec = sec.getConfigurationSection("sound");
        if (soundSec != null) {
            this.soundEnabled = soundSec.getBoolean("enabled", true);
            String sName = soundSec.getString("sound-type", "ITEM_SHIELD_BLOCK");
            try {
                this.soundType = Sound.valueOf(sName.toUpperCase());
            } catch (Exception e) {
                this.soundType = Sound.ITEM_SHIELD_BLOCK;
            }
            this.soundVolume = (float) soundSec.getDouble("volume", 1.0);
            this.soundPitch = (float) soundSec.getDouble("pitch", 1.25);
        } else {
            this.soundEnabled = true;
            this.soundType = Sound.ITEM_SHIELD_BLOCK;
            this.soundVolume = 1.0f;
            this.soundPitch = 1.25f;
        }

        // Partículas
        ConfigurationSection partSec = sec.getConfigurationSection("particles");
        if (partSec != null) {
            this.particlesEnabled = partSec.getBoolean("enabled", true);
            String pName = partSec.getString("particle-type", "ENCHANTED_HIT");
            try {
                this.particleType = Particle.valueOf(pName.toUpperCase());
            } catch (Exception e) {
                this.particleType = Particle.ENCHANTED_HIT;
            }
            this.particleCount = partSec.getInt("count", 15);
            this.particleOffsetX = partSec.getDouble("offset-x", 0.4);
            this.particleOffsetY = partSec.getDouble("offset-y", 0.6);
            this.particleOffsetZ = partSec.getDouble("offset-z", 0.4);
            this.particleSpeed = partSec.getDouble("speed", 0.1);
        } else {
            this.particlesEnabled = true;
            this.particleType = Particle.ENCHANTED_HIT;
            this.particleCount = 15;
            this.particleOffsetX = 0.4;
            this.particleOffsetY = 0.6;
            this.particleOffsetZ = 0.4;
            this.particleSpeed = 0.1;
        }

        // Detección
        ConfigurationSection detSec = sec.getConfigurationSection("detection");
        this.scoreboardTags.clear();
        this.nameKeywords.clear();
        this.autoBossEntityTypes.clear();
        this.dungeonWorlds.clear();

        if (detSec != null) {
            List<String> tags = detSec.getStringList("scoreboard-tags");
            if (tags.isEmpty()) {
                tags = Arrays.asList("boss", "dungeon_boss", "dungeon", "jefe", "jefe_mazmorra", "miniboss", "boss_dungeon");
            }
            for (String tag : tags) {
                this.scoreboardTags.add(tag.trim().toLowerCase());
            }

            List<String> keywords = detSec.getStringList("name-keywords");
            if (keywords.isEmpty()) {
                keywords = Arrays.asList("boss", "jefe", "rey", "lord", "guardian", "guardián", "warden", "centinela", "dungeon", "mazmorra", "titán", "titan", "coloso", "devorador", "sombra", "lich", "dragón", "dragon");
            }
            for (String kw : keywords) {
                this.nameKeywords.add(kw.trim().toLowerCase());
            }

            List<String> types = detSec.getStringList("auto-boss-entity-types");
            if (types.isEmpty()) {
                types = Arrays.asList("WARDEN", "WITHER", "ENDER_DRAGON", "ELDER_GUARDIAN");
            }
            for (String type : types) {
                addEntityTypeSafe(type);
            }

            this.minHealthThreshold = detSec.getDouble("min-health-threshold", 100.0);
            this.requireMonsterForHealth = detSec.getBoolean("require-monster-for-health", true);

            ConfigurationSection worldSec = detSec.getConfigurationSection("world-filter");
            if (worldSec != null) {
                this.worldFilterEnabled = worldSec.getBoolean("enabled", false);
                for (String w : worldSec.getStringList("worlds")) {
                    this.dungeonWorlds.add(w.trim().toLowerCase());
                }
            } else {
                this.worldFilterEnabled = false;
            }
        } else {
            this.scoreboardTags.addAll(Arrays.asList("boss", "dungeon_boss", "dungeon", "jefe", "jefe_mazmorra", "miniboss", "boss_dungeon"));
            this.nameKeywords.addAll(Arrays.asList("boss", "jefe", "rey", "lord", "guardian", "guardián", "warden", "centinela", "dungeon", "mazmorra", "titán", "titan", "coloso", "devorador", "sombra", "lich", "dragón", "dragon"));
            addEntityTypeSafe("WARDEN");
            addEntityTypeSafe("WITHER");
            addEntityTypeSafe("ENDER_DRAGON");
            addEntityTypeSafe("ELDER_GUARDIAN");
            this.minHealthThreshold = 100.0;
            this.requireMonsterForHealth = true;
            this.worldFilterEnabled = false;
        }
    }

    private void addEntityTypeSafe(String typeName) {
        try {
            EntityType type = EntityType.valueOf(typeName.toUpperCase().trim());
            this.autoBossEntityTypes.add(type);
        } catch (IllegalArgumentException ignored) {}
    }

    public boolean isBoss(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }

        // Jugadores normales no son bosses
        if (entity instanceof Player) {
            return false;
        }

        // 1. Verificación explícita por PersistentDataContainer (PDC)
        if (entity.getPersistentDataContainer().has(KEY_BOSS, PersistentDataType.BYTE)) {
            return true;
        }

        // 2. Verificación por Scoreboard Tags de Minecraft vanilla (/tag @e add boss)
        for (String tag : entity.getScoreboardTags()) {
            if (scoreboardTags.contains(tag.toLowerCase())) {
                return true;
            }
        }

        // Si el filtro de mundos está activado y el mundo no coincide, no evaluar heurísticas
        if (worldFilterEnabled && !dungeonWorlds.isEmpty()) {
            String worldName = entity.getWorld().getName().toLowerCase();
            boolean worldMatched = false;
            for (String dw : dungeonWorlds) {
                if (worldName.contains(dw)) {
                    worldMatched = true;
                    break;
                }
            }
            if (!worldMatched) {
                return false;
            }
        }

        // 3. Verificación por Tipo de Entidad Boss Vanilla
        if (autoBossEntityTypes.contains(entity.getType())) {
            return true;
        }

        // 4. Verificación por Nombre Visible / CustomName
        String customName = entity.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            String cleanName = ChatColor.stripColor(customName).toLowerCase();
            for (String kw : nameKeywords) {
                if (cleanName.contains(kw)) {
                    return true;
                }
            }
        }

        // 5. Verificación por Umbral de Vida Máxima (>= minHealthThreshold)
        if (minHealthThreshold > 0) {
            if (!requireMonsterForHealth || (entity instanceof Monster)) {
                double maxHealth = getMaxHealth(entity);
                if (maxHealth >= minHealthThreshold) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isSwordOrMelee(ItemStack item) {
        if (applyToAllMelee) return true;
        if (item == null || item.getType().isAir()) {
            return applyToAllMelee;
        }

        String name = item.getType().name();
        if (applyToSwords && name.endsWith("_SWORD")) {
            return true;
        }
        if (applyToAxes && name.endsWith("_AXE")) {
            return true;
        }
        if (applyToMaces && (name.equals("MACE") || name.contains("MACE"))) {
            return true;
        }
        return false;
    }

    public String getBossReason(LivingEntity entity) {
        if (entity == null || entity instanceof Player) return "No es Boss (Jugador o inválido)";

        if (entity.getPersistentDataContainer().has(KEY_BOSS, PersistentDataType.BYTE)) {
            return "PDC Tag (is_dungeon_boss)";
        }
        for (String tag : entity.getScoreboardTags()) {
            if (scoreboardTags.contains(tag.toLowerCase())) {
                return "Scoreboard Tag: " + tag;
            }
        }
        if (autoBossEntityTypes.contains(entity.getType())) {
            return "Tipo de Entidad Boss (" + entity.getType().name() + ")";
        }
        String customName = entity.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            String cleanName = ChatColor.stripColor(customName).toLowerCase();
            for (String kw : nameKeywords) {
                if (cleanName.contains(kw)) {
                    return "Nombre contiene palabra clave: '" + kw + "'";
                }
            }
        }
        if (minHealthThreshold > 0 && (!requireMonsterForHealth || (entity instanceof Monster))) {
            double maxHealth = getMaxHealth(entity);
            if (maxHealth >= minHealthThreshold) {
                return "Vida máxima superior al umbral (" + String.format("%.1f", maxHealth) + " >= " + minHealthThreshold + " HP)";
            }
        }
        return "No es Boss";
    }

    public double getMaxHealth(LivingEntity entity) {
        try {
            Attribute attr = null;
            try {
                attr = Attribute.valueOf("MAX_HEALTH");
            } catch (IllegalArgumentException e) {
                try {
                    attr = Attribute.valueOf("GENERIC_MAX_HEALTH");
                } catch (IllegalArgumentException ignored) {}
            }
            if (attr != null) {
                AttributeInstance inst = entity.getAttribute(attr);
                if (inst != null) {
                    return inst.getValue();
                }
            }
        } catch (Throwable ignored) {}
        return entity.getMaxHealth();
    }

    public void tagAsBoss(LivingEntity entity) {
        entity.getPersistentDataContainer().set(KEY_BOSS, PersistentDataType.BYTE, (byte) 1);
        entity.addScoreboardTag("boss");
        entity.addScoreboardTag("dungeon_boss");
    }

    public void untagBoss(LivingEntity entity) {
        entity.getPersistentDataContainer().remove(KEY_BOSS);
        entity.removeScoreboardTag("boss");
        entity.removeScoreboardTag("dungeon_boss");
    }

    public void playDefenseEffects(LivingEntity victim, Player attacker, double originalDamage, double finalDamage, boolean isProjectile) {
        // Efectos de partículas en el boss
        if (particlesEnabled && particleType != null) {
            victim.getWorld().spawnParticle(
                    particleType,
                    victim.getLocation().add(0, Math.max(0.5, victim.getHeight() * 0.6), 0),
                    particleCount,
                    particleOffsetX,
                    particleOffsetY,
                    particleOffsetZ,
                    particleSpeed
            );
        }

        // Sonido de bloqueo de proyectil o escudo
        if (soundEnabled && soundType != null) {
            victim.getWorld().playSound(
                    victim.getLocation(),
                    soundType,
                    soundVolume,
                    soundPitch
            );
        }

        // Mensaje / Cartel al atacante (completamente configurable si se muestra o no)
        if (attacker != null && notificationEnabled && attacker.isOnline()) {
            long now = System.currentTimeMillis();
            Long last = lastNotificationMap.get(attacker.getUniqueId());
            long cooldownMs = (long) (notificationCooldownSeconds * 1000.0);
            if (last == null || (now - last) >= cooldownMs) {
                lastNotificationMap.put(attacker.getUniqueId(), now);

                double reduction = isProjectile ? projectileReductionPercentage : swordReductionPercentage;
                String template = isProjectile ? messageProjectile : messageSword;
                if (template == null || template.isEmpty()) {
                    template = isProjectile ? "&c🛡 &e¡El Jefe absorbe el &6%percentage%%% &ede daño por proyectiles!" : "&c🛡 &e¡El Jefe absorbe el &6%percentage%%% &ede daño de tu espada!";
                }

                String formattedPercentage = String.format("%.0f", reduction);
                String formattedOriginal = String.format("%.1f", originalDamage);
                String formattedFinal = String.format("%.1f", finalDamage);
                String bossName = victim.getCustomName() != null ? victim.getCustomName() : victim.getName();

                String msg = template
                        .replace("%percentage%", formattedPercentage)
                        .replace("%reduction%", formattedPercentage)
                        .replace("%original%", formattedOriginal)
                        .replace("%final%", formattedFinal)
                        .replace("%boss%", bossName);

                msg = ChatColor.translateAlternateColorCodes('&', msg);

                sendNotification(attacker, msg, formattedPercentage, bossName);
            }
        }
    }

    private void sendNotification(Player player, String message, String percentage, String bossName) {
        if ("TITLE".equalsIgnoreCase(notificationDisplayType)) {
            String title = titleMain.replace("%percentage%", percentage).replace("%boss%", bossName);
            String sub = titleSub.replace("%percentage%", percentage).replace("%boss%", bossName);
            title = ChatColor.translateAlternateColorCodes('&', title);
            sub = ChatColor.translateAlternateColorCodes('&', sub);
            player.sendTitle(title, sub, titleFadeIn, titleStay, titleFadeOut);
        } else if ("CHAT".equalsIgnoreCase(notificationDisplayType)) {
            player.sendMessage(message);
        } else {
            // Default: ACTIONBAR
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            } catch (Throwable ignored) {
                player.sendMessage(message);
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isProjectileDefenseEnabled() { return projectileDefenseEnabled; }
    public void setProjectileDefenseEnabled(boolean projectileDefenseEnabled) { this.projectileDefenseEnabled = projectileDefenseEnabled; }
    public double getProjectileReductionPercentage() { return projectileReductionPercentage; }
    public void setProjectileReductionPercentage(double projectileReductionPercentage) { this.projectileReductionPercentage = projectileReductionPercentage; }

    public boolean isSwordDefenseEnabled() { return swordDefenseEnabled; }
    public void setSwordDefenseEnabled(boolean swordDefenseEnabled) { this.swordDefenseEnabled = swordDefenseEnabled; }
    public double getSwordReductionPercentage() { return swordReductionPercentage; }
    public void setSwordReductionPercentage(double swordReductionPercentage) { this.swordReductionPercentage = swordReductionPercentage; }

    public boolean isNotificationEnabled() { return notificationEnabled; }
    public void setNotificationEnabled(boolean notificationEnabled) { this.notificationEnabled = notificationEnabled; }
    public String getNotificationDisplayType() { return notificationDisplayType; }
    public void setNotificationDisplayType(String notificationDisplayType) { this.notificationDisplayType = notificationDisplayType; }

    public boolean isIncludePotions() { return includePotions; }
    public Set<String> getScoreboardTags() { return Collections.unmodifiableSet(scoreboardTags); }
    public List<String> getNameKeywords() { return Collections.unmodifiableList(nameKeywords); }
    public Set<EntityType> getAutoBossEntityTypes() { return Collections.unmodifiableSet(autoBossEntityTypes); }
    public double getMinHealthThreshold() { return minHealthThreshold; }
}
