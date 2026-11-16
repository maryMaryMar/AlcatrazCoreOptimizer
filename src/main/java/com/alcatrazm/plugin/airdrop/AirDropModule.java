package com.alcatrazm.plugin.airdrop;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.module.AlcatrazModule;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

/**
 * Modulo oficial de AirDrops cinematicos y automaticos absorbido de UniversAirDrop.
 */
public final class AirDropModule implements AlcatrazModule {
    private final AlcatrazPlugin plugin;
    private final RandomGenerator random = new Random();

    private MessageFormatter messageFormatter;
    private LootManager lootManager;
    private ParticleRenderer particleRenderer;
    private CinematicManager cinematicManager;
    private AirDropManager airDropManager;
    private MenuManager menuManager;
    private DropListener dropListener;

    private BukkitTask tickTask;
    private long ticks = 0L;
    private boolean active = false;
    private boolean disabling = false;

    public AirDropModule(AlcatrazPlugin plugin) {
        this.plugin = plugin;
        this.messageFormatter = new MessageFormatter(this);
    }

    @Override
    public String getId() {
        return "airdrop";
    }

    @Override
    public String getDisplayName() {
        return "AirDrops Automaticos (UniversAirDrop)";
    }

    @Override
    public boolean isEnabled() {
        return this.plugin.getConfig().getBoolean("airdrop.enabled", true);
    }

    @Override
    public void enable() {
        if (this.active) {
            return;
        }
        this.disabling = false;
        this.ticks = 0L;

        this.messageFormatter = new MessageFormatter(this);
        this.lootManager = new LootManager(this);
        this.particleRenderer = new ParticleRenderer(this);
        this.cinematicManager = new CinematicManager(this);
        this.airDropManager = new AirDropManager(this);
        this.menuManager = new MenuManager(this);
        this.dropListener = new DropListener(this);

        this.active = true;

        this.plugin.getServer().getPluginManager().registerEvents(this.cinematicManager, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.menuManager, this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(this.dropListener, this.plugin);

        this.airDropManager.purgeResidualEntities();
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.cinematicManager.restorePlayer(p);
        }

        this.tickTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            this.ticks++;
            this.airDropManager.tick(this.ticks);
            this.cinematicManager.tick(this.ticks);
            if (this.ticks % 10L == 0L) {
                this.menuManager.tick(this.ticks);
            }
        }, 1L, 1L);
    }

    @Override
    public void disable() {
        this.disabling = true;
        this.active = false;

        if (this.tickTask != null && !this.tickTask.isCancelled()) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        if (this.menuManager != null) {
            HandlerList.unregisterAll(this.menuManager);
            this.menuManager.closeAll();
        }
        if (this.cinematicManager != null) {
            HandlerList.unregisterAll(this.cinematicManager);
            this.cinematicManager.stopAll();
        }
        if (this.dropListener != null) {
            HandlerList.unregisterAll(this.dropListener);
        }
        if (this.airDropManager != null) {
            this.airDropManager.shutdown();
        }
    }

    public AlcatrazPlugin getPlugin() {
        return this.plugin;
    }

    public Object getSetting(String key) {
        return this.plugin.getConfig().get("airdrop." + key);
    }

    public void setSetting(String key, Object value) {
        this.plugin.getConfig().set("airdrop." + key, value);
    }

    public boolean getBoolean(String key, boolean def) {
        return this.plugin.getConfig().getBoolean("airdrop." + key, def);
    }

    public String getString(String key, String def) {
        return this.plugin.getConfig().getString("airdrop." + key, def);
    }

    public List<String> getStringList(String key) {
        return this.plugin.getConfig().getStringList("airdrop." + key);
    }

    public int getInt(String key, int def, int min, int max) {
        int val = this.plugin.getConfig().getInt("airdrop." + key, def);
        return Math.clamp(val, min, max);
    }

    public double getDouble(String key, double def, double min, double max) {
        double val = this.plugin.getConfig().getDouble("airdrop." + key, def);
        return Double.isFinite(val) ? Math.clamp(val, min, max) : def;
    }

    public MessageFormatter getMessageFormatter() { return this.messageFormatter; }
    public LootManager getLootManager() { return this.lootManager; }
    public ParticleRenderer getParticleRenderer() { return this.particleRenderer; }
    public CinematicManager getCinematicManager() { return this.cinematicManager; }
    public AirDropManager getAirDropManager() { return this.airDropManager; }
    public MenuManager getMenuManager() { return this.menuManager; }
    public DropListener getDropListener() { return this.dropListener; }
    public RandomGenerator getRandom() { return this.random; }
    public long getTicks() { return this.ticks; }
    public boolean isActive() { return this.active; }
    public boolean isDisabling() { return this.disabling; }
}
