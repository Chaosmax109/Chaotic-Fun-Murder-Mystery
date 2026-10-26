package de.murdermystery;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Lobby-, Spieler- und Gold-Spawnpunkte, gespeichert in der config.yml.
 * Die Punkte werden als Text gehalten und erst bei Bedarf in Locations umgewandelt –
 * so gehen sie nicht verloren, wenn eine Welt (z. B. von Multiverse) erst nach dem Plugin geladen wird.
 */
public class Arena {

    private final MurderMysteryPlugin plugin;
    private String lobby = "";
    private final List<String> spawns = new ArrayList<>();
    private final List<String> goldSpawns = new ArrayList<>();

    public Arena(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();
        lobby = c.getString("arena.lobby", "");
        spawns.clear();
        spawns.addAll(c.getStringList("arena.spawns"));
        goldSpawns.clear();
        goldSpawns.addAll(c.getStringList("arena.gold"));
    }

    public void save() {
        FileConfiguration c = plugin.getConfig();
        c.set("arena.lobby", lobby);
        c.set("arena.spawns", new ArrayList<>(spawns));
        c.set("arena.gold", new ArrayList<>(goldSpawns));
        plugin.saveConfig();
    }

    public boolean isReady() {
        return getLobby() != null && !getSpawns().isEmpty();
    }

    public Location getLobby() {
        return Util.deserialize(lobby);
    }

    public void setLobby(Location l) {
        lobby = Util.serialize(l);
        save();
    }

    /** Alle Spieler-Spawns, deren Welt geladen ist. */
    public List<Location> getSpawns() {
        return resolve(spawns);
    }

    /** Alle Gold-Spawns, deren Welt geladen ist. */
    public List<Location> getGoldSpawns() {
        return resolve(goldSpawns);
    }

    public List<String> rawSpawns() {
        return spawns;
    }

    public List<String> rawGoldSpawns() {
        return goldSpawns;
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

    private static List<Location> resolve(List<String> raw) {
        List<Location> out = new ArrayList<>();
        for (String s : raw) {
            Location l = Util.deserialize(s);
            if (l != null) out.add(l);
        }
        return out;
    }
}
