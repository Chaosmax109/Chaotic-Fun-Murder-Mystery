package de.murdermystery;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Verwaltet die Wartelobby (config.yml) und alle Maps (maps.yml).
 * Jede Map hat eine Welt sowie eigene Spieler- und Gold-Spawns. Die Punkte werden als Text gehalten und erst bei
 * Bedarf in Locations umgewandelt – so gehen sie nicht verloren, wenn eine Welt noch nicht geladen ist.
 */
public class MapManager {

    /** Wie die Map für die nächste Runde bestimmt wird. */
    public enum Mode { RANDOM_ON_START, RANDOM_EACH_ROUND, VOTE }

    public static class GameMap {
        public final String key;
        public String displayName;
        public final String world;
        public boolean enabled;
        public Material icon;
        public final List<String> spawns = new ArrayList<>();
        public final List<String> gold = new ArrayList<>();
        /** Eigene Wartelobby dieser Map ("" = keine, dann gilt die globale Lobby). */
        public String lobby = "";

        GameMap(String key, String displayName, String world) {
            this.key = key;
            this.displayName = displayName;
            this.world = world;
            this.enabled = true;
            this.icon = Material.FILLED_MAP;
        }

        public List<Location> spawnLocations() {
            return resolve(spawns);
        }

        public List<Location> goldLocations() {
            return resolve(gold);
        }

        /** Spielbar = aktiviert und mindestens ein Spieler-Spawn gesetzt. */
        public boolean playable() {
            return enabled && !spawns.isEmpty();
        }
    }

    private final MurderMysteryPlugin plugin;
    private final File file;
    private final Random random = new Random();
    private final Map<String, GameMap> maps = new LinkedHashMap<>();
    private YamlConfiguration yml;
    private String lobby = "";
    private Mode mode = Mode.VOTE;
    private int voteOptions = 3;
    private boolean avoidRepeat = true;
    private String lastMap;
    /** Map für die ganze Laufzeit (RANDOM_ON_START). */
    private GameMap startMap;
    /** Per /mm map next festgelegte Map für die nächste Runde. */
    private GameMap forcedNext;
    /** Im Modus RANDOM_EACH_ROUND schon in der Lobby ausgeloste Map (damit ihre Lobby genutzt werden kann). */
    private GameMap planned;

    public MapManager(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "maps.yml");
    }

    // ------------------------------------------------------------------ Laden & Speichern

    public void load() {
        if (!file.exists()) plugin.saveResource("maps.yml", false);
        yml = YamlConfiguration.loadConfiguration(file);
        FileConfiguration config = plugin.getConfig();
        lobby = config.getString("arena.lobby", "");

        mode = parseMode(yml.getString("mode", "VOTE"));
        voteOptions = Math.max(2, yml.getInt("vote-options", 3));
        avoidRepeat = yml.getBoolean("avoid-repeat", true);
        lastMap = yml.getString("last-map", null);

        maps.clear();
        ConfigurationSection section = yml.getConfigurationSection("maps");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) continue;
                GameMap m = new GameMap(key.toLowerCase(Locale.ROOT), Util.color(s.getString("display-name", key)), s.getString("world", key));
                m.enabled = s.getBoolean("enabled", true);
                Material icon = Material.matchMaterial(s.getString("icon", "FILLED_MAP"));
                if (icon != null && icon.isItem()) m.icon = icon;
                m.spawns.addAll(s.getStringList("spawns"));
                m.gold.addAll(s.getStringList("gold"));
                m.lobby = s.getString("lobby", "");
                maps.put(m.key, m);
            }
        }
        migrateOldArena(config);
        // Bei /mm reload die bisherige Auswahl behalten (die Map-Objekte wurden neu angelegt)
        forcedNext = forcedNext == null ? null : maps.get(forcedNext.key);
        startMap = startMap == null ? null : maps.get(startMap.key);
        planned = planned == null ? null : maps.get(planned.key);
    }

    /** Bis Version 2.2 gab es nur eine Arena in der config.yml – daraus wird jetzt eine Map. */
    private void migrateOldArena(FileConfiguration config) {
        List<String> spawns = config.getStringList("arena.spawns");
        List<String> gold = config.getStringList("arena.gold");
        if (!config.isSet("arena.spawns") && !config.isSet("arena.gold")) return;
        if (!spawns.isEmpty() || !gold.isEmpty()) {
            String worldName = (!spawns.isEmpty() ? spawns.get(0) : gold.get(0)).split(";")[0];
            String key = worldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
            GameMap m = maps.computeIfAbsent(key, k -> new GameMap(k, worldName, worldName));
            m.spawns.addAll(spawns);
            m.gold.addAll(gold);
            plugin.getLogger().info("Die bisherige Arena wurde als Map '" + key + "' in die maps.yml übernommen.");
        }
        config.set("arena.spawns", null);
        config.set("arena.gold", null);
        config.setComments("arena", List.of("Die allgemeine Wartelobby für alle Maps. Wird per /mm setlobby gesetzt.",
                "Die Maps selbst stehen in der maps.yml."));
        plugin.saveConfig();
        save();
    }

    public void save() {
        List<String> mapsComment = yml.getComments("maps");
        List<String> lastComment = yml.getComments("last-map");
        yml.set("maps", null);
        for (GameMap m : maps.values()) {
            String base = "maps." + m.key + ".";
            yml.set(base + "display-name", m.displayName.replace('§', '&'));
            yml.set(base + "world", m.world);
            yml.set(base + "enabled", m.enabled);
            yml.set(base + "icon", m.icon.name());
            yml.set(base + "lobby", m.lobby);
            yml.set(base + "spawns", new ArrayList<>(m.spawns));
            yml.set(base + "gold", new ArrayList<>(m.gold));
        }
        if (maps.isEmpty()) yml.createSection("maps");
        yml.set("last-map", lastMap);
        yml.setComments("maps", mapsComment);
        yml.setComments("last-map", lastComment);
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("maps.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Lädt die Welten aller aktivierten Maps (einmal nach dem Serverstart). */
    public void loadWorlds() {
        for (GameMap m : maps.values()) {
            if (m.enabled && loadWorld(m.world) == null) {
                plugin.getLogger().warning("Map '" + m.key + "': Welt '" + m.world + "' wurde nicht gefunden – lege den Welt-Ordner in den Server-Ordner.");
            }
        }
        if (mode == Mode.RANDOM_ON_START && (startMap == null || !startMap.playable())) {
            startMap = pickRandom(playableMaps());
            if (startMap != null) {
                plugin.getLogger().info("Map für diese Serverlaufzeit: " + startMap.key);
                markPlayed(startMap);
            }
        }
    }

    /**
     * Gibt die Welt zurück und lädt sie bei Bedarf. Neue Welten werden NICHT erzeugt:
     * Gibt es die Welt nirgends auf der Festplatte, kommt null zurück.
     */
    public World loadWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        if (!worldExists(name)) return null;
        w = Bukkit.createWorld(new WorldCreator(name));
        if (w != null) plugin.getLogger().info("Welt '" + name + "' geladen.");
        return w;
    }

    /**
     * Gibt es die Welt auf der Festplatte? Neue Welt-Ordner liegen erst im Server-Ordner; Paper (ab 26.x) verschiebt sie
     * beim ersten Laden in den Haupt-Weltordner nach {@code <welt>/dimensions/minecraft/<name>}.
     */
    private boolean worldExists(String name) {
        File container = Bukkit.getWorldContainer();
        if (new File(container, name).isDirectory()) return true;
        String mainWorld = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName();
        return new File(container, mainWorld + File.separator + "dimensions" + File.separator + "minecraft"
                + File.separator + name.toLowerCase(Locale.ROOT)).isDirectory();
    }

    // ------------------------------------------------------------------ Lobby

    /** Die globale Wartelobby (Fallback für alle Maps ohne eigene Lobby). */
    public Location getLobby() {
        return Util.deserialize(lobby);
    }

    public void setLobby(Location l) {
        lobby = Util.serialize(l);
        plugin.getConfig().set("arena.lobby", lobby);
        plugin.saveConfig();
    }

    /**
     * Die Lobby, in der gerade gewartet wird: die eigene Lobby der nächsten Map, falls diese schon feststeht
     * und eine Lobby hat – sonst die globale Lobby.
     */
    public Location currentLobby() {
        GameMap next = knownNextMap();
        if (next != null) {
            Location own = Util.deserialize(next.lobby);
            if (own != null) return own;
        }
        return getLobby();
    }

    public void setMapLobby(GameMap m, Location l) {
        m.lobby = l == null ? "" : Util.serialize(l);
        save();
    }

    /** Spielbereit = es gibt eine globale Lobby und mindestens eine spielbare Map. */
    public boolean isReady() {
        return getLobby() != null && !playableMaps().isEmpty();
    }

    // ------------------------------------------------------------------ Maps verwalten

    public Mode mode() {
        return mode;
    }

    public int voteOptionCount() {
        return voteOptions;
    }

    public Map<String, GameMap> all() {
        return maps;
    }

    public GameMap get(String key) {
        return key == null ? null : maps.get(key.toLowerCase(Locale.ROOT));
    }

    public List<GameMap> playableMaps() {
        List<GameMap> list = new ArrayList<>();
        for (GameMap m : maps.values()) {
            if (m.playable() && Bukkit.getWorld(m.world) != null) list.add(m);
        }
        return list;
    }

    /** Alle Maps, die in dieser Welt liegen. */
    public List<GameMap> mapsIn(World w) {
        List<GameMap> list = new ArrayList<>();
        for (GameMap m : maps.values()) if (m.world.equals(w.getName())) list.add(m);
        return list;
    }

    public boolean isMapWorld(World w) {
        return !mapsIn(w).isEmpty();
    }

    public GameMap create(String key, World world) {
        GameMap m = new GameMap(key.toLowerCase(Locale.ROOT), key, world.getName());
        maps.put(m.key, m);
        save();
        return m;
    }

    public boolean delete(String key) {
        boolean removed = maps.remove(key.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    public void add(List<String> list, Location l) {
        list.add(Util.serialize(l));
        save();
    }

    /** Entfernt den Punkt mit der Nummer {@code number} (ab 1). */
    public boolean remove(List<String> list, int number) {
        if (number < 1 || number > list.size()) return false;
        list.remove(number - 1);
        save();
        return true;
    }

    public void clear(List<String> list) {
        list.clear();
        save();
    }

    // ------------------------------------------------------------------ Auswahl

    public void setForcedNext(GameMap m) {
        forcedNext = m;
    }

    public GameMap forcedNext() {
        return forcedNext;
    }

    /** Die feste Map dieser Serverlaufzeit (nur im Modus RANDOM_ON_START). */
    public GameMap startMap() {
        return startMap;
    }

    /** Lost im Modus RANDOM_EACH_ROUND schon in der Lobby die nächste Map aus (zu Beginn jeder Wartephase). */
    public void planNextRound() {
        planned = mode == Mode.RANDOM_EACH_ROUND ? pickRandom(playableMaps()) : null;
    }

    /** Die Map der nächsten Runde, falls sie schon feststeht (sonst null, z. B. während einer Abstimmung). */
    public GameMap knownNextMap() {
        if (forcedNext != null) return forcedNext;
        return switch (mode) {
            case RANDOM_ON_START -> startMap;
            case RANDOM_EACH_ROUND -> planned;
            case VOTE -> null;
        };
    }

    /** Zufällige Auswahl für die Abstimmung (ohne die zuletzt gespielte Map, wenn genug andere da sind). */
    public List<GameMap> voteCandidates() {
        List<GameMap> list = withoutLast(playableMaps());
        Collections.shuffle(list, random);
        return new ArrayList<>(list.subList(0, Math.min(voteOptions, list.size())));
    }

    /** Bestimmt die Map für die Runde. {@code voted} ist die Gewinner-Map der Abstimmung (oder null). */
    public GameMap chooseForRound(GameMap voted) {
        GameMap chosen;
        if (forcedNext != null && forcedNext.playable()) {
            chosen = forcedNext;
            forcedNext = null;
        } else {
            chosen = switch (mode) {
                case RANDOM_ON_START -> startMap != null && startMap.playable() ? startMap : pickRandom(playableMaps());
                case RANDOM_EACH_ROUND -> planned != null && planned.playable() ? planned : pickRandom(playableMaps());
                case VOTE -> voted != null ? voted : pickRandom(playableMaps());
            };
        }
        planned = null;
        if (chosen != null && mode != Mode.RANDOM_ON_START) markPlayed(chosen);
        return chosen;
    }

    private GameMap pickRandom(List<GameMap> list) {
        list = withoutLast(list);
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    private List<GameMap> withoutLast(List<GameMap> list) {
        List<GameMap> copy = new ArrayList<>(list);
        if (avoidRepeat && copy.size() > 1 && lastMap != null) {
            copy.removeIf(m -> m.key.equals(lastMap));
        }
        return copy;
    }

    private void markPlayed(GameMap m) {
        lastMap = m.key;
        save();
    }

    private static Mode parseMode(String s) {
        try {
            return Mode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.VOTE;
        }
    }

    private static List<Location> resolve(List<String> raw) {
        List<Location> out = new ArrayList<>();
        for (String s : raw) {
            Location l = Util.deserialize(s);
            if (l != null) out.add(l);
        }
        return out;
    }
}
