package de.murdermystery;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MurderMysteryPlugin extends JavaPlugin {

    private Settings settings;
    private StatsManager stats;
    private Arena arena;
    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stats = new StatsManager(this);
        loadSettings();
        arena = new Arena(this);
        arena.load();
        game = new Game(this, arena);
        game.startTasks();
        getServer().getPluginManager().registerEvents(new GameListener(this, game), this);

        // Welten von Multiverse & Co. werden evtl. erst nach dem Plugin geladen – daher einen Tick warten
        Bukkit.getScheduler().runTask(this, () -> {
            if (!arena.isReady()) {
                getLogger().warning("Arena ist noch nicht eingerichtet! Nutze /mm setlobby und /mm addspawn.");
            } else if (settings.autoJoin) {
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
        File dbFile = file("database.yml");
        // roles.yml ohne Standardwerte laden: sonst kämen gelöschte Beispielrollen bei jedem Reload zurück
        YamlConfiguration roles = YamlConfiguration.loadConfiguration(rolesFile);
        YamlConfiguration gold = loadWithDefaults(goldFile, true);
        YamlConfiguration db = loadWithDefaults(dbFile, true);

        if (Settings.migrate(getConfig(), roles, gold)) {
            getLogger().info("Einstellungen wurden ins aktuelle Format umgestellt (roles.yml / gold.yml).");
        }
        // Neue Einstellungen aus Updates in die vorhandenen Dateien übernehmen
        getConfig().options().copyDefaults(true);
        saveConfig();
        save(roles, rolesFile);
        save(gold, goldFile);
        save(db, dbFile);
        settings = new Settings(getConfig(), roles, gold, getLogger());
        stats.connect(db);
    }

    private File file(String name) {
        File f = new File(getDataFolder(), name);
        if (!f.exists()) saveResource(name, false);
        return f;
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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
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
                arena.setLobby(loc);
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Lobby gesetzt. Sie darf auch in derselben Welt wie die Map liegen.");
            }
            case "tplobby" -> {
                Location lobby = arena.getLobby();
                if (lobby == null) p.sendMessage(Util.PREFIX + ChatColor.RED + "Keine Lobby gesetzt (oder ihre Welt ist nicht geladen).");
                else p.teleport(lobby);
            }
            case "addspawn" -> {
                arena.add(arena.rawSpawns(), loc);
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Spieler-Spawn #" + arena.rawSpawns().size() + " hinzugefügt.");
            }
            case "addgold" -> {
                arena.add(arena.rawGoldSpawns(), loc);
                p.sendMessage(Util.PREFIX + ChatColor.GOLD + "Gold-Spawn #" + arena.rawGoldSpawns().size() + " hinzugefügt.");
            }
            case "spawns" -> listPoints(p, "Spieler-Spawns", arena.rawSpawns());
            case "golds" -> listPoints(p, "Gold-Spawns", arena.rawGoldSpawns());
            case "removespawn" -> removePoint(p, args, arena.rawSpawns(), "Spieler-Spawn");
            case "removegold" -> removePoint(p, args, arena.rawGoldSpawns(), "Gold-Spawn");
            case "tpspawn" -> teleportToPoint(p, args, arena.rawSpawns());
            case "tpgold" -> teleportToPoint(p, args, arena.rawGoldSpawns());
            case "clearspawns" -> {
                arena.clear(arena.rawSpawns());
                p.sendMessage(Util.PREFIX + "Alle Spieler-Spawns gelöscht.");
            }
            case "cleargold" -> {
                arena.clear(arena.rawGoldSpawns());
                p.sendMessage(Util.PREFIX + "Alle Gold-Spawns gelöscht.");
            }
            case "start" -> game.forceStart(p);
            case "stop" -> game.forceStop();
            case "reload" -> {
                loadSettings();
                arena.load();
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "config.yml, roles.yml, gold.yml und database.yml neu geladen. Stats: "
                        + (stats.isEnabled() ? ChatColor.GREEN + "aktiv" : ChatColor.RED + "deaktiviert"));
            }
            case "info" -> {
                p.sendMessage(Util.PREFIX + "Lobby: " + (arena.getLobby() != null ? ChatColor.GREEN + "gesetzt" : ChatColor.RED + "fehlt")
                        + ChatColor.GRAY + ", Spawns: " + ChatColor.YELLOW + arena.rawSpawns().size()
                        + ChatColor.GRAY + ", Gold-Spawns: " + ChatColor.GOLD + arena.rawGoldSpawns().size()
                        + ChatColor.GRAY + ", Status: " + ChatColor.YELLOW + game.getState());
                if (arena.rawSpawns().size() < settings.maxPlayers) {
                    p.sendMessage(Util.PREFIX + ChatColor.YELLOW + "Tipp: Setze " + settings.maxPlayers
                            + " Spieler-Spawns, damit jeder Spieler (max-players) einen eigenen bekommt.");
                }
            }
            default -> sendHelp(p, true);
        }
        return true;
    }

    private void openStats(Player p, String name) {
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
        if (arena.remove(raw, nr)) {
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
        p.sendMessage(ChatColor.YELLOW + "/stats [Spieler]" + ChatColor.GRAY + " – Statistiken anzeigen");
        if (!admin) return;
        p.sendMessage(ChatColor.YELLOW + "/mm setlobby | tplobby" + ChatColor.GRAY + " – Wartelobby setzen / hinteleportieren");
        p.sendMessage(ChatColor.YELLOW + "/mm addspawn" + ChatColor.GRAY + " – Spieler-Spawn hinzufügen (einer pro Spieler)");
        p.sendMessage(ChatColor.YELLOW + "/mm spawns | tpspawn <nr> | removespawn <nr>" + ChatColor.GRAY + " – Spawns verwalten");
        p.sendMessage(ChatColor.YELLOW + "/mm addgold" + ChatColor.GRAY + " – Gold-Spawn hinzufügen");
        p.sendMessage(ChatColor.YELLOW + "/mm golds | tpgold <nr> | removegold <nr>" + ChatColor.GRAY + " – Gold-Spawns verwalten");
        p.sendMessage(ChatColor.YELLOW + "/mm clearspawns | cleargold" + ChatColor.GRAY + " – alle Spawns löschen");
        p.sendMessage(ChatColor.YELLOW + "/mm start | stop" + ChatColor.GRAY + " – Runde erzwingen / abbrechen");
        p.sendMessage(ChatColor.YELLOW + "/mm info | reload" + ChatColor.GRAY + " – Status / Config neu laden");
    }
}
