"""
Pripravi fotky z iPhonu pro Xiaomi tak, aby je Galerie seradila podle data.

  python -I xiaomi_prep.py "C:/Users/.../Pictures/iPhone fotky" "C:/Users/.../Pictures/Pro Xiaomi"

- HEIC / JPG s EXIF datem: zkopiruje beze zmeny
- JPG bez data: bezztratove vlozi EXIF DateTimeOriginal (z data souboru)
- PNG (screenshoty): prevede na JPG (q=95) s EXIF datem
  (Android pri kopirovani prepise datum souboru -> bez EXIF by vse bylo "dnes")
- MOV/MP4 bez creation_time: bezztratove doplni (ffmpeg -c copy)
- Live Photo videa (MOV se stejnym jmenem jako fotka) -> podslozka "Live Photo videa" + .nomedia
- .AAE vynecha
Originaly zustanou netknute.
"""
import datetime as dt
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image

SRC = Path(sys.argv[1])
DST = Path(sys.argv[2])
LIVE = DST / "Live Photo videa"
FFMPEG = sys.argv[3] if len(sys.argv) > 3 else shutil.which("ffmpeg")
FFPROBE = str(Path(FFMPEG).with_name("ffprobe.exe")) if FFMPEG else None

DST.mkdir(parents=True, exist_ok=True)
LIVE.mkdir(exist_ok=True)
(LIVE / ".nomedia").touch()

files = sorted(p for p in SRC.iterdir() if p.is_file())
photo_bases = {p.stem.lower() for p in files if p.suffix.lower() in (".heic", ".jpg", ".jpeg", ".png")}


def file_date(p):
    return dt.datetime.fromtimestamp(p.stat().st_mtime)


def exif_bytes(d):
    ex = Image.Exif()
    s = d.strftime("%Y:%m:%d %H:%M:%S")
    ex[306] = s                                  # DateTime
    ifd = ex.get_ifd(0x8769)
    ifd[36867] = s                               # DateTimeOriginal
    ifd[36868] = s                               # DateTimeDigitized
    return ex.tobytes()


def jpg_has_date(p):
    try:
        ex = Image.open(p).getexif()
        return bool(ex.get_ifd(0x8769).get(36867) or ex.get(306))
    except Exception:
        return False


def jpg_insert_exif(src, dst, d):
    """Bezztratove: vlozi APP1 Exif segment hned za SOI (stare APP1 Exif vyhodi)."""
    data = src.read_bytes()
    assert data[:2] == b"\xff\xd8"
    payload = b"Exif\x00\x00" + exif_bytes(d)
    app1 = b"\xff\xe1" + (len(payload) + 2).to_bytes(2, "big") + payload
    i, out = 2, bytearray(b"\xff\xd8")
    # projdi uvodni segmenty, zahod existujici Exif APP1
    while i + 4 <= len(data) and data[i] == 0xFF and data[i + 1] not in (0xDA, 0xD9):
        ln = int.from_bytes(data[i + 2:i + 4], "big")
        seg = data[i:i + 2 + ln]
        if not (data[i + 1] == 0xE1 and seg[4:10] == b"Exif\x00\x00"):
            out += seg
        i += 2 + ln
    # APP0 (JFIF) ma byt prvni, Exif hned za nim
    if out[2:4] == b"\xff\xe0":
        ln = int.from_bytes(out[4:6], "big")
        out = out[:4 + ln] + app1 + out[4 + ln:]
    else:
        out = out[:2] + app1 + out[2:]
    dst.write_bytes(bytes(out) + data[i:])


def video_has_date(p):
    r = subprocess.run([FFPROBE, "-v", "quiet", "-print_format", "json", "-show_format", str(p)],
                       capture_output=True, text=True)
    try:
        return "creation_time" in json.loads(r.stdout)["format"].get("tags", {})
    except Exception:
        return False


def set_mtime(p, d):
    ts = d.timestamp()
    os.utime(p, (ts, ts))


stats = {"kopie": 0, "jpg+exif": 0, "png->jpg": 0, "video+datum": 0, "live": 0, "aae": 0, "chyba": 0}
for i, p in enumerate(files, 1):
    ext = p.suffix.lower()
    d = file_date(p)
    try:
        if ext == ".aae":
            stats["aae"] += 1
            continue
        if ext in (".mov", ".mp4"):
            live = ext == ".mov" and p.stem.lower() in photo_bases
            target = (LIVE if live else DST) / p.name
            if live:
                stats["live"] += 1
            if target.exists():
                continue
            if FFMPEG and not video_has_date(p):
                iso = d.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000000Z")
                subprocess.run([FFMPEG, "-v", "error", "-y", "-i", str(p), "-map", "0", "-c", "copy",
                                "-metadata", f"creation_time={iso}", str(target)], check=True)
                stats["video+datum"] += 1
            else:
                shutil.copy2(p, target)
                stats["kopie"] += 1
        elif ext == ".png":
            target = DST / (p.stem + ".jpg")
            if target.exists():
                target = DST / (p.stem + "_png.jpg")
            if not target.exists():
                im = Image.open(p).convert("RGB")
                im.save(target, "JPEG", quality=95, exif=exif_bytes(d))
            stats["png->jpg"] += 1
        elif ext in (".jpg", ".jpeg"):
            target = DST / p.name
            if not target.exists():
                if jpg_has_date(p):
                    shutil.copy2(p, target)
                    stats["kopie"] += 1
                else:
                    jpg_insert_exif(p, target, d)
                    stats["jpg+exif"] += 1
        else:  # heic a cokoliv dalsiho
            target = DST / p.name
            if not target.exists():
                shutil.copy2(p, target)
            stats["kopie"] += 1
        set_mtime(target, d)
    except Exception as e:
        stats["chyba"] += 1
        print(f"  ! {p.name}: {e}")
    if i % 100 == 0:
        print(f"  ...{i}/{len(files)}", flush=True)

print(stats)
