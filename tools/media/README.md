# Jak powstały obrazki

Rendery, animacje i karty w `docs/media/` są wygenerowane skryptami z tego folderu. To zwykły Python z samym Pillow (bez numpy, bez Blendera), a pliki z gry czytane są wprost z klienta Minecrafta 1.21.11.

**Ten folder generuje rendery i schematy, a nie nagrania.** Nagrania z serwera testowego są przycinane osobnym skryptem: [`tools/clips/make_clips.py`](../clips/make_clips.py). Przedmioty to rendery modeli i tekstur z paczki zasobów serwera, a animacje scen są złożone z prawdziwych tekstur Minecrafta (gracz ze skinem Steve, bloki, cząsteczki, sprite'y HUD i bossbara) oraz z liczb z kodu pluginu: zasięgi, czasy, obrażenia, prędkości. Pokazują, *jak działa* przedmiot, a nie jak wygląda w grze z cząsteczkami i dźwiękiem.

## Skrypty

| Plik | Co robi |
|---|---|
| `mc.py` | wczytuje tekstury z klienta i z paczki, czcionkę Minecrafta (ASCII, polskie litery, unifont dla symboli), rysuje tekst z kodami `§` i tooltipy, zapisuje GIF-y |
| `render3d.py` | mały renderer 3D: rzut ortogonalny, algorytm malarza, teksturowane ściany, modele JSON z paczki (elementy, obroty, UV), wytłoczone sprite'y i model gracza |
| `scene.py` | scena: kamera, podłoga, gracz z przedmiotem w ręku, HUD (action bar, bossbar, serca, ikony efektów) |
| `items.py` | definicje 12 przedmiotów (teksty skopiowane 1:1 z kodu) i rendery obrotowe |
| `make_items.py` | generuje `docs/media/items/*.gif` i `*-tooltip.png` |
| `make_scenes.py`, `scenes_more.py` | animacje mechanik: Warden Sword, czarna dziura, kilof 3×3, klatka pojedynku, Smoczy Szpon |
| `make_cards.py` | karty systemów (`docs/media/systems/*.png`) i galeria okien GUI |

## Uruchamianie

Potrzebne: Python 3.10+ z Pillow (`pip install pillow`) oraz klient Minecrafta 1.21.11 (np. z Modrinth App). Ścieżki ustawia się zmiennymi środowiskowymi, a domyślne wskazują na mój komputer:

| Zmienna | Co wskazuje |
|---|---|
| `MC_CLIENT_JAR` | jar klienta 1.21.11 |
| `MC_ASSETS_DIR` | katalog `assets` z `indexes/` i `objects/` |
| `MC_ASSET_INDEX` | numer indeksu assetów (np. `29`) |
| `ZAM_PACK_DIR` | katalog `assets` z paczki zasobów serwera (modele i tekstury przedmiotów) |

```bash
cd tools/media
python -X utf8 make_items.py
python -X utf8 make_scenes.py
python -X utf8 make_cards.py
```

Zasoby Mojang (tekstury, czcionka) i paczki zasobów serwera nie są częścią repozytorium. Wygenerowane obrazki zawierają ich fragmenty, więc traktuj je jako materiał do prezentacji projektu.

## Czego te obrazki nie pokazują

* dźwięków i prawdziwych cząsteczek z gry (animacje używają uproszczonych kropek i sprite'ów z klienta),
* dokładnej fizyki ruchu gracza (np. wciąganie przez czarną dziurę jest przybliżeniem wzoru z kodu),
* wyglądu przedmiotów, których tekstury w paczce jeszcze nie ma (Berło Furii, Dynia Bossa, Kataklizm Pustki i Kilof używają zwykłych modeli z gry).
