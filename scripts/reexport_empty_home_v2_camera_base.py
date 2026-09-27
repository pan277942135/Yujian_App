#!/usr/bin/env python3
"""Re-export the Frozen V2 capture button as a clean standalone RGBA asset.

The source is the immutable, native-resolution Empty Home reference.  This
tool never reads a screenshot or presentation derivative: it crops only the
approved capture control and replaces all surrounding lake/rock pixels with
transparent alpha.  The existing gold-sweep and breathing-mask masters are
intentionally preserved.
"""

from __future__ import annotations

import hashlib
import json
from math import hypot
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FROZEN = ROOT / "design/system/core_visual_v1/reference/empty_home_v2.png"
SYSTEM_ASSET = ROOT / "design/system/components/primary_capture_button/assets/capture_button_base.png"
RUNTIME_ASSET = ROOT / "app/src/main/assets/empty_home_runtime_v2/camera/camera_button_base.png"
SIZE = 208

# Native-reference coordinates. They are the direct native-resolution mapping
# of the documented [436,1500,644,1708] V2 reference-space button box.
NATIVE_BOX = (380, 1306, 561, 1487)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def clean_button() -> Image.Image:
    source = Image.open(FROZEN).convert("RGBA")
    if source.size != (941, 1672):
        raise SystemExit(f"unexpected frozen dimensions: {source.size}")
    button = source.crop(NATIVE_BOX).resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    alpha = Image.new("L", (SIZE, SIZE))
    pixels = alpha.load()
    # Preserve the frozen gold rim through its antialiased outer edge while
    # making every lake/rock/grass pixel outside the circular control fully
    # transparent. RGB under alpha=0 is cleared as well to prevent matte bleed.
    for y in range(SIZE):
        for x in range(SIZE):
            radius = hypot(x - 103.5, y - 103.5)
            pixels[x, y] = 255 if radius <= 89 else int(max(0, min(255, (92 - radius) * 85)))
    button.putalpha(alpha)
    data = list(button.getdata())
    button.putdata([(r, g, b, a) if a else (0, 0, 0, 0) for r, g, b, a in data])
    return button


def update_contracts(base_sha: str) -> None:
    manifests = [
        ROOT / "app/src/main/assets/empty_home_runtime_v2/config/runtime_manifest.json",
        ROOT / "design/pages/home/empty_home/shared/contracts/runtime_manifest.json",
    ]
    for path in manifests:
        data = json.loads(path.read_text())
        data["asset_revision"] = "HOME_EMPTY_ASSETS_V2.1"
        data["sha256"]["camera/camera_button_base.png"] = base_sha
        write_json(path, data)

    asset_map = ROOT / "design/pages/home/empty_home/platform/android/asset_map.json"
    data = json.loads(asset_map.read_text())
    runtime = data["runtime_assets"]
    runtime["asset_revision"] = "HOME_EMPTY_ASSETS_V2.1"
    runtime["sha256"]["camera/camera_button_base.png"] = base_sha
    write_json(asset_map, data)

    asset_manifest = ROOT / "design/pages/home/empty_home/shared/contracts/asset_manifest.json"
    data = json.loads(asset_manifest.read_text())
    for asset in data["assets"]:
        if asset["asset_id"] == "home_empty_v2_capture_button_base":
            asset["sha256"] = base_sha
            asset["version"] = "2.1"
    write_json(asset_manifest, data)

    checksums = ROOT / "design/pages/home/empty_home/shared/contracts/SHA256SUMS"
    lines = []
    for line in checksums.read_text().splitlines():
        if line.endswith("design/system/components/primary_capture_button/assets/capture_button_base.png"):
            lines.append(f"{base_sha}  design/system/components/primary_capture_button/assets/capture_button_base.png")
        else:
            lines.append(line)
    checksums.write_text("\n".join(lines) + "\n")


def validate(asset: Image.Image) -> None:
    if asset.mode != "RGBA" or asset.size != (SIZE, SIZE):
        raise SystemExit("camera base must be 208x208 RGBA")
    corners = [asset.getpixel(point)[3] for point in ((0, 0), (207, 0), (0, 207), (207, 207))]
    if any(corners):
        raise SystemExit("camera base corners must be transparent")
    if not any(pixel[3] == 0 for pixel in asset.getdata()):
        raise SystemExit("camera base must contain transparent exterior pixels")


def main() -> None:
    if sha(FROZEN) != "30f95fc68b65d5a55552ba279753cac1fd979216679217377e43cea8e33cb85b":
        raise SystemExit("frozen source SHA mismatch")
    asset = clean_button()
    validate(asset)
    for path in (SYSTEM_ASSET, RUNTIME_ASSET):
        path.parent.mkdir(parents=True, exist_ok=True)
        asset.save(path, "PNG", optimize=True)
    if sha(SYSTEM_ASSET) != sha(RUNTIME_ASSET):
        raise SystemExit("system and runtime camera assets diverged")
    update_contracts(sha(RUNTIME_ASSET))
    print(json.dumps({"status": "PASS", "source": str(FROZEN.relative_to(ROOT)), "source_sha256": sha(FROZEN), "asset_sha256": sha(RUNTIME_ASSET), "dimensions": [SIZE, SIZE]}, indent=2))


if __name__ == "__main__":
    main()
