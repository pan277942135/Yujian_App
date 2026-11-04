#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
EVIDENCE_DIR="${EVIDENCE_DIR:-evidence/fish-optics-v2-2/baseline}"
SOURCE_SHA="${SOURCE_SHA:-e0288964190015c262f43e730f6d40650cd49caf}"
APK_DIR="${APK_DIR:-build/baseline-runtime-apks}"
APP_PACKAGE="com.yujian.ai.uiv2"
TEST_PACKAGE="com.yujian.ai.uiv2.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
AVD_NAME="fish_optics_api36_m07"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
ADB="${ADB:-${SDK_ROOT}/platform-tools/adb}"
EMULATOR="${SDK_ROOT}/emulator/emulator"
RESULT=BLOCKED_INFRA
FAILURE_REASON=""
EMU_PID=""
SCREENRECORD_PID=""

export EVIDENCE_DIR API_LEVEL=36 BUILD_SHA="$SOURCE_SHA" ADB
YUJIAN_PREFLIGHT_ATTEMPTS=180
YUJIAN_PREFLIGHT_SLEEP_SECONDS=2
source "$ROOT/scripts/android_runtime/common.sh"
source "$ROOT/scripts/android_runtime/result.sh"
source "$ROOT/scripts/android_runtime/preflight.sh"
source "$ROOT/scripts/android_runtime/install_apks.sh"
source "$ROOT/scripts/android_runtime/run_instrumentation.sh"
source "$ROOT/scripts/android_runtime/diagnostics.sh"

mkdir -p "$EVIDENCE_DIR" "$EVIDENCE_DIR/device-metadata" "$EVIDENCE_DIR/screenshots" \
  "$EVIDENCE_DIR/recordings" "$EVIDENCE_DIR/shader-logs" "$EVIDENCE_DIR/geometry"

finish() {
  local rc=$?
  if [[ -n "$SCREENRECORD_PID" ]]; then
    "$ADB" shell "kill -2 $SCREENRECORD_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$EMU_PID" ]]; then
    "$ADB" emu kill >/dev/null 2>&1 || true
    kill "$EMU_PID" >/dev/null 2>&1 || true
    wait "$EMU_PID" >/dev/null 2>&1 || true
  fi
  if [[ ! -s "$EVIDENCE_DIR/runtime-result.json" ]]; then
    python3 - "$EVIDENCE_DIR/runtime-result.json" "$SOURCE_SHA" "$RESULT" "$FAILURE_REASON" "$rc" <<'PY'
import json, pathlib, sys, time
path, source, classification, reason, rc = sys.argv[1:]
pathlib.Path(path).write_text(json.dumps({
    "gate": "fish-optics-visual-baseline",
    "api_level": 36,
    "source_sha": source,
    "classification": classification,
    "failure_reason": reason or None,
    "exit_code": int(rc),
    "captured_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
}, indent=2) + "\n", encoding="utf-8")
PY
  fi
  find "$EVIDENCE_DIR" -type f ! -name evidence-sha256sums.txt -print0 \
    | sort -z | xargs -0 -r sha256sum > "$EVIDENCE_DIR/evidence-sha256sums.txt"
  exit "$rc"
}
trap finish EXIT

mkdir -p "$(dirname "$EVIDENCE_DIR")"
test -n "$SDK_ROOT"
test -x "$ADB"
test -x "$EMULATOR"
test "$(git -C "$ROOT" rev-parse HEAD)" = "${HARNESS_SHA:-$(git -C "$ROOT" rev-parse HEAD)}"

cat > "$EVIDENCE_DIR/source_manifest.json" <<EOF
{
  "repository": "pan277942135/Yujian_App",
  "pr": 316,
  "branch": "feat/p0-ai-orb-external-optics-v2",
  "source_sha": "$SOURCE_SHA",
  "baseline_apk_run_id": 41771912698,
  "baseline_apk_artifact_id": 13295009296,
  "baseline_test": "com.yujian.ai.RecognitionRevealProductionRuntimeTest",
  "harness_revision": "$(git -C "$ROOT" rev-parse HEAD)"
}
EOF

test -s "$APK_DIR/app-debug.apk"
test -s "$APK_DIR/app-debug-androidTest.apk"
(cd "$APK_DIR" && sha256sum -c SHA256SUMS) | tee "$EVIDENCE_DIR/apk-provenance.txt"
python3 "$ROOT/scripts/export_apk_model_trace.py" --apk "$APK_DIR/app-debug.apk" \
  --build-sha "$SOURCE_SHA" --output-dir "$EVIDENCE_DIR/model"

mkdir -p "$AVD_HOME"
export ANDROID_AVD_HOME="$AVD_HOME"
if [[ ! -s "$AVD_HOME/${AVD_NAME}.ini" ]]; then
  printf 'no\n' | "$SDK_ROOT/cmdline-tools/latest/bin/avdmanager" create avd \
    --name "$AVD_NAME" --package "system-images;android-36;google_apis;x86_64" \
    --device pixel_2 -p "$AVD_HOME/${AVD_NAME}.avd" --force
fi
python3 - "$AVD_HOME/${AVD_NAME}.avd/config.ini" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
values = {}
for line in p.read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        k, v = line.split("=", 1)
        values[k.strip()] = v.strip()
values.update({
    "hw.lcd.width": "1170",
    "hw.lcd.height": "2532",
    "hw.lcd.density": "480",
    "hw.initialOrientation": "Portrait",
    "skin.name": "1170x2532",
    "showDeviceFrame": "no",
    "hw.gpu.enabled": "yes",
    "hw.gpu.mode": "swiftshader_indirect",
    "hw.ramSize": "4096",
    "disk.dataPartition.size": "8192M",
})
p.write_text("".join(f"{k}={v}\n" for k, v in sorted(values.items())), encoding="utf-8")
print("AVD_CONFIG_BEFORE_BOOT=" + str(p))
for k in ("hw.lcd.width", "hw.lcd.height", "hw.lcd.density", "skin.name", "hw.gpu.mode"):
    print(f"{k}={values[k]}")
PY
cp "$AVD_HOME/${AVD_NAME}.avd/config.ini" "$EVIDENCE_DIR/device-metadata/avd-config-before-boot.ini"

if ! "$EMULATOR" -accel-check > "$EVIDENCE_DIR/device-metadata/emulator-acceleration-check.txt" 2>&1; then
  FAILURE_REASON="ANDROID_HARDWARE_ACCELERATION_UNAVAILABLE"
  RESULT=BLOCKED_INFRA
  cat "$EVIDENCE_DIR/device-metadata/emulator-acceleration-check.txt" >&2
  exit 10
fi
cat "$EVIDENCE_DIR/device-metadata/emulator-acceleration-check.txt"

"$ADB" start-server
if ! "$ADB" devices | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
  "$EMULATOR" -avd "$AVD_NAME" -no-window -no-snapshot -wipe-data -noaudio -no-boot-anim \
    -camera-back none -gpu swiftshader_indirect -no-metrics -partition-size 4096 > "$EVIDENCE_DIR/device-metadata/emulator.log" 2>&1 &
  EMU_PID=$!
fi
if ! timeout 180s "$ADB" wait-for-device; then
  FAILURE_REASON="ANDROID_EMULATOR_BOOT_TIMEOUT"
  RESULT=BLOCKED_INFRA
  printf 'Emulator boot timed out; ADB state follows.\n' >&2
  "$ADB" devices -l >&2 || true
  cat "$EVIDENCE_DIR/device-metadata/emulator.log" >&2 || true
  exit 10
fi
sdk="$("$ADB" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
[[ "$sdk" == 36 ]] || { FAILURE_REASON="API36_SDK_MISMATCH:$sdk"; RESULT=BLOCKED_INFRA; exit 10; }
"$ADB" devices -l > "$EVIDENCE_DIR/device-metadata/adb-devices.txt"
"$ADB" shell getprop > "$EVIDENCE_DIR/device-metadata/getprop.txt"
"$ADB" shell wm size > "$EVIDENCE_DIR/device-metadata/wm-size.txt"
"$ADB" shell wm density > "$EVIDENCE_DIR/device-metadata/wm-density.txt"
"$ADB" shell settings get system font_scale > "$EVIDENCE_DIR/device-metadata/font-scale.txt"
"$ADB" shell dumpsys window displays > "$EVIDENCE_DIR/device-metadata/window-displays.txt"
"$ADB" exec-out screencap -p > "$EVIDENCE_DIR/screenshots/boot-screen.png"
grep -Fx 'Physical size: 1170x2532' "$EVIDENCE_DIR/device-metadata/wm-size.txt"
! grep -q '^Override size:' "$EVIDENCE_DIR/device-metadata/wm-size.txt"
grep -Fx 'Physical density: 480' "$EVIDENCE_DIR/device-metadata/wm-density.txt"
! grep -q '^Override density:' "$EVIDENCE_DIR/device-metadata/wm-density.txt"
grep -Fx '1.0' "$EVIDENCE_DIR/device-metadata/font-scale.txt"
python3 - "$EVIDENCE_DIR/screenshots/boot-screen.png" <<'PY'
from PIL import Image
import sys
size = Image.open(sys.argv[1]).size
assert size == (1170, 2532), f"native screencap mismatch: {size}"
print(f"NATIVE_SCREEN_CAPTURE_PASS={size[0]}x{size[1]}")
PY

android_runtime_preflight || { FAILURE_REASON="ANDROID_RUNTIME_PREFLIGHT_FAILED:${YUJIAN_FAILURE_REASON}"; RESULT=BLOCKED_INFRA; exit "$EXIT_BLOCKED_INFRA"; }

mkdir -p "$EVIDENCE_DIR/device-metadata"
"$SDK_ROOT/build-tools/35.0.0/aapt" dump badging "$APK_DIR/app-debug.apk" > "$EVIDENCE_DIR/apk-provenance-app-badging.txt"
"$SDK_ROOT/build-tools/35.0.0/aapt" dump badging "$APK_DIR/app-debug-androidTest.apk" > "$EVIDENCE_DIR/apk-provenance-test-badging.txt"
grep -F "package: name='$APP_PACKAGE'" "$EVIDENCE_DIR/apk-provenance-app-badging.txt"
grep -F "package: name='$TEST_PACKAGE'" "$EVIDENCE_DIR/apk-provenance-test-badging.txt"
grep -F "versionCode='6'" "$EVIDENCE_DIR/apk-provenance-app-badging.txt"
grep -F "versionName='2.3.3-orb-wake-v1.1-20261103'" "$EVIDENCE_DIR/apk-provenance-app-badging.txt"

"$ADB" logcat -c
set +e
android_runtime_install_apks "$APK_DIR/app-debug.apk" "$APK_DIR/app-debug-androidTest.apk"
install_rc=$?
set -e
if (( install_rc != EXIT_PASS )); then
  FAILURE_REASON="ANDROID_RUNTIME_INSTALL_FAILED:${YUJIAN_FAILURE_REASON}"
  if (( install_rc == EXIT_BLOCKED_INFRA )); then RESULT=BLOCKED_INFRA; exit "$EXIT_BLOCKED_INFRA"; fi
  RESULT=FAIL_ARTIFACT
  exit "$EXIT_FAIL_ARTIFACT"
fi
YUJIAN_INSTALL_STATUS=PASS
SCREENRECORD_PID="$("$ADB" shell 'screenrecord --bit-rate 8000000 --time-limit 120 /sdcard/fish_optics_baseline_processing_result.mp4 >/dev/null 2>&1 & echo $!')"
SCREENRECORD_PID="$(tr -d '\r' <<< "$SCREENRECORD_PID" | tail -n 1)"
test -n "$SCREENRECORD_PID"
sleep 1
set +e
android_runtime_run_instrumentation "com.yujian.ai.RecognitionRevealProductionRuntimeTest" 0 240
instrumentation_rc=$?
set -e
YUJIAN_INSTRUMENTATION_STATUS=$([[ "$instrumentation_rc" == 0 ]] && echo PASS || echo FAIL)
"$ADB" shell screencap -p /sdcard/fish-optics-baseline-final.png >/dev/null 2>&1 || true
"$ADB" exec-out screencap -p > "$EVIDENCE_DIR/screenshots/final-full-screen.png"
"$ADB" logcat -v threadtime -d > "$EVIDENCE_DIR/shader-logs/logcat-full.txt"
grep -E 'RecognitionPerimeterGL|OrbCoreOpticalEffect|OrbFluidRefractionV1|OrbGrassWarpV12|SlimSamSegmenter|SLIMSAM_MODEL_LOAD|RecognitionScene' \
  "$EVIDENCE_DIR/shader-logs/logcat-full.txt" > "$EVIDENCE_DIR/shader-logs/optics-filtered.log" || true
grep -E 'PHOTO_GPU_INIT=|PERIMETER_SHADER_COMPILE=|PERIMETER_SHADER_LINK=|PHOTO_GPU_FIRST_FRAME=|PHOTO_GPU_DRAW=|PHOTO_GPU_SWAP=|GLES_VERSION=|GL_RENDERER=|GLSL_VERSION=|fluid refraction|shared-current candidate|candidate fluid|SLIMSAM_MODEL_LOAD|SlimSAM segment success|REVEAL_DECISION' \
  "$EVIDENCE_DIR/shader-logs/logcat-full.txt" > "$EVIDENCE_DIR/shader-logs/runtime-events.log" || true

for attempt in $(seq 1 30); do
  if "$ADB" shell run-as "$APP_PACKAGE" test -s cache/rr09-gold-transfer-production/RR09_Production_Qualification_Report.json >/dev/null 2>&1; then break; fi
  sleep 1
done
"$ADB" shell "kill -2 $SCREENRECORD_PID" >/dev/null 2>&1 || true
SCREENRECORD_PID=""
for attempt in $(seq 1 24); do
  bytes="$("$ADB" shell "wc -c < /sdcard/fish_optics_baseline_processing_result.mp4 2>/dev/null || echo 0" | tr -d '\r' | tail -n 1)"
  [[ "$bytes" =~ ^[0-9]+$ ]] && (( bytes > 1024 )) && break
  sleep 0.5
done
"$ADB" exec-out run-as "$APP_PACKAGE" tar -cf - cache/rr09-gold-transfer-production \
  > "$EVIDENCE_DIR/production-test-cache.tar" 2> "$EVIDENCE_DIR/production-test-cache-pull.log" || true
"$ADB" pull /sdcard/fish_optics_baseline_processing_result.mp4 \
  "$EVIDENCE_DIR/recordings/fish_optics_baseline_processing_result.mp4" >/dev/null 2>&1 || true
"$ADB" shell rm -f /sdcard/fish_optics_baseline_processing_result.mp4 >/dev/null 2>&1 || true
if [[ -s "$EVIDENCE_DIR/production-test-cache.tar" ]]; then
  tar -xf "$EVIDENCE_DIR/production-test-cache.tar" -C "$EVIDENCE_DIR"
fi

python3 - "$EVIDENCE_DIR" "$SOURCE_SHA" "$instrumentation_rc" <<'PY'
import hashlib, json, pathlib, sys, time, zipfile
root, source, rc = pathlib.Path(sys.argv[1]), sys.argv[2], int(sys.argv[3])
app_report = root / "cache/rr09-gold-transfer-production/RR09_Production_Qualification_Report.json"
matrix_report = root / "cache/rr09-gold-transfer-production/AI_Orb_WaterLight_Runtime_Matrix.json"
payload = {
  "gate": "fish-optics-visual-baseline",
  "source_sha": source,
  "api_level": 36,
  "device_profile": "M07_NATIVE_390x844dp_1170x2532px_480dpi",
  "instrumentation_exit_code": rc,
  "instrumentation_status": "PASS" if rc == 0 else "FAIL_TEST",
  "production_result_report": str(app_report.relative_to(root)) if app_report.exists() else None,
  "runtime_matrix": str(matrix_report.relative_to(root)) if matrix_report.exists() else None,
  "captured_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
}
if app_report.exists():
  payload["production_test"] = json.loads(app_report.read_text(encoding="utf-8"))
if matrix_report.exists():
  payload["runtime_matrix_data"] = json.loads(matrix_report.read_text(encoding="utf-8"))
log = root / "shader-logs/runtime-events.log"
events = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
slimsam = {}
with zipfile.ZipFile("build/baseline-runtime-apks/app-debug.apk") as archive:
  for name in (
    "assets/slimsam/vision_encoder_quantized.onnx",
    "assets/slimsam/prompt_encoder_mask_decoder_quantized.onnx",
  ):
    data = archive.read(name)
    slimsam[name] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
payload["mask_provider"] = {
  "provider": "FishSubjectPreviewEngine -> SlimSamSegmenter (production ONNX path)",
  "models": slimsam,
  "runtime_vision_model_loaded": "SLIMSAM_MODEL_LOAD_END model=vision" in events,
  "runtime_mask_decoder_loaded": "SLIMSAM_MODEL_LOAD_END model=decoder" in events,
  "runtime_segment_success": "SlimSAM segment success" in events,
  "mask_status_ready": payload.get("production_test", {}).get("subject_status") == "READY",
  "mask_quality_good": payload.get("production_test", {}).get("mask_quality") == "GOOD",
  "contour_segments": payload.get("production_test", {}).get("contour_segments", 0),
}
payload["runtime_shader_evidence"] = {
  "gles2_compile_pass": "PERIMETER_SHADER_COMPILE=PASS" in events,
  "gles2_first_gpu_frame_pass": "PHOTO_GPU_FIRST_FRAME=PASS" in events,
  "agsl_candidate_created": "loaded fluid refraction with shared frozen R01B masks" in events and "loaded shared-current candidate with shared frozen R01B masks" in events,
  "agsl_fallback_logged": "candidate fluid/V1.2 stack unavailable" in events or "candidate and V1.1 AGSL unavailable" in events,
}
payload["classification"] = "BASELINE_CAPTURED" if rc == 0 else "FAIL_TEST"
payload["gates"] = {
  "API36_RUNTIME_GATE": "INSTRUMENTATION_PASS" if rc == 0 else "FAIL_TEST",
  "GLES2_SHADER_GATE": "PASS" if payload["runtime_shader_evidence"]["gles2_compile_pass"] else "NOT_PROVEN",
  "AGSL_SHADER_GATE": "PASS" if payload["runtime_shader_evidence"]["agsl_candidate_created"] else "NOT_PROVEN",
  "REAL_MASK_GATE": "PASS" if all((
    payload["mask_provider"]["runtime_vision_model_loaded"],
    payload["mask_provider"]["runtime_mask_decoder_loaded"],
    payload["mask_provider"]["runtime_segment_success"],
    payload["mask_provider"]["mask_status_ready"],
    payload["mask_provider"]["mask_quality_good"],
    payload["mask_provider"]["contour_segments"] > 0,
  )) else "NOT_PROVEN",
  "AUTOMATIC_HANDOFF_GATE": "NOT_PROVEN",
  "VISUAL_ACCEPTANCE_GATE": "REVIEW_REQUIRED",
}
payload["automatic_production_handoff"] = "NOT_PROVEN_BY_BASELINE_TEST"
payload["visual_acceptance"] = "REVIEW_REQUIRED"
(root / "runtime-result.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(payload["runtime_shader_evidence"], indent=2))
if rc != 0:
  raise SystemExit(rc if rc in (10,20,30,40,50) else 30)
PY
RESULT=BASELINE_CAPTURED
FAILURE_REASON=""
exit 0
