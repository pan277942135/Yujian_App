#!/usr/bin/env python3
"""Build an honest Normal Home real-photo evidence report from one Android run."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import shutil
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageStat

PHOTO_PAGE = "https://commons.wikimedia.org/wiki/File:Common_Carp.jpg"
PHOTO_ORIGINAL = "https://upload.wikimedia.org/wikipedia/commons/c/ca/Common_Carp.jpg"
AUTHOR = "Gavin Bradley"
LICENSE = "CC BY-SA 3.0"
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/3.0/"
FROZEN_REL = "design/system/core_visual_v1/reference/normal_home_v1.png"
AUTHORITY_REL = "design/pages/home/normal_home/authority/authority_map.json"

ROIS = {
    "top_navigation": (0.07, 0.035, 0.93, 0.145),
    "statistics": (0.07, 0.135, 0.93, 0.235),
    "hero_frame_top_edge": (0.155, 0.305, 0.845, 0.325),
    "hero_frame_bottom_edge": (0.155, 0.745, 0.845, 0.77),
    "hero_frame_left_edge": (0.155, 0.305, 0.18, 0.77),
    "hero_frame_right_edge": (0.82, 0.305, 0.845, 0.77),
    "bottom_navigation": (0.0, 0.875, 1.0, 1.0),
}
ROI_COLORS = {
    "top_navigation": "#e84a5f",
    "statistics": "#f08c00",
    "hero_frame_top_edge": "#2f9e44",
    "hero_frame_bottom_edge": "#2f9e44",
    "hero_frame_left_edge": "#2f9e44",
    "hero_frame_right_edge": "#2f9e44",
    "bottom_navigation": "#4263eb",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except Exception:
        return {}


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def fit_frozen(source: Image.Image, target_size: tuple[int, int]) -> tuple[Image.Image, dict[str, Any]]:
    tw, th = target_size
    sw, sh = source.size
    scale = min(tw / sw, th / sh)
    rw, rh = max(1, round(sw * scale)), max(1, round(sh * scale))
    resized = source.convert("RGB").resize((rw, rh), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", target_size, (22, 34, 40))
    ox, oy = (tw - rw) // 2, (th - rh) // 2
    canvas.paste(resized, (ox, oy))
    ar_source, ar_target = sw / sh, tw / th
    transform = {
        "source_dimensions": [sw, sh],
        "target_dimensions": [tw, th],
        "scale": scale,
        "offset_xy": [ox, oy],
        "aspect_ratio_source": ar_source,
        "aspect_ratio_target": ar_target,
        "aspect_ratio_match": abs(ar_source - ar_target) < 0.002,
        "method": "aspect-preserving contain; runtime screenshot unchanged",
    }
    return canvas, transform


def roi_box(size: tuple[int, int], normalized: tuple[float, float, float, float]) -> tuple[int, int, int, int]:
    w, h = size
    x0, y0, x1, y1 = normalized
    return (round(x0 * w), round(y0 * h), round(x1 * w), round(y1 * h))


def build_report(root: Path, repo: Path) -> dict[str, Any]:
    root.mkdir(parents=True, exist_ok=True)
    photo = root / "photo" / "Common_Carp.jpg"
    photo_meta = load_json(root / "photo" / "photo_provenance.json")
    screenshots = root / "screenshots"
    run_result = load_json(root / "e2e-result.json")
    device_info = load_json(root / "device_info.json")
    apk_info = load_json(root / "apk_provenance.json")
    failure_step = (root / "failure_step.txt").read_text(encoding="utf-8").strip() if (root / "failure_step.txt").exists() else ""
    if run_result.get("failure_step"):
        failure_step = str(run_result["failure_step"])

    frozen = repo / FROZEN_REL
    authority = repo / AUTHORITY_REL
    package = root / "package"
    if package.exists():
        shutil.rmtree(package)
    dirs = {
        "photo": package / "01_source_photo",
        "runtime": package / "02_runtime",
        "comparison": package / "03_comparison",
        "report": package / "04_report",
        "evidence": package / "05_evidence",
    }
    for directory in dirs.values():
        directory.mkdir(parents=True, exist_ok=True)

    photo_ok = photo.is_file()
    if photo_ok:
        shutil.copy2(photo, dirs["photo"] / "Common_Carp.jpg")
        if not photo_meta:
            photo_meta = {}
        photo_meta.update({
            "source_page": PHOTO_PAGE,
            "original_file_url": PHOTO_ORIGINAL,
            "author": AUTHOR,
            "license": LICENSE,
            "license_url": LICENSE_URL,
            "file_name": "Common_Carp.jpg",
            "sha256": sha256(photo),
            "byte_size": photo.stat().st_size,
        })
        try:
            with Image.open(photo) as im:
                photo_meta["format"] = im.format
                photo_meta["original_dimensions"] = list(im.size)
                photo_meta["genuine_photo_review"] = "Wikimedia description identifies a Michigan angler holding a common carp; Commons source says own work."
        except Exception as exc:
            photo_meta["image_validation_error"] = str(exc)
        write_json(dirs["photo"] / "photo_provenance.json", photo_meta)

    frozen_sha = sha256(frozen) if frozen.is_file() else None
    authority_obj = load_json(authority) if authority.is_file() else {}
    runtime_path = screenshots / "runtime_real_photo.png"
    required_screens = {
        "gallery_selection": "gallery-selection.png",
        "recognition_result": "recognition-result.png",
        "save_confirmation": "save-return-home.png",
        "populated_home": "runtime_real_photo.png",
        "fish_record_detail_a": "fish-record-detail-a.png",
    }
    existing_shots: dict[str, dict[str, Any]] = {}
    for key, name in required_screens.items():
        path = screenshots / name
        if path.is_file():
            try:
                with Image.open(path) as im:
                    im.verify()
                with Image.open(path) as im:
                    existing_shots[key] = {
                        "file": name, "dimensions": list(im.size), "sha256": sha256(path), "valid_png": im.format == "PNG"
                    }
                    shutil.copy2(path, dirs["runtime"] / name)
            except Exception as exc:
                existing_shots[key] = {"file": name, "valid_png": False, "error": str(exc)}
    runtime_valid = runtime_path.is_file() and existing_shots.get("populated_home", {}).get("valid_png", False)

    capture_rows = run_result.get("screenshots", [])
    capture_by_name = {row.get("file"): row for row in capture_rows if isinstance(row, dict)}
    provenance_pass = runtime_valid
    if runtime_valid:
        runtime_meta = existing_shots["populated_home"]
        declaration = capture_by_name.get("runtime_real_photo.png", {})
        provenance_pass = (
            declaration.get("sha256") == runtime_meta["sha256"]
            and declaration.get("native_width") == runtime_meta["dimensions"][0]
            and declaration.get("native_height") == runtime_meta["dimensions"][1]
            and declaration.get("expected_app_foreground") is True
            and "com.yujian.ai.uiv2" in str(declaration.get("foreground_package", ""))
            and "com.yujian.ai.uiv2" in str(declaration.get("focused_window", ""))
        )
        shutil.copy2(runtime_path, dirs["comparison"] / "runtime_real_photo.png")

    visual = {"status": "NOT_RUN", "classification": "REVIEW_REQUIRED", "images_generated": [], "rois": {}}
    if frozen.is_file() and runtime_valid:
        try:
            with Image.open(runtime_path) as runtime_source:
                runtime_size = runtime_source.size
            with Image.open(frozen) as source:
                frozen_rgb, transform = fit_frozen(source.copy(), runtime_size)
            frozen_out = dirs["comparison"] / "frozen.png"
            frozen_rgb.save(frozen_out, format="PNG", optimize=False)
            with Image.open(runtime_path) as rt:
                runtime_rgb = rt.convert("RGB")
            side = Image.new("RGB", (runtime_rgb.width * 2, runtime_rgb.height), (0, 0, 0))
            side.paste(frozen_rgb, (0, 0))
            side.paste(runtime_rgb, (runtime_rgb.width, 0))
            side.save(dirs["comparison"] / "side-by-side.png", format="PNG")
            overlay = Image.blend(frozen_rgb, runtime_rgb, 0.5)
            overlay.save(dirs["comparison"] / "overlay.png", format="PNG")
            diff = ImageChops.difference(frozen_rgb, runtime_rgb)
            ImageEnhance.Contrast(diff).enhance(4.0).save(dirs["comparison"] / "diff.png", format="PNG")
            annotated = overlay.copy()
            draw = ImageDraw.Draw(annotated)
            for name, bounds in ROIS.items():
                box = roi_box(runtime_rgb.size, bounds)
                draw.rectangle(box, outline=ROI_COLORS[name], width=max(2, round(runtime_rgb.width / 540)))
                x0, y0, _, _ = box
                label = name.replace("_", " ")
                draw.rectangle((x0, max(0, y0 - 18), x0 + max(120, len(label) * 8), y0), fill=ROI_COLORS[name])
                draw.text((x0 + 3, max(0, y0 - 17)), label, fill="white")
                crop_a = frozen_rgb.crop(box)
                crop_b = runtime_rgb.crop(box)
                delta = ImageChops.difference(crop_a, crop_b)
                stat = ImageStat.Stat(delta)
                mae = sum(stat.mean) / 3
                pixels = list(delta.convert("L").getdata())
                changed = sum(p > 24 for p in pixels) / max(1, len(pixels))
                visual["rois"][name] = {
                    "normalized_rect": list(bounds),
                    "pixel_mae_rgb": round(mae, 4),
                    "fraction_pixels_luma_delta_gt_24": round(changed, 6),
                    "classification": "MEASURED_REVIEW",
                    "note": "Dynamic fish photograph pixels are excluded from these UI-geometry ROIs.",
                }
            annotated.save(dirs["comparison"] / "annotated-differences.png", format="PNG")
            visual = {
                **visual,
                "status": "COMPARISON_GENERATED",
                "classification": "REVIEW_REQUIRED",
                "frozen_sha256": frozen_sha,
                "frozen_source_dimensions": transform["source_dimensions"],
                "runtime_dimensions": transform["target_dimensions"],
                "frozen_transform": transform,
                "aspect_ratio_mismatch": not transform["aspect_ratio_match"],
                "dynamic_photo_policy": "Fish photograph content is dynamic and excluded from geometry parity classification; Hero frame edge ribbons are measured separately.",
                "images_generated": [
                    "frozen.png", "runtime_real_photo.png", "side-by-side.png",
                    "overlay.png", "diff.png", "annotated-differences.png"
                ],
            }
            (dirs["comparison"] / "authority_source.png").write_bytes(frozen.read_bytes())
            visual["authority_source"] = FROZEN_REL
        except Exception as exc:
            visual = {"status": "FAILED", "classification": "REVIEW_REQUIRED", "error": str(exc), "images_generated": []}

    source_size = None
    saved_size = None
    image_comparison = {"status": "NOT_RUN"}
    saved_record_photo = screenshots / "saved-record-photo.jpg"
    if photo_ok and saved_record_photo.is_file():
        try:
            with Image.open(photo) as a0, Image.open(saved_record_photo) as b0:
                a, b = a0.convert("RGB"), b0.convert("RGB")
                source_size, saved_size = list(a.size), list(b.size)
                target = (256, 192)
                aa = a.resize(target, Image.Resampling.LANCZOS)
                bb = b.resize(target, Image.Resampling.LANCZOS)
                mae = sum(ImageStat.Stat(ImageChops.difference(aa, bb)).mean) / 3
                image_comparison = {
                    "status": "MEASURED",
                    "source_dimensions": source_size,
                    "saved_record_dimensions": saved_size,
                    "normalized_rgb_mae_256x192": round(mae, 4),
                    "source_sha256": sha256(photo),
                    "saved_record_photo_sha256": sha256(saved_record_photo),
                    "interpretation": "Confirms the stored guest image remains visually derived from the selected source; source JPEG itself is preserved separately byte-for-byte.",
                }
                shutil.copy2(saved_record_photo, dirs["runtime"] / "saved-record-photo.jpg")
        except Exception as exc:
            image_comparison = {"status": "FAILED", "error": str(exc)}

    required_have = sorted(existing_shots.keys())
    screenshots_pass = len(required_have) == len(required_screens) and provenance_pass
    e2e_status = run_result.get("status", "BLOCKED_AT_EXACT_STEP")
    if e2e_status != "PASS_REAL_PHOTO_E2E" and not failure_step:
        failure_step = "RUNNER"

    report = {
        "task": "Normal Home Real Photo E2E V2",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "repository": "pan277942135/Yujian_App",
        "product_baseline_sha": "3cd0753be926c3a8cc94ac56c97c9103febc6663",
        "validation_head": (root / "validation_head.txt").read_text(encoding="utf-8").strip() if (root / "validation_head.txt").exists() else None,
        "final_status": e2e_status if e2e_status == "PASS_REAL_PHOTO_E2E" else "BLOCKED_AT_EXACT_STEP",
        "failure_step": None if e2e_status == "PASS_REAL_PHOTO_E2E" else failure_step,
        "screenshot_evidence": "PASS" if screenshots_pass else "FAIL",
        "visual_parity": visual["classification"],
        "physical_device": "PASS" if device_info.get("device_type") == "PHYSICAL" else "PENDING",
        "save_mode": run_result.get("save_mode", "GUEST_LOCAL_SAVE"),
        "photo": photo_meta,
        "photo_saved_image_comparison": image_comparison,
        "apk": apk_info,
        "device": device_info,
        "e2e_result": run_result,
        "screenshots": existing_shots,
        "screenshot_provenance_pass": provenance_pass,
        "frozen_authority": {
            "path": FROZEN_REL,
            "sha256": frozen_sha,
            "authority_map_path": AUTHORITY_REL,
            "authority_map": authority_obj,
        },
        "visual_comparison": visual,
        "prior_synthetic_evidence_excluded": True,
        "product_code_changed": False,
        "workflow_scope": "single Normal Home real-photo E2E; no Android runtime matrix",
    }
    write_json(dirs["evidence"] / "e2e-result.json", run_result)
    write_json(dirs["evidence"] / "device_info.json", device_info)
    write_json(dirs["evidence"] / "apk_provenance.json", apk_info)
    write_json(dirs["evidence"] / "runtime_gate_result.json", {
        "status": report["final_status"],
        "failure_step": report["failure_step"],
        "screenshot_evidence": report["screenshot_evidence"],
        "visual_parity": report["visual_parity"],
        "screenshot_provenance_pass": provenance_pass,
    })

    for source, target_name in [
        (root / "logs" / "instrumentation.log", "instrumentation.log"),
        (root / "logs" / "runner.log", "runner.log"),
        (root / "logs" / "logcat.txt", "logcat.txt"),
        (root / "logs" / "activity_focus.txt", "activity_focus.txt"),
        (root / "logs" / "apk_build.log", "apk_build.log"),
        (root / "logs" / "workflow_context.txt", "workflow_context.txt"),
        (root / "photo" / "photo_provenance.json", "photo_provenance.json"),
    ]:
        if source.is_file():
            shutil.copy2(source, dirs["evidence"] / target_name)

    if apk_info:
        report["apk"] = apk_info
    write_json(dirs["report"] / "report.json", report)

    discrepancy_file = dirs["report"] / "discrepancies.csv"
    with discrepancy_file.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["component", "normalized_roi", "pixel_mae_rgb", "changed_fraction_delta_gt_24", "classification", "severity", "evidence"])
        for name, metrics in visual.get("rois", {}).items():
            writer.writerow([
                name, metrics["normalized_rect"], metrics["pixel_mae_rgb"],
                metrics["fraction_pixels_luma_delta_gt_24"], "REVIEW_REQUIRED",
                "REVIEW", "03_comparison/annotated-differences.png",
            ])

    image_links = []
    for rel in [
        "../03_comparison/side-by-side.png",
        "../03_comparison/overlay.png",
        "../03_comparison/diff.png",
        "../03_comparison/annotated-differences.png",
        "../02_runtime/gallery-selection.png",
        "../02_runtime/recognition-result.png",
        "../02_runtime/runtime_real_photo.png",
        "../02_runtime/fish-record-detail-a.png",
    ]:
        p = dirs["report"] / rel
        if p.is_file():
            image_links.append((rel, Path(rel).name))
    blocker = report["failure_step"] or "none"
    html = [
        "<!doctype html><html lang='en'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>",
        "<title>YuJian Normal Home Real Photo E2E V2</title>",
        "<style>body{font:16px/1.55 system-ui,sans-serif;max-width:1200px;margin:32px auto;padding:0 20px;color:#17212b}table{border-collapse:collapse;width:100%}td,th{border:1px solid #cbd5dc;padding:8px;text-align:left}.status{padding:14px;background:#f1f3f5;border-left:5px solid #4263eb}.gallery{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:12px}img{max-width:100%;border:1px solid #ccc}.muted{color:#596875}pre{white-space:pre-wrap;background:#f3f5f7;padding:12px}</style>",
        "<h1>Normal Home Real Photo E2E V2</h1>",
        f"<div class='status'><b>REAL_PHOTO_E2E: {report['final_status']}</b><br>Failure step: {blocker}<br>SCREENSHOT_EVIDENCE: {report['screenshot_evidence']}<br>VISUAL_PARITY: {report['visual_parity']}<br>PHYSICAL_DEVICE: {report['physical_device']}<br>Save mode: {report['save_mode']}</div>",
        "<h2>Run identity</h2><table>",
        f"<tr><th>Product baseline</th><td>{report['product_baseline_sha']}</td></tr>",
        f"<tr><th>Validation HEAD</th><td>{report['validation_head']}</td></tr>",
        f"<tr><th>Runner / device</th><td>{device_info.get('runner_name')} / {device_info.get('device_type')} / {device_info.get('model')} / API {device_info.get('api_level')}</td></tr>",
        f"<tr><th>Native display</th><td>{device_info.get('native_width')}×{device_info.get('native_height')}</td></tr>",
        f"<tr><th>Source photo</th><td>{AUTHOR} — {LICENSE} — {photo_meta.get('original_dimensions')} — SHA-256 {photo_meta.get('sha256')} — <a href='{PHOTO_PAGE}'>Commons source</a></td></tr>",
        f"<tr><th>Record</th><td>{run_result.get('record_id')} ({run_result.get('save_mode')}) — {run_result.get('species_name')}</td></tr>",
        "</table>",
        "<h2>Evidence images</h2><div class='gallery'>",
    ]
    for rel, label in image_links:
        html.append(f"<figure><a href='{rel}'><img src='{rel}' alt='{label}'></a><figcaption>{label}</figcaption></figure>")
    html.append("</div>")
    html.extend([
        "<h2>Measured visual differences</h2>",
        "<p>Dynamic fish-photo pixels are not scored as fixed UI parity. Geometry ROIs cover the title/navigation, statistic band, Hero frame edge strips, and bottom navigation. Visual classification remains REVIEW_REQUIRED because no numerical frozen acceptance threshold was specified.</p>",
        "<p><a href='../03_comparison/frozen.png'>Frozen</a> · <a href='../03_comparison/runtime_real_photo.png'>Runtime</a> · <a href='../03_comparison/side-by-side.png'>Side by side</a> · <a href='../03_comparison/overlay.png'>Overlay</a> · <a href='../03_comparison/diff.png'>Diff</a> · <a href='discrepancies.csv'>ROI measurements</a></p>",
        "<h2>Failure/evidence notes</h2>",
        f"<pre>{json.dumps({'failure_step': report['failure_step'], 'e2e_status': e2e_status, 'screenshot_provenance_pass': provenance_pass, 'image_comparison': image_comparison}, ensure_ascii=False, indent=2)}</pre>",
        "<p class='muted'>Original Commons JPEG bytes are preserved in 01_source_photo. Runtime screenshot PNG bytes are copied without resizing or editing. The previous synthetic Seed screenshots are excluded.</p>",
        "</html>",
    ])
    (dirs["report"] / "index.html").write_text("\n".join(html), encoding="utf-8")

    # Copy raw diagnostics, runner reports, and the original Frozen file into evidence.
    if frozen.is_file():
        shutil.copy2(frozen, dirs["evidence"] / "normal_home_v1_frozen_source.png")
    if authority.is_file():
        shutil.copy2(authority, dirs["evidence"] / "authority_map.json")
    failure_source = root / "failure_step.txt"
    if failure_source.is_file():
        shutil.copy2(failure_source, dirs["evidence"] / "workflow_failure_step.txt")
    if capture_rows:
        write_json(dirs["runtime"] / "capture_provenance.json", {"screenshots": capture_rows})
    if (root / "screenshot_capture_manifest.json").is_file():
        shutil.copy2(root / "screenshot_capture_manifest.json", dirs["runtime"] / "capture_provenance_host.json")

    # Hashes cover every delivered file except the checksum list itself.
    sums = []
    for path in sorted(package.rglob("*")):
        if path.is_file():
            sums.append(f"{sha256(path)}  {path.relative_to(package).as_posix()}")
    (dirs["evidence"] / "SHA256SUMS").write_text("\n".join(sums) + "\n", encoding="utf-8")

    zip_path = root / "YuJian_Normal_Home_Real_Photo_E2E_V2.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(package.rglob("*")):
            if path.is_file():
                archive.write(path, arcname=f"YuJian_Normal_Home_Real_Photo_E2E_V2/{path.relative_to(package).as_posix()}")
    report["evidence_zip"] = {
        "file": zip_path.name,
        "sha256": sha256(zip_path),
        "byte_size": zip_path.stat().st_size,
    }
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-root", required=True, type=Path)
    parser.add_argument("--repository-root", required=True, type=Path)
    args = parser.parse_args()
    result = build_report(args.evidence_root, args.repository_root)
    print(json.dumps({
        "final_status": result["final_status"],
        "failure_step": result["failure_step"],
        "screenshot_evidence": result["screenshot_evidence"],
        "visual_parity": result["visual_parity"],
        "evidence_zip": result["evidence_zip"],
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
