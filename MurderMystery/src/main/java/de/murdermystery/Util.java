package de.murdermystery;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Util {

    public static final String PREFIX = ChatColor.DARK_RED + "Murder" + ChatColor.RED + "Mystery " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY;

    private Util() {
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Wandelt einen Text mit §-Farbcodes in eine Adventure-Komponente um. */
    public static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    /** Wie {@link #text}, aber ohne die kursive Standardformatierung von Item-Namen. */
    public static Component itemName(String legacy) {
        return text(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static void title(Player p, String title, String subtitle) {
        p.showTitle(Title.title(text(title), text(subtitle)));
    }

    public static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** Spielt einen Vanilla-Sound über seinen Namen ab, z. B. "entity.player.levelup". */
    public static void sound(Player p, float pitch, String key) {
        p.playSound(p.getLocation(), "minecraft:" + key, 1f, pitch);
    }

    public static String serialize(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    public static Location deserialize(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String[] p = s.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) {
            return null;
        }
        return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }

    public static Location blockCenter(Location l) {
        Location c = l.clone();
        c.setX(l.getBlockX() + 0.5);
        c.setZ(l.getBlockZ() + 0.5);
        return c;
    }
}
