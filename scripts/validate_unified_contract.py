#!/usr/bin/env python3
"""Validate YuJian Unified Contract Registry V1.

This is a structural/traceability validator. It does not promote runtime,
visual, journey, or release status. Domain authorities remain authoritative.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError as exc:
    print("ERROR: PyYAML is required: python -m pip install PyYAML", file=sys.stderr)
    raise SystemExit(2) from exc

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROOT / "harness/UNIFIED_CONTRACT_REGISTRY.yaml"
MATRIX_PATH = ROOT / "harness/FEATURE_MATRIX.yaml"
STATE_PATH = ROOT / "PROJECT_MASTER_STATE.yaml"
MACHINE_PATH = ROOT / "harness/PHASE_TASK_STATE_MACHINE.yaml"

FEATURE_IDS = [
    "home_empty_v2", "home_normal_v1", "record_date_v2",
    "recognition_flow_v1_2", "recognition_result_v1", "fish_record_detail_v2",
    "share_templates_v1", "fish_memory_bside_v1", "my_catches_v2",
    "fish_guide_v2", "account_privacy_v1", "auth_login_v2",
    "auth_register_v2", "profile_edit_v1", "user_agreement_v1",
]
JOURNEY_IDS = [f"J{i:02d}" for i in range(1, 21)]
QUEUE_IDS = [f"N{i:03d}" for i in range(1, 11)]
CONTRACT_STATUSES = {
    "FROZEN", "READY", "ACTIVE_CLOSURE", "PARTIAL", "MISSING",
    "DEFERRED", "NOT_CONFIRMED", "HISTORICAL", "PROTECTED", "ACTIVE",
}
MODALITIES = ["behavior", "visual", "motion", "haptic", "sound", "runtime", "evidence", "assets"]


class UniqueKeyLoader(yaml.SafeLoader):
    pass


def _construct_mapping(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise yaml.constructor.ConstructorError(
                "while constructing a mapping",
                node.start_mark,
                f"duplicate key: {key!r}",
                key_node.start_mark,
            )
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


UniqueKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
    _construct_mapping,
)


def load_yaml(path: Path) -> dict[str, Any]:
    raw = path.read_text(encoding="utf-8")
    if "\t" in raw:
        raise ValueError(f"tabs are not allowed in YAML indentation: {path}")
    value = yaml.load(raw, Loader=UniqueKeyLoader)
    if not isinstance(value, dict):
        raise ValueError(f"root YAML value must be a mapping: {path}")
    return value


class Validation:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.gaps: list[str] = []

    def error(self, message: str) -> None:
        self.errors.append(message)

    def gap(self, message: str) -> None:
        self.gaps.append(message)

    def require(self, condition: bool, message: str) -> None:
        if not condition:
            self.error(message)


def expand_journeys(refs: list[str]) -> set[str]:
    result: set[str] = set()
    for ref in refs or []:
        if not isinstance(ref, str):
            continue
        match = re.fullmatch(r"J(\d{2})(?:-J(\d{2}))?", ref)
        if not match:
            continue
        start = int(match.group(1))
        end = int(match.group(2) or match.group(1))
        if 1 <= start <= end <= 20:
            result.update(f"J{i:02d}" for i in range(start, end + 1))
    return result


def is_local_ref(ref: str) -> bool:
    return (
        ref.startswith("design/")
        or ref.startswith("app/")
        or ref.startswith("scripts/")
        or ref.startswith("harness/")
        or ref in {"AGENTS.md", "PROJECT_MASTER_STATE.yaml"}
    )


def validate_local_ref(ref: str, v: Validation, owner: str) -> None:
    if not isinstance(ref, str) or not ref:
        return
    if not is_local_ref(ref):
        return
    path = ROOT / ref
    if not path.exists():
        v.error(f"{owner}: referenced local contract does not exist: {ref}")


def validate_project_alignment(registry: dict[str, Any], matrix: dict[str, Any], state: dict[str, Any], machine: dict[str, Any], v: Validation) -> None:
    v.require(registry.get("schema_version") == "1.0", "registry schema_version must be 1.0")
    v.require(registry.get("registry_id") == "yujian-unified-contract-registry", "registry_id mismatch")
    v.require(registry.get("status") == "ACTIVE", "registry status must be ACTIVE")

    rp = registry.get("project", {})
    mp = matrix.get("project", {})
    sp = state.get("state_control", {})
    v.require(rp.get("repository") == mp.get("repository"), "registry repository drift from Feature Matrix")
    v.require(rp.get("control_branch") == mp.get("control_branch"), "registry control branch drift from Feature Matrix")
    v.require(rp.get("execution_baseline_sha") == sp.get("execution_baseline_sha"), "registry execution baseline drift from Master State")
    v.require(rp.get("control_pr") == sp.get("current_pr"), "registry PR drift from Master State")
    v.require(rp.get("base_sha") == mp.get("base_sha"), "registry base SHA drift from Feature Matrix")
    v.require(rp.get("backend_repository") == state.get("repositories", {}).get("backend", {}).get("full_name"), "registry backend repository drift")
    v.require(rp.get("backend_main_sha") == state.get("repositories", {}).get("backend", {}).get("current_main_sha"), "registry backend SHA drift")

    inventory = registry.get("inventory", {})
    v.require(inventory.get("feature_count") == len(FEATURE_IDS), "registry feature_count must be 15")
    v.require(inventory.get("journey_count") == len(JOURNEY_IDS), "registry journey_count must be 20")
    v.require(inventory.get("route_count") == matrix.get("inventory", {}).get("route_count"), "registry route_count drift from Feature Matrix")
    v.require(inventory.get("task_count") == len(machine.get("tasks", {})), "registry task_count drift from Phase/Task Machine")
    v.require(inventory.get("execution_baseline_sha") == sp.get("execution_baseline_sha"), "registry inventory baseline drift")


def validate_features(registry: dict[str, Any], matrix: dict[str, Any], v: Validation) -> None:
    actual = registry.get("feature_contracts", {})
    matrix_features = matrix.get("features", {})
    v.require(list(actual.keys()) == FEATURE_IDS, "feature_contracts must contain exactly the 15 Feature Matrix features in order")

    for fid in FEATURE_IDS:
        feature = actual.get(fid, {})
        source = matrix_features.get(fid, {})
        v.require(feature.get("contract_id") == f"FC-{fid}", f"{fid}: contract_id mismatch")
        for key in ("name", "scope", "priority", "control_phase", "registry_overall", "owner_path"):
            v.require(feature.get(key) == source.get(key), f"{fid}: {key} drift from Feature Matrix")
        for key in ("child_surfaces", "journey_refs", "route_refs"):
            v.require(feature.get(key, []) == source.get(key, []), f"{fid}: {key} drift from Feature Matrix")

        domains = feature.get("domain_contracts", {})
        source_modalities = source.get("registry_modalities", {})
        for modality in MODALITIES:
            expected = source_modalities.get(modality, {})
            observed = domains.get(modality, {})
            v.require(observed.get("status") == expected.get("status"), f"{fid}:{modality} status drift")
            v.require(observed.get("authority") == expected.get("authority"), f"{fid}:{modality} authority drift")
            status = observed.get("status")
            v.require(status in CONTRACT_STATUSES, f"{fid}:{modality} unknown contract status {status!r}")
            validate_local_ref(observed.get("authority"), v, f"{fid}:{modality}")

        for ref in feature.get("page_contract_refs", []) or []:
            validate_local_ref(ref, v, f"{fid}:page_contract_refs")

        for ref in feature.get("shared_contract_refs", []) or []:
            v.require(ref in registry.get("shared_contracts", {}), f"{fid}: unknown shared contract {ref}")
        for ref in feature.get("additional_shared_contract_refs", []) or []:
            v.require(ref in registry.get("shared_contracts", {}), f"{fid}: unknown additional shared contract {ref}")

        required = feature.get("required_gate_classes", [])
        if source.get("scope") == "PRIMARY_JOURNEY":
            v.require("JOURNEY_E2E" in required, f"{fid}: primary feature must require JOURNEY_E2E")
        v.require("APK_PROVENANCE" in required, f"{fid}: feature must require APK_PROVENANCE")


def validate_journeys_and_children(registry: dict[str, Any], matrix: dict[str, Any], v: Validation) -> None:
    journeys = registry.get("journey_contracts", {})
    matrix_journeys = matrix.get("journeys", {})
    v.require(list(journeys.keys()) == JOURNEY_IDS, "journey_contracts must contain exactly J01-J20 in order")
    for jid in JOURNEY_IDS:
        observed = journeys.get(jid, {})
        expected = matrix_journeys.get(jid, {})
        v.require(observed.get("name") == expected.get("name"), f"{jid}: journey name drift")
        v.require(observed.get("feature_refs", []) == expected.get("feature_refs", []), f"{jid}: feature refs drift")
        v.require(
            observed.get("contract_refs", []) == [f"FC-{x}" for x in expected.get("feature_refs", [])],
            f"{jid}: contract refs must resolve to owner features",
        )
        v.require(observed.get("acceptance_owner") == "N003", f"{jid}: acceptance owner must remain N003")

    expected_children: dict[str, list[str]] = {}
    for fid, feature in matrix.get("features", {}).items():
        for child in feature.get("child_surfaces", []) or []:
            expected_children.setdefault(child, []).append(fid)

    children = registry.get("child_surface_contracts", {})
    v.require(set(children) == set(expected_children), "child_surface_contracts must exactly cover Feature Matrix child surfaces")
    for child, owners in expected_children.items():
        observed = children.get(child, {})
        owners = list(dict.fromkeys(owners))
        v.require(observed.get("owner_features", []) == owners, f"{child}: owner feature drift")
        v.require(observed.get("contract_refs", []) == [f"FC-{x}" for x in owners], f"{child}: contract refs drift")


def validate_routes(registry: dict[str, Any], matrix: dict[str, Any], v: Validation) -> None:
    routes = registry.get("route_contracts", {})
    expected = matrix.get("routes", {})
    v.require(set(routes) == set(expected), "route_contracts must exactly match Feature Matrix route inventory")
    for route, source in expected.items():
        observed = routes.get(route, {})
        v.require(observed.get("mapping_mode") == source.get("mapping_mode"), f"route {route}: mapping_mode drift")
        v.require(observed.get("owner_features", []) == source.get("owner_features", []), f"route {route}: owner feature drift")
        v.require(observed.get("contract_refs", []) == [f"FC-{x}" for x in source.get("owner_features", [])], f"route {route}: contract refs drift")
        v.require(observed.get("source") == source.get("source"), f"route {route}: source drift")
        validate_local_ref(observed.get("source"), v, f"route {route}:source")


def validate_page_sets(registry: dict[str, Any], v: Validation) -> None:
    feature_sets = registry.get("feature_contracts", {})
    page_sets = registry.get("page_contract_sets", {})
    expected_page_features = {
        fid for fid, feature in feature_sets.items()
        if feature.get("page_contract_keys")
    }
    v.require(
        set(page_sets) == expected_page_features,
        "page_contract_sets must exactly cover features that declare page_contract_keys",
    )
    for fid, feature in feature_sets.items():
        keys = feature.get("page_contract_keys", []) or []
        if not keys:
            continue
        page_set = page_sets.get(fid, {})
        refs = page_set.get("refs", {})
        v.require(
            set(keys) == set(refs),
            f"{fid}: page_contract_keys must equal its page_contract_sets.refs keys",
        )
        for name, ref in refs.items():
            validate_local_ref(ref, v, f"page_contract_sets.{fid}.{name}")


def validate_gates_and_tasks(registry: dict[str, Any], machine: dict[str, Any], v: Validation) -> None:
    gates = registry.get("gate_contracts", {})
    tasks = machine.get("tasks", {})
    bindings = registry.get("task_contract_bindings", {})
    v.require(set(bindings) == set(tasks), "task_contract_bindings must cover every Phase/Task Machine task exactly once")

    machine_gates = {
        task.get("gate_id") for task in tasks.values()
        if isinstance(task, dict) and task.get("gate_id")
    }
    v.require(machine_gates == set(gates), "gate_contracts must exactly cover every declared task gate_id")

    for task_id, task in tasks.items():
        binding = bindings.get(task_id, {})
        expected_feature_refs = [f"FC-{x}" for x in task.get("feature_refs", []) or []]
        observed_refs = binding.get("contract_refs", []) or []
        for ref in expected_feature_refs:
            v.require(ref in observed_refs, f"{task_id}: missing feature contract binding {ref}")
        gate_id = task.get("gate_id")
        if gate_id:
            v.require(gate_id in observed_refs, f"{task_id}: missing gate contract binding {gate_id}")
            v.require(gate_id in gates, f"{task_id}: unknown gate {gate_id}")
        elif not expected_feature_refs:
            v.require("STATE-EVIDENCE-PROVENANCE" in observed_refs, f"{task_id}: no feature/gate binding; provenance contract required")

    for gate_id, gate in gates.items():
        v.require(gate.get("exact_sha_required") is True, f"{gate_id}: exact_sha_required must be true")
        for ref in gate.get("contract_refs", []) or []:
            valid = ref in feature_sets_or_shared(registry)
            v.require(valid, f"{gate_id}: unknown contract ref {ref}")
        v.require(gate.get("acceptance_domains"), f"{gate_id}: acceptance_domains must not be empty")


def feature_sets_or_shared(registry: dict[str, Any]) -> set[str]:
    return set(registry.get("shared_contracts", {})) | {
        f"FC-{fid}" for fid in registry.get("feature_contracts", {})
    }


def validate_resolution(registry: dict[str, Any], v: Validation) -> None:
    resolution = registry.get("resolution", {})
    required_phrases = {
        "lookup_key": "feature_id + child_surface_id + contract_domain",
        "missing_rule": "MISSING means no authoritative source exists; do not substitute a generic contract.",
        "historical_rule": "HISTORICAL evidence cannot establish current-head PASS.",
    }
    for key, expected in required_phrases.items():
        v.require(resolution.get(key) == expected, f"resolution.{key} drift")
    precedence = resolution.get("precedence", [])
    v.require(precedence, "resolution.precedence must not be empty")
    v.require("Effective Authority Registry" in " ".join(precedence), "Effective Authority Registry must be in resolution precedence")


def write_report(path: Path, registry: dict[str, Any], v: Validation) -> None:
    lines = [
        "# Unified Contract Validation Report",
        "",
        f"- Registry: {REGISTRY_PATH.relative_to(ROOT)}",
        f"- Features: {len(registry.get('feature_contracts', {}))}",
        f"- Journeys: {len(registry.get('journey_contracts', {}))}",
        f"- Child surfaces: {len(registry.get('child_surface_contracts', {}))}",
        f"- Routes: {len(registry.get('route_contracts', {}))}",
        f"- Tasks: {len(registry.get('task_contract_bindings', {}))}",
        f"- Gates: {len(registry.get('gate_contracts', {}))}",
        "",
        f"## Structural result: {'FAIL' if v.errors else 'PASS'}",
        "",
    ]
    if v.errors:
        lines.extend(f"- ERROR: {x}" for x in v.errors)
    else:
        lines.append("- No structural errors.")
    lines += ["", f"## Open contract gaps: {len(v.gaps)}", ""]
    lines.extend(f"- OPEN_GAP: {x}" for x in v.gaps)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry", type=Path, default=REGISTRY_PATH)
    parser.add_argument("--report", type=Path, default=None)
    args = parser.parse_args()

    registry_path = args.registry.resolve()
    for required in (registry_path, MATRIX_PATH, STATE_PATH, MACHINE_PATH):
        if not required.exists():
            print(f"ERROR: required file missing: {required}", file=sys.stderr)
            return 2

    try:
        registry = load_yaml(registry_path)
        matrix = load_yaml(MATRIX_PATH)
        state = load_yaml(STATE_PATH)
        machine = load_yaml(MACHINE_PATH)
    except (OSError, ValueError, yaml.YAMLError) as exc:
        print(f"ERROR: source parse failed: {exc}", file=sys.stderr)
        return 2

    v = Validation()
    validate_project_alignment(registry, matrix, state, machine, v)
    validate_features(registry, matrix, v)
    validate_journeys_and_children(registry, matrix, v)
    validate_routes(registry, matrix, v)
    validate_page_sets(registry, v)
    validate_gates_and_tasks(registry, machine, v)
    validate_resolution(registry, v)

    if args.report:
        write_report(args.report.resolve(), registry, v)

    print(f"UNIFIED_CONTRACT_VALIDATION={'FAIL' if v.errors else 'PASS'}")
    print(f"feature_count={len(registry.get('feature_contracts', {}))}")
    print(f"journey_count={len(registry.get('journey_contracts', {}))}")
    print(f"child_surface_count={len(registry.get('child_surface_contracts', {}))}")
    print(f"route_count={len(registry.get('route_contracts', {}))}")
    print(f"task_count={len(registry.get('task_contract_bindings', {}))}")
    print(f"gate_count={len(registry.get('gate_contracts', {}))}")
    print(f"structural_errors={len(v.errors)}")
    print(f"open_gaps={len(v.gaps)}")
    if v.errors:
        print("\nSTRUCTURAL_ERRORS:", file=sys.stderr)
        for error in v.errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    if v.gaps:
        print("\nOPEN_GAPS:")
        for gap in v.gaps:
            print(f"- {gap}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
