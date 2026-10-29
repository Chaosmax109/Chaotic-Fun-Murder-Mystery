package de.murdermystery;

import de.murdermystery.MapManager.GameMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MurderMysteryPlugin extends JavaPlugin {

    private Settings settings;
    private StatsManager stats;
    private MapManager maps;
    private MapImporter importer;
    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stats = new StatsManager(this);
        loadSettings();
        maps = new MapManager(this);
        maps.load();
        importer = new MapImporter(this, maps);
        importer.prepareFolder();
        game = new Game(this, maps);
        game.startTasks();
        getServer().getPluginManager().registerEvents(new GameListener(this, game), this);
        CommandTabCompleter completer = new CommandTabCompleter(this);
        getCommand("mm").setTabCompleter(completer);
        getCommand("stats").setTabCompleter(completer);

        // Welten erst laden, wenn der Server fertig gestartet ist
        Bukkit.getScheduler().runTask(this, () -> {
            maps.loadWorlds();
            importer.importAll(null); // neue Welt-Ordner / ZIPs in plugins/MurderMystery/arenas/
            if (maps.all().isEmpty() && importer.pending().isEmpty()) {
                // Allererster Start: eine spielbare Standard-Map erzeugen, damit das Plugin sofort funktioniert
                getLogger().info("Noch keine Map vorhanden – erzeuge die Standard-Map 'default_map' (Flachwelt mit Arena und Lobby).");
                TestMapBuilder.build(maps);
            }
            game.newVoteRound();
            if (maps.playableMaps().isEmpty()) {
                getLogger().warning("Noch nicht eingerichtet: mindestens eine Map mit Spawns (/mm map create, /mm addspawn).");
            } else if (maps.getLobby() == null) {
                getLogger().info("Keine globale Lobby gesetzt – die Position des ersten Spielers, der den Server betritt, wird als Lobby gespeichert.");
            }
            if (settings.autoJoin) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    game.join(p); // Spieler, die bei /reload schon online sind
                }
            }
        });
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        if (stats != null) {
            stats.close();
        }
    }

    /** Lädt config.yml, roles.yml, gold.yml und database.yml (neu) und verbindet die Datenbank. */
    private void loadSettings() {
        reloadConfig();
        File rolesFile = file("roles.yml");
        File goldFile = file("gold.yml");
        File rewardsFile = file("gold-rewards.yml");
        File dbFile = file("database.yml");
        // roles.yml und gold-rewards.yml ohne Standardwerte laden: sonst kämen gelöschte Beispielrollen bei jedem Reload zurück
        YamlConfiguration roles = YamlConfiguration.loadConfiguration(rolesFile);
        YamlConfiguration rewards = YamlConfiguration.loadConfiguration(rewardsFile);
        YamlConfiguration gold = loadWithDefaults(goldFile, true);
        YamlConfiguration db = loadWithDefaults(dbFile, true);

        if (Settings.migrate(getConfig(), roles, gold, rewards)) {
            getLogger().info("Einstellungen wurden ins aktuelle Format umgestellt (roles.yml / gold.yml / gold-rewards.yml).");
        }
        if (Settings.completeRoles(roles, bundled("roles.yml"))) {
            getLogger().info("Fehlende Einstellungen in roles.yml ergänzt (z. B. chance, show-chance).");
        }
        // Neue Einstellungen aus Updates in die vorhandenen Dateien übernehmen
        getConfig().options().copyDefaults(true);
        saveConfig();
        save(roles, rolesFile);
        save(rewards, rewardsFile);
        save(gold, goldFile);
        save(db, dbFile);
        settings = new Settings(getConfig(), roles, gold, rewards, getLogger());
        stats.connect(db);
    }

    private File file(String name) {
        File f = new File(getDataFolder(), name);
        if (!f.exists()) saveResource(name, false);
        return f;
    }

    /** Die im Plugin mitgelieferte Version einer Config-Datei (inklusive Kommentaren). */
    private YamlConfiguration bundled(String name) {
        InputStream in = getResource(name);
        return in == null ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private YamlConfiguration loadWithDefaults(File file, boolean copy) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        InputStream in = getResource(file.getName());
        if (in != null && copy) {
            yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            yml.options().copyDefaults(true);
        }
        return yml;
    }

    private void save(YamlConfiguration yml, File file) {
        try {
            yml.save(file);
        } catch (IOException e) {
            getLogger().warning(file.getName() + " konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public Settings settings() {
        return settings;
    }

    public StatsManager stats() {
        return stats;
    }

    public MapManager maps() {
        return maps;
    }

    public MapImporter importer() {
        return importer;
    }

    public Game game() {
        return game;
    }

    // ------------------------------------------------------------------ Befehle

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            // Aus der Konsole: Test-Map erzeugen und Maps auflisten
            if (args.length >= 2 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("testmap")) {
                GameMap test = TestMapBuilder.build(maps);
                sender.sendMessage("Test-Map '" + test.key + "' erstellt: Welt " + TestMapBuilder.WORLD + ", "
                        + test.spawns.size() + " Spawns, " + test.gold.size() + " Gold-Spawns, eigene Lobby.");
                game.refreshLobby();
            } else if (args.length >= 2 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("list")) {
                for (GameMap m : maps.all().values()) {
                    String problem = maps.problem(m);
                    sender.sendMessage(m.key + " (Welt " + m.world + "): " + (problem == null ? "spielbar" : "NICHT spielbar – " + problem)
                            + ", " + m.spawns.size() + " Spawns, " + m.gold.size() + " Gold, Lobby: " + (m.ownLobby() == null ? "global" : "eigene"));
                }
            } else if (args.length >= 2 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("import")) {
                importer.importAll(sender);
            } else {
                sender.sendMessage("Aus der Konsole gehen nur: mm map testmap, mm map list, mm map import");
            }
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("stats")) {
            openStats(p, args.length > 0 ? args[0] : null);
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";

        switch (sub) {
            case "join" -> {
                game.join(p);
                return true;
            }
            case "leave" -> {
                game.leave(p, false);
                return true;
            }
            case "stats" -> {
                openStats(p, args.length > 1 ? args[1] : null);
                return true;
            }
            case "vote" -> {
                game.openVoteMenu(p);
                return true;
            }
            default -> {
            }
        }

        if (!p.hasPermission("mm.admin")) {
            sendHelp(p, false);
            return true;
        }

        Location loc = p.getLocation();
        switch (sub) {
            case "setlobby" -> {
                maps.setLobby(loc);
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Globale Lobby gesetzt. Sie gilt für alle Maps ohne eigene Lobby (/mm map setlobby).");
                game.refreshLobby();
            }
            case "tplobby" -> {
                GameMap m = args.length > 1 ? maps.get(args[1]) : null;
                if (args.length > 1 && m == null) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Keine Map mit dem Namen '" + args[1] + "'.");
                    return true;
                }
                Location lobby = m != null ? Util.deserialize(m.lobby) : maps.getLobby();
                if (lobby == null) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + (m != null ? "Die Map '" + m.key + "' hat keine eigene Lobby." : "Keine globale Lobby gesetzt.")
                            + ChatColor.GRAY + " (oder ihre Welt ist nicht geladen)");
                } else {
                    p.teleport(lobby);
                }
            }
            case "maps" -> MapAdminMenu.openOverview(this, p);
            case "map" -> mapCommand(p, args);
            case "addspawn", "addgold", "spawns", "golds", "removespawn", "removegold",
                 "tpspawn", "tpgold", "clearspawns", "cleargold" -> pointCommand(p, sub, args);
            case "start" -> game.forceStart(p);
            case "stop" -> game.forceStop();
            case "reload" -> {
                loadSettings();
                maps.load();
                maps.loadWorlds();
                game.refreshLobby();
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Alle Config-Dateien neu geladen. Stats: "
                        + (stats.isEnabled() ? ChatColor.GREEN + "aktiv" : ChatColor.RED + "deaktiviert"));
            }
            case "info" -> {
                p.sendMessage(Util.PREFIX + "Globale Lobby: " + (maps.getLobby() != null ? ChatColor.GREEN + "gesetzt" : ChatColor.RED + "fehlt (wird beim nächsten Join gesetzt)")
                        + ChatColor.GRAY + ", spielbare Maps: " + ChatColor.YELLOW + maps.playableMaps().size() + "/" + maps.all().size()
                        + ChatColor.GRAY + ", Modus: " + ChatColor.YELLOW + maps.mode()
                        + ChatColor.GRAY + ", Status: " + ChatColor.YELLOW + game.getState());
                for (GameMap m : maps.all().values()) {
                    if (m.playable() && m.spawns.size() < settings.maxPlayers) {
                        p.sendMessage(Util.PREFIX + ChatColor.YELLOW + "Tipp: Map " + m.key + " hat nur " + m.spawns.size()
                                + " Spawns – setze " + settings.maxPlayers + ", damit jeder Spieler einen eigenen bekommt.");
                    }
                }
            }
            default -> sendHelp(p, true);
        }
        return true;
    }

    /** /mm map list | create | delete | tp | enable | disable | next */
    private void mapCommand(Player p, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "list";
        String name = args.length > 2 ? args[2] : null;
        if (action.equals("list")) {
            p.sendMessage(Util.PREFIX + ChatColor.YELLOW + "Maps (" + maps.all().size() + ") – Modus " + maps.mode() + ":");
            for (GameMap m : maps.all().values()) {
                String problem = maps.problem(m);
                String status = problem == null ? ChatColor.GREEN + "spielbar" : ChatColor.RED + "NICHT spielbar: " + problem;
                p.sendMessage(ChatColor.GRAY + " " + m.key + ChatColor.DARK_GRAY + " (" + m.displayName + ChatColor.DARK_GRAY + ", Welt " + m.world + ") "
                        + status + ChatColor.GRAY + " – " + m.spawns.size() + " Spawns, " + m.gold.size() + " Gold, Lobby: "
                        + (m.ownLobby() == null ? "global" : "eigene"));
                p.sendMessage(ChatColor.DARK_GRAY + "   Datei: plugins/MurderMystery/arenas/" + m.file.getName() + " – Welt: "
                        + (maps.isTemplate(m.worldSetting) ? "arenas/" + m.worldSetting + "/" : "Server-Welt " + m.world + " (/mm map export " + m.key + ")"));
            }
            return;
        }
        if (action.equals("help")) {
            sendMapHelp(p);
            return;
        }
        if (action.equals("gui") || action.equals("menu")) {
            MapAdminMenu.openOverview(this, p);
            return;
        }
        if (action.equals("import")) {
            importer.importAll(p);
            return;
        }
        if (action.equals("testmap")) {
            p.sendMessage(Util.PREFIX + "Baue die Test-Map – einen Moment …");
            GameMap test = TestMapBuilder.build(maps);
            p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Test-Map '" + test.key + "' ist fertig: Flachwelt '" + TestMapBuilder.WORLD
                    + "' mit Arena (" + test.spawns.size() + " Spawns, " + test.gold.size() + " Gold-Spawns) und eigener Glas-Lobby.");
            p.sendMessage(Util.PREFIX + ChatColor.GRAY + "Ansehen: /mm map tp " + test.key + " – Lobby: /mm tplobby " + test.key
                    + " – nächste Runde darauf: /mm map next " + test.key);
            game.refreshLobby();
            return;
        }
        if (name == null && action.equals("setlobby")) {
            // Ohne Namen: die Map in der Welt, in der man steht
            List<GameMap> here = maps.mapsIn(p.getWorld());
            if (here.size() == 1) name = here.get(0).key;
        }
        if (name == null) {
            p.sendMessage(Util.PREFIX + ChatColor.RED + "Bitte einen Map-Namen angeben: /mm map " + action + " <name>");
            return;
        }
        if (action.equals("create")) {
            if (!name.matches("[A-Za-z0-9_-]+")) {
                p.sendMessage(Util.PREFIX + ChatColor.RED + "Der Name darf nur Buchstaben, Zahlen, _ und - enthalten.");
                return;
            }
            if (maps.get(name) != null) {
                p.sendMessage(Util.PREFIX + ChatColor.RED + "Eine Map mit diesem Namen gibt es schon.");
                return;
            }
            GameMap created;
            if (args.length > 3 && maps.isTemplate(args[3])) {
                // Welt-Ordner aus arenas/
                created = maps.create(name, args[3]);
                maps.installTemplate(created);
                maps.loadWorld(created.world);
            } else {
                World world = args.length > 3 ? maps.loadWorld(args[3]) : p.getWorld();
                if (world == null) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Welt '" + args[3] + "' nicht gefunden – weder als Ordner in arenas/ noch auf dem Server.");
                    return;
                }
                created = maps.create(name, world);
            }
            p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Map '" + created.key + "' angelegt: arenas/" + created.file.getName()
                    + " (Welt " + created.world + "). " + ChatColor.GRAY + "Jetzt hingehen (/mm map tp " + created.key
                    + ") und /mm addspawn sowie /mm addgold setzen.");
            game.refreshLobby();
            return;
        }
        GameMap m = maps.get(name);
        if (m == null) {
            p.sendMessage(Util.PREFIX + ChatColor.RED + "Keine Map mit dem Namen '" + name + "'. Liste: /mm map list");
            return;
        }
        String rest = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
        switch (action) {
            case "edit" -> MapAdminMenu.openEditor(this, p, m);
            case "area" -> {
                String what = args.length > 3 ? args[3].toLowerCase() : "";
                switch (what) {
                    case "corner1", "corner2", "pos1", "pos2" -> {
                        if (!p.getWorld().getName().equals(m.world)) {
                            p.sendMessage(Util.PREFIX + ChatColor.RED + "Du musst in der Welt der Map stehen (" + m.world + ").");
                            return;
                        }
                        if (what.endsWith("1")) m.corner1 = Util.serialize(p.getLocation());
                        else m.corner2 = Util.serialize(p.getLocation());
                        m.areaEnabled = !m.corner1.isEmpty() && !m.corner2.isEmpty();
                        maps.saveMap(m);
                        p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Ecke " + what.charAt(what.length() - 1) + " des Spielbereichs gesetzt."
                                + (m.areaEnabled ? " Spielbereich ist aktiv." : ChatColor.GRAY + " Jetzt noch die andere Ecke setzen."));
                    }
                    case "on", "off" -> {
                        m.areaEnabled = what.equals("on");
                        maps.saveMap(m);
                        p.sendMessage(Util.PREFIX + "Spielbereich " + (m.areaEnabled ? ChatColor.GREEN + "aktiv" : ChatColor.RED + "aus") + ".");
                    }
                    default -> p.sendMessage(Util.PREFIX + ChatColor.RED + "/mm map area " + m.key + " <corner1|corner2|on|off>");
                }
            }
            case "export" -> {
                if (maps.isTemplate(m.worldSetting)) {
                    p.sendMessage(Util.PREFIX + "Die Welt liegt schon als Ordner in arenas/" + m.worldSetting + ".");
                    return;
                }
                String folder = args.length > 3 ? args[3] : m.key;
                p.sendMessage(Util.PREFIX + "Speichere die Welt '" + m.world + "' als Ordner arenas/" + folder + " …");
                maps.exportTemplate(m, folder, error -> p.sendMessage(Util.PREFIX + (error == null
                        ? ChatColor.GREEN + "Fertig: plugins/MurderMystery/arenas/" + folder + "/" + ChatColor.GRAY + " – zum Ansehen, Herunterladen oder Bearbeiten."
                        : ChatColor.RED + error)));
            }
            case "reinstall" -> {
                p.sendMessage(Util.PREFIX + "Übernehme die aktuelle Version von arenas/" + m.worldSetting + " …");
                String error = maps.reinstall(m);
                p.sendMessage(Util.PREFIX + (error == null ? ChatColor.GREEN + "Welt neu installiert." : ChatColor.RED + error));
                game.refreshLobby();
            }
            case "rename", "description", "author" -> {
                if (action.equals("rename") && rest.isEmpty()) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Bitte einen Namen angeben: /mm map rename " + m.key + " <Anzeigename>");
                    return;
                }
                switch (action) {
                    case "rename" -> m.displayName = Util.color(rest);
                    case "description" -> m.description = Util.color(rest);
                    default -> m.author = rest;
                }
                maps.save();
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Gespeichert." + ChatColor.DARK_GRAY + " (Map " + m.key + ")");
                game.refreshLobby();
            }
            case "delete" -> {
                maps.delete(m.key);
                p.sendMessage(Util.PREFIX + "Map '" + m.key + "' gelöscht (die Welt selbst bleibt erhalten).");
                game.refreshLobby();
            }
            case "tp" -> {
                World w = maps.loadWorld(m.world);
                if (w == null) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Die Welt '" + m.world + "' wurde nicht gefunden.");
                    return;
                }
                List<Location> spawns = m.spawnLocations();
                p.teleport(spawns.isEmpty() ? w.getSpawnLocation() : spawns.get(0));
            }
            case "enable", "disable" -> {
                m.enabled = action.equals("enable");
                if (m.enabled) maps.loadWorld(m.world);
                maps.save();
                p.sendMessage(Util.PREFIX + "Map '" + m.key + "' ist jetzt " + (m.enabled ? ChatColor.GREEN + "aktiviert" : ChatColor.RED + "deaktiviert") + ".");
                game.refreshLobby();
            }
            case "setlobby" -> {
                maps.setMapLobby(m, p.getLocation());
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Eigene Lobby für Map '" + m.key + "' gesetzt. "
                        + ChatColor.GRAY + "Sie wird genutzt, sobald feststeht, dass als Nächstes diese Map gespielt wird.");
                game.refreshLobby();
            }
            case "clearlobby" -> {
                maps.setMapLobby(m, null);
                p.sendMessage(Util.PREFIX + "Map '" + m.key + "' nutzt jetzt wieder die globale Lobby.");
                game.refreshLobby();
            }
            case "next" -> {
                if (!m.playable()) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Map '" + m.key + "' ist nicht spielbar (deaktiviert oder ohne Spawns).");
                    return;
                }
                maps.setForcedNext(m);
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Die nächste Runde wird auf '" + m.key + "' gespielt.");
                game.refreshLobby();
            }
            default -> sendMapHelp(p);
        }
    }

    /**
     * Spawn-Befehle für eine Map. Die Map ist die, in deren Welt man steht – liegen mehrere Maps in derselben Welt,
     * wird der Map-Name als letztes Argument angegeben (z. B. /mm addspawn villa).
     */
    private void pointCommand(Player p, String sub, String[] args) {
        boolean needsNumber = sub.startsWith("remove") || sub.startsWith("tp");
        int nameIndex = needsNumber ? 2 : 1;
        GameMap m;
        if (args.length > nameIndex) {
            m = maps.get(args[nameIndex]);
            if (m == null) {
                p.sendMessage(Util.PREFIX + ChatColor.RED + "Keine Map mit dem Namen '" + args[nameIndex] + "'.");
                return;
            }
        } else {
            List<GameMap> here = maps.mapsIn(p.getWorld());
            if (here.isEmpty()) {
                p.sendMessage(Util.PREFIX + ChatColor.RED + "In dieser Welt gibt es keine Map. Lege eine an mit /mm map create <name>.");
                return;
            }
            if (here.size() > 1) {
                p.sendMessage(Util.PREFIX + ChatColor.RED + "In dieser Welt liegen mehrere Maps – bitte den Namen angeben: /mm " + sub
                        + (needsNumber ? " <nr>" : "") + " <map>");
                return;
            }
            m = here.get(0);
        }
        boolean spawn = sub.contains("spawn");
        List<String> list = spawn ? m.spawns : m.gold;
        String what = spawn ? "Spieler-Spawn" : "Gold-Spawn";
        String mapInfo = ChatColor.DARK_GRAY + " (Map " + m.key + ")";
        switch (sub) {
            case "addspawn", "addgold" -> {
                if (!p.getWorld().getName().equals(m.world)) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Du stehst nicht in der Welt der Map '" + m.key + "' (" + m.world + ").");
                    return;
                }
                maps.add(list, p.getLocation());
                p.sendMessage(Util.PREFIX + (spawn ? ChatColor.GREEN : ChatColor.GOLD) + what + " #" + list.size() + " hinzugefügt." + mapInfo);
                if (spawn && list.size() == 1) game.refreshLobby(); // Map ist jetzt spielbar
            }
            case "spawns", "golds" -> listPoints(p, what + "s" + mapInfo, list);
            case "removespawn", "removegold" -> removePoint(p, args, list, what);
            case "tpspawn", "tpgold" -> teleportToPoint(p, args, list);
            case "clearspawns", "cleargold" -> {
                maps.clear(list);
                p.sendMessage(Util.PREFIX + "Alle " + what + "s gelöscht." + mapInfo);
            }
            default -> {
            }
        }
    }

    public void openStats(Player p, String name) {
        if (!stats.isEnabled()) {
            p.sendMessage(Util.PREFIX + ChatColor.RED + "Statistiken sind deaktiviert (keine Datenbank verbunden).");
            return;
        }
        Consumer<StatsManager.PlayerStats> show = st -> {
            if (!p.isOnline()) return;
            if (st == null && name == null) {
                st = new StatsManager.PlayerStats(p.getUniqueId(), p.getName(), 0, 0, 0, 0, Map.of());
            }
            if (st == null) {
                p.sendMessage(Util.PREFIX + ChatColor.RED + "Für " + name + " gibt es noch keine Stats.");
                return;
            }
            p.openInventory(new StatsMenu(st, settings).getInventory());
        };
        if (name == null) stats.load(p.getUniqueId(), show);
        else stats.loadByName(name, show);
    }

    private void listPoints(Player p, String title, List<String> raw) {
        p.sendMessage(Util.PREFIX + ChatColor.YELLOW + title + " (" + raw.size() + "):");
        for (int i = 0; i < raw.size(); i++) {
            Location l = Util.deserialize(raw.get(i));
            String where = l == null ? ChatColor.RED + raw.get(i).split(";")[0] + " (Welt nicht geladen)"
                    : ChatColor.WHITE + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
            p.sendMessage(ChatColor.GRAY + " #" + (i + 1) + " " + where);
        }
    }

    private void removePoint(Player p, String[] args, List<String> raw, String what) {
        Integer nr = number(p, args);
        if (nr == null) return;
        if (maps.remove(raw, nr)) {
            p.sendMessage(Util.PREFIX + what + " #" + nr + " gelöscht. (Die Nummern danach rücken auf.)");
        } else {
            p.sendMessage(Util.PREFIX + ChatColor.RED + "Es gibt keinen " + what + " #" + nr + ".");
        }
    }

    private void teleportToPoint(Player p, String[] args, List<String> raw) {
        Integer nr = number(p, args);
        if (nr == null) return;
        Location l = nr >= 1 && nr <= raw.size() ? Util.deserialize(raw.get(nr - 1)) : null;
        if (l == null) {
            p.sendMessage(Util.PREFIX + ChatColor.RED + "Punkt #" + nr + " gibt es nicht (oder seine Welt ist nicht geladen).");
            return;
        }
        p.teleport(l);
    }

    private Integer number(Player p, String[] args) {
        try {
            return Integer.parseInt(args[1]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            p.sendMessage(Util.PREFIX + ChatColor.RED + "Bitte eine Nummer angeben, z. B. /mm " + args[0] + " 3");
            return null;
        }
    }

    private void sendHelp(Player p, boolean admin) {
        p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Murder Mystery");
        p.sendMessage(ChatColor.YELLOW + "/mm join" + ChatColor.GRAY + " – Spiel beitreten");
        p.sendMessage(ChatColor.YELLOW + "/mm leave" + ChatColor.GRAY + " – Spiel verlassen");
        p.sendMessage(ChatColor.YELLOW + "/mm vote" + ChatColor.GRAY + " – Für die nächste Map abstimmen");
        p.sendMessage(ChatColor.YELLOW + "/stats [Spieler]" + ChatColor.GRAY + " – Statistiken anzeigen");
        if (!admin) return;
        p.sendMessage(ChatColor.YELLOW + "/mm setlobby | tplobby [map]" + ChatColor.GRAY + " – globale Lobby setzen / zu einer Lobby teleportieren");
        p.sendMessage(ChatColor.YELLOW + "/mm maps" + ChatColor.GRAY + " – Map-Menü (Maps verwalten, importieren, Modus)");
        p.sendMessage(ChatColor.YELLOW + "/mm map ..." + ChatColor.GRAY + " – Maps per Befehl verwalten (/mm map help)");
        p.sendMessage(ChatColor.YELLOW + "/mm addspawn | addgold" + ChatColor.GRAY + " – Spieler-/Gold-Spawn in der Map, in der du stehst");
        p.sendMessage(ChatColor.YELLOW + "/mm spawns | tpspawn <nr> | removespawn <nr>" + ChatColor.GRAY + " – Spawns verwalten");
        p.sendMessage(ChatColor.YELLOW + "/mm golds | tpgold <nr> | removegold <nr>" + ChatColor.GRAY + " – Gold-Spawns verwalten");
        p.sendMessage(ChatColor.YELLOW + "/mm clearspawns | cleargold" + ChatColor.GRAY + " – alle Spawns der Map löschen");
        p.sendMessage(ChatColor.YELLOW + "/mm start | stop" + ChatColor.GRAY + " – Runde erzwingen / abbrechen");
        p.sendMessage(ChatColor.YELLOW + "/mm info | reload" + ChatColor.GRAY + " – Status / Config neu laden");
    }

    private void sendMapHelp(Player p) {
        p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Maps");
        p.sendMessage(ChatColor.YELLOW + "/mm map list" + ChatColor.GRAY + " – alle Maps mit Status");
        p.sendMessage(ChatColor.YELLOW + "/mm map create <name> [weltordner]" + ChatColor.GRAY + " – Map anlegen (ohne Weltordner: aktuelle Welt)");
        p.sendMessage(ChatColor.YELLOW + "/mm maps" + ChatColor.GRAY + " – Menü zum Verwalten aller Maps");
        p.sendMessage(ChatColor.YELLOW + "/mm map import" + ChatColor.GRAY + " – neue Welt-Ordner/ZIPs aus plugins/MurderMystery/arenas/ einrichten");
        p.sendMessage(ChatColor.YELLOW + "/mm map testmap" + ChatColor.GRAY + " – fertige Test-Map (Flachwelt mit Arena und Lobby) erzeugen");
        p.sendMessage(ChatColor.YELLOW + "/mm map rename | description | author <name> <text>" + ChatColor.GRAY + " – Anzeigename, Beschreibung, Erbauer");
        p.sendMessage(ChatColor.YELLOW + "/mm map area <name> corner1 | corner2 | on | off" + ChatColor.GRAY + " – Spielbereich (Größe) der Map");
        p.sendMessage(ChatColor.YELLOW + "/mm map reinstall <name>" + ChatColor.GRAY + " – geänderten Welt-Ordner aus arenas/ übernehmen");
        p.sendMessage(ChatColor.YELLOW + "/mm map export <name> [ordner]" + ChatColor.GRAY + " – Welt der Map als Ordner in arenas/ ablegen");
        p.sendMessage(ChatColor.YELLOW + "/mm map tp <name>" + ChatColor.GRAY + " – zur Map teleportieren");
        p.sendMessage(ChatColor.YELLOW + "/mm map enable | disable <name>" + ChatColor.GRAY + " – Map an-/ausschalten");
        p.sendMessage(ChatColor.YELLOW + "/mm map setlobby [name]" + ChatColor.GRAY + " – eigene Wartelobby für diese Map setzen");
        p.sendMessage(ChatColor.YELLOW + "/mm map clearlobby <name>" + ChatColor.GRAY + " – Map nutzt wieder die globale Lobby");
        p.sendMessage(ChatColor.YELLOW + "/mm map next <name>" + ChatColor.GRAY + " – nächste Runde auf dieser Map spielen");
        p.sendMessage(ChatColor.YELLOW + "/mm map delete <name>" + ChatColor.GRAY + " – Map entfernen (Welt bleibt)");
    }
}
