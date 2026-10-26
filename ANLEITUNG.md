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

Baue/lade eine Map (Lobby kann in derselben Welt sein). Dann:

| Befehl | Was es macht |
|---|---|
| `/mm setlobby` | Wartelobby an deiner Position setzen |
| `/mm addspawn` | Spieler-Spawn auf der Map hinzufügen (mehrere, ideal 1 pro Spieler) |
| `/mm addgold` | Gold-Spawnpunkt hinzufügen (gerne 10–20 über die Map verteilt) |
| `/mm clearspawns` / `/mm cleargold` | Spawns wieder löschen |
| `/mm info` | Zeigt, was eingerichtet ist |
| `/mm start` | Runde sofort starten (ab 2 Spielern) |
| `/mm stop` | Laufende Runde abbrechen |
| `/mm reload` | config.yml neu laden |

Für alle Spieler: `/mm join`, `/mm leave`.

Tipp: OPs im **Kreativmodus** sind vom Block-/Inventarschutz ausgenommen – zum Bauen also vorher `/mm leave` oder `/gamemode 1`.

## 5. Spielablauf

- Ab `min-players` (Standard 4) startet ein 30-Sekunden-Countdown, bei voller Runde 10 Sekunden.
- Rollen werden zufällig verteilt: **1 Mörder**, **1 Detektiv**, der Rest **Unschuldige**. Namensschilder sind versteckt.
- Nach 10 Sekunden bekommen Mörder (Messer) und Detektiv (Bogen) ihre Waffen.
- **Mörder:** Linksklick = erstechen, Rechtsklick = Messer werfen (5 s Abklingzeit).
- **Detektiv:** Bogen, nach jedem Schuss kommt nach 5 s ein neuer Pfeil.
- **Gold:** erscheint an den Gold-Spawns. 10 Gold = Bogen + 1 Pfeil (jede weiteren 10 Gold = 1 Pfeil).
- Stirbt der Detektiv, fällt sein Bogen auf den Boden – jeder Unschuldige kann ihn aufheben (nicht der Mörder).
- Erschießt ein Unschuldiger einen anderen Unschuldigen, sterben **beide**.
- **Sieg Unschuldige:** Mörder tot oder Zeit (4:30) abgelaufen. **Sieg Mörder:** alle anderen tot.
- Tote werden Zuschauer; ihr Chat ist nur für andere Tote sichtbar.
- Nach 10 Sekunden geht es zurück in die Lobby, die nächste Runde startet automatisch.

Alle Zeiten und Grenzen sind in `plugins/MurderMystery/config.yml` einstellbar.
