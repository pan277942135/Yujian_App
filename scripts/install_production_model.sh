#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ $# -ne 0 ]]; then
  echo "Usage: $0" >&2
  exit 2
fi

python3 "$ROOT/scripts/bootstrap_android_recognition_assets.py"
echo "Installed and verified the current production model Release snapshot."
