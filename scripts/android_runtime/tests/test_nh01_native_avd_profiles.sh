#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/avd/test.avd" "$tmp/evidence"
cat > "$tmp/avd/test.avd/config.ini" <<'INI'
AvdId=test
hw.device.name=pixel_2
image.sysdir.1=system-images/android-28/default/x86_64/
hw.lcd.width=720
hw.lcd.width=999
hw.lcd.height=1280
hw.lcd.density=420
INI
export ANDROID_AVD_HOME="$tmp/avd"
unset ANDROID_USER_HOME ANDROID_SDK_HOME

for profile in M04 M07 M10; do
  evidence="$tmp/evidence/$profile"
  bash "$root/scripts/android_runtime/configure_nh01_api28_avd.sh" test "$profile" "$evidence" > "$tmp/$profile.log"
  python3 - "$root/scripts/android_runtime/avd_profiles/nh01_api28_${profile}.ini" "$tmp/avd/test.avd/config.ini" "$evidence" "$profile" <<'PY'
import json, pathlib, sys
def entries(path):
    return [(key.strip(), value.strip()) for line in pathlib.Path(path).read_text().splitlines()
            if "=" in line for key, value in [line.split("=", 1)]]
profile_path, avd_path, evidence_path = map(pathlib.Path, sys.argv[1:4])
profile, avd = dict(entries(profile_path)), entries(avd_path)
expected = {key: profile[key] for key in ("hw.lcd.width", "hw.lcd.height", "hw.lcd.density")}
actual = dict(avd)
if {key: actual.get(key) for key in expected} != expected:
    raise SystemExit(f"native AVD config mismatch: expected={expected} actual={actual}")
if any(sum(1 for key, _ in avd if key == target) != 1 for target in expected):
    raise SystemExit("native AVD display keys must be unique after configuration")
if actual.get("hw.device.name") != "pixel_2" or "android-28" not in actual.get("image.sysdir.1", ""):
    raise SystemExit("the API 28 Pixel 2 AVD identity must remain intact")
if not (evidence_path / "config.before.ini").is_file() or not (evidence_path / "config.after.ini").is_file():
    raise SystemExit("configuration write/readback evidence is missing")
metadata = json.loads((evidence_path / "config_metadata.json").read_text())
if metadata["profile_id"] != sys.argv[4] or not metadata["config_path"].endswith("test.avd/config.ini"):
    raise SystemExit("resolved AVD path metadata is incorrect")
PY
done

config="$tmp/avd/test.avd/config.ini"
before="$(sha256sum "$config" | awk '{print $1}')"
bash "$root/scripts/android_runtime/configure_nh01_api28_avd.sh" test M10 "$tmp/evidence/verify" --verify-only >/dev/null
after="$(sha256sum "$config" | awk '{print $1}')"
[[ "$before" == "$after" ]] || { echo "verify-only changed the running AVD config" >&2; exit 1; }
cp "$config" "$tmp/config.good.ini"
sed -i 's/^hw.lcd.width=430$/hw.lcd.width=431/' "$config"
before="$(sha256sum "$config" | awk '{print $1}')"
if bash "$root/scripts/android_runtime/configure_nh01_api28_avd.sh" test M10 "$tmp/evidence/verify-mismatch" --verify-only >/dev/null 2>&1; then
  echo "verify-only accepted a mismatched AVD config" >&2
  exit 1
fi
after="$(sha256sum "$config" | awk '{print $1}')"
[[ "$before" == "$after" ]] || { echo "failed verify-only altered the AVD config" >&2; exit 1; }
cp "$tmp/config.good.ini" "$config"

matrix="$root/scripts/android_runtime/gates/run_nh01_adaptive_device_matrix.sh"
workflow="$root/.github/workflows/nh01-cloud-runtime.yml"
metadata="$root/scripts/design/capture_nh01_device_metadata.py"
runtime="$root/scripts/design/run_nh01_emulator_runtime.sh"
if ! rg -q 'settings put system font_scale 1\.0' "$runtime" ||
   ! rg -q 'settings delete system font_scale' "$runtime" ||
   ! rg -q 'font_scale_restored=' "$runtime" ||
   ! rg -q '\|\| metadata_rc=\$\?' "$runtime"; then
  echo "initial metadata must capture the target FontScale and restore the preexisting setting" >&2
  exit 1
fi
if rg -n 'wm size (reset|[0-9]+x[0-9]+)|wm density (reset|[0-9]+)([[:space:]";]|$)|-skin|-dpi-device' "$matrix" "$workflow"; then
  echo "runtime must not mutate WindowManager display size or rely on deprecated skin/dpi flags" >&2
  exit 1
fi
if rg -n 'wm size", "1080x1920|wm density", "480|wm size 1080x1920|wm density 480|1080x1920@480' "$metadata" "$root/scripts/design/tests/test_nh01_device_metadata.py"; then
  echo "legacy display contract remains in the metadata path" >&2
  exit 1
fi
echo "NH01_NATIVE_AVD_PROFILES=PASS"
