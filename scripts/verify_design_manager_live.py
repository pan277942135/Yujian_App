#!/usr/bin/env python3
"""Verify the deployed GitHub Pages build and original hi-fi asset bytes."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urljoin
from urllib.request import Request, urlopen

EXPECTED_GUIDE = {
    "P01": (941, 1671, "dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50"),
    "P02": (941, 1672, "1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3"),
}


class GateFailure(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GateFailure(message)


def fetch(url: str, *, expected_status: int = 200) -> tuple[bytes, int]:
    request = Request(url, headers={"User-Agent": "YuJian-Design-Manager-Release-Check/1.0", "Cache-Control": "no-cache"})
    try:
        with urlopen(request, timeout=30) as response:
            body = response.read()
            status = response.status
    except HTTPError as exc:
        if exc.code == expected_status:
            return exc.read(), exc.code
        raise GateFailure(f"HTTP {exc.code} for {url}; expected {expected_status}") from exc
    except URLError as exc:
        raise GateFailure(f"network error for {url}: {exc}") from exc
    require(status == expected_status, f"HTTP {status} for {url}; expected {expected_status}")
    return body, status


def png_dimensions(raw: bytes) -> tuple[int, int]:
    require(raw[:8] == b"\x89PNG\r\n\x1a\n" and len(raw) >= 24, "response is not a valid PNG")
    return struct.unpack(">II", raw[16:24])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True, help="Pages deployment root or Design Manager URL")
    parser.add_argument("--expected-build", required=True)
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args()

    base = args.url.rstrip("/") + "/"
    if base.rstrip("/").endswith("/design/manager"):
        manager_url = base
    else:
        manager_url = urljoin(base, "design/manager/")
    parent_url = urljoin(manager_url, "../../")
    result = {"gate": "PASS", "manager_url": manager_url, "expected_build": args.expected_build, "assets": [], "missing_paths": []}

    index_bytes, _ = fetch(manager_url)
    index = index_bytes.decode("utf-8")
    require(f'name="design-manager-build" content="{args.expected_build}"' in index,
            "Design Manager HTML build marker does not match the expected deploy SHA")
    build_raw, _ = fetch(urljoin(manager_url, "build.json"))
    build = json.loads(build_raw)
    require(build.get("sha") == args.expected_build, f"build.json SHA mismatch: {build.get('sha')}")
    result["build"] = build

    registry_raw, _ = fetch(urljoin(manager_url, "../registry/experience_registry_v1.json"))
    registry = json.loads(registry_raw)
    feature = next((item for item in registry.get("features", []) if item.get("id") == "recognition_flow_v1_2"), None)
    require(feature is not None, "recognition_flow_v1_2 is absent from the live Registry")
    views = {item.get("id"): item for item in feature.get("hifi_views", [])}
    guide = views.get("capture_first_use_guide")
    require(guide is not None and guide.get("title") == "07 · 首次拍照教学", "live guide entry changed")
    guide_children = {item.get("id"): item for item in guide.get("children", [])}
    require(set(guide_children) == {"p01", "p02"}, "live guide must expose exactly P01/P02")

    guide_manifest_raw, _ = fetch(urljoin(parent_url, "design/pages/recognition/first_use_camera_guide/manifest.json"))
    guide_manifest = json.loads(guide_manifest_raw)
    guide_assets = {asset.get("id", "").upper(): asset for asset in guide_manifest.get("assets", [])}
    for asset_id, (width, height, expected_sha) in EXPECTED_GUIDE.items():
        metadata = guide_assets.get(asset_id)
        require(metadata and metadata.get("sha256") == expected_sha, f"live guide manifest SHA changed: {asset_id}")
        path = metadata.get("path")
        raw, _ = fetch(urljoin(parent_url, quote(path, safe="/")))
        digest = hashlib.sha256(raw).hexdigest()
        dimensions = png_dimensions(raw)
        require(digest == expected_sha, f"live {asset_id} PNG SHA mismatch: {digest}")
        require(dimensions == (width, height), f"live {asset_id} dimensions mismatch: {dimensions}")
        require(guide_children[asset_id.lower()].get("visual_authority_sha256") == expected_sha,
                f"live Registry SHA mismatch: {asset_id}")
        result["assets"].append({"id": asset_id, "path": path, "http": 200, "bytes": len(raw), "sha256": digest, "dimensions": list(dimensions), "status": "FROZEN"})

    asset_dir = "design/system_v3/00_Product_Master_Flow/assets/"
    source_assets = [
        ("overview_png", asset_dir + "Recognition_Main_Journey_Overview_V1.png", "2cf0ea161360888248fb42227d2a2511df64aa94a4c4d3af7c7ffd3f06a0aeea"),
        ("overview_svg", asset_dir + "Recognition_Main_Journey_Overview_V1.svg", "fcb7a89215760b08901bdb94c2181dc5f40bc8ff0af0324b8ed0c8b705e0db6b"),
        ("new_states_png", asset_dir + "Recognition_New_States_HiFi_V1.png", "8cc7c04a4e15bf35ca0f307d0b95940d05901ead0cf6c66742a9f25d4d2e33e5"),
        ("new_states_svg", asset_dir + "Recognition_New_States_HiFi_V1.svg", "c47c2fa800d201d331c72c3aa6037404887d2567d7526445ecd6cded9ca1b9de"),
    ]
    for asset_id, path, expected_sha in source_assets:
        raw, _ = fetch(urljoin(parent_url, quote(path, safe="/")))
        digest = hashlib.sha256(raw).hexdigest()
        require(digest == expected_sha, f"live {asset_id} SHA mismatch: {digest}")
        dimensions = list(png_dimensions(raw)) if path.endswith(".png") else None
        result["assets"].append({"id": asset_id, "path": path, "http": 200, "bytes": len(raw), "sha256": digest, "dimensions": dimensions, "status": "FROZEN_BUSINESS_OVERVIEW" if asset_id.startswith("overview") else "PARTIAL_REVIEW"})

    shared_raw, _ = fetch(urljoin(manager_url, "../registry/shared_design_system_v1.json"))
    require(bool(json.loads(shared_raw)), "live Shared Registry is empty")

    missing_paths = [
        "design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Portrait_Horizontal_Fish_Frozen.png",
        "design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png",
        "湖畔相机权限提示界面.png",
        asset_dir + "Recognition_Main_Journey_Master_Board_V1.png",
        asset_dir + "Recognition_Main_Journey_Master_Board_V1.svg",
    ]
    for path in missing_paths:
        url = urljoin(parent_url, quote(path, safe="/"))
        fetch(url, expected_status=404)
        result["missing_paths"].append({"path": path, "http": 404, "status": "EXPECTED_MISSING"})

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: Pages live release | build={build['sha']} | P01/P02 + overview/review originals SHA-verified | expected missing paths=404")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except GateFailure as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
