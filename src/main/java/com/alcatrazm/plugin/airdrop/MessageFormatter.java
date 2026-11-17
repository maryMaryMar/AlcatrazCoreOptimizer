package com.alcatrazm.plugin.airdrop;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Formateador Adventure/MiniMessage para mensajes, titulos, sonidos e items GUI del modulo AirDrop.
 */
public final class MessageFormatter {
    private final AirDropModule module;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageFormatter(AirDropModule module) {
        this.module = module;
    }

    public static String formatDuration(long millis) {
        long secs = Math.max(0L, (millis + 999L) / 1000L);
        return String.format(Locale.ROOT, "%02dh %02dm %02ds", secs / 3600L, (secs % 3600L) / 60L, secs % 60L);
    }

    public static String cleanUpper(String s) {
        return Normalizer.normalize(s, Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    public static String toSmallCaps(String text) {
        String clean = Normalizer.normalize(text, Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String normal = "abcdefghijklmnopqrstuvwxyz";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        for (char c : clean.toCharArray()) {
            int idx = normal.indexOf(c);
            sb.append(idx < 0 ? c : small.charAt(idx));
        }
        return sb.toString();
    }

    private TextColor getColor(String key, String def) {
        String hex = this.module.getString("presentation." + key, def);
        TextColor c = TextColor.fromHexString(hex);
        return c == null ? TextColor.fromHexString(def) : c;
    }

    public Component gradient(String text) {
        return this.gradient(text, this.module.getString("presentation.primary-start", "#F8CB53"), this.module.getString("presentation.primary-end", "#FFE6A0"));
    }

    public Component gradient(String text, String startHex, String endHex) {
        TextColor start = TextColor.fromHexString(startHex != null ? startHex : "#F8CB53");
        TextColor end = TextColor.fromHexString(endHex != null ? endHex : "#FFE6A0");
        if (start == null) start = TextColor.fromHexString("#F8CB53");
        if (end == null) end = TextColor.fromHexString("#FFE6A0");
        Component result = Component.empty();
        int[] cps = text.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            float ratio = cps.length < 2 ? 0.0F : (float) i / (cps.length - 1);
            result = result.append(Component.text(new String(Character.toChars(cps[i])), TextColor.lerp(ratio, start, end)).decorate(TextDecoration.BOLD));
        }
        return result.decoration(TextDecoration.ITALIC, false);
    }

    public Component detail(String text) {
        return Component.text(text, this.getColor("detail-color", "#A4A8B0")).decoration(TextDecoration.ITALIC, false);
    }

    public Component success(String text) {
        return Component.text(text, this.getColor("success-color", "#87DCAA")).decoration(TextDecoration.ITALIC, false);
    }

    public Component info(String text) {
        return Component.text(text, this.getColor("info-color", "#8FC8EB")).decoration(TextDecoration.ITALIC, false);
    }

    public Component error(String text) {
        return Component.text(text, this.getColor("error-color", "#EB9198")).decoration(TextDecoration.ITALIC, false);
    }

    public Component getPrefix() {
        try {
            return this.miniMessage.deserialize(this.module.getString("prefix", "<bold><gradient:#F8CB53:#FFE6A0>AlcatrazDrops</gradient></bold> <dark_gray>|</dark_gray> "));
        } catch (RuntimeException e) {
            return this.gradient("AlcatrazDrops").append(this.detail(" | "));
        }
    }

    public void sendMessage(CommandSender sender, Component message) {
        sender.sendMessage(this.getPrefix().append(message));
    }

    public void broadcast(Component message) {
        Bukkit.getServer().sendMessage(this.getPrefix().append(message));
    }

    public void showTitle(Player player, String title, String subtitle) {
        if (this.module.getBoolean("presentation.titles", true)) {
            player.showTitle(Title.title(this.gradient(title), this.detail(subtitle), Times.times(Duration.ofMillis(120L), Duration.ofMillis(1250L), Duration.ofMillis(250L))));
        }
    }

    public ItemStack createGuiItem(Material mat, String title, String... lore) {
        return this.createColoredGuiItem(mat, title, null, null, lore);
    }

    public ItemStack createColoredGuiItem(Material mat, String title, String startHex, String endHex, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName((startHex != null && endHex != null) ? this.gradient(title, startHex, endHex) : this.gradient(title));
            meta.lore(Arrays.stream(lore).map(line -> {
                if ("SELECCIONADO".equals(line)) {
                    return this.success(line);
                } else if (line.contains("DESACTIVADO")) {
                    return this.detail(line.substring(0, line.indexOf("DESACTIVADO"))).append(this.error("DESACTIVADO"));
                } else if (line.contains("ACTIVADO")) {
                    return this.detail(line.substring(0, line.indexOf("ACTIVADO"))).append(this.success("ACTIVADO"));
                } else if (line.startsWith("Valor: ") || line.startsWith("ᴠᴀʟᴏʀ: ")) {
                    return this.detail("Valor: ").append(this.info(line.substring(7)));
                } else {
                    return this.detail(line);
                }
            }).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    public void playSound(Player player, Sound sound, float vol, float pitch) {
        if (this.module.getBoolean("presentation.sounds", true)) {
            player.playSound(player.getLocation(), sound, vol, pitch);
        }
    }

    public void playSound(Location loc, Sound sound, float vol, float pitch) {
        if (this.module.getBoolean("presentation.sounds", true) && loc.getWorld() != null) {
            loc.getWorld().playSound(loc, sound, vol, pitch);
        }
    }

    public Component coords(Location loc) {
        return this.info("X: " + loc.getBlockX() + "  Y: " + loc.getBlockY() + "  Z: " + loc.getBlockZ());
    }
}
