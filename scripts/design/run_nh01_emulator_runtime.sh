#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$YUJIAN_RUNTIME_EVIDENCE/infra"
python3 scripts/design/capture_nh01_device_metadata.py --output "$YUJIAN_RUNTIME_EVIDENCE/infra/device_metadata.json" --insets-dump "$YUJIAN_RUNTIME_EVIDENCE/infra/window_insets_dump.txt"

export ADB="$(command -v adb)"
runtime_rc=0
bash scripts/run_android_runtime_gate.sh --gate gemini-home-v2 --api-level 28 --build-sha "$YUJIAN_SOURCE_SHA" --app-apk build/runtime-apks/app-debug.apk --test-apk build/runtime-apks/app-debug-androidTest.apk --evidence-dir "$YUJIAN_RUNTIME_EVIDENCE" || runtime_rc=$?

exit "$runtime_rc"
