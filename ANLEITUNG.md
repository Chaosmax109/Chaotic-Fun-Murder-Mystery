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

## 6. Einstellungen

Im Ordner `plugins/MurderMystery/` liegen vier Dateien:

| Datei | Inhalt |
|---|---|
| `config.yml` | Allgemeines: Auto-Join, Spielerzahlen, Zeiten, Lobby, Arena-Punkte |
| `roles.yml` | Alle Rollen (auch eigene) und die Anzeige der Wahrscheinlichkeiten |
| `gold.yml` | Alles zum Gold |
| `database.yml` | Datenbank-Verbindung für die Statistiken |

Nach Änderungen `/mm reload` ausführen, das lädt alle Dateien neu und verbindet die Datenbank neu. Jede Einstellung ist in den Dateien kommentiert.

### Rollen (`roles.yml`)

Alle Rollen stehen unter `roles:`. Eingebaut sind `murderer` (Mörder), `detective` (Detektiv) und `innocent` (Unschuldig). Dazu kommt die Beispielrolle `runner` (Läufer), die mit `chance: 0` ausgeschaltet ist.

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
| `team` | `MURDERER` (gewinnt, wenn alle anderen tot sind) oder `INNOCENT` |
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

**Wahrscheinlichkeits-Anzeige:** In der Lobby zeigt das Scoreboard unter „Deine Chancen“, mit welcher Wahrscheinlichkeit man welche Rolle bekommt, abhängig von der aktuellen Spielerzahl. Für alle Rollen abschalten mit `show-chances: false` ganz oben in der `roles.yml`. Für einzelne Rollen geht das mit `show-chance: false`.

### Gold (`gold.yml`)

- `enabled`, `material` (z. B. `GOLD_INGOT`, `GOLD_NUGGET`, `EMERALD`)
- `interval`: alle wie viele Sekunden Gold erscheint. `spawns-per-interval`: an wie vielen Punkten gleichzeitig.
- `max-on-map`: höchstens so viele Gold-Items gleichzeitig (0 = so viele wie Gold-Spawns)
- `amount-per-pickup`, `for-bow`, `arrows-per-bow`, `bow-name`

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

Gezählt werden nur echte Kills: das Mörder-Team tötet einen Gegner oder umgekehrt. Wer einen Unschuldigen tötet, bekommt keinen Kill.
Wer eine Runde vorzeitig verlässt, bekommt eine Niederlage. Mit `/mm stop` abgebrochene Runden zählen nicht.
