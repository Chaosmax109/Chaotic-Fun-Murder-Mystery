package de.murdermystery;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Lobby-, Spieler- und Gold-Spawnpunkte, gespeichert in der config.yml. */
public class Arena {

    private final MurderMysteryPlugin plugin;
    private Location lobby;
    private final List<Location> spawns = new ArrayList<>();
    private final List<Location> goldSpawns = new ArrayList<>();

    public Arena(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();
        lobby = Util.deserialize(c.getString("arena.lobby"));
        spawns.clear();
        goldSpawns.clear();
        for (String s : c.getStringList("arena.spawns")) {
            Location l = Util.deserialize(s);
            if (l != null) {
                spawns.add(l);
            }
        }
        for (String s : c.getStringList("arena.gold")) {
            Location l = Util.deserialize(s);
            if (l != null) {
                goldSpawns.add(l);
            }
        }
    }

    public void save() {
        FileConfiguration c = plugin.getConfig();
        c.set("arena.lobby", lobby == null ? "" : Util.serialize(lobby));
        List<String> s = new ArrayList<>();
        for (Location l : spawns) {
            s.add(Util.serialize(l));
        }
        c.set("arena.spawns", s);
        List<String> g = new ArrayList<>();
        for (Location l : goldSpawns) {
            g.add(Util.serialize(l));
        }
        c.set("arena.gold", g);
        plugin.saveConfig();
    }

    public boolean isReady() {
        return lobby != null && !spawns.isEmpty();
    }

    public Location getLobby() {
        return lobby;
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby;
        save();
    }

    public List<Location> getSpawns() {
        return spawns;
    }

    public List<Location> getGoldSpawns() {
        return goldSpawns;
    }
}
