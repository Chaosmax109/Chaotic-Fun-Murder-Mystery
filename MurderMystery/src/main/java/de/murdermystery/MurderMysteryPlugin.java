package de.murdermystery;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MurderMysteryPlugin extends JavaPlugin {

    private Arena arena;
    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arena = new Arena(this);
        arena.load();
        game = new Game(this, arena);
        game.startTasks();
        getServer().getPluginManager().registerEvents(new GameListener(this, game), this);

        if (!arena.isReady()) {
            getLogger().warning("Arena ist noch nicht eingerichtet! Nutze /mm setlobby und /mm addspawn.");
        }
        // Spieler, die bei /reload schon online sind
        if (getConfig().getBoolean("auto-join", true) && arena.isReady()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                game.join(p);
            }
        }
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        Player p = (Player) sender;
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";

        switch (sub) {
            case "join":
                game.join(p);
                return true;
            case "leave":
                game.leave(p, false);
                return true;
            default:
                break;
        }

        if (!p.hasPermission("mm.admin")) {
            sendHelp(p, false);
            return true;
        }

        Location loc = p.getLocation();
        switch (sub) {
            case "setlobby":
                arena.setLobby(loc);
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Lobby gesetzt.");
                break;
            case "addspawn":
                arena.getSpawns().add(loc);
                arena.save();
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Spieler-Spawn #" + arena.getSpawns().size() + " hinzugefügt.");
                break;
            case "addgold":
                arena.getGoldSpawns().add(loc);
                arena.save();
                p.sendMessage(Util.PREFIX + ChatColor.GOLD + "Gold-Spawn #" + arena.getGoldSpawns().size() + " hinzugefügt.");
                break;
            case "clearspawns":
                arena.getSpawns().clear();
                arena.save();
                p.sendMessage(Util.PREFIX + "Alle Spieler-Spawns gelöscht.");
                break;
            case "cleargold":
                arena.getGoldSpawns().clear();
                arena.save();
                p.sendMessage(Util.PREFIX + "Alle Gold-Spawns gelöscht.");
                break;
            case "start":
                game.forceStart(p);
                break;
            case "stop":
                game.forceStop();
                break;
            case "reload":
                reloadConfig();
                arena.load();
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Config neu geladen.");
                break;
            case "info":
                p.sendMessage(Util.PREFIX + "Lobby: " + (arena.getLobby() != null ? ChatColor.GREEN + "gesetzt" : ChatColor.RED + "fehlt")
                        + ChatColor.GRAY + ", Spawns: " + ChatColor.YELLOW + arena.getSpawns().size()
                        + ChatColor.GRAY + ", Gold-Spawns: " + ChatColor.GOLD + arena.getGoldSpawns().size()
                        + ChatColor.GRAY + ", Status: " + ChatColor.YELLOW + game.getState());
                break;
            default:
                sendHelp(p, true);
        }
        return true;
    }

    private void sendHelp(Player p, boolean admin) {
        p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Murder Mystery");
        p.sendMessage(ChatColor.YELLOW + "/mm join" + ChatColor.GRAY + " – Spiel beitreten");
        p.sendMessage(ChatColor.YELLOW + "/mm leave" + ChatColor.GRAY + " – Spiel verlassen");
        if (!admin) return;
        p.sendMessage(ChatColor.YELLOW + "/mm setlobby" + ChatColor.GRAY + " – Wartelobby setzen");
        p.sendMessage(ChatColor.YELLOW + "/mm addspawn" + ChatColor.GRAY + " – Spieler-Spawn auf der Map hinzufügen");
        p.sendMessage(ChatColor.YELLOW + "/mm addgold" + ChatColor.GRAY + " – Gold-Spawn hinzufügen");
        p.sendMessage(ChatColor.YELLOW + "/mm clearspawns | cleargold" + ChatColor.GRAY + " – Spawns löschen");
        p.sendMessage(ChatColor.YELLOW + "/mm start | stop" + ChatColor.GRAY + " – Runde erzwingen / abbrechen");
        p.sendMessage(ChatColor.YELLOW + "/mm info | reload" + ChatColor.GRAY + " – Status / Config neu laden");
    }
}
