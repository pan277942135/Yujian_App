#!/usr/bin/env python3
"""Resolve the NH01 isolated preview release mode without weakening P3-06."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


class GateError(ValueError):
    pass


EXPECTED_CONTRACT_ID = "NH01_RESPONSIVE_LAYOUT_V2"
EXPECTED_CONTRACT_VERSION = "2.0"
PROPOSED = "PROPOSED_FOR_CANONICAL_MAIN_REVIEW"
FROZEN = "SPEC_FROZEN"
EXPECTED_BLOCKERS = {
    "hero_photo": "BLOCKED_ASSET_SOURCE",
    "neighbor_photos": "BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GateError(message)


def resolve_release_mode(
    report: dict[str, Any],
    contract: dict[str, Any],
    asset_manifest: dict[str, Any],
    preview_html: str,
    expected_source_sha: str,
) -> dict[str, Any]:
    require(bool(re.fullmatch(r"[0-9a-f]{40}", expected_source_sha)), "Expected source SHA must be a full Git commit SHA.")
    require(report.get("status") == "PASS", "Browser structure/layout verification did not PASS.")
    checks = report.get("checks") or {}
    contract_check = checks.get("contract") or {}
    require(contract.get("contract_id") == EXPECTED_CONTRACT_ID, "Unexpected responsive contract ID.")
    require(str(contract.get("contract_version")) == EXPECTED_CONTRACT_VERSION, "Unexpected responsive contract version.")
    require(contract_check.get("id") == EXPECTED_CONTRACT_ID, "Browser report contract ID mismatch.")
    require(str(contract_check.get("version")) == EXPECTED_CONTRACT_VERSION, "Browser report contract version mismatch.")
    require(contract_check.get("source_sha") == expected_source_sha, "Browser report source SHA does not match this exact commit.")
    require(contract_check.get("deployment_sha") == expected_source_sha, "Browser report deployment SHA does not match this exact commit.")
    require(contract_check.get("status") == contract.get("status"), "Browser report and machine contract status differ.")
    require(contract.get("status") in {PROPOSED, FROZEN}, "Unsupported responsive contract status.")

    require(preview_html.lstrip("\ufeff \t\r\n").lower().startswith("<!doctype html>"), "Preview HTML is not a complete HTML document.")
    require("\ufffd" not in preview_html and "\x00" not in preview_html, "Preview HTML contains replacement or NUL characters.")
    require("PREVIEW ONLY" in preview_html and "NOT VISUAL PASS" in preview_html, "Candidate preview must be visibly labeled PREVIEW ONLY / NOT VISUAL PASS.")

    assets = {item.get("id"): item for item in asset_manifest.get("assets", [])}
    require("hero_photo" in assets and "neighbor_photos" in assets, "Asset manifest must explicitly register Hero and neighbor photo policy.")
    for asset in asset_manifest.get("assets", []):
        if not asset.get("public_preview") and asset.get("source_path"):
            require(asset["source_path"] not in preview_html, f"Non-public asset path leaked into preview HTML: {asset['id']}")

    registered = contract_check.get("registered_assets") or []
    public_ids = {asset_id for asset_id, asset in assets.items() if asset.get("public_preview") is True}
    for image in registered:
        asset_id = image.get("asset")
        require(asset_id in public_ids, f"Rendered image is not on the public-preview allowlist: {asset_id}")
        src = str(image.get("src") or "")
        for asset_id, asset in assets.items():
            if not asset.get("public_preview") and asset.get("source_path"):
                require(asset["source_path"] not in src, f"Rendered image references non-public asset: {asset_id}")

    p3_status = report.get("p3_06_status")
    p3_check = checks.get("p3_06") or {}
    blockers = p3_check.get("media_blockers") or report.get("p3_06_blockers") or []
    observed = {item.get("id"): item.get("status") for item in blockers}
    require(len(observed) == len(blockers), "P3-06 blockers contain duplicate or malformed asset entries.")
    structure = contract_check.get("visible_structure") or {}
    hero_blocked = observed.get("hero_photo") == EXPECTED_BLOCKERS["hero_photo"]
    neighbors_blocked = observed.get("neighbor_photos") == EXPECTED_BLOCKERS["neighbor_photos"]

    if p3_status == "BLOCKED":
        require(contract.get("status") == PROPOSED, "A blocked P3-06 result cannot be released as a frozen acceptance build.")
        require(bool(blockers), "P3-06 is BLOCKED but no explicit media blockers were recorded.")
        require(all(item.get("id") in EXPECTED_BLOCKERS and EXPECTED_BLOCKERS[item.get("id")] == item.get("status") for item in blockers), "P3-06 has an unexpected blocker; fail closed.")
        require(hero_blocked or neighbors_blocked, "P3-06 blocker list does not identify the missing media.")
        require(bool(structure.get("heroPlaceholder")) == hero_blocked, "Hero placeholder does not match the Hero asset authorization state.")
        require(bool(structure.get("heroPhotoRendered")) == (not hero_blocked), "Hero rendered-photo state does not match the authorization state.")
        require(bool(structure.get("hiddenNeighbors")) == neighbors_blocked, "Neighbor visibility does not match the independent-photo authorization state.")
        require(structure.get("visibleNeighborCount", 0) == 0 if neighbors_blocked else structure.get("visibleNeighborCount") == 2, "Neighbor card count does not match the authorization state.")
        require(structure.get("neighborPhotoCount", 0) == 0 if neighbors_blocked else structure.get("neighborPhotoCount") == 2, "Neighbor photo count does not match the authorization state.")
        require('id="heroPlaceholder"' in preview_html if hero_blocked else 'id="heroPlaceholder"' not in preview_html, "Hero placeholder markup does not match the asset gate.")
        require('id="leftCatchCard"' in preview_html and 'id="rightCatchCard"' in preview_html, "Expected both neighbor slots in the preview structure.")
        if neighbors_blocked:
            require(bool(re.search(r'<article\b[^>]*id="leftCatchCard"[^>]*\shidden(?:\s|>)', preview_html)), "Left neighbor must remain hidden.")
            require(bool(re.search(r'<article\b[^>]*id="rightCatchCard"[^>]*\shidden(?:\s|>)', preview_html)), "Right neighbor must remain hidden.")
        mode = "PREVIEW_ONLY"
        gate_effect = "NONE"
        acceptance_status = "NOT_CLAIMED"
    elif p3_status == "PASS":
        require(not blockers, "P3-06 says PASS while blockers remain.")
        require(not hero_blocked and not neighbors_blocked, "P3-06 says PASS while media remains blocked.")
        require(not structure.get("heroPlaceholder") and structure.get("heroPhotoRendered") is True, "P3-06 cannot PASS until an approved Hero photo is actually rendered.")
        require(structure.get("visibleNeighborCount") == 2 and structure.get("neighborPhotoCount") == 2 and not structure.get("hiddenNeighbors"), "P3-06 cannot PASS until both independent neighbor photos are actually rendered.")
        require(assets["hero_photo"].get("public_preview") is True and assets["neighbor_photos"].get("public_preview") is True, "P3-06 cannot PASS while Hero/neighbor assets are not explicitly public-approved.")
        if contract.get("status") == FROZEN:
            mode = "FROZEN_ACCEPTANCE"
            gate_effect = "P3_06_PASS_REQUIRED"
            acceptance_status = "P3_06_PASS"
        else:
            mode = "PREVIEW_ONLY"
            gate_effect = "NONE"
            acceptance_status = "NOT_CLAIMED"
    else:
        raise GateError(f"Unsupported P3-06 status: {p3_status!r}")

    return {
        "publish": True,
        "preview_mode": mode,
        "contract_status": contract["status"],
        "p3_06_status": p3_status,
        "release_gate_effect": gate_effect,
        "acceptance_status": acceptance_status,
        "reason": "Candidate preview is independently publishable; blocked media remains explicitly unavailable and P3-06 is not claimed." if mode == "PREVIEW_ONLY" else "Frozen acceptance release requires and has P3-06 PASS.",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", required=True)
    parser.add_argument("--contract", required=True)
    parser.add_argument("--asset-manifest", required=True)
    parser.add_argument("--preview-html", required=True)
    parser.add_argument("--expected-source-sha", required=True)
    parser.add_argument("--github-output")
    args = parser.parse_args()

    try:
        report = json.loads(Path(args.report).read_text(encoding="utf-8"))
        contract = json.loads(Path(args.contract).read_text(encoding="utf-8"))
        asset_manifest = json.loads(Path(args.asset_manifest).read_text(encoding="utf-8"))
        preview_html = Path(args.preview_html).read_bytes().decode("utf-8")
        result = resolve_release_mode(report, contract, asset_manifest, preview_html, args.expected_source_sha)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError, GateError) as exc:
        raise SystemExit(f"NH01_PREVIEW_RELEASE_GATE=FAIL: {exc}") from exc

    print(json.dumps(result, indent=2, ensure_ascii=False))
    print("NH01_PREVIEW_RELEASE_GATE=PASS")
    print(f"PREVIEW_MODE={result['preview_mode']}")
    if args.github_output:
        with Path(args.github_output).open("a", encoding="utf-8") as output:
            output.write(f"publish={str(result['publish']).lower()}\n")
            output.write(f"preview_mode={result['preview_mode']}\n")


if __name__ == "__main__":
    main()
