# Chaotic-Fun Murder Mystery

Ein Murder-Mystery-Spielmodus als Plugin für **Paper 26.2**. Mit ViaVersion, ViaBackwards und ViaRewind können Spieler **ab Minecraft 1.8.9** joinen.

Ein Spieler ist der **Mörder**, einer der **Detektiv**, alle anderen sind **unschuldig**. Der Mörder muss alle töten, ohne erwischt zu werden. Die Unschuldigen müssen überleben oder den Mörder entlarven.

## Features

- Zufällige Rollen: Mörder (Messer), Detektiv (Bogen mit Pfeil-Nachladen), Unschuldige
- Messer erstechen (Linksklick) oder werfen (Rechtsklick, mit Abklingzeit)
- Gold sammeln: 10 Gold = Bogen + Pfeil
- Stirbt der Detektiv, bleibt sein Bogen liegen und kann aufgehoben werden
- Wer einen Unschuldigen erschießt, stirbt mit
- Versteckte Namensschilder, Scoreboard mit Rolle, Zeit und Überlebenden
- Tote werden Zuschauer mit eigenem Chat
- Automatische Lobby mit Countdown, die nächste Runde startet von selbst. Die Lobby kann in derselben Welt wie die Map liegen.
- Eigener Spawnpunkt für jeden Spieler beim Rundenstart, Spawns per Befehl verwalten
- Rollen anpassbar in eigener `roles.yml`: Anzahl Mörder/Detektive, Namen, Farben, Texte, Waffen, Abklingzeiten
- Gold anpassbar in eigener `gold.yml`: Item, Intervall, Menge, Preis für den Bogen
- Statistiken mit Menü (`/stats`): Kills (gesamt und pro Rolle), Tode, Wins, Loses, Winrate

## Download

Die fertigen Plugin-Dateien gibt es unter [Releases](../../releases):

| Datei | Server |
|---|---|
| `MurderMystery-2.1.0.jar` | Paper 26.2, Java 25 (aktuell) |
| `MurderMystery-26.2.jar` | Paper 26.2, Java 25 (Version 2.0.0) |
| `MurderMystery-1.8.8.jar` | Spigot/Paper 1.8.8, Java 8 (ältere Version) |

## Einrichtung

Server aufsetzen, Arena einrichten und alle Befehle: siehe [ANLEITUNG.md](ANLEITUNG.md).

Kurz: `/mm setlobby`, mehrmals `/mm addspawn` und `/mm addgold` – fertig.

## Selbst bauen

Benötigt JDK 25 und Maven:

```bash
cd MurderMystery
mvn package
```

Die Datei liegt danach unter `MurderMystery/target/MurderMystery.jar`.

`test-server.ps1` startet einen lokalen Paper-Testserver (Windows), wenn unter `%LOCALAPPDATA%\Temp\mmbuild` ein Server und ein JDK 25 liegen.
