package de.murdermystery;

import de.murdermystery.MapManager.GameMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Nimmt neue Maps aus {@code plugins/MurderMystery/arenas/} auf:
 * <ul>
 *   <li>ZIP-Dateien werden in einen gleichnamigen Welt-Ordner entpackt (die ZIP wandert danach nach arenas/importiert/).</li>
 *   <li>Für jeden Welt-Ordner, den noch keine Arena-Datei benutzt, wird {@code <name>.yml} angelegt.</li>
 * </ul>
 * Danach wird die Welt installiert und geladen – es fehlen nur noch die Spawns.
 */
public class MapImporter {

    private static final String DONE_DIR = "importiert";

    private final MurderMysteryPlugin plugin;
    private final MapManager maps;
    private boolean running;

    public MapImporter(MurderMysteryPlugin plugin, MapManager maps) {
        this.plugin = plugin;
        this.maps = maps;
    }

    private File dir() {
        return maps.arenasDir();
    }

    /** Legt den arenas-Ordner mit einer kurzen Anleitung an. */
    public void prepareFolder() {
        if (!dir().isDirectory() && !dir().mkdirs()) return;
        File readme = new File(dir(), "LIESMICH.txt");
        if (readme.exists()) return;
        try {
            Files.writeString(readme.toPath(), String.join(System.lineSeparator(),
                    "Maps (Arenen)",
                    "=============",
                    "Jede Map besteht aus einer .yml-Datei hier im Ordner und ihrer Welt:",
                    "  arenas/Dam/          <- Welt-Ordner (mit level.dat) – oder Dam.zip",
                    "  arenas/dam.yml       <- Einstellungen: Name, world: Dam, Lobby, Spielbereich, Spawns, Gold",
                    "",
                    "Neue Map: Welt-Ordner oder ZIP hier hochladen (FTP / Dateimanager deines Hosters).",
                    "Beim nächsten Serverstart, mit /mm map import oder im Menü /mm maps wird die ZIP entpackt",
                    "und automatisch eine .yml angelegt. Danach im Spiel /mm map tp <name> und /mm addspawn, /mm addgold.",
                    "",
                    "In der .yml kann statt eines Welt-Ordners auch eine vorhandene Server-Welt stehen (z. B. world: world).",
                    "Ein Welt-Ordner wird beim ersten Mal als Server-Welt mm_<name> kopiert. Änderst du danach den Ordner,",
                    "übernimmt /mm map reinstall <name> die neue Version.",
                    ""), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** ZIP-Dateien und Welt-Ordner ohne eigene Arena-Datei. */
    public List<File> pending() {
        List<File> list = new ArrayList<>();
        File[] files = dir().listFiles();
        if (files == null) return list;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) list.add(f);
            else if (f.isDirectory() && !f.getName().equals(DONE_DIR) && !f.getName().startsWith(".")
                    && MapManager.templateRoot(f) != null && !referenced(f.getName())) list.add(f);
        }
        return list;
    }

    private boolean referenced(String folder) {
        for (GameMap m : maps.all().values()) {
            if (folder.equalsIgnoreCase(m.worldSetting)) return true;
        }
        return false;
    }

    /**
     * Nimmt alle neuen ZIPs und Welt-Ordner auf. Entpacken läuft im Hintergrund, Anlegen und Laden danach auf dem
     * Server-Thread. {@code sender} bekommt die Rückmeldungen (darf null sein).
     */
    public void importAll(CommandSender sender) {
        List<File> todo = pending();
        if (todo.isEmpty()) {
            if (sender != null) tell(sender, ChatColor.GRAY + "Keine neuen Maps in plugins/MurderMystery/arenas/ gefunden.");
            return;
        }
        if (running) {
            tell(sender, ChatColor.RED + "Es läuft bereits ein Import.");
            return;
        }
        running = true;
        tell(sender, "Neue Maps gefunden: " + todo.size() + " – wird eingerichtet …");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> folders = new ArrayList<>();
            for (File f : todo) {
                if (f.isDirectory()) {
                    folders.add(f.getName());
                    continue;
                }
                String folder = f.getName().replaceFirst("(?i)\\.zip$", "");
                File target = new File(dir(), folder);
                try {
                    if (target.exists()) throw new IOException("es gibt schon einen Ordner arenas/" + folder);
                    unzipWorld(f.toPath(), target.toPath());
                    File done = new File(dir(), DONE_DIR);
                    done.mkdirs();
                    Files.move(f.toPath(), new File(done, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    folders.add(folder);
                } catch (IOException e) {
                    tellSync(sender, ChatColor.RED + "'" + f.getName() + "' konnte nicht entpackt werden: " + e.getMessage());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String folder : folders) register(sender, folder);
                running = false;
                plugin.game().refreshLobby();
            });
        });
    }

    /** Legt die Arena-Datei an, installiert die Welt und lädt sie. */
    private void register(CommandSender sender, String folder) {
        if (referenced(folder)) return;
        String key = MapManager.keyOf(folder);
        while (maps.get(key) != null || new File(dir(), key + ".yml").exists()) key = key + "_2";
        GameMap m = maps.create(key, folder);
        m.displayName = folder;
        maps.saveMap(m);
        maps.installTemplate(m);
        if (maps.loadWorld(m.world) == null) {
            tell(sender, ChatColor.RED + "Map '" + key + "' angelegt, aber die Welt aus arenas/" + folder + " ließ sich nicht laden.");
            return;
        }
        tell(sender, ChatColor.GREEN + "Map '" + key + "' eingerichtet (arenas/" + key + ".yml, Welt arenas/" + folder + "). "
                + ChatColor.GRAY + "Jetzt Spawns setzen: /mm map tp " + key + ", dann /mm addspawn und /mm addgold – oder im Menü /mm maps.");
    }

    /** Entpackt eine ZIP so, dass der Ordner mit der level.dat zu {@code target} wird. */
    private void unzipWorld(Path zip, Path target) throws IOException {
        Path temp = Files.createTempDirectory(dir().toPath(), ".import_");
        try {
            Path root = temp.toAbsolutePath().normalize();
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry e;
                while ((e = in.getNextEntry()) != null) {
                    Path out = root.resolve(e.getName()).normalize();
                    if (!out.startsWith(root)) throw new IOException("ungültiger Pfad in der ZIP-Datei: " + e.getName());
                    if (e.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Path world = MapManager.templateRoot(temp.toFile());
            if (world == null) throw new IOException("keine level.dat gefunden – ist das eine Minecraft-Welt?");
            MapManager.copyTree(world, target);
        } finally {
            try (Stream<Path> s = Files.walk(temp)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private void tell(CommandSender sender, String msg) {
        if (sender != null) sender.sendMessage(Util.PREFIX + msg);
        plugin.getLogger().info(ChatColor.stripColor(msg));
    }

    private void tellSync(CommandSender sender, String msg) {
        Bukkit.getScheduler().runTask(plugin, () -> tell(sender, msg));
    }
}
