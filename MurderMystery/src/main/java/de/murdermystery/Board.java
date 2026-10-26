package de.murdermystery;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Sidebar-Scoreboard eines Spielers; ändert nur Zeilen, die sich wirklich geändert haben (kein Flackern). */
public class Board {

    private final Scoreboard scoreboard;
    private final Objective objective;
    private final Team hiddenNames;
    private final List<String> current = new ArrayList<>();

    public Board(Player player) {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective("mm", Criteria.DUMMY,
                Util.text(ChatColor.YELLOW + "" + ChatColor.BOLD + "MURDER MYSTERY"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank()); // rote Zahlen rechts ausblenden
        hiddenNames = scoreboard.registerNewTeam("hidden");
        hiddenNames.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        player.setScoreboard(scoreboard);
    }

    /** Namensschilder aller angegebenen Spieler verstecken (wie auf Hypixel). */
    public void setHiddenNames(Collection<String> names, boolean hide) {
        for (String entry : new ArrayList<>(hiddenNames.getEntries())) {
            hiddenNames.removeEntry(entry);
        }
        if (hide) {
            for (String n : names) {
                hiddenNames.addEntry(n);
            }
        }
    }

    public void setLines(List<String> lines) {
        // Zeilen eindeutig machen (doppelte Leerzeilen etc.)
        List<String> unique = new ArrayList<>();
        for (String line : lines) {
            while (unique.contains(line)) {
                line = line + ChatColor.RESET;
            }
            unique.add(line);
        }
        for (String old : current) {
            if (!unique.contains(old)) {
                scoreboard.resetScores(old);
            }
        }
        for (int i = 0; i < unique.size(); i++) {
            objective.getScore(unique.get(i)).setScore(unique.size() - i);
        }
        current.clear();
        current.addAll(unique);
    }
}
