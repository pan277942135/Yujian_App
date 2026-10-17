#!/usr/bin/env python3
"""Audits recognition-only test catalog, E2E compositions and QA user reverse index."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "docs/qa/recognition_main_journey/test_case_catalog_v1.json"
E2E = ROOT / "docs/qa/recognition_main_journey/e2e_scenarios_v1.json"
INPUTS = ROOT / "docs/qa/recognition_main_journey/test_inputs_v1.json"
USERS = ROOT / "app/src/androidTest/assets/qa/recognition/v1/test_users.json"
COUNTS = {"EN": 6, "PER": 7, "GUIDE": 11, "CAP": 9, "PROC": 9,
          "RES": 10, "NAV": 7, "SYS": 7}
STATES = {"FIRST_USE", "GUIDE_PENDING", "RETURNING", "PERMISSION_DENIED"}
LEVELS = {"L1", "L2", "L3"}
MODES = {"NATIVE_DEVICE", "CONTROLLED_UI", "STATE_MACHINE"}
E2E_MODES = {"REAL_DEVICE", "CONTROLLED_UI", "CONTROLLED_FAILURE"}
STATUS = {"DRAFT", "REVIEW", "FROZEN"}
EVIDENCE_FIELDS = {"source_sha", "apk_sha256", "native_runtime_trace", "jvm_assertion_log",
                   "instrumentation_assertions", "camera_capture_trace", "native_screenshots"}
PHOTO = re.compile(r"^IMG-(0[1-8])$")
RESP = re.compile(r"^RESP-(HIGH|MEDIUM|LOW|NO_FISH|WARNING|INVALID)$")
SEMVER = re.compile(r"^\d+\.\d+\.\d+$")


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def validate(catalog: dict, e2e: dict, inputs: dict, users: dict,
             *, check_paths: bool = True) -> list[str]:
    errors: list[str] = []
    def require(ok: bool, why: str) -> None:
        if not ok:
            errors.append(why)
    def nonempty_list(seq: object) -> bool:
        return isinstance(seq, list) and bool(seq) and all(isinstance(x, str) and x.strip() for x in seq)

    require(catalog.get("schema_version") == "1.0" and catalog.get("catalog_id") ==
            "YU_JIAN_RECOGNITION_MAIN_FLOW_CASES", "catalog identity invalid")
    require(e2e.get("schema_version") == "1.0" and e2e.get("suite_id") ==
            "YU_JIAN_RECOGNITION_E2E", "E2E suite identity invalid")
    require(inputs.get("schema_version") == "1.0" and inputs.get("registry_id") ==
            "YU_JIAN_RECOGNITION_TEST_INPUTS", "input registry identity invalid")
    for data, key in ((catalog, "catalog_version"), (e2e, "suite_version"),
                      (inputs, "registry_version"), (users, "dataset_version")):
        require(bool(SEMVER.fullmatch(str(data.get(key, "")))), f"invalid semver: {key}")
    require(catalog.get("status") == "REVIEW" and e2e.get("status") == "REVIEW" and
            inputs.get("status") == "REVIEW", "review-phase catalogs must not claim Frozen/PASS")
    versions = {users.get("dataset_version"), catalog.get("test_user_dataset_version"),
                e2e.get("test_user_dataset_version")}
    require(len(versions) == 1, "dataset versions not synchronized")
    for name, data in (("catalog", catalog), ("E2E", e2e)):
        require(data.get("test_user_id") == "QA_RECOGNITION_001",
                name + ": test user id drift")
    require(users.get("identity", {}).get("user_id") == "QA_RECOGNITION_001",
            "invalid source user ID")
    cases = catalog.get("cases", [])
    require(isinstance(cases, list) and len(cases) == 66 and catalog.get("case_count") == 66,
            "expected exactly 66 base cases")
    require(catalog.get("module_counts") == COUNTS, "expected 8 module counts drift")
    by_case: dict[str, dict] = {}
    for item in cases if isinstance(cases, list) else []:
        cid = str(item.get("case_id", ""))
        require(cid not in by_case, f"duplicate base case {cid}")
        by_case[cid] = item
        module = item.get("module")
        require(module in COUNTS and cid.startswith(str(module) + "-"), f"bad case module {cid}")
        require(item.get("priority") in ("P0", "P1"), f"bad priority {cid}")
        require(item.get("initial_state") in STATES, f"missing primary user state {cid}")
        require(item.get("test_user_id") == "QA_RECOGNITION_001", f"bad user binding {cid}")
        layer = item.get("test_layer")
        require(layer in LEVELS, f"invalid layer {cid}")
        require(item.get("execution_mode") in MODES, f"invalid mode {cid}")
        require(item.get("execution_mode") == {"L1":"STATE_MACHINE","L2":"CONTROLLED_UI","L3":"NATIVE_DEVICE"}.get(layer),
                f"layer/mode mismatch {cid}")
        require(item.get("design_status") in STATUS, f"bad design status {cid}")
        require(item.get("execution_status") == "NOT_RUN", f"source catalog must not fake execution PASS {cid}")
        require(nonempty_list(item.get("steps")) and nonempty_list(item.get("assertions")),
                f"missing steps/assertions {cid}")
        require(nonempty_list(item.get("evidence_required")) and nonempty_list(item.get("authority_refs")),
                f"missing evidence/authority {cid}")
        if item.get("design_status") == "REVIEW":
            require(bool(item.get("review_reason")), f"review case needs reason {cid}")
        if check_paths:
            for authority in item.get("authority_refs", []):
                require((ROOT / authority).is_file(), f"authority missing {cid}: {authority}")
        if layer == "L3":
            require("apk_sha256" in item.get("evidence_required", []) and
                    "native_runtime_trace" in item.get("evidence_required", []),
                    f"native evidence missing {cid}")
    for module, count in COUNTS.items():
        expected = {f"{module}-{i:02d}" for i in range(1, count + 1)}
        found = {c for c in by_case if c.startswith(module+"-")}
        require(found == expected, f"non-contiguous/missing {module} case ID")
    require(len(by_case) == 66, "duplicate 66-case keys")

    scenarios = e2e.get("scenarios", [])
    require(isinstance(scenarios, list) and len(scenarios) == 10 and
            e2e.get("composition_count") == 10, "expected exactly ten E2E scenarios")
    by_e2e: dict[str, dict] = {}
    for item in scenarios if isinstance(scenarios, list) else []:
        sid = str(item.get("scenario_id", ""))
        require(sid not in by_e2e, f"duplicate E2E {sid}")
        by_e2e[sid] = item
        require(item.get("test_user_id") == "QA_RECOGNITION_001" and
                item.get("initial_state") in STATES, f"E2E primary user state missing {sid}")
        require(item.get("execution_mode") in E2E_MODES, f"invalid E2E mode {sid}")
        require(item.get("design_status") in STATUS and item.get("execution_status") == "NOT_RUN",
                f"unverified E2E declared PASS or invalid design state {sid}")
        require(nonempty_list(item.get("assertions")) and
                nonempty_list(item.get("covers_case_ids")), f"no E2E assertions/coverage {sid}")
        require(nonempty_list(item.get("evidence_required")), f"no E2E evidence {sid}")
        for cid in item.get("covers_case_ids", []):
            require(cid in by_case, f"E2E {sid} references unknown base case {cid}")
        require(len(set(item.get("covers_case_ids", []))) == len(item.get("covers_case_ids", [])),
                f"E2E duplicate covered case ID {sid}")
        if item.get("design_status") == "REVIEW":
            require(bool(item.get("review_reason")), f"E2E REVIEW without reason {sid}")
        if item.get("execution_mode") == "REAL_DEVICE":
            require("camera_capture_trace" in item.get("evidence_required", []),
                    f"real E2E missing camera proof {sid}")
    require(set(by_e2e) == {f"E2E-{n:02d}" for n in range(1,11)}, "E2E IDs not exact 01–10")
    quality = by_e2e.get("E2E-07", {})
    require("PROC-09" not in quality.get("covers_case_ids", []) and
            "RES-05" in quality.get("covers_case_ids", []),
            "Image Quality INVALID must not reuse TECHNICAL_FAILURE oracle PROC-09")
    for sid in ("E2E-05", "E2E-08"):
        require(by_e2e.get(sid, {}).get("execution_mode") != "REAL_DEVICE",
                f"{sid}: controlled injection misrepresented as real E2E")

    source_ids = [entry.get("input_id") for entry in inputs.get("inputs", [])]
    require(len(source_ids) == len(set(source_ids)), "duplicate test input ID")
    source_by_id = {entry["input_id"]:entry for entry in inputs.get("inputs", [])}
    require({f"IMG-{i:02d}" for i in range(1,9)}.issubset(set(source_ids)),
            "IMG-01–IMG-08 input registry incomplete")
    for item in list(by_case.values()) + list(by_e2e.values()):
        cid = item.get("case_id") or item.get("scenario_id")
        for ref in item.get("input_ids", []):
            require(ref in source_by_id, f"{cid}: unknown input {ref}")
        if item.get("execution_mode") in ("NATIVE_DEVICE", "REAL_DEVICE") and \
                any(str(ref).startswith("RESP-") for ref in item.get("input_ids", [])):
            # Native device screenshot/result state may be injected for UI tests;
            # claims of real-model end-to-end must not.
            if item.get("execution_mode") == "REAL_DEVICE":
                require(False, f"REAL_DEVICE E2E cannot be satisfied with response mock: {cid}")
    for record in inputs.get("inputs", []):
        if PHOTO.fullmatch(record["input_id"]):
            require(record.get("status") in {"MISSING", "CANDIDATE_REVIEW", "SPEC_ONLY", "APPROVED"},
                    f"invalid photo source state {record['input_id']}")
            if record.get("status") == "APPROVED":
                require(bool(record.get("source_sha256") and record.get("rights_evidence")),
                        f"approved photo missing provenance/rights {record['input_id']}")
            else:
                require(record.get("source_sha256") is None,
                        f"candidate photo must not present approved SHA {record['input_id']}")
            if check_paths and record.get("repository_candidate"):
                require((ROOT / record["repository_candidate"]).is_file(),
                        f"missing candidate path {record['input_id']}")

    states = users.get("states", [])
    actual_states = {s.get("id") for s in states}
    require(actual_states == STATES and len(states) == 4, "QA user states missing/duplicate")
    expected_reverse: dict[str, set] = {sid:set() for sid in STATES}
    for item in list(by_case.values()) + list(by_e2e.values()):
        sid = item.get("initial_state")
        if sid in STATES:
            expected_reverse[sid].add(item.get("case_id") or item.get("scenario_id"))
    for state in states:
        sid = state.get("id")
        if sid not in STATES:
            continue
        links = state.get("test_case_ids", [])
        require(len(links) == len(set(links)), f"duplicated mapping in {sid}")
        require(set(links) == expected_reverse[sid],
                f"reverse case mapping inconsistent for {sid}")
    bindings = users.get("catalog_bindings", {})
    require(bindings.get("base_cases") == str(CATALOG.relative_to(ROOT)) and
            bindings.get("e2e_scenarios") == str(E2E.relative_to(ROOT)) and
            bindings.get("input_registry") == str(INPUTS.relative_to(ROOT)),
            "missing canonical GitHub catalog links in QA user")
    return errors


def main() -> int:
    try:
        errors = validate(load(CATALOG), load(E2E), load(INPUTS), load(USERS))
    except (OSError, ValueError, KeyError, TypeError) as exc:
        errors = [str(exc)]
    summary = {"status":"PASS" if not errors else "FAIL", "base_cases":66, "e2e_scenarios":10,
               "unique_primary_user_states":4, "errors":errors,
               "scope":"Catalog consistency only; not Android/API28 runtime acceptance"}
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if not errors else 1

if __name__ == "__main__":
    raise SystemExit(main())
