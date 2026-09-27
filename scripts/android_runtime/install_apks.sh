#!/usr/bin/env bash

android_runtime_install_apks() {
  local app_apk="$1"
  local test_apk="$2"
  mkdir -p "$YUJIAN_INFRA_DIR"

  if [[ ! -s "$app_apk" || ! -s "$test_apk" ]]; then
    runtime_set_failure "INSTALL" "APK_FILE_MISSING"
    return "$EXIT_FAIL_ARTIFACT"
  fi

  # Only the documented package-not-installed responses are tolerated.  Any
  # other uninstall error is an infrastructure failure and is not swallowed.
  local uninstall_rc
  timeout 30s "${YUJIAN_ADB_BIN}" uninstall "$YUJIAN_APP_PACKAGE" > "$YUJIAN_INFRA_DIR/uninstall_app.log" 2>&1
  uninstall_rc=$?
  if (( uninstall_rc == 124 )); then
    runtime_set_failure "INSTALL" "ADB_UNINSTALL_APP_TIMEOUT"
    return "$EXIT_BLOCKED_INFRA"
  fi
  if (( uninstall_rc != 0 )) && ! grep -Eiq 'Unknown package|not installed|DELETE_FAILED_INTERNAL_ERROR' "$YUJIAN_INFRA_DIR/uninstall_app.log"; then
    runtime_set_failure "INSTALL" "ADB_UNINSTALL_APP_FAILED"
    return "$EXIT_BLOCKED_INFRA"
  fi
  timeout 30s "${YUJIAN_ADB_BIN}" uninstall "$YUJIAN_TEST_PACKAGE" > "$YUJIAN_INFRA_DIR/uninstall_test.log" 2>&1
  uninstall_rc=$?
  if (( uninstall_rc == 124 )); then
    runtime_set_failure "INSTALL" "ADB_UNINSTALL_TEST_TIMEOUT"
    return "$EXIT_BLOCKED_INFRA"
  fi
  if (( uninstall_rc != 0 )) && ! grep -Eiq 'Unknown package|not installed|DELETE_FAILED_INTERNAL_ERROR' "$YUJIAN_INFRA_DIR/uninstall_test.log"; then
    runtime_set_failure "INSTALL" "ADB_UNINSTALL_TEST_FAILED"
    return "$EXIT_BLOCKED_INFRA"
  fi

  local install_retry=0 app_rc test_rc verify_rc app_log test_log verify_log
  app_log="$YUJIAN_INFRA_DIR/install_app.log"
  test_log="$YUJIAN_INFRA_DIR/install_test.log"
  verify_log="$YUJIAN_INFRA_DIR/install_verify.log"
  : > "$app_log"
  : > "$test_log"
  : > "$verify_log"

  while true; do
    printf 'INSTALL_ATTEMPT=%s APK=%s\n' "$((install_retry + 1))" "$app_apk" >> "$app_log"
    timeout 120s "${YUJIAN_ADB_BIN}" install -r -t "$app_apk" >> "$app_log" 2>&1
    app_rc=$?
    if (( app_rc == 124 )); then
      printf 'INSTALL_TIMEOUT=120s\n' >> "$app_log"
    fi
    if (( app_rc == 0 )); then
      break
    fi

    if (( app_rc == 124 )) || runtime_is_transport_failure_file "$app_log"; then
      if (( install_retry >= 1 )); then
        if (( app_rc == 124 )); then
          runtime_set_failure "INSTALL" "ADB_INSTALL_TIMEOUT"
        else
          runtime_set_failure "INSTALL" "ADB_TRANSPORT_DURING_INSTALL"
        fi
        return "$EXIT_BLOCKED_INFRA"
      fi
      install_retry=1
      runtime_restart_adb_once >/dev/null 2>&1 || true
      if ! runtime_wait_for_device; then
        runtime_set_failure "INSTALL" "ADB_TRANSPORT_DURING_INSTALL"
        return "$EXIT_BLOCKED_INFRA"
      fi
      continue
    fi
    if runtime_is_storage_failure_file "$app_log"; then
      runtime_set_failure "INSTALL" "INSUFFICIENT_STORAGE"
      return "$EXIT_BLOCKED_INFRA"
    fi
    if runtime_is_install_artifact_failure_file "$app_log" || grep -Eiq 'Failure \[' "$app_log"; then
      runtime_set_failure "INSTALL" "APK_INSTALL_REJECTED"
      return "$EXIT_FAIL_ARTIFACT"
    fi
    runtime_set_failure "INSTALL" "APK_INSTALL_COMMAND_FAILED"
    return "$EXIT_BLOCKED_INFRA"
  done

  printf 'INSTALL_ATTEMPT=%s APK=%s\n' "$((install_retry + 1))" "$test_apk" >> "$test_log"
  timeout 120s "${YUJIAN_ADB_BIN}" install -r -t "$test_apk" >> "$test_log" 2>&1
  test_rc=$?
  if (( test_rc == 124 )); then
    printf 'INSTALL_TIMEOUT=120s\n' >> "$test_log"
  fi
  if (( test_rc != 0 )); then
    if (( test_rc == 124 )) || runtime_is_transport_failure_file "$test_log"; then
      if (( install_retry >= 1 )); then
        runtime_set_failure "INSTALL" "ADB_TRANSPORT_DURING_INSTALL"
        return "$EXIT_BLOCKED_INFRA"
      fi
      install_retry=1
      runtime_restart_adb_once >/dev/null 2>&1 || true
      if ! runtime_wait_for_device; then
        runtime_set_failure "INSTALL" "ADB_TRANSPORT_DURING_INSTALL"
        return "$EXIT_BLOCKED_INFRA"
      fi
      printf 'INSTALL_RETRY=1 APK=%s\n' "$test_apk" >> "$test_log"
      timeout 120s "${YUJIAN_ADB_BIN}" install -r -t "$test_apk" >> "$test_log" 2>&1
      test_rc=$?
      if (( test_rc == 124 )); then
        printf 'INSTALL_TIMEOUT=120s\n' >> "$test_log"
      fi
    fi
  fi
  if (( test_rc != 0 )); then
    if (( test_rc == 124 )); then
      runtime_set_failure "INSTALL" "ADB_TEST_APK_INSTALL_TIMEOUT"
      return "$EXIT_BLOCKED_INFRA"
    fi
    if runtime_is_storage_failure_file "$test_log"; then
      runtime_set_failure "INSTALL" "INSUFFICIENT_STORAGE"
      return "$EXIT_BLOCKED_INFRA"
    fi
    if runtime_is_install_artifact_failure_file "$test_log" || grep -Eiq 'Failure \[' "$test_log"; then
      runtime_set_failure "INSTALL" "TEST_APK_INSTALL_REJECTED"
      return "$EXIT_FAIL_ARTIFACT"
    fi
    if runtime_is_transport_failure_file "$test_log"; then
      runtime_set_failure "INSTALL" "ADB_TRANSPORT_DURING_INSTALL"
      return "$EXIT_BLOCKED_INFRA"
    fi
    runtime_set_failure "INSTALL" "TEST_APK_INSTALL_COMMAND_FAILED"
    return "$EXIT_BLOCKED_INFRA"
  fi

  timeout 30s "${YUJIAN_ADB_BIN}" shell pm path "$YUJIAN_APP_PACKAGE" > "$verify_log" 2>&1
  verify_rc=$?
  if (( verify_rc == 124 )); then
    runtime_set_failure "INSTALL" "ADB_VERIFY_APP_TIMEOUT"
    return "$EXIT_BLOCKED_INFRA"
  fi
  if (( verify_rc != 0 )) || grep -Eiq 'offline|no devices|transport|closed' "$verify_log"; then
    runtime_set_failure "INSTALL" "ADB_TRANSPORT_AFTER_INSTALL"
    return "$EXIT_BLOCKED_INFRA"
  fi
  if ! grep -q '^package:' "$verify_log"; then
    runtime_set_failure "INSTALL" "APP_PACKAGE_NOT_INSTALLED"
    return "$EXIT_FAIL_ARTIFACT"
  fi

  : > "$verify_log"
  timeout 30s "${YUJIAN_ADB_BIN}" shell pm path "$YUJIAN_TEST_PACKAGE" >> "$verify_log" 2>&1
  verify_rc=$?
  if (( verify_rc == 124 )); then
    runtime_set_failure "INSTALL" "ADB_VERIFY_TEST_TIMEOUT"
    return "$EXIT_BLOCKED_INFRA"
  fi
  if (( verify_rc != 0 )) || grep -Eiq 'offline|no devices|transport|closed' "$verify_log"; then
    runtime_set_failure "INSTALL" "ADB_TRANSPORT_AFTER_INSTALL"
    return "$EXIT_BLOCKED_INFRA"
  fi
  if ! grep -q '^package:' "$verify_log"; then
    runtime_set_failure "INSTALL" "TEST_PACKAGE_NOT_INSTALLED"
    return "$EXIT_FAIL_ARTIFACT"
  fi

  runtime_log "APK install PASS app=$YUJIAN_APP_PACKAGE test=$YUJIAN_TEST_PACKAGE"
  return "$EXIT_PASS"
}
