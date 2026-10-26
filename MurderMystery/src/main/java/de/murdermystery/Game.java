package de.murdermystery;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Game {

    public enum State { WAITING, STARTING, INGAME, ENDING }

    public enum Role { MURDERER, DETECTIVE, INNOCENT }

    private enum Winner { INNOCENTS, MURDERER, NONE }

    private static final int SLOT_WEAPON = 1;
    private static final int SLOT_GOLD = 8;
    private static final int SLOT_ARROW = 9;

    private final MurderMysteryPlugin plugin;
    private final Arena arena;
    private final Random random = new Random();

    // Concurrent, weil der Chat-Listener asynchron darauf zugreift
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> left = new HashSet<>();
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Map<UUID, Integer> gold = new HashMap<>();
    private final Map<UUID, Long> knifeReady = new HashMap<>();
    private final Map<Integer, Item> goldItems = new HashMap<>();
    private final List<BukkitTask> knifeTasks = new ArrayList<>();
    private final List<ArmorStand> knives = new ArrayList<>();
    private final Set<UUID> bowHolders = new HashSet<>(); // haben einen Detektiv-Bogen (unendlich Pfeile)
    private final List<ArmorStand> droppedBows = new ArrayList<>();

    private State state = State.WAITING;
    private boolean forceStart;
    private int countdown;
    private int timeLeft;
    private int graceLeft;
    private int goldTimer;
    private int endCountdown;
    private String heroName;

    private BukkitTask ticker;
    private BukkitTask fastTicker;

    public Game(MurderMysteryPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
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
        if (!arena.isReady()) {
            if (p.hasPermission("mm.admin")) {
                p.sendMessage(Util.PREFIX + ChatColor.RED + "Die Arena ist noch nicht eingerichtet. (/mm setlobby, /mm addspawn)");
            }
            return;
        }
        boards.put(p.getUniqueId(), new Board(p));
        if (state == State.INGAME || state == State.ENDING || players.size() >= s().maxPlayers) {
            spectators.add(p.getUniqueId());
            prepare(p, GameMode.SPECTATOR);
            p.teleport(state == State.INGAME ? arena.getSpawns().get(0) : arena.getLobby());
            p.sendMessage(Util.PREFIX + "Eine Runde läuft gerade – du schaust zu und spielst in der nächsten Runde mit.");
            if (state == State.INGAME) {
                boards.get(p.getUniqueId()).setHiddenNames(playerNames(players), true);
            }
            return;
        }
        players.add(p.getUniqueId());
        prepare(p, GameMode.ADVENTURE);
        p.teleport(arena.getLobby());
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

        if (state == State.INGAME && roles.containsKey(id)) {
            left.add(id); // wer mitten in der Runde geht, bekommt eine Niederlage
        }
        if (state == State.INGAME && alive.remove(id)) {
            if (bowHolders.remove(id)) {
                dropBow(p.getLocation());
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
        Location lobby = arena.getLobby();
        if (lobby == null) return;
        if (!p.getWorld().equals(lobby.getWorld()) || p.getLocation().distanceSquared(lobby) > (double) max * max) {
            p.teleport(lobby);
            p.sendMessage(Util.PREFIX + "Bleib in der Lobby, bis die Runde startet!");
        }
    }

    // ------------------------------------------------------------------ Ticks

    private void tick() {
        switch (state) {
            case WAITING -> {
                if (players.size() >= s().minPlayers && arena.isReady()) {
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
        for (ArmorStand bow : new ArrayList<>(droppedBows)) {
            Location bowLoc = bow.getLocation();
            for (Player p : online(alive)) {
                UUID id = p.getUniqueId();
                if (roles.get(id) == Role.MURDERER || bowHolders.contains(id) || !p.getWorld().equals(bowLoc.getWorld())) continue;
                if (p.getLocation().distanceSquared(bowLoc) <= 2.25) {
                    bow.remove();
                    droppedBows.remove(bow);
                    bowHolders.add(id);
                    PlayerInventory inv = p.getInventory();
                    inv.remove(s().detective.weapon);
                    inv.remove(Material.BOW);
                    inv.setItem(SLOT_WEAPON, item(s().detective.weapon, s().detective.weaponName));
                    addArrows(p, 1);
                    Util.title(p, s().detective.color + "Du hast den Bogen!", ChatColor.GRAY + "Finde und erschieße den Mörder");
                    Util.sound(p, 1f, "entity.player.levelup");
                    broadcast(s().detective.color + "Jemand hat den Bogen des " + s().detective.name + "s aufgehoben!");
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Rundenablauf

    private void startGame() {
        List<Player> list = online(players);
        List<Location> spawns = new ArrayList<>(arena.getSpawns());
        if (list.size() < 2 || spawns.isEmpty()) {
            state = State.WAITING;
            return;
        }
        state = State.INGAME;
        forceStart = false;
        Collections.shuffle(list);
        Collections.shuffle(spawns);
        roles.clear();
        names.clear();
        left.clear();
        gold.clear();
        knifeReady.clear();
        alive.clear();
        bowHolders.clear();
        heroName = null;

        int n = list.size();
        int murderers = Math.max(1, Math.min(s().murderer.count(n), n - 1));
        int detectives = Math.max(0, Math.min(s().detective.count(n), n - murderers));

        for (int i = 0; i < n; i++) {
            Player p = list.get(i);
            UUID id = p.getUniqueId();
            Role role = i < murderers ? Role.MURDERER : i < murderers + detectives ? Role.DETECTIVE : Role.INNOCENT;
            roles.put(id, role);
            names.put(id, p.getName());
            alive.add(id);
            gold.put(id, 0);
            prepare(p, GameMode.ADVENTURE);
            // Jeder Spieler bekommt einen eigenen Spawn, solange genug gesetzt sind
            p.teleport(spawns.get(i % spawns.size()));
            Settings.RoleSettings rs = roleSettings(role);
            Util.title(p, rs.title, rs.subtitle);
            p.sendMessage(Util.PREFIX + rs.message);
            Util.sound(p, 1f, "entity.ender_dragon.growl");
        }
        if (murderers > 1) {
            String partners = String.join(", ", namesOf(Role.MURDERER));
            for (Player p : online(alive)) {
                if (roles.get(p.getUniqueId()) == Role.MURDERER) {
                    p.sendMessage(Util.PREFIX + "Die " + s().murderer.coloredName() + ChatColor.GRAY + " in dieser Runde: " + s().murderer.color + partners);
                }
            }
        }
        if (spawns.size() < n) {
            plugin.getLogger().warning("Nur " + spawns.size() + " Spawns für " + n + " Spieler – einige Spieler teilen sich einen Spawn.");
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
            if (role == Role.MURDERER) {
                p.getInventory().setItem(SLOT_WEAPON, item(s().murderer.weapon, s().murderer.weaponName));
                p.sendMessage(Util.PREFIX + s().murderer.color + "Du hast deine Waffe erhalten!");
                Util.sound(p, 1f, "entity.experience_orb.pickup");
            } else if (role == Role.DETECTIVE) {
                bowHolders.add(p.getUniqueId());
                p.getInventory().setItem(SLOT_WEAPON, item(s().detective.weapon, s().detective.weaponName));
                addArrows(p, 1);
                p.sendMessage(Util.PREFIX + s().detective.color + "Du hast deinen Bogen erhalten!");
                Util.sound(p, 1f, "entity.experience_orb.pickup");
            }
        }
        broadcast(s().murderer.color + "Der " + s().murderer.name + " hat seine Waffe erhalten!");
    }

    private void kill(Player victim, Player killer, String reason) {
        UUID id = victim.getUniqueId();
        if (state != State.INGAME || !alive.remove(id)) return;
        Role victimRole = roles.get(id);

        plugin.stats().addDeath(id, victim.getName());
        if (killer != null && countsAsKill(killer.getUniqueId(), id)) {
            plugin.stats().addKill(killer.getUniqueId(), killer.getName(), roles.get(killer.getUniqueId()));
        }

        if (bowHolders.remove(id)) {
            dropBow(victim.getLocation());
            broadcast(s().detective.color + (victimRole == Role.DETECTIVE ? "Ein " + s().detective.name : "Ein Bogenträger")
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

        if (victimRole == Role.MURDERER) {
            if (killer != null && roles.get(killer.getUniqueId()) != Role.MURDERER) {
                heroName = killer.getName();
            }
            if (countAlive(Role.MURDERER) > 0) {
                broadcast(s().murderer.color + "Ein " + s().murderer.name + " wurde getötet!");
            }
        }
        checkWin();
    }

    /** Nur echte Kills zählen: Mörder tötet Nicht-Mörder oder Nicht-Mörder tötet Mörder (keine Teamkills). */
    private boolean countsAsKill(UUID killer, UUID victim) {
        return (roles.get(killer) == Role.MURDERER) != (roles.get(victim) == Role.MURDERER);
    }

    private void checkWin() {
        if (state != State.INGAME) return;
        int murderers = countAlive(Role.MURDERER);
        if (murderers == 0) {
            end(Winner.INNOCENTS);
        } else if (alive.size() - murderers == 0) {
            end(Winner.MURDERER);
        }
    }

    private void end(Winner winner) {
        if (state != State.INGAME) return;
        state = State.ENDING;
        endCountdown = 10;
        clearEntities();

        String murdererList = String.join(", ", namesOf(Role.MURDERER));
        String detectiveList = String.join(", ", namesOf(Role.DETECTIVE));
        String title;
        String sub = ChatColor.GRAY + s().murderer.name + ": " + s().murderer.color + murdererList;
        if (winner == Winner.INNOCENTS) {
            title = ChatColor.GREEN + "UNSCHULDIGE GEWINNEN!";
        } else if (winner == Winner.MURDERER) {
            title = s().murderer.color + s().murderer.name.toUpperCase() + " GEWINNT!";
        } else {
            title = ChatColor.GRAY + "RUNDE BEENDET";
            sub = "";
        }

        // Statistiken
        if (winner != Winner.NONE) {
            for (Map.Entry<UUID, Role> e : roles.entrySet()) {
                UUID id = e.getKey();
                boolean murdererTeam = e.getValue() == Role.MURDERER;
                boolean won = !left.contains(id) && (winner == Winner.MURDERER) == murdererTeam;
                plugin.stats().addGame(id, names.get(id), won);
            }
        }
        plugin.stats().save();

        String line = ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "--------------------------------------";
        for (Player p : online(allMembers())) {
            Util.title(p, title, sub);
            p.sendMessage(line);
            p.sendMessage("         " + title);
            p.sendMessage("");
            p.sendMessage(ChatColor.GRAY + "  " + s().murderer.name + ": " + s().murderer.color + murdererList);
            if (!detectiveList.isEmpty()) {
                p.sendMessage(ChatColor.GRAY + "  " + s().detective.name + ": " + s().detective.color + detectiveList);
            }
            if (heroName != null) {
                p.sendMessage(ChatColor.GRAY + "  Held: " + ChatColor.GOLD + heroName);
            }
            p.sendMessage(line);
            Role r = roles.get(p.getUniqueId());
            if (winner != Winner.NONE && r != null && (winner == Winner.MURDERER) == (r == Role.MURDERER)) {
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
        List<Player> all = online(allMembers());
        players.clear();
        spectators.clear();
        boards.clear();
        for (Player p : all) {
            join(p);
        }
    }

    // ------------------------------------------------------------------ Kampf

    public void onMelee(Player attacker, Player victim) {
        if (!isAlive(attacker) || !isAlive(victim)) return;
        if (roles.get(attacker.getUniqueId()) == Role.MURDERER && roles.get(victim.getUniqueId()) != Role.MURDERER
                && isKnife(attacker.getInventory().getItemInMainHand())) {
            kill(victim, attacker, "Du wurdest vom " + s().murderer.name + " erstochen!");
        }
    }

    public void onArrowHit(Player shooter, Player victim) {
        if (!isAlive(shooter) || !isAlive(victim) || shooter.equals(victim)) return;
        boolean shooterMurderer = roles.get(shooter.getUniqueId()) == Role.MURDERER;
        boolean victimMurderer = roles.get(victim.getUniqueId()) == Role.MURDERER;
        if (shooterMurderer) {
            if (!victimMurderer) kill(victim, shooter, "Du wurdest vom " + s().murderer.name + " erschossen!");
        } else if (victimMurderer) {
            kill(victim, shooter, ChatColor.YELLOW + shooter.getName() + " hat dich erschossen!");
        } else {
            kill(victim, shooter, "Du wurdest von einem Unschuldigen erschossen!");
            if (s().teamKillKillsShooter) {
                kill(shooter, null, "Du hast einen Unschuldigen erschossen!");
            }
        }
    }

    public void onShoot(Player p) {
        UUID id = p.getUniqueId();
        if (!isAlive(p) || !bowHolders.contains(id)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isAlive(p) && bowHolders.contains(id) && !p.getInventory().contains(Material.ARROW)) {
                addArrows(p, 1);
            }
        }, s().arrowCooldown * 20L);
    }

    public boolean isKnife(ItemStack it) {
        return it != null && it.getType() == s().murderer.weapon;
    }

    public void throwKnife(Player p) {
        UUID id = p.getUniqueId();
        if (!s().knifeThrow || !isAlive(p) || roles.get(id) != Role.MURDERER) return;
        long now = System.currentTimeMillis();
        if (knifeReady.getOrDefault(id, 0L) > now) return;
        int cd = s().knifeCooldown;
        knifeReady.put(id, now + cd * 1000L);

        p.getInventory().setItem(SLOT_WEAPON, null);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isAlive(p) && roles.get(id) == Role.MURDERER) {
                p.getInventory().setItem(SLOT_WEAPON, item(s().murderer.weapon, s().murderer.weaponName));
            }
        }, Math.max(1, cd * 20L));

        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        int range = s().knifeRange;
        ArmorStand stand = start.getWorld().spawn(start.clone().subtract(0, 1.4, 0), ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.getEquipment().setItemInMainHand(new ItemStack(s().murderer.weapon));
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
                    if (target.equals(p) || roles.get(target.getUniqueId()) == Role.MURDERER
                            || !target.getWorld().equals(pos.getWorld())) continue;
                    Location body = target.getLocation().add(0, 1, 0);
                    if (body.distanceSquared(pos) < 1.3) {
                        kill(target, p, "Du wurdest vom Messer des " + s().murderer.name + "s getroffen!");
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
        if (!roleSettings(roles.get(id)).canCollectGold) return;
        item.remove();
        goldItems.values().remove(item);

        int forBow = s().goldForBow;
        int g = gold.getOrDefault(id, 0) + s().goldPerPickup;
        Util.sound(p, 1.5f, "entity.experience_orb.pickup");
        while (g >= forBow) {
            g -= forBow;
            PlayerInventory inv = p.getInventory();
            if (!inv.contains(Material.BOW) && !inv.contains(Material.CROSSBOW)) {
                // Der Mörder behält Slot 1 für seine Waffe (auch wenn sie gerade geworfen wurde)
                int slot = roles.get(id) == Role.MURDERER ? SLOT_WEAPON + 1 : SLOT_WEAPON;
                inv.setItem(slot, item(Material.BOW, s().goldBowName));
                p.sendMessage(Util.PREFIX + ChatColor.GOLD + "Du hast genug Gold gesammelt und einen Bogen erhalten!");
            } else {
                p.sendMessage(Util.PREFIX + ChatColor.GOLD + "Du hast zusätzliche Pfeile erhalten!");
            }
            addArrows(p, s().arrowsPerBow);
            Util.sound(p, 1f, "entity.player.levelup");
        }
        gold.put(id, g);
        p.getInventory().setItem(SLOT_GOLD, g > 0 ? new ItemStack(s().goldMaterial, Math.min(g, 64)) : null);
    }

    private void spawnGold() {
        List<Location> spots = arena.getGoldSpawns();
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

    private void dropBow(Location loc) {
        Location l = loc.getBlock().getLocation().add(0.5, 0, 0.5);
        ArmorStand stand = l.getWorld().spawn(l, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.getEquipment().setItemInMainHand(new ItemStack(s().detective.weapon));
        stand.customName(Util.text(s().detective.weaponName));
        stand.setCustomNameVisible(true);
        droppedBows.add(stand);
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
        for (ArmorStand st : droppedBows) st.remove();
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
                lines.add(ChatColor.WHITE + "Spieler: " + ChatColor.GREEN + players.size() + "/" + s().maxPlayers);
                lines.add("");
                lines.add(state == State.STARTING
                        ? ChatColor.WHITE + "Start in: " + ChatColor.GREEN + Math.max(countdown, 0) + "s"
                        : ChatColor.WHITE + "Warte auf Spieler...");
            } else {
                UUID id = e.getKey();
                lines.add(ChatColor.WHITE + "Rolle: " + roleDisplay(id));
                lines.add("");
                int murderersAlive = countAlive(Role.MURDERER);
                lines.add(ChatColor.WHITE + "Unschuldige übrig: " + ChatColor.GREEN + (alive.size() - murderersAlive));
                if (count(Role.MURDERER) > 1) {
                    lines.add(ChatColor.WHITE + s().murderer.name + " übrig: " + s().murderer.color + murderersAlive);
                }
                lines.add(ChatColor.WHITE + "Zeit übrig: " + ChatColor.GREEN + Util.formatTime(Math.max(timeLeft, 0)));
                int detTotal = count(Role.DETECTIVE);
                if (detTotal > 0) {
                    int detAlive = countAlive(Role.DETECTIVE);
                    String det;
                    if (detAlive > 0) det = ChatColor.GREEN + (detTotal > 1 ? detAlive + "/" + detTotal + " leben" : "Lebt");
                    else if (!droppedBows.isEmpty()) det = ChatColor.RED + "Bogen liegt!";
                    else det = ChatColor.RED + "Tot";
                    lines.add("");
                    lines.add(ChatColor.WHITE + s().detective.name + ": " + det);
                }
                if (alive.contains(id) && s().goldEnabled) {
                    lines.add("");
                    lines.add(ChatColor.WHITE + "Gold: " + ChatColor.GOLD + gold.getOrDefault(id, 0) + "/" + s().goldForBow);
                }
                if (state == State.INGAME && graceLeft > 0) {
                    lines.add("");
                    lines.add(ChatColor.WHITE + "Waffen in: " + ChatColor.RED + graceLeft + "s");
                }
            }
            lines.add("");
            lines.add(s().serverName);
            e.getValue().setLines(lines);
        }
    }

    private String roleDisplay(UUID id) {
        Role r = roles.get(id);
        if (r == null) return ChatColor.GRAY + "Zuschauer";
        if (!alive.contains(id) && state == State.INGAME) return ChatColor.GRAY + "Tot";
        return roleSettings(r).coloredName();
    }

    // ------------------------------------------------------------------ Helfer

    public State getState() {
        return state;
    }

    public Set<UUID> getAlive() {
        return alive;
    }

    public Location respawnPoint() {
        List<Location> spawns = arena.getSpawns();
        if (state == State.INGAME && !spawns.isEmpty()) {
            return spawns.get(random.nextInt(spawns.size()));
        }
        return arena.getLobby();
    }

    private Settings.RoleSettings roleSettings(Role r) {
        return switch (r) {
            case MURDERER -> s().murderer;
            case DETECTIVE -> s().detective;
            case INNOCENT -> s().innocent;
        };
    }

    private int count(Role role) {
        int c = 0;
        for (Role r : roles.values()) if (r == role) c++;
        return c;
    }

    private int countAlive(Role role) {
        int c = 0;
        for (UUID id : alive) if (roles.get(id) == role) c++;
        return c;
    }

    private List<String> namesOf(Role role) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<UUID, Role> e : roles.entrySet()) {
            if (e.getValue() == role) list.add(names.get(e.getKey()));
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
