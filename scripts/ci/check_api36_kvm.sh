#!/usr/bin/env bash
set -uo pipefail

report="${KVM_PREFLIGHT_REPORT:-kvm-preflight.txt}"
mkdir -p "$(dirname "$report")"
: > "$report"

log() {
  printf '%s\n' "$*" | tee -a "$report"
}

finish() {
  local classification="$1"
  local exit_code="$2"
  log "classification=$classification"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf 'classification=%s\n' "$classification" >> "$GITHUB_OUTPUT"
  fi
  exit "$exit_code"
}

log "runner=${RUNNER_NAME:-local}"
log "os=$(uname -s) arch=$(uname -m)"
log "user=$(id -un) uid=$(id -u) groups=$(id -Gn)"

if [[ "$(uname -m)" != "x86_64" ]]; then
  log "reason=expected_x86_64_runner"
  finish KVM_ACCEL_NOT_USABLE 22
fi

if [[ ! -e /dev/kvm ]]; then
  log "kvm_device=MISSING path=/dev/kvm"
  finish KVM_MISSING 20
fi

log "kvm_permissions_before=$(stat -c '%A owner=%U group=%G type=%F' /dev/kvm 2>&1)"
if [[ -r /dev/kvm && -w /dev/kvm ]]; then
  log "kvm_access_before=READ_WRITE"
else
  log "kvm_access_before=INSUFFICIENT read=$([[ -r /dev/kvm ]] && echo true || echo false) write=$([[ -w /dev/kvm ]] && echo true || echo false)"
fi
if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  log "kvm_permissions=INSUFFICIENT action=sudo_chmod_0666"
  if ! sudo chmod 0666 /dev/kvm; then
    log "reason=sudo_chmod_failed"
    finish KVM_PERMISSION_DENIED 21
  fi
fi
log "kvm_permissions_after=$(stat -c '%A owner=%U group=%G type=%F' /dev/kvm 2>&1)"
if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  log "reason=current_user_lacks_read_write_after_chmod"
  finish KVM_PERMISSION_DENIED 21
fi
log "kvm_access_after=READ_WRITE"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
emulator="$sdk_root/emulator/emulator"
if [[ -z "$sdk_root" || ! -x "$emulator" ]]; then
  log "reason=android_emulator_binary_missing path=$emulator"
  finish KVM_ACCEL_NOT_USABLE 22
fi

log "emulator_binary=$emulator"
set +e
accel_output="$("$emulator" -accel-check 2>&1)"
accel_status=$?
set -e
log "emulator_accel_check_exit=$accel_status"
log "emulator_accel_check_output_begin"
log "$accel_output"
log "emulator_accel_check_output_end"
if [[ "$accel_status" -ne 0 ]] || ! grep -Eiq 'KVM.*(installed and )?usable|KVM.*available' <<< "$accel_output"; then
  log "reason=emulator_accel_check_did_not_confirm_usable_kvm"
  finish KVM_ACCEL_NOT_USABLE 22
fi

finish KVM_READY 0
