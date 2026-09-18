#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="${1:-}"
CONTRACT_SOURCE="${2:-}"
DEST="$ROOT/app/src/main/assets/fish_classifier.tflite"
CONTRACT_DEST="$ROOT/app/src/main/assets/model_tensor_contract.json"
EXPECTED_SHA="b77ea78e7f8554078ea3a79051039af1ace04f0ac4e2604da57d1dd8f0b010e7"
EXPECTED_SIZE="6249008"

if [[ -z "$SOURCE" || ! -f "$SOURCE" || -z "$CONTRACT_SOURCE" || ! -f "$CONTRACT_SOURCE" ]]; then
  echo "Usage: $0 /path/to/fish_classifier.tflite /path/to/tensor_contract.json" >&2
  exit 2
fi

SIZE="$(wc -c < "$SOURCE" | tr -d ' ')"
SHA="$(sha256sum "$SOURCE" | awk '{print $1}')"

[[ "$SIZE" == "$EXPECTED_SIZE" ]] || { echo "wrong model size: $SIZE" >&2; exit 1; }
[[ "$SHA" == "$EXPECTED_SHA" ]] || { echo "wrong model sha256: $SHA" >&2; exit 1; }

mkdir -p "$(dirname "$DEST")"
cp "$SOURCE" "$DEST"
cp "$CONTRACT_SOURCE" "$CONTRACT_DEST"
python3 "$ROOT/scripts/verify_production_model.py"

echo "Installed exact production model at app/src/main/assets/fish_classifier.tflite"
