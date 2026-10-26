# Murder Mystery – Server-Einrichtung

Es gibt zwei Plugin-Dateien:

| Datei | Server-Version | Java |
|---|---|---|
| `MurderMystery-26.2.jar` | **Paper 26.2** (empfohlen) | Java 25 |
| `MurderMystery-1.8.8.jar` | Spigot/Paper 1.8.8 (alte Version) | Java 8 |

## 1. Server aufsetzen (Paper 26.2)

1. **Java 25** installieren (z. B. Eclipse Temurin 25: https://adoptium.net).
2. **Paper 26.2** herunterladen: https://papermc.io/downloads/all → Version „26.2“.
3. Server starten: `java -Xmx4G -jar paper-26.2.jar --nogui`, `eula.txt` auf `eula=true` setzen, erneut starten.

## 2. Ältere Clients erlauben (ab 1.8.9)

Ein 26.2-Server lässt von sich aus nur 26.2-Clients rein. Damit Spieler **ab 1.8.9** joinen können, diese drei Plugins in den `plugins`-Ordner legen (jeweils die neueste Version, die 26.2 unterstützt):

- **ViaVersion** – https://hangar.papermc.io/ViaVersion/ViaVersion (Grundlage)
- **ViaBackwards** – https://hangar.papermc.io/ViaVersion/ViaBackwards (Clients älter als der Server, bis 1.10)
- **ViaRewind** – https://hangar.papermc.io/ViaVersion/ViaRewind (zusätzlich 1.9 und **1.8.x**)

Hinweis für 1.8.9-Spieler: Das Spiel ist darauf ausgelegt, dass Mörder mit **einem Treffer** töten und es keine Nebenhand braucht – das funktioniert mit 1.8-Kampf genauso wie mit neuem Kampf.

## 3. Plugin installieren

`MurderMystery-26.2.jar` in den `plugins`-Ordner legen und Server neu starten. Hast du vorher schon die 1.8.8-Version benutzt: deine `plugins/MurderMystery/config.yml` (mit Lobby und Spawns) kann bleiben, sofern die Welt dieselbe ist.

## 4. Arena einrichten (als OP)

Baue/lade eine Map. Die Lobby darf in **derselben Welt** wie die Map liegen oder in einer eigenen Welt (auch Multiverse-Welten). Dann:

| Befehl | Was es macht |
|---|---|
| `/mm setlobby` | Wartelobby an deiner Position setzen |
| `/mm tplobby` | Zur Lobby teleportieren |
| `/mm addspawn` | Spieler-Spawn auf der Map hinzufügen – **einen pro Spieler** (also so viele wie `max-players`) |
| `/mm spawns` | Alle Spieler-Spawns mit Nummer und Position auflisten |
| `/mm tpspawn <nr>` / `/mm removespawn <nr>` | Zu einem Spawn teleportieren / ihn löschen |
| `/mm addgold` | Gold-Spawnpunkt hinzufügen (gerne 10–20 über die Map verteilt) |
| `/mm golds`, `/mm tpgold <nr>`, `/mm removegold <nr>` | Gold-Spawns verwalten |
| `/mm clearspawns` / `/mm cleargold` | Alle Spawns löschen |
| `/mm info` | Zeigt, was eingerichtet ist |
| `/mm start` | Runde sofort starten (ab 2 Spielern) |
| `/mm stop` | Laufende Runde abbrechen |
| `/mm reload` | config.yml neu laden |

Für alle Spieler: `/mm join`, `/mm leave`, `/stats [Spieler]`.

Beim Rundenstart bekommt **jeder Spieler einen eigenen Spawn** (zufällig verteilt). Gibt es weniger Spawns als Spieler, teilen sich einige einen – `/mm info` weist darauf hin.

**Lobby in derselben Welt:** Wartende Spieler, die sich weiter als `lobby.max-distance` Blöcke (Standard 40) von der Lobby entfernen, werden zurückteleportiert. So läuft niemand vor Rundenstart auf die Map. Mit `0` wird das abgeschaltet.

Tipp: OPs im **Kreativmodus** sind vom Block-/Inventarschutz ausgenommen – zum Bauen also vorher `/mm leave` oder `/gamemode creative`.

## 5. Spielablauf

- Ab `min-players` (Standard 4) startet ein 30-Sekunden-Countdown, bei voller Runde 10 Sekunden.
- Rollen werden zufällig verteilt: standardmäßig **1 Mörder**, **1 Detektiv**, der Rest **Unschuldige** (Anzahl einstellbar, siehe unten). Namensschilder sind versteckt.
- Nach 10 Sekunden bekommen Mörder (Messer) und Detektiv (Bogen) ihre Waffen.
- **Mörder:** Linksklick = erstechen, Rechtsklick = Messer werfen (5 s Abklingzeit).
- **Detektiv:** Bogen, nach jedem Schuss kommt nach 5 s ein neuer Pfeil.
- **Gold:** erscheint an den Gold-Spawns. 10 Gold = Bogen + 1 Pfeil (jede weiteren 10 Gold = 1 Pfeil).
- Stirbt der Detektiv, fällt sein Bogen auf den Boden – jeder Unschuldige kann ihn aufheben (nicht der Mörder).
- Erschießt ein Unschuldiger einen anderen Unschuldigen, sterben **beide**.
- **Sieg Unschuldige:** Mörder tot oder Zeit (4:30) abgelaufen. **Sieg Mörder:** alle anderen tot.
- Tote werden Zuschauer; ihr Chat ist nur für andere Tote sichtbar.
- Nach 10 Sekunden geht es zurück in die Lobby, die nächste Runde startet automatisch.

## 6. Einstellungen (`plugins/MurderMystery/config.yml`)

Nach Änderungen `/mm reload` ausführen. Jede Einstellung ist in der Datei kommentiert. Die wichtigsten:

**Rollen** (`roles.murderer`, `roles.detective`, `roles.innocent`):
- `amount` und `extra-per-players`: wie viele Mörder bzw. Detektive es gibt. Beispiel: `amount: 1`, `extra-per-players: 8` bedeutet ab 8 Spielern 2 Mörder, ab 16 Spielern 3.
  Bei mehreren Mördern kennen sie sich gegenseitig und können sich nicht töten.
- `name`, `color`, `title`, `subtitle`, `message`: Anzeigename, Farbe und Texte beim Rundenstart.
- `weapon.material` / `weapon.name`: Waffe der Rolle (z. B. `DIAMOND_SWORD` für den Mörder, `BOW` oder `CROSSBOW` für den Detektiv).
- Mörder: `knife-throw`, `knife-cooldown`, `knife-range`. Detektiv: `arrow-cooldown`.
- `can-collect-gold`: ob die Rolle Gold sammeln darf.
- `roles.team-kill-kills-shooter`: ob ein Unschuldiger stirbt, wenn er einen anderen Unschuldigen erschießt.

**Gold** (`gold`):
- `enabled`, `material` (z. B. `GOLD_INGOT`, `GOLD_NUGGET`, `EMERALD`)
- `interval`: alle wie viele Sekunden Gold erscheint. `spawns-per-interval`: an wie vielen Punkten gleichzeitig.
- `max-on-map`: höchstens so viele Gold-Items gleichzeitig (0 = so viele wie Gold-Spawns)
- `amount-per-pickup`, `for-bow`, `arrows-per-bow`, `bow-name`

Configs aus Version 2.0 werden beim ersten Start automatisch auf das neue Format umgestellt. Lobby und Spawns bleiben dabei erhalten.

## 7. Statistiken

`/stats` öffnet ein Menü mit deinen Statistiken, `/stats <Spieler>` zeigt die eines anderen Spielers:

- **Kills** gesamt, aufgeteilt nach Rolle (als Mörder, als Detektiv, als Unschuldiger)
- **Tode**, **Wins**, **Loses**, **Winrate** (und K/D)

Gezählt werden nur echte Kills: Mörder tötet Nicht-Mörder, oder ein Nicht-Mörder tötet einen Mörder. Wer einen Unschuldigen erschießt, bekommt keinen Kill.
Wer eine Runde vorzeitig verlässt, bekommt eine Niederlage. Mit `/mm stop` abgebrochene Runden zählen nicht.
Gespeichert wird in `plugins/MurderMystery/stats.yml`.
