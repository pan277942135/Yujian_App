#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-evidence
apk=$(find startup-input -name 'YuJian-debug.apk' -print -quit)
test -n "$apk"
sha256sum "$apk" | tee startup-evidence/APK_SHA256.txt
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n com.yujian.ai.uiv2/com.yujian.ai.MainActivity | tee startup-evidence/launch.txt
sleep 12
adb logcat -d -b crash > startup-evidence/crash.txt
adb logcat -d > startup-evidence/logcat.txt
adb shell pidof com.yujian.ai.uiv2 > startup-evidence/pid.txt || true
adb shell uiautomator dump /sdcard/startup.xml || true
adb pull /sdcard/startup.xml startup-evidence/startup.xml || true
adb exec-out screencap -p > startup-evidence/startup.png
if test -s startup-evidence/pid.txt && ! grep -q 'com.yujian.ai.uiv2' startup-evidence/crash.txt; then echo PASS > startup-evidence/STATUS; else echo FAIL > startup-evidence/STATUS; fi
adb shell am force-stop com.yujian.ai.uiv2
adb shell am start -W -n com.yujian.ai.uiv2/com.yujian.ai.MainActivity > startup-evidence/relaunch.txt
sleep 5
adb shell pidof com.yujian.ai.uiv2 > startup-evidence/relaunch-pid.txt || true
adb logcat -d -b crash > startup-evidence/relaunch-crash.txt

if test -d startup-tests; then
  test_apk=$(find startup-tests -name 'app-debug-androidTest.apk' -print -quit)
  test -n "$test_apk"
  adb install -r "$test_apk"
  adb shell am instrument -w -e class com.yujian.ai.CapturePresentationAssetRuntimeTest com.yujian.ai.uiv2.test/androidx.test.runner.AndroidJUnitRunner | tee startup-evidence/resource-decode.txt
  if grep -q 'OK (1 test)' startup-evidence/resource-decode.txt && ! grep -q 'FAILURES!!!\|INSTRUMENTATION_FAILED' startup-evidence/resource-decode.txt; then
    echo PASS > startup-evidence/RESOURCE_DECODE_STATUS
  else
    echo FAIL > startup-evidence/RESOURCE_DECODE_STATUS
  fi
fi
