package com.alcatrazm.plugin.airdrop;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Panel GUI administrativo multi-pagina para configurar y lanzar AirDrops desde el juego.
 */
public final class MenuManager implements Listener {
    private final AirDropModule module;
    private final Map<UUID, ChatPrompt> activePrompts = new ConcurrentHashMap<>();
    private UUID lootEditor;

    public MenuManager(AirDropModule module) {
        this.module = module;
    }

    private boolean isAdmin(Player player) {
        return player.hasPermission("alcatraz.airdrop.admin")
                || player.hasPermission("universairdrop.admin")
                || player.hasPermission("alcatraz.admin");
    }

    private boolean canLaunch(Player player) {
        return player.hasPermission("alcatraz.airdrop.launch")
                || player.hasPermission("universairdrop.launch")
                || isAdmin(player);
    }

    public void openMenu(Player player, Page page) {
        this.openMenu(player, page, 0, null, null);
    }

    public void openMenu(Player player, Page page, String tierId) {
        this.openMenu(player, page, 0, null, tierId);
    }

    public void openMenu(Player player, Page page, int pageIndex, List<ItemStack> customItems) {
        this.openMenu(player, page, pageIndex, customItems, null);
    }

    public void openMenu(Player player, Page page, int pageIndex, List<ItemStack> customItems, String tierId) {
        if (!isAdmin(player)) {
            return;
        }

        String resolvedTier = (tierId == null || tierId.isBlank()) ? "comun" : DropTier.normalizeId(tierId);

        if (page == Page.LOOT_CONTENT && this.lootEditor != null && !this.lootEditor.equals(player.getUniqueId())) {
            this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().error("Otro administrador esta editando el contenido"));
            return;
        }

        if (page == Page.LOOT_CONTENT && customItems == null) {
            customItems = new ArrayList<>(this.module.getLootManager().getItems(resolvedTier));
        }

        MenuSession session = new MenuSession(page, pageIndex, customItems, resolvedTier);
        String title = switch (page) {
            case MAIN -> "ALCATRAZ AIRDROP | PANEL";
            case LAUNCH -> "LANZAR DROP";
            case AUTOMATIC -> "AUTOMATICOS";
            case LOOT_TIER_SELECT -> "ESCOGER RAREZA | BOTIN";
            case LOOT_CONTENT -> "BOTIN " + resolvedTier.toUpperCase() + " | " + (pageIndex + 1);
            case LOCATION -> "UBICACION Y DIMENSIONES";
            case CHOOSE_WORLD -> "ESCOGER MUNDO";
            case LIMITS -> "LIMITES Y RAREZAS";
            case TIER_EDIT -> "AJUSTES | " + resolvedTier.toUpperCase();
            case PRESENTATION -> "PRESENTACION";
            case PARTICLES -> "EFECTOS DE PARTICULAS";
            case NAME_DESC -> "NOMBRE Y DESCRIPCION";
            case ACTIVE_DROPS -> "DROPS ACTIVOS";
        };

        session.inventory = Bukkit.createInventory(session, 54, this.module.getMessageFormatter().gradient(title));
        if (page != Page.LOOT_CONTENT) {
            for (int i = 0; i < 54; i++) {
                session.inventory.setItem(i, this.emptyPane(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        this.updateHeaderFooter(session, this.module.getTicks());
        this.populatePage(session);

        if (page == Page.PRESENTATION) {
            this.refreshCinematicToggle(session);
        }
        if (page == Page.LAUNCH) {
            this.renderStatusItem(session, 31);
        }
        if (page == Page.AUTOMATIC) {
            this.renderNextAutoItem(session, 31, this.module.getTicks());
        }

        session.inventory.setItem(49, this.module.getMessageFormatter().createGuiItem(Material.SPECTRAL_ARROW, "VOLVER", "Click para volver al panel"));
        session.inventory.setItem(53, this.module.getMessageFormatter().createGuiItem(Material.BARRIER, "CERRAR", "Los cambios se guardan al instante"));

        if (page == Page.LOOT_CONTENT) {
            session.inventory.setItem(45, this.module.getMessageFormatter().createGuiItem(Material.ARROW, "ANTERIOR", "Pagina " + Math.max(1, pageIndex)));
            session.inventory.setItem(48, this.module.getMessageFormatter().createGuiItem(Material.KNOWLEDGE_BOOK, "EDITAR OBJETOS",
                "Click en tu inventario: Copiar",
                "Click en el panel: Quitar",
                "Se conservan nombres y encantamientos"));
            session.inventory.setItem(51, this.module.getMessageFormatter().createGuiItem(Material.EMERALD, "GUARDAR", "Guardar y volver"));
            session.inventory.setItem(52, this.module.getMessageFormatter().createGuiItem(Material.ARROW, "SIGUIENTE", "Agregar mas objetos"));
        }

        player.openInventory(session.inventory);
        if (page == Page.LOOT_CONTENT) {
            this.lootEditor = player.getUniqueId();
        }
        this.module.getMessageFormatter().playSound(player, Sound.UI_BUTTON_CLICK, 0.45F, 1.15F);
    }

    private ItemStack emptyPane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getConfigVal(String path) {
        return String.valueOf(this.module.getSetting(path));
    }

    private String getStatus(String path) {
        return this.module.getBoolean(path, false) ? "ACTIVADO" : "DESACTIVADO";
    }

    private void addToggleItem(MenuSession s, int slot, String path, String title, boolean defaultVal) {
        boolean val = this.module.getBoolean(path, defaultVal);
        Material mat = val ? Material.LIME_DYE : Material.GRAY_DYE;
        s.inventory.setItem(slot, this.module.getMessageFormatter().createGuiItem(mat, title, "Estado: " + (val ? "ACTIVADO" : "DESACTIVADO"), "Click para cambiar"));
    }

    private void addToggleItem(MenuSession s, int slot, String path, String title) {
        this.addToggleItem(s, slot, path, title, false);
    }

    private void addValueItem(MenuSession s, int slot, Material mat, String path, String title, String unit) {
        String suffix = unit == null || unit.isEmpty() ? "" : " " + unit;
        s.inventory.setItem(slot, this.module.getMessageFormatter().createGuiItem(mat, title, "Valor: " + this.getConfigVal(path) + suffix, "Click para editar"));
    }

    private void populatePage(MenuSession s) {
        switch (s.page) {
            case MAIN:
                s.inventory.setItem(10, this.module.getMessageFormatter().createGuiItem(Material.ENDER_CHEST, "LANZAR DROP", "Aleatorio o por rareza"));
                s.inventory.setItem(12, this.module.getMessageFormatter().createGuiItem(Material.CLOCK, "AUTOMATICOS", "Estado: " + this.getStatus("automatic.enabled"), "Intervalo: " + this.getConfigVal("automatic.interval-minutes") + " min", "Modo: " + this.getConfigVal("automatic.mode")));
                s.inventory.setItem(14, this.module.getMessageFormatter().createGuiItem(Material.CHEST, "CONTENIDO", this.module.getLootManager().getTotalItemCount() + " objetos en pools", "Editar Común, Épico y Legendario"));
                s.inventory.setItem(16, this.module.getMessageFormatter().createGuiItem(Material.COMPASS, "UBICACION Y DIMENSIONES", "Mundo: " + this.getConfigVal("location.world"), "Overworld=" + this.module.getBoolean("location.dimensions.overworld", true) + " | Nether=" + this.module.getBoolean("location.dimensions.nether", false) + " | End=" + this.module.getBoolean("location.dimensions.end", false)));
                s.inventory.setItem(28, this.module.getMessageFormatter().createGuiItem(Material.BLAZE_POWDER, "PRESENTACION", "Cinematica | Sonidos | Efectos"));
                s.inventory.setItem(30, this.module.getMessageFormatter().createGuiItem(Material.COMPARATOR, "LIMITES Y RAREZAS", "Probabilidades, duracion y limites"));
                s.inventory.setItem(32, this.module.getMessageFormatter().createGuiItem(Material.ENDER_EYE, "DROPS ACTIVOS", this.module.getAirDropManager().getActiveAndPendingCount() + " drops en curso", "Visitar o cancelar"));
                s.inventory.setItem(34, this.module.getMessageFormatter().createGuiItem(Material.NAME_TAG, "NOMBRE Y DESCRIPCION", "Editar textos y colores del drop"));
                this.renderStatusItem(s, 40);
                s.inventory.setItem(42, this.module.getMessageFormatter().createGuiItem(Material.NETHER_STAR, "MODO ALCATRAZ", "Integrado en ModuleRegistry", "Protecciones GP/WG activas"));
                break;
            case LAUNCH: {
                s.inventory.setItem(13, this.module.getMessageFormatter().createGuiItem(Material.FIREWORK_ROCKET, "LANZAR ALEATORIO", "Sortea según probabilidad", "Mundo: " + this.getConfigVal("location.world")));
                List<DropTier> tiers = this.module.getAirDropManager().getTiers();
                int[] slots = {20, 22, 24, 29, 31, 33};
                for (int i = 0; i < tiers.size() && i < slots.length; i++) {
                    DropTier t = tiers.get(i);
                    String pct = String.format(java.util.Locale.ROOT, "%.0f%%", DropTier.getChancePercent(tiers, t));
                    s.inventory.setItem(slots[i], this.module.getMessageFormatter().createGuiItem(
                        t.blockMaterial(),
                        t.displayName(),
                        t.colorStart(),
                        t.colorEnd(),
                        "Probabilidad: " + pct + " | Limite: " + t.expiryMinutes() + " min",
                        "Items: " + t.minItems() + "-" + t.maxItems() + " | Pool: " + this.module.getLootManager().getItemCount(t.id()),
                        "Click para lanzar ahora"
                    ));
                }
                break;
            }
            case AUTOMATIC:
                this.addToggleItem(s, 10, "automatic.enabled", "AUTOMATICOS");
                this.addValueItem(s, 12, Material.CLOCK, "automatic.interval-minutes", "INTERVALO", "min");
                s.inventory.setItem(14, this.module.getMessageFormatter().createGuiItem(Material.TRIPWIRE_HOOK, "MODO: " + this.getConfigVal("automatic.mode"), "Click para alternar (ALEATORIO / rareza)"));
                this.addToggleItem(s, 16, "automatic.require-online-players", "REQUIERE JUGADORES");
                this.renderNextAutoItem(s, 31, this.module.getTicks());
                break;
            case LOOT_TIER_SELECT: {
                List<DropTier> tiers = this.module.getAirDropManager().getTiers();
                int[] slots = {20, 22, 24, 29, 31, 33};
                for (int i = 0; i < tiers.size() && i < slots.length; i++) {
                    DropTier t = tiers.get(i);
                    s.inventory.setItem(slots[i], this.module.getMessageFormatter().createGuiItem(
                        t.blockMaterial(),
                        "EDITAR " + t.displayName(),
                        t.colorStart(),
                        t.colorEnd(),
                        "Objetos configurados: " + this.module.getLootManager().getItemCount(t.id()),
                        "Cantidad por cofre: " + t.minItems() + " - " + t.maxItems(),
                        "Click para abrir editor de botin"
                    ));
                }
                break;
            }
            case LOOT_CONTENT:
                for (int i = 0; i < 45; i++) {
                    int idx = s.pageIndex * 45 + i;
                    if (idx < s.customItems.size()) {
                        s.inventory.setItem(i, s.customItems.get(idx));
                    }
                }
                break;
            case LOCATION:
                s.inventory.setItem(10, this.module.getMessageFormatter().createGuiItem(Material.GRASS_BLOCK, "MUNDO", "Actual: " + this.getConfigVal("location.world"), "Click para escoger"));
                this.addValueItem(s, 12, Material.COMPASS, "location.radius", "RADIO DESDE 0, 0", "bloques");
                this.addToggleItem(s, 14, "location.avoid-water", "EVITAR AGUA");
                this.addToggleItem(s, 16, "location.avoid-leaves", "EVITAR HOJAS");
                this.addValueItem(s, 19, Material.LEVER, "location.maximum-slope", "DESNIVEL MAXIMO", "bloques");
                this.addValueItem(s, 21, Material.REPEATER, "location.attempts", "INTENTOS DE BUSQUEDA", "intentos");
                this.addToggleItem(s, 23, "location.avoid-protected-regions", "EVITAR CLAIMS Y REGIONES");
                this.addToggleItem(s, 28, "location.dimensions.overworld", "DIMENSION OVERWORLD", true);
                this.addToggleItem(s, 30, "location.dimensions.nether", "DIMENSION NETHER", false);
                this.addToggleItem(s, 32, "location.dimensions.end", "DIMENSION END", false);
                break;
            case CHOOSE_WORLD:
                List<World> worlds = Bukkit.getWorlds();
                for (int i = 0; i < worlds.size() && i < 36; i++) {
                    World w = worlds.get(i);
                    boolean allowed = this.module.getAirDropManager().isDimensionAllowed(w.getEnvironment());
                    s.inventory.setItem(9 + i, this.module.getMessageFormatter().createGuiItem(Material.GRASS_BLOCK, w.getName(), "Dimension: " + w.getEnvironment().name() + (allowed ? " (DESBLOQUEADA)" : " (BLOQUEADA)"), "Click para seleccionar este mundo"));
                }
                break;
            case LIMITS: {
                this.addValueItem(s, 11, Material.COMPARATOR, "drop.max-active", "MAXIMO DROPS ACTIVOS", "drops");
                this.addValueItem(s, 15, Material.COMPASS, "safe.interaction-distance", "DISTANCIA DE APERTURA", "bloques");
                List<DropTier> tiers = this.module.getAirDropManager().getTiers();
                int[] slots = {29, 31, 33, 38, 40, 42};
                for (int i = 0; i < tiers.size() && i < slots.length; i++) {
                    DropTier t = tiers.get(i);
                    String pct = String.format(java.util.Locale.ROOT, "%.0f%%", DropTier.getChancePercent(tiers, t));
                    s.inventory.setItem(slots[i], this.module.getMessageFormatter().createGuiItem(
                        t.blockMaterial(),
                        "AJUSTAR " + t.displayName(),
                        t.colorStart(),
                        t.colorEnd(),
                        "Estado: " + (t.enabled() ? "ACTIVADO" : "DESACTIVADO"),
                        "Probabilidad: " + t.probability() + " (" + pct + ")",
                        "Duracion: " + t.expiryMinutes() + " min",
                        "Items: " + t.minItems() + " - " + t.maxItems(),
                        "Click para configurar"
                    ));
                }
                break;
            }
            case TIER_EDIT: {
                String base = "tiers." + s.tierId + ".";
                this.addToggleItem(s, 10, base + "enabled", "RAREZA ACTIVA", true);
                this.addValueItem(s, 12, Material.EMERALD, base + "probability", "PROBABILIDAD (PESO)", "");
                this.addValueItem(s, 14, Material.CLOCK, base + "expiry-minutes", "DURACION DEL DROP", "min");
                this.addValueItem(s, 16, Material.CHEST, base + "min-items", "MINIMO DE OBJETOS", "items");
                this.addValueItem(s, 29, Material.ENDER_CHEST, base + "max-items", "MAXIMO DE OBJETOS", "items");
                s.inventory.setItem(33, this.module.getMessageFormatter().createGuiItem(Material.BOOK, "EDITAR BOTIN", "Abrir inventario de " + s.tierId.toUpperCase()));
                break;
            }
            case PRESENTATION:
                this.addToggleItem(s, 10, "cinematic.enabled", "CINEMATICA");
                this.addToggleItem(s, 12, "presentation.sounds", "SONIDOS");
                this.addToggleItem(s, 14, "presentation.particles", "PARTICULAS");
                this.addToggleItem(s, 16, "presentation.titles", "TITULOS");
                this.addToggleItem(s, 19, "presentation.holograms", "HOLOGRAMAS");
                s.inventory.setItem(21, this.module.getMessageFormatter().createGuiItem(Material.FIREWORK_STAR, "ESCOGER EFECTO", "ORBITA DORADA", "Click para ver"));
                this.addValueItem(s, 23, Material.CLOCK, "drop.countdown-seconds", "CUENTA REGRESIVA", "s");
                this.addValueItem(s, 25, Material.FEATHER, "drop.fall-seconds", "TIEMPO DE CAIDA", "s");
                this.addValueItem(s, 28, Material.PHANTOM_MEMBRANE, "drop.fall-height", "ALTURA DE CAIDA", "bloques");
                this.addValueItem(s, 30, Material.SPYGLASS, "cinematic.seconds", "DURACION DE CAMARA", "s");
                this.addValueItem(s, 32, Material.ENDER_PEARL, "cinematic.orbit-radius", "RADIO DE CAMARA", "bloques");
                this.addValueItem(s, 34, Material.BLAZE_POWDER, "presentation.particle-density", "DENSIDAD DE PARTICULAS", "");
                this.addValueItem(s, 37, Material.FEATHER, "presentation.chest-bob-height", "MOVIMIENTO DEL COFRE", "bloques");
                this.addValueItem(s, 39, Material.FIREWORK_STAR, "presentation.effect-radius", "RADIO DEL EFECTO", "bloques");
                this.addValueItem(s, 41, Material.SHIELD, "cinematic.combat-grace-seconds", "PAUSA TRAS RECIBIR DANO", "s");
                break;
            case PARTICLES:
                s.inventory.setItem(10, this.module.getMessageFormatter().createGuiItem(Material.GOLD_NUGGET, "ORBITA DORADA", "SELECCIONADO"));
                this.addToggleItem(s, 31, "presentation.particles", "PARTICULAS");
                break;
            case NAME_DESC:
                s.inventory.setItem(10, this.module.getMessageFormatter().createGuiItem(Material.YELLOW_DYE, "DORADO INICIAL", this.getConfigVal("presentation.primary-start"), "Click para editar"));
                s.inventory.setItem(12, this.module.getMessageFormatter().createGuiItem(Material.ORANGE_DYE, "DORADO FINAL", this.getConfigVal("presentation.primary-end"), "Click para editar"));
                s.inventory.setItem(14, this.module.getMessageFormatter().createGuiItem(Material.LIGHT_GRAY_DYE, "DETALLES GRISES", this.getConfigVal("presentation.detail-color"), "Click para editar"));
                s.inventory.setItem(16, this.module.getMessageFormatter().createGuiItem(Material.LIME_DYE, "COLOR DE EXITO", this.getConfigVal("presentation.success-color"), "Click para editar"));
                s.inventory.setItem(19, this.module.getMessageFormatter().createGuiItem(Material.LIGHT_BLUE_DYE, "COLOR INFORMATIVO", this.getConfigVal("presentation.info-color"), "Click para editar"));
                s.inventory.setItem(21, this.module.getMessageFormatter().createGuiItem(Material.RED_DYE, "COLOR DE ERROR", this.getConfigVal("presentation.error-color"), "Click para editar"));
                s.inventory.setItem(23, this.module.getMessageFormatter().createGuiItem(Material.NAME_TAG, "NOMBRE DEL DROP", this.getConfigVal("drop.name"), "Click para editar"));
                s.inventory.setItem(25, this.module.getMessageFormatter().createGuiItem(Material.PAPER, "DESCRIPCION DEL DROP", this.getConfigVal("drop.description"), "Click para editar"));
                s.inventory.setItem(28, this.module.getMessageFormatter().createGuiItem(Material.NAME_TAG, "TITULO DE CUENTA", this.getConfigVal("messages.countdown-title"), "Usa <seconds> para el contador", "Click para editar"));
                s.inventory.setItem(30, this.module.getMessageFormatter().createGuiItem(Material.PAPER, "SUBTITULO DE CUENTA", this.getConfigVal("messages.countdown-subtitle"), "Click para editar"));
                break;
            case ACTIVE_DROPS:
                int slot = 9;
                for (DropInstance drop : this.module.getAirDropManager().getActiveDrops()) {
                    if (slot >= 45) break;
                    s.inventory.setItem(slot++, this.module.getMessageFormatter().createGuiItem(
                        Material.ENDER_CHEST,
                        "DROP " + drop.id,
                        drop.state.name() + " | " + drop.type.name(),
                        "X: " + drop.location.getBlockX() + " | Z: " + drop.location.getBlockZ(),
                        "Click izquierdo: Visitar",
                        "Shift + Click derecho: Cancelar"
                    ));
                }
                break;
        }
    }

    private void updateHeaderFooter(MenuSession s, long tick) {
        int highlight = (int) (tick / 10L) % 9;
        if (s.page != Page.LOOT_CONTENT) {
            for (int i = 0; i < 9; i++) {
                s.inventory.setItem(i, this.emptyPane(i == highlight ? Material.YELLOW_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
            }
        }
        for (int i = 45; i < 54; i++) {
            if (i != 49 && i != 53 && (s.page != Page.LOOT_CONTENT || (i != 45 && i != 48 && i != 51 && i != 52))) {
                s.inventory.setItem(i, this.emptyPane((i - 45) == highlight ? Material.YELLOW_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
            }
        }
    }

    private void renderStatusItem(MenuSession s, int slot) {
        boolean autoOn = this.module.getBoolean("automatic.enabled", false);
        long remSecs = this.module.getAirDropManager().getSecondsUntilNextAutoDrop(this.module.getTicks());
        String nextText = autoOn ? MessageFormatter.formatDuration(remSecs * 1000L) : "Desactivado";
        s.inventory.setItem(slot, this.module.getMessageFormatter().createGuiItem(
            Material.CLOCK,
            "ESTADO DEL MODULO",
            "Drops activos: " + this.module.getAirDropManager().getActiveAndPendingCount() + "/" + this.getConfigVal("drop.max-active"),
            "Proximo automatico: " + nextText,
            "Modo: AlcatrazCoreOptimizer"
        ));
    }

    private void renderNextAutoItem(MenuSession s, int slot, long tick) {
        boolean autoOn = this.module.getBoolean("automatic.enabled", false);
        long remSecs = this.module.getAirDropManager().getSecondsUntilNextAutoDrop(tick);
        String timeStr = autoOn ? MessageFormatter.formatDuration(remSecs * 1000L) : "Desactivado";
        s.inventory.setItem(slot, this.module.getMessageFormatter().createGuiItem(
            Material.PAPER,
            "PROXIMO DROP",
            "Tiempo: " + timeStr,
            "Usa el mismo contenido configurado"
        ));
    }

    public void tick(long tick) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuSession s) {
                this.updateHeaderFooter(s, tick);
                if (tick % 20L == 0L) {
                    if (s.page == Page.MAIN) this.renderStatusItem(s, 40);
                    if (s.page == Page.LAUNCH) this.renderStatusItem(s, 31);
                    if (s.page == Page.AUTOMATIC) this.renderNextAutoItem(s, 31, tick);
                    if (s.page == Page.PRESENTATION) this.refreshCinematicToggle(s);
                }
            }
        }
    }

    private void refreshCinematicToggle(MenuSession s) {
        s.inventory.setItem(10, this.module.getMessageFormatter().createGuiItem(
            this.module.getCinematicManager().isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
            "CINEMATICA",
            "Estado: " + (this.module.getCinematicManager().isEnabled() ? "ACTIVADO" : "DESACTIVADO"),
            "Click para alternar"
        ));
    }

    private void toggleBoolean(Player player, MenuSession s, String path) {
        this.toggleBoolean(player, s, path, false);
    }

    private void toggleBoolean(Player player, MenuSession s, String path, boolean defaultVal) {
        boolean curr = this.module.getBoolean(path, defaultVal);
        this.module.setSetting(path, !curr);
        this.module.getPlugin().saveConfig();
        if ("automatic.enabled".equals(path)) {
            this.module.getAirDropManager().resetAutoSchedule(this.module.getTicks());
        }
        this.openMenu(player, s.page, s.tierId);
    }

    private void promptChat(Player player, MenuSession s, String path, String title, double min, double max, String type) {
        this.activePrompts.put(player.getUniqueId(), new ChatPrompt(path, title, min, max, s.page, s.tierId, type));
        player.closeInventory();
        this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().gradient(title));
        String hint;
        if ("color".equals(type)) {
            hint = "Escribe un color HEX (ej. #F8CB53)";
        } else if ("text".equals(type)) {
            hint = "Escribe el nuevo texto";
        } else if ("double".equals(type)) {
            hint = "Escribe un numero decimal entre " + min + " y " + max;
        } else {
            hint = "Escribe un valor entero entre " + (int) min + " y " + (int) max;
        }
        this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().detail(hint));
        this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().detail("Escribe 'cancelar' para volver"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuSession s && event.getWhoClicked() instanceof Player player) {
            event.setCancelled(true);
            if (!isAdmin(player)) {
                Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> player.closeInventory());
                return;
            }
            int rawSlot = event.getRawSlot();
            ItemStack currentItem = event.getCurrentItem() != null ? event.getCurrentItem().clone() : null;
            boolean leftClick = event.isLeftClick();
            boolean rightClick = event.isRightClick();
            boolean shiftClick = event.isShiftClick();
            Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> {
                if (player.isOnline() && isAdmin(player) && player.getOpenInventory().getTopInventory() == s.inventory) {
                    this.handleClick(player, s, rawSlot, currentItem, leftClick, rightClick, shiftClick);
                }
            });
        }
    }

    private void handleClick(Player player, MenuSession s, int slot, ItemStack clickedItem, boolean leftClick, boolean rightClick, boolean shiftClick) {
        if (slot < 0) return;

        if (s.page == Page.LOOT_CONTENT) {
            this.handleLootClick(player, s, slot, clickedItem);
            return;
        }

        if (slot < 54) {
            if (slot == 53) {
                player.closeInventory();
            } else if (slot == 49) {
                Page back = switch (s.page) {
                    case PARTICLES -> Page.PRESENTATION;
                    case TIER_EDIT -> Page.LIMITS;
                    case CHOOSE_WORLD -> Page.LOCATION;
                    default -> Page.MAIN;
                };
                this.openMenu(player, back, s.tierId);
            } else {
                switch (s.page) {
                    case MAIN -> {
                        switch (slot) {
                            case 10 -> this.openMenu(player, Page.LAUNCH);
                            case 12 -> this.openMenu(player, Page.AUTOMATIC);
                            case 14 -> this.openMenu(player, Page.LOOT_TIER_SELECT);
                            case 16 -> this.openMenu(player, Page.LOCATION);
                            case 28 -> this.openMenu(player, Page.PRESENTATION);
                            case 30 -> this.openMenu(player, Page.LIMITS);
                            case 32 -> this.openMenu(player, Page.ACTIVE_DROPS);
                            case 34 -> this.openMenu(player, Page.NAME_DESC);
                        }
                    }
                    case LAUNCH -> {
                        if (slot == 13) {
                            player.closeInventory();
                            if (canLaunch(player)) {
                                this.module.getAirDropManager().launch("ALEATORIO", player);
                            } else {
                                this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().error(this.module.getString("messages.no-permission", "No tienes permiso")));
                            }
                        } else {
                            int[] slots = {20, 22, 24, 29, 31, 33};
                            List<DropTier> tiers = this.module.getAirDropManager().getTiers();
                            for (int i = 0; i < slots.length && i < tiers.size(); i++) {
                                if (slot == slots[i]) {
                                    player.closeInventory();
                                    if (canLaunch(player)) {
                                        this.module.getAirDropManager().launch(tiers.get(i).id(), player);
                                    } else {
                                        this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().error(this.module.getString("messages.no-permission", "No tienes permiso")));
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    case LOOT_TIER_SELECT -> {
                        int[] slots = {20, 22, 24, 29, 31, 33};
                        List<DropTier> tiers = this.module.getAirDropManager().getTiers();
                        for (int i = 0; i < slots.length && i < tiers.size(); i++) {
                            if (slot == slots[i]) {
                                this.openMenu(player, Page.LOOT_CONTENT, tiers.get(i).id());
                                break;
                            }
                        }
                    }
                    case AUTOMATIC -> {
                        if (slot == 10) this.toggleBoolean(player, s, "automatic.enabled");
                        else if (slot == 12) this.promptChat(player, s, "automatic.interval-minutes", "INTERVALO", 1, 1440, "int");
                        else if (slot == 14) {
                            List<String> modes = new ArrayList<>();
                            modes.add("ALEATORIO");
                            for (DropTier t : this.module.getAirDropManager().getTiers()) {
                                modes.add(t.id());
                            }
                            String curMode = this.module.getString("automatic.mode", "ALEATORIO");
                            int idx = 0;
                            for (int i = 0; i < modes.size(); i++) {
                                if (modes.get(i).equalsIgnoreCase(curMode)) {
                                    idx = (i + 1) % modes.size();
                                    break;
                                }
                            }
                            this.module.setSetting("automatic.mode", modes.get(idx));
                            this.module.getPlugin().saveConfig();
                            this.openMenu(player, Page.AUTOMATIC);
                        } else if (slot == 16) this.toggleBoolean(player, s, "automatic.require-online-players");
                    }
                    case LOCATION -> {
                        switch (slot) {
                            case 10 -> this.openMenu(player, Page.CHOOSE_WORLD);
                            case 12 -> this.promptChat(player, s, "location.radius", "RADIO DESDE 0, 0", 16, 30000000, "int");
                            case 14 -> this.toggleBoolean(player, s, "location.avoid-water");
                            case 16 -> this.toggleBoolean(player, s, "location.avoid-leaves");
                            case 19 -> this.promptChat(player, s, "location.maximum-slope", "DESNIVEL MAXIMO", 0, 30, "int");
                            case 21 -> this.promptChat(player, s, "location.attempts", "INTENTOS DE BUSQUEDA", 1, 500, "int");
                            case 23 -> this.toggleBoolean(player, s, "location.avoid-protected-regions");
                            case 28 -> this.toggleBoolean(player, s, "location.dimensions.overworld", true);
                            case 30 -> this.toggleBoolean(player, s, "location.dimensions.nether", false);
                            case 32 -> this.toggleBoolean(player, s, "location.dimensions.end", false);
                        }
                    }
                    case CHOOSE_WORLD -> {
                        int idx = slot - 9;
                        if (idx >= 0 && idx < Math.min(36, Bukkit.getWorlds().size())) {
                            this.module.setSetting("location.world", Bukkit.getWorlds().get(idx).getName());
                            this.module.getPlugin().saveConfig();
                            this.openMenu(player, Page.LOCATION);
                        }
                    }
                    case LIMITS -> {
                        if (slot == 11) this.promptChat(player, s, "drop.max-active", "MAXIMO DROPS ACTIVOS", 1, 20, "int");
                        else if (slot == 15) this.promptChat(player, s, "safe.interaction-distance", "DISTANCIA DE APERTURA", 1.5, 16.0, "double");
                        else {
                            int[] slots = {29, 31, 33, 38, 40, 42};
                            List<DropTier> tiers = this.module.getAirDropManager().getTiers();
                            for (int i = 0; i < slots.length && i < tiers.size(); i++) {
                                if (slot == slots[i]) {
                                    this.openMenu(player, Page.TIER_EDIT, tiers.get(i).id());
                                    break;
                                }
                            }
                        }
                    }
                    case TIER_EDIT -> {
                        String base = "tiers." + s.tierId + ".";
                        if (slot == 10) this.toggleBoolean(player, s, base + "enabled", true);
                        else if (slot == 12) this.promptChat(player, s, base + "probability", "PROBABILIDAD (PESO)", 0, 10000, "int");
                        else if (slot == 14) this.promptChat(player, s, base + "expiry-minutes", "DURACION DEL DROP", 1, 1440, "int");
                        else if (slot == 16) this.promptChat(player, s, base + "min-items", "MINIMO DE OBJETOS", 1, 54, "int");
                        else if (slot == 29) this.promptChat(player, s, base + "max-items", "MAXIMO DE OBJETOS", 1, 54, "int");
                        else if (slot == 33) this.openMenu(player, Page.LOOT_CONTENT, s.tierId);
                    }
                    case PRESENTATION -> {
                        if (slot == 10) this.toggleBoolean(player, s, "cinematic.enabled");
                        else if (slot == 12) this.toggleBoolean(player, s, "presentation.sounds");
                        else if (slot == 14) this.toggleBoolean(player, s, "presentation.particles");
                        else if (slot == 16) this.toggleBoolean(player, s, "presentation.titles");
                        else if (slot == 19) this.toggleBoolean(player, s, "presentation.holograms");
                        else if (slot == 21) this.openMenu(player, Page.PARTICLES);
                        else if (slot == 23) this.promptChat(player, s, "drop.countdown-seconds", "CUENTA REGRESIVA", 0, 120, "int");
                        else if (slot == 25) this.promptChat(player, s, "drop.fall-seconds", "TIEMPO DE CAIDA", 0, 30, "int");
                        else if (slot == 28) this.promptChat(player, s, "drop.fall-height", "ALTURA DE CAIDA", 8, 150, "int");
                        else if (slot == 30) this.promptChat(player, s, "cinematic.seconds", "DURACION DE CAMARA", 2, 20, "int");
                        else if (slot == 32) this.promptChat(player, s, "cinematic.orbit-radius", "RADIO DE CAMARA", 4.0, 16.0, "double");
                        else if (slot == 34) this.promptChat(player, s, "presentation.particle-density", "DENSIDAD DE PARTICULAS", 1, 4, "int");
                        else if (slot == 37) this.promptChat(player, s, "presentation.chest-bob-height", "MOVIMIENTO DEL COFRE", 0.0, 0.5, "double");
                        else if (slot == 39) this.promptChat(player, s, "presentation.effect-radius", "RADIO DEL EFECTO", 0.5, 6.0, "double");
                        else if (slot == 41) this.promptChat(player, s, "cinematic.combat-grace-seconds", "PAUSA TRAS DANO", 0, 120, "int");
                    }
                    case PARTICLES -> {
                        if (slot == 31) this.toggleBoolean(player, s, "presentation.particles");
                    }
                    case ACTIVE_DROPS -> {
                        List<DropInstance> drops = new ArrayList<>(this.module.getAirDropManager().getActiveDrops());
                        int idx = slot - 9;
                        if (idx >= 0 && idx < drops.size()) {
                            DropInstance d = drops.get(idx);
                            if (shiftClick && rightClick) {
                                this.module.getAirDropManager().removeDrop(d, "cancelado");
                                this.openMenu(player, Page.ACTIVE_DROPS);
                            } else if (leftClick) {
                                player.closeInventory();
                                this.module.getCinematicManager().stopCinematic(player.getUniqueId());
                                player.teleport(d.location.clone().add(3.0, 1.0, 0.0));
                            }
                        }
                    }
                    case NAME_DESC -> {
                        if (slot == 10) this.promptChat(player, s, "presentation.primary-start", "DORADO INICIAL", 0, 0, "color");
                        else if (slot == 12) this.promptChat(player, s, "presentation.primary-end", "DORADO FINAL", 0, 0, "color");
                        else if (slot == 14) this.promptChat(player, s, "presentation.detail-color", "DETALLES GRISES", 0, 0, "color");
                        else if (slot == 16) this.promptChat(player, s, "presentation.success-color", "COLOR DE EXITO", 0, 0, "color");
                        else if (slot == 19) this.promptChat(player, s, "presentation.info-color", "COLOR INFORMATIVO", 0, 0, "color");
                        else if (slot == 21) this.promptChat(player, s, "presentation.error-color", "COLOR DE ERROR", 0, 0, "color");
                        else if (slot == 23) this.promptChat(player, s, "drop.name", "NOMBRE DEL DROP", 0, 0, "text");
                        else if (slot == 25) this.promptChat(player, s, "drop.description", "DESCRIPCION DEL DROP", 0, 0, "text");
                        else if (slot == 28) this.promptChat(player, s, "messages.countdown-title", "TITULO DE CUENTA", 0, 0, "text");
                        else if (slot == 30) this.promptChat(player, s, "messages.countdown-subtitle", "SUBTITULO DE CUENTA", 0, 0, "text");
                    }
                }
            }
        }
    }

    private void syncLootBuffer(MenuSession s) {
        while (s.customItems.size() < (s.pageIndex + 1) * 45) {
            s.customItems.add(null);
        }
        for (int i = 0; i < 45; i++) {
            ItemStack item = s.inventory.getItem(i);
            s.customItems.set(s.pageIndex * 45 + i, item == null ? null : item.clone());
        }
    }

    private void handleLootClick(Player player, MenuSession s, int slot, ItemStack clicked) {
        if (slot >= 54) {
            if (clicked != null && !clicked.getType().isAir()) {
                for (int i = 0; i < 45; i++) {
                    if (s.inventory.getItem(i) == null) {
                        s.inventory.setItem(i, clicked.clone());
                        this.module.getMessageFormatter().playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.3F);
                        return;
                    }
                }
                this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().error("Esta pagina esta llena | Abre la siguiente"));
            }
        } else if (slot < 45) {
            s.inventory.setItem(slot, null);
        } else {
            this.syncLootBuffer(s);
            if (slot == 45 && s.pageIndex > 0) {
                this.openMenu(player, Page.LOOT_CONTENT, s.pageIndex - 1, s.customItems, s.tierId);
            } else if (slot == 52 && s.pageIndex < 100) {
                this.openMenu(player, Page.LOOT_CONTENT, s.pageIndex + 1, s.customItems, s.tierId);
            } else if (slot == 49 || slot == 51) {
                this.openMenu(player, Page.LOOT_TIER_SELECT);
            } else if (slot == 53) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncChatEvent event) {
        ChatPrompt prompt = this.activePrompts.get(event.getPlayer().getUniqueId());
        if (prompt != null) {
            event.setCancelled(true);
            String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            Bukkit.getScheduler().runTask(this.module.getPlugin(), () -> this.handleChatInput(event.getPlayer(), prompt, input));
        }
    }

    private void handleChatInput(Player player, ChatPrompt prompt, String input) {
        if (player.isOnline() && isAdmin(player) && this.activePrompts.get(player.getUniqueId()) == prompt) {
            if ("cancelar".equalsIgnoreCase(input)) {
                this.activePrompts.remove(player.getUniqueId());
                this.openMenu(player, prompt.returnPage, prompt.tierId);
            } else {
                try {
                    if ("int".equals(prompt.type)) {
                        int val = Integer.parseInt(input);
                        if (val < prompt.min || val > prompt.max) throw new IllegalArgumentException();
                        this.module.setSetting(prompt.path, val);
                    } else if ("double".equals(prompt.type)) {
                        double val = Double.parseDouble(input.replace(',', '.'));
                        if (!Double.isFinite(val) || val < prompt.min || val > prompt.max) throw new IllegalArgumentException();
                        this.module.setSetting(prompt.path, val);
                    } else if ("color".equals(prompt.type)) {
                        if (!input.matches("^#[0-9A-Fa-f]{6}$")) throw new IllegalArgumentException();
                        this.module.setSetting(prompt.path, input.toUpperCase());
                    } else {
                        if (input.isEmpty()) throw new IllegalArgumentException();
                        this.module.setSetting(prompt.path, input);
                    }
                    this.module.getPlugin().saveConfig();
                    if ("automatic.interval-minutes".equals(prompt.path)) {
                        this.module.getAirDropManager().resetAutoSchedule(this.module.getTicks());
                    }
                    this.activePrompts.remove(player.getUniqueId());
                    this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().success("Ajuste guardado"));
                    this.openMenu(player, prompt.returnPage, prompt.tierId);
                } catch (IllegalArgumentException e) {
                    this.module.getMessageFormatter().sendMessage(player, this.module.getMessageFormatter().error("Valor no valido | Intenta de nuevo o escribe cancelar"));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuSession s && s.page == Page.LOOT_CONTENT) {
            this.syncLootBuffer(s);
            boolean saved = this.module.getLootManager().setItems(s.tierId, s.customItems);
            if (event.getPlayer().getUniqueId().equals(this.lootEditor)) {
                this.lootEditor = null;
            }
            if (!saved) {
                this.module.getMessageFormatter().sendMessage(event.getPlayer(), this.module.getMessageFormatter().error("No se pudo guardar el contenido"));
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuSession) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.activePrompts.remove(event.getPlayer().getUniqueId());
        if (event.getPlayer().getUniqueId().equals(this.lootEditor)) {
            this.lootEditor = null;
        }
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuSession) {
                p.closeInventory();
            }
        }
        this.activePrompts.clear();
        this.lootEditor = null;
    }

    public static final class MenuSession implements InventoryHolder {
        public final Page page;
        public final int pageIndex;
        public final List<ItemStack> customItems;
        public final String tierId;
        public Inventory inventory;

        MenuSession(Page page, int pageIndex, List<ItemStack> customItems, String tierId) {
            this.page = page;
            this.pageIndex = pageIndex;
            this.customItems = customItems;
            this.tierId = tierId;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    public enum Page {
        MAIN,
        LAUNCH,
        AUTOMATIC,
        LOOT_TIER_SELECT,
        LOOT_CONTENT,
        LOCATION,
        CHOOSE_WORLD,
        LIMITS,
        TIER_EDIT,
        PRESENTATION,
        PARTICLES,
        NAME_DESC,
        ACTIVE_DROPS
    }

    record ChatPrompt(String path, String title, double min, double max, Page returnPage, String tierId, String type) {}
}
