#!/usr/bin/env python3
"""YuJian Design Manager utilities.

V1 validates and serves two design layers:
1. shared design system
2. page modules
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE_REGISTRY = ROOT / "design" / "registry" / "experience_registry_v1.json"
SHARED_REGISTRY = ROOT / "design" / "registry" / "shared_design_system_v1.json"
DESIGN_MODALITIES = ["behavior", "visual", "motion", "haptic", "sound", "assets"]
VALID_STATUSES = {
    "FROZEN", "ACTIVE_CLOSURE", "PARTIAL", "RUNTIME_ONLY",
    "DESIGN_ONLY", "MISSING", "DEPRECATED",
}
VERSION_STATUSES = VALID_STATUSES | {"CANDIDATE"}


def load_json(path: Path, label: str) -> dict:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read {label}: {exc}") from exc
    if not isinstance(data, dict):
        raise ValueError(f"{label} root must be an object")
    return data


def _check_path(errors: list[str], label: str, value: object) -> None:
    if value is None:
        return
    if not isinstance(value, str) or not value:
        errors.append(f"{label}: path must be null or a non-empty string")
        return
    if not (ROOT / value).exists():
        errors.append(f"{label}: path does not exist: {value}")


def validate_shared(data: dict) -> tuple[list[str], dict[str, dict]]:
    errors: list[str] = []
    items = data.get("items")
    if not isinstance(items, list):
        return ["shared.items must be a list"], {}

    seen: set[str] = set()
    by_id: dict[str, dict] = {}

    for index, item in enumerate(items):
        prefix = f"shared.items[{index}]"
        if not isinstance(item, dict):
            errors.append(f"{prefix}: must be an object")
            continue

        item_id = item.get("id")
        if not isinstance(item_id, str) or not item_id:
            errors.append(f"{prefix}.id: invalid")
            continue
        if item_id in seen:
            errors.append(f"duplicate shared system id: {item_id}")
        seen.add(item_id)
        by_id[item_id] = item

        if not isinstance(item.get("display_name"), str) or not item.get("display_name"):
            errors.append(f"{item_id}: display_name is required")
        if item.get("category") not in {"foundation", "component"}:
            errors.append(f"{item_id}: category must be foundation or component")
        if item.get("overall") not in VALID_STATUSES:
            errors.append(f"{item_id}: invalid overall status {item.get('overall')!r}")
        if not isinstance(item.get("current_version"), str) or not item.get("current_version"):
            errors.append(f"{item_id}: current_version is required")

        paths = item.get("authority_paths")
        if not isinstance(paths, list) or not paths:
            errors.append(f"{item_id}: authority_paths must be a non-empty list")
        else:
            for path in paths:
                _check_path(errors, f"{item_id}.authority_paths", path)

        _check_path(errors, f"{item_id}.preview", item.get("preview"))

        variants = item.get("variants")
        if not isinstance(variants, list) or not variants:
            errors.append(f"{item_id}: variants must be a non-empty list")
            continue
        variant_ids: set[str] = set()
        for variant in variants:
            if not isinstance(variant, dict):
                errors.append(f"{item_id}: each variant must be an object")
                continue
            variant_id = variant.get("id")
            if not isinstance(variant_id, str) or not variant_id:
                errors.append(f"{item_id}: variant id is invalid")
            elif variant_id in variant_ids:
                errors.append(f"{item_id}: duplicate variant {variant_id}")
            else:
                variant_ids.add(variant_id)

    return errors, by_id


def validate_pages(data: dict, shared_by_id: dict[str, dict]) -> list[str]:
    errors: list[str] = []

    manager = data.get("design_manager")
    if not isinstance(manager, dict):
        errors.append("design_manager metadata is missing")
    else:
        if manager.get("scope") != "design_only":
            errors.append("design_manager.scope must be design_only")
        if manager.get("modalities") != DESIGN_MODALITIES:
            errors.append("design_manager.modalities must match the design-only modality order")
        if manager.get("shared_registry") != "design/registry/shared_design_system_v1.json":
            errors.append("design_manager.shared_registry is invalid")
        if manager.get("structure") != ["shared_design_system", "page_modules"]:
            errors.append("design_manager.structure must declare the two-layer model")

    features = data.get("features")
    if not isinstance(features, list):
        return errors + ["features must be a list"]

    seen: set[str] = set()
    for index, feature in enumerate(features):
        prefix = f"features[{index}]"
        if not isinstance(feature, dict):
            errors.append(f"{prefix}: feature must be an object")
            continue

        feature_id = feature.get("id")
        if not isinstance(feature_id, str) or not feature_id:
            errors.append(f"{prefix}.id: invalid")
            continue
        if feature_id in seen:
            errors.append(f"duplicate feature id: {feature_id}")
        seen.add(feature_id)

        if not isinstance(feature.get("display_name"), str) or not feature.get("display_name"):
            errors.append(f"{feature_id}: display_name is required")
        if not isinstance(feature.get("owner_path"), str) or not feature.get("owner_path"):
            errors.append(f"{feature_id}: owner_path is required")
        if feature.get("design_overall") not in VALID_STATUSES:
            errors.append(f"{feature_id}: invalid design_overall")

        modalities = feature.get("modalities")
        if not isinstance(modalities, dict):
            errors.append(f"{feature_id}: modalities must be an object")
            continue

        for modality in DESIGN_MODALITIES:
            entry = modalities.get(modality)
            if not isinstance(entry, dict):
                errors.append(f"{feature_id}: missing design modality {modality}")
                continue
            status = entry.get("status")
            if status not in VALID_STATUSES:
                errors.append(f"{feature_id}/{modality}: invalid status {status!r}")
            authority = entry.get("authority")
            if status == "FROZEN" and authority is None:
                errors.append(f"{feature_id}/{modality}: FROZEN requires authority")
            _check_path(errors, f"{feature_id}/{modality}.authority", authority)
            _check_path(errors, f"{feature_id}/{modality}.contract", entry.get("contract"))

        versions = feature.get("design_versions")
        if not isinstance(versions, list) or not versions:
            errors.append(f"{feature_id}: design_versions must be a non-empty list")
        else:
            current_count = 0
            version_ids: set[str] = set()
            for version in versions:
                if not isinstance(version, dict):
                    errors.append(f"{feature_id}: each design version must be an object")
                    continue
                version_id = version.get("version")
                if not isinstance(version_id, str) or not version_id:
                    errors.append(f"{feature_id}: version id is invalid")
                elif version_id in version_ids:
                    errors.append(f"{feature_id}: duplicate design version {version_id}")
                else:
                    version_ids.add(version_id)
                if version.get("status") not in VERSION_STATUSES:
                    errors.append(f"{feature_id}/{version_id or '?'}: invalid version status")
                if version.get("current") is True:
                    current_count += 1
                _check_path(errors, f"{feature_id}/{version_id or '?'}.visual_authority", version.get("visual_authority"))
                _check_path(errors, f"{feature_id}/{version_id or '?'}.spec_authority", version.get("spec_authority"))
            if current_count != 1:
                errors.append(f"{feature_id}: exactly one design version must be current; found {current_count}")

        refs = feature.get("shared_system_refs")
        if not isinstance(refs, list):
            errors.append(f"{feature_id}: shared_system_refs must be a list")
            continue

        ref_ids: set[str] = set()
        for ref in refs:
            if not isinstance(ref, dict):
                errors.append(f"{feature_id}: shared reference must be an object")
                continue
            shared_id = ref.get("id")
            if shared_id in ref_ids:
                errors.append(f"{feature_id}: duplicate shared reference {shared_id}")
                continue
            ref_ids.add(shared_id)
            shared_item = shared_by_id.get(shared_id)
            if shared_item is None:
                errors.append(f"{feature_id}: unknown shared system {shared_id}")
                continue
            variant = ref.get("variant")
            if variant is not None:
                allowed = {v.get("id") for v in shared_item.get("variants", [])}
                if variant not in allowed:
                    errors.append(f"{feature_id}: unknown variant {shared_id}/{variant}")

    return errors


def print_table(header: list[str], rows: list[list[str]]) -> None:
    widths = [len(value) for value in header]
    for row in rows:
        for i, value in enumerate(row):
            widths[i] = max(widths[i], len(str(value)))
    def fmt(row: list[str]) -> str:
        return "  ".join(str(value).ljust(widths[i]) for i, value in enumerate(row))
    print(fmt(header))
    print(fmt(["-" * width for width in widths]))
    for row in rows:
        print(fmt(row))


def print_summary(page_data: dict, shared_data: dict) -> None:
    print("SHARED DESIGN SYSTEM")
    print_table(
        ["SYSTEM", "TYPE", "STATUS", "VERSION"],
        [[x["display_name"], x["category"], x["overall"], x["current_version"]]
         for x in shared_data["items"]],
    )
    print()
    print("PAGE MODULES")
    header = ["MODULE", "OVERALL"] + [m.upper() for m in DESIGN_MODALITIES]
    rows = [
        [feature["display_name"], feature["design_overall"],
         *[feature["modalities"][m]["status"] for m in DESIGN_MODALITIES]]
        for feature in page_data["features"]
    ]
    print_table(header, rows)


def serve(host: str, port: int) -> int:
    os.chdir(ROOT)
    server = ThreadingHTTPServer((host, port), SimpleHTTPRequestHandler)
    print(f"YuJian Design Manager: http://{host}:{port}/design/manager/")
    print("Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="YuJian Design Manager")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("validate")
    sub.add_parser("summary")
    serve_parser = sub.add_parser("serve")
    serve_parser.add_argument("--host", default="127.0.0.1")
    serve_parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    try:
        page_data = load_json(PAGE_REGISTRY, "page registry")
        shared_data = load_json(SHARED_REGISTRY, "shared design registry")
    except ValueError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1

    shared_errors, shared_by_id = validate_shared(shared_data)
    page_errors = validate_pages(page_data, shared_by_id)
    errors = shared_errors + page_errors

    if errors:
        for error in errors:
            print(f"ERROR {error}", file=sys.stderr)
        print(f"FAIL: {len(errors)} design-registry issue(s)", file=sys.stderr)
        return 1

    if args.command == "validate":
        print(
            f"PASS: {len(shared_data['items'])} shared systems + "
            f"{len(page_data['features'])} page modules valid"
        )
        return 0
    if args.command == "summary":
        print_summary(page_data, shared_data)
        return 0
    return serve(args.host, args.port)


if __name__ == "__main__":
    raise SystemExit(main())
