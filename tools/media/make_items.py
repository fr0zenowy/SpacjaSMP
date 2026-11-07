"""Generuje rendery przedmiotow (obracajace sie GIF-y) i tooltipy do docs/media/items/.

Uzycie:  python -X utf8 make_items.py [id ...]     (bez argumentow: wszystkie)
         python -X utf8 make_items.py --sheet SCIEZKA.png   (tylko arkusz podgladu)
"""
import os
import sys

from PIL import Image

import items
import mc

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "docs", "media", "items"))


def main(argv):
    if argv and argv[0] == "--sheet":
        sheet_path = argv[1]
        tiles = [items.turntable(i, size=240, frames=1)[0] for i in items.ITEMS]
        cols = 4
        rows = (len(tiles) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * 240, rows * 240))
        for n, t in enumerate(tiles):
            sheet.paste(t.convert("RGB"), ((n % cols) * 240, (n // cols) * 240))
        sheet.save(sheet_path)
        return
    os.makedirs(OUT, exist_ok=True)
    wanted = set(argv)
    for it in items.ITEMS:
        if wanted and it["id"] not in wanted:
            continue
        frames = items.turntable(it, size=320, frames=40)
        mb = mc.save_gif(frames, os.path.join(OUT, it["id"] + ".gif"), ms=50, colors=128, max_mb=1.2)
        items.tooltip_image(it, scale=3).save(os.path.join(OUT, it["id"] + "-tooltip.png"), optimize=True)
        print(f"{it['id']:14s} {mb:.2f} MB")


if __name__ == "__main__":
    main(sys.argv[1:])
