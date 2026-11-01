#!/usr/bin/env python3
"""Create native-size Login frozen/structured comparison evidence without rescaling."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageEnhance


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--render", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id")
    args = parser.parse_args()
    reference_path = args.reference.resolve()
    render_path = args.render.resolve()
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    with Image.open(reference_path) as source, Image.open(render_path) as candidate:
        reference = source.convert("RGBA")
        rendered = candidate.convert("RGBA")
    if reference.size != (864, 1536):
        raise SystemExit(f"frozen Login authority has unexpected dimensions: {reference.size}")
    if rendered.size != reference.size:
        raise SystemExit(f"candidate is not the native-size comparison canvas: {rendered.size} != {reference.size}; comparison will not resize either image")
    overlay = Image.blend(reference, rendered, 0.5)
    difference = ImageChops.difference(reference, rendered).convert("RGB")
    difference = ImageEnhance.Contrast(difference).enhance(3.0)
    overlay_path = output_dir / "login-v2_1-overlay-50.png"
    difference_path = output_dir / "login-v2_1-difference.png"
    reference_copy_path = output_dir / "login-v2_1-frozen-reference.png"
    overlay.save(overlay_path, optimize=True)
    difference.save(difference_path, optimize=True)
    reference.save(reference_copy_path, optimize=True)
    diff = ImageChops.difference(reference, rendered)
    extrema = diff.getextrema()
    total = reference.width * reference.height * 4
    absolute_error = sum(sum(channel) for channel in extrema)  # bounded diagnostic; not a pass threshold
    pixels = list(diff.getdata())
    changed = sum(1 for pixel in pixels if any(channel != 0 for channel in pixel))
    report = {
        "page_id": "auth_login_v2",
        "authority_version": "V2.1",
        "source_sha": args.source_sha,
        "run_id": str(args.run_id) if args.run_id else None,
        "reference": {"path": str(reference_path), "sha256": sha256(reference_path), "dimensions_px": [864, 1536]},
        "render": {"path": str(render_path), "sha256": sha256(render_path), "dimensions_css_px": [864, 1536]},
        "comparison": {
            "method": "exact-size 50% alpha overlay plus per-pixel absolute difference; no resize, crop, or warp",
            "mean_absolute_channel_error": round(sum(sum(pixel) for pixel in pixels) / total, 4),
            "changed_pixel_count": changed,
            "changed_pixel_fraction": round(changed / (reference.width * reference.height), 6),
            "overlay_path": overlay_path.name,
            "overlay_sha256": sha256(overlay_path),
            "difference_path": difference_path.name,
            "difference_sha256": sha256(difference_path),
            "frozen_reference_copy_path": reference_copy_path.name,
            "frozen_reference_copy_sha256": sha256(reference_copy_path),
            "interpretation": "MEASUREMENT_ONLY_NOT_VISUAL_PASS; no acceptance threshold is inferred"
        },
        "visual_review_status": "USER_VISUAL_ACCEPTANCE_PENDING"
    }
    report_path = output_dir / "login-v2_1-visual-comparison-report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
