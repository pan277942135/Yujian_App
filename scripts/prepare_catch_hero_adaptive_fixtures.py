#!/usr/bin/env python3
"""Build reproducible, explicitly labeled test fixtures from licensed photographs."""

from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app/src/androidTest/assets/catch_hero_adaptive_v1_1"
SOURCE_ROOT = ASSET_ROOT / "source_photos"
FIXTURE_ROOT = ASSET_ROOT / "fixtures"

SOURCES = {
    "repo": {
        "file": "yujian_repo_sample_recent_catch.jpg",
        "title": "Yujian repository bundled sample_recent_catch.jpg",
        "author": "Repository asset; external author metadata is not present in the repository",
        "source_url": "https://github.com/pan277942135/Yujian_App/blob/main/app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg",
        "license": "Repository-authorized test sample; redistribution outside this repository is not independently licensed",
        "license_url": "",
        "expected_sha256": "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef",
        "expected_bytes": 292222,
        "download_url": "",
    },
    "gambia": {
        "file": "gambia_rod_caught_original.jpg",
        "title": "Fish caught with a rod in Gambia.jpg",
        "author": "Peter van der Sluijs",
        "source_url": "https://commons.wikimedia.org/wiki/File:Fish_caught_with_a_rod_in_Gambia.jpg",
        "license": "CC BY-SA 3.0 (or GFDL, as offered on the source page)",
        "license_url": "https://creativecommons.org/licenses/by-sa/3.0/",
        "expected_sha256": "6117d964dd10ee8f3f38d82b78441c7e03a2402cbfd623e6d05d9d2d75685581",
        "expected_bytes": 999273,
        "bundled_path": "app/src/androidTest/assets/catch_hero_adaptive_v1_1/source_photos/gambia_rod_caught_original.jpg",
        "download_url": "https://upload.wikimedia.org/wikipedia/commons/8/8d/Fish_caught_with_a_rod_in_Gambia.jpg",
    },
    "auckland": {
        "file": "auckland_portrait_caught_fish_original.jpg",
        "title": "Portrait of man on boat holding a caught fish (AM 81857-1).jpg",
        "author": "Photograph by Collins; Auckland War Memorial Museum collection",
        "source_url": "https://commons.wikimedia.org/wiki/File:Portrait_of_man_on_boat_holding_a_caught_fish_(AM_81857-1).jpg",
        "license": "CC BY 4.0",
        "license_url": "https://creativecommons.org/licenses/by/4.0/",
        "expected_sha256": "d806491e4711a58aa242c8ef3feb6fcc0e9b13c56c5db5e7808203134b3c1ffa",
        "expected_bytes": 626677,
        "bundled_path": "app/src/androidTest/assets/catch_hero_adaptive_v1_1/source_photos/auckland_portrait_caught_fish_original.jpg",
        "download_url": "https://upload.wikimedia.org/wikipedia/commons/4/4b/Portrait_of_man_on_boat_holding_a_caught_fish_%28AM_81857-1%29.jpg",
    },

}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_oriented(source_key: str) -> Image.Image:
    with Image.open(SOURCE_ROOT / SOURCES[source_key]["file"]) as image:
        return ImageOps.exif_transpose(image).convert("RGB")


def ensure_source_photos() -> None:
    SOURCE_ROOT.mkdir(parents=True, exist_ok=True)
    repo_source = ROOT / "app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg"
    for key, source in SOURCES.items():
        path = SOURCE_ROOT / source["file"]
        if not path.is_file():
            if key == "repo":
                shutil.copyfile(repo_source, path)
            elif source.get("bundled_path"):
                shutil.copyfile(ROOT / source["bundled_path"], path)
            else:
                raise FileNotFoundError(f"source photo is not bundled: {source['file']}")
        actual_hash = sha256(path)
        if actual_hash != source["expected_sha256"] or path.stat().st_size != source["expected_bytes"]:
            raise ValueError(
                f"source integrity mismatch for {source['file']}: "
                f"sha256={actual_hash} bytes={path.stat().st_size}"
            )


def save_jpeg(image: Image.Image, filename: str) -> Path:
    destination = FIXTURE_ROOT / filename
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, "JPEG", quality=94, subsampling=0, optimize=True)
    return destination


def fit_canvas(source: Image.Image, size: tuple[int, int], black_bars: bool = False) -> Image.Image:
    width, height = size
    if black_bars:
        canvas = Image.new("RGB", size, (0, 0, 0))
    else:
        ambient = ImageOps.fit(source, size, method=Image.Resampling.LANCZOS)
        ambient = ambient.filter(ImageFilter.GaussianBlur(radius=max(18, round(max(size) * 0.035))))
        canvas = ImageEnhance.Brightness(ambient).enhance(0.72)
        overlay = Image.new("RGB", size, (20, 34, 43))
        canvas = Image.blend(canvas, overlay, 0.12)

    foreground = ImageOps.contain(source, size, method=Image.Resampling.LANCZOS)
    x = (width - foreground.width) // 2
    y = (height - foreground.height) // 2
    canvas.paste(foreground, (x, y))
    return canvas


def main() -> None:
    ensure_source_photos()
    FIXTURE_ROOT.mkdir(parents=True, exist_ok=True)
    fixture_specs = [
        ("landscape_4_3_gambia_original.jpg", None, "Original public photograph, no pixel transformation.", "gambia", "horizontal 4:3"),
        ("landscape_16_9_gambia_ambient_fixture.jpg", None, "Derived fixture: full source photo contained over a softened same-source ambient canvas; no subject crop.", "gambia", "horizontal 16:9"),
        ("portrait_3_4_repo_original.jpg", None, "Original repository photograph, no pixel transformation.", "repo", "vertical 3:4"),
        ("portrait_9_16_repo_ambient_fixture.jpg", None, "Derived fixture: full source photo contained over a softened same-source ambient canvas; no subject crop.", "repo", "vertical 9:16"),
        ("portrait_extreme_1_3_auckland_fixture.jpg", None, "Derived 1:3 canvas fixture: full source photo contained over a softened same-source ambient canvas; no generated content.", "auckland", "extreme vertical 1:3"),
        ("fish_near_left_edge_auckland_crop_fixture.jpg", None, "Derived crop fixture from the real photograph. The complete fish silhouette is retained with its tail close to the left frame edge. No detector box is claimed.", "auckland", "fish near frame edge"),
        ("portrait_black_bars_repo_fixture.jpg", None, "Derived black-bar fixture: original photo centered without scaling distortion on explicit pure-black top and bottom bars; original is preserved separately.", "repo", "black bars present"),
        ("no_trusted_bbox_gambia_original.jpg", None, "Original public photograph; no trusted fish detection box is available or asserted.", "gambia", "no trusted fish box"),
    ]
    expected_fixtures = {item[0] for item in fixture_specs}
    for stale in FIXTURE_ROOT.glob("*.jpg"):
        if stale.name not in expected_fixtures:
            stale.unlink()
    expected_sources = {source["file"] for source in SOURCES.values()}
    for stale in SOURCE_ROOT.glob("*"):
        if stale.is_file() and stale.name not in expected_sources:
            stale.unlink()

    images = {key: load_oriented(key) for key in SOURCES}

    fixture_images = {
        "landscape_4_3_gambia_original.jpg": images["gambia"],
        "landscape_16_9_gambia_ambient_fixture.jpg": fit_canvas(images["gambia"], (1920, 1080)),
        "portrait_3_4_repo_original.jpg": images["repo"],
        "portrait_9_16_repo_ambient_fixture.jpg": fit_canvas(images["repo"], (1080, 1920)),
        "portrait_extreme_1_3_auckland_fixture.jpg": fit_canvas(images["auckland"], (600, 1800)),
        "fish_near_left_edge_auckland_crop_fixture.jpg": images["auckland"].crop((235, 325, 735, 1110)),
        "portrait_black_bars_repo_fixture.jpg": fit_canvas(images["repo"], (1080, 1920), black_bars=True),
        "no_trusted_bbox_gambia_original.jpg": images["gambia"],
    }

    manifest = {
        "purpose": "Android runtime visual matrix for YuJianCatchHeroCard Adaptive Media V1.1",
        "synthetic_ai_images": False,
        "notes": [
            "All visual test content is a real photograph from the repository or a public source listed below.",
            "Files labeled derived_fixture are explicitly transformed from the named original and must not be described as unmodified originals.",
            "No fixture claims a trusted fish detector box. Runtime RemoteCatch does not persist or expose one.",
            "CC BY-SA adaptations remain available under the corresponding ShareAlike license; source attribution is retained here.",
        ],
        "sources": {},
        "fixtures": [],
    }
    for key, source in SOURCES.items():
        source_path = SOURCE_ROOT / source["file"]
        with Image.open(source_path) as raw:
            raw_size = list(raw.size)
            exif_orientation = raw.getexif().get(274, 1)
        with Image.open(source_path) as normalized:
            normalized_size = list(ImageOps.exif_transpose(normalized).size)
        manifest["sources"][key] = {
            **source,
            "path": str(source_path.relative_to(ROOT)),
            "sha256": sha256(source_path),
            "bytes": source_path.stat().st_size,
            "raw_pixel_dimensions": raw_size,
            "exif_orientation": exif_orientation,
            "display_dimensions_after_exif": normalized_size,
        }

    for filename, _, transformation, source_key, use in fixture_specs:
        image = fixture_images[filename]
        path = FIXTURE_ROOT / filename
        if transformation.startswith("Original"):
            shutil.copyfile(SOURCE_ROOT / SOURCES[source_key]["file"], path)
        else:
            save_jpeg(image, filename)
        with Image.open(path) as fixture_image:
            fixture_size = list(fixture_image.size)
        manifest["fixtures"].append({
            "id": Path(filename).stem,
            "file": str(path.relative_to(ROOT)),
            "sha256": sha256(path),
            "bytes": path.stat().st_size,
            "dimensions": fixture_size,
            "test_use": use,
            "provenance": "original" if "Original" in transformation else "derived_fixture",
            "source_key": source_key,
            "source_url": SOURCES[source_key]["source_url"],
            "source_author": SOURCES[source_key]["author"],
            "source_license": SOURCES[source_key]["license"],
            "transformation": transformation,
            "trusted_fish_box": False,
            "expected_home_mode": "EVIDENCE_FIT",
            "expected_detail_mode": "EVIDENCE_FIT",
        })

    (ASSET_ROOT / "fixture_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({"sources": len(SOURCES), "fixtures": len(fixture_specs), "manifest": str((ASSET_ROOT / 'fixture_manifest.json').relative_to(ROOT))}, indent=2))


if __name__ == "__main__":
    main()
