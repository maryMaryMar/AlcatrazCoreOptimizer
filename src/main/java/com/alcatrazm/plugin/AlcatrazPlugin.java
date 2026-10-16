package com.alcatrazm.plugin;

import com.alcatrazm.plugin.duels.DuelCommand;
import com.alcatrazm.plugin.duels.DuelMenuListener;
import com.alcatrazm.plugin.duels.DuelMenuManager;
import com.alcatrazm.plugin.chatbubbles.ChatBubbleCommand;
import com.alcatrazm.plugin.chatbubbles.ChatBubbleListener;
import com.alcatrazm.plugin.chatbubbles.ChatBubbleManager;
import com.alcatrazm.plugin.antiflood.AntiFloodListener;
import com.alcatrazm.plugin.antiflood.AntiFloodManager;
import com.alcatrazm.plugin.chatfilter.ChatFilterListener;
import com.alcatrazm.plugin.chatfilter.ChatFilterManager;
import com.alcatrazm.plugin.anticaps.AntiCapsListener;
import com.alcatrazm.plugin.anticaps.AntiCapsManager;
import com.alcatrazm.plugin.antispam.AntiSpamListener;
import com.alcatrazm.plugin.antispam.AntiSpamManager;
import com.alcatrazm.plugin.announcer.AutoAnnouncer;
import com.alcatrazm.plugin.chestlock.ChestLockCommand;
import com.alcatrazm.plugin.chestlock.ChestLockListener;
import com.alcatrazm.plugin.chestlock.ChestLockManager;
import com.alcatrazm.plugin.combatlog.CombatLogListener;
import com.alcatrazm.plugin.combatlog.CombatLogManager;
import com.alcatrazm.plugin.commands.AlcatrazCommand;
import com.alcatrazm.plugin.commands.ClearParticlesCommand;
import com.alcatrazm.plugin.elevator.ElevatorListener;
import com.alcatrazm.plugin.lifemending.commands.LifeMendingCommand;
import com.alcatrazm.plugin.lifemending.listeners.LifeMendingListener;
import com.alcatrazm.plugin.lifemending.managers.LifeMendingManager;
import com.alcatrazm.plugin.lifemending.tasks.LifeMendingTask;
import com.alcatrazm.plugin.listeners.BackpackAntiDupeListener;
import com.alcatrazm.plugin.mobcatcher.MobCatcherCommand;
import com.alcatrazm.plugin.mobcatcher.MobCatcherListener;
import com.alcatrazm.plugin.mobcatcher.MobCatcherManager;
import com.alcatrazm.plugin.netheriteelytra.commands.NetheriteElytraCommand;
import com.alcatrazm.plugin.netheriteelytra.listeners.AnvilListener;
import com.alcatrazm.plugin.netheriteelytra.listeners.FlightListener;
import com.alcatrazm.plugin.netheriteelytra.managers.ItemManager;
import com.alcatrazm.plugin.newbieprotection.NewbieProtectionCommand;
import com.alcatrazm.plugin.newbieprotection.NewbieProtectionListener;
import com.alcatrazm.plugin.newbieprotection.NewbieProtectionManager;
import com.alcatrazm.plugin.optimizer.chunks.TransientChunkUnloader;
import com.alcatrazm.plugin.optimizer.items.ItemAndXPStacker;
import com.alcatrazm.plugin.optimizer.mobs.FarmOptimizer;
import com.alcatrazm.plugin.optimizer.monitor.PerformanceMonitor;
import com.alcatrazm.plugin.optimizer.redstone.RedstoneLimiter;
import com.alcatrazm.plugin.optimizer.tnt.TNTOptimizer;
import com.alcatrazm.plugin.optimizer.villagers.VillagerOptimizer;
import com.alcatrazm.plugin.playervaults.PlayerVaultCommand;
import com.alcatrazm.plugin.playervaults.PlayerVaultListener;
import com.alcatrazm.plugin.playervaults.PlayerVaultManager;
import com.alcatrazm.plugin.protection.ProtectionCommand;
import com.alcatrazm.plugin.protection.ProtectionListener;
import com.alcatrazm.plugin.protection.ProtectionManager;
import com.alcatrazm.plugin.sonicblade.SonicBladeCommand;
import com.alcatrazm.plugin.sonicblade.SonicBladeListener;
import com.alcatrazm.plugin.sonicblade.SonicBladeManager;
import com.alcatrazm.plugin.trash.TrashCommand;
import com.alcatrazm.plugin.trash.TrashManager;
import com.alcatrazm.plugin.skills.AuraSkillsFixer;
import com.alcatrazm.plugin.skills.AuraSkillsListener;
import com.alcatrazm.plugin.voidspawn.VoidSpawnListener;
import com.alcatrazm.plugin.sellwand.SellWandCommand;
import com.alcatrazm.plugin.sellwand.SellWandListener;
import com.alcatrazm.plugin.sellwand.SellWandManager;
import com.alcatrazm.plugin.mounts.MountCommand;
import com.alcatrazm.plugin.mounts.MountListener;
import com.alcatrazm.plugin.mounts.MountManager;
import com.alcatrazm.plugin.bossdefense.BossDefenseCommand;
import com.alcatrazm.plugin.bossdefense.BossDefenseListener;
import com.alcatrazm.plugin.bossdefense.BossDefenseManager;
import com.alcatrazm.plugin.resourcepack.ResourcePackCommand;
import com.alcatrazm.plugin.resourcepack.ResourcePackListener;
import com.alcatrazm.plugin.resourcepack.ResourcePackManager;
import com.alcatrazm.plugin.antixray.AntiXrayCommand;
import com.alcatrazm.plugin.antixray.AntiXrayListener;
import com.alcatrazm.plugin.antixray.AntiXrayManager;
import com.alcatrazm.plugin.grapplinghook.GrapplingHookCommand;
import com.alcatrazm.plugin.grapplinghook.GrapplingHookListener;
import com.alcatrazm.plugin.grapplinghook.GrapplingHookManager;
import com.alcatrazm.plugin.halloween.HalloweenCommand;
import com.alcatrazm.plugin.halloween.HalloweenListener;
import com.alcatrazm.plugin.halloween.HalloweenManager;
import org.bukkit.Bukkit;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AlcatrazPlugin extends JavaPlugin {

    // Módulos Core de Combate e Ítems
    private SonicBladeManager sonicBladeManager;
    private SonicBladeListener sonicBladeListener;
    private ItemManager itemManager;
    private FlightListener flightListener;
    private LifeMendingManager lifeMendingManager;
    private LifeMendingListener lifeMendingListener;

    // Módulos de Utilidades y Jugabilidad
    private ElevatorListener elevatorListener;
    private VoidSpawnListener voidSpawnListener;
    private NewbieProtectionManager newbieProtectionManager;
    private PlayerVaultManager playerVaultManager;
    private ProtectionManager protectionManager;
    private MobCatcherManager mobCatcherManager;
    private CombatLogManager combatLogManager;
    private TrashManager trashManager;
    private AutoAnnouncer autoAnnouncer;
    private ChestLockManager chestLockManager;
    private SellWandManager sellWandManager;
    private SellWandListener sellWandListener;
    private AuraSkillsFixer auraSkillsFixer;
    private AuraSkillsListener auraSkillsListener;
    private MountManager mountManager;
    private BossDefenseManager bossDefenseManager;
    private BossDefenseListener bossDefenseListener;
    private ChatBubbleManager chatBubbleManager;
    private AntiFloodManager antiFloodManager;
    private ChatFilterManager chatFilterManager;
    private AntiCapsManager antiCapsManager;
    private AntiSpamManager antiSpamManager;
    private ResourcePackManager resourcePackManager;
    private ResourcePackListener resourcePackListener;
    private AntiXrayManager antiXrayManager;
    private AntiXrayListener antiXrayListener;
    private GrapplingHookManager grapplingHookManager;
    private GrapplingHookListener grapplingHookListener;
    private DuelMenuManager duelMenuManager;
    private DuelMenuListener duelMenuListener;
    private HalloweenManager halloweenManager;


    // Módulos Optimizadores de Rendimiento y Memoria
    private TransientChunkUnloader transientChunkUnloader;
    private FarmOptimizer farmOptimizer;
    private ItemAndXPStacker itemAndXPStacker;
    private RedstoneLimiter redstoneLimiter;
    private VillagerOptimizer villagerOptimizer;
    private TNTOptimizer tntOptimizer;
    private PerformanceMonitor performanceMonitor;

    private BukkitTask flightTask;
    private BukkitTask lifeMendingTask;

    private static AlcatrazPlugin instance;
    private static com.alcatrazm.plugin.protection.AlcatrazTrustProvider trustProvider = null;

    public static AlcatrazPlugin getInstance() {
        return instance;
    }

    public static void setTrustProvider(com.alcatrazm.plugin.protection.AlcatrazTrustProvider provider) {
        trustProvider = provider;
    }

    public static com.alcatrazm.plugin.protection.AlcatrazTrustProvider getTrustProvider() {
        return trustProvider;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 1. Módulo Sonic Blade (Hoja Sónica)
        this.sonicBladeManager = new SonicBladeManager(this);
        this.sonicBladeListener = new SonicBladeListener(this, sonicBladeManager);
        getServer().getPluginManager().registerEvents(sonicBladeListener, this);

        SonicBladeCommand sonicCmd = new SonicBladeCommand(this, sonicBladeManager);
        registerPluginCommand("hojasonica", sonicCmd, sonicCmd);

        // 2. Módulo Netherite Elytra (Pechera con Élitra)
        this.itemManager = new ItemManager(this);
        this.flightListener = new FlightListener(this, itemManager);
        getServer().getPluginManager().registerEvents(flightListener, this);
        getServer().getPluginManager().registerEvents(new AnvilListener(this, itemManager), this);

        NetheriteElytraCommand elytraCmd = new NetheriteElytraCommand(this, itemManager);
        registerPluginCommand("netheriteelytra", elytraCmd, elytraCmd);
        registerPluginCommand("elytrachest", elytraCmd, elytraCmd);
        registerPluginCommand("elytrabook", elytraCmd, elytraCmd);
        registerPluginCommand("pechera", elytraCmd, elytraCmd);
        registerPluginCommand("libro", elytraCmd, elytraCmd);

        // 3. Módulo Life Mending (Reparación Vital)
        this.lifeMendingManager = new LifeMendingManager(this);
        this.lifeMendingListener = new LifeMendingListener(this, lifeMendingManager);
        getServer().getPluginManager().registerEvents(lifeMendingListener, this);

        LifeMendingCommand lifeMendingCmd = new LifeMendingCommand(this, lifeMendingManager);
        registerPluginCommand("lifemending", lifeMendingCmd, lifeMendingCmd);
        registerPluginCommand("lm", lifeMendingCmd, lifeMendingCmd);

        // 4. Módulo Elevadores
        this.elevatorListener = new ElevatorListener(this);
        getServer().getPluginManager().registerEvents(elevatorListener, this);

        // 5. Módulo VoidSpawn (Rescate de Caída al Vacío)
        this.voidSpawnListener = new VoidSpawnListener(this);
        getServer().getPluginManager().registerEvents(voidSpawnListener, this);

        // 6. Módulo Protección de Nuevos Jugadores (Anti-PvP)
        this.newbieProtectionManager = new NewbieProtectionManager(this);
        getServer().getPluginManager().registerEvents(new NewbieProtectionListener(this, newbieProtectionManager), this);
        NewbieProtectionCommand newbieCmd = new NewbieProtectionCommand(this, newbieProtectionManager);
        registerPluginCommand("newbieprotection", newbieCmd, newbieCmd);

        // 7. Módulos Optimizadores de Rendimiento y Memoria RAM
        this.transientChunkUnloader = new TransientChunkUnloader(this);
        getServer().getPluginManager().registerEvents(transientChunkUnloader, this);

        this.farmOptimizer = new FarmOptimizer(this);
        getServer().getPluginManager().registerEvents(farmOptimizer, this);

        this.itemAndXPStacker = new ItemAndXPStacker(this);
        getServer().getPluginManager().registerEvents(itemAndXPStacker, this);

        this.redstoneLimiter = new RedstoneLimiter(this);
        getServer().getPluginManager().registerEvents(redstoneLimiter, this);

        this.villagerOptimizer = new VillagerOptimizer(this);
        getServer().getPluginManager().registerEvents(villagerOptimizer, this);

        this.tntOptimizer = new TNTOptimizer(this);
        getServer().getPluginManager().registerEvents(tntOptimizer, this);

        this.performanceMonitor = new PerformanceMonitor(this);

        // 8. Módulo Player Vaults Infinitos para Administradores
        this.playerVaultManager = new PlayerVaultManager(this);
        getServer().getPluginManager().registerEvents(new PlayerVaultListener(playerVaultManager), this);
        PlayerVaultCommand pvCmd = new PlayerVaultCommand(playerVaultManager);
        registerPluginCommand("playervault", pvCmd, pvCmd);
        registerPluginCommand("pv", pvCmd, pvCmd);
        registerPluginCommand("vault", pvCmd, pvCmd);

        // 9. Módulo Sistema de Menas de Protección (30, 50, 100, 150, 300 bloques)
        this.protectionManager = new ProtectionManager(this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this, protectionManager), this);
        if (getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
            getServer().getPluginManager().registerEvents(new com.alcatrazm.plugin.protection.GriefPreventionListener(protectionManager), this);
            getLogger().info(" Hook con GriefPrevention activado exitosamente (Anti-Superposición de Protecciones)");
        }
        ProtectionCommand claimCmd = new ProtectionCommand(protectionManager);
        // Comandos ps y proteccion desvinculados para ceder 100% el control a AEGrief

        // 10. Módulo Mob Catcher (Esfera de Contención Cuántica)
        this.mobCatcherManager = new MobCatcherManager(this);
        getServer().getPluginManager().registerEvents(new MobCatcherListener(this, mobCatcherManager), this);
        MobCatcherCommand mobCatcherCmd = new MobCatcherCommand(mobCatcherManager);
        registerPluginCommand("mobcatcher", mobCatcherCmd, mobCatcherCmd);

        // 11. Módulo CombatLog (Anti-Desconexión en PvP)
        this.combatLogManager = new CombatLogManager(this);
        getServer().getPluginManager().registerEvents(new CombatLogListener(this, combatLogManager), this);

        // 12. Módulo Papelera / Basurero Virtual
        this.trashManager = new TrashManager();
        getServer().getPluginManager().registerEvents(trashManager, this);
        TrashCommand trashCmd = new TrashCommand(trashManager);
        registerPluginCommand("trash", trashCmd, trashCmd);
        registerPluginCommand("basurero", trashCmd, trashCmd);

        // 14. Módulo Anuncios y Consejos de Rendimiento Automáticos
        this.autoAnnouncer = new AutoAnnouncer(this);

        // 15. Módulo Candados Privados para Cofres y Puertas (Lockette / BlockLocker)
        this.chestLockManager = new ChestLockManager(this);
        getServer().getPluginManager().registerEvents(new ChestLockListener(this, chestLockManager), this);
        ChestLockCommand lockCmd = new ChestLockCommand(chestLockManager);
        registerPluginCommand("lock", lockCmd, lockCmd);
        registerPluginCommand("candado", lockCmd, lockCmd);
        registerPluginCommand("bloquear", lockCmd, lockCmd);
        registerPluginCommand("cprivate", lockCmd, lockCmd);
        registerPluginCommand("privado", lockCmd, lockCmd);

        // 17. Módulo Sellwands (Varitas de Venta Inteligente)
        this.sellWandManager = new SellWandManager(this);
        this.sellWandListener = new SellWandListener(this, sellWandManager);
        getServer().getPluginManager().registerEvents(sellWandListener, this);

        SellWandCommand sellWandCmd = new SellWandCommand(this, sellWandManager);
        registerPluginCommand("sellwand", sellWandCmd, sellWandCmd);
        registerPluginCommand("varaventa", sellWandCmd, sellWandCmd);
        registerPluginCommand("vara", sellWandCmd, sellWandCmd);
        registerPluginCommand("varitas", sellWandCmd, sellWandCmd);
        registerPluginCommand("sw", sellWandCmd, sellWandCmd);

        // 18. Módulo Auto-Recuperador y Sincronizador de AuraSkills (Fix No-Profile)
        this.auraSkillsFixer = new AuraSkillsFixer(this);
        this.auraSkillsListener = new AuraSkillsListener(this, auraSkillsFixer);
        getServer().getPluginManager().registerEvents(auraSkillsListener, this);
        if (auraSkillsFixer.isAuraSkillsEnabled()) {
            int loaded = auraSkillsFixer.syncAllOnlinePlayers();
            if (loaded > 0) {
                getLogger().info(" Sincronizados " + loaded + " jugadores online con perfiles faltantes en AuraSkills.");
            }
        }

        // 19. Módulo Sistema de Monturas y Mascotas Nativas (Zorro Gigante, Sniffer, Phantom Volador 3D, Panda)
        this.mountManager = new MountManager(this);
        getServer().getPluginManager().registerEvents(new MountListener(mountManager), this);

        MountCommand mountCmd = new MountCommand(mountManager);
        registerPluginCommand("monturas", mountCmd, mountCmd);
        registerPluginCommand("montura", mountCmd, mountCmd);
        registerPluginCommand("mounts", mountCmd, mountCmd);
        registerPluginCommand("mount", mountCmd, mountCmd);
        registerPluginCommand("mascotas", mountCmd, mountCmd);
        registerPluginCommand("mascota", mountCmd, mountCmd);
        registerPluginCommand("pets", mountCmd, mountCmd);
        registerPluginCommand("pet", mountCmd, mountCmd);

        // 20. Módulo Defensa de Bosses contra Proyectiles (75% Reducción)
        this.bossDefenseManager = new BossDefenseManager(this);
        this.bossDefenseListener = new BossDefenseListener(this, bossDefenseManager);
        getServer().getPluginManager().registerEvents(bossDefenseListener, this);

        BossDefenseCommand bossCmd = new BossDefenseCommand(this, bossDefenseManager);
        registerPluginCommand("bossdefense", bossCmd, bossCmd);
        registerPluginCommand("defensaboss", bossCmd, bossCmd);
        registerPluginCommand("bossdef", bossCmd, bossCmd);
        // 16. Comando Maestro AlcatrazCoreOptimizer y Limpieza de Partículas
        AlcatrazCommand alcatrazCmd = new AlcatrazCommand(this, sonicBladeManager, itemManager, lifeMendingManager, 
                newbieProtectionManager, performanceMonitor, protectionManager, mobCatcherManager, claimCmd);
        registerPluginCommand("alcatraz", alcatrazCmd, alcatrazCmd);
        registerPluginCommand("alcatrazcore", alcatrazCmd, alcatrazCmd);
        registerPluginCommand("alcatrazoptimizer", alcatrazCmd, alcatrazCmd);

        ClearParticlesCommand clearParticlesCmd = new ClearParticlesCommand(this);
        registerPluginCommand("limpiarparticulas", clearParticlesCmd, clearParticlesCmd);
        registerPluginCommand("fixpp", clearParticlesCmd, clearParticlesCmd);

                // 21. Módulo Chat Bubbles (Globos de texto estilo cómic con fondo blanco y texto negro)
        this.chatBubbleManager = new ChatBubbleManager(this);
        getServer().getPluginManager().registerEvents(new ChatBubbleListener(this, chatBubbleManager), this);
        ChatBubbleCommand chatBubbleCmd = new ChatBubbleCommand(chatBubbleManager);
        registerPluginCommand("chatbubbles", chatBubbleCmd, chatBubbleCmd);
        registerPluginCommand("cb", chatBubbleCmd, chatBubbleCmd);

        // 22. Módulo Anti-Flood de Chat (Cooldown y anti-repetición sin afectar comandos)
        this.antiFloodManager = new AntiFloodManager(this);
        getServer().getPluginManager().registerEvents(new AntiFloodListener(antiFloodManager), this);

        // 23. Módulo Filtro de Groserías Inteligente & Sistema de Mutes
        this.chatFilterManager = new ChatFilterManager(this);
        getServer().getPluginManager().registerEvents(new ChatFilterListener(chatFilterManager), this);

        // 24. Módulo Anti-Spam (Bloqueo de enlaces, IPs, discord y burst)
        this.antiSpamManager = new AntiSpamManager(this);
        getServer().getPluginManager().registerEvents(new AntiSpamListener(antiSpamManager), this);

        // 25. Módulo Anti-Mayúsculas (Anti-Caps)
        this.antiCapsManager = new AntiCapsManager(this);
        getServer().getPluginManager().registerEvents(new AntiCapsListener(antiCapsManager), this);

        // 26. Módulo Resource Pack Manager & Bedrock Fix (Descarga única & Zero-Lag Bedrock)
        this.resourcePackManager = new ResourcePackManager(this);
        this.resourcePackListener = new ResourcePackListener(this, resourcePackManager);
        getServer().getPluginManager().registerEvents(resourcePackListener, this);

        ResourcePackCommand packCmd = new ResourcePackCommand(this, resourcePackManager);
        registerPluginCommand("pack", packCmd, packCmd);
        registerPluginCommand("resourcepack", packCmd, packCmd);
        registerPluginCommand("forcebd", packCmd, packCmd);

        // 27. Módulo Anti-Xray Inteligente (Heurística & Alertas Staff)
        this.antiXrayManager = new AntiXrayManager(this);
        this.antiXrayListener = new AntiXrayListener(antiXrayManager);
        getServer().getPluginManager().registerEvents(antiXrayListener, this);

        AntiXrayCommand xrayCmd = new AntiXrayCommand(this, antiXrayManager);
        registerPluginCommand("xray", xrayCmd, xrayCmd);
        registerPluginCommand("antixray", xrayCmd, xrayCmd);

        // 28. Módulo Gancho de Agarre (Grappling Hook con Físicas Vectoriales)
        this.grapplingHookManager = new GrapplingHookManager(this);
        this.grapplingHookListener = new GrapplingHookListener(this, grapplingHookManager);
        getServer().getPluginManager().registerEvents(grapplingHookListener, this);

        GrapplingHookCommand ghookCmd = new GrapplingHookCommand(this, grapplingHookManager);
        registerPluginCommand("grapplinghook", ghookCmd, ghookCmd);
        registerPluginCommand("gancho", ghookCmd, ghookCmd);
        registerPluginCommand("ghook", ghookCmd, ghookCmd);

        // 31. Módulo Menú Interactivo de Duelos (/duel)
        this.duelMenuManager = new DuelMenuManager(this);
        this.duelMenuListener = new DuelMenuListener(duelMenuManager);
        getServer().getPluginManager().registerEvents(duelMenuListener, this);

        DuelCommand duelCmd = new DuelCommand(duelMenuManager);
        registerPluginCommand("duel", duelCmd, duelCmd);
        registerPluginCommand("duels", duelCmd, duelCmd);
        registerPluginCommand("duelo", duelCmd, duelCmd);
        registerPluginCommand("duelos", duelCmd, duelCmd);

        try {
            java.lang.reflect.Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            org.bukkit.command.CommandMap commandMap = (org.bukkit.command.CommandMap) commandMapField.get(getServer());
            org.bukkit.command.PluginCommand duelPluginCmd = getCommand("duel");
            if (duelPluginCmd != null && commandMap != null) {
                commandMap.register("alcatrazcore", duelPluginCmd);
                commandMap.register("duel", duelPluginCmd);
                commandMap.register("duels", duelPluginCmd);
            }
        } catch (Exception e) {
            getLogger().warning("No se pudo registrar alias de duelos en CommandMap: " + e.getMessage());
        }

        // 32. Módulo Evento de Halloween (calabazas HD, scatter asíncrono, panel staff)
        if (getConfig().getBoolean("halloween.enabled", false)) {
            this.halloweenManager = new HalloweenManager(this);
            getServer().getPluginManager().registerEvents(new HalloweenListener(halloweenManager), this);
            HalloweenCommand halloweenCmd = new HalloweenCommand(halloweenManager);
            registerPluginCommand("halloween", halloweenCmd, halloweenCmd);
            getLogger().info("  32. Evento de Halloween [ACTIVO]");
        } else {
            getLogger().info("  32. Evento de Halloween [DESACTIVADO — halloween.enabled: false en config.yml]");
        }

        // 16. Seguridad Anti-Dupe de Mochilas
        getServer().getPluginManager().registerEvents(new BackpackAntiDupeListener(this), this);

        // 17. Iniciar Tareas Periódicas
        startTasks();

        // Sincronizar baneos nativos desde disco
        reloadBanList();

        getLogger().info("=========================================");
        getLogger().info(" AlcatrazCoreOptimizer v" + getDescription().getVersion() + " habilitado!");
        getLogger().info(" Autor: AlcatrazM | Optimizado para Paper 1.21.x");
        getLogger().info(" Módulos de Combate y Utilidades:");
        getLogger().info("  1. Hoja Sónica (Warden Sonic Boom)");
        getLogger().info("  2. Pechera con Élitra Integrada");
        getLogger().info("  3. Life Mending Universal");
        getLogger().info("  4. Elevadores Rápidos & VoidSpawn");
        getLogger().info("  5. Anti-PvP para Nuevos Jugadores");
        getLogger().info("  6. Player Vaults Infinitos (/pv 1, 2, 3...)");
        getLogger().info("  7. Protecciones por Área 80-150b (Normales y Permanentes)");
        getLogger().info("  8. Esfera Cuántica de Mobs (Mob Catcher)");
        getLogger().info("  9. Anti-CombatLog & Portal Blocker");
        getLogger().info("  10. Papelera Virtual (/trash)");
        getLogger().info("  11. Anunciador de Rendimiento Automático");
        getLogger().info(" Módulos de Optimización Ultra-Ligera:");
        getLogger().info("  12. Descargador de Chunks de Paso (5s)");
        getLogger().info("  13. FarmControl AI & Colisiones Selectivas");
        getLogger().info("  14. Villager Optimizer (Trading Hall AI Freeze)");
        getLogger().info("  15. TNT & Falling Block Anti-Lag Throttle");
        getLogger().info("  16. Monitor de Rendimiento en Tiempo Real (/alcatraz monitor)");
        getLogger().info("  17. Defensa de Bosses contra Proyectiles (75% Reducción)");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        cancelTasks();

        if (playerVaultManager != null) {
            playerVaultManager.saveAll();
        }

        if (transientChunkUnloader != null) {
            transientChunkUnloader.stopTask();
        }

        if (farmOptimizer != null) {
            farmOptimizer.stopTask();
        }

        if (villagerOptimizer != null) {
            villagerOptimizer.stopTask();
        }

        if (autoAnnouncer != null) {
            autoAnnouncer.stopTask();
        }

        if (combatLogManager != null) {
            combatLogManager.stopTask();
        }

        if (protectionManager != null) {
            protectionManager.saveClaims();
        }

        if (chestLockManager != null) {
            chestLockManager.saveLocks();
        }

        if (sonicBladeListener != null) {
            sonicBladeListener.clearCooldowns();
        }

        if (flightListener != null) {
            flightListener.clearAll();
        }

        if (newbieProtectionManager != null) {
            newbieProtectionManager.saveData();
        }

        if (mountManager != null) {
            mountManager.dismissAll();
        }

        if (chatBubbleManager != null) {
            chatBubbleManager.cleanAll();
        }

        if (resourcePackManager != null) {
            resourcePackManager.saveData();
        }

        if (halloweenManager != null) {
            halloweenManager.savePlayerData();
            halloweenManager.saveSpawnData();
        }

        getLogger().info("AlcatrazCoreOptimizer v" + getDescription().getVersion() + " ha sido desactivado.");
        instance = null;
    }

    public void restartTasks() {
        cancelTasks();
        startTasks();
    }

    private void cancelTasks() {
        if (flightTask != null && !flightTask.isCancelled()) {
            flightTask.cancel();
            flightTask = null;
        }
        if (lifeMendingTask != null && !lifeMendingTask.isCancelled()) {
            lifeMendingTask.cancel();
            lifeMendingTask = null;
        }
    }

    private void startTasks() {
        int flightInterval = getConfig().getInt("flight.durability-loss-gliding-interval-ticks", 40);
        if (flightInterval > 0) {
            flightTask = getServer().getScheduler().runTaskTimer(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }

                    if (player.isGliding()) {
                        ItemStack chestplate = player.getInventory().getChestplate();
                        if (itemManager.isElytraChestplate(chestplate)) {
                            if (!flightListener.hasEnoughDurability(chestplate)) {
                                player.setGliding(false);
                                player.sendMessage(getConfig().getString("messages-elytra.prefix", "") + 
                                        getConfig().getString("messages-elytra.chestplate-broken", ""));
                            } else {
                                flightListener.damageChestplate(player, chestplate, 1);
                            }
                        }
                    }
                }
            }, (long) flightInterval, (long) flightInterval);
        }

        int mendingInterval = getConfig().getInt("life-mending.repair-interval-ticks", 20);
        if (mendingInterval > 0) {
            lifeMendingTask = getServer().getScheduler().runTaskTimer(this, 
                    new LifeMendingTask(this, lifeMendingManager), 
                    (long) mendingInterval, 
                    (long) mendingInterval);
        }
    }

    private void registerPluginCommand(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter completer) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (completer != null) {
                cmd.setTabCompleter(completer);
            }
        }
    }

        public ChatBubbleManager getChatBubbleManager() { return chatBubbleManager; }
    public AntiFloodManager getAntiFloodManager() { return antiFloodManager; }
    public ChatFilterManager getChatFilterManager() { return chatFilterManager; }
    public AntiCapsManager getAntiCapsManager() { return antiCapsManager; }
    public AntiSpamManager getAntiSpamManager() { return antiSpamManager; }
    public SonicBladeManager getSonicBladeManager() { return sonicBladeManager; }
    public ItemManager getItemManager() { return itemManager; }
    public LifeMendingManager getLifeMendingManager() { return lifeMendingManager; }
    public ElevatorListener getElevatorListener() { return elevatorListener; }
    public VoidSpawnListener getVoidSpawnListener() { return voidSpawnListener; }
    public NewbieProtectionManager getNewbieProtectionManager() { return newbieProtectionManager; }
    public TransientChunkUnloader getTransientChunkUnloader() { return transientChunkUnloader; }
    public FarmOptimizer getFarmOptimizer() { return farmOptimizer; }
    public ItemAndXPStacker getItemAndXPStacker() { return itemAndXPStacker; }
    public RedstoneLimiter getRedstoneLimiter() { return redstoneLimiter; }
    public VillagerOptimizer getVillagerOptimizer() { return villagerOptimizer; }
    public TNTOptimizer getTntOptimizer() { return tntOptimizer; }
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    public PlayerVaultManager getPlayerVaultManager() { return playerVaultManager; }
    public ProtectionManager getProtectionManager() { return protectionManager; }
    public MobCatcherManager getMobCatcherManager() { return mobCatcherManager; }
    public CombatLogManager getCombatLogManager() { return combatLogManager; }
    public TrashManager getTrashManager() { return trashManager; }
    public AutoAnnouncer getAutoAnnouncer() { return autoAnnouncer; }
    public ChestLockManager getChestLockManager() { return chestLockManager; }
    public SellWandManager getSellWandManager() { return sellWandManager; }
    public AuraSkillsFixer getAuraSkillsFixer() { return auraSkillsFixer; }
    public MountManager getMountManager() { return mountManager; }
    public BossDefenseManager getBossDefenseManager() { return bossDefenseManager; }
    public ResourcePackManager getResourcePackManager() { return resourcePackManager; }
    public AntiXrayManager getAntiXrayManager() { return antiXrayManager; }
    public GrapplingHookManager getGrapplingHookManager() { return grapplingHookManager; }
    public DuelMenuManager getDuelMenuManager() { return duelMenuManager; }
    public HalloweenManager getHalloweenManager() { return halloweenManager; }

    public void reloadBanList() {
        try {
            Object playerList = getServer().getClass().getMethod("getHandle").invoke(getServer());
            Object bans = playerList.getClass().getMethod("getBans").invoke(playerList);
            bans.getClass().getMethod("load").invoke(bans);
            getLogger().info(" Lista de baneos nativa sincronizada con éxito desde el disco.");
        } catch (Exception e) {
            getLogger().warning("No se pudo recargar la lista de baneos: " + e.getMessage());
        }
    }
}
