#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="${1:-}"
DEST="$ROOT/app/src/main/assets/fish_classifier.tflite"
EXPECTED_SHA="5bb77f0bea96be2c6d2ace8a0fea36e8907bc9e4076beac05e0c82f44c345459"
EXPECTED_SIZE="2077712"

if [[ -z "$SOURCE" || ! -f "$SOURCE" ]]; then
  echo "Usage: $0 /path/to/fish_classifier.tflite" >&2
  exit 2
fi

SIZE="$(wc -c < "$SOURCE" | tr -d ' ')"
SHA="$(sha256sum "$SOURCE" | awk '{print $1}')"

[[ "$SIZE" == "$EXPECTED_SIZE" ]] || { echo "wrong model size: $SIZE" >&2; exit 1; }
[[ "$SHA" == "$EXPECTED_SHA" ]] || { echo "wrong model sha256: $SHA" >&2; exit 1; }

mkdir -p "$(dirname "$DEST")"
cp "$SOURCE" "$DEST"
python3 "$ROOT/scripts/verify_production_model.py"

echo "Installed exact production model at app/src/main/assets/fish_classifier.tflite"
