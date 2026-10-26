package de.murdermystery;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Truhen-Menü mit den Statistiken eines Spielers (/stats). Klicks darin werden blockiert. */
public class StatsMenu implements InventoryHolder {

    private static final int[] ROLE_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    private final Inventory inventory;

    public StatsMenu(StatsManager.PlayerStats st, Settings s) {
        inventory = Bukkit.createInventory(this, 36, Util.text(ChatColor.DARK_GRAY + "Stats: " + ChatColor.DARK_RED + st.name()));

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        String winrate = String.format(Locale.GERMANY, "%.1f %%", st.winrate());
        String kd = String.format(Locale.GERMANY, "%.2f", st.deaths() == 0 ? st.kills() : (double) st.kills() / st.deaths());

        ItemStack head = item(Material.PLAYER_HEAD, ChatColor.YELLOW + st.name(),
                ChatColor.GRAY + "Spiele: " + ChatColor.WHITE + st.games(),
                ChatColor.GRAY + "Winrate: " + ChatColor.WHITE + winrate,
                ChatColor.GRAY + "K/D: " + ChatColor.WHITE + kd);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(st.id()));
            head.setItemMeta(skull);
        }
        inventory.setItem(4, head);

        // Kills pro Rolle: alle Rollen aus roles.yml, dazu Rollen, die es inzwischen nicht mehr gibt
        Map<String, Integer> perRole = new LinkedHashMap<>();
        for (Settings.Role r : s.allRoles()) perRole.put(r.key, st.roleKills().getOrDefault(r.key, 0));
        for (Map.Entry<String, Integer> e : st.roleKills().entrySet()) perRole.putIfAbsent(e.getKey(), e.getValue());

        List<String> killLore = new ArrayList<>();
        for (Map.Entry<String, Integer> e : perRole.entrySet()) {
            killLore.add(ChatColor.GRAY + "als " + roleName(s, e.getKey()) + ChatColor.GRAY + ": " + ChatColor.WHITE + e.getValue());
        }
        inventory.setItem(10, item(Material.DIAMOND_SWORD, ChatColor.GOLD + "Kills (gesamt): " + ChatColor.WHITE + st.kills(),
                killLore.toArray(new String[0])));
        inventory.setItem(12, item(Material.SKELETON_SKULL, ChatColor.RED + "Tode: " + ChatColor.WHITE + st.deaths()));
        inventory.setItem(14, item(Material.EMERALD, ChatColor.GREEN + "Wins: " + ChatColor.WHITE + st.wins()));
        inventory.setItem(16, item(Material.REDSTONE, ChatColor.RED + "Loses: " + ChatColor.WHITE + st.losses()));
        inventory.setItem(22, item(Material.NETHER_STAR, ChatColor.AQUA + "Winrate: " + ChatColor.WHITE + winrate,
                ChatColor.GRAY + "" + st.wins() + " von " + st.games() + " Spielen gewonnen"));

        int slot = 0;
        for (Map.Entry<String, Integer> e : perRole.entrySet()) {
            if (slot >= ROLE_SLOTS.length) break;
            Settings.Role r = s.role(e.getKey());
            Material icon = r != null ? r.icon : Material.PAPER;
            inventory.setItem(ROLE_SLOTS[slot++], item(icon, roleName(s, e.getKey()) + ChatColor.GRAY + "-Kills: " + ChatColor.WHITE + e.getValue()));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static String roleName(Settings s, String key) {
        Settings.Role r = s.role(key);
        return r != null ? r.coloredName() : ChatColor.GRAY + key;
    }

    private static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Util.itemName(name));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Util.itemName(l));
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }
}
