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

## 4. Lobby und Maps einrichten (als OP)

Es gibt **eine globale Wartelobby** und **beliebig viele Maps**. Jede Map hat ihre eigenen Spieler- und Gold-Spawns und kann optional eine **eigene Lobby** haben.

### Lobby

| Befehl | Was es macht |
|---|---|
| `/mm setlobby` | Globale Lobby an deiner Position setzen |
| `/mm map setlobby [map]` | Eigene Lobby für eine Map an deiner Position setzen |
| `/mm map clearlobby <map>` | Eigene Lobby der Map entfernen, dann gilt wieder die globale |
| `/mm tplobby [map]` | Zur globalen Lobby teleportieren, oder zur Lobby einer Map |

**Welche Lobby genutzt wird:**
1. Die **eigene Lobby der nächsten Map**, sobald feststeht, welche Map als Nächstes gespielt wird:
   - bei `RANDOM_ON_START` immer
   - bei `RANDOM_EACH_ROUND`, weil die Map schon zu Beginn der Wartezeit ausgelost wird
   - nach `/mm map next`
2. Sonst die **globale Lobby**, z. B. während einer Abstimmung oder wenn die Map keine eigene Lobby hat.
3. **Fallback:** Ist keine globale Lobby gesetzt, wird die Position, an der der **erste Spieler** den Server betritt, automatisch als globale Lobby gespeichert. Das steht dann im Log, und OPs bekommen einen Hinweis im Chat. Ändern kannst du sie jederzeit mit `/mm setlobby`.

Eine Lobby kann in einer eigenen Welt liegen oder in derselben Welt wie eine Map. Wartende Spieler, die sich weiter als `lobby.max-distance` Blöcke (Standard 40) von der Lobby entfernen, werden zurückteleportiert. Mit `0` ist das abgeschaltet.

### Schnellstart: Test-Map

Zum Ausprobieren ohne eigene Map: **`/mm map testmap`**, das geht im Spiel und in der Server-Konsole. Das Plugin erzeugt dann:
- eine Flachwelt `mm_testmap`
- eine ummauerte **Arena** (41 × 41 Blöcke) mit vier Räumen, Säulen und Deckungen
- **16 Spieler-Spawns** und **12 Gold-Spawns**, schon gesetzt
- daneben eine **Glas-Lobby** als eigene Lobby der Map. Ist noch keine globale Lobby gesetzt, wird sie auch die globale Lobby.

Danach ist die Map sofort spielbar. Ansehen: `/mm map tp testmap`, Lobby: `/mm tplobby testmap`, nächste Runde darauf: `/mm map next testmap`. Erneutes `/mm map testmap` baut sie neu auf, z. B. wenn etwas kaputtgegangen ist. Nicht mehr gebraucht? `/mm map delete testmap`.

### Map-Menü: `/mm maps`

Alles rund um die Maps geht bequem über ein Menü (nur für OPs):

- **Übersicht:** alle Maps mit Status. Bei nicht spielbaren Maps steht der Grund dabei, z. B. „keine Spieler-Spawns“ oder „Welt nicht gefunden“. Unten gibt es Knöpfe für:
  - **Test-Map erzeugen**
  - **Hochgeladene Maps importieren**
  - **Auswahl-Modus wechseln:** Abstimmung → Zufall pro Runde → Zufall pro Serverstart
  - **Globale Lobby hier setzen**
- **Map anklicken → Bearbeiten:**
  - zur Map teleportieren
  - Spieler-Spawn hier hinzufügen, Gold-Spawn hier hinzufügen
  - eigene Lobby hier setzen (Rechtsklick: entfernen)
  - Icon = Item in deiner Hand
  - aktivieren / deaktivieren
  - als nächste Map spielen
  - alle Spawns oder Gold-Spawns löschen bzw. die Map löschen, jeweils mit Shift-Klick

Anzeigename, Beschreibung und Erbauer setzt du per Befehl: `/mm map rename <map> <Name>`, `/mm map description <map> <Text>`, `/mm map author <map> <Name>`. Sie erscheinen in der Map-Info und in der Abstimmung.

### Maps hochladen

1. Die Map als **Welt-Ordner** oder **ZIP-Datei** nach `plugins/MurderMystery/upload/` hochladen, z. B. per FTP oder im Dateimanager deines Hosters. Die Welt darf in der ZIP auch in einem Unterordner liegen, das Plugin sucht die `level.dat`.
2. Importiert wird **automatisch beim nächsten Serverstart**, oder sofort mit `/mm map import` bzw. im Menü `/mm maps` → „Hochgeladene Maps importieren“. Das geht auch in der Server-Konsole.
3. Der Dateiname wird zum Map-Namen, z. B. `Villa Neu.zip` → Map `villa_neu` in der Welt `mm_villa_neu`. Die hochgeladene Datei wandert danach nach `upload/importiert/`.
4. Danach nur noch Spawns setzen: `/mm map tp villa_neu`, dann `/mm addspawn` und `/mm addgold`, oder im Menü.

### Maps per Befehl hinzufügen (ohne Upload)

1. **Welt-Ordner der Map** (z. B. `villa`) in den Server-Ordner legen, also neben `world`, und den Server neu starten.
   - Alternativ kannst du eine Map auch in einer schon geladenen Welt bauen, z. B. in der Hauptwelt.
   - Hinweis zu Paper 26.x: Beim ersten Laden verschiebt Paper den Ordner selbst nach `world/dimensions/minecraft/<name>`. Das ist normal, das Plugin findet die Map dort weiterhin.
2. **Map anlegen:** `/mm map create villa villa`. Das erste Wort ist der Map-Name, das zweite der Welt-Ordner. Ohne Welt-Ordner nimmt das Plugin die Welt, in der du gerade stehst.
3. **Hingehen:** `/mm map tp villa`
4. **Spawns setzen:** `/mm addspawn`, **einen pro Spieler** (so viele wie `max-players`), und `/mm addgold`, gerne 10–20 über die Map verteilt.

Die Spawn-Befehle gelten immer für die Map in der Welt, in der du stehst. Liegen mehrere Maps in derselben Welt, hängst du den Map-Namen an, z. B. `/mm addspawn villa` oder `/mm removespawn 3 villa`.

| Befehl | Was es macht |
|---|---|
| `/mm maps` | Map-Menü (siehe oben) |
| `/mm map list` | Alle Maps mit Status und dem Grund, falls eine Map nicht spielbar ist |
| `/mm map import` | Hochgeladene Maps aus `upload/` importieren |
| `/mm map edit <name>` | Bearbeiten-Menü einer Map öffnen |
| `/mm map rename` / `description` / `author <name> <text>` | Anzeigename, Beschreibung, Erbauer |
| `/mm map create <name> [weltordner]` | Map anlegen |
| `/mm map testmap` | Fertige Test-Map (Flachwelt mit Arena und Lobby) erzeugen |
| `/mm map tp <name>` | Zur Map teleportieren |
| `/mm map enable` / `disable <name>` | Map an- und ausschalten |
| `/mm map next <name>` | Die nächste Runde auf genau dieser Map spielen |
| `/mm map setlobby [name]` / `clearlobby <name>` | Eigene Lobby der Map setzen / entfernen |
| `/mm map delete <name>` | Map entfernen (die Welt selbst bleibt) |
| `/mm addspawn` / `/mm addgold` | Spieler- bzw. Gold-Spawn hinzufügen |
| `/mm spawns`, `/mm tpspawn <nr>`, `/mm removespawn <nr>` | Spieler-Spawns verwalten |
| `/mm golds`, `/mm tpgold <nr>`, `/mm removegold <nr>` | Gold-Spawns verwalten |
| `/mm clearspawns` / `/mm cleargold` | Alle Spawns dieser Map löschen |
| `/mm info` | Zeigt, was eingerichtet ist |
| `/mm start` / `/mm stop` | Runde sofort starten (ab 2 Spielern) / abbrechen |
| `/mm reload` | Alle Config-Dateien neu laden |

Die Map-Welten lädt das Plugin beim Serverstart selbst, Multiverse ist nicht nötig. In den Map-Welten spawnen keine Monster oder Tiere von selbst.

### Welche Map gespielt wird (`maps.yml`)

In der `maps.yml` stellst du mit `mode` ein, wie die Map gewählt wird:

| `mode` | Verhalten |
|---|---|
| `VOTE` (Standard) | Die Spieler stimmen in der Lobby ab. Dafür gibt es ein **Papier „Map-Abstimmung“** im Inventar (Rechtsklick öffnet das Menü) oder `/mm vote`. Die Map mit den meisten Stimmen gewinnt, bei Gleichstand oder ohne Stimmen entscheidet der Zufall. Das Scoreboard zeigt die Stimmen live. |
| `RANDOM_EACH_ROUND` | Jede Runde eine zufällige Map |
| `RANDOM_ON_START` | Bei **jedem Serverstart** eine zufällige Map, die bis zum nächsten Neustart bleibt |

- `vote-options`: wie viele Maps zur Abstimmung stehen (zufällig ausgewählt, Standard 3)
- `avoid-repeat: true`: nie zweimal hintereinander dieselbe Map, auch nicht über einen Neustart hinweg (gilt, sobald es mindestens 2 Maps gibt)
- Pro Map in der `maps.yml`: `display-name` (Name in Abstimmung und Scoreboard), `enabled`, `icon` (Item im Abstimmungs-Menü), `lobby` (eigene Lobby, leer = globale)

Wer mit einer älteren Version eine Arena eingerichtet hatte: Die Spawns werden beim ersten Start automatisch als Map übernommen, benannt nach ihrer Welt. Die Lobby bleibt, wie sie war.

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
- **Mörder:** Linksklick = erstechen, Rechtsklick = Messer werfen (5 s Abklingzeit).
- **Detektiv:** Bogen, nach jedem Schuss kommt nach 5 s ein neuer Pfeil.
- **Gold:** erscheint an den Gold-Spawns. Standardmäßig gibt es für 10 Gold Bogen + 1 Pfeil (jede weiteren 10 Gold = 1 Pfeil). Pro Rolle einstellbar in `gold-rewards.yml`.
- Stirbt der Detektiv, fällt sein Bogen auf den Boden – jeder Unschuldige kann ihn aufheben (nicht der Mörder).
- Erschießt ein Unschuldiger einen anderen Unschuldigen, sterben **beide**.
- **Sieg Unschuldige:** Mörder tot oder Zeit (4:30) abgelaufen. **Sieg Mörder:** alle anderen tot.
- Tote werden Zuschauer; ihr Chat ist nur für andere Tote sichtbar.
- Nach 10 Sekunden geht es zurück in die Lobby, die nächste Runde startet automatisch.

## 6. Einstellungen

Im Ordner `plugins/MurderMystery/` liegen sechs Dateien:

| Datei | Inhalt |
|---|---|
| `config.yml` | Allgemeines: Auto-Join, Spielerzahlen, Zeiten, Wartelobby |
| `maps.yml` | Alle Maps mit ihren Spawns und wie die Map gewählt wird |
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

**Wahrscheinlichkeits-Anzeige:** In der Lobby zeigt das Scoreboard unter „Deine Chancen“, mit welcher Wahrscheinlichkeit man welche Rolle bekommt, abhängig von der aktuellen Spielerzahl. Für alle Rollen abschalten mit `show-chances: false` ganz oben in der `roles.yml`. Für einzelne Rollen geht das mit `show-chance: false`.

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
