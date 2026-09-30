#!/usr/bin/env python3
"""YuJian Design Manager utilities.

Zero-dependency commands:
- validate: validate design registry integrity
- summary: print design-only module matrix
- serve: serve the repository so design/manager can load registry/assets
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "design" / "registry" / "experience_registry_v1.json"
DESIGN_MODALITIES = ["behavior", "visual", "motion", "haptic", "sound", "assets"]
VALID_STATUSES = {
    "FROZEN",
    "ACTIVE_CLOSURE",
    "PARTIAL",
    "RUNTIME_ONLY",
    "DESIGN_ONLY",
    "MISSING",
    "DEPRECATED",
}
VERSION_STATUSES = VALID_STATUSES | {"CANDIDATE"}


def load_registry() -> dict:
    try:
        data = json.loads(REGISTRY.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read registry: {exc}") from exc
    if not isinstance(data, dict):
        raise ValueError("registry root must be an object")
    return data


def _check_path(errors: list[str], label: str, value: object) -> None:
    if value is None:
        return
    if not isinstance(value, str) or not value:
        errors.append(f"{label}: path must be null or a non-empty string")
        return
    if not (ROOT / value).exists():
        errors.append(f"{label}: path does not exist: {value}")


def validate(data: dict) -> list[str]:
    errors: list[str] = []

    manager = data.get("design_manager")
    if not isinstance(manager, dict):
        errors.append("design_manager metadata is missing")
    else:
        if manager.get("scope") != "design_only":
            errors.append("design_manager.scope must be design_only")
        if manager.get("modalities") != DESIGN_MODALITIES:
            errors.append("design_manager.modalities must match the design-only modality order")

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

        design_overall = feature.get("design_overall")
        if design_overall not in VALID_STATUSES:
            errors.append(f"{feature_id}: invalid design_overall {design_overall!r}")

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
            continue

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
                errors.append(
                    f"{feature_id}/{version_id or '?'}: invalid version status {version.get('status')!r}"
                )
            if version.get("current") is True:
                current_count += 1
            _check_path(
                errors,
                f"{feature_id}/{version_id or '?'}.visual_authority",
                version.get("visual_authority"),
            )
            _check_path(
                errors,
                f"{feature_id}/{version_id or '?'}.spec_authority",
                version.get("spec_authority"),
            )

        if current_count != 1:
            errors.append(f"{feature_id}: exactly one design version must be current; found {current_count}")

    return errors


def print_summary(data: dict) -> None:
    header = ["MODULE", "OVERALL"] + [m.upper() for m in DESIGN_MODALITIES]
    rows = []
    for feature in data["features"]:
        rows.append(
            [
                feature["display_name"],
                feature["design_overall"],
                *[feature["modalities"][m]["status"] for m in DESIGN_MODALITIES],
            ]
        )
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
        data = load_registry()
    except ValueError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1

    errors = validate(data)
    if errors:
        for error in errors:
            print(f"ERROR {error}", file=sys.stderr)
        print(f"FAIL: {len(errors)} design-registry issue(s)", file=sys.stderr)
        return 1

    if args.command == "validate":
        print(f"PASS: Design Manager registry valid; {len(data['features'])} modules")
        return 0
    if args.command == "summary":
        print_summary(data)
        return 0
    return serve(args.host, args.port)


if __name__ == "__main__":
    raise SystemExit(main())
