#!/usr/bin/env bash
set -euo pipefail
bash scripts/android_runtime/startup_smoke.sh
test "$(cat startup-evidence/STATUS)" = PASS
test "$(cat startup-evidence/RESOURCE_DECODE_STATUS)" = PASS
test -s startup-evidence/relaunch-pid.txt
! grep -q 'com.yujian.ai.uiv2' startup-evidence/relaunch-crash.txt
! grep -q '启动诊断' startup-evidence/startup.xml
python3 scripts/android_runtime/capture_v2_evidence.py
