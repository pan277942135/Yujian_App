#!/usr/bin/env bash

# Compatibility entry point for callers that still use the historical script.
# All ADB readiness, installation, instrumentation, diagnostics and result
# classification now live in the unified runtime Harness.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
TEST_APK="${2:-app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}"
EVIDENCE_DIR="${3:-evidence/runtime/recognition-frozen}"

ARGS=(
  --gate recognition-frozen \
  --app-apk "$APP_APK" \
  --test-apk "$TEST_APK" \
  --evidence-dir "$EVIDENCE_DIR"
)
if [[ -n "${4:-}" ]]; then ARGS+=(--api-level "$4"); fi
if [[ -n "${5:-}" ]]; then ARGS+=(--build-sha "$5"); fi

exec bash "$ROOT_DIR/scripts/run_android_runtime_gate.sh" "${ARGS[@]}"
