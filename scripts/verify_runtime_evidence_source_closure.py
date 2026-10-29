#!/usr/bin/env python3
"""Fail-closed, source-backed promotion of Android visual evidence.

This validator intentionally does not approve provenance or visual quality by
itself. Those decisions must be evidenced independently in source manifests
and review records, then checked against exact APK and screenshot bytes.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
APPROVED = {"APPROVED", "VERIFIED"}
PAGES = ("P01", "P02", "P03")
CASES = tuple("F%02d" % i for i in range(1, 9))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def in_root(root: Path, name: object) -> Path | None:
    if not isinstance(name, str) or not name or Path(name).is_absolute():
        return None
    path = (root / name).resolve()
    if not path.is_relative_to(root.resolve()) or not path.is_file():
        return None
    return path


def matches(root: Path, name: object, expected: object, size: tuple[int, int] | None = None) -> bool:
    path = in_root(root, name)
    if not path or not isinstance(expected, str) or len(expected) != 64:
        return False
    if digest(path) != expected:
        return False
    if size is not None:
        try:
            with Image.open(path) as image:
                image.load()
                return image.size == size
        except (OSError, ValueError):
            return False
    return True


def visual_review_ok(review: object, evidence_root: Path) -> bool:
    if not isinstance(review, dict) or review.get("decision") != "APPROVED":
        return False
    if not isinstance(review.get("reviewer"), str) or not review["reviewer"].strip():
        return False
    return matches(evidence_root, review.get("artifact"), review.get("artifact_sha256"))


def _capture_ok(entry: object, evidence_root: Path, apk: str, sha: str,
                fish_record: str, seen: set[str]) -> bool:
    if not isinstance(entry, dict):
        return False
    if any(entry.get(key) != value for key, value in (
        ("capture_origin", "ANDROID_API28_INSTRUMENTATION"),
        ("api_level", 28), ("source_commit", sha), ("apk_sha256", apk),
        ("fish_record_id", fish_record))):
        return False
    checksum = entry.get("sha256")
    if not isinstance(checksum, str) or checksum in seen:
        return False
    if not matches(evidence_root, entry.get("path"), checksum, (1080, 1920)):
        return False
    seen.add(checksum)
    return True


def evaluate_home(fixtures: dict, preflight: dict, evidence_root: Path,
                  source_commit: str, apk_sha256: str) -> dict:
    """Require eight approved originals and independently reviewed HOME/DETAIL captures."""
    path = evidence_root / "hero_runtime_matrix.json"
    reasons: list[str] = []
    if preflight.get("status") != "SOURCE_READY":
        reasons.append("F01_F08_APPROVED_ORIGINALS_PROVENANCE_INCOMPLETE")
    if not path.is_file():
        reasons.append("HERO_RUNTIME_MATRIX_NOT_CAPTURED")
        matrix = {}
    else:
        try:
            matrix = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError, TypeError):
            matrix = {}
            reasons.append("HERO_RUNTIME_MATRIX_INVALID")
    records = matrix.get("cases", []) if isinstance(matrix, dict) else []
    by_id = {item.get("fixture_id"): item for item in records if isinstance(item, dict)}
    if len(records) != len(CASES) or set(by_id) != set(CASES):
        reasons.append("HERO_RUNTIME_CASE_COVERAGE_INCOMPLETE_OR_DUPLICATED")
    fixtures_by_id = {item["id"]: item for item in fixtures.get("fixtures", [])}
    seen_images: set[str] = set()
    for fid in CASES:
        item = fixtures_by_id.get(fid, {})
        capture = by_id.get(fid, {})
        rid = capture.get("fish_record_id")
        source_sha = item.get("source_sha256")
        problems: list[str] = []
        if not rid or not isinstance(rid, str):
            problems.append("RECORD_ID_MISSING")
        if capture.get("source_sha256_before") != source_sha or capture.get("source_sha256_after") != source_sha:
            problems.append("STORED_ORIGINAL_HASH_NOT_PRESERVED")
        if capture.get("source_commit") != source_commit or capture.get("apk_sha256") != apk_sha256:
            problems.append("EXACT_BUILD_PROVENANCE_MISMATCH")
        if capture.get("render_mode") not in {"CROP", "EVIDENCE_FIT"}:
            problems.append("UNVERIFIED_RENDER_MODE")
        if capture.get("fish_visibility") != "VERIFIED":
            problems.append("FISH_VISIBILITY_UNVERIFIED")
        if not visual_review_ok(capture.get("visual_review"), evidence_root):
            problems.append("INDEPENDENT_VISUAL_REVIEW_MISSING")
        for surface in ("HOME", "DETAIL"):
            if not _capture_ok(capture.get(surface), evidence_root, apk_sha256,
                               source_commit, rid, seen_images):
                problems.append(surface + "_NATIVE_CAPTURE_INVALID")
        if problems:
            reasons.append(fid + ":" + ",".join(problems))
    status = "PASS" if not reasons else "BLOCKED"
    return {
        "status": status,
        "fixture_preflight_status": preflight.get("status", "UNKNOWN"),
        "fixture_preflight_report": "Hero_Fixture_Preflight_Report.json",
        "runtime_evidence_matrix": "hero_runtime_matrix.json" if path.is_file() else None,
        "required_real_photo_cases": [
            {"fixture_id": fid, "case": fixtures_by_id.get(fid, {}).get("test_case"),
             "source_status": next((x.get("source_status") for x in preflight.get("cases", [])
                                    if x.get("fixture_id") == fid), "UNKNOWN")}
            for fid in CASES
        ],
        "blocking_reason": "; ".join(reasons),
        "promotion_rule": "Approved F01-F08 originals + exact-head HOME/DETAIL device capture + unchanged source photo + independent visual review",
    }


def evaluate_guide(report: dict, pages: list[dict], evidence_root: Path) -> dict:
    """Do not confuse Compose-component captures with live CameraX route evidence."""
    reasons = []
    if report.get("runtime_route_evidence") != "PASS":
        reasons.append("PRODUCTION_CAMERAX_ROUTE_NOT_VERIFIED")
    if report.get("runtime_evidence_status") != "PRODUCTION_CAMERAX_INSTRUMENTATION":
        reasons.append("COMPONENT_SCREENSHOTS_ARE_NOT_ROUTE_EVIDENCE")
    if report.get("runtime_profile_validation", {}).get("status") != "PASS":
        reasons.append("DEVICE_PROFILE_NOT_VERIFIED")
    if report.get("apk_sha256_match") is not True:
        reasons.append("APK_SHA_MISMATCH")
    observed = {item.get("page"): item for item in pages}
    if set(observed) != set(PAGES) or len(pages) != 3:
        reasons.append("THREE_PAGE_RUNTIME_COVERAGE_MISSING")
    for page in PAGES:
        p = observed.get(page, {})
        assets = p.get("runtime_photo_assets", {})
        if assets.get("status") != "SOURCE_READY":
            reasons.append(page + ":INDEPENDENT_PHOTO_ASSETS_NOT_APPROVED")
            continue
        sources = [assets.get("hero")] + assets.get("errors", [])
        if len(sources) != 4:
            reasons.append(page + ":REQUIRED_HERO_AND_THREE_ERROR_SOURCES_MISSING")
            continue
        for index, source in enumerate(sources):
            if not isinstance(source, dict) or not matches(
                ROOT, source.get("source_path"), source.get("sha256")
            ) or source.get("photo_provenance") not in APPROVED or \
                    source.get("source_usage_rights") not in APPROVED or \
                    not source.get("source_evidence") or not source.get("usage_evidence"):
                reasons.append(page + ":SOURCE_%d_INVALID_OR_NOT_APPROVED" % index)
        if p.get("visual_status") != "APPROVED" or not visual_review_ok(
            p.get("visual_review"), evidence_root
        ):
            reasons.append(page + ":INDEPENDENT_RUNTIME_VISUAL_REVIEW_MISSING")
        shot = p.get("screenshot")
        shot_sha = p.get("screenshot_sha256")
        if not matches(evidence_root.parent, shot, shot_sha, (1080, 1920)):
            reasons.append(page + ":PRODUCTION_SCREENSHOT_SHA_OR_DIMENSIONS_INVALID")
    if reasons:
        return {"status": "BLOCKED_ASSET_SOURCE", "pass": False, "blockers": reasons}
    return {"status": "PASS", "pass": True, "blockers": []}
