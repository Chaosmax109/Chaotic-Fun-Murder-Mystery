package de.murdermystery;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Speichert die Statistiken aller Spieler in plugins/MurderMystery/stats.yml. */
public class StatsManager {

    public record PlayerStats(UUID id, String name, int kills, int murdererKills, int detectiveKills,
                              int innocentKills, int deaths, int wins, int losses) {

        public int games() {
            return wins + losses;
        }

        /** Gewinnrate in Prozent (0–100). */
        public double winrate() {
            return games() == 0 ? 0 : wins * 100.0 / games();
        }
    }

    private final MurderMysteryPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public StatsManager(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("stats.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public void addKill(UUID id, String name, Game.Role role) {
        increment(id, name, "kills.total");
        switch (role) {
            case MURDERER -> increment(id, name, "kills.murderer");
            case DETECTIVE -> increment(id, name, "kills.detective");
            case INNOCENT -> increment(id, name, "kills.innocent");
        }
    }

    public void addDeath(UUID id, String name) {
        increment(id, name, "deaths");
    }

    public void addGame(UUID id, String name, boolean won) {
        increment(id, name, won ? "wins" : "losses");
    }

    public PlayerStats get(UUID id) {
        ConfigurationSection s = data.getConfigurationSection("players." + id);
        if (s == null) {
            return null;
        }
        return new PlayerStats(id, s.getString("name", "?"),
                s.getInt("kills.total"), s.getInt("kills.murderer"), s.getInt("kills.detective"),
                s.getInt("kills.innocent"), s.getInt("deaths"), s.getInt("wins"), s.getInt("losses"));
    }

    /** Sucht einen Spieler über seinen zuletzt bekannten Namen (Groß-/Kleinschreibung egal). */
    public PlayerStats findByName(String name) {
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return null;
        for (String key : players.getKeys(false)) {
            if (name.equalsIgnoreCase(players.getString(key + ".name"))) {
                try {
                    return get(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private void increment(UUID id, String name, String path) {
        String base = "players." + id + ".";
        data.set(base + "name", name);
        data.set(base + path, data.getInt(base + path) + 1);
    }
}
