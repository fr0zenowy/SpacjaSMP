# Jak to jest zrobione

Plugin to jedna aplikacja `JavaPlugin` (`pl.zamrozenie.ZamrozeniePlugin`) z około 19 tys. linii Javy w 139 plikach. Poniżej skrót tego, jak jest poukładany.

## Układ pakietów

```
pl.zamrozenie
├── ZamrozeniePlugin        punkt wejścia: tworzy managery, rejestruje komendy i listenery
├── *Manager                stan i logika jednego systemu (Lifesteal, Zrzut, Dragon, Shop, Market, Drop...)
├── *Listener               zdarzenia Bukkita (jeden listener = jedna sprawa)
├── *Command                komendy (CommandExecutor i TabCompleter)
├── *GuiHolder              marker InventoryHolder dla okien GUI
├── LegendaryItems          fabryka przedmiotów z umiejętnościami
├── anticheat/              własny anticheat (Check, MovementChecks, CombatChecks, WorldChecks, BehaviorAnalyzer)
└── clans/                  system klanów: commands/, listeners/, managers/, models/, utils/
```

## Najważniejsze decyzje

* **Przedmioty rozpoznaje się po NBT, nie po nazwie.** `LegendaryItems` ustawia w `PersistentDataContainer` klucz `legendary_id` i `CustomModelData`. Dzięki temu zmiana nazwy w kowadle albo materiału nic nie psuje, a paczka zasobów podpina modele po `CustomModelData` (format `items/*.json` z `range_dispatch`).
* **Stan przedmiotu siedzi w przedmiocie.** Ładunek Warden Sword i tryb Kataklizmu Pustki są w jego `PersistentDataContainer`, więc przechodzą z graczem do skrzyni i z powrotem.
* **Okna GUI to `InventoryHolder`.** Każde okno ma własną klasę-marker (`ShopGuiHolder`, `DropGuiHolder`, `MarketGuiHolder`, `TutorialGuiHolder`...), a listener sprawdza `getHolder()`, więc kliknięcia w cudzy ekwipunek nie są pomylone z oknem pluginu. Tła okien (sklep, drop, rynek) są w paczce zasobów jako tekstury.
* **Efekty wizualne bez własnych modeli.** Czarna dziura jest zbudowana z `BlockDisplay` (rdzeń z czarnego betonu, obracające się płytki) i cząsteczek pyłu, a poruszanie jest liczone co tick. Pasek ładunku i tryb to action bar.
* **Cooldowny wbudowane tam, gdzie się da.** `Player#setCooldown(Material, ticks)` pokazuje nakładkę w ekwipunku, a dłuższe (Kataklizm: 150 s) trzyma mapa `UUID → czas`.
* **Klany są osobnym podsystemem** z własnymi modelami (`Clan`, `ClanMember`, `ClanRank`, `Alliance`) i wzorcem komend z interfejsem `SubCommand`: `ClanCommand` trzyma mapę podkomend po nazwie.
* **AntiCheat dzieli się na checki.** `Check` to `enum` z wagą VL (violation level) i opisem, a `MovementChecks`, `CombatChecks`, `WorldChecks` są listenerami, które zgłaszają naruszenia do jednego `AntiCheat` z zanikaniem VL w czasie.
* **Zależności opcjonalne.** PlaceholderAPI i MythicMobs są w `softdepend`: brak PAPI wyłącza tylko `%zamrozenie_money%`, a brak MythicMobs wyłącza integrację z bossem (`MythicHook`).

## Dane na dysku

Wszystko trafia do `plugins/Zamrozenie/` jako YAML:

| Plik | Co zawiera |
|---|---|
| `config.yml` | ustawienia (sklep, anticheat, walka, limity, boss) |
| `lifesteal.yml` | serca graczy, bany, ochrony |
| `homes.yml` | domy graczy (2 sloty) |
| `zrzut.yml` | harmonogram i stan zrzutu |
| `shop-items.yml`, `shop-balances.yml`, `shop-player-locks.yml` | oferty, salda i osobiste obniżki cen |
| `rynek-listings.yml` | oferty rynku graczy |
| `boss-drops.yml`, `boss-arena.yml` | pule dropu i stan areny bossa |
| `player-stats.yml`, `historical-stats.yml` | statystyki zabójstw i śmierci |
| `combat-log-respawn.yml` | kary za wylog w walce |
| `clans/` | klany i skarbce klanów |

Zapis dzieje się w `onDisable` i przy zmianach. Wyjątek: zawartość [Brzuszka Smoka](ITEMY.md#brzuszek-smoka) jest trzymana tylko w pamięci.

## Budowanie

Wymagania: JDK 17 lub nowszy i Maven. Plugin celuje w Java 17, a serwer Paper 1.21.11 działa na Javie 21.

```bash
mvn package
```

Zależności: `spigot-api 1.21.11-R0.1-SNAPSHOT` i `placeholderapi 2.11.6` (obie `provided`). `maven-jar-plugin` zapisuje gotowy `Zamrozenie.jar` w katalogu głównym projektu (`outputDirectory` = `${project.basedir}`). Jar wrzucasz do `plugins/`. Sprawdziłem, że `mvn -o package` na czystej kopii tych źródeł kończy się sukcesem.

## Czego tu nie ma

* **Testów automatycznych.** Katalogu `src/test` nie ma, plugin był sprawdzany ręcznie na serwerze testowym.
* **Plików MythicMobs, ModelEngine i paczki zasobów.** Są osobno, bo zawierają duże modele i tekstury, a niektóre pochodzą od innych autorów.
* **Konfiguracji sekretów.** Plugin ich nie potrzebuje.
