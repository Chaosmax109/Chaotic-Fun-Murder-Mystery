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

## 4. Maps und Lobby einrichten (als OP)

### So sind die Maps aufgebaut

Alle Maps liegen im Ordner **`plugins/MurderMystery/arenas/`**:

```
arenas/
├── Dam/                 ← Welt-Ordner einer Map (mit level.dat), oder Dam.zip
├── default_map.yml      ← Standard-Map (wird beim ersten Start automatisch erzeugt)
├── dam.yml              ← Einstellungen der Map "dam"
└── importiert/          ← hier landen ZIP-Dateien nach dem Entpacken
```

**Jede Map ist eine `.yml`-Datei.** Darin stehen:

```yaml
name: "&bDam"              # Anzeigename
world: Dam                 # Welt-Ordner hier in arenas/ – oder eine vorhandene Server-Welt (z. B. world)
enabled: true
icon: WATER_BUCKET         # Item im Menü und in der Abstimmung
description: "Staudamm mit Kontrollraum"
author: "Max"

lobby:
  mode: OWN                # GLOBAL = globale Lobby aus der config.yml, OWN = eigene Lobby (location)
  location: 80.5 -60 0.5 90 0
  radius: 15               # Lobby-Bereich: weiter weg = zurück in die Lobby (0 = aus)

area:                      # Spielbereich (Größe der Map): wer ihn in der Runde verlässt, kommt zurück zu einem Spawn
  enabled: true
  corner1: -20 -62 -20
  corner2: 20 -40 20

spawns:                    # Spieler-Spawns: "x y z" oder "x y z yaw pitch"
- 14.5 -60 0.5 90 0
- 12.5 -60 5.5 113 0

gold:                      # Gold-Spawns
- -15.5 -60 -15.5
```

Alles kannst du direkt in der Datei ändern und mit `/mm reload` übernehmen, oder im Spiel setzen, siehe unten. Die Koordinaten gelten immer in der Welt der Map.

### Neue Map hinzufügen

1. Den **Welt-Ordner** (z. B. `Dam`) oder eine **ZIP-Datei** (`Dam.zip`) nach `plugins/MurderMystery/arenas/` hochladen, z. B. per FTP oder im Dateimanager deines Hosters.
2. **Server neu starten**, oder `/mm map import` bzw. im Menü `/mm maps` → „Neue Maps aus arenas/ einrichten“. Das geht auch in der Konsole. Dann passiert Folgendes:
   - Eine ZIP wird zu einem Welt-Ordner entpackt.
   - Für jeden neuen Welt-Ordner wird automatisch eine `.yml` angelegt, hier `dam.yml` mit `world: Dam`.
   - Die Welt wird als Server-Welt `mm_dam` eingerichtet und geladen.
3. **Spawns setzen:** `/mm map tp dam`, dann `/mm addspawn` (einen pro Spieler, so viele wie `max-players`) und `/mm addgold`. Alternativ im Menü oder direkt in der `dam.yml`.

Hinweise:
- Ein Welt-Ordner aus `arenas/` wird nur **beim ersten Mal** auf den Server kopiert. Hast du die Map danach im Welt-Ordner geändert, übernimmt `/mm map reinstall dam` die neue Version.
- Paper 26.x legt die Server-Welt intern unter `world/dimensions/minecraft/mm_dam` ab. Das ist normal.
- Statt eines Welt-Ordners kann in `world:` auch eine Welt stehen, die schon auf dem Server existiert, z. B. die Hauptwelt `world`. Dafür gibt es `/mm map create <name> [welt]`.

### Standard-Map

Beim **allerersten Start**, wenn es noch keine Map gibt, erzeugt das Plugin automatisch **`default_map`**:
- eine Flachwelt mit ummauerter Arena (41 × 41) mit vier Räumen, Säulen und Deckungen
- 16 Spieler-Spawns und 12 Gold-Spawns
- eine Glas-Lobby als eigene Lobby
- einen fertig eingestellten Spielbereich

Damit ist das Plugin sofort spielbar. Die Welt der Standard-Map liegt danach wie jede andere Map als Ordner `arenas/default_map/`, zum Ansehen, Herunterladen und Bearbeiten. `/mm map testmap` baut sie jederzeit neu auf.

### Lobby

| Befehl | Was es macht |
|---|---|
| `/mm setlobby` | Globale Lobby an deiner Position setzen |
| `/mm map setlobby [map]` | Eigene Lobby der Map an deiner Position setzen (`lobby.mode: OWN`) |
| `/mm map clearlobby <map>` | Map nutzt wieder die globale Lobby (`lobby.mode: GLOBAL`) |
| `/mm tplobby [map]` | Zur globalen Lobby teleportieren, oder zur Lobby einer Map |

**Welche Lobby genutzt wird:**
1. Die **eigene Lobby der nächsten Map** (`lobby.mode: OWN`), sobald feststeht, welche Map als Nächstes kommt:
   - bei `RANDOM_ON_START` immer
   - bei `RANDOM_EACH_ROUND`, weil die Map schon zu Beginn der Wartezeit ausgelost wird
   - nach `/mm map next`
   - Dann gilt auch ihr `lobby.radius`.
2. Sonst die **globale Lobby** mit `lobby.max-distance` aus der `config.yml`, z. B. während einer Map-Abstimmung.
3. **Fallback:** Ist keine globale Lobby gesetzt, wird die Position des **ersten Spielers**, der den Server betritt, als globale Lobby gespeichert. Ändern kannst du sie mit `/mm setlobby`.

### Map-Menü: `/mm maps`

- **Übersicht:** alle Maps mit Status. Bei nicht spielbaren Maps steht der Grund dabei. Unten gibt es Knöpfe für:
  - Standard-Map erzeugen
  - neue Maps aus `arenas/` einrichten
  - Auswahl-Modus wechseln
  - globale Lobby setzen
- **Map anklicken → Bearbeiten:**
  - zur Map teleportieren
  - Spieler- und Gold-Spawn hier hinzufügen
  - eigene Lobby setzen (Rechtsklick: zurück zu GLOBAL)
  - **Lobby-Bereich** (Radius ±1, mit Shift ±5)
  - **Spielbereich:** Linksklick setzt Ecke 1, Rechtsklick Ecke 2, Shift schaltet an und aus
  - Icon aus der Hand übernehmen
  - aktivieren / deaktivieren
  - als nächste Map spielen
  - Spawns oder Map löschen, jeweils mit Shift-Klick

### Alle Map-Befehle

Die Spawn-Befehle gelten für die Map in der Welt, in der du stehst. Liegen mehrere Maps in derselben Welt, hängst du den Map-Namen an, z. B. `/mm addspawn dam` oder `/mm removespawn 3 dam`.

| Befehl | Was es macht |
|---|---|
| `/mm maps` | Map-Menü |
| `/mm map list` | Alle Maps mit Status, Grund (falls nicht spielbar) und wo ihre Datei und ihr Welt-Ordner liegen |
| `/mm map import` | Neue Welt-Ordner und ZIPs in `arenas/` einrichten |
| `/mm map create <name> [welt]` | Map-Datei für eine vorhandene Welt anlegen (Welt-Ordner aus `arenas/` oder Server-Welt) |
| `/mm map testmap` | Standard-Map (neu) erzeugen |
| `/mm map tp <name>` | Zur Map teleportieren |
| `/mm map edit <name>` | Bearbeiten-Menü öffnen |
| `/mm map area <name> corner1 \| corner2 \| on \| off` | Spielbereich (Größe) festlegen |
| `/mm map setlobby [name]` / `clearlobby <name>` | Eigene Lobby setzen / zurück zu GLOBAL |
| `/mm map enable` / `disable <name>` | Map an- und ausschalten |
| `/mm map next <name>` | Die nächste Runde auf dieser Map spielen |
| `/mm map rename` / `description` / `author <name> <text>` | Anzeigename, Beschreibung, Erbauer |
| `/mm map reinstall <name>` | Geänderten Welt-Ordner aus `arenas/` übernehmen |
| `/mm map export <name> [ordner]` | Welt einer Map, die eine Server-Welt nutzt, als Ordner nach `arenas/` kopieren und die Map darauf umstellen |
| `/mm map delete <name>` | Map-Datei löschen (Welt-Ordner und Server-Welt bleiben) |
| `/mm addspawn` / `/mm addgold` | Spieler- bzw. Gold-Spawn hinzufügen |
| `/mm spawns`, `/mm tpspawn <nr>`, `/mm removespawn <nr>` | Spieler-Spawns verwalten |
| `/mm golds`, `/mm tpgold <nr>`, `/mm removegold <nr>` | Gold-Spawns verwalten |
| `/mm clearspawns` / `/mm cleargold` | Alle Spawns dieser Map löschen |
| `/mm info` | Zeigt, was eingerichtet ist |
| `/mm start` / `/mm stop` | Runde sofort starten (ab 2 Spielern) / abbrechen |
| `/mm reload` | Alle Config- und Arena-Dateien neu laden |

### Welche Map gespielt wird (`maps.yml`)

In der `maps.yml` stellst du mit `mode` ein, wie die Map gewählt wird. Das geht auch im Menü:

| `mode` | Verhalten |
|---|---|
| `VOTE` (Standard) | Die Spieler stimmen in der Lobby ab: Papier in der Hotbar oder `/mm vote`. Die Map mit den meisten Stimmen gewinnt, bei Gleichstand oder ohne Stimmen entscheidet der Zufall. |
| `RANDOM_EACH_ROUND` | Jede Runde eine zufällige Map |
| `RANDOM_ON_START` | Bei **jedem Serverstart** eine zufällige Map, die bis zum nächsten Neustart bleibt |

- `vote-options`: wie viele Maps zur Abstimmung stehen (Standard 3)
- `avoid-repeat: true`: nie zweimal hintereinander dieselbe Map, auch nicht über einen Neustart hinweg

Wer eine ältere Version benutzt hat: Bisherige Maps aus der `maps.yml` bzw. die alte Arena aus der `config.yml` werden beim Start automatisch als Dateien in `arenas/` übernommen.

### Allgemein

Für alle Spieler: `/mm join`, `/mm leave`, `/mm vote`, `/stats [Spieler]`. Alle `/mm`-Befehle lassen sich mit **Tab** vervollständigen, auch Map-Namen, Spieler und Spawn-Nummern.

**Items in der Lobby-Hotbar** (Rechtsklick benutzt sie):

| Platz | Item | Funktion |
|---|---|---|
| 1 | Karte „Map-Info“ | Zeigt die nächste Map mit Beschreibung und Erbauer, bzw. den Stand der Abstimmung |
| 5 | Papier „Map-Abstimmung“ | Öffnet das Abstimmungs-Menü (nur im Abstimmungs-Modus mit mehreren Maps) |
| 9 | Eigener Kopf „Deine Stats“ | Öffnet das Stats-Menü (nur mit verbundener Datenbank) |

Die Plätze stellst du in der `config.yml` unter `lobby-items` ein. `0` schaltet ein Item aus.

Beim Rundenstart bekommt **jeder Spieler einen eigenen Spawn** (zufällig verteilt). Gibt es weniger Spawns als Spieler, teilen sich einige einen. `/mm info` weist darauf hin.

Tipp: OPs im **Kreativmodus** sind vom Block- und Inventarschutz ausgenommen. Zum Bauen also vorher `/mm leave` oder `/gamemode creative`.

## 5. Spielablauf

- Ab `min-players` (Standard 4) startet ein 30-Sekunden-Countdown, bei voller Runde 10 Sekunden.
- Rollen werden zufällig verteilt: standardmäßig **1 Mörder**, **1 Detektiv**, der Rest **Unschuldige** (Anzahl einstellbar, siehe unten). Namensschilder sind versteckt.
- Nach 10 Sekunden bekommen Mörder (Messer) und Detektiv (Bogen) ihre Waffen.
- **Mörder:** Linksklick = erstechen, Rechtsklick = Messer werfen. Das Messer kommt zurück ins Inventar, sobald es gelandet ist (Wand, Reichweite oder Treffer). Danach kann man es erst nach der Abklingzeit (Standard 5 s) wieder werfen. Über der Hotbar läuft dafür ein Balken mit Restzeit, am Ende steht „Messer-Wurf bereit“.
- **Detektiv:** Bogen. Nach jedem Schuss lädt er nach (Standard 5 s). Über der Hotbar läuft ein Balken mit Restzeit, am Ende steht „Bogen geladen“. Auf dem Bogen selbst ist die Abklingzeit ebenfalls zu sehen.
- **Gold:** erscheint an den Gold-Spawns. Standardmäßig gibt es für 10 Gold Bogen + 1 Pfeil (jede weiteren 10 Gold = 1 Pfeil). Pro Rolle einstellbar in `gold-rewards.yml`.
- Stirbt der Detektiv, fällt sein Bogen auf den Boden – jeder Unschuldige kann ihn aufheben (nicht der Mörder).
- Erschießt ein Unschuldiger einen anderen Unschuldigen, sterben **beide**.
- **Sieg Unschuldige:** Mörder tot oder Zeit (4:30) abgelaufen. **Sieg Mörder:** alle anderen tot.
- Tote werden Zuschauer; ihr Chat ist nur für andere Tote sichtbar.
- Nach 10 Sekunden geht es zurück in die Lobby, die nächste Runde startet automatisch.

## 6. Einstellungen

Im Ordner `plugins/MurderMystery/` liegen diese Dateien:

| Datei | Inhalt |
|---|---|
| `config.yml` | Allgemeines: Auto-Join, Spielerzahlen, Zeiten, globale Wartelobby, Lobby-Items |
| `maps.yml` | Wie die Map gewählt wird (Abstimmung / Zufall) |
| `arenas/*.yml` | Eine Datei pro Map: Welt, Lobby, Spielbereich, Spieler- und Gold-Spawns |
| `roles.yml` | Alle Rollen (auch eigene), Teams und die Anzeige der Wahrscheinlichkeiten |
| `gold.yml` | Wie und wo Gold erscheint |
| `gold-rewards.yml` | Was jede Rolle für ihr Gold bekommt |
| `database.yml` | Datenbank-Verbindung für die Statistiken |

Nach Änderungen `/mm reload` ausführen, das lädt alle Dateien neu und verbindet die Datenbank neu. Jede Einstellung ist in den Dateien kommentiert.

### Rollen (`roles.yml`)

Alle Rollen stehen unter `roles:`. Eingebaut sind `murderer` (Mörder), `detective` (Detektiv) und `innocent` (Unschuldig). Dazu kommen zwei Beispielrollen, die mit `chance: 0` ausgeschaltet sind: `runner` (Läufer, unschuldig mit Speed) und `survivor` (Überlebender, neutral).

**Die drei Teams:**

| Team | Gewinnt, wenn … |
|---|---|
| `MURDERER` | kein Spieler aus dem Team `INNOCENT` mehr lebt |
| `INNOCENT` | kein Spieler aus dem Team `MURDERER` mehr lebt oder die Zeit abläuft |
| `NEUTRAL` | er am Ende der Runde noch lebt, egal welches Team gewinnt. Neutrale müssen nicht getötet werden, damit die Mörder gewinnen. |

Neutrale können von allen getötet werden und mit einer Waffe selbst jeden töten. Tötet ein Unschuldiger einen Neutralen, passiert ihm nichts, außer `neutral-kill-kills-shooter: true` ist gesetzt. Kills an Neutralen und von Neutralen zählen in den Stats.

- `innocent` ist Pflicht: Diese Rolle bekommen alle, die keine andere Rolle erhalten.
- Es muss mindestens eine Rolle mit `team: MURDERER` geben.

**Eigene Rolle hinzufügen:** einen neuen Abschnitt unter `roles:` anlegen, z. B. so:

```yaml
roles:
  # ... murderer, detective, innocent ...
  sheriff:
    team: INNOCENT
    amount: 1
    chance: 25          # in 25 % der Runden gibt es einen Sheriff
    show-chance: true
    name: "Sheriff"
    color: "&6"
    title: "&6SHERIFF"
    subtitle: "&7Du hast eine Armbrust!"
    message: "Du bist der &6Sheriff&7! Deine Armbrust lädt sich nach jedem Schuss neu."
    weapon:
      type: BOW
      material: CROSSBOW
      name: "&6Armbrust des Sheriffs"
    arrow-cooldown: 8
    can-collect-gold: true
    can-pickup-bow: true
    effects: []
```

Was jede Rolle einstellen kann:

| Einstellung | Bedeutung |
|---|---|
| `team` | `MURDERER`, `INNOCENT` oder `NEUTRAL` (siehe oben) |
| `amount`, `extra-per-players` | Wie viele Spieler die Rolle bekommen. Beispiel: `amount: 1`, `extra-per-players: 8` bedeutet ab 8 Spielern 2, ab 16 Spielern 3 |
| `chance` | Wahrscheinlichkeit in Prozent, dass die Rolle in einer Runde vergeben wird (`100` = immer, `0` = nie) |
| `show-chance` | Wahrscheinlichkeit dieser Rolle in der Lobby anzeigen |
| `name`, `color`, `title`, `subtitle`, `message` | Anzeigename, Farbe und Texte beim Rundenstart |
| `weapon.type` | `KNIFE` (Nahkampf + Werfen), `BOW` (Bogen/Armbrust mit Pfeil-Nachladen) oder `NONE` |
| `weapon.material`, `weapon.name` | Item und Name der Waffe |
| `knife-throw`, `knife-cooldown`, `knife-range` | Nur für `KNIFE` |
| `arrow-cooldown` | Nur für `BOW`: Sekunden bis zum nächsten Pfeil |
| `can-collect-gold` | Darf Gold sammeln |
| `can-pickup-bow` | Darf den Bogen eines toten Bogenträgers aufheben |
| `effects` | Trankeffekte für die ganze Runde, z. B. `["SPEED:1", "JUMP_BOOST:2"]` (Zahl = Stufe) |
| `icon` | Item für die Rolle im Stats-Menü (Standard: die Waffe) |

Die Rollen werden in der Reihenfolge aus der Datei vergeben. Mehrere Spieler im Team `MURDERER` kennen sich gegenseitig und können sich nicht töten. Tötet ein Unschuldiger einen anderen Unschuldigen, stirbt er mit, außer `team-kill-kills-shooter: false` ist gesetzt.

**Wahrscheinlichkeits-Anzeige:** In der Lobby sieht jeder Spieler oben am Bildschirm **eine Bossbar pro Rolle**, z. B. „Deine Chance auf Mörder: 25 %“, in der Farbe der Rolle. Die Chance hängt von der aktuellen Spielerzahl ab. Mit `chance-display` in der `roles.yml` wählst du `BOSSBAR` (Standard), `SCOREBOARD` oder `BOTH`. Für alle Rollen abschalten mit `show-chances: false`, für einzelne Rollen mit `show-chance: false`.

### Gold (`gold.yml`)

- `enabled`, `material` (z. B. `GOLD_INGOT`, `GOLD_NUGGET`, `EMERALD`)
- `interval`: alle wie viele Sekunden Gold erscheint. `spawns-per-interval`: an wie vielen Punkten gleichzeitig.
- `max-on-map`: höchstens so viele Gold-Items gleichzeitig (0 = so viele wie Gold-Spawns)
- `amount-per-pickup`: wie viel Gold ein aufgesammeltes Item zählt

### Gold-Belohnungen (`gold-rewards.yml`)

Hier steht für jede Rolle, was sie für ihr Gold bekommt. Der Abschnitt-Name ist der Rollen-Schlüssel aus der `roles.yml`. Rollen ohne eigenen Abschnitt bekommen das Paket `default`.

```yaml
default:              # alle Rollen ohne eigenen Eintrag
  enabled: true
  cost: 10            # 10 Gold = 1 Paket, danach beginnt der Zähler von vorn
  message: "&6Du hast dein Gold eingelöst!"
  items:
    - material: BOW
      name: "&eBogen"
      once: true      # nur, wenn der Spieler noch keinen Bogen hat
    - material: ARROW
      amount: 1
  effects: []

murderer:             # eigenes Paket nur für den Mörder
  enabled: true
  cost: 15
  message: "&cDu hast eine Enderperle bekommen!"
  items:
    - material: ENDER_PEARL
  effects: ["SPEED:1:5"]   # 5 Sekunden Speed I
```

- `enabled: false`: Gold bringt dieser Rolle nichts. Ob sie überhaupt Gold sammeln darf, regelt `can-collect-gold` in der `roles.yml`.
- `items`: `material`, `amount`, `name`, `once`.
- `effects`: `EFFEKT:Stufe:Sekunden`. Ohne Sekunden hält der Effekt die ganze Runde.

Mitgeliefert sind `default` (Bogen + Pfeil), `detective` (10 Sekunden Speed II, er hat ja schon einen Bogen) und `runner` (Enderperle).

Ältere Configs werden beim ersten Start automatisch umgestellt. Deine Werte bleiben dabei erhalten.

## 7. Statistiken (Datenbank)

Statistiken brauchen eine **MySQL- oder MariaDB-Datenbank** und sind **standardmäßig deaktiviert**. Ohne Datenbank meldet `/stats`, dass die Stats deaktiviert sind, und es wird nichts gespeichert.

So schaltest du sie ein:
1. Eine Datenbank und einen Benutzer anlegen (bei vielen Hostern per Webinterface möglich), z. B.:
   ```sql
   CREATE DATABASE minecraft;
   CREATE USER 'minecraft'@'%' IDENTIFIED BY 'geheimes-passwort';
   GRANT ALL PRIVILEGES ON minecraft.* TO 'minecraft'@'%';
   ```
2. In `plugins/MurderMystery/database.yml` die Zugangsdaten eintragen und `enabled: true` setzen.
3. `/mm reload` oder Server neu starten. Im Log steht dann „Mit der Datenbank verbunden – Stats sind aktiv.“

Die Tabellen (`mm_players`, `mm_role_kills`) legt das Plugin selbst an. Die Datenbank-Treiber lädt Paper beim ersten Start automatisch herunter, dafür braucht der Server einmal Internetzugang.
Ist die Datenbank nicht erreichbar, steht im Log „Keine Verbindung zur Datenbank – Stats sind deaktiviert“, und das Spiel läuft ganz normal ohne Stats weiter.

`/stats` öffnet ein Menü mit deinen Statistiken, `/stats <Spieler>` zeigt die eines anderen Spielers:

- **Kills** gesamt und pro Rolle (auch für eigene Rollen)
- **Tode**, **Wins**, **Loses**, **Winrate** (und K/D)

Gezählt werden nur echte Kills an Spielern aus einem anderen Team. Neutrale zählen für alle als Gegner. Wer einen Unschuldigen aus dem eigenen Team tötet, bekommt keinen Kill.
Neutrale bekommen einen Win, wenn sie das Rundenende überleben, sonst einen Lose.
Wer eine Runde vorzeitig verlässt, bekommt eine Niederlage. Mit `/mm stop` abgebrochene Runden zählen nicht.
