#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.ui.home.GeminiNormalHomeResponsiveTest'
}

gate_instrumentation_timeout_seconds() {
  printf '%s\n' '900'
}

gate_before_instrumentation() {
  "$YUJIAN_ADB_BIN" logcat -c >/dev/null 2>&1 || true
}

gate_after_instrumentation() {
  local output="$YUJIAN_EVIDENCE_DIR/compose-simulation"
  local external="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/nh01-gemini-v3"
  mkdir -p "$output" "$YUJIAN_EVIDENCE_DIR/android-device"
  "$YUJIAN_ADB_BIN" pull "$external" "$output" \
    > "$YUJIAN_EVIDENCE_DIR/android-device/compose_evidence_pull_after_instrumentation.log" 2>&1 || true
}

gemini_v2_run_seed() {
  local method="$1"
  local log="$YUJIAN_EVIDENCE_DIR/seed_${method}.log"
  timeout 240s "$YUJIAN_ADB_BIN" shell am instrument -w -r \
    -e class "com.yujian.ai.HomeVisualEvidenceSeedTest#$method" \
    "$YUJIAN_INSTRUMENTATION_TARGET" > "$log" 2>&1
  local rc=$?
  if (( rc != 0 )) || grep -Eiq 'FAILURES!!!|INSTRUMENTATION_FAILED|Assertion(Error|FailedError)|Process (crashed|has died)|Process .* (crashed|has died)|shortMsg=Process crashed' "$log"; then
    cat "$log" >&2
    return 1
  fi
  grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \([0-9]+ test' "$log"
}

gemini_v2_wait_for_app() {
  local expected_size="$1"
  local attempt resumed pid screenshot
  screenshot="$YUJIAN_EVIDENCE_DIR/android-device/readiness_${expected_size}.png"
  for attempt in $(seq 1 30); do
    resumed="$("$YUJIAN_ADB_BIN" shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|ResumedActivity' | head -n 1 || true)"
    pid="$("$YUJIAN_ADB_BIN" shell pidof "$YUJIAN_APP_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    if [[ "$resumed" == *"$YUJIAN_APP_PACKAGE/com.yujian.ai.MainActivity"* && "$pid" =~ [0-9] ]]; then
      local render_attempt
      for render_attempt in $(seq 1 20); do
        "$YUJIAN_ADB_BIN" exec-out screencap -p > "$screenshot" || return 1
        if python3 - "$screenshot" "$expected_size" <<'PY'
from PIL import Image, ImageStat
import sys
path, expected = sys.argv[1], tuple(int(part) for part in sys.argv[2].split("x"))
image = Image.open(path).convert("RGB")
if image.size != expected:
    raise SystemExit(1)
stat = ImageStat.Stat(image)
if sum(stat.stddev) / 3.0 < 18 or sum(stat.mean) / 3.0 < 12:
    raise SystemExit(1)
PY
        then
          return 0
        fi
        sleep 1
      done
    fi
    sleep 1
  done
  return 1
}

gemini_v2_capture_hierarchy() {
  local profile="$1"
  local directory="$YUJIAN_EVIDENCE_DIR/android-device"
  local device_xml="/sdcard/nh01_gemini_${profile}_window.xml"
  local dump_log="$directory/${profile}_uiautomator_dump.log"
  local xml="$directory/${profile}_window.xml"
  local json="$directory/${profile}_bounds.json"
  local status="NOT_RUN"
  if timeout 45s "$YUJIAN_ADB_BIN" shell uiautomator dump --compressed "$device_xml" > "$dump_log" 2>&1 && \
     "$YUJIAN_ADB_BIN" pull "$device_xml" "$xml" >> "$dump_log" 2>&1 && [[ -s "$xml" ]]; then
    if python3 - "$xml" "$json" <<'PY'
import json, sys, xml.etree.ElementTree as ET
source, output = sys.argv[1:]
root = ET.parse(source).getroot()
nodes = []
for node in root.iter("node"):
    bounds = node.attrib.get("bounds", "")
    if not bounds:
        continue
    nodes.append({
        "text": node.attrib.get("text", ""),
        "content_description": node.attrib.get("content-desc", ""),
        "resource_id": node.attrib.get("resource-id", ""),
        "class": node.attrib.get("class", ""),
        "clickable": node.attrib.get("clickable") == "true",
        "bounds_px": bounds,
    })
with open(output, "w", encoding="utf-8") as handle:
    json.dump({"source": "ANDROID_UIAUTOMATOR", "status": "CAPTURED", "nodes": nodes}, handle, ensure_ascii=False, indent=2)
PY
    then
      status="CAPTURED"
    fi
  fi
  if [[ "$status" != "CAPTURED" ]]; then
    python3 - "$json" "$dump_log" <<'PY'
import json, sys
json.dump({"source": "ANDROID_UIAUTOMATOR", "status": "NOT_RUN", "reason": "API28 hierarchy dump unavailable or timed out", "log": sys.argv[2]}, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
  fi
  printf '%s\n' "$status"
}

gemini_v2_assert_reference_home() {
  local profile="$1"
  local source="$YUJIAN_EVIDENCE_DIR/android-device/${profile}_bounds.json"
  python3 - "$source" <<'PY'
import json, pathlib, sys
payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("status") != "CAPTURED":
    raise SystemExit("Gemini Home hierarchy bounds are unavailable")
texts = [str(node.get("text", "")) for node in payload.get("nodes", [])]
required_exact = {"3", "1", "草鱼"}
missing = sorted(value for value in required_exact if value not in texts)
required_fragments = ("最近鱼获", "42.6 cm", "1.28 kg", "浙江 · 千岛湖", "记录下一条鱼")
missing.extend(value for value in required_fragments if not any(value in text for text in texts))
if missing:
    raise SystemExit("Reference business state is not present in the running Gemini page: " + ", ".join(missing))
payload["business_fixture"] = {
    "species_count": 3,
    "catch_count": 3,
    "record_days": 1,
    "species_name": "草鱼",
    "length_cm": 42.6,
    "weight_kg": 1.28,
    "location": "浙江 · 千岛湖",
    "photo_source": "home_normal/fish_record/sample_recent_catch.jpg",
    "verification": "PASS_ANDROID_UIAUTOMATOR_TEXT",
}
pathlib.Path(sys.argv[1]).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
}

gemini_v2_wait_for_reference_home() {
  local profile="$1" status attempt
  local wait_log="$YUJIAN_EVIDENCE_DIR/android-device/${profile}_reference_home_wait.log"
  for attempt in $(seq 1 12); do
    status="$(gemini_v2_capture_hierarchy "$profile")"
    if [[ "$status" == "CAPTURED" ]] && gemini_v2_assert_reference_home "$profile" >> "$wait_log" 2>&1; then
      printf '%s\n' "$status"
      return 0
    fi
    sleep 1
  done
  cat "$wait_log" >&2 2>/dev/null || true
  return 1
}

gemini_v2_write_device_profile() {
  local profile="$1" size="$2" screenshot="$3" hierarchy_status="$4" display_override="$5"
  python3 - "$YUJIAN_EVIDENCE_DIR/android-device" "$profile" "$size" "$screenshot" \
    "$hierarchy_status" "$display_override" "$BUILD_SHA" "$YUJIAN_APP_APK" "$YUJIAN_ADB_BIN" <<'PY'
import hashlib, json, pathlib, subprocess, sys
from PIL import Image
directory, profile, requested_size, screenshot_path, hierarchy_status, display_override, source_sha, apk_path, adb = sys.argv[1:]
def shell(*args):
    return subprocess.check_output([adb, "shell", *args], text=True, timeout=20).strip().replace("\r", "")
screen = pathlib.Path(screenshot_path)
with Image.open(screen) as image:
    dimensions = list(image.size)
payload = {
    "profile_id": profile,
    "execution_mode": "ANDROID_DEVICE_RUNTIME",
    "status": "CAPTURED",
    "api_level": int(shell("getprop", "ro.build.version.sdk")),
    "device_model": shell("getprop", "ro.product.model"),
    "build_fingerprint": shell("getprop", "ro.build.fingerprint"),
    "requested_resolution_px": [int(part) for part in requested_size.split("x")],
    "reported_wm_size": shell("wm", "size"),
    "density_dpi": int(shell("wm", "density").split(":")[-1].strip()),
    "font_scale": float(shell("settings", "get", "system", "font_scale")),
    "display_size_override": display_override == "true",
    "source_commit": source_sha,
    "apk_sha256": hashlib.sha256(pathlib.Path(apk_path).read_bytes()).hexdigest(),
    "screenshot": {
        "path": screen.name,
        "sha256": hashlib.sha256(screen.read_bytes()).hexdigest(),
        "dimensions_px": dimensions,
        "capture": "adb exec-out screencap -p",
    },
    "node_bounds": {
        "source": "Android UIAutomator hierarchy",
        "status": hierarchy_status,
        "path": f"{profile}_bounds.json",
    },
    "interactions": {
        "guest_avatar_login": "PASS_IN_COMPOSE_CALLBACK_TEST; physical-route verification is separately reported",
        "camera_identify": "PASS_IN_COMPOSE_CALLBACK_TEST",
        "catch_record": "PASS_IN_COMPOSE_CALLBACK_TEST",
        "vertical_scroll": "PASS_IN_COMPOSE_CONSTRAINT_TEST",
        "horizontal_carousel": "PASS_IN_COMPOSE_CONSTRAINT_TEST",
    },
}
pathlib.Path(directory, f"{profile}.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(payload, ensure_ascii=False))
PY
}

gate_collect_evidence() {
  local output="$YUJIAN_EVIDENCE_DIR/android-device"
  local app_package="$YUJIAN_APP_PACKAGE"
  mkdir -p "$output"
  "$YUJIAN_ADB_BIN" shell settings put system font_scale 1.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell wm density 480 >/dev/null
  "$YUJIAN_ADB_BIN" shell wm size 1080x1920 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 0.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 0.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 0.0 >/dev/null
  gemini_v2_run_seed seedGeminiReferenceGuestHome || {
    runtime_set_failure "EVIDENCE" "GEMINI_REFERENCE_GUEST_SEED_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  }

  "$YUJIAN_ADB_BIN" shell am force-stop "$app_package"
  "$YUJIAN_ADB_BIN" shell monkey -p "$app_package" -c android.intent.category.LAUNCHER 1 >/dev/null
  gemini_v2_wait_for_app 1080x1920 || {
    runtime_set_failure "EVIDENCE" "GEMINI_HOME_API28_DID_NOT_RENDER_1080X1920"
    return "$EXIT_FAIL_EVIDENCE"
  }
  local bounds_1920
  bounds_1920="$(gemini_v2_wait_for_reference_home api28_1080x1920)" || {
    runtime_set_failure "EVIDENCE" "GEMINI_REFERENCE_HOME_DATA_OR_ROUTE_NOT_VERIFIED_1080X1920"
    return "$EXIT_FAIL_EVIDENCE"
  }
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$output/gemini_home_1080x1920.png" || return "$EXIT_FAIL_EVIDENCE"
  gemini_v2_write_device_profile \
    api28_1080x1920 \
    1080x1920 \
    "$output/gemini_home_1080x1920.png" \
    "$bounds_1920" false || return "$EXIT_FAIL_EVIDENCE"

  "$YUJIAN_ADB_BIN" shell wm size 1080x2340 > "$output/1080x2340_wm_size.log" 2>&1 || return "$EXIT_BLOCKED_INFRA"
  "$YUJIAN_ADB_BIN" shell am force-stop "$app_package"
  "$YUJIAN_ADB_BIN" shell monkey -p "$app_package" -c android.intent.category.LAUNCHER 1 >/dev/null
  gemini_v2_wait_for_app 1080x2340 || {
    runtime_set_failure "EVIDENCE" "GEMINI_HOME_API28_DID_NOT_RENDER_1080X2340"
    return "$EXIT_FAIL_EVIDENCE"
  }
  local bounds_2340
  bounds_2340="$(gemini_v2_wait_for_reference_home api28_1080x2340)" || {
    runtime_set_failure "EVIDENCE" "GEMINI_REFERENCE_HOME_DATA_OR_ROUTE_NOT_VERIFIED_1080X2340"
    return "$EXIT_FAIL_EVIDENCE"
  }
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$output/gemini_home_1080x2340.png" || return "$EXIT_FAIL_EVIDENCE"
  gemini_v2_write_device_profile \
    api28_1080x2340 \
    1080x2340 \
    "$output/gemini_home_1080x2340.png" \
    "$bounds_2340" true || return "$EXIT_FAIL_EVIDENCE"

  "$YUJIAN_ADB_BIN" shell wm size 720x1280 > "$output/720x1280_wm_size.log" 2>&1 || return "$EXIT_BLOCKED_INFRA"
  "$YUJIAN_ADB_BIN" shell wm density 320 > "$output/720x1280_wm_density.log" 2>&1 || return "$EXIT_BLOCKED_INFRA"
  "$YUJIAN_ADB_BIN" shell am force-stop "$app_package"
  "$YUJIAN_ADB_BIN" shell monkey -p "$app_package" -c android.intent.category.LAUNCHER 1 >/dev/null
  gemini_v2_wait_for_app 720x1280 || {
    runtime_set_failure "EVIDENCE" "GEMINI_HOME_API28_DID_NOT_RENDER_720X1280"
    return "$EXIT_FAIL_EVIDENCE"
  }
  local bounds_720
  bounds_720="$(gemini_v2_wait_for_reference_home api28_720x1280)" || {
    runtime_set_failure "EVIDENCE" "GEMINI_REFERENCE_HOME_DATA_OR_ROUTE_NOT_VERIFIED_720X1280"
    return "$EXIT_FAIL_EVIDENCE"
  }
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$output/gemini_home_720x1280.png" || return "$EXIT_FAIL_EVIDENCE"
  gemini_v2_write_device_profile \
    api28_720x1280 \
    720x1280 \
    "$output/gemini_home_720x1280.png" \
    "$bounds_720" true || return "$EXIT_FAIL_EVIDENCE"

  "$YUJIAN_ADB_BIN" shell wm size 1080x1920 >/dev/null 2>&1 || true
  "$YUJIAN_ADB_BIN" shell wm density 480 >/dev/null 2>&1 || true
  "$YUJIAN_ADB_BIN" shell settings put system font_scale 1.0 >/dev/null || true
  "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 1.0 >/dev/null || true
  "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 1.0 >/dev/null || true
  "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 1.0 >/dev/null || true

  if ! YUJIAN_ADB_BIN="$YUJIAN_ADB_BIN" \
    YUJIAN_APP_PACKAGE="$YUJIAN_APP_PACKAGE" \
    YUJIAN_INSTRUMENTATION_TARGET="$YUJIAN_INSTRUMENTATION_TARGET" \
    YUJIAN_EVIDENCE_DIR="$YUJIAN_EVIDENCE_DIR" \
    BUILD_SHA="$BUILD_SHA" \
    GITHUB_RUN_ID="${GITHUB_RUN_ID:-local}" \
    GITHUB_SERVER_URL="${GITHUB_SERVER_URL:-https://github.com}" \
    GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-pan277942135/Yujian_App}" \
    bash scripts/android_runtime/gates/run_nh01_adaptive_device_matrix.sh; then
    runtime_set_failure "EVIDENCE" "NH01_ADAPTIVE_API28_3X3_MATRIX_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  local external="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/nh01-gemini-v3"
  "$YUJIAN_ADB_BIN" pull "$external" "$YUJIAN_EVIDENCE_DIR/compose-simulation" \
    > "$output/compose_evidence_pull.log" 2>&1 || {
      runtime_set_failure "EVIDENCE" "GEMINI_COMPOSE_PROFILE_EVIDENCE_NOT_RETRIEVED"
      return "$EXIT_FAIL_EVIDENCE"
    }
  python3 - "$YUJIAN_EVIDENCE_DIR/compose-simulation" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
compose = root / "compose-simulation/gemini_responsive_compose_report.json"
avatar = root / "avatar/gemini_avatar_transition_report.json"
carousel = root / "compose-simulation/carousel/gemini_catch_carousel_count_report.json"
if not compose.is_file() or not avatar.is_file() or not carousel.is_file():
    raise SystemExit("Gemini Compose, avatar, or carousel report missing")
profile_report = json.loads(compose.read_text(encoding="utf-8"))
avatar_report = json.loads(avatar.read_text(encoding="utf-8"))
carousel_report = json.loads(carousel.read_text(encoding="utf-8"))
profiles = profile_report.get("profiles", [])
if len(profiles) != 15 or any(item.get("status") != "PASS" for item in profiles):
    raise SystemExit("Gemini Compose constraint profile matrix is incomplete or failed")
if any(item.get("interactions", {}).get("guest_avatar_login") != "PASS" for item in profiles):
    raise SystemExit("Guest login click callback did not pass across Compose profiles")
if avatar_report.get("account_switch_clears_previous_avatar") != "PASS" or avatar_report.get("logout_clears_previous_avatar") != "PASS":
    raise SystemExit("Gemini avatar transition checks did not pass")
cases = carousel_report.get("count_cases", [])
if [item.get("catch_count") for item in cases] != [0, 1, 2, 3, 10]:
    raise SystemExit("Gemini carousel count matrix is incomplete")
PY
  local report="$YUJIAN_EVIDENCE_DIR/Gemini_NH01_Responsive_Runtime_Report_V3.json"
  python3 - "$report" "$BUILD_SHA" "$bounds_1920" "$bounds_2340" "$bounds_720" <<'PY'
import json, pathlib, sys
output, source_sha, bounds_1920, bounds_2340, bounds_720 = sys.argv[1:]
payload = {
    "schema": "NH01_GEMINI_RESPONSIVE_RUNTIME_REPORT_V3",
    "source_commit": source_sha,
    "physical_device_profiles": [
        {"profile": "API28_1080x1920", "execution_mode": "ANDROID_DEVICE_RUNTIME", "status": "CAPTURED", "bounds_status": bounds_1920},
        {"profile": "API28_1080x2340", "execution_mode": "ANDROID_DEVICE_RUNTIME", "status": "CAPTURED", "display_size_override": True, "bounds_status": bounds_2340},
        {"profile": "API28_720x1280", "execution_mode": "ANDROID_EMULATOR_DISPLAY_OVERRIDE", "status": "CAPTURED", "display_size_override": True, "density_dpi": 320, "bounds_status": bounds_720},
    ],
    "compose_constraint_simulation": {"report": "compose-simulation/compose-simulation/gemini_responsive_compose_report.json", "status": "PASS", "profiles": 15},
    "avatar_transitions": {"report": "compose-simulation/avatar/gemini_avatar_transition_report.json", "status": "PASS"},
    "finite_carousel": {"report": "compose-simulation/compose-simulation/carousel/gemini_catch_carousel_count_report.json", "status": "PASS", "cases": [0, 1, 2, 3, 10]},
    "reference_business_fixture": {"species_count": 3, "catch_count": 3, "record_days": 1, "species_name": "草鱼", "length_cm": 42.6, "weight_kg": 1.28, "location": "浙江 · 千岛湖", "fish_photo_source": "home_normal/fish_record/sample_recent_catch.jpg"},
    "physical_screenshot_business_state_verified_by": "Android UIAutomator hierarchy text on all three captures",
    "frozen_pixel_parity": "NOT_CLAIMED_INTENTIONAL_GEMINI_V3_LAYOUT",
    "visual_review": "PENDING_SEPARATE_REVIEW",
}
pathlib.Path(output).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

  runtime_set_failure "" ""
  return "$EXIT_PASS"
}
