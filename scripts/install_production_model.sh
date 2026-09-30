#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "$#" -ne 0 ]]; then
  echo "The production classifier is no longer installed from a manually pinned file." >&2
  echo "The canonical channel is pan277942135/Yujian@mobile-model-v0.2." >&2
  echo "Usage: $0" >&2
  exit 2
fi

python3 "$ROOT/scripts/bootstrap_android_recognition_assets.py"

echo "Installed and verified the current mobile-model-v0.2 production classifier."
