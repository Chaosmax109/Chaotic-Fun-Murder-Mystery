package de.murdermystery;

import de.murdermystery.MapManager.GameMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Truhen-Menü für die Map-Abstimmung in der Lobby. Ein Klick auf eine Map gibt ihr die eigene Stimme. */
public class VoteMenu implements InventoryHolder {

    private final Game game;
    private final Inventory inventory;
    private final Map<Integer, GameMap> slots = new HashMap<>();

    public VoteMenu(Game game) {
        this.game = game;
        List<GameMap> options = game.voteOptions();
        int size = Math.max(9, Math.min(54, ((options.size() * 2 + 8) / 9) * 9));
        inventory = Bukkit.createInventory(this, size, Util.text(ChatColor.DARK_GRAY + "Map-Abstimmung"));
        // Maps mit je einem freien Platz dazwischen, mittig in der ersten Reihe beginnend
        int slot = options.size() <= 4 ? 4 - (options.size() - 1) : 0;
        for (GameMap m : options) {
            if (slot >= size) break;
            slots.put(slot, m);
            slot += options.size() <= 4 ? 2 : 1;
        }
    }

    /** Baut die Items für den Spieler auf (mit aktuellen Stimmen und Markierung der eigenen Wahl). */
    public VoteMenu render(Player viewer) {
        String own = game.voteOf(viewer);
        for (Map.Entry<Integer, GameMap> e : slots.entrySet()) {
            GameMap m = e.getValue();
            boolean chosen = m.key.equals(own);
            ItemStack it = new ItemStack(m.icon);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Util.itemName(ChatColor.YELLOW + m.displayName));
            List<Component> lore = new ArrayList<>();
            lore.add(Util.itemName(ChatColor.GRAY + "Stimmen: " + ChatColor.WHITE + game.votesFor(m)));
            lore.add(Util.itemName(""));
            lore.add(Util.itemName(chosen ? ChatColor.GREEN + "✔ Deine Wahl" : ChatColor.YELLOW + "Klicke zum Abstimmen"));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(chosen);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
            inventory.setItem(e.getKey(), it);
        }
        return this;
    }

    public GameMap mapAt(int slot) {
        return slots.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
