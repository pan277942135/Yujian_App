#!/usr/bin/env python3
"""Verify and compose a contact sheet from the API 28 production runtime matrix."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


EXPECTED_STAGE_IDS = [
    "PHOTO_STABLE", "ORB_WAKE", "ORB_RELEASE", "PERIMETER_ARRIVAL", "PATH_TO_FISH",
    "CONTOUR_A", "CONTOUR_B", "CONTOUR_C", "FISH_GOLD_PEAK", "ORB_RETURN",
    "ORB_TO_NAME", "SPECIES_NAME_REVEAL", "RESIDUE_SETTLE",
]
EXPECTED_STAGE_TIMES = [0, 1, 350, 950, 1075, 1200, 1550, 1700, 1950, 2800, 3180, 3350, 3480]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    )
    for candidate in candidates:
        if Path(candidate).is_file():
            return ImageFont.truetype(candidate, size=size)
    return ImageFont.load_default()


def build(evidence_dir: Path) -> tuple[Path, Path]:
    matrix_path = evidence_dir / "AI_Orb_WaterLight_Runtime_Matrix.json"
    matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
    if matrix.get("status") != "PASS":
        raise ValueError("runtime matrix is not PASS")
    identity = (matrix.get("session_id"), matrix.get("attempt_id"), matrix.get("image_id"), matrix.get("photo_sha256"))
    if any(not value for value in identity) or len(identity[3]) != 64:
        raise ValueError("runtime matrix lacks a complete PhotoSession identity")
    if matrix.get("mask_status") != "READY" or matrix.get("mask_quality") != "GOOD":
        raise ValueError("trusted production Mask is required for the 13-stage matrix")
    if not matrix.get("result_is_high") or int(matrix.get("contour_segments", 0)) <= 0:
        raise ValueError("high-confidence real classifier output and trusted contour are required")

    stages = matrix.get("stages")
    if not isinstance(stages, list) or len(stages) != 13:
        raise ValueError(f"expected 13 runtime frames, got {len(stages) if isinstance(stages, list) else 'invalid'}")
    verified_frames = []
    opened = []
    for number, (stage, expected_id, expected_ms) in enumerate(zip(stages, EXPECTED_STAGE_IDS, EXPECTED_STAGE_TIMES), start=1):
        if stage.get("stage_number") != number or stage.get("stage_id") != expected_id:
            raise ValueError(f"stage {number} identity mismatch: {stage}")
        if stage.get("scene_time_ms") != expected_ms:
            raise ValueError(f"stage {number} timestamp mismatch: {stage.get('scene_time_ms')} != {expected_ms}")
        relative = Path(stage["file"])
        source = (evidence_dir / ("13-stage" if number < 10 else "") / relative).resolve()
        if not source.is_file() or source.stat().st_size <= 0:
            raise ValueError(f"missing runtime screenshot: {source}")
        image = Image.open(source).convert("RGB")
        if image.width < 320 or image.height < 480:
            raise ValueError(f"runtime screenshot is implausibly small: {source} {image.size}")
        opened.append((stage, image))
        verified_frames.append({**stage, "image_sha256": sha256(source), "image_size_px": [image.width, image.height]})

    keyframes = matrix.get("p0_p7")
    if not isinstance(keyframes, list) or [frame.get("keyframe") for frame in keyframes] != [f"P{i}" for i in range(8)]:
        raise ValueError("P0-P7 keyframe coverage is incomplete")
    expected_p_times = [0, 350, 950, 1200, 1550, 2050, 2600, 2800]
    for frame, expected_time in zip(keyframes, expected_p_times):
        if frame.get("time_ms") != expected_time:
            raise ValueError(f"{frame.get('keyframe')} timestamp mismatch: {frame.get('time_ms')} != {expected_time}")
        source = evidence_dir / "13-stage" / frame["file"]
        if not source.is_file() or source.stat().st_size <= 0:
            raise ValueError(f"missing P0-P7 runtime screenshot: {source}")
        with Image.open(source) as image:
            frame["image_size_px"] = list(image.size)
        frame["image_sha256"] = sha256(source)
    rr09 = matrix.get("rr09_r0_r5")
    expected_rr09 = [("R0", 0), ("R0.5", 100), ("R1", 200), ("R2", 380), ("R3", 550), ("R4", 680), ("R5", 850)]
    if not isinstance(rr09, list) or [(frame.get("keyframe"), frame.get("rr09_elapsed_ms")) for frame in rr09] != expected_rr09:
        raise ValueError("RR09 V4 R0-R5 keyframe coverage is incomplete")
    for frame, (keyframe, elapsed_ms) in zip(rr09, expected_rr09):
        source = evidence_dir / "rr09-v4" / frame["file"]
        if not source.is_file() or source.stat().st_size <= 0:
            raise ValueError(f"missing RR09 V4 runtime screenshot: {source}")
        with Image.open(source) as image:
            frame["image_size_px"] = list(image.size)
        frame["image_sha256"] = sha256(source)
    matrix["stages"] = verified_frames
    matrix["source_artifact"] = "API28 production Recognition Runtime Harness"
    matrix["material_calibration"] = "B01 candidate values; not visually frozen"
    verified_matrix = evidence_dir / "AI_Orb_WaterLight_Runtime_Matrix.verified.json"
    verified_matrix.write_text(json.dumps(matrix, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    cols, cell_w, image_h, label_h = 4, 270, 480, 60
    rows = 4
    footer_h = 190
    sheet = Image.new("RGB", (cols * cell_w, rows * (image_h + label_h) + footer_h), (11, 22, 29))
    draw = ImageDraw.Draw(sheet)
    label_font = font(15)
    small_font = font(13)
    for index, (stage, image) in enumerate(opened):
        col, row = index % cols, index // cols
        x, y = col * cell_w, row * (image_h + label_h)
        thumb = ImageOps.fit(image, (cell_w, image_h), method=Image.Resampling.LANCZOS)
        sheet.paste(thumb, (x, y))
        draw.rectangle((x, y + image_h, x + cell_w, y + image_h + label_h), fill=(20, 38, 47))
        label = f"{stage['stage_number']:02d}  {stage['stage_id']}"
        draw.text((x + 8, y + image_h + 6), label, font=label_font, fill=(229, 239, 238))
        detail = f"scene {stage['scene_time_ms']}ms  geom {stage['geometry_version']}"
        draw.text((x + 8, y + image_h + 31), detail, font=small_font, fill=(151, 181, 186))

    footer_y = rows * (image_h + label_h) + 18
    draw.text((18, footer_y), "AI Orb x WaterLight | API 28 production runtime frames", font=font(20), fill=(229, 239, 238))
    draw.text((18, footer_y + 40), f"session={identity[0]}  attempt={identity[1]}", font=small_font, fill=(186, 210, 211))
    draw.text((18, footer_y + 66), f"image={identity[2]}", font=small_font, fill=(186, 210, 211))
    draw.text((18, footer_y + 92), f"photo_sha256={identity[3]}", font=small_font, fill=(186, 210, 211))
    draw.text((18, footer_y + 118), f"mask={matrix['mask_status']}/{matrix['mask_quality']} contour_segments={matrix['contour_segments']}", font=small_font, fill=(186, 210, 211))
    draw.text((18, footer_y + 144), "materials: B01 candidates; runtime evidence does not freeze visual calibration", font=small_font, fill=(213, 184, 129))

    contact = evidence_dir / "AI_Orb_WaterLight_13_Stage_Runtime_Contact_Sheet.png"
    sheet.save(contact, format="PNG", optimize=True)
    report = evidence_dir / "AI_Orb_WaterLight_13_Stage_Runtime_Contact_Sheet.sha256"
    report.write_text(f"{sha256(contact)}  {contact.name}\n", encoding="utf-8")
    return contact, verified_matrix


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", required=True, type=Path)
    args = parser.parse_args()
    contact, verified_matrix = build(args.evidence_dir)
    print(f"AI_ORB_WATERLIGHT_13_STAGE_CONTACT_SHEET=PASS")
    print(f"contact_sheet={contact}")
    print(f"verified_matrix={verified_matrix}")
    print(f"contact_sheet_sha256={sha256(contact)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
