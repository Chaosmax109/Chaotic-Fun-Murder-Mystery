package de.murdermystery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Alle Werte aus config.yml, roles.yml und gold.yml, einmal beim Start bzw. bei /mm reload eingelesen. */
public class Settings {

    /**
     * MURDERER gewinnt, wenn kein INNOCENT mehr lebt; INNOCENT gewinnt, wenn kein MURDERER mehr lebt
     * oder die Zeit abläuft. NEUTRAL spielt für sich: gewinnt, wenn er das Rundenende überlebt.
     */
    public enum Team { MURDERER, INNOCENT, NEUTRAL }

    public enum WeaponType { KNIFE, BOW, NONE }

    /** Eine Rolle aus roles.yml (eingebaut wie murderer/detective/innocent oder selbst angelegt). */
    public static class Role {
        public final String key;
        public final Team team;
        public final int amount;
        public final int extraPerPlayers;
        public final double chance;
        public final boolean showChance;
        public final String name;
        public final String color;
        public final String title;
        public final String subtitle;
        public final String message;
        public final WeaponType weaponType;
        public final Material weapon;
        public final String weaponName;
        public final boolean knifeThrow;
        public final int knifeCooldown;
        public final int knifeRange;
        public final int arrowCooldown;
        public final boolean canCollectGold;
        public final boolean canPickupBow;
        public final List<PotionEffect> effects;
        public final Material icon;

        Role(String key, ConfigurationSection c, Logger log) {
            this.key = key;
            team = parseEnum(Team.class, c.getString("team"), key.equals("murderer") ? Team.MURDERER : Team.INNOCENT, log, key + ".team");
            amount = Math.max(0, c.getInt("amount", 1));
            extraPerPlayers = Math.max(0, c.getInt("extra-per-players", 0));
            chance = Math.max(0, Math.min(100, c.getDouble("chance", 100)));
            showChance = c.getBoolean("show-chance", true);
            name = Util.color(c.getString("name", key));
            color = Util.color(c.getString("color", "&7"));
            title = Util.color(c.getString("title", color + name.toUpperCase(Locale.GERMAN)));
            subtitle = Util.color(c.getString("subtitle", ""));
            message = Util.color(c.getString("message", "Du bist " + color + name + "&7!"));

            WeaponType defaultType = switch (key) {
                case "murderer" -> WeaponType.KNIFE;
                case "detective" -> WeaponType.BOW;
                default -> WeaponType.NONE;
            };
            weaponType = parseEnum(WeaponType.class, c.getString("weapon.type"), defaultType, log, key + ".weapon.type");
            Material defaultWeapon = switch (weaponType) {
                case KNIFE -> Material.IRON_SWORD;
                case BOW -> Material.BOW;
                case NONE -> Material.AIR;
            };
            weapon = weaponType == WeaponType.NONE ? Material.AIR
                    : material(c.getString("weapon.material"), defaultWeapon, log, "roles.yml: " + key + ".weapon.material");
            weaponName = Util.color(c.getString("weapon.name", ""));
            knifeThrow = c.getBoolean("knife-throw", true);
            knifeCooldown = Math.max(0, c.getInt("knife-cooldown", 5));
            knifeRange = Math.max(1, c.getInt("knife-range", 40));
            arrowCooldown = Math.max(0, c.getInt("arrow-cooldown", 5));
            canCollectGold = c.getBoolean("can-collect-gold", true);
            canPickupBow = c.getBoolean("can-pickup-bow", team == Team.INNOCENT);
            effects = parseEffects(c.getStringList("effects"), log, key);
            Material iconDefault = weapon != Material.AIR ? weapon : Material.GOLD_INGOT;
            icon = material(c.getString("icon"), iconDefault, log, "roles.yml: " + key + ".icon");
        }

        /** Wie viele Spieler diese Rolle bei {@code players} Spielern bekommen. */
        public int count(int players) {
            return amount + (extraPerPlayers > 0 ? players / extraPerPlayers : 0);
        }

        public String coloredName() {
            return color + name;
        }

        public boolean isMurderer() {
            return team == Team.MURDERER;
        }
    }

    /** Ein Item aus einem Gold-Paket (gold-rewards.yml). */
    public record RewardItem(Material material, int amount, String name, boolean once) {
    }

    /** Was eine Rolle für {@code cost} Gold bekommt (gold-rewards.yml). */
    public record GoldReward(boolean enabled, int cost, String message, List<RewardItem> items, List<PotionEffect> effects) {

        static GoldReward parse(ConfigurationSection c, Logger log) {
            String path = "gold-rewards.yml: " + c.getName();
            List<RewardItem> items = new ArrayList<>();
            for (Map<?, ?> m : c.getMapList("items")) {
                Object mat = m.get("material");
                Material material = material(mat == null ? null : mat.toString(), null, log, path + ".items");
                if (material == null) continue;
                Object amount = m.get("amount");
                Object name = m.get("name");
                Object once = m.get("once");
                items.add(new RewardItem(material,
                        amount instanceof Number n ? Math.max(1, n.intValue()) : 1,
                        name == null ? "" : Util.color(name.toString()),
                        once instanceof Boolean b && b));
            }
            return new GoldReward(c.getBoolean("enabled", true), Math.max(1, c.getInt("cost", 10)),
                    Util.color(c.getString("message", "")), Collections.unmodifiableList(items),
                    parseEffects(c.getStringList("effects"), log, path));
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
    public final boolean neutralKillKillsShooter;
    public final boolean showChances;
    /** Alle Rollen außer "innocent", in der Reihenfolge aus roles.yml. */
    public final List<Role> specialRoles;
    /** Die Rolle "innocent" für alle übrigen Spieler. */
    public final Role innocent;

    public final boolean goldEnabled;
    public final Material goldMaterial;
    public final int goldInterval;
    public final int goldSpawnsPerInterval;
    public final int goldMaxOnMap;
    public final int goldPerPickup;
    /** Gold-Pakete pro Rollen-Schlüssel; "default" gilt für Rollen ohne eigenen Eintrag. */
    private final Map<String, GoldReward> goldRewards = new HashMap<>();

    /**
     * @param c       config.yml (allgemeine Einstellungen)
     * @param roles   roles.yml
     * @param gold    gold.yml
     * @param rewards gold-rewards.yml
     */
    public Settings(FileConfiguration c, FileConfiguration roles, FileConfiguration gold, FileConfiguration rewards, Logger log) {
        autoJoin = c.getBoolean("auto-join", true);
        minPlayers = Math.max(2, c.getInt("min-players", 4));
        maxPlayers = Math.max(minPlayers, c.getInt("max-players", 16));
        lobbyCountdown = Math.max(1, c.getInt("lobby-countdown", 30));
        gracePeriod = Math.max(0, c.getInt("grace-period", 10));
        gameDuration = Math.max(10, c.getInt("game-duration", 270));
        serverName = Util.color(c.getString("server-name", "&edein-server.de"));
        lobbyMaxDistance = Math.max(0, c.getInt("lobby.max-distance", 40));

        teamKillKillsShooter = roles.getBoolean("team-kill-kills-shooter", true);
        neutralKillKillsShooter = roles.getBoolean("neutral-kill-kills-shooter", false);
        showChances = roles.getBoolean("show-chances", true);
        List<Role> special = new ArrayList<>();
        Role filler = null;
        ConfigurationSection section = roles.getConfigurationSection("roles");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection rs = section.getConfigurationSection(key);
                if (rs == null) continue;
                Role r = new Role(key.toLowerCase(Locale.ROOT), rs, log);
                if (r.key.equals("innocent")) filler = r;
                else special.add(r);
            }
        }
        if (filler == null) {
            log.warning("roles.yml: Rolle 'innocent' fehlt – nutze Standardwerte.");
            filler = new Role("innocent", roles.createSection("roles.innocent"), log);
        }
        if (filler.team != Team.INNOCENT) {
            log.warning("roles.yml: 'innocent' muss im Team INNOCENT sein.");
        }
        if (special.stream().noneMatch(Role::isMurderer)) {
            log.warning("roles.yml: keine Rolle mit team: MURDERER – nutze den Standard-Mörder.");
            special.add(0, new Role("murderer", roles.createSection("roles.murderer"), log));
        }
        specialRoles = Collections.unmodifiableList(special);
        innocent = filler;

        goldEnabled = gold.getBoolean("enabled", true);
        goldMaterial = material(gold.getString("material"), Material.GOLD_INGOT, log, "gold.yml: material");
        goldInterval = Math.max(1, gold.getInt("interval", 8));
        goldSpawnsPerInterval = Math.max(1, gold.getInt("spawns-per-interval", 1));
        goldMaxOnMap = Math.max(0, gold.getInt("max-on-map", 0));
        goldPerPickup = Math.max(1, gold.getInt("amount-per-pickup", 1));

        for (String key : rewards.getKeys(false)) {
            ConfigurationSection rs = rewards.getConfigurationSection(key);
            if (rs != null) goldRewards.put(key.toLowerCase(Locale.ROOT), GoldReward.parse(rs, log));
        }
        if (!goldRewards.containsKey("default")) {
            log.warning("gold-rewards.yml: Abschnitt 'default' fehlt – Gold bringt Rollen ohne eigenen Eintrag nichts.");
            goldRewards.put("default", new GoldReward(false, 10, "", List.of(), List.of()));
        }
    }

    /** Das Gold-Paket einer Rolle (oder "default"). */
    public GoldReward goldReward(Role role) {
        return goldRewards.getOrDefault(role.key, goldRewards.get("default"));
    }

    /** Alle Rollen inklusive "innocent". */
    public List<Role> allRoles() {
        List<Role> all = new ArrayList<>(specialRoles);
        all.add(innocent);
        return all;
    }

    public Role role(String key) {
        for (Role r : allRoles()) if (r.key.equals(key)) return r;
        return null;
    }

    /** Die erste Rolle im Team MURDERER – für Texte wie "Der Mörder hat seine Waffe erhalten". */
    public Role mainMurderer() {
        for (Role r : specialRoles) if (r.isMurderer()) return r;
        return specialRoles.get(0);
    }

    // ------------------------------------------------------------------ Migration

    /**
     * Bringt ältere Einstellungen ins aktuelle Format:
     * Rollen/Gold aus der config.yml nach roles.yml/gold.yml und Rollen in roles.yml unter "roles:".
     */
    public static boolean migrate(FileConfiguration c, FileConfiguration roles, FileConfiguration gold, FileConfiguration rewards) {
        boolean changed = moveSection(c, "roles", roles, "") | moveSection(c, "gold", gold, "");
        changed |= moveKey(c, "gold-interval", gold, "interval");
        changed |= moveKey(c, "gold-for-bow", gold, "for-bow");
        changed |= moveKey(c, "knife-cooldown", roles, "murderer.knife-cooldown");
        changed |= moveKey(c, "arrow-cooldown", roles, "detective.arrow-cooldown");
        // Version 2.1: murderer/detective/innocent lagen direkt in roles.yml
        for (String key : new String[]{"murderer", "detective", "innocent"}) {
            changed |= moveSection(roles, key, roles, "roles." + key + ".");
        }
        if (!roles.isSet("show-chances")) {
            roles.set("show-chances", true);
            changed = true;
        }
        // Version 2.1: Bogen-Preis stand in gold.yml – jetzt das "default"-Paket in gold-rewards.yml
        if (gold.isSet("for-bow") || gold.isSet("arrows-per-bow") || gold.isSet("bow-name")) {
            rewards.set("default.cost", gold.getInt("for-bow", 10));
            rewards.set("default.items", List.of(
                    Map.of("material", "BOW", "name", gold.getString("bow-name", "&eBogen"), "once", true),
                    Map.of("material", "ARROW", "amount", gold.getInt("arrows-per-bow", 1))));
            gold.set("for-bow", null);
            gold.set("arrows-per-bow", null);
            gold.set("bow-name", null);
            changed = true;
        }
        return changed;
    }

    private static boolean moveSection(FileConfiguration from, String path, FileConfiguration to, String targetPrefix) {
        ConfigurationSection s = from.getConfigurationSection(path);
        if (s == null || !from.isSet(path)) return false;
        for (var e : s.getValues(true).entrySet()) {
            if (!(e.getValue() instanceof ConfigurationSection)) {
                to.set(targetPrefix + e.getKey(), e.getValue());
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

    // ------------------------------------------------------------------ Parser

    private static List<PotionEffect> parseEffects(List<String> list, Logger log, String role) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String entry : list) {
            String[] parts = entry.split(":");
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                log.warning("roles.yml: unbekannter Effekt '" + parts[0] + "' bei " + role);
                continue;
            }
            // Format: EFFEKT[:Stufe[:Sekunden]] – ohne Sekunden hält der Effekt die ganze Runde
            int level = parts.length > 1 ? parseInt(parts[1], 1) : 1;
            int seconds = parts.length > 2 ? parseInt(parts[2], 0) : 0;
            int duration = seconds > 0 ? seconds * 20 : PotionEffect.INFINITE_DURATION;
            effects.add(new PotionEffect(type, duration, Math.max(1, level) - 1, false, false, true));
        }
        return Collections.unmodifiableList(effects);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback, Logger log, String path) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("roles.yml: ungültiger Wert '" + value + "' bei " + path + " – nutze " + fallback);
            return fallback;
        }
    }

    private static Material material(String name, Material fallback, Logger log, String path) {
        if (name == null || name.isEmpty()) {
            if (fallback == null) log.warning(path + ": 'material' fehlt");
            return fallback;
        }
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            log.warning("Unbekanntes Item '" + name + "' bei " + path + " – nutze " + fallback);
            return fallback;
        }
        return m;
    }
}
