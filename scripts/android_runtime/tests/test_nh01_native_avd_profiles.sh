#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/avd/test.avd"
cat > "$tmp/avd/test.avd/config.ini" <<'INI'
AvdId=test
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.device.name=pixel_2
INI
export ANDROID_AVD_HOME="$tmp/avd"

for profile in M04 M07 M10; do
  bash "$root/scripts/android_runtime/configure_nh01_api28_avd.sh" test "$profile"
  python3 - "$root/scripts/android_runtime/avd_profiles/nh01_api28_${profile}.ini" "$tmp/avd/test.avd/config.ini" <<'PY'
import pathlib, sys
def values(path):
    return {key.strip(): value.strip() for line in pathlib.Path(path).read_text().splitlines()
            if "=" in line for key, value in [line.split("=", 1)]}
profile, avd = map(values, sys.argv[1:])
expected = {key: profile[key] for key in ("hw.lcd.width", "hw.lcd.height", "hw.lcd.density")}
actual = {key: avd.get(key) for key in expected}
if actual != expected:
    raise SystemExit(f"native AVD config mismatch: expected={expected} actual={actual}")
if avd.get("hw.device.name") != "pixel_2":
    raise SystemExit("the existing Pixel 2 hardware profile must remain intact")
PY
done

if rg -n 'wm size "\$requested"|wm density 160' "$root/scripts/android_runtime/gates/run_nh01_adaptive_device_matrix.sh"; then
  echo "runtime matrix must use pre-boot native display profiles, not post-boot wm overrides" >&2
  exit 1
fi
echo "NH01_NATIVE_AVD_PROFILES=PASS"
