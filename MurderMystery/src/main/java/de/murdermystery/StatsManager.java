package de.murdermystery;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Statistiken in einer MySQL-/MariaDB-Datenbank. Ohne Verbindung ist alles deaktiviert.
 * Alle Datenbankzugriffe laufen in einem eigenen Thread, damit der Server nicht laggt.
 */
public class StatsManager {

    public record PlayerStats(UUID id, String name, int kills, int deaths, int wins, int losses,
                              Map<String, Integer> roleKills) {

        public int games() {
            return wins + losses;
        }

        /** Gewinnrate in Prozent (0–100). */
        public double winrate() {
            return games() == 0 ? 0 : wins * 100.0 / games();
        }
    }

    private final MurderMysteryPlugin plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private String players;
    private String roleKills;

    public StatsManager(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return dataSource != null;
    }

    /** Baut die Verbindung anhand der database.yml auf. Schlägt sie fehl, bleiben die Stats deaktiviert. */
    public void connect(FileConfiguration db) {
        close();
        if (!db.getBoolean("enabled", false)) {
            plugin.getLogger().info("Stats sind deaktiviert (database.yml: enabled: false).");
            return;
        }
        String type = db.getString("type", "mysql").toLowerCase();
        String prefix = db.getString("table-prefix", "mm_").replaceAll("[^A-Za-z0-9_]", "");
        players = prefix + "players";
        roleKills = prefix + "role_kills";
        boolean ssl = db.getBoolean("use-ssl", false);

        HikariConfig cfg = new HikariConfig();
        String base = "//" + db.getString("host", "localhost") + ":" + db.getInt("port", 3306) + "/" + db.getString("database", "minecraft");
        if (type.equals("mariadb")) {
            cfg.setDriverClassName("org.mariadb.jdbc.Driver");
            cfg.setJdbcUrl("jdbc:mariadb:" + base + "?useSsl=" + ssl);
        } else {
            cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
            cfg.setJdbcUrl("jdbc:mysql:" + base + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        }
        cfg.setUsername(db.getString("username", "root"));
        cfg.setPassword(db.getString("password", ""));
        cfg.setMaximumPoolSize(Math.max(1, db.getInt("pool-size", 4)));
        cfg.setPoolName("MurderMystery-Stats");
        cfg.setConnectionTimeout(5000);

        try {
            dataSource = new HikariDataSource(cfg);
            createTables();
            executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "MurderMystery-Stats"));
            plugin.getLogger().info("Mit der Datenbank verbunden – Stats sind aktiv.");
        } catch (Exception e) {
            plugin.getLogger().warning("Keine Verbindung zur Datenbank – Stats sind deaktiviert. Grund: " + e.getMessage());
            close();
        }
    }

    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS); // ausstehende Einträge noch schreiben
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void createTables() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + players + " ("
                    + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                    + "name VARCHAR(16) NOT NULL,"
                    + "kills INT NOT NULL DEFAULT 0,"
                    + "deaths INT NOT NULL DEFAULT 0,"
                    + "wins INT NOT NULL DEFAULT 0,"
                    + "losses INT NOT NULL DEFAULT 0,"
                    + "INDEX idx_name (name))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + roleKills + " ("
                    + "uuid CHAR(36) NOT NULL,"
                    + "role VARCHAR(32) NOT NULL,"
                    + "kills INT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (uuid, role))");
        }
    }

    // ------------------------------------------------------------------ Schreiben

    public void addKill(UUID id, String name, String roleKey) {
        increment(id, name, "kills");
        run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + roleKills + " (uuid, role, kills) VALUES (?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE kills = kills + 1")) {
                ps.setString(1, id.toString());
                ps.setString(2, roleKey);
                ps.executeUpdate();
            }
        });
    }

    public void addDeath(UUID id, String name) {
        increment(id, name, "deaths");
    }

    public void addGame(UUID id, String name, boolean won) {
        increment(id, name, won ? "wins" : "losses");
    }

    private void increment(UUID id, String name, String column) {
        run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + players + " (uuid, name, " + column + ") VALUES (?, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE name = VALUES(name), " + column + " = " + column + " + 1")) {
                ps.setString(1, id.toString());
                ps.setString(2, name);
                ps.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------------ Lesen

    /** Lädt die Stats eines Spielers (per UUID) und gibt sie auf dem Server-Thread zurück (null = keine Stats). */
    public void load(UUID id, Consumer<PlayerStats> callback) {
        query("uuid = ?", id.toString(), callback);
    }

    /** Wie {@link #load(UUID, Consumer)}, aber über den zuletzt bekannten Namen. */
    public void loadByName(String name, Consumer<PlayerStats> callback) {
        query("name = ?", name, callback);
    }

    private void query(String where, String value, Consumer<PlayerStats> callback) {
        run(c -> {
            PlayerStats result = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, kills, deaths, wins, losses FROM " + players + " WHERE " + where + " LIMIT 1")) {
                ps.setString(1, value);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String uuid = rs.getString("uuid");
                        Map<String, Integer> perRole = new LinkedHashMap<>();
                        try (PreparedStatement rk = c.prepareStatement("SELECT role, kills FROM " + roleKills + " WHERE uuid = ?")) {
                            rk.setString(1, uuid);
                            try (ResultSet r = rk.executeQuery()) {
                                while (r.next()) perRole.put(r.getString("role"), r.getInt("kills"));
                            }
                        }
                        result = new PlayerStats(UUID.fromString(uuid), rs.getString("name"), rs.getInt("kills"),
                                rs.getInt("deaths"), rs.getInt("wins"), rs.getInt("losses"), perRole);
                    }
                }
            }
            PlayerStats finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        });
    }

    // ------------------------------------------------------------------ Helfer

    private interface SqlTask {
        void run(Connection c) throws SQLException;
    }

    private void run(SqlTask task) {
        if (dataSource == null || executor == null) return;
        HikariDataSource ds = dataSource;
        executor.execute(() -> {
            try (Connection c = ds.getConnection()) {
                task.run(c);
            } catch (SQLException e) {
                plugin.getLogger().warning("Datenbankfehler: " + e.getMessage());
            }
        });
    }
}
