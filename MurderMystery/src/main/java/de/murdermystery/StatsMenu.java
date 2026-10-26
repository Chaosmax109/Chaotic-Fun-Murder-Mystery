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
import java.util.List;
import java.util.Locale;

/** Truhen-Menü mit den Statistiken eines Spielers (/stats). Klicks darin werden blockiert. */
public class StatsMenu implements InventoryHolder {

    private final Inventory inventory;

    public StatsMenu(StatsManager.PlayerStats st, Settings s) {
        inventory = Bukkit.createInventory(this, 27, Util.text(ChatColor.DARK_GRAY + "Stats: " + ChatColor.DARK_RED + st.name()));

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

        inventory.setItem(10, item(Material.DIAMOND_SWORD, ChatColor.GOLD + "Kills (gesamt): " + ChatColor.WHITE + st.kills(),
                ChatColor.GRAY + "als " + s.murderer.coloredName() + ChatColor.GRAY + ": " + ChatColor.WHITE + st.murdererKills(),
                ChatColor.GRAY + "als " + s.detective.coloredName() + ChatColor.GRAY + ": " + ChatColor.WHITE + st.detectiveKills(),
                ChatColor.GRAY + "als " + s.innocent.coloredName() + ChatColor.GRAY + ": " + ChatColor.WHITE + st.innocentKills()));
        inventory.setItem(11, item(Material.IRON_SWORD, s.murderer.coloredName() + ChatColor.GRAY + "-Kills: " + ChatColor.WHITE + st.murdererKills()));
        inventory.setItem(12, item(Material.BOW, s.detective.coloredName() + ChatColor.GRAY + "-Kills: " + ChatColor.WHITE + st.detectiveKills()));
        inventory.setItem(13, item(Material.GOLD_INGOT, s.innocent.coloredName() + ChatColor.GRAY + "-Kills: " + ChatColor.WHITE + st.innocentKills(),
                ChatColor.DARK_GRAY + "Mörder, die du als Unschuldiger erledigt hast"));
        inventory.setItem(14, item(Material.SKELETON_SKULL, ChatColor.RED + "Tode: " + ChatColor.WHITE + st.deaths()));
        inventory.setItem(15, item(Material.EMERALD, ChatColor.GREEN + "Wins: " + ChatColor.WHITE + st.wins()));
        inventory.setItem(16, item(Material.REDSTONE, ChatColor.RED + "Loses: " + ChatColor.WHITE + st.losses()));
        inventory.setItem(22, item(Material.NETHER_STAR, ChatColor.AQUA + "Winrate: " + ChatColor.WHITE + winrate,
                ChatColor.GRAY + "" + st.wins() + " von " + st.games() + " Spielen gewonnen"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
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
