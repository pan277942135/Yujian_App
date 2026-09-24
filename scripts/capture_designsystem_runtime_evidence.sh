#!/usr/bin/env bash
set -euo pipefail

# Capture the debug-only Core UI V1 gallery from a connected Android device.
# This script intentionally launches the native Compose preview activity; it
# never uses the frozen page reference PNGs as runtime UI.

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
OUTPUT_DIR="${2:-build/designsystem-evidence}"
PACKAGE_NAME="com.yujian.ai.uiv2"
ACTIVITY_NAME="${PACKAGE_NAME}/com.yujian.ai.ui.designsystem.DesignSystemPreviewActivity"
ADB_BIN="${ADB:-adb}"

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

if [[ "${SKIP_ADB_INSTALL:-0}" != "1" ]]; then
  "$ADB_BIN" install -r "$APK_PATH"
fi

mkdir -p "$OUTPUT_DIR"

for setting in animator_duration_scale transition_animation_scale window_animation_scale; do
  "$ADB_BIN" shell settings put global "$setting" 1.0 || true
done

capture_section() {
  local section="$1"
  local output="$OUTPUT_DIR/${section}.png"

  "$ADB_BIN" shell am force-stop "$PACKAGE_NAME"
  "$ADB_BIN" shell am start -n "$ACTIVITY_NAME" --es section "$section" >/dev/null
  # The gallery is deterministic after the first Compose frame. CI emulators
  # have hardware acceleration; the extra second keeps screenshots stable on
  # slower Android 9 runners as well.
  sleep "${DESIGNSYSTEM_CAPTURE_SETTLE_SECONDS:-3}"
  # Some software-rendered local API 28 images show an emulator-side System UI
  # ANR dialog while the first Compose frame is still being drawn. It is not
  # part of the app evidence, so dismiss it when requested before the capture.
  if [[ "${DESIGNSYSTEM_DISMISS_SYSTEM_DIALOGS:-0}" == "1" ]]; then
    # The software-rendered API 28 image places the dialog's Wait action near
    # the center of the 1080x1920 reference display. Tapping this coordinate is
    # harmless when the dialog is absent and avoids recording emulator chrome.
    "$ADB_BIN" shell input tap 340 1060 || true
    sleep 1
    if ! "$ADB_BIN" shell dumpsys activity activities | grep -q 'DesignSystemPreviewActivity'; then
      "$ADB_BIN" shell am start -n "$ACTIVITY_NAME" --es section "$section" >/dev/null
      sleep 3
    fi
  fi
  "$ADB_BIN" exec-out screencap -p > "$output"
}

for section in colors typography glass components all; do
  capture_section "$section"
done

cp "$OUTPUT_DIR/all.png" "$OUTPUT_DIR/designsystem_preview.png"
cp "$OUTPUT_DIR/components.png" "$OUTPUT_DIR/component_gallery.png"

"$ADB_BIN" shell am force-stop "$PACKAGE_NAME"
"$ADB_BIN" shell am start -n "$ACTIVITY_NAME" --es section components >/dev/null
sleep "${DESIGNSYSTEM_CAPTURE_SETTLE_SECONDS:-3}"
"$ADB_BIN" shell screenrecord --time-limit "${DESIGNSYSTEM_MOTION_SECONDS:-8}" /sdcard/yujian_designsystem_motion.mp4
"$ADB_BIN" pull /sdcard/yujian_designsystem_motion.mp4 "$OUTPUT_DIR/motion_preview.mp4" >/dev/null
"$ADB_BIN" shell rm /sdcard/yujian_designsystem_motion.mp4 || true

{
  printf '{\n  "package": "%s",\n  "activity": "%s",\n  "files": {\n' "$PACKAGE_NAME" "$ACTIVITY_NAME"
  first=1
  for file in colors.png typography.png glass.png components.png all.png designsystem_preview.png component_gallery.png motion_preview.mp4; do
    [[ -f "$OUTPUT_DIR/$file" ]] || continue
    if [[ "$first" -eq 0 ]]; then printf ',\n'; fi
    first=0
    printf '    "%s": "%s"' "$file" "$(sha256sum "$OUTPUT_DIR/$file" | cut -d' ' -f1)"
  done
  printf '\n  }\n}\n'
} > "$OUTPUT_DIR/manifest.json"

echo "Design System runtime evidence written to $OUTPUT_DIR"
