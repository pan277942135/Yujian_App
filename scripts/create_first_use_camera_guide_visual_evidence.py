#!/usr/bin/env python3
"""Create exact-reference overlays and diffs for production guide screenshots."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path

from PIL import Image, ImageChops, ImageStat
from verify_runtime_evidence_source_closure import evaluate_guide


REPO_ROOT = Path(__file__).resolve().parents[1]
FROZEN_ROOT = REPO_ROOT / "design/pages/recognition/first_use_camera_guide/frozen"
REPORT_NAME = "first_use_camera_guide_runtime_report.json"
PAGE_FILES = {
    "P01": (
        "P01_runtime.png",
        "P01_First_Use_Camera_Guide_Frozen.png",
        "dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50",
        1671,
    ),
    "P02": (
        "P02_runtime.png",
        "P02_First_Use_Camera_Guide_Frozen.png",
        "fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f",
        1671,
    ),
    "P03": (
        "P03_runtime.png",
        "P03_First_Use_Camera_Guide_Frozen.png",
        "1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3",
        1672,
    ),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def rect(left: int, top: int, right: int, bottom: int) -> dict[str, int]:
    return {"left": left, "top": top, "right": right, "bottom": bottom}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--build-sha", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    report_path = args.runtime_dir / REPORT_NAME
    report = json.loads(report_path.read_text(encoding="utf-8"))
    pages = {item.get("page"): item for item in report.get("pages", [])}
    profile_failures: list[str] = []
    if report.get("api_level") != 28:
        profile_failures.append(f"api_level={report.get('api_level')} expected=28")
    viewport = report.get("viewport_px", {})
    if viewport.get("width") != 1080 or viewport.get("height") != 1920:
        profile_failures.append(
            f"viewport={viewport.get('width')}x{viewport.get('height')} expected=1080x1920"
        )
    try:
        font_scale = float(report.get("font_scale"))
    except (TypeError, ValueError):
        font_scale = -1.0
    if abs(font_scale - 1.0) > 0.01:
        profile_failures.append(f"font_scale={report.get('font_scale')} expected=1.0")

    visual_pages: list[dict] = []
    for page, (runtime_name, frozen_name, expected_sha, frozen_height) in PAGE_FILES.items():
        runtime_path = args.runtime_dir / runtime_name
        frozen_path = FROZEN_ROOT / frozen_name
        if not runtime_path.is_file() or not frozen_path.is_file():
            raise SystemExit(f"required runtime/reference image is missing: {runtime_path} / {frozen_path}")
        actual_sha = sha256(frozen_path)
        if actual_sha != expected_sha:
            raise SystemExit(f"Frozen {page} SHA mismatch: {actual_sha} != {expected_sha}")

        with Image.open(runtime_path) as image:
            runtime = image.convert("RGB")
        if runtime.size != (1080, 1920):
            profile_failures.append(
                f"{page}_runtime_dimensions={runtime.size[0]}x{runtime.size[1]} expected=1080x1920"
            )
        with Image.open(frozen_path) as image:
            frozen = image.convert("RGB")
        runtime_width, runtime_height = runtime.size
        if frozen.size != (941, frozen_height):
            raise SystemExit(f"Frozen {page} dimensions mismatch: {frozen.size}")

        scale = runtime_width / 941.0
        scaled_height = round(frozen_height * scale)
        offset_y = round((runtime_height - frozen_height * scale) / 2.0)
        mapped = Image.new("RGB", runtime.size, (0, 0, 0))
        resized = frozen.resize((runtime_width, scaled_height), Image.Resampling.LANCZOS)
        paste_y = max(0, offset_y)
        source_top = max(0, -offset_y)
        visible_height = min(scaled_height - source_top, runtime_height - paste_y)
        if visible_height > 0:
            mapped.paste(resized.crop((0, source_top, runtime_width, source_top + visible_height)), (0, paste_y))

        overlay = Image.blend(runtime, mapped, 0.5)
        diff = ImageChops.difference(runtime, mapped)
        mean_diff = sum(ImageStat.Stat(diff).mean) / 3.0
        threshold = diff.convert("L").point(lambda value: 255 if value >= 24 else 0)
        changed_fraction = sum(threshold.histogram()[1:]) / (runtime_width * runtime_height)

        shutil.copyfile(frozen_path, args.output_dir / f"{page}_frozen_reference_original.png")
        mapped.save(args.output_dir / f"{page}_frozen_reference_mapped.png")
        overlay.save(args.output_dir / f"{page}_overlay.png")
        diff.save(args.output_dir / f"{page}_diff.png")

        page_record = pages.get(page, {})
        page_record.update(
            {
                "frozen_reference_original": f"{page}_frozen_reference_original.png",
                "frozen_reference_sha256": actual_sha,
                "frozen_reference_dimensions": {"width": 941, "height": frozen_height},
                "uniform_reference_mapping": {
                    "scale": scale,
                    "offset_x_px": 0.0,
                    "offset_y_px": (runtime_height - frozen_height * scale) / 2.0,
                    "mapped_width_px": runtime_width,
                    "mapped_height_px": frozen_height * scale,
                },
                "comparison_outputs": {
                    "mapped_reference": f"{page}_frozen_reference_mapped.png",
                    "overlay": f"{page}_overlay.png",
                    "diff": f"{page}_diff.png",
                },
                "mean_absolute_difference_0_255": round(mean_diff, 4),
                "pixels_changed_over_24_fraction": round(changed_fraction, 6),
                "visual_parity": "BLOCKED_ASSET_SOURCE",
            }
        )
        visual_pages.append(page_record)

    report["source_commit_sha"] = args.build_sha
    report["apk_sha256_host"] = sha256(args.apk)
    report["apk_bytes_host"] = args.apk.stat().st_size
    report["apk_sha256_match"] = report.get("apk_sha256") == report["apk_sha256_host"]
    report["frozen_source_sha256"] = {page: PAGE_FILES[page][2] for page in PAGE_FILES}
    report["visual_evidence_dir"] = str(args.output_dir)
    report["pages"] = visual_pages
    report["canonical_profile"] = {"api_level": 28, "viewport_px": {"width": 1080, "height": 1920}, "font_scale": 1.0}
    report["runtime_profile_validation"] = {
        "status": "PASS" if not profile_failures else "FAIL",
        "failures": profile_failures,
    }
    verdict = evaluate_guide(report, visual_pages, args.output_dir)
    report["visual_parity"] = verdict["status"]
    report["pass"] = verdict["pass"]
    report["visual_blockers"] = verdict["blockers"]
    report["failure_classification"] = (
        "APK_SHA_MISMATCH" if not report["apk_sha256_match"] else
        "RUNTIME_PROFILE_MISMATCH" if profile_failures else
        "ASSET_SOURCE_BLOCKED" if not verdict["pass"] else None
    )
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    shutil.copyfile(report_path, args.output_dir / REPORT_NAME)
    if not report["apk_sha256_match"]:
        raise SystemExit("installed APK SHA-256 does not match the exact host build APK")
    if profile_failures:
        raise SystemExit("canonical API 28 / 1080x1920 / fontScale 1.0 profile failed: " + "; ".join(profile_failures))
    print("RUNTIME_ROUTE_EVIDENCE=" + str(report.get("runtime_route_evidence", "NOT_RUN")))
    print("CANONICAL_RUNTIME_PROFILE=PASS")
    print("APK_SHA_MATCH=PASS")
    print("VISUAL_PARITY=" + verdict["status"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
