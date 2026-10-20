#!/usr/bin/env bash
set -uo pipefail

: "${YUJIAN_ADB_BIN:?YUJIAN_ADB_BIN is required}"
: "${YUJIAN_APP_PACKAGE:?YUJIAN_APP_PACKAGE is required}"
: "${YUJIAN_INSTRUMENTATION_TARGET:?YUJIAN_INSTRUMENTATION_TARGET is required}"
: "${YUJIAN_EVIDENCE_DIR:?YUJIAN_EVIDENCE_DIR is required}"
: "${BUILD_SHA:?BUILD_SHA is required}"

readonly TEST_CLASS="com.yujian.ai.ui.home.GeminiHomeDeviceMatrixTest#capturesActualWindowWithTwoUniqueCatches"
readonly RAW_DIR="$YUJIAN_EVIDENCE_DIR/nh01-adaptive-raw"
readonly MATRIX_DIR="$YUJIAN_EVIDENCE_DIR/NH01_RUNTIME_MATRIX_V1"
readonly REMOTE_DIR="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/nh01-adaptive-visual-balance-v1"
rm -rf "$RAW_DIR" "$MATRIX_DIR"
mkdir -p "$RAW_DIR" "$MATRIX_DIR/logs"

selected_value() {
  local output="$1" label="$2"
  local value
  value="$(sed -n "s/^${label}: //p" <<< "$output" | tail -n 1 | tr -d '\r')"
  if [[ -z "$value" ]]; then
    value="$(sed -n 's/^Physical size: //p; s/^Physical density: //p' <<< "$output" | head -n 1 | tr -d '\r')"
  fi
  printf '%s' "$value"
}

initial_size_output="$("$YUJIAN_ADB_BIN" shell wm size 2>&1)"
initial_density_output="$("$YUJIAN_ADB_BIN" shell wm density 2>&1)"
initial_size="$(selected_value "$initial_size_output" "Override size")"
initial_density="$(selected_value "$initial_density_output" "Override density")"
initial_font="$("$YUJIAN_ADB_BIN" shell settings get system font_scale | tr -d '\r')"

restore_device() {
  "$YUJIAN_ADB_BIN" shell wm size "$initial_size" >/dev/null 2>&1 || true
  "$YUJIAN_ADB_BIN" shell wm density "$initial_density" >/dev/null 2>&1 || true
  "$YUJIAN_ADB_BIN" shell settings put system font_scale "$initial_font" >/dev/null 2>&1 || true
}
trap restore_device EXIT INT TERM

"$YUJIAN_ADB_BIN" shell rm -rf "$REMOTE_DIR" >/dev/null 2>&1 || true

all_ok=1
profiles=(
  "M01 320 640 1.0" "M02 320 640 1.3" "M03 320 640 1.5"
  "M04 393 852 1.0" "M05 393 852 1.3" "M06 393 852 1.5"
  "M07 411 915 1.0" "M08 411 915 1.3" "M09 411 915 1.5"
)

for row in "${profiles[@]}"; do
  read -r profile width height font_scale <<< "$row"
  requested="${width}x${height}"
  "$YUJIAN_ADB_BIN" shell wm size "$requested" > "$MATRIX_DIR/logs/${profile}_wm_size.log" 2>&1 || all_ok=0
  "$YUJIAN_ADB_BIN" shell wm density 160 > "$MATRIX_DIR/logs/${profile}_wm_density.log" 2>&1 || all_ok=0
  "$YUJIAN_ADB_BIN" shell settings put system font_scale "$font_scale" > "$MATRIX_DIR/logs/${profile}_font_scale.log" 2>&1 || all_ok=0
  sleep 2
  for variant in ADAPTIVE_V1 V3_BASELINE; do
    variant_token="$(tr '[:upper:]' '[:lower:]' <<< "$variant" | tr -d '\r\n')"
    log="$MATRIX_DIR/logs/${profile}_${variant_token}.log"
    "$YUJIAN_ADB_BIN" shell am force-stop "$YUJIAN_APP_PACKAGE" >/dev/null 2>&1 || true
    if ! timeout 300s "$YUJIAN_ADB_BIN" shell am instrument -w -r \
      -e class "$TEST_CLASS" \
      -e profile_id "$profile" \
      -e layout_variant "$variant" \
      -e font_scale "$font_scale" \
      -e expected_display_px "$requested" \
      -e source_sha "$BUILD_SHA" \
      "$YUJIAN_INSTRUMENTATION_TARGET" > "$log" 2>&1; then
      all_ok=0
    elif grep -Eiq 'FAILURES!!!|INSTRUMENTATION_FAILED|Assertion(Error|FailedError)|Process (crashed|has died)|shortMsg=Process crashed' "$log"; then
      all_ok=0
    elif ! grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \(1 test' "$log"; then
      all_ok=0
    fi
  done
done

"$YUJIAN_ADB_BIN" pull "$REMOTE_DIR" "$RAW_DIR" > "$MATRIX_DIR/logs/pull_runtime_matrix.log" 2>&1 || all_ok=0
python3 scripts/android_runtime/aggregate_nh01_adaptive_matrix.py \
  --raw-dir "$RAW_DIR/nh01-adaptive-visual-balance-v1" \
  --output-dir "$YUJIAN_EVIDENCE_DIR" \
  --source-sha "$BUILD_SHA" \
  --run-id "${GITHUB_RUN_ID:-local}" \
  --run-url "${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-pan277942135/Yujian_App}/actions/runs/${GITHUB_RUN_ID:-local}" \
  --contract design/qa/NH01_ADAPTIVE_VISUAL_BALANCE_V1.md || all_ok=0

if (( all_ok == 0 )); then
  printf '%s\n' "NH01_ADAPTIVE_DEVICE_MATRIX=FAIL logs=$MATRIX_DIR/logs" >&2
  exit 1
fi
printf '%s\n' "NH01_ADAPTIVE_DEVICE_MATRIX=PASS source_sha=$BUILD_SHA"
