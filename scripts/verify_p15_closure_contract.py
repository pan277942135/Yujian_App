#!/usr/bin/env python3
"""Check P15's full-surface APK/provenance artifact wiring without claiming a build."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
BUNDLE_CHECK = (ROOT / "scripts/verify_full_surface_apk_bundle.py").read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


build_job = WORKFLOW.split("  android-runtime:", 1)[0]
runtime_job = WORKFLOW.split("  android-runtime:", 1)[1]
required_steps = (
    "name: Prepare YuJian Full-Surface APK bundle",
    "name: Upload Full-Surface Android APK and provenance",
    "name: Upload installable debug APK",
    "python3 scripts/verify_full_surface_apk_bundle.py",
    "sha256sum \"${filename}\" model_release_contract.json apk_model_trace.json > SHA256SUMS",
)
for value in required_steps:
    require(value in build_job, f"build job is missing P15 artifact step: {value}")

require('YuJian_Full_Surface_Android_${GITHUB_SHA:0:12}_${{ steps.model_trace.outputs.model_id }}.apk' in build_job,
        "full-surface APK filename must include source SHA and production model id")
require("name: YuJian-Full-Surface-${{ github.sha }}-${{ steps.model_trace.outputs.model_id }}" in build_job,
        "artifact identity must be tied to the exact source SHA and production model")
require("build/full-surface-apk/apk_model_trace.json" in build_job,
        "APK model trace must be included in the downloadable artifact")
require("path: build/full-surface-apk" in build_job and "> SHA256SUMS" in build_job,
        "artifact must include checksums for the APK and provenance files")
require("app/build/outputs/apk/debug/app-debug.apk" in build_job,
        "full-surface artifact must reuse the build-once production debug APK")
require("runs-on: [self-hosted, yujian-android, api28]" in runtime_job,
        "P15 must retain the authoritative API 28 runtime runner")
require('trace.get("build_sha")' in BUNDLE_CHECK,
        "bundle verifier must bind APK provenance to the exact source commit")
print("P15 APK/evidence closure contract static checks: PASS")
