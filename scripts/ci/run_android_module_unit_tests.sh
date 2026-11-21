#!/usr/bin/env bash
set -uo pipefail

if [[ $# -ne 1 || -z "$1" ]]; then
  echo "usage: $0 ANDROID_AAPT2_PATH" >&2
  exit 64
fi

aapt2_path="$1"
gradle_bin="${GRADLE_BIN:-gradle}"
diagnostics_dir="${GITHUB_WORKSPACE:-$(pwd)}/build/diagnostics"
mkdir -p "$diagnostics_dir"
results_file="$diagnostics_dir/unit-module-results.txt"
: > "$results_file"

module_tasks=(
  "recognition-domain|:recognition-domain:test"
  "recognition-runtime|:recognition-runtime:test"
  "application-core|:application-core:testDebugUnitTest"
  "waterlight-renderer|:waterlight-renderer:testDebugUnitTest"
  "recognition-ui|:recognition-ui:testDebugUnitTest"
  "recognition-result-ui|:recognition-result-ui:testDebugUnitTest"
  "app|:app:testDebugUnitTest"
)

failed=0
for item in "${module_tasks[@]}"; do
  module="${item%%|*}"
  task="${item#*|}"
  label="unit-${module}"
  echo "=== MODULE_UNIT_TEST_START module=${module} task=${task} ==="

  if bash scripts/ci/with-gradle-memory-diagnostics.sh "$label" \
      "$gradle_bin" \
      "-Pandroid.aapt2FromMavenOverride=${aapt2_path}" \
      "$task" \
      --console=plain --stacktrace --info --profile --no-daemon --max-workers=1; then
    result="PASS"
  else
    result="FAIL"
    failed=1
  fi

  printf '%s|%s|%s\n' "$module" "$task" "$result" | tee -a "$results_file"
  echo "=== MODULE_UNIT_TEST_END module=${module} result=${result} ==="
done

if (( failed != 0 )); then
  echo "One or more source module unit-test tasks failed; see $results_file" >&2
  exit 1
fi

echo "All source module unit-test tasks passed; see $results_file"
