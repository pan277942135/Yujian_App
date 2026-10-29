#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

python3 - "$tmp/physical.png" "$tmp/wrong.png" <<'PY'
from PIL import Image
import pathlib, sys
Image.new("RGB", (8, 8), (25, 80, 120)).save(pathlib.Path(sys.argv[1]))
Image.new("RGB", (7, 8), (25, 80, 120)).save(pathlib.Path(sys.argv[2]))
PY

cat > "$tmp/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == "shell" && "$2" == "wm" && "$3" == "size" ]]; then
  printf 'Physical size: 8x8\nOverride size: 2x2\n'
elif [[ "$1" == "exec-out" && "$2" == "screencap" && "$3" == "-p" ]]; then
  cat "$NH01_FAKE_SCREEN"
else
  echo "unexpected fake adb command: $*" >&2
  exit 2
fi
ADB
chmod +x "$tmp/adb"

export NH01_FAKE_SCREEN="$tmp/physical.png"
export YUJIAN_ADB_BIN="$tmp/adb"
source "$root/scripts/android_runtime/gates/gemini_home_v2.sh"
sleep() { :; }

mkdir -p "$tmp/pass" "$tmp/logical-fail" "$tmp/framebuffer-fail"
gemini_v2_verify_display_profile "2x2" "$tmp/pass"
grep -q '^expected_logical_size=2x2$' "$tmp/pass/display_profile_2x2.log"
grep -q '^physical_size=8x8$' "$tmp/pass/display_profile_2x2.log"
grep -q '^override_size=2x2$' "$tmp/pass/display_profile_2x2.log"
grep -q '^logical_size=2x2$' "$tmp/pass/display_profile_2x2.log"
grep -q '^screencap_size=8x8$' "$tmp/pass/display_profile_2x2.log"
grep -q '^expected_physical_size=8x8$' "$tmp/pass/display_profile_2x2.log"

if gemini_v2_verify_display_profile "3x2" "$tmp/logical-fail"; then
  echo "expected a wrong logical display override to fail verification" >&2
  exit 1
fi
grep -q '^logical_display_size_mismatch=true$' "$tmp/logical-fail/display_profile_3x2.log"

export NH01_FAKE_SCREEN="$tmp/wrong.png"
if gemini_v2_verify_display_profile "2x2" "$tmp/framebuffer-fail"; then
  echo "expected a screencap that differs from the physical framebuffer to fail" >&2
  exit 1
fi
grep -q '^screencap_size=7x8$' "$tmp/framebuffer-fail/display_profile_2x2.log"
grep -q '^screencap_physical_size_mismatch=true$' "$tmp/framebuffer-fail/display_profile_2x2.log"
echo "NH01_DISPLAY_PROFILE_VERIFICATION=PASS"
