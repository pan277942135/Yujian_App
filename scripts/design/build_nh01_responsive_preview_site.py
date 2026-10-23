#!/usr/bin/env python3
"""Add a branch-pinned NH01 acceptance workbench to an existing Pages tree.

The script writes only to previews/nh01-responsive-v2 in the provided site root.
It copies the interactive source workspace and the read-only assets the browser needs,
then verifies that production Design Manager entry files are byte-identical.
"""
from __future__ import annotations
import argparse, hashlib, json, re, shutil, struct
from pathlib import Path

PREVIEW_SOURCE = Path("previews/pr-176")
PREVIEW_HTML = Path("design/manager/nh01_acceptance/gemini-native-preview.html")
PREVIEW_HELPER = Path("design/manager/nh01_acceptance/nh01-responsive-contract-runtime.js")
ASSET_MANIFEST = Path("design/manager/nh01_acceptance/NH01_Gemini_Native_Asset_Binding_V1.json")
CONTRACT_PATH = Path("design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json")
ROUTE = Path("previews/nh01-responsive-v2")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_size(path: Path) -> tuple[int, int] | None:
    data = path.read_bytes()
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def copy_file(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--site-root", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--deployment-sha", required=True)
    parser.add_argument("--base-site-sha", required=True)
    parser.add_argument("--workflow-run-url", default="")
    args = parser.parse_args()
    source = args.source_root.resolve()
    site = args.site_root.resolve()
    for label, value in (("source", args.source_sha), ("deployment", args.deployment_sha), ("base site", args.base_site_sha)):
        if not re.fullmatch(r"[0-9a-f]{40}", value):
            raise SystemExit(f"{label} SHA must be a full 40-character commit SHA")
    if not (source / PREVIEW_SOURCE / PREVIEW_HTML).is_file():
        raise SystemExit("NH01 interactive workspace source is missing")
    contract_file = source / CONTRACT_PATH
    contract = json.loads(contract_file.read_text(encoding="utf-8"))
    if contract.get("contract_id") != "NH01_RESPONSIVE_LAYOUT_V2" or contract.get("contract_version") != "2.0":
        raise SystemExit("Unexpected responsive contract identity/version")
    if contract.get("status") not in {"PROPOSED_FOR_CANONICAL_MAIN_REVIEW", "SPEC_FROZEN"}:
        raise SystemExit("Responsive contract status is unsupported")
    if contract.get("native_acceptance", {}).get("status") != "NOT_VERIFIED":
        raise SystemExit("Android Runtime status must remain NOT_VERIFIED")
    if len(contract.get("visual_anchors", [])) != 6:
        raise SystemExit("Exactly six frozen references are required")
    if (site / ROUTE).exists():
        raise SystemExit(f"Refusing to overwrite an existing independent preview route: {ROUTE}")

    manager_paths = [Path("design/manager/index.html"), Path("design/manager/build.json"), Path("design/manager/nh01_acceptance/index.html")]
    manager_before = {path: digest(site / path) for path in manager_paths if (site / path).is_file()}
    if len(manager_before) != len(manager_paths):
        raise SystemExit("Production Design Manager baseline is incomplete")

    target = site / ROUTE
    target.mkdir(parents=True)
    # Package the existing Design Manager workbench's executable files only. The
    # source preview subtree may acquire unrelated or private assets over time.
    copy_file(source / PREVIEW_SOURCE / PREVIEW_HTML, target / PREVIEW_HTML)
    copy_file(source / PREVIEW_SOURCE / PREVIEW_HELPER, target / PREVIEW_HELPER)
    copied_html = target / PREVIEW_HTML
    try:
        html = copied_html.read_bytes().decode("utf-8")
    except UnicodeDecodeError as exc:
        raise SystemExit(f"NH01 preview HTML is not valid UTF-8; restore a verified text blob before publishing: {exc}") from exc
    if not html.lstrip("\ufeff \t\r\n").lower().startswith("<!doctype html>"):
        raise SystemExit("NH01 preview HTML is not a valid HTML document; refusing to publish")
    if "\ufffd" in html or "\x00" in html:
        raise SystemExit("NH01 preview HTML contains replacement or NUL characters; refusing to publish")
    if 'id="heroPlaceholder"' not in html or 'data-status="BLOCKED_ASSET_SOURCE"' not in html:
        raise SystemExit("NH01 preview must retain the explicit blocked Hero-photo placeholder")
    if not re.search(r'<article\b[^>]*id="leftCatchCard"[^>]*\shidden(?:\s|>)', html):
        raise SystemExit("NH01 preview must keep the unapproved left-neighbor card hidden")
    if not re.search(r'<article\b[^>]*id="rightCatchCard"[^>]*\shidden(?:\s|>)', html):
        raise SystemExit("NH01 preview must keep the unapproved right-neighbor card hidden")
    if "PREVIEW ONLY" not in html or "NOT VISUAL PASS" not in html:
        raise SystemExit("NH01 candidate workbench must visibly disclose PREVIEW ONLY / NOT VISUAL PASS")
    html = html.replace('name="nh01-preview-root" content="../../../../../"', 'name="nh01-preview-root" content="../../../"')
    html = html.replace("__NH01_GEMINI_PREVIEW_BUILD__", args.source_sha)
    html = html.replace("__NH01_PREVIEW_DEPLOYMENT_SHA__", args.deployment_sha)
    if "__NH01_GEMINI_PREVIEW_BUILD__" in html or "__NH01_PREVIEW_DEPLOYMENT_SHA__" in html:
        raise SystemExit("Preview SHA substitution was incomplete")
    copied_html.write_text(html, encoding="utf-8")

    # Include only assets explicitly registered for public preview plus the six immutable anchors.
    copied_assets = []
    for anchor in contract["visual_anchors"]:
        rel = Path(anchor["path"])
        original = source / rel
        if not original.is_file() or digest(original) != anchor["sha256"]:
            raise SystemExit(f"Frozen reference changed or missing: {rel}")
        if png_size(original) != (anchor["canvas_px"]["width"], anchor["canvas_px"]["height"]):
            raise SystemExit(f"Frozen reference canvas mismatch: {rel}")
        copy_file(original, target / rel)
        copied_assets.append({"path": rel.as_posix(), "sha256": anchor["sha256"], "canvas_px": anchor["canvas_px"]})

    asset_manifest_path = source / PREVIEW_SOURCE / ASSET_MANIFEST
    asset_manifest = json.loads(asset_manifest_path.read_text(encoding="utf-8"))
    asset_by_id = {item.get("id"): item for item in asset_manifest.get("assets", [])}
    for required_id in ("hero_photo", "neighbor_photos"):
        if required_id not in asset_by_id:
            raise SystemExit(f"Asset manifest is missing required privacy entry: {required_id}")
    if not asset_by_id["hero_photo"].get("public_preview") and "id=\"heroPlaceholder\"" not in html:
        raise SystemExit("A non-public Hero photo requires the visible blocked-photo placeholder")
    if not asset_by_id["neighbor_photos"].get("public_preview") and not (
        re.search(r'<article\b[^>]*id="leftCatchCard"[^>]*\shidden(?:\s|>)', html)
        and re.search(r'<article\b[^>]*id="rightCatchCard"[^>]*\shidden(?:\s|>)', html)
    ):
        raise SystemExit("Non-public neighboring fish photos must remain hidden")
    for asset in asset_manifest.get("assets", []):
        if not asset.get("public_preview"):
            continue
        rel = Path(asset["source_path"])
        original = source / rel
        if not original.is_file() or digest(original) != asset["sha256"]:
            raise SystemExit(f"Public preview asset changed or missing: {rel}")
        preview_path = asset.get("preview_path", "")
        if not preview_path or preview_path.startswith("/"):
            raise SystemExit(f"Public asset preview_path must be a relative URL: {asset.get('id')}")
        destination_rel = PREVIEW_HTML.parent / preview_path[2:] if preview_path.startswith("./") else Path(re.sub(r"^(?:\.\./)+", "", preview_path))
        if destination_rel.is_absolute() or ".." in destination_rel.parts:
            raise SystemExit(f"Public asset preview_path escapes the isolated route: {preview_path}")
        copy_file(original, target / destination_rel)
        copied_assets.append({"source_path": rel.as_posix(), "preview_path": preview_path, "sha256": asset["sha256"], "dimensions_px": asset["dimensions_px"]})

    # Redact source paths and hashes for assets that are not approved for public preview.
    public_manifest = json.loads(json.dumps(asset_manifest))
    for item in public_manifest.get("assets", []):
        if not item.get("public_preview"):
            for field in ("source_path", "preview_path", "sha256", "dimensions_px"):
                item[field] = None
    (target / ASSET_MANIFEST).write_text(
        json.dumps(public_manifest, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    copy_file(contract_file, target / CONTRACT_PATH)
    contract_sha = digest(target / CONTRACT_PATH)
    profile_path = Path(contract.get("authority", {}).get("viewport_profiles", ""))
    if profile_path.as_posix() != "design/system_v3/07_Responsive_System/Viewport_Profiles_V1.json":
        raise SystemExit("Responsive contract must point to the page-independent viewport profile registry")
    profiles_source = source / profile_path
    if not profiles_source.is_file():
        raise SystemExit("Responsive viewport profile registry is missing")
    copy_file(profiles_source, target / profile_path)
    profiles_sha = digest(target / profile_path)
    deployment = {
        "schema": "NH01_RESPONSIVE_PREVIEW_DEPLOYMENT_V1",
        "contract_id": contract["contract_id"],
        "contract_version": contract["contract_version"],
        "contract_sha256": contract_sha,
        "preview_source_sha": args.source_sha,
        "pages_deployment_sha": args.deployment_sha,
        "preserved_production_site_sha": args.base_site_sha,
        "contract_status": contract["status"],
        "release_mode": "FROZEN_ACCEPTANCE" if contract["status"] == "SPEC_FROZEN" else "PREVIEW_ONLY",
        "release_gate_effect": "G-NH01-RESPONSIVE-PREVIEW-V2_PASS_REQUIRED" if contract["status"] == "SPEC_FROZEN" else "NONE",
        "acceptance_status": "NOT_CLAIMED" if contract["status"] != "SPEC_FROZEN" else "NH01_RESPONSIVE_PREVIEW_V2_PASS_REQUIRED",
        "media_policy": {
            "hero": "PLACEHOLDER_ONLY" if not asset_by_id["hero_photo"].get("public_preview") else "APPROVED_SOURCE_ONLY",
            "neighbors": "HIDDEN_UNTIL_APPROVED_INDEPENDENT_ORIGINALS" if not asset_by_id["neighbor_photos"].get("public_preview") else "APPROVED_INDEPENDENT_ORIGINALS",
            "non_public_asset_files_copied": False,
        },
        "viewport_profiles_path": profile_path.as_posix(),
        "viewport_profiles_sha256": profiles_sha,
        "route": "/previews/nh01-responsive-v2/design/manager/nh01_acceptance/gemini-native-preview.html",
        "workflow_run_url": args.workflow_run_url,
        "android_runtime": "NOT_VERIFIED",
        "frozen_references": copied_assets,
    }
    (target / "NH01_RESPONSIVE_PREVIEW_DEPLOYMENT_V1.json").write_text(json.dumps(deployment, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    # A short route landing page points to the same existing workbench; no second workspace is built.
    landing = f'''<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="nh01-preview-source-sha" content="{args.source_sha}"><meta name="nh01-preview-deployment-sha" content="{args.deployment_sha}"><meta http-equiv="refresh" content="0; url=./design/manager/nh01_acceptance/gemini-native-preview.html"><title>NH01 Responsive Acceptance Preview</title></head>
<body><main><h1>NH01 响应式验收预览</h1><p>交互式预览源 SHA：<code>{args.source_sha}</code></p><p>Pages 部署 SHA：<code>{args.deployment_sha}</code></p><p>保留的生产 Design Manager 基线 SHA：<code>{args.base_site_sha}</code></p><p>合同：<code>{contract["contract_id"]} V{contract["contract_version"]}</code> · SHA-256 <code>{contract_sha}</code></p><p><a href="./design/manager/nh01_acceptance/gemini-native-preview.html">打开 NH01 交互预览</a></p><p><a href="../../design/manager/">返回生产 Design Manager</a></p></main></body></html>\n'''
    (target / "index.html").write_text(landing, encoding="utf-8")

    manager_after = {path: digest(site / path) for path in manager_paths}
    if manager_before != manager_after:
        raise SystemExit("Independent preview packaging modified production Design Manager bytes")
    print(json.dumps({"status": "PASS", "route": deployment["route"], "contract_id": contract["contract_id"], "contract_version": contract["contract_version"], "contract_sha256": contract_sha, "viewport_profiles_path": profile_path.as_posix(), "viewport_profiles_sha256": profiles_sha, "preview_source_sha": args.source_sha, "pages_deployment_sha": args.deployment_sha, "public_asset_count": len(copied_assets), "production_design_manager_unchanged": True}, indent=2))


if __name__ == "__main__":
    main()
