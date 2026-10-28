package de.murdermystery;

import de.murdermystery.MapManager.GameMap;
import de.murdermystery.Settings.GoldReward;
import de.murdermystery.Settings.RewardItem;
import de.murdermystery.Settings.Role;
import de.murdermystery.Settings.Team;
import de.murdermystery.Settings.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Game {

    public enum State { WAITING, STARTING, INGAME, ENDING }

    private enum Winner { INNOCENTS, MURDERER, NONE }

    private static final int SLOT_WEAPON = 1;
    private static final int SLOT_GOLD = 8;
    private static final int SLOT_ARROW = 9;

    private final MurderMysteryPlugin plugin;
    private final MapManager maps;
    private final Random random = new Random();

    // Concurrent, weil der Chat-Listener asynchron darauf zugreift
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Role> roles = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> left = new HashSet<>();
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Map<UUID, Integer> gold = new HashMap<>();
    private final Map<UUID, Long> knifeReady = new HashMap<>();
    private final Map<Integer, Item> goldItems = new HashMap<>();
    private final List<BukkitTask> knifeTasks = new ArrayList<>();
    private final List<ArmorStand> knives = new ArrayList<>();
    /** Spieler mit einem Rollen-Bogen (unendlich Pfeile) → Rolle, von der der Bogen stammt. */
    private final Map<UUID, Role> bowHolders = new HashMap<>();
    /** Auf der Map liegende Bögen toter Bogenträger → Rolle, von der der Bogen stammt. */
    private final Map<ArmorStand, Role> droppedBows = new LinkedHashMap<>();

    private State state = State.WAITING;
    private boolean forceStart;
    private int countdown;
    private int timeLeft;
    private int graceLeft;
    private int goldTimer;
    private int endCountdown;
    private String heroName;

    /** Map der laufenden (bzw. zuletzt gespielten) Runde. */
    private GameMap map;
    /** Maps, die in der Lobby zur Abstimmung stehen. */
    private final List<GameMap> voteOptions = new ArrayList<>();
    /** Spieler → Schlüssel der gewählten Map. */
    private final Map<UUID, String> votes = new HashMap<>();
    private final NamespacedKey voteItemKey;

    private BukkitTask ticker;
    private BukkitTask fastTicker;

    public Game(MurderMysteryPlugin plugin, MapManager maps) {
        this.plugin = plugin;
        this.maps = maps;
        this.voteItemKey = new NamespacedKey(plugin, "vote_item");
    }

    private Settings s() {
        return plugin.settings();
    }

    // ------------------------------------------------------------------ Lifecycle

    public void startTasks() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        fastTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::fastTick, 4L, 4L);
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        if (fastTicker != null) fastTicker.cancel();
        clearEntities();
        for (UUID id : allMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                resetToServer(p);
            }
        }
    }

    // ------------------------------------------------------------------ Join / Leave

    public boolean isInArena(Player p) {
        return players.contains(p.getUniqueId()) || spectators.contains(p.getUniqueId());
    }

    public boolean isAlive(Player p) {
        return state == State.INGAME && alive.contains(p.getUniqueId());
    }

    public void join(Player p) {
        if (isInArena(p)) {
            p.sendMessage(Util.PREFIX + "Du bist bereits im Spiel.");
            return;
        }
        if (!maps.isReady()) {
            if (p.hasPermission("mm.admin")) {
                p.sendMessage(Util.PREFIX + ChatColor.RED + "Noch nicht eingerichtet: Lobby (/mm setlobby) und mindestens eine Map mit Spawns (/mm map create, /mm addspawn).");
            }
            return;
        }
        boards.put(p.getUniqueId(), new Board(p));
        if (state == State.INGAME || state == State.ENDING || players.size() >= s().maxPlayers) {
            spectators.add(p.getUniqueId());
            prepare(p, GameMode.SPECTATOR);
            p.teleport(state == State.INGAME ? respawnPoint() : maps.getLobby());
            p.sendMessage(Util.PREFIX + "Eine Runde läuft gerade – du schaust zu und spielst in der nächsten Runde mit.");
            if (state == State.INGAME) {
                boards.get(p.getUniqueId()).setHiddenNames(playerNames(players), true);
            }
            return;
        }
        players.add(p.getUniqueId());
        prepare(p, GameMode.ADVENTURE);
        p.teleport(maps.getLobby());
        if (voteOptions.isEmpty()) newVoteRound();
        giveVoteItem(p);
        broadcast(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " ist beigetreten ("
                + ChatColor.GREEN + players.size() + ChatColor.GRAY + "/" + ChatColor.GREEN + s().maxPlayers + ChatColor.GRAY + ")");
        updateBoards();
    }

    public void leave(Player p, boolean quitting) {
        UUID id = p.getUniqueId();
        if (!isInArena(p)) {
            if (!quitting) p.sendMessage(Util.PREFIX + "Du bist in keinem Spiel.");
            return;
        }
        boolean wasPlaying = players.remove(id);
        spectators.remove(id);
        boards.remove(id);
        gold.remove(id);
        votes.remove(id);

        if (state == State.INGAME && roles.containsKey(id)) {
            left.add(id); // wer mitten in der Runde geht, bekommt eine Niederlage
        }
        if (state == State.INGAME && alive.remove(id)) {
            Role bow = bowHolders.remove(id);
            if (bow != null) {
                dropBow(p.getLocation(), bow);
            }
            broadcast(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " hat das Spiel verlassen.");
            checkWin();
        } else if (wasPlaying && state != State.INGAME) {
            broadcast(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " hat das Spiel verlassen ("
                    + ChatColor.GREEN + players.size() + ChatColor.GRAY + "/" + ChatColor.GREEN + s().maxPlayers + ChatColor.GRAY + ")");
        }
        if (!quitting) {
            resetToServer(p);
            p.sendMessage(Util.PREFIX + "Du hast das Spiel verlassen.");
        }
    }

    public void forceStart(Player sender) {
        if (state != State.WAITING && state != State.STARTING) {
            sender.sendMessage(Util.PREFIX + ChatColor.RED + "Es läuft bereits eine Runde.");
            return;
        }
        if (players.size() < 2) {
            sender.sendMessage(Util.PREFIX + ChatColor.RED + "Es werden mindestens 2 Spieler benötigt.");
            return;
        }
        forceStart = true;
        state = State.STARTING;
        countdown = Math.min(countdown <= 0 ? 5 : countdown, 5);
        broadcast(ChatColor.GREEN + "Die Runde wurde gestartet!");
    }

    public void forceStop() {
        if (state == State.INGAME) {
            broadcast(ChatColor.RED + "Die Runde wurde von einem Admin beendet.");
            end(Winner.NONE);
        }
    }

    /** Hält wartende Spieler in der Nähe der Lobby, falls diese in derselben Welt wie die Map liegt. */
    public void checkLobbyBounds(Player p) {
        int max = s().lobbyMaxDistance;
        if (max <= 0 || (state != State.WAITING && state != State.STARTING)) return;
        if (!players.contains(p.getUniqueId()) || p.getGameMode() == GameMode.CREATIVE) return;
        Location lobby = maps.getLobby();
        if (lobby == null) return;
        if (!p.getWorld().equals(lobby.getWorld()) || p.getLocation().distanceSquared(lobby) > (double) max * max) {
            p.teleport(lobby);
            p.sendMessage(Util.PREFIX + "Bleib in der Lobby, bis die Runde startet!");
        }
    }

    // ------------------------------------------------------------------ Map-Abstimmung

    /** Stellt neue Maps zur Abstimmung und löscht alle Stimmen (nach jeder Runde und bei /mm reload). */
    public void newVoteRound() {
        votes.clear();
        voteOptions.clear();
        if (maps.mode() == MapManager.Mode.VOTE) {
            voteOptions.addAll(maps.voteCandidates());
        }
    }

    /** Abstimmung nur im Modus VOTE, in der Lobby und wenn es mehr als eine Map zur Auswahl gibt. */
    public boolean votingOpen() {
        return maps.mode() == MapManager.Mode.VOTE && voteOptions.size() > 1 && maps.forcedNext() == null
                && (state == State.WAITING || state == State.STARTING);
    }

    public List<GameMap> voteOptions() {
        return voteOptions;
    }

    public int votesFor(GameMap m) {
        int c = 0;
        for (String key : votes.values()) if (key.equals(m.key)) c++;
        return c;
    }

    public String voteOf(Player p) {
        return votes.get(p.getUniqueId());
    }

    public void openVoteMenu(Player p) {
        if (!players.contains(p.getUniqueId()) || !votingOpen()) {
            p.sendMessage(Util.PREFIX + ChatColor.RED + "Gerade läuft keine Map-Abstimmung.");
            return;
        }
        p.openInventory(new VoteMenu(this).render(p).getInventory());
    }

    public void vote(Player p, GameMap m) {
        if (!players.contains(p.getUniqueId()) || !votingOpen() || !voteOptions.contains(m)) return;
        votes.put(p.getUniqueId(), m.key);
        p.sendMessage(Util.PREFIX + "Du hast für " + ChatColor.YELLOW + m.displayName + ChatColor.GRAY + " gestimmt.");
        Util.sound(p, 1.2f, "ui.button.click");
        updateBoards();
    }

    /** Die Map mit den meisten Stimmen (bei Gleichstand zufällig), oder null ohne Stimmen. */
    private GameMap voteWinner() {
        List<GameMap> best = new ArrayList<>();
        int max = 0;
        for (GameMap m : voteOptions) {
            int v = votesFor(m);
            if (v > max) {
                max = v;
                best.clear();
            }
            if (v == max && v > 0) best.add(m);
        }
        return best.isEmpty() ? null : best.get(random.nextInt(best.size()));
    }

    public boolean isVoteItem(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(voteItemKey, PersistentDataType.BYTE);
    }

    private void giveVoteItem(Player p) {
        if (!votingOpen()) return;
        ItemStack it = item(Material.PAPER, ChatColor.YELLOW + "Map-Abstimmung " + ChatColor.GRAY + "(Rechtsklick)");
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(voteItemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        p.getInventory().setItem(4, it);
    }

    /** Text für Scoreboard & Co.: welche Map als Nächstes gespielt wird. */
    private String nextMapText() {
        if (maps.forcedNext() != null) return maps.forcedNext().displayName;
        return switch (maps.mode()) {
            case RANDOM_ON_START -> maps.startMap() != null ? maps.startMap().displayName : "?";
            case RANDOM_EACH_ROUND -> "Zufällig";
            case VOTE -> voteOptions.size() == 1 ? voteOptions.get(0).displayName : "Abstimmung";
        };
    }

    // ------------------------------------------------------------------ Ticks

    private void tick() {
        switch (state) {
            case WAITING -> {
                if (players.size() >= s().minPlayers && maps.isReady()) {
                    state = State.STARTING;
                    countdown = s().lobbyCountdown;
                }
            }
            case STARTING -> tickCountdown();
            case INGAME -> tickGame();
            case ENDING -> {
                if (--endCountdown <= 0) {
                    reset();
                }
            }
        }
        updateBoards();
    }

    private void tickCountdown() {
        if (players.size() < (forceStart ? 2 : s().minPlayers)) {
            state = State.WAITING;
            forceStart = false;
            broadcast(ChatColor.RED + "Zu wenige Spieler – Countdown abgebrochen.");
            return;
        }
        if (players.size() >= s().maxPlayers && countdown > 10) {
            countdown = 10;
        }
        if (countdown <= 0) {
            startGame();
            return;
        }
        if (countdown == 30 || countdown == 20 || countdown == 10 || countdown <= 5) {
            broadcast("Die Runde startet in " + ChatColor.YELLOW + countdown + ChatColor.GRAY + " Sekunde" + (countdown == 1 ? "" : "n") + "!");
            for (Player p : online(players)) {
                Util.sound(p, 1f, "block.note_block.hat");
                if (countdown <= 5) {
                    Util.title(p, ChatColor.YELLOW + "" + countdown, "");
                }
            }
        }
        countdown--;
    }

    private void tickGame() {
        if (graceLeft > 0) {
            graceLeft--;
            if (graceLeft == 0) {
                giveWeapons();
            }
        }
        if (s().goldEnabled && ++goldTimer >= s().goldInterval) {
            goldTimer = 0;
            spawnGold();
        }
        timeLeft--;
        if (timeLeft == 60) {
            broadcast(ChatColor.YELLOW + "Noch 1 Minute!");
        }
        if (timeLeft <= 0) {
            broadcast(ChatColor.GREEN + "Die Zeit ist abgelaufen!");
            end(Winner.INNOCENTS);
        }
    }

    private void fastTick() {
        if (state != State.INGAME || droppedBows.isEmpty()) return;
        for (Map.Entry<ArmorStand, Role> bow : new ArrayList<>(droppedBows.entrySet())) {
            Location bowLoc = bow.getKey().getLocation();
            Role source = bow.getValue();
            for (Player p : online(alive)) {
                UUID id = p.getUniqueId();
                if (!roles.get(id).canPickupBow || bowHolders.containsKey(id) || !p.getWorld().equals(bowLoc.getWorld())) continue;
                if (p.getLocation().distanceSquared(bowLoc) <= 2.25) {
                    bow.getKey().remove();
                    droppedBows.remove(bow.getKey());
                    bowHolders.put(id, source);
                    PlayerInventory inv = p.getInventory();
                    inv.remove(Material.BOW);
                    inv.remove(Material.CROSSBOW);
                    inv.setItem(weaponSlot(roles.get(id)), item(source.weapon, source.weaponName));
                    addArrows(p, 1);
                    Util.title(p, source.color + "Du hast den Bogen!", ChatColor.GRAY + "Finde und erschieße den " + s().mainMurderer().name);
                    Util.sound(p, 1f, "entity.player.levelup");
                    broadcast(source.color + "Jemand hat den Bogen des " + source.name + "s aufgehoben!");
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Rollen verteilen

    /** Verteilt die Rollen: jede Rolle wird mit ihrer Wahrscheinlichkeit (chance) vergeben, der Rest wird "innocent". */
    private Map<Player, Role> assignRoles(List<Player> list) {
        Map<Player, Role> result = new LinkedHashMap<>();
        List<Player> pool = new ArrayList<>(list);
        Collections.shuffle(pool, random);
        int n = pool.size();
        int murderers = 0;
        int innocents = 0;
        for (Role r : s().specialRoles) {
            if (r.chance < 100 && random.nextDouble() * 100 >= r.chance) continue;
            for (int k = 0; k < r.count(n) && !pool.isEmpty(); k++) {
                // Es muss immer mindestens einen Mörder und einen Unschuldigen geben – dafür Plätze freihalten
                int reserved = (murderers == 0 && r.team != Team.MURDERER ? 1 : 0)
                        + (innocents == 0 && r.team != Team.INNOCENT ? 1 : 0);
                if (pool.size() <= reserved) break;
                result.put(pool.remove(0), r);
                if (r.team == Team.MURDERER) murderers++;
                if (r.team == Team.INNOCENT) innocents++;
            }
        }
        if (murderers == 0) {
            result.put(pool.remove(0), s().mainMurderer());
        }
        for (Player p : pool) {
            result.put(p, s().innocent);
        }
        return result;
    }

    /** Wahrscheinlichkeit (0–100), mit der ein Spieler bei {@code n} Spielern die Rolle bekommt (für die Lobby-Anzeige). */
    private double chanceFor(Role r, int n) {
        int slots = Math.min(r.count(n), r.team == Team.INNOCENT ? n - 1 : n - 1 - (r.team == Team.NEUTRAL ? 1 : 0));
        if (r.isMurderer() && r == s().mainMurderer()) slots = Math.max(slots, 1);
        return Math.max(0, r.chance / 100.0 * slots / n * 100);
    }

    // ------------------------------------------------------------------ Rundenablauf

    private void startGame() {
        List<Player> list = online(players);
        GameMap chosen = maps.chooseForRound(voteWinner());
        if (chosen != null) maps.loadWorld(chosen.world);
        List<Location> spawns = chosen == null ? new ArrayList<>() : new ArrayList<>(chosen.spawnLocations());
        if (list.size() < 2 || spawns.isEmpty()) {
            state = State.WAITING;
            broadcast(ChatColor.RED + "Keine spielbare Map gefunden – Start abgebrochen.");
            return;
        }
        map = chosen;
        state = State.INGAME;
        broadcast("Gespielt wird auf: " + ChatColor.YELLOW + map.displayName);
        forceStart = false;
        Collections.shuffle(spawns);
        roles.clear();
        names.clear();
        left.clear();
        gold.clear();
        knifeReady.clear();
        alive.clear();
        bowHolders.clear();
        heroName = null;

        Map<Player, Role> assigned = assignRoles(list);
        int i = 0;
        for (Map.Entry<Player, Role> e : assigned.entrySet()) {
            Player p = e.getKey();
            Role role = e.getValue();
            UUID id = p.getUniqueId();
            roles.put(id, role);
            names.put(id, p.getName());
            alive.add(id);
            gold.put(id, 0);
            prepare(p, GameMode.ADVENTURE);
            p.addPotionEffects(role.effects);
            // Jeder Spieler bekommt einen eigenen Spawn, solange genug gesetzt sind
            p.teleport(spawns.get(i++ % spawns.size()));
            Util.title(p, role.title, role.subtitle);
            p.sendMessage(Util.PREFIX + role.message);
            Util.sound(p, 1f, "entity.ender_dragon.growl");
        }
        List<String> murdererNames = murdererNames();
        if (murdererNames.size() > 1) {
            for (Player p : online(alive)) {
                if (roles.get(p.getUniqueId()).isMurderer()) {
                    p.sendMessage(Util.PREFIX + "Dein Team in dieser Runde: " + ChatColor.RED + String.join(", ", murdererNames));
                }
            }
        }
        if (spawns.size() < list.size()) {
            plugin.getLogger().warning("Nur " + spawns.size() + " Spawns für " + list.size() + " Spieler – einige Spieler teilen sich einen Spawn.");
        }
        for (Player p : online(spectators)) {
            p.teleport(spawns.get(0));
        }
        for (Board b : boards.values()) {
            b.setHiddenNames(playerNames(players), true);
        }
        timeLeft = s().gameDuration;
        graceLeft = s().gracePeriod;
        goldTimer = 0;
        if (graceLeft <= 0) {
            giveWeapons();
        } else {
            broadcast(ChatColor.YELLOW + "Die Waffen werden in " + graceLeft + " Sekunden verteilt!");
        }
    }

    private void giveWeapons() {
        for (Player p : online(alive)) {
            Role role = roles.get(p.getUniqueId());
            if (role.weaponType == WeaponType.NONE) continue;
            p.getInventory().setItem(SLOT_WEAPON, item(role.weapon, role.weaponName));
            if (role.weaponType == WeaponType.BOW) {
                bowHolders.put(p.getUniqueId(), role);
                addArrows(p, 1);
            }
            p.sendMessage(Util.PREFIX + role.color + "Du hast deine Waffe erhalten!");
            Util.sound(p, 1f, "entity.experience_orb.pickup");
        }
        broadcast(ChatColor.RED + "Die Waffen wurden verteilt – pass auf!");
    }

    private void kill(Player victim, Player killer, String reason) {
        UUID id = victim.getUniqueId();
        if (state != State.INGAME || !alive.remove(id)) return;
        Role victimRole = roles.get(id);

        plugin.stats().addDeath(id, victim.getName());
        if (killer != null && countsAsKill(killer.getUniqueId(), id)) {
            plugin.stats().addKill(killer.getUniqueId(), killer.getName(), roles.get(killer.getUniqueId()).key);
        }

        Role bow = bowHolders.remove(id);
        if (bow != null) {
            dropBow(victim.getLocation(), bow);
            broadcast(bow.color + (victimRole == bow ? "Ein " + bow.name : "Ein Bogenträger")
                    + " ist gestorben! Der Bogen liegt jetzt auf der Map.");
        }
        Location deathLoc = victim.getLocation();
        prepare(victim, GameMode.SPECTATOR);
        victim.teleport(deathLoc);
        Util.title(victim, ChatColor.RED + "DU BIST TOT!", ChatColor.GRAY + reason);
        victim.sendMessage(Util.PREFIX + ChatColor.RED + reason);
        for (Player p : online(players)) {
            Util.sound(p, 1f, "entity.player.hurt");
        }

        if (victimRole.isMurderer()) {
            if (killer != null && !roles.get(killer.getUniqueId()).isMurderer()) {
                heroName = killer.getName();
            }
            if (countAlive(Team.MURDERER) > 0) {
                broadcast(victimRole.color + "Ein " + victimRole.name + " wurde getötet!");
            }
        }
        checkWin();
    }

    /** Nur echte Kills zählen: Gegner aus einem anderen Team (Neutrale sind für alle Gegner) – keine Teamkills. */
    private boolean countsAsKill(UUID killer, UUID victim) {
        Team k = roles.get(killer).team;
        return k != roles.get(victim).team || k == Team.NEUTRAL;
    }

    private void checkWin() {
        if (state != State.INGAME) return;
        if (countAlive(Team.MURDERER) == 0) {
            end(Winner.INNOCENTS);
        } else if (countAlive(Team.INNOCENT) == 0) { // Neutrale müssen nicht getötet werden
            end(Winner.MURDERER);
        }
    }

    /** Hat der Spieler gewonnen? Neutrale gewinnen, wenn sie das Rundenende überleben. */
    private boolean hasWon(UUID id, Role role, Winner winner) {
        if (winner == Winner.NONE || left.contains(id)) return false;
        return switch (role.team) {
            case MURDERER -> winner == Winner.MURDERER;
            case INNOCENT -> winner == Winner.INNOCENTS;
            case NEUTRAL -> alive.contains(id);
        };
    }

    private void end(Winner winner) {
        if (state != State.INGAME) return;
        state = State.ENDING;
        endCountdown = 10;
        clearEntities();

        Role mainMurderer = s().mainMurderer();
        String title;
        String sub = ChatColor.GRAY + mainMurderer.name + ": " + ChatColor.RED + String.join(", ", murdererNames());
        if (winner == Winner.INNOCENTS) {
            title = ChatColor.GREEN + "UNSCHULDIGE GEWINNEN!";
        } else if (winner == Winner.MURDERER) {
            title = mainMurderer.color + mainMurderer.name.toUpperCase(Locale.GERMAN) + " GEWINNT!";
        } else {
            title = ChatColor.GRAY + "RUNDE BEENDET";
            sub = "";
        }

        // Statistiken
        if (winner != Winner.NONE) {
            for (Map.Entry<UUID, Role> e : roles.entrySet()) {
                UUID id = e.getKey();
                plugin.stats().addGame(id, names.get(id), hasWon(id, e.getValue(), winner));
            }
        }
        List<String> neutralWinners = new ArrayList<>();
        for (Map.Entry<UUID, Role> e : roles.entrySet()) {
            if (e.getValue().team == Team.NEUTRAL && hasWon(e.getKey(), e.getValue(), winner)) {
                neutralWinners.add(e.getValue().color + names.get(e.getKey()));
            }
        }

        // Wer welche Rolle hatte (ohne "innocent")
        Map<Role, List<String>> byRole = new LinkedHashMap<>();
        for (Map.Entry<UUID, Role> e : roles.entrySet()) {
            if (e.getValue() != s().innocent) {
                byRole.computeIfAbsent(e.getValue(), r -> new ArrayList<>()).add(names.get(e.getKey()));
            }
        }

        String line = ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "--------------------------------------";
        for (Player p : online(allMembers())) {
            Util.title(p, title, sub);
            p.sendMessage(line);
            p.sendMessage("         " + title);
            p.sendMessage("");
            for (Map.Entry<Role, List<String>> e : byRole.entrySet()) {
                p.sendMessage(ChatColor.GRAY + "  " + e.getKey().name + ": " + e.getKey().color + String.join(", ", e.getValue()));
            }
            if (heroName != null) {
                p.sendMessage(ChatColor.GRAY + "  Held: " + ChatColor.GOLD + heroName);
            }
            if (!neutralWinners.isEmpty()) {
                p.sendMessage(ChatColor.GRAY + "  Überlebt (neutral, gewinnt auch): " + String.join(ChatColor.GRAY + ", ", neutralWinners));
            }
            p.sendMessage(line);
            Role r = roles.get(p.getUniqueId());
            if (r != null && hasWon(p.getUniqueId(), r, winner)) {
                Util.sound(p, 1f, "entity.player.levelup");
            }
        }
        for (Player p : online(alive)) {
            p.getInventory().clear();
        }
    }

    private void reset() {
        clearEntities();
        state = State.WAITING;
        alive.clear();
        roles.clear();
        names.clear();
        left.clear();
        gold.clear();
        bowHolders.clear();
        newVoteRound();
        List<Player> all = online(allMembers());
        players.clear();
        spectators.clear();
        boards.clear();
        for (Player p : all) {
            join(p);
        }
    }

    /** Nach /mm reload oder Änderungen an den Maps: Abstimmung neu aufsetzen und Abstimmungs-Items verteilen. */
    public void refreshLobby() {
        if (state != State.WAITING && state != State.STARTING) return;
        newVoteRound();
        for (Player p : online(players)) {
            p.getInventory().setItem(4, null);
            giveVoteItem(p);
        }
        updateBoards();
    }

    // ------------------------------------------------------------------ Kampf

    public void onMelee(Player attacker, Player victim) {
        if (!isAlive(attacker) || !isAlive(victim)) return;
        if (isKnife(attacker, attacker.getInventory().getItemInMainHand())) {
            attack(attacker, victim, "erstochen");
        }
    }

    public void onArrowHit(Player shooter, Player victim) {
        if (!isAlive(shooter) || !isAlive(victim) || shooter.equals(victim)) return;
        attack(shooter, victim, "erschossen");
    }

    private void attack(Player attacker, Player victim, String verb) {
        Role a = roles.get(attacker.getUniqueId());
        Role v = roles.get(victim.getUniqueId());
        switch (a.team) {
            case MURDERER -> {
                // Mörder töten alle außer ihren Teamkollegen
                if (!v.isMurderer()) kill(victim, attacker, "Du wurdest von einem " + a.name + " " + verb + "!");
            }
            case NEUTRAL -> kill(victim, attacker, "Du wurdest von einem " + a.name + " " + verb + "!");
            case INNOCENT -> {
                if (v.isMurderer()) {
                    kill(victim, attacker, ChatColor.YELLOW + attacker.getName() + " hat dich " + verb + "!");
                } else if (v.team == Team.NEUTRAL) {
                    kill(victim, attacker, ChatColor.YELLOW + attacker.getName() + " hat dich " + verb + "!");
                    if (s().neutralKillKillsShooter) {
                        kill(attacker, null, "Du hast einen " + v.name + " getötet!");
                    }
                } else {
                    kill(victim, attacker, "Du wurdest von einem Unschuldigen " + verb + "!");
                    if (s().teamKillKillsShooter) {
                        kill(attacker, null, "Du hast einen Unschuldigen getötet!");
                    }
                }
            }
        }
    }

    public void onShoot(Player p) {
        UUID id = p.getUniqueId();
        Role bow = bowHolders.get(id);
        if (!isAlive(p) || bow == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isAlive(p) && bowHolders.containsKey(id) && !p.getInventory().contains(Material.ARROW)) {
                addArrows(p, 1);
            }
        }, Math.max(1, bow.arrowCooldown * 20L));
    }

    /** Hält der Spieler die Messer-Waffe seiner Rolle? */
    public boolean isKnife(Player p, ItemStack it) {
        Role r = roles.get(p.getUniqueId());
        return r != null && r.weaponType == WeaponType.KNIFE && it != null && it.getType() == r.weapon;
    }

    public void throwKnife(Player p) {
        UUID id = p.getUniqueId();
        Role role = roles.get(id);
        if (!isAlive(p) || role == null || role.weaponType != WeaponType.KNIFE || !role.knifeThrow) return;
        long now = System.currentTimeMillis();
        if (knifeReady.getOrDefault(id, 0L) > now) return;
        knifeReady.put(id, now + role.knifeCooldown * 1000L);

        p.getInventory().setItem(SLOT_WEAPON, null);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isAlive(p)) {
                p.getInventory().setItem(SLOT_WEAPON, item(role.weapon, role.weaponName));
            }
        }, Math.max(1, role.knifeCooldown * 20L));

        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        int range = role.knifeRange;
        ArmorStand stand = start.getWorld().spawn(start.clone().subtract(0, 1.4, 0), ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.getEquipment().setItemInMainHand(new ItemStack(role.weapon));
        stand.setRightArmPose(new EulerAngle(Math.toRadians(350), Math.toRadians(-90), Math.toRadians(0)));
        knives.add(stand);
        Util.sound(p, 1.5f, "entity.arrow.shoot");

        BukkitTask[] task = new BukkitTask[1];
        task[0] = new BukkitRunnable() {
            final Location pos = start.clone();
            int steps = 0;

            @Override
            public void run() {
                if (state != State.INGAME || ++steps > range) {
                    stop();
                    return;
                }
                pos.add(dir);
                if (pos.getBlock().getType().isSolid()) {
                    stop();
                    return;
                }
                Location standLoc = pos.clone().subtract(0, 1.4, 0);
                standLoc.setYaw(start.getYaw());
                stand.teleport(standLoc);
                for (Player target : online(alive)) {
                    if (target.equals(p) || !target.getWorld().equals(pos.getWorld())) continue;
                    // Mörder treffen keine Teamkollegen
                    if (role.isMurderer() && roles.get(target.getUniqueId()).isMurderer()) continue;
                    Location body = target.getLocation().add(0, 1, 0);
                    if (body.distanceSquared(pos) < 1.3) {
                        attack(p, target, "mit dem Messer getroffen");
                        stop();
                        return;
                    }
                }
            }

            private void stop() {
                stand.remove();
                knives.remove(stand);
                cancel();
                knifeTasks.remove(task[0]);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        knifeTasks.add(task[0]);
    }

    // ------------------------------------------------------------------ Gold & Bogen

    public boolean isGoldItem(Item item) {
        return goldItems.containsValue(item);
    }

    public void onGoldPickup(Player p, Item item) {
        if (!isAlive(p)) return;
        UUID id = p.getUniqueId();
        Role role = roles.get(id);
        if (!role.canCollectGold) return;
        item.remove();
        goldItems.values().remove(item);

        GoldReward reward = s().goldReward(role);
        int g = gold.getOrDefault(id, 0) + s().goldPerPickup;
        Util.sound(p, 1.5f, "entity.experience_orb.pickup");
        while (reward.enabled() && g >= reward.cost()) {
            g -= reward.cost();
            giveReward(p, role, reward);
        }
        gold.put(id, g);
        p.getInventory().setItem(SLOT_GOLD, g > 0 ? new ItemStack(s().goldMaterial, Math.min(g, 64)) : null);
    }

    /** Gibt dem Spieler das Gold-Paket seiner Rolle aus gold-rewards.yml. */
    private void giveReward(Player p, Role role, GoldReward reward) {
        PlayerInventory inv = p.getInventory();
        for (RewardItem ri : reward.items()) {
            if (ri.once() && inv.contains(ri.material())) continue;
            if (ri.material() == Material.ARROW) {
                addArrows(p, ri.amount());
            } else if ((ri.material() == Material.BOW || ri.material() == Material.CROSSBOW) && inv.getItem(weaponSlot(role)) == null) {
                inv.setItem(weaponSlot(role), item(ri.material(), ri.name()));
            } else {
                ItemStack stack = item(ri.material(), ri.name());
                stack.setAmount(Math.min(ri.amount(), stack.getMaxStackSize()));
                inv.addItem(stack);
            }
        }
        p.addPotionEffects(reward.effects());
        if (!reward.message().isEmpty()) {
            p.sendMessage(Util.PREFIX + reward.message());
        }
        Util.sound(p, 1f, "entity.player.levelup");
    }

    /** Rollen mit eigener Waffe behalten Slot 1 dafür (auch wenn ein Messer gerade geworfen wurde). */
    private int weaponSlot(Role role) {
        return role.weaponType == WeaponType.NONE ? SLOT_WEAPON : SLOT_WEAPON + 1;
    }

    private void spawnGold() {
        if (map == null) return;
        List<Location> spots = map.goldLocations();
        if (spots.isEmpty()) return;
        List<Integer> free = new ArrayList<>();
        int onMap = 0;
        for (int i = 0; i < spots.size(); i++) {
            Item it = goldItems.get(i);
            if (it == null || it.isDead() || !it.isValid()) {
                free.add(i);
            } else {
                onMap++;
            }
        }
        int max = s().goldMaxOnMap > 0 ? s().goldMaxOnMap : spots.size();
        Collections.shuffle(free);
        for (int k = 0; k < s().goldSpawnsPerInterval && k < free.size() && onMap < max; k++, onMap++) {
            int idx = free.get(k);
            Location loc = Util.blockCenter(spots.get(idx)).add(0, 0.3, 0);
            Item it = loc.getWorld().dropItem(loc, new ItemStack(s().goldMaterial));
            it.setVelocity(new Vector(0, 0, 0));
            it.setPickupDelay(0);
            goldItems.put(idx, it);
        }
    }

    private void dropBow(Location loc, Role source) {
        Location l = loc.getBlock().getLocation().add(0.5, 0, 0.5);
        ArmorStand stand = l.getWorld().spawn(l, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.getEquipment().setItemInMainHand(new ItemStack(source.weapon));
        stand.customName(Util.text(source.weaponName.isEmpty() ? source.color + "Bogen" : source.weaponName));
        stand.setCustomNameVisible(true);
        droppedBows.put(stand, source);
    }

    private void addArrows(Player p, int amount) {
        PlayerInventory inv = p.getInventory();
        ItemStack cur = inv.getItem(SLOT_ARROW);
        if (cur != null && cur.getType() == Material.ARROW) {
            cur.setAmount(Math.min(64, cur.getAmount() + amount));
            inv.setItem(SLOT_ARROW, cur);
        } else {
            inv.setItem(SLOT_ARROW, new ItemStack(Material.ARROW, amount));
        }
    }

    private void clearEntities() {
        for (BukkitTask t : new ArrayList<>(knifeTasks)) t.cancel();
        knifeTasks.clear();
        for (ArmorStand st : knives) st.remove();
        knives.clear();
        for (Item it : goldItems.values()) it.remove();
        goldItems.clear();
        for (ArmorStand st : droppedBows.keySet()) st.remove();
        droppedBows.clear();
    }

    // ------------------------------------------------------------------ Scoreboard

    private void updateBoards() {
        for (Map.Entry<UUID, Board> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            List<String> lines = new ArrayList<>();
            lines.add("");
            if (state == State.WAITING || state == State.STARTING) {
                lobbyLines(lines);
            } else {
                gameLines(lines, e.getKey());
            }
            lines.add("");
            lines.add(s().serverName);
            e.getValue().setLines(lines);
        }
    }

    private void lobbyLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Spieler: " + ChatColor.GREEN + players.size() + "/" + s().maxPlayers);
        lines.add("");
        lines.add(state == State.STARTING
                ? ChatColor.WHITE + "Start in: " + ChatColor.GREEN + Math.max(countdown, 0) + "s"
                : ChatColor.WHITE + "Warte auf Spieler...");
        lines.add("");
        if (votingOpen()) {
            lines.add(ChatColor.WHITE + "Map-Abstimmung:");
            for (GameMap m : voteOptions) {
                lines.add(ChatColor.GRAY + " " + m.displayName + ChatColor.GRAY + ": " + ChatColor.YELLOW + votesFor(m));
            }
        } else {
            lines.add(ChatColor.WHITE + "Map: " + ChatColor.YELLOW + nextMapText());
        }
        if (!s().showChances) return;
        int n = Math.max(players.size(), 2);
        List<String> chances = new ArrayList<>();
        for (Role r : s().specialRoles) {
            if (!r.showChance || r.chance <= 0) continue;
            double c = chanceFor(r, n);
            String pct = c < 10 ? String.format(Locale.GERMANY, "%.1f%%", c) : String.format(Locale.GERMANY, "%.0f%%", c);
            chances.add(ChatColor.GRAY + " " + r.coloredName() + ChatColor.GRAY + ": " + ChatColor.WHITE + pct);
        }
        if (!chances.isEmpty()) {
            lines.add("");
            lines.add(ChatColor.WHITE + "Deine Chancen:");
            lines.addAll(chances);
        }
    }

    private void gameLines(List<String> lines, UUID id) {
        lines.add(ChatColor.WHITE + "Rolle: " + roleDisplay(id));
        if (map != null) {
            lines.add(ChatColor.WHITE + "Map: " + ChatColor.YELLOW + map.displayName);
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "Unschuldige übrig: " + ChatColor.GREEN + countAlive(Team.INNOCENT));
        if (murdererNames().size() > 1) {
            lines.add(ChatColor.WHITE + s().mainMurderer().name + " übrig: " + ChatColor.RED + countAlive(Team.MURDERER));
        }
        if (hasTeam(Team.NEUTRAL)) {
            lines.add(ChatColor.WHITE + "Neutrale übrig: " + ChatColor.GRAY + countAlive(Team.NEUTRAL));
        }
        lines.add(ChatColor.WHITE + "Zeit übrig: " + ChatColor.GREEN + Util.formatTime(Math.max(timeLeft, 0)));

        // Status der Bogen-Rollen (z. B. Detektiv)
        boolean header = false;
        for (Role r : s().specialRoles) {
            if (r.team != Team.INNOCENT || r.weaponType != WeaponType.BOW) continue;
            int total = 0;
            int living = 0;
            for (Map.Entry<UUID, Role> e : roles.entrySet()) {
                if (e.getValue() != r) continue;
                total++;
                if (alive.contains(e.getKey())) living++;
            }
            if (total == 0) continue;
            if (!header) {
                lines.add("");
                header = true;
            }
            String status = living > 0 ? ChatColor.GREEN + (total > 1 ? living + "/" + total + " leben" : "Lebt") : ChatColor.RED + "Tot";
            lines.add(ChatColor.WHITE + r.name + ": " + status);
        }
        if (!droppedBows.isEmpty()) {
            lines.add(ChatColor.RED + "Ein Bogen liegt auf der Map!");
        }
        Role own = roles.get(id);
        if (alive.contains(id) && s().goldEnabled && own.canCollectGold) {
            GoldReward reward = s().goldReward(own);
            lines.add("");
            lines.add(ChatColor.WHITE + "Gold: " + ChatColor.GOLD + gold.getOrDefault(id, 0) + (reward.enabled() ? "/" + reward.cost() : ""));
        }
        if (state == State.INGAME && graceLeft > 0) {
            lines.add("");
            lines.add(ChatColor.WHITE + "Waffen in: " + ChatColor.RED + graceLeft + "s");
        }
    }

    private String roleDisplay(UUID id) {
        Role r = roles.get(id);
        if (r == null) return ChatColor.GRAY + "Zuschauer";
        if (!alive.contains(id) && state == State.INGAME) return ChatColor.GRAY + "Tot";
        return r.coloredName();
    }

    // ------------------------------------------------------------------ Helfer

    public State getState() {
        return state;
    }

    public Set<UUID> getAlive() {
        return alive;
    }

    public Location respawnPoint() {
        List<Location> spawns = map == null ? List.of() : map.spawnLocations();
        if (state == State.INGAME && !spawns.isEmpty()) {
            return spawns.get(random.nextInt(spawns.size()));
        }
        return maps.getLobby();
    }

    private int countAlive(Team team) {
        int c = 0;
        for (UUID id : alive) if (roles.get(id).team == team) c++;
        return c;
    }

    private boolean hasTeam(Team team) {
        for (Role r : roles.values()) if (r.team == team) return true;
        return false;
    }

    private List<String> murdererNames() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<UUID, Role> e : roles.entrySet()) {
            if (e.getValue().isMurderer()) list.add(names.get(e.getKey()));
        }
        return list;
    }

    private Set<UUID> allMembers() {
        Set<UUID> all = new HashSet<>(players);
        all.addAll(spectators);
        return all;
    }

    private List<Player> online(Set<UUID> ids) {
        List<Player> list = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) list.add(p);
        }
        return list;
    }

    private List<String> playerNames(Set<UUID> ids) {
        List<String> list = new ArrayList<>();
        for (Player p : online(ids)) list.add(p.getName());
        return list;
    }

    private void broadcast(String msg) {
        for (Player p : online(allMembers())) {
            p.sendMessage(Util.PREFIX + msg);
        }
    }

    private static ItemStack item(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null && !name.isEmpty()) {
            meta.displayName(Util.itemName(name));
            it.setItemMeta(meta);
        }
        return it;
    }

    private void prepare(Player p, GameMode mode) {
        p.getInventory().clear();
        p.setGameMode(mode);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }
    }

    private void resetToServer(Player p) {
        prepare(p, Bukkit.getDefaultGameMode());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
    }
}
