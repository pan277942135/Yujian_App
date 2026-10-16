#!/usr/bin/env python3
"""Validate YuJian Feature Matrix V1 against live repository inventories.

The validator checks traceability integrity, not product completion. Open product
gaps are reported explicitly and do not fail the matrix unless they indicate
structural drift (missing feature, orphan route/journey, missing registry unit,
or unknown blocker/next-action reference).
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError as exc:
    print("ERROR: PyYAML is required: python -m pip install PyYAML", file=sys.stderr)
    raise SystemExit(2) from exc

ROOT = Path(__file__).resolve().parents[1]
MATRIX_PATH = ROOT / "harness/FEATURE_MATRIX.yaml"
REGISTRY_PATH = ROOT / "design/registry/experience_registry_v1.json"
STATE_PATH = ROOT / "PROJECT_MASTER_STATE.yaml"
APP_ROOT = ROOT / "app/src/main/java"

REQUIRED_FEATURE_KEYS = {
    "sequence", "name", "priority", "scope", "parent_feature", "control_phase",
    "owner_path", "registry_overall", "registry_design_overall",
    "registry_modalities", "design_units", "design_children", "child_surfaces",
    "journey_refs", "route_refs", "implementation", "acceptance", "blockers",
    "next_actions", "known_gaps",
}

REQUIRED_MODALITIES = {
    "behavior", "visual", "motion", "haptic", "sound", "runtime", "evidence", "assets"
}

JOURNEY_IDS = [f"J{i:02d}" for i in range(1, 21)]


class UniqueKeyLoader(yaml.SafeLoader):
    """PyYAML loader that rejects duplicate mapping keys."""


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


def load_yaml(path: Path) -> dict:
    raw = path.read_text(encoding="utf-8")
    if "\t" in raw:
        raise ValueError(f"tabs are not allowed in YAML indentation: {path}")
    value = yaml.load(raw, Loader=UniqueKeyLoader)
    if not isinstance(value, dict):
        raise ValueError(f"root YAML value must be a mapping: {path}")
    return value


def load_json(path: Path) -> dict:
    import json
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"root JSON value must be a mapping: {path}")
    return value


def registry_feature_data(registry: dict) -> tuple[dict[str, dict], dict[str, list[str]], dict[str, list[str]]]:
    features = {}
    design_units = {}
    design_children = {}

    def walk_children(items: list[dict], out: list[str]) -> None:
        for item in items or []:
            for child in item.get("children", []) or []:
                child_id = child.get("id")
                if child_id:
                    out.append(child_id)
                walk_children([child], out)

    for feature in registry.get("features", []):
        fid = feature.get("id")
        if not fid:
            continue
        features[fid] = feature
        design_units[fid] = [
            view.get("id")
            for view in feature.get("hifi_views", []) or []
            if view.get("id")
        ]
        children: list[str] = []
        walk_children(feature.get("hifi_views", []) or [], children)
        design_children[fid] = children

    return features, design_units, design_children


def extract_declared_routes() -> set[str]:
    """Extract literal routes plus routes referenced through *Route constants."""
    sources = list(APP_ROOT.rglob("*.kt"))
    constants: dict[str, str] = {}
    direct: set[str] = set()
    constant_usages: set[str] = set()

    const_re = re.compile(r'const\s+val\s+(\w+Route)\s*=\s*"([^"]+)"')
    direct_re = re.compile(r'(?:composable|dialog)\s*\(\s*"([^"]+)"')
    route_literal_arg_re = re.compile(
        r'(?:composable|dialog)\s*\(\s*route\s*=\s*"([^"]+)"'
    )
    route_arg_re = re.compile(
        r'(?:composable|dialog)\s*\(\s*route\s*=\s*(\w+Route)'
    )

    for path in sources:
        text = path.read_text(encoding="utf-8", errors="ignore")
        constants.update(dict(const_re.findall(text)))
        direct.update(direct_re.findall(text))
        direct.update(route_literal_arg_re.findall(text))
        constant_usages.update(route_arg_re.findall(text))

    resolved = {constants[name] for name in constant_usages if name in constants}
    return direct | resolved


def validate_structure(matrix: dict, registry: dict, state: dict, v: Validation) -> None:
    v.require(matrix.get("schema_version") == "1.0", "schema_version must be 1.0")
    v.require(matrix.get("matrix_id") == "yujian-feature-matrix", "matrix_id must be yujian-feature-matrix")

    registry_features, registry_units, registry_children = registry_feature_data(registry)
    features = matrix.get("features", {})
    v.require(isinstance(features, dict), "features must be a mapping")

    registry_ids = list(registry_features)
    matrix_ids = list(features) if isinstance(features, dict) else []

    v.require(
        matrix.get("inventory", {}).get("feature_count") == len(registry_ids),
        f"inventory.feature_count must equal registry feature count ({len(registry_ids)})",
    )
    v.require(
        len(matrix_ids) == len(set(matrix_ids)),
        "feature IDs must be unique",
    )
    v.require(
        set(matrix_ids) == set(registry_ids),
        f"feature inventory mismatch; expected exactly registry IDs: {registry_ids}",
    )

    order = matrix.get("feature_order", [])
    v.require(order == registry_ids, "feature_order must match experience_registry_v1.json order")

    blocker_ids = {
        item.get("id")
        for item in state.get("blockers", [])
        if isinstance(item, dict) and item.get("id")
    }
    action_ids = {
        item.get("id")
        for item in state.get("next_actions", [])
        if isinstance(item, dict) and item.get("id")
    }

    for fid, feature in features.items():
        missing = REQUIRED_FEATURE_KEYS - set(feature)
        v.require(not missing, f"{fid}: missing required keys: {sorted(missing)}")
        if missing:
            continue

        registry_feature = registry_features[fid]
        v.require(
            feature.get("owner_path") == registry_feature.get("owner_path"),
            f"{fid}: owner_path drift from Experience Registry",
        )
        v.require(
            feature.get("registry_overall") == registry_feature.get("overall"),
            f"{fid}: registry_overall drift from Experience Registry",
        )
        v.require(
            feature.get("registry_design_overall") == registry_feature.get("design_overall"),
            f"{fid}: registry_design_overall drift from Experience Registry",
        )

        modalities = feature.get("registry_modalities", {})
        v.require(
            set(modalities) == REQUIRED_MODALITIES,
            f"{fid}: registry_modalities must contain exactly {sorted(REQUIRED_MODALITIES)}",
        )
        for modality in REQUIRED_MODALITIES:
            expected = registry_feature.get("modalities", {}).get(modality, {})
            actual = modalities.get(modality, {})
            v.require(
                actual.get("status") == expected.get("status"),
                f"{fid}: {modality}.status drift from Experience Registry",
            )
            v.require(
                actual.get("authority") == expected.get("authority"),
                f"{fid}: {modality}.authority drift from Experience Registry",
            )

        v.require(
            feature.get("design_units", []) == registry_units[fid],
            f"{fid}: design_units do not exactly cover Experience Registry hifi_views",
        )
        v.require(
            feature.get("design_children", []) == registry_children[fid],
            f"{fid}: design_children do not exactly cover nested Experience Registry children",
        )

        parent = feature.get("parent_feature")
        if parent is not None:
            v.require(
                parent in matrix_ids,
                f"{fid}: parent_feature {parent!r} is not a registered feature",
            )

        for ref in feature.get("journey_refs", []):
            v.require(ref in JOURNEY_IDS, f"{fid}: unknown journey ref {ref}")
        for ref in feature.get("route_refs", []):
            v.require(ref in matrix.get("routes", {}), f"{fid}: route ref {ref!r} is not in route inventory")
        for bid in feature.get("blockers", []):
            v.require(bid in blocker_ids, f"{fid}: blocker {bid} is not in PROJECT_MASTER_STATE")
        for aid in feature.get("next_actions", []):
            v.require(aid in action_ids, f"{fid}: next_action {aid} is not in PROJECT_MASTER_STATE")

    journeys = matrix.get("journeys", {})
    v.require(set(journeys) == set(JOURNEY_IDS), "journey inventory must contain exactly J01-J20")
    state_route = state.get("recognition_main_journey", {}).get("route", [])
    state_journey_ids = [
        item.split(" ", 1)[0]
        for item in state_route
        if isinstance(item, str)
    ]
    v.require(
        state_journey_ids == JOURNEY_IDS,
        "PROJECT_MASTER_STATE recognition journey must contain J01-J20 in order",
    )

    for jid in JOURNEY_IDS:
        item = journeys.get(jid, {})
        refs = item.get("feature_refs", [])
        v.require(refs, f"{jid}: must map to at least one feature")
        for fid in refs:
            v.require(fid in matrix_ids, f"{jid}: unknown feature ref {fid}")
        expected_name = next(
            (
                x.split(" ", 1)[1]
                for x in state_route
                if isinstance(x, str) and x.startswith(jid + " ")
            ),
            None,
        )
        v.require(item.get("name") == expected_name, f"{jid}: name must match PROJECT_MASTER_STATE")

    routes = matrix.get("routes", {})
    actual_routes = extract_declared_routes()
    matrix_routes = set(routes)
    v.require(
        matrix.get("inventory", {}).get("route_count") == len(actual_routes),
        f"inventory.route_count must equal declared Android route count ({len(actual_routes)})",
    )
    v.require(
        actual_routes == matrix_routes,
        "route inventory mismatch with declared Android navigation routes: "
        f"missing_from_matrix={sorted(actual_routes - matrix_routes)} "
        f"extra_in_matrix={sorted(matrix_routes - actual_routes)}",
    )

    for route, item in routes.items():
        owners = item.get("owner_features", [])
        v.require(owners, f"route {route!r}: must have owner_features")
        for fid in owners:
            v.require(fid in matrix_ids, f"route {route!r}: unknown owner feature {fid}")
            if fid in features:
                v.require(
                    route in features[fid].get("route_refs", []),
                    f"route {route!r}: owner feature {fid} does not reference the route",
                )

    def has_cycle(start: str) -> bool:
        seen: set[str] = set()
        cur = start
        while cur:
            if cur in seen:
                return True
            seen.add(cur)
            cur = features.get(cur, {}).get("parent_feature")
        return False

    for fid in matrix_ids:
        v.require(not has_cycle(fid), f"{fid}: parent_feature cycle detected")

    for fid, feature in features.items():
        for modality, data in feature.get("registry_modalities", {}).items():
            status = data.get("status")
            if status not in {"FROZEN", "PASS", "DEFINED", "READY"}:
                v.gap(f"{fid}: modality {modality}={status}")
        if not feature.get("route_refs"):
            v.gap(f"{fid}: no currently declared Android navigation route")
        if not feature.get("design_units"):
            v.gap(f"{fid}: no hifi_views registered in Experience Registry")
        if feature.get("acceptance", {}).get("current_branch_runtime") != "PASS":
            v.gap(
                f"{fid}: current-branch runtime acceptance="
                f"{feature.get('acceptance', {}).get('current_branch_runtime')}"
            )
        for gap in feature.get("known_gaps", []) or []:
            v.gap(f"{fid}: {gap}")

    guide_paths = [
        "design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png",
        "design/pages/recognition/first_use_camera_guide/frozen/P02_First_Use_Camera_Guide_Frozen.png",
        "design/pages/recognition/first_use_camera_guide/frozen/P03_First_Use_Camera_Guide_Frozen.png",
    ]
    for rel in guide_paths:
        if not (ROOT / rel).exists():
            v.gap(f"missing registered frozen asset: {rel}")


def write_report(path: Path, matrix: dict, v: Validation) -> None:
    lines = [
        "# Feature Matrix V1 Validation Report",
        "",
        f"- Matrix: {MATRIX_PATH.relative_to(ROOT)}",
        f"- Feature count: {matrix.get('inventory', {}).get('feature_count')}",
        f"- Journey count: {matrix.get('inventory', {}).get('journey_count')}",
        f"- Route count: {matrix.get('inventory', {}).get('route_count')}",
        "",
        f"## Structural result: {'FAIL' if v.errors else 'PASS'}",
        "",
    ]
    if v.errors:
        lines += [f"- ERROR: {item}" for item in v.errors]
    else:
        lines.append("- No structural errors.")
    lines += ["", f"## Open gaps: {len(v.gaps)}", ""]
    if v.gaps:
        lines += [f"- {item}" for item in v.gaps]
    else:
        lines.append("- None.")
    lines += [
        "",
        "Open product gaps are non-fatal by policy; structural traceability errors are fatal.",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matrix", type=Path, default=MATRIX_PATH)
    parser.add_argument("--report", type=Path, default=None)
    args = parser.parse_args()

    matrix_path = args.matrix.resolve()
    if not matrix_path.exists():
        print(f"ERROR: matrix file not found: {matrix_path}", file=sys.stderr)
        return 2
    for required in (REGISTRY_PATH, STATE_PATH):
        if not required.exists():
            print(f"ERROR: required source file missing: {required}", file=sys.stderr)
            return 2

    try:
        matrix = load_yaml(matrix_path)
        registry = load_json(REGISTRY_PATH)
        state = load_yaml(STATE_PATH)
    except (OSError, ValueError, yaml.YAMLError) as exc:
        print(f"ERROR: source parse failed: {exc}", file=sys.stderr)
        return 2

    v = Validation()
    validate_structure(matrix, registry, state, v)

    if args.report:
        write_report(args.report.resolve(), matrix, v)

    print(f"FEATURE_MATRIX_VALIDATION={'FAIL' if v.errors else 'PASS'}")
    print(f"feature_count={matrix.get('inventory', {}).get('feature_count')}")
    print(f"journey_count={matrix.get('inventory', {}).get('journey_count')}")
    print(f"route_count={matrix.get('inventory', {}).get('route_count')}")
    print(f"structural_errors={len(v.errors)}")
    print(f"open_gaps={len(v.gaps)}")

    if v.gaps:
        print("\nOPEN_GAPS:")
        for gap in v.gaps:
            print(f"- {gap}")

    if v.errors:
        print("\nSTRUCTURAL_ERRORS:", file=sys.stderr)
        for error in v.errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
