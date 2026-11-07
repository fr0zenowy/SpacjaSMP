"""Przycina nagrania z serwera testowego i robi z nich GIF-y (do README) oraz MP4 bez dźwięku.

Wejście: oryginalne nagrania 1280x720 (OBS) z folderu Videos. Skrypt:
  * wycina wybrane fragmenty (bez okna OBS na końcu nagrań),
  * obcina pasek tytułu okna i pasek zadań Windows,
  * rozmywa półprzezroczystą nakładkę Discorda z lewej strony (nicki osób na rozmowie),
  * dla okien GUI wycina sam środek ekranu (bez przycisków innego moda),
  * składa GIF (z paletą liczoną na cały klip) i MP4 bez ścieżki dźwiękowej.

Uzycie:  python -X utf8 make_clips.py [nazwa ...]        (bez argumentów: wszystkie)
Zmienne: ZAM_CLIPS_DIR (folder z nagraniami), ZAM_TMP (folder roboczy)
Wymaga: pip install imageio-ffmpeg (daje ffmpeg z libx264).
"""
import os
import subprocess
import sys
import tempfile

import imageio_ffmpeg

FF = imageio_ffmpeg.get_ffmpeg_exe()
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "docs", "media", "clips"))
SRC = os.environ.get("ZAM_CLIPS_DIR", r"C:\Users\piotr\Videos")
TMP = os.environ.get("ZAM_TMP") or tempfile.mkdtemp(prefix="zamclips_")

# Cały ekran gry: bez paska tytułu (góra 16 px) i paska zadań (dół 32 px).
FULL = (0, 16, 1280, 672)
# Środek ekranu z oknem GUI (bez przycisków moda po bokach).
GUI = (340, 160, 580, 360)
# Nakładka Discorda z lewej: rozmywana, bo pokazuje nicki osób na rozmowie.
BLUR = (4, 250, 196, 104)

SPECS = {
    "zamrozenie": dict(src="14-12-29", segs=[(0.3, 5.5)], crop=FULL, blur=True, width=600, fps=8, colors=48),
    "kilof-otchlani": dict(src="14-13-22", segs=[(0.3, 4.7)], crop=FULL, blur=True, width=600, fps=12, colors=80),
    "warden-sonic-boom": dict(src="14-21-52", segs=[(0.1, 2.7)], crop=FULL, blur=True, width=600, fps=14, colors=80),
    "warden-trafienia": dict(src="14-17-18", segs=[(10.8, 17.6)], crop=FULL, blur=True, width=600, fps=10, colors=80),
    "smoczy-szpon": dict(src="14-26-14", segs=[(1.2, 10.6)], crop=FULL, blur=True, width=560, fps=10, colors=64),
    "zrzut": dict(src="14-25-15", segs=[(4.5, 8.5), (9.8, 11.9), (16.6, 21.6)], crop=FULL, blur=True, width=560, fps=10, colors=64),
    "smok-kresu": dict(src="14-26-55", segs=[(1.0, 7.4), (10.4, 14.7)], crop=FULL, blur=True, width=560, fps=10, colors=64),
    "brzuszek-gui": dict(src="14-25-45", segs=[(13.0, 15.9)], crop=GUI, blur=False, width=640, fps=10, colors=96),
    "drop-gui": dict(src="14-14-22", segs=[(1.4, 7.6)], crop=GUI, blur=False, width=640, fps=10, colors=96),
    "rynek-gui": dict(src="14-24-16", segs=[(1.0, 9.8)], crop=GUI, blur=False, width=640, fps=10, colors=96),
}


def run(args):
    p = subprocess.run([FF, "-hide_banner", "-loglevel", "error", "-y"] + args, capture_output=True, text=True)
    if p.returncode:
        raise RuntimeError(p.stderr[-800:])


def prefilter(spec):
    cx, cy, cw, ch = spec["crop"]
    vf = []
    if spec["blur"]:
        bx, by, bw, bh = BLUR
        vf.append(f"split[a][b];[b]crop={bw}:{bh}:{bx}:{by},boxblur=14:3[bb];[a][bb]overlay={bx}:{by}")
    vf.append(f"crop={cw}:{ch}:{cx}:{cy}")
    return ",".join(vf)


def make(name):
    spec = SPECS[name]
    src = os.path.join(SRC, f"2026-11-07 {spec['src']}.mp4")
    parts = []
    for i, (s, e) in enumerate(spec["segs"]):
        part = os.path.join(TMP, f"{name}_{i}.mp4")
        run(["-ss", f"{s}", "-to", f"{e}", "-i", src, "-an", "-vf", prefilter(spec), "-r", "30",
             "-c:v", "libx264", "-crf", "14", "-preset", "veryfast", "-pix_fmt", "yuv420p", part])
        parts.append(part)
    joined = os.path.join(TMP, f"{name}_joined.mp4")
    if len(parts) == 1:
        joined = parts[0]
    else:
        lst = os.path.join(TMP, f"{name}.txt")
        with open(lst, "w", encoding="utf-8") as f:
            for p in parts:
                f.write("file '" + p.replace("\\", "/") + "'\n")
        run(["-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", joined])
    os.makedirs(OUT, exist_ok=True)
    w = spec["width"]
    gif = os.path.join(OUT, name + ".gif")
    vf = (f"fps={spec['fps']},scale={w}:-1:flags=lanczos,split[s0][s1];"
          f"[s0]palettegen=max_colors={spec['colors']}:stats_mode=diff[p];"
          f"[s1][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle")
    run(["-i", joined, "-vf", vf, "-loop", "0", gif])
    mp4 = os.path.join(OUT, name + ".mp4")
    run(["-i", joined, "-an", "-vf", f"scale={int(w * 1.5) // 2 * 2}:-2:flags=lanczos", "-c:v", "libx264",
         "-crf", "27", "-preset", "slow", "-pix_fmt", "yuv420p", "-movflags", "+faststart", mp4])
    print(f"{name:20s} GIF {os.path.getsize(gif) / 1048576:.2f} MB   MP4 {os.path.getsize(mp4) / 1048576:.2f} MB")


if __name__ == "__main__":
    names = sys.argv[1:] or list(SPECS)
    for n in names:
        make(n)
