#!/usr/bin/env python3
"""Validate required NH01 Gemini runtime evidence without treating missing files as success."""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

COMPOSE_REPORT = Path("compose-simulation/gemini_responsive_compose_report.json")
AVATAR_REPORT = Path("avatar/gemini_avatar_transition_report.json")
CAROUSEL_REPORT = Path("compose-simulation/carousel/gemini_catch_carousel_count_report.json")


class EvidenceValidationError(ValueError):
    """Raised when a required runtime evidence report is missing or invalid."""


def _read_json(root: Path, relative: Path) -> dict[str, Any]:
    path = root / relative
    if not path.is_file():
        raise EvidenceValidationError(f"MISSING: {relative.as_posix()}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise EvidenceValidationError(f"INVALID_JSON: {relative.as_posix()}: {error}") from error
    if not isinstance(value, dict):
        raise EvidenceValidationError(f"INVALID_SCHEMA: {relative.as_posix()} must contain a JSON object")
    return value


def validate(root: Path) -> dict[str, Any]:
    """Validate the exact directory tree produced by the Android instrumentation tests."""
    root = Path(root)
    required = (COMPOSE_REPORT, AVATAR_REPORT, CAROUSEL_REPORT)
    missing = [relative.as_posix() for relative in required if not (root / relative).is_file()]
    if missing:
        raise EvidenceValidationError("MISSING: " + ", ".join(missing))

    compose = _read_json(root, COMPOSE_REPORT)
    avatar = _read_json(root, AVATAR_REPORT)
    carousel = _read_json(root, CAROUSEL_REPORT)

    profiles = compose.get("profiles")
    if not isinstance(profiles, list) or len(profiles) != 15:
        count = len(profiles) if isinstance(profiles, list) else "not-a-list"
        raise EvidenceValidationError(f"FAIL: Compose profile matrix expected 15 rows, got {count}")
    failed_profiles = [
        str(item.get("profile", index))
        for index, item in enumerate(profiles)
        if not isinstance(item, dict) or item.get("status") != "PASS"
    ]
    if failed_profiles:
        raise EvidenceValidationError("FAIL: Compose profiles did not pass: " + ", ".join(failed_profiles))

    missing_avatar_callbacks = [
        str(item.get("profile", index))
        for index, item in enumerate(profiles)
        if item.get("interactions", {}).get("guest_avatar_login") != "PASS"
    ]
    if missing_avatar_callbacks:
        raise EvidenceValidationError(
            "FAIL: guest avatar callback did not pass: " + ", ".join(missing_avatar_callbacks)
        )

    for field in ("account_switch_clears_previous_avatar", "logout_clears_previous_avatar"):
        if avatar.get(field) != "PASS":
            raise EvidenceValidationError(f"FAIL: avatar transition {field}={avatar.get(field)!r}")

    cases = carousel.get("count_cases")
    observed_counts = [item.get("catch_count") for item in cases if isinstance(item, dict)] if isinstance(cases, list) else []
    if observed_counts != [0, 1, 2, 3, 10]:
        raise EvidenceValidationError(f"FAIL: carousel count cases expected [0, 1, 2, 3, 10], got {observed_counts!r}")

    return {
        "status": "PASS",
        "compose_profiles": len(profiles),
        "avatar_transitions": "PASS",
        "carousel_count_cases": observed_counts,
        "reports": [item.as_posix() for item in required],
    }


def main(argv: list[str] | None = None) -> int:
    if not argv:
        argv = sys.argv[1:]
    if len(argv) != 1:
        print("Usage: validate_nh01_gemini_evidence.py REPORT_ROOT", file=sys.stderr)
        return 2
    try:
        result = validate(Path(argv[0]))
    except EvidenceValidationError as error:
        print(f"NH01_GEMINI_EVIDENCE_VALIDATION=FAIL reason={error}", file=sys.stderr)
        return 1
    print("NH01_GEMINI_EVIDENCE_VALIDATION=" + json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
