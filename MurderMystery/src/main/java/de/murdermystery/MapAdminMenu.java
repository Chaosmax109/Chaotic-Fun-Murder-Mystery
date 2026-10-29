package de.murdermystery;

import de.murdermystery.MapManager.GameMap;
import de.murdermystery.MapManager.Mode;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-Menü zum Verwalten der Maps (/mm maps): Übersicht aller Maps und ein Bearbeiten-Menü pro Map.
 * Alles, was das Menü kann, geht auch per Befehl – das Menü ist nur bequemer.
 */
public class MapAdminMenu implements InventoryHolder {

    // Übersicht
    private static final int OV_TESTMAP = 45, OV_IMPORT = 47, OV_MODE = 49, OV_LOBBY = 51, OV_CLOSE = 53;
    // Bearbeiten
    private static final int ED_NAME = 4, ED_TP = 10, ED_SPAWN = 11, ED_GOLD = 12, ED_STATUS = 13, ED_LOBBY = 14,
            ED_ICON = 15, ED_ENABLED = 16, ED_CLEAR_SPAWNS = 19, ED_CLEAR_GOLD = 20, ED_AREA = 21, ED_NEXT = 22,
            ED_LOBBY_RADIUS = 23, ED_DELETE = 24, ED_BACK = 26;

    private final MurderMysteryPlugin plugin;
    private final MapManager maps;
    private final GameMap editing; // null = Übersicht
    private final Inventory inventory;
    private final Map<Integer, GameMap> mapSlots = new HashMap<>();

    private MapAdminMenu(MurderMysteryPlugin plugin, GameMap editing) {
        this.plugin = plugin;
        this.maps = plugin.maps();
        this.editing = editing;
        this.inventory = editing == null
                ? Bukkit.createInventory(this, 54, Util.text(ChatColor.DARK_GRAY + "Maps verwalten"))
                : Bukkit.createInventory(this, 27, Util.text(ChatColor.DARK_GRAY + "Map: " + ChatColor.DARK_RED + editing.key));
    }

    public static void openOverview(MurderMysteryPlugin plugin, Player p) {
        MapAdminMenu menu = new MapAdminMenu(plugin, null);
        menu.render(p);
        p.openInventory(menu.inventory);
    }

    public static void openEditor(MurderMysteryPlugin plugin, Player p, GameMap m) {
        MapAdminMenu menu = new MapAdminMenu(plugin, m);
        menu.render(p);
        p.openInventory(menu.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------ Anzeige

    private void render(Player p) {
        inventory.clear();
        if (editing == null) renderOverview();
        else renderEditor(p);
    }

    private void renderOverview() {
        mapSlots.clear();
        int slot = 0;
        for (GameMap m : maps.all().values()) {
            if (slot >= 45) break;
            String problem = maps.problem(m);
            List<String> lore = new ArrayList<>();
            lore.add(problem == null ? ChatColor.GREEN + "✔ spielbar" : ChatColor.RED + "✘ " + problem);
            lore.add(ChatColor.GRAY + "Welt: " + ChatColor.WHITE + m.world);
            lore.add(ChatColor.GRAY + "Spieler-Spawns: " + ChatColor.WHITE + m.spawns.size() + ChatColor.GRAY + "  Gold: " + ChatColor.WHITE + m.gold.size());
            lore.add(ChatColor.GRAY + "Lobby: " + ChatColor.WHITE + (m.ownLobby() == null ? "global" : "eigene"));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Klicken zum Bearbeiten");
            inventory.setItem(slot, item(m.icon, ChatColor.YELLOW + m.displayName + ChatColor.DARK_GRAY + " (" + m.key + ")", lore, problem == null));
            mapSlots.put(slot, m);
            slot++;
        }
        int uploads = plugin.importer().pending().size();
        inventory.setItem(OV_TESTMAP, item(Material.GRASS_BLOCK, ChatColor.GREEN + "Test-Map erzeugen",
                List.of(ChatColor.GRAY + "Flachwelt mit Arena, Spawns und Lobby", ChatColor.GRAY + "(baut sie neu, falls es sie schon gibt)"), false));
        inventory.setItem(OV_IMPORT, item(Material.CHEST, ChatColor.GOLD + "Neue Maps aus arenas/ einrichten",
                List.of(ChatColor.GRAY + "Ordner: plugins/MurderMystery/arenas/", ChatColor.GRAY + "Neue Welt-Ordner/ZIPs: " + ChatColor.WHITE + uploads,
                        ChatColor.GRAY + "Welt-Ordner oder ZIP-Dateien hineinlegen"), uploads > 0));
        inventory.setItem(OV_MODE, item(Material.COMPARATOR, ChatColor.AQUA + "Auswahl-Modus: " + ChatColor.WHITE + modeName(maps.mode()),
                List.of(ChatColor.GRAY + "Klicken zum Wechseln:", ChatColor.GRAY + "Abstimmung → Zufall pro Runde → Zufall pro Serverstart"), false));
        inventory.setItem(OV_LOBBY, item(Material.BEACON, ChatColor.LIGHT_PURPLE + "Globale Lobby hier setzen",
                List.of(ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + (maps.getLobby() == null ? "keine" : "gesetzt"),
                        ChatColor.GRAY + "Gilt für alle Maps ohne eigene Lobby"), false));
        inventory.setItem(OV_CLOSE, item(Material.BARRIER, ChatColor.RED + "Schließen", List.of(), false));
    }

    private void renderEditor(Player p) {
        GameMap m = editing;
        String problem = maps.problem(m);
        inventory.setItem(ED_NAME, item(Material.NAME_TAG, ChatColor.YELLOW + m.displayName,
                List.of(ChatColor.GRAY + "Anzeigename ändern:", ChatColor.WHITE + "/mm map rename " + m.key + " <Name>",
                        ChatColor.GRAY + "Beschreibung: " + ChatColor.WHITE + (m.description.isEmpty() ? "–" : m.description),
                        ChatColor.WHITE + "/mm map description " + m.key + " <Text>",
                        ChatColor.GRAY + "Erbauer: " + ChatColor.WHITE + (m.author.isEmpty() ? "–" : m.author),
                        ChatColor.WHITE + "/mm map author " + m.key + " <Name>"), false));
        inventory.setItem(ED_TP, item(Material.ENDER_PEARL, ChatColor.AQUA + "Zur Map teleportieren", List.of(), false));
        boolean inWorld = p.getWorld().getName().equals(m.world);
        String here = inWorld ? ChatColor.GRAY + "an deiner Position" : ChatColor.RED + "Erst in die Map-Welt teleportieren!";
        inventory.setItem(ED_SPAWN, item(Material.ARMOR_STAND, ChatColor.GREEN + "Spieler-Spawn hinzufügen",
                List.of(here, ChatColor.GRAY + "Bisher: " + ChatColor.WHITE + m.spawns.size() + ChatColor.GRAY + " (ideal: " + plugin.settings().maxPlayers + ")"), false));
        inventory.setItem(ED_GOLD, item(Material.GOLD_INGOT, ChatColor.GOLD + "Gold-Spawn hinzufügen",
                List.of(here, ChatColor.GRAY + "Bisher: " + ChatColor.WHITE + m.gold.size()), false));
        inventory.setItem(ED_STATUS, item(m.icon, problem == null ? ChatColor.GREEN + "✔ spielbar" : ChatColor.RED + "✘ nicht spielbar",
                problem == null ? List.of(ChatColor.GRAY + "Welt: " + m.world) : List.of(ChatColor.GRAY + problem, ChatColor.GRAY + "Welt: " + m.world), problem == null));
        inventory.setItem(ED_LOBBY, item(Material.BEACON, ChatColor.LIGHT_PURPLE + "Eigene Lobby hier setzen",
                List.of(ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + (m.ownLobby() == null ? "globale Lobby (GLOBAL)" : "eigene Lobby (OWN)"),
                        ChatColor.GRAY + "Rechtsklick: eigene Lobby entfernen"), false));
        ItemStack hand = p.getInventory().getItemInMainHand();
        inventory.setItem(ED_ICON, item(Material.ITEM_FRAME, ChatColor.YELLOW + "Icon = Item in deiner Hand",
                List.of(ChatColor.GRAY + "Aktuell: " + ChatColor.WHITE + m.icon.name(),
                        ChatColor.GRAY + "In der Hand: " + ChatColor.WHITE + (hand.getType().isAir() ? "nichts" : hand.getType().name())), false));
        inventory.setItem(ED_ENABLED, item(m.enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                m.enabled ? ChatColor.GREEN + "Aktiviert" : ChatColor.RED + "Deaktiviert", List.of(ChatColor.GRAY + "Klicken zum Umschalten"), false));
        inventory.setItem(ED_CLEAR_SPAWNS, item(Material.TNT, ChatColor.RED + "Alle Spieler-Spawns löschen",
                List.of(ChatColor.GRAY + "Shift-Klick zum Bestätigen"), false));
        inventory.setItem(ED_CLEAR_GOLD, item(Material.TNT, ChatColor.RED + "Alle Gold-Spawns löschen",
                List.of(ChatColor.GRAY + "Shift-Klick zum Bestätigen"), false));
        inventory.setItem(ED_AREA, item(Material.STRUCTURE_VOID, ChatColor.YELLOW + "Spielbereich (Größe der Map)",
                List.of(ChatColor.GRAY + "Status: " + (m.areaEnabled ? ChatColor.GREEN + "aktiv" : ChatColor.RED + "aus"),
                        ChatColor.GRAY + "Ecke 1: " + ChatColor.WHITE + (m.corner1.isEmpty() ? "–" : MapManager.toConfig(m.world, m.corner1, false)),
                        ChatColor.GRAY + "Ecke 2: " + ChatColor.WHITE + (m.corner2.isEmpty() ? "–" : MapManager.toConfig(m.world, m.corner2, false)),
                        "", ChatColor.GRAY + "Linksklick: Ecke 1 hier", ChatColor.GRAY + "Rechtsklick: Ecke 2 hier",
                        ChatColor.GRAY + "Shift-Klick: an/aus"), m.areaEnabled));
        inventory.setItem(ED_LOBBY_RADIUS, item(Material.LEAD, ChatColor.LIGHT_PURPLE + "Lobby-Bereich: " + ChatColor.WHITE + m.lobbyRadius + " Blöcke",
                List.of(ChatColor.GRAY + "Wie weit sich Wartende von der eigenen Lobby", ChatColor.GRAY + "entfernen dürfen (0 = beliebig)",
                        "", ChatColor.GRAY + "Linksklick: +1, Rechtsklick: −1", ChatColor.GRAY + "Shift: ±5"), false));
        inventory.setItem(ED_NEXT, item(Material.CLOCK, ChatColor.AQUA + "Als nächste Map spielen",
                List.of(ChatColor.GRAY + "Die nächste Runde läuft auf dieser Map"), maps.forcedNext() == m));
        inventory.setItem(ED_DELETE, item(Material.LAVA_BUCKET, ChatColor.DARK_RED + "Map löschen",
                List.of(ChatColor.GRAY + "Shift-Klick zum Bestätigen", ChatColor.GRAY + "(die Welt selbst bleibt erhalten)"), false));
        inventory.setItem(ED_BACK, item(Material.ARROW, ChatColor.GRAY + "Zurück zur Übersicht", List.of(), false));
    }

    // ------------------------------------------------------------------ Klicks

    public void click(Player p, int slot, ClickType click) {
        if (editing == null) clickOverview(p, slot);
        else clickEditor(p, slot, click);
    }

    private void clickOverview(Player p, int slot) {
        GameMap m = mapSlots.get(slot);
        if (m != null) {
            openEditor(plugin, p, m);
            return;
        }
        switch (slot) {
            case OV_TESTMAP -> {
                p.closeInventory();
                p.performCommand("mm map testmap");
            }
            case OV_IMPORT -> {
                p.closeInventory();
                plugin.importer().importAll(p);
            }
            case OV_MODE -> {
                Mode next = switch (maps.mode()) {
                    case VOTE -> Mode.RANDOM_EACH_ROUND;
                    case RANDOM_EACH_ROUND -> Mode.RANDOM_ON_START;
                    case RANDOM_ON_START -> Mode.VOTE;
                };
                maps.setMode(next);
                plugin.game().refreshLobby();
                p.sendMessage(Util.PREFIX + "Auswahl-Modus: " + ChatColor.YELLOW + modeName(next));
                render(p);
            }
            case OV_LOBBY -> {
                maps.setLobby(p.getLocation());
                plugin.game().refreshLobby();
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Globale Lobby gesetzt.");
                render(p);
            }
            case OV_CLOSE -> p.closeInventory();
            default -> {
            }
        }
    }

    private void clickEditor(Player p, int slot, ClickType click) {
        GameMap m = editing;
        switch (slot) {
            case ED_TP -> {
                p.closeInventory();
                World w = maps.loadWorld(m.world);
                if (w == null) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Die Welt '" + m.world + "' wurde nicht gefunden.");
                    return;
                }
                List<Location> spawns = m.spawnLocations();
                p.teleport(spawns.isEmpty() ? w.getSpawnLocation() : spawns.get(0));
            }
            case ED_SPAWN, ED_GOLD -> {
                if (!p.getWorld().getName().equals(m.world)) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Du musst in der Welt der Map stehen (" + m.world + ").");
                    return;
                }
                boolean spawn = slot == ED_SPAWN;
                maps.add(spawn ? m.spawns : m.gold, p.getLocation());
                p.sendMessage(Util.PREFIX + (spawn ? ChatColor.GREEN + "Spieler-Spawn #" + m.spawns.size() : ChatColor.GOLD + "Gold-Spawn #" + m.gold.size())
                        + " hinzugefügt." + ChatColor.DARK_GRAY + " (Map " + m.key + ")");
                if (spawn && m.spawns.size() == 1) plugin.game().refreshLobby();
            }
            case ED_LOBBY -> {
                boolean remove = click.isRightClick();
                maps.setMapLobby(m, remove ? null : p.getLocation());
                p.sendMessage(Util.PREFIX + (remove ? "Map nutzt wieder die globale Lobby." : ChatColor.GREEN + "Eigene Lobby der Map gesetzt."));
                plugin.game().refreshLobby();
            }
            case ED_ICON -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Nimm zuerst das gewünschte Item in die Hand.");
                    return;
                }
                m.icon = hand.getType();
                maps.save();
            }
            case ED_ENABLED -> {
                m.enabled = !m.enabled;
                if (m.enabled) maps.loadWorld(m.world);
                maps.save();
                plugin.game().refreshLobby();
            }
            case ED_CLEAR_SPAWNS, ED_CLEAR_GOLD -> {
                if (!click.isShiftClick()) {
                    p.sendMessage(Util.PREFIX + ChatColor.YELLOW + "Zum Löschen bitte Shift-Klick.");
                    return;
                }
                maps.clear(slot == ED_CLEAR_SPAWNS ? m.spawns : m.gold);
                p.sendMessage(Util.PREFIX + (slot == ED_CLEAR_SPAWNS ? "Alle Spieler-Spawns" : "Alle Gold-Spawns") + " gelöscht.");
                plugin.game().refreshLobby();
            }
            case ED_AREA -> {
                if (click.isShiftClick()) {
                    m.areaEnabled = !m.areaEnabled;
                } else {
                    if (!p.getWorld().getName().equals(m.world)) {
                        p.sendMessage(Util.PREFIX + ChatColor.RED + "Du musst in der Welt der Map stehen (" + m.world + ").");
                        return;
                    }
                    if (click.isRightClick()) m.corner2 = Util.serialize(p.getLocation());
                    else m.corner1 = Util.serialize(p.getLocation());
                    m.areaEnabled = !m.corner1.isEmpty() && !m.corner2.isEmpty();
                }
                maps.saveMap(m);
            }
            case ED_LOBBY_RADIUS -> {
                int step = click.isShiftClick() ? 5 : 1;
                m.lobbyRadius = Math.max(0, m.lobbyRadius + (click.isRightClick() ? -step : step));
                maps.saveMap(m);
            }
            case ED_NEXT -> {
                if (maps.problem(m) != null) {
                    p.sendMessage(Util.PREFIX + ChatColor.RED + "Die Map ist nicht spielbar: " + maps.problem(m));
                    return;
                }
                maps.setForcedNext(m);
                plugin.game().refreshLobby();
                p.sendMessage(Util.PREFIX + ChatColor.GREEN + "Die nächste Runde läuft auf '" + m.key + "'.");
            }
            case ED_DELETE -> {
                if (!click.isShiftClick()) {
                    p.sendMessage(Util.PREFIX + ChatColor.YELLOW + "Zum Löschen bitte Shift-Klick.");
                    return;
                }
                maps.delete(m.key);
                plugin.game().refreshLobby();
                p.sendMessage(Util.PREFIX + "Map '" + m.key + "' gelöscht (die Welt bleibt erhalten).");
                openOverview(plugin, p);
                return;
            }
            case ED_BACK -> {
                openOverview(plugin, p);
                return;
            }
            default -> {
                return;
            }
        }
        if (p.getOpenInventory().getTopInventory().getHolder() == this) render(p);
    }

    // ------------------------------------------------------------------ Helfer

    private static String modeName(Mode m) {
        return switch (m) {
            case VOTE -> "Abstimmung";
            case RANDOM_EACH_ROUND -> "Zufall pro Runde";
            case RANDOM_ON_START -> "Zufall pro Serverstart";
        };
    }

    private static ItemStack item(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(mat.isItem() ? mat : Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Util.itemName(name));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Util.itemName(l));
        meta.lore(lines);
        meta.setEnchantmentGlintOverride(glow);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }
}
