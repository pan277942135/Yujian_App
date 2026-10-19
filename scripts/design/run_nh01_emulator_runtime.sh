#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$YUJIAN_RUNTIME_EVIDENCE/infra"
python3 scripts/design/capture_nh01_device_metadata.py --output "$YUJIAN_RUNTIME_EVIDENCE/infra/device_metadata.json" --insets-dump "$YUJIAN_RUNTIME_EVIDENCE/infra/window_insets_dump.txt"

export ADB="$(command -v adb)"
runtime_rc=0
bash scripts/run_android_runtime_gate.sh --gate normal-home-v1 --api-level 28 --build-sha "$YUJIAN_SOURCE_SHA" --app-apk build/runtime-apks/app-debug.apk --test-apk build/runtime-apks/app-debug-androidTest.apk --evidence-dir "$YUJIAN_RUNTIME_EVIDENCE" || runtime_rc=$?

screenshot="$YUJIAN_RUNTIME_EVIDENCE/normal-home-v1/04_normal_home_runtime_1080x1920.png"
if [[ -s "$screenshot" ]]; then
  apk_sha="$(sha256sum build/runtime-apks/app-debug.apk | awk '{print $1}')"
  python3 scripts/design/compare_nh01_android_runtime.py --screenshot "$screenshot" --output-dir "$YUJIAN_RUNTIME_EVIDENCE/normal-home-v1" --source-sha "$YUJIAN_SOURCE_SHA" --apk-sha256 "$apk_sha" --run-id "$GITHUB_RUN_ID" || echo "NH01_ANDROID_PIXEL_DIFF=REVIEW_REQUIRED_SCRIPT_FAILED"
fi

exit "$runtime_rc"
