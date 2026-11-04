#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
PROFILE="${PROFILE:-M07}"
EVIDENCE_DIR="${EVIDENCE_DIR:-evidence/fish-optics-v2-2/baseline}"
SOURCE_SHA="${SOURCE_SHA:-e0288964190015c262f43e730f6d40650cd49caf}"
APK_DIR="${APK_DIR:-build/baseline-runtime-apks}"
APP_PACKAGE="com.yujian.ai.uiv2"
TEST_PACKAGE="com.yujian.ai.uiv2.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
AVD_NAME="fish_optics_api36_${PROFILE,,}"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
ADB="${ADB:-${SDK_ROOT}/platform-tools/adb}"
EMULATOR="${SDK_ROOT}/emulator/emulator"
RESULT=BLOCKED_INFRA
FAILURE_REASON=""
EMU_PID=""
SCREENRECORD_PID=""
case "$PROFILE" in
  M04) SCREEN_WIDTH=1080; SCREEN_HEIGHT=1920; SCREEN_DENSITY=480; LOGICAL_WIDTH=360; LOGICAL_HEIGHT=640 ;;
  M07) SCREEN_WIDTH=1170; SCREEN_HEIGHT=2532; SCREEN_DENSITY=480; LOGICAL_WIDTH=390; LOGICAL_HEIGHT=844 ;;
  M10) SCREEN_WIDTH=1290; SCREEN_HEIGHT=2796; SCREEN_DENSITY=480; LOGICAL_WIDTH=430; LOGICAL_HEIGHT=932 ;;
  HUAWEI_CMS_AL20) SCREEN_WIDTH=1280; SCREEN_HEIGHT=2832; SCREEN_DENSITY=560; LOGICAL_WIDTH=366; LOGICAL_HEIGHT=809 ;;
  *) printf 'Unsupported API36 profile: %s\n' "$PROFILE" >&2; exit 10 ;;
esac
RECORDING_NAME="fish_optics_${PROFILE,,}_processing_result.mp4"

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
    python3 - "$EVIDENCE_DIR/runtime-result.json" "$SOURCE_SHA" "$PROFILE" "$RESULT" "$FAILURE_REASON" "$rc" <<'PY'
import json, pathlib, sys, time
path, source, profile, classification, reason, rc = sys.argv[1:]
pathlib.Path(path).write_text(json.dumps({
    "gate": "fish-optics-visual-api36",
    "api_level": 36,
    "device_profile": profile,
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
  "baseline_apk_run_id": $([[ "$SOURCE_SHA" == e0288964190015c262f43e730f6d40650cd49caf ]] && echo 41771912698 || echo null),
  "baseline_apk_artifact_id": $([[ "$SOURCE_SHA" == e0288964190015c262f43e730f6d40650cd49caf ]] && echo 13295009296 || echo null),
  "apk_provenance_type": "$([[ "$SOURCE_SHA" == e0288964190015c262f43e730f6d40650cd49caf ]] && echo FROZEN_BASELINE || echo EXACT_HEAD_ANDROID_CI_ARTIFACT)",
  "apk_sha256": "$(sha256sum "$APK_DIR/app-debug.apk" | cut -d' ' -f1)",
  "apk_bytes": $(stat -c%s "$APK_DIR/app-debug.apk"),
  "android_ci_run_id": "${GITHUB_RUN_ID:-not_available}",
  "physical_device_gate": "NOT_RUN",
  "avd_profile": "$PROFILE",
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
SCREEN_WIDTH="$SCREEN_WIDTH" SCREEN_HEIGHT="$SCREEN_HEIGHT" SCREEN_DENSITY="$SCREEN_DENSITY" \
python3 - "$AVD_HOME/${AVD_NAME}.avd/config.ini" <<'PY'
import pathlib, sys
import os
p = pathlib.Path(sys.argv[1])
values = {}
for line in p.read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        k, v = line.split("=", 1)
        values[k.strip()] = v.strip()
values.update({
    "hw.lcd.width": os.environ["SCREEN_WIDTH"],
    "hw.lcd.height": os.environ["SCREEN_HEIGHT"],
    "hw.lcd.density": os.environ["SCREEN_DENSITY"],
    "hw.initialOrientation": "Portrait",
    "skin.name": os.environ["SCREEN_WIDTH"] + "x" + os.environ["SCREEN_HEIGHT"],
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
boot_completed=""
for attempt in $(seq 1 90); do
  boot_completed="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "$boot_completed" == 1 ]] && break
  sleep 2
done
if [[ "$boot_completed" != 1 ]]; then
  FAILURE_REASON="ANDROID_BOOT_COMPLETED_TIMEOUT"
  RESULT=BLOCKED_INFRA
  printf 'Android boot did not reach sys.boot_completed=1.\n' >&2
  "$ADB" shell getprop >&2 || true
  exit 10
fi
printf 'sys.boot_completed=%s\n' "$boot_completed" | tee "$EVIDENCE_DIR/device-metadata/boot-completed.txt"
sdk="$("$ADB" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
[[ "$sdk" == 36 ]] || { FAILURE_REASON="API36_SDK_MISMATCH:$sdk"; RESULT=BLOCKED_INFRA; exit 10; }
"$ADB" devices -l > "$EVIDENCE_DIR/device-metadata/adb-devices.txt"
"$ADB" shell getprop > "$EVIDENCE_DIR/device-metadata/getprop.txt"
"$ADB" shell wm size > "$EVIDENCE_DIR/device-metadata/wm-size.txt"
"$ADB" shell wm density > "$EVIDENCE_DIR/device-metadata/wm-density.txt"
"$ADB" shell settings get system font_scale > "$EVIDENCE_DIR/device-metadata/font-scale.txt"
"$ADB" shell dumpsys window displays > "$EVIDENCE_DIR/device-metadata/window-displays.txt"
"$ADB" exec-out screencap -p > "$EVIDENCE_DIR/screenshots/boot-screen.png"
grep -Fx "Physical size: ${SCREEN_WIDTH}x${SCREEN_HEIGHT}" "$EVIDENCE_DIR/device-metadata/wm-size.txt"
! grep -q '^Override size:' "$EVIDENCE_DIR/device-metadata/wm-size.txt"
grep -Fx "Physical density: ${SCREEN_DENSITY}" "$EVIDENCE_DIR/device-metadata/wm-density.txt"
! grep -q '^Override density:' "$EVIDENCE_DIR/device-metadata/wm-density.txt"
grep -Fx '1.0' "$EVIDENCE_DIR/device-metadata/font-scale.txt"
python3 - "$EVIDENCE_DIR/screenshots/boot-screen.png" "$SCREEN_WIDTH" "$SCREEN_HEIGHT" <<'PY'
from PIL import Image
import sys
size = Image.open(sys.argv[1]).size
expected = (int(sys.argv[2]), int(sys.argv[3]))
assert size == expected, f"native screencap mismatch: expected {expected}, got {size}"
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
SCREENRECORD_PID="$("$ADB" shell "screenrecord --bit-rate 8000000 --time-limit 120 /sdcard/$RECORDING_NAME >/dev/null 2>&1 & echo \$!")"
SCREENRECORD_PID="$(tr -d '\r' <<< "$SCREENRECORD_PID" | tail -n 1)"
test -n "$SCREENRECORD_PID"
sleep 1
set +e
android_runtime_run_instrumentation "com.yujian.ai.RecognitionRevealProductionRuntimeTest" 0 240
instrumentation_rc=$?
set -e
cp "$EVIDENCE_DIR/instrumentation.log" "$EVIDENCE_DIR/instrumentation-production.log"
YUJIAN_INSTRUMENTATION_STATUS=$([[ "$instrumentation_rc" == 0 ]] && echo PASS || echo FAIL)
"$ADB" shell screencap -p "/sdcard/fish-optics-${PROFILE,,}-final.png" >/dev/null 2>&1 || true
"$ADB" exec-out screencap -p > "$EVIDENCE_DIR/screenshots/final-full-screen.png"

for attempt in $(seq 1 30); do
  if "$ADB" shell run-as "$APP_PACKAGE" test -s cache/rr09-gold-transfer-production/RR09_Production_Qualification_Report.json >/dev/null 2>&1; then break; fi
  sleep 1
done
"$ADB" shell "kill -2 $SCREENRECORD_PID" >/dev/null 2>&1 || true
SCREENRECORD_PID=""
for attempt in $(seq 1 24); do
  bytes="$("$ADB" shell "wc -c < /sdcard/$RECORDING_NAME 2>/dev/null || echo 0" | tr -d '\r' | tail -n 1)"
  [[ "$bytes" =~ ^[0-9]+$ ]] && (( bytes > 1024 )) && break
  sleep 0.5
done
"$ADB" exec-out run-as "$APP_PACKAGE" tar -cf - cache/rr09-gold-transfer-production \
  > "$EVIDENCE_DIR/production-test-cache.tar" 2> "$EVIDENCE_DIR/production-test-cache-pull.log" || true
"$ADB" pull "/sdcard/$RECORDING_NAME" \
  "$EVIDENCE_DIR/recordings/$RECORDING_NAME" >/dev/null 2>&1 || true
"$ADB" shell rm -f "/sdcard/$RECORDING_NAME" >/dev/null 2>&1 || true
if [[ -s "$EVIDENCE_DIR/production-test-cache.tar" ]]; then
  tar -xf "$EVIDENCE_DIR/production-test-cache.tar" -C "$EVIDENCE_DIR"
fi

set +e
android_runtime_run_instrumentation "com.yujian.ai.RecognitionOpticalFidelityRuntimeTest" 0 240
performance_rc=$?
set -e
cp "$EVIDENCE_DIR/instrumentation.log" "$EVIDENCE_DIR/instrumentation-performance.log"
"$ADB" exec-out run-as "$APP_PACKAGE" tar -cf - cache/recognition-optical-fidelity \
  > "$EVIDENCE_DIR/performance-test-cache.tar" 2> "$EVIDENCE_DIR/performance-test-cache-pull.log" || true
if [[ -s "$EVIDENCE_DIR/performance-test-cache.tar" ]]; then
  tar -xf "$EVIDENCE_DIR/performance-test-cache.tar" -C "$EVIDENCE_DIR"
fi

"$ADB" logcat -v threadtime -d > "$EVIDENCE_DIR/shader-logs/logcat-full.txt"
grep -E 'RecognitionPerimeterGL|OrbCoreOpticalEffect|OrbFluidRefractionV1|OrbGrassWarpV12|SlimSamSegmenter|SLIMSAM_MODEL_LOAD|RecognitionScene' \
  "$EVIDENCE_DIR/shader-logs/logcat-full.txt" > "$EVIDENCE_DIR/shader-logs/optics-filtered.log" || true
grep -E 'PHOTO_GPU_INIT=|PERIMETER_SHADER_COMPILE=|PERIMETER_SHADER_LINK=|PHOTO_GPU_FIRST_FRAME=|PHOTO_GPU_DRAW=|PHOTO_GPU_SWAP=|GLES_VERSION=|GL_RENDERER=|GLSL_VERSION=|fluid refraction|shared-current candidate|candidate fluid|SLIMSAM_MODEL_LOAD|SLIMSAM_ENCODER_|SLIMSAM_DECODER_|SlimSAM segment success|REVEAL_DECISION|WaterLightEnergy: ENERGY_EVENT|VISUAL_STAGE|SCENE_STAGE_ENTER' \
  "$EVIDENCE_DIR/shader-logs/logcat-full.txt" > "$EVIDENCE_DIR/shader-logs/runtime-events.log" || true

python3 - "$EVIDENCE_DIR" "$SOURCE_SHA" "$PROFILE" "$SCREEN_WIDTH" "$SCREEN_HEIGHT" "$SCREEN_DENSITY" \
  "$instrumentation_rc" "$performance_rc" "$APK_DIR/app-debug.apk" <<'PY'
import hashlib, json, pathlib, sys, time, zipfile
root, source, profile = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
width, height, density, rc, performance_rc = map(int, sys.argv[4:9])
apk_path = pathlib.Path(sys.argv[9])
app_report = root / "cache/rr09-gold-transfer-production/RR09_Production_Qualification_Report.json"
matrix_report = root / "cache/rr09-gold-transfer-production/AI_Orb_WaterLight_Runtime_Matrix.json"
diagnostic_report = root / "cache/rr09-gold-transfer-production/Runtime_Test_Diagnostic.json"
performance_report = root / "cache/recognition-optical-fidelity/recognition_optical_fidelity_v3_1.json"
payload = {
  "gate": "fish-optics-visual-api36",
  "source_sha": source,
  "api_level": 36,
  "device_profile": {
    "profile": profile,
    "device_kind": "API36_CLOUD_AVD",
    "physical_device_gate": "NOT_RUN",
    "logical_width_dp": width * 160 / density,
    "logical_height_dp": height * 160 / density,
    "physical_width_px": width,
    "physical_height_px": height,
    "density_dpi": density,
    "font_scale": 1.0,
  },
  "instrumentation_exit_code": rc,
  "instrumentation_status": "PASS" if rc == 0 else "FAIL_TEST",
  "performance_test_exit_code": performance_rc,
  "performance_test_status": "PASS" if performance_rc == 0 else "FAIL_TEST",
  "production_result_report": str(app_report.relative_to(root)) if app_report.exists() else None,
  "runtime_diagnostic_report": str(diagnostic_report.relative_to(root)) if diagnostic_report.exists() else None,
  "runtime_matrix": str(matrix_report.relative_to(root)) if matrix_report.exists() else None,
  "performance_report": str(performance_report.relative_to(root)) if performance_report.exists() else None,
  "captured_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
}
if app_report.exists():
  payload["production_test"] = json.loads(app_report.read_text(encoding="utf-8"))
if matrix_report.exists():
  payload["runtime_matrix_data"] = json.loads(matrix_report.read_text(encoding="utf-8"))
if diagnostic_report.exists():
  payload["runtime_test_diagnostic"] = json.loads(diagnostic_report.read_text(encoding="utf-8"))
if performance_report.exists():
  payload["performance_data"] = json.loads(performance_report.read_text(encoding="utf-8"))
log = root / "shader-logs/runtime-events.log"
events = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
slimsam = {}
with zipfile.ZipFile(apk_path) as archive:
  for name in (
    "assets/slimsam/vision_encoder_quantized.onnx",
    "assets/slimsam/prompt_encoder_mask_decoder_quantized.onnx",
  ):
    data = archive.read(name)
    slimsam[name] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
production = payload.get("production_test", {})
diagnostic = payload.get("runtime_test_diagnostic", {})
matrix = payload.get("runtime_matrix_data", {})
mask_status = production.get("subject_status", diagnostic.get("mask_status"))
mask_quality = production.get("mask_quality", diagnostic.get("mask_quality"))
contour_segments = production.get("contour_segments", diagnostic.get("contour_segments", 0)) or 0
payload["mask_provider"] = {
  "provider": "FishSubjectPreviewEngine -> SlimSamSegmenter (production ONNX path)",
  "models": slimsam,
  "runtime_vision_model_loaded": "SLIMSAM_MODEL_LOAD_END model=vision" in events,
  "runtime_mask_decoder_loaded": "SLIMSAM_MODEL_LOAD_END model=decoder" in events,
  "runtime_segment_success": "SlimSAM segment success" in events,
  "mask_status_ready": mask_status == "READY",
  "mask_quality_good": mask_quality == "GOOD",
  "contour_segments": contour_segments,
}
payload["runtime_shader_evidence"] = {
  "gles2_compile_pass": "PERIMETER_SHADER_COMPILE=PASS" in events,
  "gles2_first_gpu_frame_pass": "PHOTO_GPU_FIRST_FRAME=PASS" in events,
  "agsl_candidate_created": "loaded fluid refraction with shared frozen R01B masks" in events and "loaded shared-current candidate with shared frozen R01B masks" in events,
  "agsl_fallback_logged": "candidate fluid/V1.2 stack unavailable" in events or "candidate and V1.1 AGSL unavailable" in events,
}
baseline = source == "e0288964190015c262f43e730f6d40650cd49caf"
payload["classification"] = ("BASELINE_CAPTURED" if baseline else "EXACT_HEAD_RUNTIME_CAPTURED") if rc == 0 and performance_rc == 0 else "FAIL_TEST"
production = payload.get("production_test", {})
diagnostic = payload.get("runtime_test_diagnostic", {})
matrix = payload.get("runtime_matrix_data", {})
automatic_handoff_observed = production.get("automatic_handoff_observed", diagnostic.get("automatic_handoff_observed", False))
result_geometry_status = production.get("result_geometry_status", diagnostic.get("result_geometry_status"))
photo_identity_matches = diagnostic.get("processing_result_photo_identity_matches", False)
observed_stages = set(matrix.get("real_time_stages_observed", diagnostic.get("real_time_stages_observed", [])))
payload["gates"] = {
  "SOURCE_GATE": "PASS" if source else "FAIL_ARTIFACT",
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
  "REAL_TIME_13_STAGE_GATE": "PASS" if rc == 0 and automatic_handoff_observed and len(observed_stages) == 13 else "NOT_PROVEN",
  "13_STAGE_GATE": "PASS" if len(observed_stages) == 13 and len(matrix.get("stages", [])) >= 13 else "NOT_PROVEN",
  "AUTOMATIC_HANDOFF_GATE": "PASS" if automatic_handoff_observed and (production.get("result_entry_mode", diagnostic.get("result_entry_mode", "")).startswith("PRODUCTION_ON_FINISHED") and (production.get("automatic_handoff_observed") or diagnostic.get("result_entries", 0) >= 1)) else "NOT_PROVEN",
  "PROCESSING_RESULT_GATE": "PASS" if automatic_handoff_observed and result_geometry_status == "RESOLVED" and photo_identity_matches else "NOT_PROVEN",
  "DETERMINISTIC_13_STAGE_CAPTURE_GATE": "PASS" if len(matrix.get("stages", [])) >= 13 else "NOT_PROVEN",
  "PERIMETER_VISUAL_GATE": "REVIEW_REQUIRED",
  "FISH_CONTOUR_VISUAL_GATE": "REVIEW_REQUIRED",
  "GOLD_GLINT_GATE": "REVIEW_REQUIRED",
  "VISUAL_EVIDENCE_GATE": "CAPTURED_REVIEW_REQUIRED",
  "PERFORMANCE_GATE": "MEASURED" if performance_rc == 0 and performance_report.exists() else "NOT_RUN",
  "PHYSICAL_DEVICE_GATE": "NOT_RUN",
  "VISUAL_ACCEPTANCE_GATE": "REVIEW_REQUIRED",
}
payload["automatic_production_handoff"] = production.get(
  "result_entry_mode", diagnostic.get("result_entry_mode", "NOT_PROVEN"))
payload["visual_acceptance"] = "REVIEW_REQUIRED"
(root / "runtime-result.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(payload["runtime_shader_evidence"], indent=2))
if rc != 0 or performance_rc != 0:
  code = rc or performance_rc
  raise SystemExit(code if code in (10,20,30,40,50) else 30)
PY
RESULT=$([[ "$SOURCE_SHA" == e0288964190015c262f43e730f6d40650cd49caf ]] && echo BASELINE_CAPTURED || echo EXACT_HEAD_RUNTIME_CAPTURED)
FAILURE_REASON=""
exit 0
