"""Classify NH01 device-matrix failures by the evidence that actually failed."""
from __future__ import annotations

from typing import Any

PRODUCT_FAILURE_MARKERS = (
    "failure_message=camera artwork frame follows the V4 responsive visual size",
    "failure_message=camera artwork frame remains square",
    "failure_message=camera artwork is centered in the interactive target",
    "failure_message=camera artwork remains above the actual safe bottom inset",
    "failure_message=camera effective touch target remains",
    "failure_message=hero aspect ratio matches frozen",
    "failure_message=hero is centered in the carousel track",
    "failure_message=hero remains inside the track",
    "failure_message=hero remains within 70% width",
    "failure_message=side card width stays within 82%",
    "failure_message=side card height remains",
    "failure_message=CTA label does not overlap",
    "failure_message=footer and camera remain above",
    "PAGER_SINGLE_PAGE_INTERACTION_FAILED",
    "SWIPE_TO_NEXT_REAL_REPOSITORY_CATCH",
    "Adaptive V2 species, measurement, or metadata text visually overflows",
)
EVIDENCE_FAILURE_MARKERS = (
    "FAIL_CAPTURE_PIPELINE",
    "SCREENSHOT_MISSING",
    "SCREENSHOT_DECODE_FAILED",
    "EVIDENCE_HASH_MISMATCH",
    "PROFILE_SETTINGS_MISMATCH",
)
TEST_FAILURE_MARKERS = (
    "INSTRUMENTATION_EXIT_",
    "TEST_ASSERTION_FAILED",
    "TEST_HARNESS_ERROR",
)


def classify_matrix_result(matrix_rc: int, report: dict[str, Any]) -> tuple[str, list[str]]:
    records = [
        case.get("execution", {})
        for case in report.get("cases", [])
        if isinstance(case, dict) and isinstance(case.get("execution", {}), dict)
    ]
    statuses = [str(record.get("status", "MISSING")).upper() for record in records]
    failures = [record for record in records if str(record.get("status", "")).upper() == "FAIL"]
    reasons = sorted({str(record.get("reason")) for record in records if record.get("reason")})

    if matrix_rc == 0 and len(records) == 3 and statuses == ["PASS"] * 3:
        return "PASS", reasons
    if any(
        marker in str(record.get("reason", ""))
        for record in failures
        for marker in PRODUCT_FAILURE_MARKERS
    ):
        return "FAIL_PRODUCT", reasons
    if any(
        marker in str(record.get("reason", ""))
        for record in failures
        for marker in EVIDENCE_FAILURE_MARKERS
    ):
        return "FAIL_EVIDENCE", reasons
    if any(status in {"MISSING", "RUNNING"} for status in statuses):
        return "BLOCKED_INFRA", reasons
    if any(
        marker in str(record.get("reason", ""))
        for record in failures
        for marker in TEST_FAILURE_MARKERS
    ) or any("failure_class=" in str(record.get("reason", "")) for record in failures):
        return "FAIL_TEST", reasons
    if failures or matrix_rc != 0:
        return "FAIL_EVIDENCE", reasons
    return "BLOCKED_INFRA", reasons

if __name__ == "__main__":
    import json
    import sys
    from pathlib import Path

    report_path = Path(sys.argv[1])
    matrix_rc = int(sys.argv[2])
    try:
        report_data = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        report_data = {}
    classification, failure_reasons = classify_matrix_result(matrix_rc, report_data)
    print(classification + "\t" + "; ".join(failure_reasons))
