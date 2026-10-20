#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

python3 - "$tmp/frame.png" <<'PY'
from PIL import Image
import pathlib, sys
Image.new("RGB", (2, 2), (25, 80, 120)).save(pathlib.Path(sys.argv[1]))
PY

cat > "$tmp/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == "shell" && "$2" == "wm" && "$3" == "size" ]]; then
  printf 'Physical size: 1080x1920\nOverride size: 2x2\n'
elif [[ "$1" == "exec-out" && "$2" == "screencap" && "$3" == "-p" ]]; then
  cat "$NH01_FAKE_SCREEN"
else
  echo "unexpected fake adb command: $*" >&2
  exit 2
fi
ADB
chmod +x "$tmp/adb"

export NH01_FAKE_SCREEN="$tmp/frame.png"
export YUJIAN_ADB_BIN="$tmp/adb"
source "$root/scripts/android_runtime/gates/gemini_home_v2.sh"
sleep() { :; }

mkdir -p "$tmp/pass" "$tmp/fail"
gemini_v2_verify_display_profile "2x2" "$tmp/pass"
if gemini_v2_verify_display_profile "3x2" "$tmp/fail"; then
  echo "expected an unsupported display profile to fail verification" >&2
  exit 1
fi
grep -q '^expected_size=3x2$' "$tmp/fail/display_profile_3x2.log"
grep -q '^screencap_size=2x2$' "$tmp/fail/display_profile_3x2.log"
echo "NH01_DISPLAY_PROFILE_VERIFICATION=PASS"
