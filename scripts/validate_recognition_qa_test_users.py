#!/usr/bin/env python3
"""Validate committed QA recognition users; no backend accounts or secrets."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT = ROOT / "app/src/androidTest/assets/qa/recognition/v1/test_users.json"
CASE_ID = re.compile(r"^(?:EN|PER|GUIDE|CAP|PROC|RES|NAV|SYS|E2E)-\d{2}$")
REQUIRED_STATES = {"FIRST_USE", "GUIDE_PENDING", "RETURNING", "PERMISSION_DENIED"}
FORBIDDEN = re.compile(r"(?i)(password|passwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|cookie|credential|private[_-]?key|bearer\s+)")
EXPECTED_STORAGE = {
    "guest_archive_preferences": "yujian_guest_archive",
    "guest_records_key": "records",
    "guest_image_directory": "guest_catches",
    "guide_preferences": "yujian_capture_guide_v1",
    "guide_completed_key": "capture_guide_v1_completed",
}
EXPECTED_SETUP = {
    "FIRST_USE": (False, "HOST_REVOKE"),
    "GUIDE_PENDING": (False, "HOST_GRANT"),
    "RETURNING": (True, "HOST_GRANT"),
    "PERMISSION_DENIED": (False, "HOST_REVOKE"),
}
METHODS = {
    "FIRST_USE": "seedFirstUseGuest",
    "GUIDE_PENDING": "seedGuidePendingGuest",
    "RETURNING": "seedReturningGuest",
    "PERMISSION_DENIED": "seedPermissionDeniedGuest",
}


def validate(data: dict, source: Path = DEFAULT) -> list[str]:
    errors: list[str] = []
    def need(condition: bool, message: str) -> None:
        if not condition:
            errors.append(message)
    need(data.get("schema_version") == "1.0", "schema_version must be 1.0")
    version = data.get("dataset_version")
    need(isinstance(version, str) and bool(re.fullmatch(r"\d+\.\d+\.\d+", version)), "invalid semver")
    need(data.get("dataset_id") == "YU_JIAN_RECOGNITION_QA_USERS", "dataset id drift")
    need(data.get("status") == "FROZEN_BASELINE", "baseline status drift")
    ident = data.get("identity", {})
    need(ident.get("user_id") == "QA_RECOGNITION_001", "test identity id drift")
    need(ident.get("type") == "GUEST_LOCAL_FIXTURE", "user type must remain local guest")
    for key in ("is_real_backend_account", "is_production_user", "credentials_in_git"):
        need(ident.get(key) is False, f"{key} must be false")
    store = data.get("storage_contract", {})
    for k, expected in EXPECTED_STORAGE.items():
        need(store.get(k) == expected, f"storage contract mismatch: {k}")
    need(set(store.get("allowed_mutations", [])) == {
        "guest_archive_preferences", "guest_image_directory", "guide_preferences"
    }, "reset scope must be allowlisted exactly")
    need("system_camera_permission" in store.get("forbidden_mutations", []),
         "camera permission must remain host controlled")
    invariant = data.get("invariant", {})
    need(invariant.get("initial_fish_records") == 0, "recognition users must start with zero catches")
    need(invariant.get("reset_is_idempotent") is True, "reset must be idempotent")
    need(invariant.get("persistence_scope") == "APP_PRIVATE_DEBUG_TARGET_ONLY",
         "fixture must not mutate release or backend")
    states = data.get("states", [])
    ids = [state.get("id") for state in states if isinstance(state, dict)]
    need(len(ids) == len(REQUIRED_STATES) and set(ids) == REQUIRED_STATES,
         "state roster is not the frozen four profiles")
    seen_case_ids: set[str] = set()
    for state in states:
        sid = state.get("id")
        if sid not in EXPECTED_SETUP:
            continue
        expected_done, expected_permission = EXPECTED_SETUP[sid]
        need(state.get("guide_completed") is expected_done, f"{sid}: guide persistence mismatch")
        need(state.get("camera_permission") == expected_permission, f"{sid}: host permission mismatch")
        need(state.get("expected_initial_page") == "EMPTY_HOME", f"{sid}: home must start empty")
        cases = state.get("test_case_ids", [])
        need(bool(cases) and len(cases) == len(set(cases)), f"{sid}: duplicate/missing test case ids")
        for case in cases:
            need(isinstance(case, str) and bool(CASE_ID.fullmatch(case)),
                 f"{sid}: invalid testcase id {case!r}")
            seen_case_ids.add(case)
    need("E2E-01" in seen_case_ids and "E2E-02" in seen_case_ids,
         "first-use and return E2E coverage must remain")
    plain = json.dumps(data, ensure_ascii=False)
    # Structural denial, not an assertion that a GitHub repository is a credential vault.
    for key in data:
        need(not FORBIDDEN.search(key) or key == "extension_policy",
             f"sensitive top-level key: {key}")
    def walk(o, path=""):
        if isinstance(o, dict):
            for key, value in o.items():
                if FORBIDDEN.search(key) and key != "credentials_in_git":
                    # forbidden_mutations is a *list of prohibited categories*, not secrets.
                    errors.append(f"secret-bearing key prohibited: {path}/{key}")
                walk(value, f"{path}/{key}")
        elif isinstance(o, list):
            for i, value in enumerate(o):
                walk(value, f"{path}/{i}")
    walk(data)
    if source == DEFAULT:
        kotlin = ROOT / "app/src/androidTest/java/com/yujian/ai/RecognitionQaUserFixtureSeedTest.kt"
        need(kotlin.is_file(), "missing Android seed bridge")
        if kotlin.is_file():
            source_code = kotlin.read_text(encoding="utf-8")
            for method in METHODS.values():
                need(f"fun {method}()" in source_code, f"missing seed entrypoint: {method}")
            need("FLAG_DEBUGGABLE" in source_code, "missing debug-only guard")
            need("qa_seed_enabled" in source_code, "missing opt-in instrumentation argument")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT)
    args = parser.parse_args()
    try:
        raw = args.manifest.read_bytes()
        data = json.loads(raw.decode("utf-8"))
        errors = validate(data, args.manifest.resolve())
    except (OSError, ValueError, UnicodeError) as exc:
        errors = [f"manifest inaccessible/invalid: {exc}"]
        raw = b""
        data = {}
    report = {
        "dataset_id": data.get("dataset_id"),
        "dataset_version": data.get("dataset_version"),
        "manifest_sha256": hashlib.sha256(raw).hexdigest() if raw else None,
        "status": "PASS" if not errors else "FAIL",
        "errors": errors,
        "note": "Manifest validation only; does not create accounts, grant CAMERA, assert AI inference or certify Runtime."
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
