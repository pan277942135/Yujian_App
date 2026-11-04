#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 2 || "$#" -gt 4 ]]; then
  echo "usage: $0 <avd-name> <M04|M07|M10> [evidence-dir] [--verify-only]" >&2
  exit 2
fi

avd_name="$1"
profile="$2"
case "$profile" in M04|M07|M10) ;; *) echo "unsupported profile: $profile" >&2; exit 2 ;; esac
profile_config="$(cd "$(dirname "$0")" && pwd)/avd_profiles/nh01_api28_${profile}.ini"
evidence_dir="${3:-${NH01_AVD_CONFIG_EVIDENCE_DIR:-}}"
verify_only=false
if [[ "${4:-}" == "--verify-only" ]]; then verify_only=true; fi
if [[ "${3:-}" == "--verify-only" ]]; then evidence_dir=""; verify_only=true; fi

if [[ ! -f "$profile_config" ]]; then
  echo "Native display profile not found: $profile_config" >&2
  exit 4
fi

candidates=()
if [[ -n "${ANDROID_AVD_HOME:-}" ]]; then
  candidates+=("$ANDROID_AVD_HOME")
elif [[ -n "${ANDROID_USER_HOME:-}" ]]; then
  candidates+=("$ANDROID_USER_HOME/avd")
elif [[ -n "${ANDROID_SDK_HOME:-}" ]]; then
  if [[ "$ANDROID_SDK_HOME" == */.android ]]; then
    candidates+=("$ANDROID_SDK_HOME/avd")
  else
    candidates+=("$ANDROID_SDK_HOME/.android/avd" "$ANDROID_SDK_HOME/avd")
  fi
  candidates+=("$HOME/.android/avd")
else
  candidates+=("$HOME/.android/avd")
fi

avd_root=""
for candidate in "${candidates[@]}"; do
  if [[ -f "$candidate/$avd_name.avd/config.ini" ]]; then
    avd_root="$candidate"
    break
  fi
done
if [[ -z "$avd_root" ]]; then
  printf 'AVD config not found for %s. Candidate roots: %s\n' "$avd_name" "${candidates[*]}" >&2
  printf 'ANDROID_AVD_HOME=%s\nANDROID_USER_HOME=%s\nANDROID_SDK_HOME=%s\nHOME=%s\n' \
    "${ANDROID_AVD_HOME:-}" "${ANDROID_USER_HOME:-}" "${ANDROID_SDK_HOME:-}" "$HOME" >&2
  exit 3
fi
config="$avd_root/$avd_name.avd/config.ini"

if [[ -n "$evidence_dir" ]]; then
  mkdir -p "$evidence_dir"
  cp "$config" "$evidence_dir/config.before.ini"
  cp "$profile_config" "$evidence_dir/profile.ini"
fi

python3 - "$config" "$profile_config" "$profile" "$verify_only" <<'PY'
import hashlib
import pathlib
import sys

path, profile_path = map(pathlib.Path, sys.argv[1:3])
profile = sys.argv[3]
verify_only = sys.argv[4] == "true"
keys = ("hw.lcd.width", "hw.lcd.height", "hw.lcd.density")
expected = {}
for line in profile_path.read_text(encoding="utf-8").splitlines():
    stripped = line.strip()
    if not stripped or stripped.startswith("#"):
        continue
    key, separator, value = line.partition("=")
    key = key.strip()
    if not separator or key not in keys or key in expected:
        raise SystemExit(f"invalid or duplicate native display profile key: {line!r}")
    expected[key] = value.strip()
if set(expected) != set(keys):
    raise SystemExit(f"native display profile is incomplete: {profile_path}")

before = path.read_text(encoding="utf-8")
lines = before.splitlines()
if verify_only:
    after = before
else:
    out = []
    inserted = set()
    for line in lines:
        key = line.partition("=")[0].strip()
        if key in expected:
            if key not in inserted:
                out.append(f"{key}={expected[key]}")
                inserted.add(key)
            continue
        out.append(line)
    for key in keys:
        if key not in inserted:
            out.append(f"{key}={expected[key]}")
    after = "\n".join(out) + "\n"
    path.write_text(after, encoding="utf-8")
counts = {key: sum(1 for line in after.splitlines() if line.partition("=")[0].strip() == key) for key in keys}
actual = {}
for line in after.splitlines():
    key, separator, value = line.partition("=")
    if separator and key.strip() in keys:
        actual[key.strip()] = value.strip()
if actual != expected or any(counts[key] != 1 for key in keys):
    action = "verify" if verify_only else "write/readback"
    raise SystemExit(f"AVD native display {action} mismatch: expected={expected} actual={actual} counts={counts}")
print(f"AVD_CONFIG_MODE={'VERIFY_ONLY' if verify_only else 'WRITE'}")
print(f"AVD_CONFIG_BEFORE_SHA256={hashlib.sha256(before.encode()).hexdigest()}")
print(f"AVD_CONFIG_AFTER_SHA256={hashlib.sha256(after.encode()).hexdigest()}")
print(f"AVD_NATIVE_DISPLAY_READBACK={profile} {expected}")
PY

if [[ -n "$evidence_dir" ]]; then
  cp "$config" "$evidence_dir/config.after.ini"
  python3 - "$evidence_dir/config.before.ini" "$config" "$profile" "$avd_name" "$avd_root" "$evidence_dir/config_metadata.json" <<'PY'
import hashlib, json, pathlib, sys
before_path, after_path = map(pathlib.Path, sys.argv[1:3])
profile, avd_name, avd_root, output = sys.argv[3], sys.argv[4], sys.argv[5], pathlib.Path(sys.argv[6])
before, after = before_path.read_bytes(), after_path.read_bytes()
payload = {
    "profile_id": profile,
    "avd_root": avd_root,
    "config_path": str(pathlib.Path(avd_root) / f"{avd_name}.avd" / "config.ini"),
    "config_before_sha256": hashlib.sha256(before).hexdigest(),
    "config_after_sha256": hashlib.sha256(after).hexdigest(),
    "config_before_evidence": str(before_path),
    "config_after_evidence": str(after_path),
}
output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
PY
fi

printf 'AVD_NAME=%s\nAVD_ROOT=%s\nAVD_CONFIG_PATH=%s\nAVD_PROFILE=%s\n' \
  "$avd_name" "$avd_root" "$config" "$profile"
printf 'ANDROID_AVD_HOME=%s\nANDROID_USER_HOME=%s\nANDROID_SDK_HOME=%s\nANDROID_HOME=%s\n' \
  "${ANDROID_AVD_HOME:-}" "${ANDROID_USER_HOME:-}" "${ANDROID_SDK_HOME:-}" "${ANDROID_HOME:-}"
printf 'AVD_CONFIG_CANDIDATES=%s\n' "${candidates[*]}"
printf '%s\n' '--- AVD config display readback ---'
awk -F= '$1 ~ /^hw\.lcd\.(width|height|density)$/ {print}' "$config"
printf '%s\n' '--- AVD identity readback ---'
awk -F= '$1 ~ /^(AvdId|hw\.device\.name|image\.sysdir\.1|target)$/ {print}' "$config"
