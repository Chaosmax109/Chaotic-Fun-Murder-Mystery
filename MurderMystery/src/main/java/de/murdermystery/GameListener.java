package de.murdermystery;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.projectiles.ProjectileSource;

public class GameListener implements Listener {

    private final MurderMysteryPlugin plugin;
    private final Game game;

    public GameListener(MurderMysteryPlugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    private boolean protectedPlayer(Player p) {
        return game.isInArena(p) && p.getGameMode() != GameMode.CREATIVE;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Ohne gesetzte Lobby wird die Spawn-Position des ersten Spielers zur globalen Lobby
        game.ensureLobby(p);
        if (plugin.settings().autoJoin) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) game.join(p);
            }, 2L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        game.leave(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!game.isInArena(victim)) return;
        e.setCancelled(true);

        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            victim.teleport(game.respawnPoint());
            return;
        }
        if (!(e instanceof EntityDamageByEntityEvent byEntity) || game.getState() != Game.State.INGAME) return;
        Entity damager = byEntity.getDamager();
        if (damager instanceof AbstractArrow arrow) {
            ProjectileSource src = arrow.getShooter();
            arrow.remove();
            if (src instanceof Player shooter) {
                game.onArrowHit(shooter, victim);
            }
        } else if (damager instanceof Player attacker) {
            game.onMelee(attacker, victim);
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && game.isInArena(p)) {
            game.onShoot(p);
        }
    }

    @EventHandler
    public void onArrowLand(ProjectileHitEvent e) {
        if (e.getEntity() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player p && game.isInArena(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, arrow::remove, 1L);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!game.isInArena(p)) return;
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true); // kein Feldzertrampeln
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) return; // Event kommt sonst für Haupt- und Nebenhand doppelt
        String lobbyItem = game.lobbyItemType(e.getItem());
        if (lobbyItem != null) {
            e.setCancelled(true);
            switch (lobbyItem) {
                case "vote" -> game.openVoteMenu(p);
                case "stats" -> plugin.openStats(p, null);
                case "mapinfo" -> game.showMapInfo(p);
                default -> {
                }
            }
            return;
        }
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && game.isKnife(p, e.getItem())) {
            e.setCancelled(true);
            game.throwKnife(p);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !game.isInArena(p)) return;
        e.setCancelled(true);
        if (game.isGoldItem(e.getItem())) {
            game.onGoldPickup(p, e.getItem());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!game.isInArena(p) || !e.hasChangedBlock()) return;
        if (e.getTo().getY() < p.getWorld().getMinHeight() && p.getGameMode() != GameMode.SPECTATOR) {
            p.teleport(game.respawnPoint());
            return;
        }
        game.checkLobbyBounds(p);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (game.getState() != Game.State.INGAME || !game.isInArena(p)) return;
        // Tote und Zuschauer schreiben nur untereinander
        if (!game.getAlive().contains(p.getUniqueId())) {
            e.viewers().removeIf(v -> v instanceof Player viewer && game.getAlive().contains(viewer.getUniqueId()));
            e.renderer((source, displayName, message, viewer) -> Component.text("[TOT] ", NamedTextColor.GRAY)
                    .append(displayName.color(NamedTextColor.GRAY))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(message.color(NamedTextColor.GRAY)));
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && game.isInArena(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (protectedPlayer(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (protectedPlayer(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (protectedPlayer(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (protectedPlayer(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onInventory(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MapAdminMenu menu) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p && e.getClickedInventory() == e.getView().getTopInventory()
                    && p.hasPermission("mm.admin")) {
                menu.click(p, e.getSlot(), e.getClick());
            }
            return;
        }
        if (e.getView().getTopInventory().getHolder() instanceof VoteMenu menu) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p && e.getClickedInventory() == e.getView().getTopInventory()) {
                MapManager.GameMap m = menu.mapAt(e.getSlot());
                if (m != null) {
                    game.vote(p, m);
                    menu.render(p);
                }
            }
            return;
        }
        if (e.getView().getTopInventory().getHolder() instanceof StatsMenu
                || (e.getWhoClicked() instanceof Player p && protectedPlayer(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof StatsMenu || holder instanceof VoteMenu || holder instanceof MapAdminMenu) e.setCancelled(true);
    }

    /** Keine Monster und Tiere, die von selbst in den Map-Welten auftauchen. */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL && plugin.maps().isMapWorld(e.getLocation().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (game.isInArena(e.getPlayer())) e.setCancelled(true);
    }
}
