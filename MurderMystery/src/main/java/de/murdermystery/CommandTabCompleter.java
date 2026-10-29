package de.murdermystery;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/** Tab-Vervollständigung für /mm und /stats. Admin-Befehle werden nur mit mm.admin vorgeschlagen. */
public class CommandTabCompleter implements TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("join", "leave", "vote", "stats", "help");
    private static final List<String> ADMIN_SUBS = List.of("maps", "map", "setlobby", "tplobby", "addspawn", "addgold",
            "spawns", "golds", "removespawn", "removegold", "tpspawn", "tpgold", "clearspawns", "cleargold",
            "start", "stop", "info", "reload");
    private static final List<String> MAP_ACTIONS = List.of("list", "gui", "create", "import", "testmap", "edit", "tp",
            "enable", "disable", "next", "setlobby", "clearlobby", "rename", "description", "author", "delete", "help");
    /** Map-Aktionen, die als drittes Argument einen Map-Namen erwarten. */
    private static final List<String> MAP_ACTIONS_WITH_NAME = List.of("edit", "tp", "enable", "disable", "next",
            "setlobby", "clearlobby", "rename", "description", "author", "delete");

    private final MurderMysteryPlugin plugin;

    public CommandTabCompleter(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("stats")) {
            return args.length == 1 ? match(args[0], playerNames()) : List.of();
        }
        boolean admin = sender.hasPermission("mm.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (admin) subs.addAll(ADMIN_SUBS);
            return match(args[0], subs);
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("stats") && args.length == 2) return match(args[1], playerNames());
        if (!admin) return List.of();

        if (sub.equals("map")) {
            if (args.length == 2) return match(args[1], MAP_ACTIONS);
            String action = args[1].toLowerCase();
            if (args.length == 3 && MAP_ACTIONS_WITH_NAME.contains(action)) return match(args[2], mapNames());
            if (args.length == 4 && action.equals("create")) return match(args[3], worldNames());
            return List.of();
        }
        switch (sub) {
            case "tplobby", "addspawn", "addgold", "spawns", "golds", "clearspawns", "cleargold" -> {
                return args.length == 2 ? match(args[1], mapNames()) : List.of();
            }
            case "removespawn", "removegold", "tpspawn", "tpgold" -> {
                if (args.length == 2) return match(args[1], numbers(sub, sender));
                return args.length == 3 ? match(args[2], mapNames()) : List.of();
            }
            default -> {
                return List.of();
            }
        }
    }

    /** Nummern der Spawns der Map, in deren Welt der Spieler steht. */
    private List<String> numbers(String sub, CommandSender sender) {
        List<String> list = new ArrayList<>();
        if (!(sender instanceof Player p)) return list;
        List<MapManager.GameMap> here = plugin.maps().mapsIn(p.getWorld());
        if (here.size() != 1) return list;
        int count = sub.contains("spawn") ? here.get(0).spawns.size() : here.get(0).gold.size();
        for (int i = 1; i <= count; i++) list.add(String.valueOf(i));
        return list;
    }

    private List<String> mapNames() {
        return new ArrayList<>(plugin.maps().all().keySet());
    }

    private static List<String> worldNames() {
        List<String> list = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) list.add(w.getName());
        return list;
    }

    private static List<String> playerNames() {
        List<String> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
        return list;
    }

    private static List<String> match(String input, List<String> options) {
        return StringUtil.copyPartialMatches(input, options, new ArrayList<>());
    }
}
