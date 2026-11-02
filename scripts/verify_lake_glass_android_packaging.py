#!/usr/bin/env python3
"""Verify canonical Lake Glass runtime assets and the Android asset mirror."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "design/system/backgrounds/lake_glass_v2/assets/android_runtime"
APP_ASSETS = ROOT / "app/src/main/assets/lake_glass_v2"
SOURCE_MANIFEST = PACK / "layer_manifest_v1.json"
APP_MANIFEST = APP_ASSETS / "assets/android_runtime/layer_manifest_v1.json"
SHADER = APP_ASSETS / "shaders/lake_glass_v2.agsl"
NOTICE = ROOT / "third_party/NOTICE.md"
LICENSES = ROOT / "third_party/licenses"
EXPECTED_RUNTIME_PATHS = {
    "assets/android_runtime/api24_static/lake_glass_static_layered_v3.png",
    "assets/android_runtime/api24_static/lake_glass_master_fallback.png",
    "assets/android_runtime/runtime_optical_masks_v1.png",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fail(message: str) -> None:
    raise SystemExit(f"LAKE_GLASS_ANDROID_PACKAGING=FAIL: {message}")


def main() -> int:
    if not SOURCE_MANIFEST.is_file() or not APP_MANIFEST.is_file():
        fail("canonical source manifest or Android mirror is missing")
    if SOURCE_MANIFEST.read_bytes() != APP_MANIFEST.read_bytes():
        fail("Android manifest bytes do not match the single canonical asset manifest")
    manifest = json.loads(SOURCE_MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("visibleRuntimeStack") != ["LAYER_STATIC_COMPOSITE"]:
        fail("the static composite must be the only visible runtime surface")
    if manifest.get("master", {}).get("dimensionsPx") != [1440, 3120]:
        fail("unexpected Master dimensions")

    layers = {layer["layerId"]: layer for layer in manifest.get("layers", [])}
    required = {
        "LAYER_STATIC_COMPOSITE",
        "LAYER_MASTER_FALLBACK",
        "LAYER_CAUSTICS_MODULATION",
        "LAYER_SUNLIGHT_MODULATION",
        "LAYER_DROPLET_01_REFRACTION",
        "LAYER_DROPLET_02_REFRACTION",
        "LAYER_DROPLET_03_REFRACTION",
    }
    if not required.issubset(layers):
        fail(f"manifest is missing runtime protocol IDs: {sorted(required - layers.keys())}")

    records = []
    for relative in sorted(EXPECTED_RUNTIME_PATHS):
        source = PACK / relative.removeprefix("assets/android_runtime/")
        app_copy = APP_ASSETS / relative
        if not source.is_file() or not app_copy.is_file():
            fail(f"missing formal runtime asset: {relative}")
        if source.read_bytes() != app_copy.read_bytes():
            fail(f"Android copy differs from canonical resource: {relative}")
        expected = next(
            layer["assetSha256"]
            for layer in layers.values()
            if layer["assetFile"] == relative
        )
        actual = sha256(app_copy)
        if actual != expected:
            fail(f"manifest SHA mismatch for {relative}: {actual}")
        records.append({"assetFile": relative, "sha256": actual, "bytes": app_copy.stat().st_size})

    actual_pack_files = {
        path.relative_to(APP_ASSETS).as_posix()
        for path in APP_ASSETS.rglob("*")
        if path.is_file() and path != APP_MANIFEST and path != SHADER
    }
    expected_pack_files = EXPECTED_RUNTIME_PATHS
    if actual_pack_files != expected_pack_files:
        fail(
            "Android runtime bundle contains missing or diagnostic-only files: "
            f"extra={sorted(actual_pack_files - expected_pack_files)} "
            f"missing={sorted(expected_pack_files - actual_pack_files)}"
        )
    if not SHADER.is_file():
        fail("AGSL shader source is missing")
    shader_text = SHADER.read_text(encoding="utf-8")
    if re.search(r"^\s*(?:package|import)\s+", shader_text, flags=re.MULTILINE):
        fail("AGSL source must not contain a host-language package or import")
    if "GLSurfaceView" in shader_text or "EGL" in shader_text:
        fail("AGSL source contains a host/runtime implementation instead of shader code")
    if "inputImage.eval" not in shader_text or "opticalMasks.eval" not in shader_text:
        fail("shader must sample the composite input and canonical optical mask")
    for uniform in ("drop01Anchor", "drop02Anchor", "drop03Anchor", "sunlightAnchor"):
        if f"uniform float2 {uniform}" not in shader_text:
            fail(f"shader is missing a manifest-driven anchor uniform: {uniform}")
    if "float2 sunAnchor = float2(1361.0, 109.0)" in shader_text:
        fail("sunlight coordinates must come from the canonical manifest anchor")
    if "elapsedSeconds * 0.72" not in shader_text or "bakedHighlight" not in shader_text:
        fail("fixed droplets must include time-varying in-place refraction and baked-highlight modulation")
    if "inputImage.eval(sampleCoord)" not in shader_text:
        fail("droplet refraction must resample the same static composite input")

    if not NOTICE.is_file():
        fail("third-party attribution NOTICE is missing")
    notice_text = NOTICE.read_text(encoding="utf-8")
    expected_notices = {
        "ethanfox/water-caustics": "de856eec62819b7c4176366db73b8fd37a1b1455",
        "ScottieFox/caustic-volume": "d87351bff19831aa9d11c0679605fad6797b133f",
        "AndroidPoet/mirage": "9da9b00f678d57ce9989838ac513ffa88a11b038",
        "styropyr0/PrismalAGSL": "c853feb2e8b375fc8eb2dcce25a6d4f18250fb68",
        "frmlinn/raindrops-v2": "6e95911694bac6bafd1aadbc0e85fbd9a2e56ada",
    }
    for project, commit in expected_notices.items():
        if project not in notice_text or commit not in notice_text:
            fail(f"NOTICE does not pin attribution and source revision for {project}")
    license_files = sorted(LICENSES.glob("*.txt"))
    if len(license_files) != 5 or any(path.stat().st_size == 0 for path in license_files):
        fail("all five inspected open-source license texts must be retained")
    if list((ROOT / "app/src/main/assets/lake_glass_v2").rglob("*.js")):
        fail("a JavaScript/Three.js source bundle must not be packaged with Android assets")

    report = {
        "status": "PASS_STATIC_FIRST_ANDROID_BUNDLE",
        "manifestId": manifest["manifestId"],
        "manifestSha256": sha256(SOURCE_MANIFEST),
        "masterSourceSha256": manifest["master"]["sourceSha256"],
        "visibleRuntimeStack": manifest["visibleRuntimeStack"],
        "runtimeAssets": records,
        "shaderSha256": sha256(SHADER),
        "shaderBytes": SHADER.stat().st_size,
        "thirdPartyNoticeSha256": sha256(NOTICE),
        "thirdPartyLicenseFileCount": len(license_files),
        "diagnosticAssetsPackaged": False,
        "glSurfaceViewOrEgl": False,
    }
    output = ROOT / "build/lake-glass-android-packaging-report.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("LAKE_GLASS_ANDROID_PACKAGING=PASS")
    print(f"manifest_sha256={report['manifestSha256']}")
    print(f"shader_sha256={report['shaderSha256']}")
    print(f"runtime_asset_count={len(records)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
