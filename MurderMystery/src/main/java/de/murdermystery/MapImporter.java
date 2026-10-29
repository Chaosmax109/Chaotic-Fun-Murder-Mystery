package de.murdermystery;

import de.murdermystery.MapManager.GameMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
 * Importiert hochgeladene Maps aus {@code plugins/MurderMystery/upload/}: Welt-Ordner oder ZIP-Dateien.
 * Jede Map wird als Welt {@code mm_<name>} in den Server-Ordner kopiert, geladen und als Map eingetragen.
 * Erfolgreich importierte Uploads wandern nach {@code upload/importiert/}.
 */
public class MapImporter {

    private static final String DONE_DIR = "importiert";

    private final MurderMysteryPlugin plugin;
    private final MapManager maps;
    private final File uploadDir;
    private boolean running;

    public MapImporter(MurderMysteryPlugin plugin, MapManager maps) {
        this.plugin = plugin;
        this.maps = maps;
        this.uploadDir = new File(plugin.getDataFolder(), "upload");
    }

    /** Legt den Upload-Ordner mit einer kurzen Anleitung an. */
    public void prepareFolder() {
        if (!uploadDir.isDirectory() && !uploadDir.mkdirs()) return;
        File readme = new File(uploadDir, "LIESMICH.txt");
        if (readme.exists()) return;
        try {
            Files.writeString(readme.toPath(), String.join(System.lineSeparator(),
                    "Maps hochladen",
                    "==============",
                    "Lege hier Welt-Ordner oder ZIP-Dateien mit einer Minecraft-Welt ab (z. B. per FTP oder im Dateimanager deines Hosters).",
                    "Der Datei- bzw. Ordnername wird zum Map-Namen, z. B. villa.zip -> Map 'villa' (Welt 'mm_villa').",
                    "",
                    "Importiert wird beim nächsten Serverstart automatisch, oder sofort mit /mm map import (oder im Menü /mm maps).",
                    "Danach im Spiel: /mm map tp <name> und dort /mm addspawn sowie /mm addgold setzen (oder im Menü /mm maps).",
                    "Fertig importierte Uploads werden in den Unterordner 'importiert' verschoben.",
                    ""), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** Alle Uploads, die noch auf den Import warten. */
    public List<File> pending() {
        List<File> list = new ArrayList<>();
        File[] files = uploadDir.listFiles();
        if (files == null) return list;
        for (File f : files) {
            if (f.isDirectory() && !f.getName().equals(DONE_DIR) && !f.getName().startsWith(".")) list.add(f);
            else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) list.add(f);
        }
        return list;
    }

    /**
     * Importiert alle wartenden Uploads. Das Kopieren läuft im Hintergrund, Laden der Welt und Eintragen der Map
     * danach auf dem Server-Thread. {@code sender} bekommt die Rückmeldungen (darf null sein).
     */
    public void importAll(CommandSender sender) {
        List<File> uploads = pending();
        if (uploads.isEmpty()) {
            tell(sender, ChatColor.GRAY + "Keine neuen Uploads in plugins/MurderMystery/upload/ gefunden.");
            return;
        }
        if (running) {
            tell(sender, ChatColor.RED + "Es läuft bereits ein Import.");
            return;
        }
        // Prüfungen auf dem Server-Thread, nur das Kopieren läuft im Hintergrund
        List<File> todo = new ArrayList<>();
        for (File upload : uploads) {
            String key = keyOf(upload);
            if (maps.get(key) != null) {
                tell(sender, ChatColor.RED + "'" + upload.getName() + "': Eine Map '" + key + "' gibt es schon – Datei umbenennen.");
            } else if (maps.worldExists("mm_" + key) || Bukkit.getWorld("mm_" + key) != null) {
                tell(sender, ChatColor.RED + "'" + upload.getName() + "': Eine Welt 'mm_" + key + "' gibt es schon.");
            } else {
                todo.add(upload);
            }
        }
        if (todo.isEmpty()) return;
        running = true;
        tell(sender, "Importiere " + todo.size() + " Upload(s) …");
        File container = Bukkit.getWorldContainer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String[]> copied = new ArrayList<>(); // {Map-Name, Welt-Name}
            for (File upload : todo) {
                String key = keyOf(upload);
                String worldName = "mm_" + key;
                try {
                    copyWorld(upload, new File(container, worldName).toPath());
                    File doneDir = new File(uploadDir, DONE_DIR);
                    doneDir.mkdirs();
                    Files.move(upload.toPath(), new File(doneDir, upload.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    copied.add(new String[]{key, worldName});
                } catch (IOException e) {
                    tellSync(sender, ChatColor.RED + "'" + upload.getName() + "' konnte nicht importiert werden: " + e.getMessage());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String[] c : copied) {
                    World w = maps.loadWorld(c[1]);
                    if (w == null) {
                        tell(sender, ChatColor.RED + "Welt '" + c[1] + "' wurde kopiert, ließ sich aber nicht laden.");
                        continue;
                    }
                    GameMap m = maps.create(c[0], w);
                    tell(sender, ChatColor.GREEN + "Map '" + m.key + "' importiert (Welt " + w.getName() + "). "
                            + ChatColor.GRAY + "Jetzt Spawns setzen: /mm map tp " + m.key + ", dann /mm addspawn und /mm addgold – oder im Menü /mm maps.");
                }
                running = false;
                plugin.game().refreshLobby();
            });
        });
    }

    /** Map-Name aus dem Datei-/Ordnernamen, z. B. "Villa Neu.zip" → "villa_neu". */
    private static String keyOf(File upload) {
        return upload.getName().replaceFirst("(?i)\\.zip$", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    /** Kopiert eine Welt aus einem Ordner oder ZIP. Gesucht wird der Ordner mit der level.dat. */
    private void copyWorld(File upload, Path target) throws IOException {
        Path temp = null;
        Path source;
        if (upload.isFile()) {
            temp = Files.createTempDirectory(uploadDir.toPath(), ".import_");
            unzip(upload.toPath(), temp);
            source = findWorldRoot(temp);
        } else {
            source = findWorldRoot(upload.toPath());
        }
        try {
            if (source == null) throw new IOException("keine level.dat gefunden – ist das eine Minecraft-Welt?");
            copyTree(source, target);
            Files.deleteIfExists(target.resolve("uid.dat"));          // sonst meldet der Server eine doppelte Welt
            Files.deleteIfExists(target.resolve("session.lock"));
        } finally {
            if (temp != null) deleteTree(temp);
        }
    }

    private static Path findWorldRoot(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir, 4)) {
            return s.filter(p -> p.getFileName().toString().equals("level.dat"))
                    .map(Path::getParent)
                    .min(Comparator.comparingInt(Path::getNameCount))
                    .orElse(null);
        }
    }

    private static void unzip(Path zip, Path into) throws IOException {
        Path root = into.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                Path out = root.resolve(e.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("ungültiger Pfad in der ZIP-Datei: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    copy(in, out);
                }
            }
        }
    }

    private static void copy(InputStream in, Path out) throws IOException {
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(dest);
                else Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
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
