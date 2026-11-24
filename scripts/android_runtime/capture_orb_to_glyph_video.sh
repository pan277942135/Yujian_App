#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: capture_orb_to_glyph_video.sh --serial SERIAL --attempt-id ID --snapshot-id ID \
  --kf-index-json FILE --output VIDEO.mp4 [--duration-seconds 180]

Launch the production app and start the recognition flow on the selected device before
running this command. The script records the real device display with adb screenrecord,
then validates the decoded video and writes VIDEO.mp4.metadata.json. KF14-KF20 timestamps
must come from the same attempt's runtime event trace and be expressed in milliseconds
from the beginning of the captured video.
EOF
}

serial=""
attempt_id=""
snapshot_id=""
kf_index_json=""
output=""
duration_seconds=180
while (($#)); do
  case "$1" in
    --serial) serial="${2:?missing serial}"; shift 2 ;;
    --attempt-id) attempt_id="${2:?missing attempt id}"; shift 2 ;;
    --snapshot-id) snapshot_id="${2:?missing snapshot id}"; shift 2 ;;
    --kf-index-json) kf_index_json="${2:?missing KF index json}"; shift 2 ;;
    --output) output="${2:?missing output path}"; shift 2 ;;
    --duration-seconds) duration_seconds="${2:?missing duration}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 2 ;;
  esac
done

[[ -n "$serial" && -n "$attempt_id" && -n "$snapshot_id" && -n "$kf_index_json" && -n "$output" ]] || { usage; exit 2; }
[[ -f "$kf_index_json" ]] || { echo "KF index JSON does not exist: $kf_index_json" >&2; exit 2; }
[[ "$duration_seconds" =~ ^[0-9]+$ ]] && ((duration_seconds > 0 && duration_seconds <= 180)) || {
  echo "duration must be an integer from 1 to 180 seconds" >&2; exit 2;
}
command -v adb >/dev/null || { echo "adb is required" >&2; exit 127; }
command -v ffprobe >/dev/null || { echo "ffprobe is required" >&2; exit 127; }
command -v ffmpeg >/dev/null || { echo "ffmpeg is required" >&2; exit 127; }

device_state="$(adb -s "$serial" get-state 2>/dev/null || true)"
[[ "$device_state" == "device" ]] || { echo "selected device is not online: $serial" >&2; exit 3; }
mkdir -p "$(dirname "$output")"
output="$(cd "$(dirname "$output")" && pwd)/$(basename "$output")"
remote="/sdcard/Movies/yujian-orb-to-glyph-$(date +%s).mp4"
trap 'adb -s "$serial" shell rm -f "$remote" >/dev/null 2>&1 || true' EXIT

echo "Recording the real device screen on $serial for at most ${duration_seconds}s."
adb -s "$serial" shell screenrecord --time-limit "$duration_seconds" "$remote"
adb -s "$serial" pull "$remote" "$output" >/dev/null
[[ -s "$output" ]] || { echo "screenrecord produced an empty file" >&2; exit 4; }

python3 "$(dirname "$0")/verify_orb_to_glyph_video.py" \
  --video "$output" \
  --attempt-id "$attempt_id" \
  --snapshot-id "$snapshot_id" \
  --kf-index-json "$kf_index_json" \
  --write-metadata "${output}.metadata.json"
echo "VIDEO=$output"
echo "METADATA=${output}.metadata.json"
