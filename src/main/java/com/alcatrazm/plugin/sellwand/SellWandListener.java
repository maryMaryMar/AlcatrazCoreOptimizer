package com.alcatrazm.plugin.sellwand;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.chestlock.ChestLockManager;
import com.alcatrazm.plugin.chestlock.LockedBlock;
import com.alcatrazm.plugin.protection.Claim;
import com.alcatrazm.plugin.protection.ProtectionManager;
import com.alcatrazm.plugin.protection.WorldGuardHook;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SellWandListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final SellWandManager manager;
    private final WorldGuardHook worldGuardHook;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_FMT = new DecimalFormat("#,###");

    // Cache estático de reflexión para GriefPrevention
    private static boolean gpInitialized = false;
    private static Object gpInstance = null;
    private static Method gpGetDataStoreMethod = null;
    private static Method gpGetClaimAtMethod = null;
    private static Method gpAllowContainersMethod = null;

    public SellWandListener(AlcatrazPlugin plugin, SellWandManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.worldGuardHook = new WorldGuardHook();
        initGriefPrevention();
    }

    private static synchronized void initGriefPrevention() {
        if (gpInitialized) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) return;
        try {
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            gpInstance = gpClass.getField("instance").get(null);
            gpGetDataStoreMethod = gpClass.getMethod("getDataStore");
            Object dataStore = gpGetDataStoreMethod.invoke(gpInstance);

            Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
            gpGetClaimAtMethod = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, claimClass);
            gpAllowContainersMethod = claimClass.getMethod("allowContainers", Player.class);
            gpInitialized = true;
        } catch (Throwable ignored) {
            gpInitialized = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        BlockState state = block.getState();
        if (!(state instanceof Container)) return;

        Player player = event.getPlayer();
        ItemStack wand = player.getInventory().getItemInMainHand();

        if (!manager.isSellWand(wand)) return;

        // Cancelar apertura normal del cofre
        event.setCancelled(true);

        if (!player.hasPermission("alcatraz.sellwand.use")) {
            player.sendMessage(SellWandTier.colorize(plugin.getConfig().getString("sellwand.messages.no-permission", "&cNo tienes permiso para usar esta varita de venta.")));
            return;
        }

        // Verificaciones de Seguridad y Claims antes del cooldown
        if (!canAccessContainer(player, block)) {
            player.sendMessage(SellWandTier.colorize(plugin.getConfig().getString("sellwand.messages.protected-chest", "&cNo tienes permiso para vender el contenido de este cofre protegido.")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Anti-Spam / Cooldown
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (manager.getCooldownSeconds() * 1000L);
        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse != null && (now - lastUse < cooldownMs)) {
            return;
        }

        SellWandTier tier = manager.getTier(wand);
        if (tier == null) return;

        SellWandManager.SellResult result = manager.processSale(player, block, wand);

        if (!result.hasSold()) {
            String noItemsMsg = plugin.getConfig().getString("sellwand.messages.no-items-to-sell", "&eNo se encontraron objetos vendibles en este contenedor.");
            sendActionBar(player, SellWandTier.colorize(noItemsMsg));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1.2f);
            return;
        }

        // Cooldown registrado solo tras venta exitosa
        cooldowns.put(player.getUniqueId(), now);

        // Efectos de venta exitosa
        playSuccessEffects(player, block.getLocation());

        // Mensaje de venta con desglose de bono
        double multiplier = manager.getMultiplier(wand);
        String actionbarTemplate = plugin.getConfig().getString("sellwand.messages.actionbar-sold",
                "&6&l✔ &e¡Vendiste &f%items% ítems &epor &a+$%dinero% &7(%multiplicador%x) &8[&a+Bono: $%bono%&8]!");
        String msg = actionbarTemplate
                .replace("%items%", INT_FMT.format(result.getItemsSold()))
                .replace("%dinero%", MONEY_FMT.format(result.getFinalTotal()))
                .replace("%raw_dinero%", MONEY_FMT.format(result.getRawTotal()))
                .replace("%bono%", MONEY_FMT.format(result.getBonusTotal()))
                .replace("%multiplicador%", String.format(Locale.US, "%.2f", multiplier))
                .replace("%usos%", tier.isInfinite() ? "Infinitos" : String.valueOf(result.getRemainingUses()))
                .replace("%tier%", tier.getName());

        sendActionBar(player, SellWandTier.colorize(msg));

        // Notificación en chat opcional
        if (plugin.getConfig().getBoolean("sellwand.messages.send-chat-summary", false)) {
            player.sendMessage(SellWandTier.colorize(msg));
        }

        // Varita rota al agotarse los usos
        if (result.isBroken()) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.05);

            String brokenMsg = plugin.getConfig().getString("sellwand.messages.wand-broken", "&c¡Tu &e%tier% &cse ha roto tras agotar todos sus usos!");
            player.sendMessage(SellWandTier.colorize(brokenMsg.replace("%tier%", tier.getName())));
        }

        // Forzar actualización visual inmediata en cliente / Bedrock
        player.updateInventory();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private boolean canAccessContainer(Player player, Block block) {
        if (player.isOp() || player.hasPermission("alcatraz.sellwand.bypass")) {
            return true;
        }

        if (!canAccessSingleBlock(player, block)) {
            return false;
        }

        // Si es un cofre doble, validar permisos también en la otra mitad
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData && chestData.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            ChestLockManager lockMgr = plugin.getChestLockManager();
            Block otherHalf = lockMgr != null ? lockMgr.getRelatedDoubleChestPart(block) : null;
            if (otherHalf != null && !canAccessSingleBlock(player, otherHalf)) {
                return false;
            }
        }

        return true;
    }

    private boolean canAccessSingleBlock(Player player, Block block) {
        // 1. Alcatraz Candados Privados
        ChestLockManager lockMgr = plugin.getChestLockManager();
        if (lockMgr != null) {
            LockedBlock lock = lockMgr.getLock(block);
            if (lock != null) {
                if (!lock.isOwner(player.getUniqueId()) && !lock.isTrusted(player.getName())) {
                    return false;
                }
            }
        }

        // 2. Alcatraz Protecciones
        ProtectionManager protMgr = plugin.getProtectionManager();
        if (protMgr != null) {
            Claim claim = protMgr.getClaimAt(block.getLocation());
            if (claim != null && !claim.isTrusted(player.getUniqueId())) {
                return false;
            }
        }

        // 3. WorldGuard
        if (worldGuardHook != null && worldGuardHook.isWorldGuardPresent()) {
            if (!worldGuardHook.canBuildWithWorldGuard(player, block.getLocation())) {
                return false;
            }
        }

        // 4. GriefPrevention (Reflection cacheada estática)
        if (Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) {
            if (!gpInitialized) {
                initGriefPrevention();
            }
            if (gpInitialized) {
                try {
                    Object dataStore = gpGetDataStoreMethod.invoke(gpInstance);
                    Object claim = gpGetClaimAtMethod.invoke(dataStore, block.getLocation(), false, null);
                    if (claim != null) {
                        Object reason = gpAllowContainersMethod.invoke(claim, player);
                        if (reason != null) {
                            return false;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        return true;
    }

    private void playSuccessEffects(Player player, Location blockLoc) {
        Location loc = blockLoc.clone().add(0.5, 1.1, 0.5);
        player.spawnParticle(Particle.HAPPY_VILLAGER, loc, 12, 0.35, 0.25, 0.35, 0.05);
        player.spawnParticle(Particle.ENCHANT, loc, 15, 0.3, 0.3, 0.3, 0.1);

        String soundName = plugin.getConfig().getString("sellwand.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.25f);
        } catch (Exception e) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.25f);
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }
}
