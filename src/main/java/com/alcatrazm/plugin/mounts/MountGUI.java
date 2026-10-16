package com.alcatrazm.plugin.mounts;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MountGUI {

    public static final String GUI_TITLE = ChatColor.translateAlternateColorCodes('&', "&8» &6&lESTABLO DE MONTURAS &8«");

    public static void open(Player player, MountManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        ItemStack sep = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);

        // Bordes y separadores
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, sep);
        for (int i = 36; i < 45; i++) inv.setItem(i, sep);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border);
        inv.setItem(17, border);
        inv.setItem(27, border);
        inv.setItem(35, border);

        // Header
        inv.setItem(4, createItem(
                Material.NETHER_STAR,
                "&6✦ &lSISTEMA DE MONTURAS Y MASCOTAS &6✦",
                List.of(
                        "&7Elige tu compañero ideal para explorar Alcatraz.",
                        "&7Monturas terrestres, voladoras, acuáticas",
                        "&7y hadas protectoras disponibles."
                )
        ));

        ActiveMount current = manager.getActiveMount(player.getUniqueId());

        // --- FILA 1: TERRESTRES (Slots 10 al 16) ---

        // 10: Zorro Ancestral
        addMountItem(inv, 10, MountType.FOX, current, manager, player,
                List.of(
                        "&7Una bestia legendaria ágil y veloz.",
                        "&7Capaz de realizar saltos acrobáticos.",
                        "",
                        "&e▪ Tipo: &fTerrestre Ágil",
                        "&e▪ Escala: &6x2.0 &7(Gigante)",
                        "&e▪ Habilidad: &bSuper Salto y Zancada",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐☆"
                )
        );

        // 11: Conejo Colosal
        addMountItem(inv, 11, MountType.RABBIT, current, manager, player,
                List.of(
                        "&7Un conejo gigante colosal y acrobático.",
                        "&7Alcanza grandes alturas en cada salto.",
                        "",
                        "&e▪ Tipo: &fTerrestre Saltador",
                        "&e▪ Escala: &6x2.7 &7(1.5x tamaño del jugador)",
                        "&e▪ Habilidad: &bSalto Colosal Potenciado",
                        "&e▪ Velocidad: &a⭐⭐⭐☆☆"
                )
        );

        // 12: Armadillo Veloz
        addMountItem(inv, 12, MountType.ARMADILLO, current, manager, player,
                List.of(
                        "&7Un armadillo hiperveloz con coraza reforzada.",
                        "&7Corre tan rápido como un caballo de 14 bps.",
                        "",
                        "&e▪ Tipo: &fTerrestre de Carreras",
                        "&e▪ Escala: &6x2.2 &7(1.2x tamaño del jugador)",
                        "&e▪ Habilidad: &eVelocidad Extrema (14 b/s)",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐⭐"
                )
        );

        // 13: Gato Pantera
        addMountItem(inv, 13, MountType.CAT, current, manager, player,
                List.of(
                        "&7Una pantera negra domesticada del tamaño de un caballo.",
                        "&7Desplazamiento felino ultrarrápido y sigiloso.",
                        "",
                        "&e▪ Tipo: &fTerrestre Felino",
                        "&e▪ Escala: &6x2.4 &7(Tamaño Caballo)",
                        "&e▪ Habilidad: &dAgilidad y Zancada Felina",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐⭐"
                )
        );

        // 14: Oso Polar Guerrero
        addMountItem(inv, 14, MountType.POLAR_BEAR, current, manager, player,
                List.of(
                        "&7Un oso polar de las tundras árticas.",
                        "&7Ligeramente más rápido que un caballo con paso firme.",
                        "",
                        "&e▪ Tipo: &fTerrestre Pesada",
                        "&e▪ Escala: &6x1.3 &7(Robusto)",
                        "&e▪ Habilidad: &bPaso Firme Ártico",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐☆"
                )
        );

        // 15: Oso Panda Guardián
        addMountItem(inv, 15, MountType.PANDA, current, manager, player,
                List.of(
                        "&7Compañero noble, robusto y carismático.",
                        "&7Pasea por el mundo con la máxima tranquilidad.",
                        "",
                        "&e▪ Tipo: &fTerrestre Noble",
                        "&e▪ Escala: &6x1.4 &7(Robusto)",
                        "&e▪ Habilidad: &aPaseo Confortable",
                        "&e▪ Resistencia: &a⭐⭐⭐⭐⭐"
                )
        );

        // 16: Sniffer Arcaico
        addMountItem(inv, 16, MountType.SNIFFER, current, manager, player,
                List.of(
                        "&7Un titán ancestral rescatado del pasado.",
                        "&7Imparable, imponente e inmune al retroceso.",
                        "",
                        "&e▪ Tipo: &fTerrestre Coloso",
                        "&e▪ Escala: &6x1.0 &7(Titán Original)",
                        "&e▪ Habilidad: &aInmunidad a Empuje (Knockback)",
                        "&e▪ Resistencia: &a⭐⭐⭐⭐⭐"
                )
        );

        // --- FILA 2: MASCOTA COMPAÑERA ESPECIAL (Slot 22) ---
        // 22: Hada Allay Protectora (COMPAÑERA)
        addMountItem(inv, 22, MountType.ALLAY, current, manager, player,
                List.of(
                        "&7Un espíritu feérico que te acompaña lealmente.",
                        "&b(No es montura: es un hada que vuela a tu lado)",
                        "",
                        "&e▪ Tipo: &bMascota Compañera / Hada",
                        "&e▪ Efectos continuos para ti:",
                        "   &8▪ &aRegeneración I &7(Curación constante)",
                        "   &8▪ &eVelocidad I &7(Agilidad constante)",
                        "&e▪ Movimiento: &bOrbita flotando junto a ti"
                )
        );

        // --- FILA 3: VOLADORAS & ACUÁTICAS (Slots 28 al 34) ---

        // 28: Phantom del Abismo (PREMIUM)
        addMountItem(inv, 28, MountType.PHANTOM, current, manager, player,
                List.of(
                        "&7Cazador nocturno con vuelo aerodinámico supersónico.",
                        "&7Defiende a su jinete atacando enemigos al desmontar.",
                        "",
                        "&e▪ Tipo: &dVoladora 3D &8| &cGuardián Hostil",
                        "&e▪ Escala: &6x1.25 &7(Aerodinámico)",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐⭐ &b(Velocidad Élitra)",
                        "&e▪ Controles: &fWASD + Mirada + &bEspacio &f/ &eShift",
                        "&e▪ Especial: &cAtaca enemigos cercanos al desmontar"
                )
        );

        // 29: MiniDragón del End
        addMountItem(inv, 29, MountType.MINIDRAGON, current, manager, player,
                List.of(
                        "&7Cría mística de dragón del End domesticada.",
                        "&7Vuelo aerodinámico supersónico (1.5x Élitras).",
                        "&7Silencioso, compacto y fiel a su dueño.",
                        "",
                        "&e▪ Tipo: &dVoladora Mítica 3D &8| &5Furia Protectora",
                        "&e▪ Escala: &6x0.20 &7(Mini Dragón Compacto)",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐⭐+ &b(1.5x Élitras)",
                        "&e▪ Furia Protectora: &5Solo ataca a quien dañe a su dueño",
                        "&e▪ Protección: &aRespeta protecciones y claims (/ps)"
                )
        );

        // 30: Murciélago Espectral
        addMountItem(inv, 30, MountType.BAT, current, manager, player,
                List.of(
                        "&7Un murciélago gigante de las profundidades.",
                        "&7Vuelo suave, sigiloso y maniobrable en cavernas.",
                        "",
                        "&e▪ Tipo: &dVoladora Sigilosa",
                        "&e▪ Escala: &6x2.5 &7(Murciélago Grande)",
                        "&e▪ Velocidad: &a⭐⭐⭐☆☆ &7(0.50x Élitras)",
                        "&e▪ Controles: &fWASD + Mirada + &bEspacio &f/ &eShift"
                )
        );

        // 31: Abeja Reina Melífera
        addMountItem(inv, 31, MountType.BEE, current, manager, player,
                List.of(
                        "&7Una abeja colosal dulce, mística e inofensiva.",
                        "&7Vuela a velocidad de carrera &e(Velocidad II)&7.",
                        "&7Control dócil y maniobrable como un caballo aéreo.",
                        "",
                        "&e▪ Tipo: &eVoladora Mansa &8| &aPolinizadora",
                        "&e▪ Escala: &6x2.0 &7(Gigante Cómoda)",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐☆ &e(Velocidad II - Óptima)",
                        "&e▪ Utilidad: &aInmunidad a Veneno &7+ &6Polinización de cultivos",
                        "&e▪ Efecto: &cEstela de corazones y néctar dulce"
                )
        );

        // 32: Salmón Titán
        addMountItem(inv, 32, MountType.SALMON, current, manager, player,
                List.of(
                        "&7Un pez colosal que surca aguas fluviales y marinas.",
                        "&7Permite nadar velozmente por ríos y mares.",
                        "",
                        "&e▪ Tipo: &bAcuática Marina / Fluvial",
                        "&e▪ Escala: &6x3.5 &7(2x tamaño del jugador)",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐☆ &b(Nado Rápido)",
                        "&e▪ Ventaja: &9Respiración acuática incluida"
                )
        );

        // 33: Delfín de las Profundidades
        addMountItem(inv, 33, MountType.DOLPHIN, current, manager, player,
                List.of(
                        "&7Compañero marino de alta velocidad.",
                        "&c¡Solo puede nadar dentro del agua!",
                        "",
                        "&e▪ Tipo: &bAcuática Marina Exclusiva",
                        "&e▪ Escala: &6x1.5 &7(Agrandado)",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐⭐ &b(Nado Supersónico)",
                        "&e▪ Efectos: &bGracia del Delfín &7+ &9Respiración Acuática"
                )
        );

        // 34: Ajolote Místico (ANFIBIO)
        addMountItem(inv, 34, MountType.AXOLOTL, current, manager, player,
                List.of(
                        "&7Una criatura mística anfibia de gran tamaño.",
                        "&a¡Capaz de caminar en tierra y nadar a gran velocidad!",
                        "",
                        "&e▪ Tipo: &dAnfibia Mágica &7(Tierra y Agua)",
                        "&e▪ Escala: &6x3.0 &7(3x tamaño del jugador)",
                        "&e▪ Velocidad: &a⭐⭐⭐⭐☆ &d(Nado y Trote Veloz)",
                        "&e▪ Efectos en agua: &9Respiración Acuática &7+ &dRegeneración"
                )
        );

        // --- FILA 5: CONTROLES (Slots 47, 49, 51) ---

        // 47: Información y Guía
        inv.setItem(47, createItem(
                Material.BOOK,
                "&e&l¿Cómo controlar tus criaturas?",
                List.of(
                        "&8▪ &eWASD: &7Moverse en tierra, agua o en el aire.",
                        "&8▪ &eEspacio: &7Saltar (terrestre) o ascender (voladoras).",
                        "&8▪ &eShift: &7Descender (aire/agua) o desmontar.",
                        "&8▪ &eClick Derecho: &7Volver a montar si te has bajado.",
                        "&8▪ &eProtecciones: &7Todas las criaturas respetan las /ps."
                )
        ));

        // 49: Despedir Criatura
        inv.setItem(49, createItem(
                Material.BARRIER,
                "&c&lDespedir Criatura Activa",
                List.of(
                        "&7Guarda tu montura o hada activa de vuelta",
                        "&7al establo sin dejar rastros.",
                        "",
                        current != null ? "&e▶ Click para guardar criatura." : "&7No tienes ninguna criatura activa."
                )
        ));

        // 51: Tu Membresía / Estado
        int unlocked = 0;
        int total = MountType.values().length;
        for (MountType mt : MountType.values()) {
            if (manager.canUseMount(player, mt)) {
                unlocked++;
            }
        }
        boolean isAllAccess = player.isOp() || player.hasPermission("alcatraz.mounts.admin") || player.hasPermission("alcatraz.mounts.*");

        inv.setItem(51, createItem(
                Material.PLAYER_HEAD,
                "&e&lTu Estado de Jinete",
                List.of(
                        "&7Jugador: &f" + player.getName(),
                        "&7Monturas Desbloqueadas: &a" + unlocked + "&7/&e" + total,
                        "&7Criatura activa: " + (current != null ? "&a" + current.getType().getDisplayName() : "&cNinguna"),
                        "",
                        isAllAccess ? "&6👑 Acceso Total (Admin / VIP)" : "&7Adquiere más monturas en la tienda."
                )
        ));

        player.openInventory(inv);
    }

    private static void addMountItem(Inventory inv, int slot, MountType type, ActiveMount current, MountManager manager, Player player, List<String> details) {
        boolean isActive = current != null && current.getType() == type;
        boolean canUse = manager.canUseMount(player, type);
        String perm = manager.getMountPermission(type);

        List<String> lore = new ArrayList<>(details);
        lore.add("");

        if (canUse) {
            lore.add("&a✔ &2&lDESBLOQUEADA &7(Adquirida)");
        } else {
            lore.add("&c✖ &4&lBLOQUEADA &7(De Pago / Tienda)");
            lore.add("&8▪ &7Permiso: &e" + perm);
            lore.add("&6⭐ &e¡Adquiérela en la tienda del servidor!");
        }

        lore.add("");
        if (isActive) {
            lore.add("&a✔ &2&l¡CRIATURA ACTUALMENTE ACTIVA!");
            lore.add("&7(Click en la barrera para despedirla)");
        } else if (!canUse) {
            lore.add("&c✖ ¡No tienes desbloqueada esta criatura!");
        } else if (!type.isRideable()) {
            lore.add("&b▶ ¡Haz click para invocar a tu lado!");
        } else {
            lore.add("&a▶ ¡Haz click para invocar y montar!");
        }

        String displayName = (canUse ? "&a✦ " : "&c🔒 ") + "&l" + type.getDisplayName();
        inv.setItem(slot, createItem(type.getIconMaterial(), displayName, lore));
    }

    private static ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (loreLines != null) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : loreLines) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
