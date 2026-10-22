#!/usr/bin/env bash
# Independent Hi-Fi evidence. Auth/Recognition failures must not hide snapshots.
gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.ui.hifi.HiFiPagesRuntimeTest'
}

gate_collect_evidence() {
  local out="$YUJIAN_EVIDENCE_DIR/hifi-pages"
  local remote="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/login_v2/hifi-pages"
  mkdir -p "$out"
  if ! "$YUJIAN_ADB_BIN" pull "$remote/." "$out/" > "$out/adb_pull.log" 2>&1; then
    runtime_set_failure "EVIDENCE" "HIFI_PAGES_PULL_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  local file
  for file in record_detail_a.png record_detail_a.png.provenance.json \
              my_catches_timeline.png my_catches_timeline.png.provenance.json; do
    if [[ ! -s "$out/$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$out/$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "HIFI_PAGES_MISSING"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  python3 - "$out" "$YUJIAN_BUILD_SHA" "$YUJIAN_APP_PACKAGE" <<'PY'
import hashlib, json, pathlib, sys
out, commit, package = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
for name in ("record_detail_a.png", "my_catches_timeline.png"):
    path = out / name
    proof = json.loads((out / (name + ".provenance.json")).read_text())
    assert proof.get("build_sha") == commit, "BUILD_SHA_MISMATCH"
    assert proof.get("package") == package, "PACKAGE_MISMATCH"
    assert proof.get("resumed_activity", "").startswith(package + "/"), "WRONG_FOREGROUND"
    assert proof.get("foreground_verified") is True and proof.get("activity_window_focus") is True, "FOREGROUND_NOT_PROVEN"
    assert proof.get("test_assertions_passed") is True, "NO_COMPOSE_ASSERTIONS"
    assert proof.get("capture_method") == "instrumentation-ui-automation", "WRONG_CAPTURE_METHOD"
    assert hashlib.sha256(path.read_bytes()).hexdigest() == proof.get("screenshot_sha256"), "CAPTURE_HASH_MISMATCH"
    print("HIFI_PAGE_EVIDENCE_PASS", name)
PY
}
