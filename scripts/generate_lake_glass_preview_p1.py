#!/usr/bin/env python3
"""Create a compact P1 preview from canonical Master and layered output."""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps, ImageFont

PKG = Path("design/system/backgrounds/lake_glass_v2")


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    args = ap.parse_args()
    root = Path(args.root).resolve()
    ref = root / PKG / "assets/reference"
    manifest_path = root / PKG / "assets/metadata/asset_manifest_v3.json"
    master = Image.open(ref / "static_master.png").convert("RGB")
    layered = Image.open(ref / "static_layered_v3.png").convert("RGB")
    clean = Image.open(root / PKG / "assets/base/lake_base_clean_v2.png").convert("RGB")
    font = ImageFont.load_default()
    cards = []
    for label, im in [("STATIC MASTER", master), ("STATIC LAYERED V3", layered), ("CLEAN PLATE (SUNLIGHT BAKED)", clean)]:
        thumb = ImageOps.contain(im, (360, 780), Image.Resampling.LANCZOS)
        card = Image.new("RGB", (390, 830), (245, 248, 248))
        d = ImageDraw.Draw(card)
        d.text((14, 10), label, fill=(10, 43, 52), font=font)
        card.paste(thumb, ((390-thumb.width)//2, 36))
        cards.append(card)
    out = Image.new("RGB", (390 * 3 + 28, 858), (220, 230, 230))
    for i, card in enumerate(cards):
        out.paste(card, (7 + i * 397, 7))
    preview_path = ref / "p1_preview_summary.png"
    out.save(preview_path, optimize=True)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    rel = (PKG / "assets/reference/p1_preview_summary.png").as_posix()
    record = {
        "path": rel,
        "sha256": digest(preview_path),
        "sizeBytes": preview_path.stat().st_size,
        "format": "PNG",
        "dimensionsPx": list(out.size),
        "mode": "RGB",
        "colorSpace": "sRGB",
        "decodedMemoryBytesRgba8888": out.width * out.height * 4,
    }
    manifest["derivedFiles"] = [x for x in manifest.get("derivedFiles", []) if x.get("path") != rel] + [record]
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"preview": rel, "sha256": record["sha256"], "sizePx": out.size}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
