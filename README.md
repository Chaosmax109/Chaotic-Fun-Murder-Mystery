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
- Automatische Lobby mit Countdown, die nächste Runde startet von selbst
- **Mehrere Maps** mit allgemeiner Wartelobby: Map-Abstimmung in der Lobby, zufällige Map pro Runde oder pro Serverstart. Map-Welten werden automatisch geladen.
- Eigener Spawnpunkt für jeden Spieler beim Rundenstart, Spawns per Befehl verwalten
- Rollen in eigener `roles.yml`: eigene Rollen hinzufügen (Team, Waffe, Effekte), Anzahl, Texte, Abklingzeiten
- Drei Teams: Mörder, Unschuldige und **Neutral** (gewinnt, wenn er überlebt)
- Wahrscheinlichkeit pro Rolle einstellbar, Anzeige der eigenen Chancen in der Lobby (abschaltbar)
- Gold anpassbar in eigener `gold.yml`: Item, Intervall, Menge
- Gold-Belohnungen pro Rolle in `gold-rewards.yml`: Preis, Items, Trankeffekte
- Statistiken in MySQL/MariaDB mit Menü (`/stats`): Kills (gesamt und pro Rolle), Tode, Wins, Loses, Winrate. Ohne Datenbank sind die Stats deaktiviert (Standard).

## Download

Die fertigen Plugin-Dateien gibt es unter [Releases](../../releases):

| Datei | Server |
|---|---|
| `MurderMystery-2.3.0.jar` | Paper 26.2, Java 25 (aktuell) |
| `MurderMystery-26.2.jar` | Paper 26.2, Java 25 (Version 2.0.0) |
| `MurderMystery-1.8.8.jar` | Spigot/Paper 1.8.8, Java 8 (ältere Version) |

## Einrichtung

Server aufsetzen, Arena einrichten und alle Befehle: siehe [ANLEITUNG.md](ANLEITUNG.md).

Kurz: `/mm setlobby`, `/mm map create <name>`, dann auf der Map mehrmals `/mm addspawn` und `/mm addgold` – fertig.

## Selbst bauen

Benötigt JDK 25 und Maven:

```bash
cd MurderMystery
mvn package
```

Die Datei liegt danach unter `MurderMystery/target/MurderMystery.jar`.

