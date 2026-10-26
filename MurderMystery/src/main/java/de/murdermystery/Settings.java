package de.murdermystery;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/** Alle Werte aus der config.yml, einmal beim Start bzw. bei /mm reload eingelesen. */
public class Settings {

    /** Einstellungen einer Rolle (roles.murderer / roles.detective / roles.innocent). */
    public static class RoleSettings {
        public final int amount;
        public final int extraPerPlayers;
        public final String name;
        public final String color;
        public final String title;
        public final String subtitle;
        public final String message;
        public final Material weapon;
        public final String weaponName;
        public final boolean canCollectGold;

        RoleSettings(ConfigurationSection c, Material defaultWeapon, Logger log) {
            amount = Math.max(0, c.getInt("amount", 1));
            extraPerPlayers = Math.max(0, c.getInt("extra-per-players", 0));
            name = Util.color(c.getString("name", "?"));
            color = Util.color(c.getString("color", "&7"));
            title = Util.color(c.getString("title", ""));
            subtitle = Util.color(c.getString("subtitle", ""));
            message = Util.color(c.getString("message", ""));
            weapon = material(c.getString("weapon.material"), defaultWeapon, log, c.getCurrentPath() + ".weapon.material");
            weaponName = Util.color(c.getString("weapon.name", ""));
            canCollectGold = c.getBoolean("can-collect-gold", true);
        }

        /** Wie viele Spieler diese Rolle bei {@code players} Spielern bekommen. */
        public int count(int players) {
            return amount + (extraPerPlayers > 0 ? players / extraPerPlayers : 0);
        }

        public String coloredName() {
            return color + name;
        }
    }

    public final boolean autoJoin;
    public final int minPlayers;
    public final int maxPlayers;
    public final int lobbyCountdown;
    public final int gracePeriod;
    public final int gameDuration;
    public final String serverName;
    public final int lobbyMaxDistance;

    public final boolean teamKillKillsShooter;
    public final RoleSettings murderer;
    public final RoleSettings detective;
    public final RoleSettings innocent;
    public final boolean knifeThrow;
    public final int knifeCooldown;
    public final int knifeRange;
    public final int arrowCooldown;

    public final boolean goldEnabled;
    public final Material goldMaterial;
    public final int goldInterval;
    public final int goldSpawnsPerInterval;
    public final int goldMaxOnMap;
    public final int goldPerPickup;
    public final int goldForBow;
    public final int arrowsPerBow;
    public final String goldBowName;

    /**
     * @param c     config.yml (allgemeine Einstellungen)
     * @param roles roles.yml
     * @param gold  gold.yml
     */
    public Settings(FileConfiguration c, FileConfiguration roles, FileConfiguration gold, Logger log) {
        autoJoin = c.getBoolean("auto-join", true);
        minPlayers = Math.max(2, c.getInt("min-players", 4));
        maxPlayers = Math.max(minPlayers, c.getInt("max-players", 16));
        lobbyCountdown = Math.max(1, c.getInt("lobby-countdown", 30));
        gracePeriod = Math.max(0, c.getInt("grace-period", 10));
        gameDuration = Math.max(10, c.getInt("game-duration", 270));
        serverName = Util.color(c.getString("server-name", "&edein-server.de"));
        lobbyMaxDistance = Math.max(0, c.getInt("lobby.max-distance", 40));

        teamKillKillsShooter = roles.getBoolean("team-kill-kills-shooter", true);
        murderer = new RoleSettings(section(roles, "murderer"), Material.IRON_SWORD, log);
        detective = new RoleSettings(section(roles, "detective"), Material.BOW, log);
        innocent = new RoleSettings(section(roles, "innocent"), Material.AIR, log);
        knifeThrow = roles.getBoolean("murderer.knife-throw", true);
        knifeCooldown = Math.max(0, roles.getInt("murderer.knife-cooldown", 5));
        knifeRange = Math.max(1, roles.getInt("murderer.knife-range", 40));
        arrowCooldown = Math.max(0, roles.getInt("detective.arrow-cooldown", 5));

        goldEnabled = gold.getBoolean("enabled", true);
        goldMaterial = material(gold.getString("material"), Material.GOLD_INGOT, log, "gold.yml: material");
        goldInterval = Math.max(1, gold.getInt("interval", 8));
        goldSpawnsPerInterval = Math.max(1, gold.getInt("spawns-per-interval", 1));
        goldMaxOnMap = Math.max(0, gold.getInt("max-on-map", 0));
        goldPerPickup = Math.max(1, gold.getInt("amount-per-pickup", 1));
        goldForBow = Math.max(1, gold.getInt("for-bow", 10));
        arrowsPerBow = Math.max(1, gold.getInt("arrows-per-bow", 1));
        goldBowName = Util.color(gold.getString("bow-name", "&eBogen"));
    }

    /**
     * Verschiebt Rollen- und Gold-Einstellungen aus älteren config.yml-Versionen
     * (2.0: einzelne Schlüssel, 2.1-Vorabversion: Abschnitte roles/gold) in roles.yml und gold.yml.
     */
    public static boolean migrate(FileConfiguration c, FileConfiguration roles, FileConfiguration gold) {
        boolean changed = moveSection(c, "roles", roles) | moveSection(c, "gold", gold);
        String[][] moves = {
                {"gold-interval", "interval"},
                {"gold-for-bow", "for-bow"},
        };
        for (String[] m : moves) {
            changed |= moveKey(c, m[0], gold, m[1]);
        }
        changed |= moveKey(c, "knife-cooldown", roles, "murderer.knife-cooldown");
        changed |= moveKey(c, "arrow-cooldown", roles, "detective.arrow-cooldown");
        return changed;
    }

    private static boolean moveSection(FileConfiguration from, String path, FileConfiguration to) {
        ConfigurationSection s = from.getConfigurationSection(path);
        if (s == null || !from.isSet(path)) return false;
        for (var e : s.getValues(true).entrySet()) {
            if (!(e.getValue() instanceof ConfigurationSection)) {
                to.set(e.getKey(), e.getValue());
            }
        }
        from.set(path, null);
        return true;
    }

    private static boolean moveKey(FileConfiguration from, String key, FileConfiguration to, String target) {
        if (!from.isSet(key)) return false;
        to.set(target, from.get(key));
        from.set(key, null);
        return true;
    }

    private static ConfigurationSection section(FileConfiguration c, String path) {
        ConfigurationSection s = c.getConfigurationSection(path);
        return s != null ? s : c.createSection(path);
    }

    private static Material material(String name, Material fallback, Logger log, String path) {
        if (name == null || name.isEmpty()) return fallback;
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            log.warning("Unbekanntes Item '" + name + "' bei " + path + " – nutze " + fallback);
            return fallback;
        }
        return m;
    }
}
