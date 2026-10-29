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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Verwaltet die globale Wartelobby (config.yml), die Auswahl-Einstellungen (maps.yml)
 * und alle Maps im Ordner {@code plugins/MurderMystery/arenas/}: je Map eine .yml-Datei, daneben die Welt-Ordner.
 * <p>
 * Welt einer Map: Liegt im arenas-Ordner ein Welt-Ordner mit dem Namen aus {@code world:}, wird er beim ersten Mal
 * als Server-Welt {@code mm_<name>} kopiert und geladen. Sonst ist {@code world:} der Name einer vorhandenen Server-Welt.
 */
public class MapManager {

    /** Wie die Map für die nächste Runde bestimmt wird. */
    public enum Mode { RANDOM_ON_START, RANDOM_EACH_ROUND, VOTE }

    /** Welche Lobby eine Map nutzt. */
    public enum LobbyMode { GLOBAL, OWN }

    public static class GameMap {
        public final String key;
        /** Datei arenas/<key>.yml */
        public final File file;
        YamlConfiguration yml;
        public String displayName;
        /** Wert von "world:" in der Datei (Welt-Ordner im arenas-Ordner oder Name einer Server-Welt). */
        public String worldSetting;
        /** Name der Server-Welt, in der gespielt wird. */
        public String world;
        public boolean enabled = true;
        public Material icon = Material.FILLED_MAP;
        public String description = "";
        public String author = "";
        public LobbyMode lobbyMode = LobbyMode.GLOBAL;
        /** Eigene Wartelobby (intern "welt;x;y;z;yaw;pitch", "" = keine). */
        public String lobby = "";
        public int lobbyRadius = 15;
        public boolean areaEnabled;
        public String corner1 = "";
        public String corner2 = "";
        public final List<String> spawns = new ArrayList<>();
        public final List<String> gold = new ArrayList<>();

        GameMap(String key, File file) {
            this.key = key;
            this.file = file;
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

        /** Die eigene Lobby, falls die Map eine nutzt (Modus OWN und Position gesetzt). */
        public Location ownLobby() {
            return lobbyMode == LobbyMode.OWN ? Util.deserialize(lobby) : null;
        }

        /** Liegt die Position im Spielbereich? Ohne aktiven Spielbereich immer true. */
        public boolean inArea(Location l) {
            Location a = Util.deserialize(corner1), b = Util.deserialize(corner2);
            if (!areaEnabled || a == null || b == null) return true;
            if (!l.getWorld().equals(a.getWorld())) return false;
            return between(l.getX(), a.getX(), b.getX()) && between(l.getY(), a.getY(), b.getY()) && between(l.getZ(), a.getZ(), b.getZ());
        }

        private static boolean between(double v, double a, double b) {
            return v >= Math.min(a, b) && v <= Math.max(a, b) + 1;
        }
    }

    private final MurderMysteryPlugin plugin;
    private final File settingsFile;
    private final File arenasDir;
    private final Random random = new Random();
    private final Map<String, GameMap> maps = new LinkedHashMap<>();
    private YamlConfiguration settings;
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
        this.settingsFile = new File(plugin.getDataFolder(), "maps.yml");
        this.arenasDir = new File(plugin.getDataFolder(), "arenas");
    }

    public File arenasDir() {
        return arenasDir;
    }

    // ------------------------------------------------------------------ Laden

    public void load() {
        if (!settingsFile.exists()) plugin.saveResource("maps.yml", false);
        arenasDir.mkdirs();
        settings = YamlConfiguration.loadConfiguration(settingsFile);
        FileConfiguration config = plugin.getConfig();
        lobby = config.getString("arena.lobby", "");

        mode = parseMode(settings.getString("mode", "VOTE"));
        voteOptions = Math.max(2, settings.getInt("vote-options", 3));
        avoidRepeat = settings.getBoolean("avoid-repeat", true);
        lastMap = settings.getString("last-map", null);

        migrateMapsYml();
        migrateOldArena(config);

        maps.clear();
        File[] files = arenasDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                GameMap m = read(f);
                maps.put(m.key, m);
            }
        }
        // Bei /mm reload die bisherige Auswahl behalten (die Map-Objekte wurden neu angelegt)
        forcedNext = forcedNext == null ? null : maps.get(forcedNext.key);
        startMap = startMap == null ? null : maps.get(startMap.key);
        planned = planned == null ? null : maps.get(planned.key);
    }

    private GameMap read(File f) {
        String key = keyOf(f.getName().replaceFirst("(?i)\\.yml$", ""));
        GameMap m = new GameMap(key, f);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        m.yml = y;
        m.displayName = Util.color(y.getString("name", key));
        m.worldSetting = y.getString("world", key);
        m.world = serverWorldName(m.worldSetting);
        m.enabled = y.getBoolean("enabled", true);
        Material icon = Material.matchMaterial(y.getString("icon", "FILLED_MAP"));
        if (icon != null && icon.isItem()) m.icon = icon;
        m.description = Util.color(y.getString("description", ""));
        m.author = y.getString("author", "");
        m.lobbyMode = "OWN".equalsIgnoreCase(y.getString("lobby.mode", "GLOBAL")) ? LobbyMode.OWN : LobbyMode.GLOBAL;
        m.lobby = toInternal(m.world, y.getString("lobby.location", ""));
        m.lobbyRadius = Math.max(0, y.getInt("lobby.radius", 15));
        m.areaEnabled = y.getBoolean("area.enabled", false);
        m.corner1 = toInternal(m.world, y.getString("area.corner1", ""));
        m.corner2 = toInternal(m.world, y.getString("area.corner2", ""));
        for (String s : y.getStringList("spawns")) m.spawns.add(toInternal(m.world, s));
        for (String s : y.getStringList("gold")) m.gold.add(toInternal(m.world, s));
        return m;
    }

    /** Welt-Ordner im arenas-Ordner → Server-Welt mm_<name>; sonst ist es schon der Name einer Server-Welt. */
    private String serverWorldName(String setting) {
        if (setting == null || setting.isEmpty()) return "";
        return isTemplate(setting) ? "mm_" + keyOf(setting) : setting;
    }

    /** Liegt im arenas-Ordner ein Welt-Ordner (mit level.dat) mit diesem Namen? */
    public boolean isTemplate(String folder) {
        return folder != null && !folder.isEmpty() && templateRoot(new File(arenasDir, folder)) != null;
    }

    // ------------------------------------------------------------------ Speichern

    /** Speichert die Auswahl-Einstellungen und alle Maps. */
    public void save() {
        settings.set("mode", mode.name());
        settings.set("last-map", lastMap);
        trySave(settings, settingsFile);
        for (GameMap m : maps.values()) saveMap(m);
    }

    public void saveMap(GameMap m) {
        YamlConfiguration y = m.yml != null ? m.yml : template();
        y.set("name", m.displayName.replace('§', '&'));
        y.set("world", m.worldSetting);
        y.set("enabled", m.enabled);
        y.set("icon", m.icon.name());
        y.set("description", m.description.replace('§', '&'));
        y.set("author", m.author);
        y.set("lobby.mode", m.lobbyMode.name());
        y.set("lobby.location", toConfig(m.world, m.lobby));
        y.set("lobby.radius", m.lobbyRadius);
        y.set("area.enabled", m.areaEnabled);
        y.set("area.corner1", toConfig(m.world, m.corner1, false));
        y.set("area.corner2", toConfig(m.world, m.corner2, false));
        List<String> spawns = new ArrayList<>();
        for (String s : m.spawns) spawns.add(toConfig(m.world, s));
        y.set("spawns", spawns);
        List<String> gold = new ArrayList<>();
        for (String s : m.gold) gold.add(toConfig(m.world, s, false));
        y.set("gold", gold);
        m.yml = y;
        trySave(y, m.file);
    }

    private YamlConfiguration template() {
        var in = plugin.getResource("arena-template.yml");
        return in == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private void trySave(YamlConfiguration y, File f) {
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning(f.getName() + " konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Umstellung älterer Versionen

    /** Version 2.3 (Vorabversion): Maps standen in der maps.yml unter "maps:" – jetzt je eine Datei in arenas/. */
    private void migrateMapsYml() {
        ConfigurationSection section = settings.getConfigurationSection("maps");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            File f = new File(arenasDir, keyOf(key) + ".yml");
            if (s == null || f.exists()) continue;
            GameMap m = new GameMap(keyOf(key), f);
            m.displayName = Util.color(s.getString("display-name", key));
            m.worldSetting = s.getString("world", key);
            m.world = m.worldSetting;
            m.enabled = s.getBoolean("enabled", true);
            Material icon = Material.matchMaterial(s.getString("icon", "FILLED_MAP"));
            if (icon != null && icon.isItem()) m.icon = icon;
            m.description = Util.color(s.getString("description", ""));
            m.author = s.getString("author", "");
            m.lobby = s.getString("lobby", "");
            m.lobbyMode = m.lobby.isEmpty() ? LobbyMode.GLOBAL : LobbyMode.OWN;
            m.spawns.addAll(s.getStringList("spawns"));
            m.gold.addAll(s.getStringList("gold"));
            saveMap(m);
            plugin.getLogger().info("Map '" + m.key + "' aus der maps.yml nach arenas/" + f.getName() + " übernommen.");
        }
        settings.set("maps", null);
        trySave(settings, settingsFile);
    }

    /** Bis Version 2.2 gab es nur eine Arena in der config.yml – daraus wird jetzt eine Map. */
    private void migrateOldArena(FileConfiguration config) {
        if (!config.isSet("arena.spawns") && !config.isSet("arena.gold")) return;
        List<String> spawns = config.getStringList("arena.spawns");
        List<String> gold = config.getStringList("arena.gold");
        if (!spawns.isEmpty() || !gold.isEmpty()) {
            String worldName = (!spawns.isEmpty() ? spawns.get(0) : gold.get(0)).split(";")[0];
            File f = new File(arenasDir, keyOf(worldName) + ".yml");
            if (!f.exists()) {
                GameMap m = new GameMap(keyOf(worldName), f);
                m.displayName = worldName;
                m.worldSetting = worldName;
                m.world = worldName;
                m.spawns.addAll(spawns);
                m.gold.addAll(gold);
                saveMap(m);
                plugin.getLogger().info("Die bisherige Arena wurde als Map arenas/" + f.getName() + " übernommen.");
            }
        }
        config.set("arena.spawns", null);
        config.set("arena.gold", null);
        config.setComments("arena", List.of("Die globale Wartelobby für alle Maps. Wird per /mm setlobby gesetzt.",
                "Die Maps selbst liegen im Ordner arenas/."));
        plugin.saveConfig();
    }

    // ------------------------------------------------------------------ Welten

    /** Kopiert Welt-Ordner aus arenas/ auf den Server (nur beim ersten Mal) und lädt die Welten aller aktiven Maps. */
    public void loadWorlds() {
        for (GameMap m : maps.values()) {
            if (!m.enabled) continue;
            installTemplate(m);
            loadWorld(m.world);
        }
        for (GameMap m : maps.values()) {
            String problem = problem(m);
            plugin.getLogger().info("Map '" + m.key + "': " + (problem == null ? "spielbar" : "NICHT spielbar – " + problem));
        }
        if (mode == Mode.RANDOM_ON_START && (startMap == null || !startMap.playable())) {
            startMap = pickRandom(playableMaps());
            if (startMap != null) {
                plugin.getLogger().info("Map für diese Serverlaufzeit: " + startMap.key);
                markPlayed(startMap);
            }
        }
    }

    /** Kopiert den Welt-Ordner der Map aus arenas/ in den Server-Ordner, falls die Server-Welt noch nicht existiert. */
    public void installTemplate(GameMap m) {
        if (!isTemplate(m.worldSetting) || worldExists(m.world)) return;
        Path source = templateRoot(new File(arenasDir, m.worldSetting));
        Path target = new File(Bukkit.getWorldContainer(), m.world).toPath();
        try {
            copyTree(source, target);
            Files.deleteIfExists(target.resolve("uid.dat"));      // sonst meldet der Server eine doppelte Welt
            Files.deleteIfExists(target.resolve("session.lock"));
            plugin.getLogger().info("Welt-Ordner arenas/" + m.worldSetting + " als Welt '" + m.world + "' installiert.");
        } catch (IOException e) {
            plugin.getLogger().warning("Welt-Ordner arenas/" + m.worldSetting + " konnte nicht kopiert werden: " + e.getMessage());
        }
    }

    /**
     * Übernimmt eine geänderte Version des Welt-Ordners aus arenas/: Server-Welt entladen, löschen, neu kopieren, laden.
     * Gibt eine Fehlermeldung zurück oder null bei Erfolg. Spieler in der Welt werden vorher zur Lobby gebracht.
     */
    public String reinstall(GameMap m) {
        if (!isTemplate(m.worldSetting)) return "Die Map nutzt keinen Welt-Ordner aus arenas/, sondern die Server-Welt '" + m.world + "'.";
        World w = Bukkit.getWorld(m.world);
        if (w != null) {
            Location fallback = getLobby() != null && !getLobby().getWorld().equals(w) ? getLobby() : Bukkit.getWorlds().get(0).getSpawnLocation();
            for (var p : w.getPlayers()) p.teleport(fallback);
            if (!Bukkit.unloadWorld(w, false)) return "Die Welt '" + m.world + "' ließ sich nicht entladen.";
        }
        File container = Bukkit.getWorldContainer();
        List<File> old = new ArrayList<>();
        old.add(new File(container, m.world));
        String mainWorld = Bukkit.getWorlds().get(0).getName();
        File[] namespaces = new File(container, mainWorld + File.separator + "dimensions").listFiles(File::isDirectory);
        if (namespaces != null) {
            for (File ns : namespaces) old.add(new File(ns, m.world.toLowerCase(Locale.ROOT)));
        }
        for (File f : old) {
            if (!f.exists()) continue;
            try (Stream<Path> s = Files.walk(f.toPath())) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                return "Alte Welt-Daten konnten nicht gelöscht werden: " + e.getMessage();
            }
        }
        installTemplate(m);
        return loadWorld(m.world) == null ? "Die neue Welt ließ sich nicht laden." : null;
    }

    /**
     * Gibt die Welt zurück und lädt sie bei Bedarf. Neue Welten werden NICHT erzeugt:
     * Gibt es die Welt nirgends auf der Festplatte, kommt null zurück.
     */
    public World loadWorld(String name) {
        if (name == null || name.isEmpty()) return null;
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        if (!worldExists(name)) return null;
        w = Bukkit.createWorld(new WorldCreator(name));
        if (w != null) plugin.getLogger().info("Welt '" + name + "' geladen.");
        return w;
    }

    /**
     * Gibt es die Welt auf der Festplatte? Neue Welt-Ordner liegen erst im Server-Ordner; Paper (ab 26.x) verschiebt sie
     * beim ersten Laden in den Haupt-Weltordner nach {@code <welt>/dimensions/<namespace>/<name>}.
     */
    public boolean worldExists(String name) {
        File container = Bukkit.getWorldContainer();
        if (new File(container, name).isDirectory()) return true;
        String mainWorld = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName();
        File[] namespaces = new File(container, mainWorld + File.separator + "dimensions").listFiles(File::isDirectory);
        if (namespaces == null) return false;
        for (File ns : namespaces) {
            if (new File(ns, name.toLowerCase(Locale.ROOT)).isDirectory()) return true;
        }
        return false;
    }

    /** Warum ist die Map nicht spielbar? null = spielbar. Lädt die Welt bei Bedarf nach. */
    public String problem(GameMap m) {
        if (!m.enabled) return "deaktiviert (enabled: false)";
        if (m.world.isEmpty()) return "keine Welt eingetragen (world: in arenas/" + m.file.getName() + ")";
        if (loadWorld(m.world) == null) {
            return isTemplate(m.worldSetting) ? "Welt-Ordner arenas/" + m.worldSetting + " konnte nicht geladen werden"
                    : "Welt '" + m.worldSetting + "' nicht gefunden (weder in arenas/ noch auf dem Server)";
        }
        if (m.spawns.isEmpty()) return "keine Spieler-Spawns (/mm addspawn in der Map oder spawns: in arenas/" + m.file.getName() + ")";
        if (m.spawnLocations().isEmpty()) return "die Spawns liegen in einer nicht geladenen Welt";
        return null;
    }

    /** Der Ordner mit der level.dat (bis zu 3 Ebenen tief), oder null. */
    static Path templateRoot(File dir) {
        if (!dir.isDirectory()) return null;
        try (Stream<Path> s = Files.walk(dir.toPath(), 3)) {
            return s.filter(p -> p.getFileName().toString().equals("level.dat"))
                    .map(Path::getParent)
                    .min(java.util.Comparator.comparingInt(Path::getNameCount))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(dest);
                else Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // ------------------------------------------------------------------ Lobby

    /** Die globale Wartelobby (für alle Maps mit lobby.mode: GLOBAL). */
    public Location getLobby() {
        return Util.deserialize(lobby);
    }

    public void setLobby(Location l) {
        lobby = Util.serialize(l);
        plugin.getConfig().set("arena.lobby", lobby);
        plugin.saveConfig();
    }

    /**
     * Die Lobby, in der gerade gewartet wird: die eigene Lobby der nächsten Map (lobby.mode: OWN), falls diese schon
     * feststeht – sonst die globale Lobby.
     */
    public Location currentLobby() {
        GameMap next = knownNextMap();
        Location own = next == null ? null : next.ownLobby();
        return own != null ? own : getLobby();
    }

    /** Wie weit sich wartende Spieler von der aktuellen Lobby entfernen dürfen (0 = beliebig). */
    public int currentLobbyRadius() {
        GameMap next = knownNextMap();
        if (next != null && next.ownLobby() != null) return next.lobbyRadius;
        return plugin.settings().lobbyMaxDistance;
    }

    /** Setzt die eigene Lobby der Map (Modus OWN). {@code null} = zurück zur globalen Lobby. */
    public void setMapLobby(GameMap m, Location l) {
        if (l == null) {
            m.lobbyMode = LobbyMode.GLOBAL;
        } else {
            m.lobby = Util.serialize(l);
            m.lobbyMode = LobbyMode.OWN;
        }
        saveMap(m);
    }

    /** Spielbereit = es gibt eine globale Lobby und mindestens eine spielbare Map. */
    public boolean isReady() {
        return getLobby() != null && !playableMaps().isEmpty();
    }

    // ------------------------------------------------------------------ Maps verwalten

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = m;
        if (m == Mode.RANDOM_ON_START && (startMap == null || !startMap.playable())) {
            startMap = pickRandom(playableMaps());
        }
        save();
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
            if (m.playable() && problem(m) == null) list.add(m);
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

    /** Legt arenas/<key>.yml an. {@code worldSetting}: Welt-Ordner in arenas/ oder Name einer Server-Welt. */
    public GameMap create(String key, String worldSetting) {
        key = keyOf(key);
        GameMap m = new GameMap(key, new File(arenasDir, key + ".yml"));
        m.displayName = key;
        m.worldSetting = worldSetting;
        m.world = serverWorldName(worldSetting);
        maps.put(m.key, m);
        saveMap(m);
        return m;
    }

    public GameMap create(String key, World world) {
        return create(key, world.getName());
    }

    /** Löscht die Datei der Map (Welt-Ordner und Server-Welt bleiben erhalten). */
    public boolean delete(String key) {
        GameMap m = maps.remove(key.toLowerCase(Locale.ROOT));
        if (m == null) return false;
        if (!m.file.delete()) plugin.getLogger().warning("arenas/" + m.file.getName() + " konnte nicht gelöscht werden.");
        return true;
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
        settings.set("last-map", lastMap);
        trySave(settings, settingsFile);
    }

    // ------------------------------------------------------------------ Positionen

    /** Map-Name aus einem Datei- oder Ordnernamen, z. B. "Villa Neu" → "villa_neu". */
    public static String keyOf(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    /** "x y z [yaw pitch]" aus der Arena-Datei → intern "welt;x;y;z;yaw;pitch". Einträge mit ';' bleiben, wie sie sind. */
    static String toInternal(String world, String s) {
        if (s == null || s.isBlank()) return "";
        if (s.contains(";")) return s;
        String[] p = s.trim().split("[\\s,]+");
        if (p.length < 3) return "";
        String yaw = p.length > 3 ? p[3] : "0";
        String pitch = p.length > 4 ? p[4] : "0";
        return world + ";" + p[0] + ";" + p[1] + ";" + p[2] + ";" + yaw + ";" + pitch;
    }

    static String toConfig(String world, String internal) {
        return toConfig(world, internal, true);
    }

    /** Intern "welt;x;y;z;yaw;pitch" → "x y z yaw pitch" für die Arena-Datei (andere Welten bleiben vollständig). */
    static String toConfig(String world, String internal, boolean withDirection) {
        if (internal == null || internal.isEmpty()) return "";
        String[] p = internal.split(";");
        if (p.length < 4 || !p[0].equals(world)) return internal;
        String pos = num(p[1]) + " " + num(p[2]) + " " + num(p[3]);
        if (!withDirection || p.length < 6) return pos;
        return pos + " " + num(p[4]) + " " + num(p[5]);
    }

    private static String num(String s) {
        try {
            double d = Double.parseDouble(s);
            return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.2f", d).replaceAll("0+$", "");
        } catch (NumberFormatException e) {
            return s;
        }
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
