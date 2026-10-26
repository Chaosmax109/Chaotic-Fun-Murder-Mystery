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

    private static final String KNIFE_NAME = ChatColor.RED + "Messer";
    private static final String DETECTIVE_BOW_NAME = ChatColor.AQUA + "Bogen des Detektivs";
    private static final String BOW_NAME = ChatColor.YELLOW + "Bogen";
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
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Map<UUID, Integer> gold = new HashMap<>();
    private final Map<UUID, Long> knifeReady = new HashMap<>();
    private final Map<Integer, Item> goldItems = new HashMap<>();
    private final List<BukkitTask> knifeTasks = new ArrayList<>();
    private final List<ArmorStand> knives = new ArrayList<>();

    private State state = State.WAITING;
    private boolean forceStart;
    private int countdown;
    private int timeLeft;
    private int graceLeft;
    private int goldTimer;
    private int endCountdown;

    private UUID murderer;
    private UUID detective;
    private UUID bowHolder; // hat den Detektiv-Bogen (unendlich Pfeile)
    private ArmorStand droppedBow;
    private String murdererName = "-";
    private String detectiveName = "-";
    private String heroName;

    private BukkitTask ticker;
    private BukkitTask fastTicker;

    public Game(MurderMysteryPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
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
            p.sendMessage(Util.PREFIX + ChatColor.RED + "Die Arena ist noch nicht eingerichtet. (/mm setlobby, /mm addspawn)");
            return;
        }
        boards.put(p.getUniqueId(), new Board(p));
        if (state == State.INGAME || state == State.ENDING || players.size() >= maxPlayers()) {
            spectators.add(p.getUniqueId());
            prepare(p, GameMode.SPECTATOR);
            p.teleport(state == State.INGAME ? arena.getSpawns().get(0) : arena.getLobby());
            p.sendMessage(Util.PREFIX + "Eine Runde läuft gerade – du schaust zu und spielst in der nächsten Runde mit.");
            if (state == State.INGAME) {
                boards.get(p.getUniqueId()).setHiddenNames(names(players), true);
            }
            return;
        }
        players.add(p.getUniqueId());
        prepare(p, GameMode.ADVENTURE);
        p.teleport(arena.getLobby());
        broadcast(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " ist beigetreten ("
                + ChatColor.GREEN + players.size() + ChatColor.GRAY + "/" + ChatColor.GREEN + maxPlayers() + ChatColor.GRAY + ")");
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

        if (state == State.INGAME && alive.remove(id)) {
            if (id.equals(bowHolder)) {
                bowHolder = null;
                dropBow(p.getLocation());
            }
            broadcast(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " hat das Spiel verlassen.");
            if (id.equals(murderer)) {
                broadcast(ChatColor.RED + "Der Mörder hat das Spiel verlassen!");
                end(Winner.INNOCENTS);
            } else {
                checkWin();
            }
        } else if (wasPlaying && state != State.INGAME) {
            broadcast(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " hat das Spiel verlassen ("
                    + ChatColor.GREEN + players.size() + ChatColor.GRAY + "/" + ChatColor.GREEN + maxPlayers() + ChatColor.GRAY + ")");
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

    // ------------------------------------------------------------------ Ticks

    private void tick() {
        switch (state) {
            case WAITING:
                if (players.size() >= minPlayers() && arena.isReady()) {
                    state = State.STARTING;
                    countdown = plugin.getConfig().getInt("lobby-countdown", 30);
                }
                break;
            case STARTING:
                if (players.size() < (forceStart ? 2 : minPlayers())) {
                    state = State.WAITING;
                    forceStart = false;
                    broadcast(ChatColor.RED + "Zu wenige Spieler – Countdown abgebrochen.");
                    break;
                }
                if (players.size() >= maxPlayers() && countdown > 10) {
                    countdown = 10;
                }
                if (countdown <= 0) {
                    startGame();
                    break;
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
                break;
            case INGAME:
                tickGame();
                break;
            case ENDING:
                if (--endCountdown <= 0) {
                    reset();
                }
                break;
        }
        updateBoards();
    }

    private void tickGame() {
        if (graceLeft > 0) {
            graceLeft--;
            if (graceLeft == 0) {
                giveWeapons();
            }
        }
        if (++goldTimer >= plugin.getConfig().getInt("gold-interval", 8)) {
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
        if (state != State.INGAME || droppedBow == null) return;
        Location bowLoc = droppedBow.getLocation();
        for (Player p : online(alive)) {
            if (p.getUniqueId().equals(murderer) || !p.getWorld().equals(bowLoc.getWorld())) continue;
            if (p.getLocation().distanceSquared(bowLoc) <= 2.25) {
                droppedBow.remove();
                droppedBow = null;
                bowHolder = p.getUniqueId();
                PlayerInventory inv = p.getInventory();
                inv.remove(Material.BOW);
                inv.setItem(SLOT_WEAPON, item(Material.BOW, DETECTIVE_BOW_NAME));
                addArrow(p);
                Util.title(p, ChatColor.AQUA + "Du hast den Bogen!", ChatColor.GRAY + "Finde und erschieße den Mörder");
                Util.sound(p, 1f, "entity.player.levelup");
                broadcast(ChatColor.AQUA + "Jemand hat den Bogen des Detektivs aufgehoben!");
                return;
            }
        }
    }

    // ------------------------------------------------------------------ Rundenablauf

    private void startGame() {
        List<Player> list = online(players);
        if (list.size() < 2 || arena.getSpawns().isEmpty()) {
            state = State.WAITING;
            return;
        }
        state = State.INGAME;
        forceStart = false;
        Collections.shuffle(list);
        roles.clear();
        gold.clear();
        knifeReady.clear();
        alive.clear();
        heroName = null;
        bowHolder = null;

        murderer = list.get(0).getUniqueId();
        detective = list.get(1).getUniqueId();
        murdererName = list.get(0).getName();
        detectiveName = list.get(1).getName();

        List<Location> spawns = new ArrayList<>(arena.getSpawns());
        Collections.shuffle(spawns);
        for (int i = 0; i < list.size(); i++) {
            Player p = list.get(i);
            UUID id = p.getUniqueId();
            Role role = id.equals(murderer) ? Role.MURDERER : id.equals(detective) ? Role.DETECTIVE : Role.INNOCENT;
            roles.put(id, role);
            alive.add(id);
            gold.put(id, 0);
            prepare(p, GameMode.ADVENTURE);
            p.teleport(spawns.get(i % spawns.size()));
            switch (role) {
                case MURDERER:
                    Util.title(p, ChatColor.RED + "MÖRDER", ChatColor.GRAY + "Töte alle Spieler!");
                    p.sendMessage(Util.PREFIX + "Du bist der " + ChatColor.RED + "Mörder" + ChatColor.GRAY
                            + "! Töte alle, ohne erwischt zu werden. Rechtsklick mit dem Messer wirft es.");
                    break;
                case DETECTIVE:
                    Util.title(p, ChatColor.AQUA + "DETEKTIV", ChatColor.GRAY + "Finde und erschieße den Mörder!");
                    p.sendMessage(Util.PREFIX + "Du bist der " + ChatColor.AQUA + "Detektiv" + ChatColor.GRAY
                            + "! Du hast einen Bogen mit unendlich Pfeilen. Erschieße den Mörder!");
                    break;
                default:
                    Util.title(p, ChatColor.GREEN + "UNSCHULDIG", ChatColor.GRAY + "Überlebe so lange wie möglich!");
                    p.sendMessage(Util.PREFIX + "Du bist " + ChatColor.GREEN + "unschuldig" + ChatColor.GRAY
                            + "! Sammle " + goldForBow() + " Gold für einen Bogen und überlebe.");
            }
            Util.sound(p, 1f, "entity.ender_dragon.growl");
        }
        for (Player p : online(spectators)) {
            p.teleport(spawns.get(0));
        }
        for (Board b : boards.values()) {
            b.setHiddenNames(names(players), true);
        }
        timeLeft = plugin.getConfig().getInt("game-duration", 270);
        graceLeft = plugin.getConfig().getInt("grace-period", 10);
        goldTimer = 0;
        broadcast(ChatColor.YELLOW + "Die Waffen werden in " + graceLeft + " Sekunden verteilt!");
        if (graceLeft <= 0) {
            giveWeapons();
        }
    }

    private void giveWeapons() {
        Player m = Bukkit.getPlayer(murderer);
        if (m != null && alive.contains(murderer)) {
            m.getInventory().setItem(SLOT_WEAPON, item(Material.IRON_SWORD, KNIFE_NAME));
            m.sendMessage(Util.PREFIX + ChatColor.RED + "Du hast dein Messer erhalten!");
            Util.sound(m, 1f, "entity.experience_orb.pickup");
        }
        Player d = Bukkit.getPlayer(detective);
        if (d != null && alive.contains(detective)) {
            bowHolder = detective;
            d.getInventory().setItem(SLOT_WEAPON, item(Material.BOW, DETECTIVE_BOW_NAME));
            addArrow(d);
            d.sendMessage(Util.PREFIX + ChatColor.AQUA + "Du hast deinen Bogen erhalten!");
            Util.sound(d, 1f, "entity.experience_orb.pickup");
        }
        broadcast(ChatColor.RED + "Der Mörder hat sein Messer erhalten!");
    }

    private void kill(Player victim, Player killer, String reason) {
        UUID id = victim.getUniqueId();
        if (state != State.INGAME || !alive.remove(id)) return;

        if (id.equals(bowHolder)) {
            bowHolder = null;
            dropBow(victim.getLocation());
            broadcast(ChatColor.AQUA + (roles.get(id) == Role.DETECTIVE ? "Der Detektiv" : "Der Bogenträger")
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

        if (id.equals(murderer)) {
            if (killer != null && !killer.getUniqueId().equals(murderer)) {
                heroName = killer.getName();
            }
            end(Winner.INNOCENTS);
        } else {
            checkWin();
        }
    }

    private void checkWin() {
        if (state != State.INGAME) return;
        if (!alive.contains(murderer)) {
            end(Winner.INNOCENTS);
            return;
        }
        for (UUID id : alive) {
            if (!id.equals(murderer)) return;
        }
        end(Winner.MURDERER);
    }

    private void end(Winner winner) {
        if (state != State.INGAME) return;
        state = State.ENDING;
        endCountdown = 10;
        clearEntities();

        String title;
        String sub;
        if (winner == Winner.INNOCENTS) {
            title = ChatColor.GREEN + "UNSCHULDIGE GEWINNEN!";
            sub = ChatColor.GRAY + "Der Mörder war " + ChatColor.RED + murdererName;
        } else if (winner == Winner.MURDERER) {
            title = ChatColor.RED + "DER MÖRDER GEWINNT!";
            sub = ChatColor.GRAY + "Der Mörder war " + ChatColor.RED + murdererName;
        } else {
            title = ChatColor.GRAY + "RUNDE BEENDET";
            sub = "";
        }
        String line = ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "--------------------------------------";
        for (Player p : online(allMembers())) {
            Util.title(p, title, sub);
            p.sendMessage(line);
            p.sendMessage("         " + title);
            p.sendMessage("");
            p.sendMessage(ChatColor.GRAY + "  Mörder: " + ChatColor.RED + murdererName);
            p.sendMessage(ChatColor.GRAY + "  Detektiv: " + ChatColor.AQUA + detectiveName);
            if (heroName != null) {
                p.sendMessage(ChatColor.GRAY + "  Held: " + ChatColor.GOLD + heroName);
            }
            p.sendMessage(line);
            boolean won = (winner == Winner.MURDERER) == p.getUniqueId().equals(murderer) && players.contains(p.getUniqueId());
            if (winner != Winner.NONE && won) {
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
        gold.clear();
        murderer = null;
        detective = null;
        bowHolder = null;
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
        if (attacker.getUniqueId().equals(murderer) && isKnife(attacker.getInventory().getItemInMainHand())) {
            kill(victim, attacker, "Du wurdest vom Mörder erstochen!");
        }
    }

    public void onArrowHit(Player shooter, Player victim) {
        if (!isAlive(shooter) || !isAlive(victim) || shooter.equals(victim)) return;
        if (shooter.getUniqueId().equals(murderer)) {
            kill(victim, shooter, "Du wurdest vom Mörder erschossen!");
        } else if (victim.getUniqueId().equals(murderer)) {
            kill(victim, shooter, ChatColor.YELLOW + shooter.getName() + " hat dich erschossen!");
        } else {
            kill(victim, shooter, "Du wurdest von einem Unschuldigen erschossen!");
            kill(shooter, null, "Du hast einen Unschuldigen erschossen!");
        }
    }

    public void onShoot(Player p) {
        UUID id = p.getUniqueId();
        if (!isAlive(p) || !id.equals(bowHolder)) return;
        int cd = plugin.getConfig().getInt("arrow-cooldown", 5);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isAlive(p) && id.equals(bowHolder) && !p.getInventory().contains(Material.ARROW)) {
                addArrow(p);
            }
        }, cd * 20L);
    }

    public void throwKnife(Player p) {
        UUID id = p.getUniqueId();
        if (!isAlive(p) || !id.equals(murderer)) return;
        long now = System.currentTimeMillis();
        if (knifeReady.getOrDefault(id, 0L) > now) return;
        int cd = plugin.getConfig().getInt("knife-cooldown", 5);
        knifeReady.put(id, now + cd * 1000L);

        p.getInventory().setItem(SLOT_WEAPON, null);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isAlive(p) && id.equals(murderer)) {
                p.getInventory().setItem(SLOT_WEAPON, item(Material.IRON_SWORD, KNIFE_NAME));
            }
        }, cd * 20L);

        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        ArmorStand stand = start.getWorld().spawn(start.clone().subtract(0, 1.4, 0), ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        stand.setRightArmPose(new EulerAngle(Math.toRadians(350), Math.toRadians(-90), Math.toRadians(0)));
        knives.add(stand);
        Util.sound(p, 1.5f, "entity.arrow.shoot");

        BukkitTask[] task = new BukkitTask[1];
        task[0] = new BukkitRunnable() {
            final Location pos = start.clone();
            int steps = 0;

            @Override
            public void run() {
                if (state != State.INGAME || ++steps > 40) {
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
                    Location body = target.getLocation().add(0, 1, 0);
                    if (body.distanceSquared(pos) < 1.3) {
                        kill(target, p, "Du wurdest vom Messer des Mörders getroffen!");
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
        item.remove();
        goldItems.values().remove(item);
        if (!isAlive(p)) return;
        UUID id = p.getUniqueId();
        int g = gold.getOrDefault(id, 0) + 1;
        Util.sound(p, 1.5f, "entity.experience_orb.pickup");
        if (g >= goldForBow()) {
            g -= goldForBow();
            PlayerInventory inv = p.getInventory();
            if (!inv.contains(Material.BOW)) {
                // Der Mörder behält Slot 1 für sein Messer (auch wenn es gerade geworfen wurde)
                int slot = id.equals(murderer) ? SLOT_WEAPON + 1 : SLOT_WEAPON;
                inv.setItem(slot, item(Material.BOW, BOW_NAME));
                p.sendMessage(Util.PREFIX + ChatColor.GOLD + "Du hast genug Gold gesammelt und einen Bogen erhalten!");
            } else {
                p.sendMessage(Util.PREFIX + ChatColor.GOLD + "Du hast einen zusätzlichen Pfeil erhalten!");
            }
            addArrow(p);
            Util.sound(p, 1f, "entity.player.levelup");
        }
        gold.put(id, g);
        p.getInventory().setItem(SLOT_GOLD, g > 0 ? new ItemStack(Material.GOLD_INGOT, g) : null);
    }

    private void spawnGold() {
        List<Location> spots = arena.getGoldSpawns();
        if (spots.isEmpty()) return;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            Item it = goldItems.get(i);
            if (it == null || it.isDead() || !it.isValid()) {
                free.add(i);
            }
        }
        if (free.isEmpty()) return;
        int idx = free.get(random.nextInt(free.size()));
        Location loc = Util.blockCenter(spots.get(idx)).add(0, 0.3, 0);
        Item it = loc.getWorld().dropItem(loc, new ItemStack(Material.GOLD_INGOT));
        it.setVelocity(new Vector(0, 0, 0));
        it.setPickupDelay(0);
        goldItems.put(idx, it);
    }

    private void dropBow(Location loc) {
        if (droppedBow != null) droppedBow.remove();
        Location l = loc.getBlock().getLocation().add(0.5, 0, 0.5);
        ArmorStand stand = l.getWorld().spawn(l, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        stand.customName(Util.text(DETECTIVE_BOW_NAME));
        stand.setCustomNameVisible(true);
        droppedBow = stand;
    }

    private void addArrow(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack cur = inv.getItem(SLOT_ARROW);
        if (cur != null && cur.getType() == Material.ARROW) {
            cur.setAmount(cur.getAmount() + 1);
            inv.setItem(SLOT_ARROW, cur);
        } else {
            inv.setItem(SLOT_ARROW, new ItemStack(Material.ARROW));
        }
    }

    private void clearEntities() {
        for (BukkitTask t : new ArrayList<>(knifeTasks)) t.cancel();
        knifeTasks.clear();
        for (ArmorStand s : knives) s.remove();
        knives.clear();
        for (Item it : goldItems.values()) it.remove();
        goldItems.clear();
        if (droppedBow != null) {
            droppedBow.remove();
            droppedBow = null;
        }
    }

    // ------------------------------------------------------------------ Scoreboard

    private void updateBoards() {
        String server = Util.color(plugin.getConfig().getString("server-name", "&edein-server.de"));
        for (Map.Entry<UUID, Board> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            List<String> lines = new ArrayList<>();
            lines.add("");
            if (state == State.WAITING || state == State.STARTING) {
                lines.add(ChatColor.WHITE + "Spieler: " + ChatColor.GREEN + players.size() + "/" + maxPlayers());
                lines.add("");
                lines.add(state == State.STARTING
                        ? ChatColor.WHITE + "Start in: " + ChatColor.GREEN + Math.max(countdown, 0) + "s"
                        : ChatColor.WHITE + "Warte auf Spieler...");
            } else {
                UUID id = e.getKey();
                lines.add(ChatColor.WHITE + "Rolle: " + roleDisplay(id));
                lines.add("");
                int innocents = 0;
                for (UUID a : alive) if (!a.equals(murderer)) innocents++;
                lines.add(ChatColor.WHITE + "Unschuldige übrig: " + ChatColor.GREEN + innocents);
                lines.add(ChatColor.WHITE + "Zeit übrig: " + ChatColor.GREEN + Util.formatTime(Math.max(timeLeft, 0)));
                lines.add("");
                String det;
                if (alive.contains(detective)) det = ChatColor.GREEN + "Lebt";
                else if (droppedBow != null) det = ChatColor.RED + "Bogen liegt!";
                else det = ChatColor.RED + "Tot";
                lines.add(ChatColor.WHITE + "Detektiv: " + det);
                if (alive.contains(id)) {
                    lines.add("");
                    lines.add(ChatColor.WHITE + "Gold: " + ChatColor.GOLD + gold.getOrDefault(id, 0) + "/" + goldForBow());
                }
                if (state == State.INGAME && graceLeft > 0) {
                    lines.add("");
                    lines.add(ChatColor.WHITE + "Waffen in: " + ChatColor.RED + graceLeft + "s");
                }
            }
            lines.add("");
            lines.add(server);
            e.getValue().setLines(lines);
        }
    }

    private String roleDisplay(UUID id) {
        Role r = roles.get(id);
        if (r == null) return ChatColor.GRAY + "Zuschauer";
        if (!alive.contains(id) && state == State.INGAME) return ChatColor.GRAY + "Tot";
        switch (r) {
            case MURDERER: return ChatColor.RED + "Mörder";
            case DETECTIVE: return ChatColor.AQUA + "Detektiv";
            default: return ChatColor.GREEN + "Unschuldig";
        }
    }

    // ------------------------------------------------------------------ Helfer

    public State getState() {
        return state;
    }

    public Set<UUID> getAlive() {
        return alive;
    }

    public Location respawnPoint() {
        if (state == State.INGAME && !arena.getSpawns().isEmpty()) {
            return arena.getSpawns().get(random.nextInt(arena.getSpawns().size()));
        }
        return arena.getLobby();
    }

    private boolean isKnife(ItemStack it) {
        return it != null && it.getType() == Material.IRON_SWORD;
    }

    private int minPlayers() {
        return Math.max(2, plugin.getConfig().getInt("min-players", 4));
    }

    private int maxPlayers() {
        return plugin.getConfig().getInt("max-players", 16);
    }

    private int goldForBow() {
        return Math.max(1, plugin.getConfig().getInt("gold-for-bow", 10));
    }

    private Set<UUID> allMembers() {
        Set<UUID> all = ConcurrentHashMap.newKeySet();
        all.addAll(players);
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

    private List<String> names(Set<UUID> ids) {
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
        meta.displayName(Util.itemName(name));
        it.setItemMeta(meta);
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
