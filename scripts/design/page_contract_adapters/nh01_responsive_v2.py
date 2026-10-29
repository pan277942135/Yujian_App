"""Read-only binding adapter for the frozen NH01 Responsive Layout V2 contract."""

from __future__ import annotations

import subprocess
import sys
import json
from pathlib import Path


LEGACY_VALIDATOR = "scripts/design/validate_nh01_responsive_layout_contract_v2.py"
NH01_CONTRACT = "design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json"


def validate(envelope: dict, profile: dict, root: Path) -> dict:
    """Run the unchanged NH01 validator and retain runtime acceptance as unverified."""
    if envelope.get("page", {}).get("page_id") != "home_normal_v1":
        return {"status": "FAIL", "formal_acceptance": "NOT_VERIFIED", "errors": ["NH01 adapter page id mismatch"]}
    if profile.get("numeric_authority", {}).get("authority_ref_id") != "nh01-responsive-v2":
        return {"status": "FAIL", "formal_acceptance": "NOT_VERIFIED", "errors": ["NH01 V2 is not the profile's single numeric authority"]}

    contract = json.loads((root / NH01_CONTRACT).read_text(encoding="utf-8"))
    if contract.get("page_id") != "normal_home_v1":
        return {"status": "FAIL", "formal_acceptance": "NOT_VERIFIED", "errors": ["unexpected legacy NH01 page id"]}

    result = subprocess.run(
        [sys.executable, LEGACY_VALIDATOR],
        cwd=root,
        capture_output=True,
        text=True,
        check=False,
    )
    return {
        "status": "PASS" if result.returncode == 0 else "FAIL",
        "formal_acceptance": "NOT_VERIFIED",
        "legacy_validator": LEGACY_VALIDATOR,
        "legacy_contract": NH01_CONTRACT,
        "exit_code": result.returncode,
        "stdout": result.stdout.strip(),
        "stderr": result.stderr.strip(),
    }
