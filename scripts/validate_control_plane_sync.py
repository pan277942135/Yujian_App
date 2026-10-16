#!/usr/bin/env python3
"""Validate product-branch synchronization with the canonical main control plane."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

try:
    import yaml
except ImportError as exc:
    print("ERROR: PyYAML is required: python -m pip install PyYAML", file=sys.stderr)
    raise SystemExit(2) from exc

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "CONTROL_PLANE_REGISTRY.yaml"
DEFAULT_BASE = "origin/main"


def git(*args: str) -> str:
    p = subprocess.run(["git", *args], cwd=ROOT, text=True, capture_output=True)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip() or "git command failed")
    return p.stdout


def exists_at(ref: str, path: str) -> bool:
    p = subprocess.run(["git", "cat-file", "-e", f"{ref}:{path}"], cwd=ROOT, text=True, capture_output=True)
    return p.returncode == 0


def blob_sha_at(ref: str, path: str) -> str | None:
    p = subprocess.run(["git", "rev-parse", f"{ref}:{path}"], cwd=ROOT, text=True, capture_output=True)
    return p.stdout.strip() if p.returncode == 0 else None


def load_registry_from(ref: str) -> dict:
    raw = git("show", f"{ref}:CONTROL_PLANE_REGISTRY.yaml")
    value = yaml.safe_load(raw)
    if not isinstance(value, dict):
        raise ValueError(f"{ref}:CONTROL_PLANE_REGISTRY.yaml must be a mapping")
    return value


def flatten_files(ref: str, paths: list[str]) -> list[str]:
    out: list[str] = []
    for path in paths:
        if path.endswith("/"):
            listing = git("ls-tree", "-r", "--name-only", ref, "--", path)
            out.extend(x.strip() for x in listing.splitlines() if x.strip())
        else:
            out.append(path)
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=DEFAULT_BASE)
    args = parser.parse_args()

    if not REGISTRY.exists():
        print("CONTROL_PLANE_SYNC=FAIL")
        print("ERROR: local CONTROL_PLANE_REGISTRY.yaml is missing")
        return 1

    try:
        canonical = load_registry_from(args.base)
        local_raw = REGISTRY.read_text(encoding="utf-8")
        canonical_raw = git("show", f"{args.base}:CONTROL_PLANE_REGISTRY.yaml")
    except Exception as exc:
        print("CONTROL_PLANE_SYNC=BLOCKED")
        print(f"ERROR: cannot resolve canonical registry: {exc}")
        return 2

    errors: list[str] = []
    if local_raw != canonical_raw:
        errors.append("CONTROL_PLANE_REGISTRY.yaml differs from canonical main")

    files = canonical.get("canonical_static_control_plane", {}).get("files", [])
    design_paths = canonical.get("canonical_design_authorities", {}).get("paths", [])
    compare_paths = flatten_files(args.base, [*files, *design_paths])

    branch_local = {
        "PROJECT_MASTER_STATE.yaml",
        "docs/qa/recognition_main_journey/WORK_EXECUTION_STATE.json",
    }
    for path in sorted(set(compare_paths)):
        if path in branch_local:
            continue
        if not exists_at(args.base, path):
            errors.append(f"canonical main file missing: {path}")
            continue
        if not (ROOT / path).exists():
            errors.append(f"branch missing canonical file: {path}")
            continue
        local_sha = blob_sha_at("HEAD", path)
        main_sha = blob_sha_at(args.base, path)
        if local_sha != main_sha:
            errors.append(f"stale/diverged canonical file: {path}")

    print(f"CONTROL_PLANE_SYNC={'FAIL' if errors else 'PASS'}")
    print(f"canonical_registry={args.base}:CONTROL_PLANE_REGISTRY.yaml")
    print(f"compared_paths={len(set(compare_paths))}")
    print(f"errors={len(errors)}")
    for error in errors:
        print(f"- {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
