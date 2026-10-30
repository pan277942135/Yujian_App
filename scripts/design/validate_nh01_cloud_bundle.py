#!/usr/bin/env python3
"""Schema and source-integrity validation for the NH01 cloud handoff bundle."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "design/manager/nh01_acceptance/data"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def validate(instance, schema, label: str) -> None:
    validator = Draft202012Validator(schema)
    errors = sorted(validator.iter_errors(instance), key=lambda e: list(e.absolute_path))
    if errors:
        details = "\n".join(f"{label}: /{'/'.join(map(str, e.absolute_path))}: {e.message}" for e in errors)
        raise SystemExit(details)


def main() -> int:
    document_schema = load(DATA / "NH01_Document_Schema_V1.json")
    mapping_schema = load(DATA / "source/M1_NH01_FIGMA_NODE_MAPPING_V2.schema.json")
    source_map = DATA / "source/M1_NH01_FIGMA_NODE_MAPPING_V2.json"
    validate(load(source_map), mapping_schema, source_map.name)
    data_files = sorted(p for p in DATA.glob("NH01_*.json") if not p.name.endswith("Schema_V1.json"))
    if len(data_files) < 9:
        raise SystemExit(f"Expected at least 9 NH01 data documents; found {len(data_files)}")
    for path in data_files:
        validate(load(path), document_schema, path.name)

    expected_mapping = "0a9880d1250969d85104641686efe1b1f85c965b8d9b2f7954d387d3b72c0b6a"
    if sha(source_map) != expected_mapping:
        raise SystemExit(f"PR #164 mapping snapshot hash mismatch: {sha(source_map)}")

    manifest = load(DATA / "NH01_Source_Manifest_V1.json")
    for asset in [
        ("design/system/core_visual_v1/reference/normal_home_v1.png", "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377", (1080, 1920)),
        ("design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png", "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7", (941, 1672)),
        ("design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png", "2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc", None),
        ("design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png", "fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2", (208, 208)),
    ]:
        path = ROOT / asset[0]
        if not path.is_file() or sha(path) != asset[1]:
            raise SystemExit(f"Frozen/source asset hash mismatch: {asset[0]}")
        if asset[2]:
            with Image.open(path) as im:
                if im.size != asset[2]:
                    raise SystemExit(f"Unexpected dimensions for {asset[0]}: {im.size}")
    if manifest["frozen_authority"]["sha256"] != manifest["frozen_authority"]["expected_sha256"]:
        raise SystemExit("Frozen authority source hash does not match expected SHA-256")

    evidence_path = DATA / "NH01_Evidence_Provenance_V1.json"
    evidence = load(evidence_path)
    evidence["gate_status"]["G1_STRUCTURE_VALID"] = "PASS"
    evidence_path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    validate(evidence, document_schema, evidence_path.name)

    print(f"NH01_SCHEMA_VALIDATION=PASS documents={len(data_files)} mapping=PASS frozen_assets=4/4")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ImportError as exc:
        print(f"MISSING_VALIDATOR_DEPENDENCY: {exc}", file=sys.stderr)
        raise SystemExit(2)
