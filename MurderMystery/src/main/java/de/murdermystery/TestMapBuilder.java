package de.murdermystery;

import de.murdermystery.MapManager.GameMap;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * Erzeugt eine fertige Test-Map zum Ausprobieren: eine Flachwelt mit einer ummauerten Arena
 * (Spieler- und Gold-Spawns bereits gesetzt) und daneben einer Glas-Lobby.
 */
public final class TestMapBuilder {

    public static final String WORLD = "mm_testmap";
    public static final String KEY = "testmap";

    private static final int ARENA_RADIUS = 20;   // Arena: 41 x 41 Blöcke um (0, 0)
    private static final int WALL_HEIGHT = 4;
    private static final int LOBBY_X = 80;        // Lobby: 11 x 11 Blöcke um (80, 0), weit genug von der Arena entfernt
    private static final int LOBBY_RADIUS = 5;

    private TestMapBuilder() {
    }

    /** Baut Welt, Arena und Lobby und trägt die Map ein. Gibt die fertige Map zurück. */
    public static GameMap build(MapManager maps) {
        World w = Bukkit.getWorld(WORLD);
        if (w == null) {
            w = Bukkit.createWorld(new WorldCreator(WORLD).type(WorldType.FLAT).generateStructures(false));
        }
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setDifficulty(Difficulty.PEACEFUL);

        int y = w.getHighestBlockYAt(0, 0) + 1; // erste freie Höhe über dem Boden
        buildArena(w, y);
        buildLobby(w, y);

        GameMap m = maps.get(KEY);
        if (m == null) {
            m = maps.create(KEY, w);
        }
        m.displayName = Util.color("&aTest-Map");
        m.icon = Material.GRASS_BLOCK;
        m.enabled = true;
        m.spawns.clear();
        m.gold.clear();

        // 16 Spieler-Spawns im Kreis, Blick zur Mitte
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            double x = Math.cos(angle) * 14 + 0.5;
            double z = Math.sin(angle) * 14 + 0.5;
            Location l = new Location(w, x, y, z);
            l.setDirection(new Location(w, 0.5, y, 0.5).toVector().subtract(l.toVector()));
            m.spawns.add(Util.serialize(l));
        }
        // 12 Gold-Spawns verteilt über die Arena
        int[][] gold = {{-16, -16}, {16, -16}, {-16, 16}, {16, 16}, {0, -10}, {0, 10},
                {-10, 0}, {10, 0}, {-6, -6}, {6, 6}, {-6, 6}, {6, -6}};
        for (int[] g : gold) {
            m.gold.add(Util.serialize(new Location(w, g[0] + 0.5, y, g[1] + 0.5)));
        }

        Location lobby = new Location(w, LOBBY_X + 0.5, y, 0.5, 90f, 0f);
        m.lobby = Util.serialize(lobby);
        maps.save();
        if (maps.getLobby() == null) {
            maps.setLobby(lobby);
        }
        return m;
    }

    /** Ummauerte Arena mit ein paar Räumen, Säulen und Deckungen zum Verstecken. */
    private static void buildArena(World w, int y) {
        // Luft freiräumen und Boden aus Holz
        fill(w, -ARENA_RADIUS, y, -ARENA_RADIUS, ARENA_RADIUS, y + WALL_HEIGHT + 2, ARENA_RADIUS, Material.AIR);
        fill(w, -ARENA_RADIUS, y - 1, -ARENA_RADIUS, ARENA_RADIUS, y - 1, ARENA_RADIUS, Material.OAK_PLANKS);
        // Außenmauer
        for (int h = 0; h < WALL_HEIGHT; h++) {
            Material mat = h == WALL_HEIGHT - 1 ? Material.STONE_BRICK_SLAB : Material.STONE_BRICKS;
            fill(w, -ARENA_RADIUS, y + h, -ARENA_RADIUS, ARENA_RADIUS, y + h, -ARENA_RADIUS, mat);
            fill(w, -ARENA_RADIUS, y + h, ARENA_RADIUS, ARENA_RADIUS, y + h, ARENA_RADIUS, mat);
            fill(w, -ARENA_RADIUS, y + h, -ARENA_RADIUS, -ARENA_RADIUS, y + h, ARENA_RADIUS, mat);
            fill(w, ARENA_RADIUS, y + h, -ARENA_RADIUS, ARENA_RADIUS, y + h, ARENA_RADIUS, mat);
        }
        // Vier kleine Räume in den Ecken (Mauern mit Durchgang)
        int[][] rooms = {{-18, -18}, {11, -18}, {-18, 11}, {11, 11}};
        for (int[] r : rooms) {
            int x1 = r[0], z1 = r[1], x2 = r[0] + 7, z2 = r[1] + 7;
            for (int h = 0; h < 3; h++) {
                fill(w, x1, y + h, z1, x2, y + h, z1, Material.SPRUCE_PLANKS);
                fill(w, x1, y + h, z2, x2, y + h, z2, Material.SPRUCE_PLANKS);
                fill(w, x1, y + h, z1, x1, y + h, z2, Material.SPRUCE_PLANKS);
                fill(w, x2, y + h, z1, x2, y + h, z2, Material.SPRUCE_PLANKS);
            }
            // Durchgänge zur Mitte hin
            int doorX = r[0] < 0 ? x2 : x1;
            int doorZ = r[1] < 0 ? z2 : z1;
            fill(w, doorX, y, z1 + 3, doorX, y + 1, z1 + 4, Material.AIR);
            fill(w, x1 + 3, y, doorZ, x1 + 4, y + 1, doorZ, Material.AIR);
            w.getBlockAt(x1 + 2, y, z1 + 2).setType(Material.BOOKSHELF);
            w.getBlockAt(x2 - 2, y, z2 - 2).setType(Material.CRAFTING_TABLE);
        }
        // Säulen und niedrige Deckungen in der Mitte
        int[][] pillars = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        for (int[] p : pillars) {
            fill(w, p[0], y, p[1], p[0], y + 2, p[1], Material.OAK_LOG);
            w.getBlockAt(p[0], y + 3, p[1]).setType(Material.LANTERN);
        }
        fill(w, -2, y, -9, 2, y, -9, Material.HAY_BLOCK);
        fill(w, -2, y, 9, 2, y, 9, Material.HAY_BLOCK);
        fill(w, -9, y, -2, -9, y, 2, Material.BARREL);
        fill(w, 9, y, -2, 9, y, 2, Material.BARREL);
    }

    /** Glas-Box als Wartelobby. */
    private static void buildLobby(World w, int y) {
        int x1 = LOBBY_X - LOBBY_RADIUS, x2 = LOBBY_X + LOBBY_RADIUS;
        int z1 = -LOBBY_RADIUS, z2 = LOBBY_RADIUS;
        fill(w, x1, y, z1, x2, y + 4, z2, Material.AIR);
        fill(w, x1, y - 1, z1, x2, y - 1, z2, Material.QUARTZ_BLOCK);
        for (int h = 0; h < 4; h++) {
            fill(w, x1, y + h, z1, x2, y + h, z1, Material.GLASS);
            fill(w, x1, y + h, z2, x2, y + h, z2, Material.GLASS);
            fill(w, x1, y + h, z1, x1, y + h, z2, Material.GLASS);
            fill(w, x2, y + h, z1, x2, y + h, z2, Material.GLASS);
        }
        fill(w, x1, y + 4, z1, x2, y + 4, z2, Material.GLASS);
        w.getBlockAt(LOBBY_X, y - 1, 0).setType(Material.GOLD_BLOCK);
    }

    private static void fill(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material mat) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    w.getBlockAt(x, y, z).setType(mat, false);
                }
            }
        }
    }
}
