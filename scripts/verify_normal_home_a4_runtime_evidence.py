#!/usr/bin/env python3
"""Build an auditable A.4 report from native API 28 Normal Home evidence.

Evidence completeness and acceptance are intentionally separate: this validator
returns success when the evidence package is internally valid, while preserving
REVIEW_REQUIRED/BLOCKED acceptance results in the generated reports.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

from PIL import Image, ImageChops
from verify_normal_home_hero_fixtures import audit as audit_hero_fixtures

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ROOT / "design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Pixel_Targets_V1.json"
FIXTURES = ROOT / "design/pages/home/normal_home/engineering/hero_real_photo_fixture_register_v1.json"
FROZEN = ROOT / "design/system/core_visual_v1/reference/normal_home_v1.png"
BASE_PROFILE = "1080x1920_fs1_0"
PROFILE_IDS = [
    BASE_PROFILE,
    "1080x1920_fs1_3",
    "1080x1920_fs1_5",
    "1080x2340_fs1_0",
]
TYPOGRAPHY_PROFILE_IDS = PROFILE_IDS[:3]


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def require_file(path: Path) -> None:
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"missing runtime evidence file: {path.name}")


def read_log(path: Path) -> str:
    require_file(path)
    return path.read_text(encoding="utf-8", errors="replace")


def parse_rects(log: str, marker: str) -> dict[str, list[float]]:
    matches = re.findall(rf"{re.escape(marker)}[^\n]*", log)
    if not matches:
        return {}
    line = matches[-1]
    result: dict[str, list[float]] = {}
    for key, raw in re.findall(r"([a-z_]+)=(-?[0-9.]+,-?[0-9.]+,-?[0-9.]+,-?[0-9.]+)", line):
        result[key] = [float(part) for part in raw.split(",")]
    return result


def parse_text_metrics(log: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for line in log.splitlines():
        if "NORMAL_HOME_TEXT_LAYOUT" not in line or "tag=" not in line:
            continue
        entry: dict[str, Any] = {}
        for key, value in re.findall(r"([A-Za-z][A-Za-z0-9_]*)=([^ ]+)", line):
            entry[key] = value.rstrip(",")
        if "tag" in entry:
            visible = re.fullmatch(r"(\d+)/(\d+)", entry.get("visibleTextEnd", ""))
            if visible:
                entry["visible_characters"] = int(visible.group(1))
                entry["text_characters"] = int(visible.group(2))
            for key in ("lineCount", "fontScale", "effectiveFontPx", "density", "didOverflowWidth", "didOverflowHeight", "visualOverflow"):
                if key in entry:
                    raw = entry[key]
                    if raw.lower() in ("true", "false"):
                        entry[key] = raw.lower() == "true"
                    elif key == "lineCount":
                        entry[key] = int(raw)
                    else:
                        try:
                            entry[key] = float(raw)
                        except ValueError:
                            pass
            result.append(entry)
    return result


def parse_motion_trace(log: str, tag: str, event: str | None = None) -> dict[str, str]:
    for line in log.splitlines():
        if tag not in line:
            continue
        if event is not None and f"event={event}" not in line:
            continue
        return dict(re.findall(r"([A-Za-z][A-Za-z0-9_]*)=([^ ]+)", line))
    return {}


def trace_number(trace: dict[str, str], key: str) -> float | None:
    try:
        return float(trace[key])
    except (KeyError, ValueError):
        return None


def bbox_mask(image: Image.Image, roi: tuple[int, int, int, int], predicate) -> list[int] | None:
    x0, y0, x1, y1 = roi
    pixels = image.convert("RGB")
    xs: list[int] = []
    ys: list[int] = []
    for y in range(y0, min(y1, pixels.height)):
        for x in range(x0, min(x1, pixels.width)):
            if predicate(pixels.getpixel((x, y))):
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return [min(xs), min(ys), max(xs), max(ys)]


def raster_bounds(image: Image.Image, targets: dict[str, Any]) -> dict[str, Any]:
    masks = targets["measured_source_targets"]["normal_home_source_masks"]
    rules = {
        "CTA_NEAR_WHITE": (lambda p: min(p) >= 210 and max(p) - min(p) <= 46, (390, 1510, 700, 1570)),
        "CAMERA_CORE_NEAR_WHITE": (lambda p: min(p) >= 210 and max(p) - min(p) <= 46, (400, 1580, 680, 1785)),
        "CAMERA_GOLD": (lambda p: p[0] - p[1] >= 18 and p[1] - p[2] >= 5 and p[0] >= 125 and p[1] >= 105, (400, 1580, 680, 1800)),
    }
    output: dict[str, Any] = {}
    tolerances = targets["tolerances"]
    for key, (predicate, roi) in rules.items():
        expected = masks[key]["bbox"]
        actual = bbox_mask(image, roi, predicate)
        tolerance = 3.24 if key == "CTA_NEAR_WHITE" else 2.16
        edge_delta = [None if actual is None else round(actual[i] - expected[i], 2) for i in range(4)]
        passed = actual is not None and max(abs(value) for value in edge_delta if value is not None) <= tolerance
        output[key] = {
            "expected_bbox_inclusive_px": expected,
            "runtime_bbox_inclusive_px": actual,
            "edge_delta_px": edge_delta,
            "tolerance_px": tolerance,
            "status": "PASS" if passed else "FAIL",
            "authority": "frozen Normal_Home_Pixel_Targets_V1 source mask",
            "roi_px": list(roi),
        }
    return output


def motion_frames(video: Path, times: list[float]) -> dict[float, Image.Image]:
    frames: dict[float, Image.Image] = {}
    with tempfile.TemporaryDirectory(prefix="normal-home-motion-") as temp:
        for index, second in enumerate(times):
            output = Path(temp) / f"frame-{index}.png"
            subprocess.run(
                ["ffmpeg", "-v", "error", "-ss", str(second), "-i", str(video), "-frames:v", "1", "-y", str(output)],
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            with Image.open(output) as frame:
                frames[second] = frame.convert("RGB").copy()
    return frames


def changed_pixels(left: Image.Image, right: Image.Image, roi: tuple[int, int, int, int]) -> int:
    diff = ImageChops.difference(left.crop(roi), right.crop(roi))
    return sum(1 for px in diff.getdata() if px != (0, 0, 0))


def main(out: Path) -> int:
    targets = load(TARGETS)
    fixtures = load(FIXTURES)
    fixture_preflight = audit_hero_fixtures(fixtures)
    write_json(out / "Hero_Fixture_Preflight_Report.json", fixture_preflight)
    frozen_sha = sha(FROZEN)
    profiles: dict[str, Any] = {}
    profile_logs: dict[str, str] = {}
    profile_rects: dict[str, dict[str, list[float]]] = {}
    profile_text: dict[str, list[dict[str, Any]]] = {}
    blocked_profiles: dict[str, dict[str, Any]] = {}

    for profile_id in PROFILE_IDS:
        profile_path = out / f"normal_home_profile_{profile_id}.json"
        if not profile_path.is_file():
            if profile_id == "1080x2340_fs1_0":
                blocked_profiles[profile_id] = {
                    "status": "BLOCKED_INFRA",
                    "reason": "No native 1080x2340 API28 profile record was produced.",
                }
                continue
            raise ValueError(f"missing runtime profile record: {profile_id}")
        payload = load(profile_path)
        if payload.get("status") == "BLOCKED_INFRA":
            shot_name = (payload.get("screenshot") or {}).get("path")
            if shot_name:
                shot = out / shot_name
                if shot.is_file() and sha(shot) != payload.get("screenshot", {}).get("sha256"):
                    raise ValueError(f"blocked runtime screenshot SHA mismatch: {profile_id}")
            blocked_profiles[profile_id] = payload
            continue
        require_file(out / payload["screenshot"]["path"])
        shot = out / payload["screenshot"]["path"]
        actual_sha = sha(shot)
        with Image.open(shot) as image:
            actual_size = list(image.size)
        if actual_sha != payload["screenshot"]["sha256"]:
            raise ValueError(f"runtime screenshot SHA mismatch: {profile_id}")
        if actual_size != payload["screenshot"]["dimensions"]:
            raise ValueError(f"runtime screenshot dimensions mismatch: {profile_id}")
        expected_size = [1080, 2340] if "2340" in profile_id else [1080, 1920]
        if actual_size != expected_size or payload["api_level"] != "28" or payload["density_dpi"] != 480:
            raise ValueError(f"runtime device profile is outside the required API28/480dpi setup: {profile_id}")
        log = read_log(out / payload["window_metrics_log"])
        if "NORMAL_HOME_TEXT_LAYOUT" not in log or "NORMAL_HOME_HIERARCHY_EVIDENCE" not in log or "NORMAL_HOME_WINDOW_METRICS" not in log:
            raise ValueError(f"missing on-device layout measurement log: {profile_id}")
        hierarchy = parse_rects(log, "NORMAL_HOME_HIERARCHY_EVIDENCE")
        text_metrics = parse_text_metrics(log)
        if not hierarchy or len(text_metrics) < 10:
            raise ValueError(f"incomplete on-device hierarchy/text measurements: {profile_id}")
        profiles[profile_id] = payload
        profile_logs[profile_id] = log
        profile_rects[profile_id] = hierarchy
        profile_text[profile_id] = text_metrics

    base = profiles[BASE_PROFILE]
    runtime_screenshot = out / base["screenshot"]["path"]
    with Image.open(runtime_screenshot) as runtime_image:
        runtime_image = runtime_image.convert("RGB")
        raster = raster_bounds(runtime_image, targets)

    base_rects = profile_rects[BASE_PROFILE]
    required_rects = ("root", "header", "brand_title", "guest_avatar", "stats", "recent_header", "hero", "hero_media", "hero_footer", "cta_layout", "cta_text", "camera_touch")
    missing_rects = [key for key in required_rects if key not in base_rects]
    if missing_rects:
        raise ValueError("missing required hierarchy nodes: " + ", ".join(missing_rects))

    # Pixel-target geometry uses the unmodified 1080x1920 reference canvas.
    geometry_tol = max(2.0, 0.002 * 1080.0)
    root = base_rects["root"]
    anchors = targets["measured_source_targets"]["normal_home_reference_anchors"]
    header_target = anchors["header"]
    hero_target = anchors["hero"]
    expected_header = [header_target[0], header_target[1], header_target[0] + header_target[2], header_target[1] + header_target[3]]
    expected_hero = [hero_target[0], hero_target[1], hero_target[0] + hero_target[2], hero_target[1] + hero_target[3]]

    # Frozen anchors use the page content coordinate system. boundsInWindow is
    # retained as raw device evidence; normalize it by the measured content
    # root origin before comparing it with page-local targets.
    content_origin = [root[0], root[1]]

    def page_rect(raw: list[float]) -> list[float]:
        return [
            round(raw[0] - content_origin[0], 2),
            round(raw[1] - content_origin[1], 2),
            round(raw[2] - content_origin[0], 2),
            round(raw[3] - content_origin[1], 2),
        ]

    local_rects = {key: page_rect(value) for key, value in base_rects.items()}

    def rect_status(key: str, expected: list[float]) -> dict[str, Any]:
        actual_window = base_rects[key]
        actual = local_rects[key]
        delta = [round(actual[i] - expected[i], 2) for i in range(4)]
        return {"expected_page_local_px": expected,
                "runtime_boundsInWindow_px": actual_window,
                "runtime_page_local_px": actual,
                "content_root_origin_boundsInWindow_px": content_origin,
                "edge_delta_px": delta,
                "tolerance_px": geometry_tol,
                "status": "PASS" if max(abs(value) for value in delta) <= geometry_tol else "FAIL"}

    header = rect_status("header", expected_header)
    hero = rect_status("hero", expected_hero)
    media = base_rects["hero_media"]
    footer = base_rects["hero_footer"]
    camera = base_rects["camera_touch"]
    cta = base_rects["cta_layout"]
    title = base_rects["brand_title"]
    avatar = base_rects["guest_avatar"]
    hierarchy_checks = {
        "header_matches_frozen_anchor": header,
        "hero_matches_frozen_anchor": hero,
        "brand_title_inside_header": {"status": "PASS" if title[0] >= base_rects["header"][0] and title[2] <= base_rects["header"][2] else "FAIL", "runtime_px": title},
        "avatar_inside_header": {"status": "PASS" if avatar[0] >= base_rects["header"][0] and avatar[2] <= base_rects["header"][2] else "FAIL", "runtime_px": avatar},
        "brand_title_avatar_non_overlap": {"status": "PASS" if title[2] <= avatar[0] or avatar[2] <= title[0] else "FAIL"},
        "hero_media_same_bounds_as_card": {"status": "PASS" if all(abs(media[i] - base_rects["hero"][i]) <= 1.0 for i in range(4)) else "FAIL", "hero_px": base_rects["hero"], "media_px": media},
        "hero_footer_inside_card": {
            "status": "PASS" if (
                local_rects["hero_footer"][0] >= local_rects["hero"][0]
                and local_rects["hero_footer"][1] >= local_rects["hero"][1]
                and local_rects["hero_footer"][2] <= local_rects["hero"][2]
                and local_rects["hero_footer"][3] <= local_rects["hero"][3]
            ) else "FAIL",
            "footer_page_local_px": local_rects["hero_footer"],
            "hero_page_local_px": local_rects["hero"],
        },
        "cta_layout_separate_from_camera_touch": {"status": "PASS" if cta[3] <= camera[1] or camera[3] <= cta[1] else "FAIL", "cta_layout_px": cta, "camera_touch_px": camera},
        "camera_touch_minimum_48dp": {"status": "PASS" if camera[2] - camera[0] >= 144 and camera[3] - camera[1] >= 144 else "FAIL", "runtime_px": camera, "minimum_px_at_density_480": [144, 144]},
        "camera_touch_inside_window": {
            "status": "PASS" if camera[0] >= root[0] and camera[1] >= root[1] and camera[2] <= root[2] and camera[3] <= root[3] else "FAIL",
            "runtime_boundsInWindow_px": camera,
            "root_boundsInWindow_px": root,
        },
    }
    hierarchy_pass = all(value["status"] == "PASS" for value in hierarchy_checks.values())
    hierarchy_report = {
        "status": "PASS" if hierarchy_pass else "FAIL",
        "profile": BASE_PROFILE,
        "screenshot_sha256": base["screenshot"]["sha256"],
        "device": base["device"],
        "api_level": base["api_level"],
        "resolution_px": base["screenshot"]["dimensions"],
        "density_dpi": base["density_dpi"],
        "font_scale": base["font_scale"],
        "checks": hierarchy_checks,
        "layer_order_source_review": {
            "header": ["Background", "Brand Artwork", "Avatar", "Content"],
            "hero": ["Source Photo / EvidenceFit", "Gradient Treatment", "Footer Metadata", "Outline Decoration"],
            "authority": ["NormalHomeContent.kt", "RecentFishCard.kt", "YuJianHeroCard.kt", "RemoteImage.kt"],
            "status": "CODE_ORDER_VERIFIED; runtime pixels are separately represented by bounds and screenshot evidence",
        },
    }

    # Map the actual saved record into the contract's fit mode. The repository's
    # only 3:4 JPEG is explicitly a candidate, not approved source evidence.
    fixture_f03 = next(item for item in fixtures["fixtures"] if item["id"] == "F03")
    fixture_path = ROOT / fixture_f03["source_path"]
    with Image.open(fixture_path) as source_image:
        source_dimensions = list(source_image.size)
    source_ratio = source_dimensions[0] / source_dimensions[1]
    hero_report = {
        "status": "BLOCKED",
        "fixture_preflight_status": fixture_preflight["status"],
        "fixture_preflight_report": "Hero_Fixture_Preflight_Report.json",
        "runtime_fixture": {
            "fixture_id": "F03",
            "source_path": fixture_f03["source_path"],
            "source_sha256": sha(fixture_path),
            "source_dimensions_px": source_dimensions,
            "source_ratio": round(source_ratio, 5),
            "source_provenance": fixture_f03["photo_provenance"],
            "usage_rights": fixture_f03["source_usage_rights"],
            "fish_bbox_ground_truth": fixture_f03["bbox_ground_truth"],
            "render_mode": "EVIDENCE_FIT",
            "contract_binding": "RemoteImage(preserveEvidenceWithFitBackdrop=true; fitted unchanged source over same-source blur)",
            "fish_safe_area": "UNVERIFIED",
            "crop": "No front-layer crop; source file SHA is preserved by seed path. Subject-safe region cannot be scored without approved bbox.",
            "layout_test": "PASS for shared viewport and rounded card clipping; synthetic 9:16/4:5 sources are layout-only fixtures.",
        },
        "required_real_photo_cases": [
            {"fixture_id": item["id"], "case": item["test_case"], "status": item["status"], "reason": item["notes"]}
            for item in fixtures["fixtures"]
        ],
        "blocking_reason": "No approved originals/rights/ground-truth fish boxes for required landscape, vertical, edge, letterbox and low-confidence cases.",
    }

    expected_text_tags = [
        "normal-home-stat-species-value", "normal-home-stat-species-label",
        "normal-home-stat-catches-value", "normal-home-stat-catches-label",
        "normal-home-stat-record-days-value", "normal-home-stat-record-days-label",
        "normal-home-recent-title", "normal-home-recent-all-label",
        "normal-home-catch-species-catch-snakehead", "normal-home-catch-measurement-catch-snakehead",
        "normal-home-catch-meta-catch-snakehead", "normal-home-capture-cta-text",
    ]
    text_profiles: list[dict[str, Any]] = []
    typography_pass = True
    for profile_id in TYPOGRAPHY_PROFILE_IDS:
        metrics = profile_text[profile_id]
        by_tag = {item["tag"]: item for item in metrics}
        missing = [tag for tag in expected_text_tags if tag not in by_tag]
        checked: list[dict[str, Any]] = []
        for tag in expected_text_tags:
            item = by_tag.get(tag)
            if item is None:
                continue
            is_metadata = "-meta-" in tag
            complete = item.get("visible_characters") == item.get("text_characters")
            if is_metadata:
                # The contract permits long metadata to ellipsize after the timestamp/location prefix.
                complete = item.get("visible_characters", 0) > 0
            one_line = item.get("lineCount") == 1
            checked.append({
                "tag": tag,
                "font_scale": item.get("fontScale"),
                "effective_font_px": item.get("effectiveFontPx"),
                "line_count": item.get("lineCount"),
                "visible_text": f"{item.get('visible_characters')}/{item.get('text_characters')}",
                "overflow_width_reported": item.get("didOverflowWidth"),
                "overflow_height_reported": item.get("didOverflowHeight"),
                "status": "PASS" if one_line and complete else "FAIL",
                "note": "Metadata ellipsis is permitted by its contract." if is_metadata else None,
            })
        pass_profile = not missing and all(item["status"] == "PASS" for item in checked)
        typography_pass = typography_pass and pass_profile
        text_profiles.append({
            "profile_id": profile_id,
            "font_scale": profiles[profile_id]["font_scale"],
            "screenshot_sha256": profiles[profile_id]["screenshot"]["sha256"],
            "missing_text_tags": missing,
            "text": checked,
            "status": "PASS" if pass_profile else "REVIEW_REQUIRED",
        })
    typography_report = {
        "status": "PASS" if typography_pass else "REVIEW_REQUIRED",
        "brand_title": "PASS; frozen artwork asset excluded from dynamic text measurement",
        "dynamic_text_profiles": text_profiles,
        "cjk_fallback": "Android Platform SansSerif/CJK system fallback; glyph visibility recorded from live TextLayoutResult and screenshots.",
        "caveat": "TextLayoutResult overflow flags are logged as supplementary signals; visible character count and line geometry decide clipping/ellipsis.",
    }

    responsive_profiles = []
    for profile_id in PROFILE_IDS:
        if profile_id in blocked_profiles:
            p = blocked_profiles[profile_id]
            responsive_profiles.append({
                "profile_id": profile_id,
                "resolution_px": [1080, 2340],
                "font_scale": 1.0,
                "status": "BLOCKED_INFRA",
                "reason": p.get("reason", "Native full-height runtime evidence unavailable."),
                "observed_screenshot": p.get("screenshot"),
            })
            continue
        p = profiles[profile_id]
        rects = profile_rects[profile_id]
        screenshot = out / p["screenshot"]["path"]
        expected_size = [1080, 2340] if "2340" in profile_id else [1080, 1920]
        has_expected = all(key in rects for key in ("header", "guest_avatar", "hero", "stats", "cta_layout", "camera_touch"))
        no_overlap = rects.get("cta_layout", [0, 0, 0, 0])[3] <= rects.get("camera_touch", [0, 0, 0, 0])[1]
        responsive_profiles.append({
            "profile_id": profile_id,
            "resolution_px": expected_size,
            "font_scale": p["font_scale"],
            "screenshot_sha256": p["screenshot"]["sha256"],
            "native_full_frame": p["screenshot"]["native_capture"] and p["screenshot"]["dimensions"] == expected_size,
            "required_bounds_present": has_expected,
            "cta_and_camera_non_overlap": no_overlap,
            "status": "PASS" if has_expected and no_overlap and p["screenshot"]["dimensions"] == expected_size else "FAIL",
        })
    responsive_pass = all(item["status"] == "PASS" for item in responsive_profiles) and typography_pass
    responsive_status = (
        "PASS" if responsive_pass else
        "BLOCKED_INFRA" if any(item["status"] == "BLOCKED_INFRA" for item in responsive_profiles) else
        "FAIL"
    )
    responsive_report = {
        "status": responsive_status,
        "profiles": responsive_profiles,
        "limitations": ["API28 runtime evidence covers 1080x1920 and 1080x2340 only; shared geometry's 320/393/412/480dp widths need their separate supported device profiles."],
    }

    # Sample the native screen recording in the Hero and camera regions. Change
    # counts are descriptive evidence and do not create a visual pass threshold.
    video = out / "11_Normal_Home_Runtime_18s.mp4"
    require_file(video)
    duration_text = subprocess.check_output(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(video)],
        text=True,
    ).strip()
    duration = float(duration_text)
    video_sha = sha(video)
    frame_times = [0.25, 0.5, 1.0, 3.0, 6.0, 9.0, 12.0, 15.0]
    frames = motion_frames(video, frame_times)
    hero_roi = (170, 596, 910, 1476)
    camera_roi = (400, 1580, 680, 1800)
    hero_motion = [
        {"from_s": left, "to_s": right, "changed_pixels": changed_pixels(frames[left], frames[right], hero_roi)}
        for left, right in ((1.0, 3.0), (3.0, 6.0), (6.0, 9.0), (9.0, 12.0))
    ]
    camera_motion = [
        {"from_s": left, "to_s": right, "changed_pixels": changed_pixels(frames[left], frames[right], camera_roi)}
        for left, right in ((1.0, 3.0), (3.0, 6.0), (6.0, 9.0), (9.0, 12.0))
    ]
    motion_neutral = out / "normal_home_motion_neutral_1080x1920.png"
    require_file(motion_neutral)
    motion_trace_path = out / "normal-home-motion-instrumentation.log"
    motion_trace_log = motion_trace_path.read_text(encoding="utf-8", errors="replace") if motion_trace_path.is_file() else ""
    reveal_trace = parse_motion_trace(motion_trace_log, "NORMAL_HOME_MOTION_TRACE", "card_reveal")
    hero_trace = parse_motion_trace(motion_trace_log, "NORMAL_HOME_MOTION_TRACE", "hero_idle")
    avatar_trace = parse_motion_trace(motion_trace_log, "NORMAL_HOME_AVATAR_STATE_TRACE")

    reveal_duration = trace_number(reveal_trace, "duration_ms")
    reveal_alpha_0 = trace_number(reveal_trace, "alpha_0")
    reveal_alpha_80 = trace_number(reveal_trace, "alpha_80")
    reveal_alpha_160 = trace_number(reveal_trace, "alpha_160")
    reveal_settled_pixel_delta = trace_number(reveal_trace, "settled_pixel_delta")
    reveal_layout_delta = trace_number(reveal_trace, "max_layout_edge_delta_px")
    reveal_pass = (
        reveal_duration == 160
        and reveal_trace.get("easing") == "linear"
        and reveal_alpha_0 is not None and reveal_alpha_0 <= 0.05
        and reveal_alpha_80 is not None and 0.25 <= reveal_alpha_80 <= 0.75
        and reveal_alpha_160 is not None and reveal_alpha_160 >= 0.98
        and reveal_settled_pixel_delta is not None and reveal_settled_pixel_delta <= 2
        and reveal_layout_delta is not None and reveal_layout_delta <= 2.0
    )

    hero_period = trace_number(hero_trace, "period_ms")
    hero_scale_0 = trace_number(hero_trace, "scale_0")
    hero_scale_3000 = trace_number(hero_trace, "scale_3000")
    hero_scale_6000 = trace_number(hero_trace, "scale_6000")
    hero_y_3000 = trace_number(hero_trace, "y_3000_dp")
    hero_y_6000 = trace_number(hero_trace, "y_6000_dp")
    neighbor_q1 = trace_number(hero_trace, "neighbor_q1_dp")
    hero_breath_pass = (
        hero_period == 6000
        and hero_scale_0 is not None and abs(hero_scale_0 - 1.0) <= 0.002
        and hero_scale_3000 is not None and abs(hero_scale_3000 - 1.008) <= 0.002
        and hero_scale_6000 is not None and abs(hero_scale_6000 - 1.0) <= 0.002
        and hero_y_3000 is not None and abs(hero_y_3000 + 2.0) <= 0.5
        and hero_y_6000 is not None and abs(hero_y_6000) <= 0.5
        and neighbor_q1 is not None and abs(neighbor_q1 - 4.0) <= 0.01
    )
    avatar_pass = (
        avatar_trace.get("guest") == "GUEST_DEFAULT"
        and avatar_trace.get("profile_default") == "PROFILE_DEFAULT"
        and avatar_trace.get("loading") == "PROFILE_LOADING"
        and avatar_trace.get("ready") == "PROFILE_IMAGE"
        and avatar_trace.get("failed") == "PROFILE_DEFAULT"
        and avatar_trace.get("transition") == "immediate_replacement"
        and avatar_trace.get("animator") == "not_authorized_by_NH07"
    )
    motion_report = {
        "status": "PASS" if (
            reveal_pass and hero_breath_pass and avatar_pass and duration >= 16.0
        ) else "REVIEW_REQUIRED",
        "contract_version": "Normal Home Motion Contract V1",
        "video": {"path": video.name, "sha256": video_sha, "duration_seconds": round(duration, 3), "minimum_seconds": 16.0, "duration_status": "PASS" if duration >= 16.0 else "FAIL"},
        "reduce_motion": {"path": motion_neutral.name, "sha256": sha(motion_neutral), "settings": {"animator_duration_scale": 0, "transition_animation_scale": 0, "window_animation_scale": 0}, "status": "CAPTURED"},
        "instrumentation_trace": {"path": motion_trace_path.name, "sha256": sha(motion_trace_path) if motion_trace_path.is_file() else None},
        "hero_reveal": {
            "status": "PASS" if reveal_pass else "REVIEW_REQUIRED",
            "contract_values": {"duration_ms": 160, "delay_ms": 0, "easing": "linear", "property": "opacity_only", "repeat": "once"},
            "runtime_trace": reveal_trace,
            "video_observed_region_change_counts": hero_motion,
        },
        "hero_idle_breath": {"observed_region_change_counts": hero_motion, "status": "PASS" if hero_breath_pass else "REVIEW_REQUIRED", "runtime_trace": hero_trace},
        "camera_motion": {"observed_region_change_counts": camera_motion, "status": "OBSERVED" if any(item["changed_pixels"] > 0 for item in camera_motion) else "NOT_OBSERVED"},
        "avatar_guest_to_user_and_loading_to_ready": {"status": "PASS" if avatar_pass else "REVIEW_REQUIRED", "runtime_trace": avatar_trace, "rule": "NH07 specifies in-place state replacement; no avatar tween is authorized."},
        "card_entrance": {
            "status": "PASS" if reveal_pass else "REVIEW_REQUIRED",
            "contract_values": {"duration_ms": 160, "delay_ms": 0, "easing": "linear", "property": "opacity_only", "repeat": "once"},
            "runtime_trace": reveal_trace,
        },
    }

    bounds_report = {
        "status": "PASS" if hierarchy_pass else "FAIL",
        "measurement_method": "Compose boundsInWindow from API28 instrumentation; semantic hit bounds kept distinct from raster visual masks.",
        "device": {"api_level": base["api_level"], "model": base["device"], "resolution_px": base["screenshot"]["dimensions"], "density_dpi": base["density_dpi"], "font_scale": base["font_scale"]},
        "screenshot_sha256": base["screenshot"]["sha256"],
        "components": {
            "brand_title": {"runtime_boundsInWindow_px": title, "expected_visible_ink_px": anchors["brand_visible_glyph"], "raster_result": raster.get("BRAND_TITLE", {"status": "SEE_FROZEN_ARTWORK_BINDING_TEST"})},
            "avatar": {"runtime_boundsInWindow_px": avatar, "diameter_px": round(avatar[2] - avatar[0], 2), "touch_target_in_header": hierarchy_checks["avatar_inside_header"]["status"]},
            "hero": {"runtime_boundsInWindow_px": base_rects["hero"], "runtime_page_local_px": local_rects["hero"], "expected_page_local_px": expected_hero, "delta_px": hero["edge_delta_px"], "status": hero["status"]},
            "statistic_card": {"runtime_boundsInWindow_px": base_rects["stats"], "runtime_page_local_px": local_rects["stats"], "expected_page_local_y_height_px": [anchors["statistics"]["y"], anchors["statistics"]["height"]]},
            "cta": {
                "layout_boundsInWindow_px": cta,
                "layout_bounds_page_local_px": local_rects["cta_layout"],
                "text_boundsInWindow_px": base_rects["cta_text"],
                "text_bounds_page_local_px": local_rects["cta_text"],
                "visual_bounds_px": raster["CTA_NEAR_WHITE"]["runtime_bbox_inclusive_px"],
                "touch_owner": "camera_action",
                "touch_boundsInWindow_px": camera,
            },
            "camera": {"visual_circle_bounds_px": raster["CAMERA_GOLD"]["runtime_bbox_inclusive_px"], "touch_boundsInWindow_px": camera, "touch_bounds_page_local_px": local_rects["camera_touch"]},
        },
        "checks": hierarchy_checks,
    }

    pixel_pass = all(item["status"] == "PASS" for item in raster.values())
    pixel_report = {
        "status": "PASS" if pixel_pass else "REVIEW_REQUIRED",
        "runtime_screenshot_sha256": base["screenshot"]["sha256"],
        "frozen_target_path": str(FROZEN.relative_to(ROOT)),
        "frozen_target_sha256": frozen_sha,
        "same_native_dimensions": [1080, 1920],
        "global_mae_gate": "NOT_USED_BY_CONTRACT",
        "diffs": raster,
        "affected_regions": [key for key, item in raster.items() if item["status"] != "PASS"],
    }

    # Preserve the legacy report path while replacing its earlier placeholder
    # values with this run's measured outcomes.
    legacy_path = out / "09_normal_home_visual_parity_report.json"
    legacy = load(legacy_path) if legacy_path.exists() else {}
    legacy.update({
        "status": "PASS" if hierarchy_pass and pixel_pass else "REVIEW_REQUIRED",
        "frozen_hierarchy": "PASS" if hierarchy_pass else "FAIL",
        "hero_geometry": "BLOCKED" if hero_report["status"] == "BLOCKED" else "PASS",
        "typography_readability": typography_report["status"],
        "responsive_measurement": responsive_report["status"],
        "motion": motion_report["status"],
        "runtime_screenshot_sha256": base["screenshot"]["sha256"],
        "apk_sha256": base["apk_sha256"],
        "acceptance_summary": "BLOCKED; approved Hero photo provenance/bbox cases remain unavailable" if hero_report["status"] == "BLOCKED" else "See A.4 reports",
    })
    write_json(legacy_path, legacy)
    write_json(out / "visual_parity_report.json", legacy)

    write_json(out / "Normal_Home_Hierarchy_Report.json", hierarchy_report)
    write_json(out / "Normal_Home_Hero_EvidenceFit_Report.json", hero_report)
    write_json(out / "Normal_Home_Typography_Runtime_Report.json", typography_report)
    write_json(out / "Normal_Home_Motion_Runtime_Report.json", motion_report)
    write_json(out / "Normal_Home_Responsive_Runtime_Report.json", responsive_report)
    write_json(out / "Normal_Home_Runtime_Bounds_Report.json", bounds_report)
    write_json(out / "Normal_Home_Pixel_Diff_Report.json", pixel_report)
    camera_audit = subprocess.run([
        sys.executable, str(ROOT / "scripts/audit_normal_home_camera_mask.py"),
        "--screenshot", str(runtime_screenshot),
        "--output", str(out / "Camera_Core_Mask_Report.json"),
    ], check=False)
    if camera_audit.returncode not in (0, 1):
        raise ValueError("Camera mask diagnostic failed to execute")

    blockers = []
    if not hierarchy_pass:
        blockers.append({"priority": "P0", "id": "HIERARCHY_OR_GEOMETRY", "reason": "Runtime bounds did not meet frozen anchors or interaction containment."})
    if not responsive_pass:
        blockers.append({"priority": "P1", "id": "RESPONSIVE_RUNTIME_PROFILE", "reason": "One or more required runtime responsive profiles failed or could not be captured natively."})
    if hero_report["status"] != "PASS":
        blockers.append({"priority": "P1", "id": "HERO_APPROVED_FIXTURE_MATRIX", "reason": hero_report["blocking_reason"]})
    if typography_report["status"] != "PASS":
        blockers.append({"priority": "P1", "id": "TYPOGRAPHY_READABILITY", "reason": "One or more runtime text profiles did not satisfy the no-clipping/visibility contract."})
    if pixel_report["status"] != "PASS":
        blockers.append({"priority": "P1", "id": "PIXEL_TARGET_GLYPH_MASK", "reason": "One or more immutable source masks exceed their declared pixel tolerance."})
    if motion_report["status"] != "PASS":
        blockers.append({"priority": "P2", "id": "MOTION_RUNTIME_EVIDENCE", "reason": "Hero/card reveal timing and avatar state transition are not fully instrumented by this run."})
    overall = "PASS" if not blockers and hierarchy_pass and hero_report["status"] == "PASS" and typography_report["status"] == "PASS" and pixel_pass and motion_report["status"] == "PASS" and responsive_pass else "BLOCKED"

    audit = [
        "# Normal Home Runtime Visual Audit Report",
        "",
        f"- Branch commit: `{base['source_commit']}`",
        f"- API / device: `{base['api_level']} / {base['device']}`",
        f"- Resolution / density / fontScale: `{base['screenshot']['dimensions'][0]}×{base['screenshot']['dimensions'][1]} / {base['density_dpi']} dpi / {base['font_scale']}`",
        f"- APK SHA-256: `{base['apk_sha256']}`",
        f"- Screenshot SHA-256: `{base['screenshot']['sha256']}`",
        "- Page state: `NORMAL_HOME_DATA` / account `GUEST`; Empty Home is not used as the Data Home authority.",
        "",
        "## Closure result",
        "",
        f"`NORMAL_HOME_RUNTIME_PARITY = {overall}`",
        "",
        "## Evidence status",
        "",
        f"- Frozen hierarchy: `{hierarchy_report['status']}`",
        f"- Hero geometry / source evidence: `{hero_report['status']}`",
        f"- Typography readability: `{typography_report['status']}`",
        f"- Responsive runtime: `{responsive_report['status']}`",
        f"- Pixel target masks: `{pixel_report['status']}`",
        f"- Motion runtime: `{motion_report['status']}`",
        "",
        "## Remaining blockers",
        "",
    ]
    audit += [f"- **{item['priority']} `{item['id']}`** — {item['reason']}" for item in blockers] or ["- None."]
    audit += [
        "",
        "Reports are generated from native API28 runtime captures and instrumentation measurements. Frozen sources, thresholds, and asset bytes are not modified.",
    ]
    (out / "Normal_Home_Runtime_Visual_Audit_Report.md").write_text("\n".join(audit) + "\n", encoding="utf-8")
    closure = {
        "gate": "NORMAL_HOME_RUNTIME_PARITY",
        "status": overall,
        "commit": base["source_commit"],
        "apk_sha256": base["apk_sha256"],
        "screenshot_sha256": base["screenshot"]["sha256"],
        "subgates": {
            "hierarchy": hierarchy_report["status"],
            "hero": hero_report["status"],
            "typography": typography_report["status"],
            "responsive": responsive_report["status"],
            "pixel_targets": pixel_report["status"],
            "motion": motion_report["status"],
        },
        "blockers": blockers,
    }
    write_json(out / "Normal_Home_Runtime_Closure_Result.json", closure)
    print(json.dumps(closure, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: verify_normal_home_a4_runtime_evidence.py EVIDENCE_DIRECTORY")
    try:
        raise SystemExit(main(Path(sys.argv[1]).resolve()))
    except (OSError, ValueError, KeyError, IndexError, subprocess.CalledProcessError) as exc:
        print(f"A4_EVIDENCE_VALIDATION_FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
