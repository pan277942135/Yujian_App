#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "usage: $0 <avd-name> <profile>" >&2
  exit 2
fi

avd_name="$1"
profile="$2"
avd_root="${ANDROID_AVD_HOME:-${ANDROID_SDK_HOME:-$HOME/.android}/avd}"
config="$avd_root/$avd_name.avd/config.ini"
profile_config="$(dirname "$0")/avd_profiles/nh01_api28_${profile}.ini"

if [[ ! -f "$config" ]]; then
  echo "AVD config not found: $config" >&2
  exit 3
fi
if [[ ! -f "$profile_config" ]]; then
  echo "Native display profile not found: $profile_config" >&2
  exit 4
fi

python3 - "$config" "$profile_config" "$profile" <<'PY'
import pathlib
import sys

path, profile_path = map(pathlib.Path, sys.argv[1:3])
profile = sys.argv[3]
updates = {}
for line in profile_path.read_text(encoding="utf-8").splitlines():
    if not line.strip() or line.lstrip().startswith("#"):
        continue
    key, separator, value = line.partition("=")
    if not separator or key not in {"hw.lcd.width", "hw.lcd.height", "hw.lcd.density"}:
        raise SystemExit(f"invalid native display profile line: {line!r}")
    updates[key.strip()] = value.strip()
if set(updates) != {"hw.lcd.width", "hw.lcd.height", "hw.lcd.density"}:
    raise SystemExit(f"native display profile is incomplete: {profile_path}")
seen = set()
lines = []
for line in path.read_text(encoding="utf-8").splitlines():
    key = line.partition("=")[0].strip()
    if key in updates:
        lines.append(f"{key}={updates[key]}")
        seen.add(key)
    else:
        lines.append(line)
for key, value in updates.items():
    if key not in seen:
        lines.append(f"{key}={value}")
temporary = path.with_suffix(".ini.tmp")
temporary.write_text("\n".join(lines) + "\n", encoding="utf-8")
temporary.replace(path)
print("AVD_NATIVE_DISPLAY profile={} width={} height={} density={} config={} source={}".format(
    profile, updates["hw.lcd.width"], updates["hw.lcd.height"], updates["hw.lcd.density"], path, profile_path))
PY
