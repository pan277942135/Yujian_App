#!/usr/bin/env python3
"""Publish a fail-closed API36 smoke summary and evidence hashes."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import sys
import traceback
from typing import Any


TEST_A = "com.yujian.ai.ClassifierRealTfliteTensorBindingTest#packagedClassifierRunsWithFrozenTensorBinding"
TEST_B = "com.yujian.ai.RecognitionRevealProductionRuntimeTest#realNightCatchHandoffDrivesTheNativeResultReveal"
TEST_C = "com.yujian.ai.RecognitionResultHandoffProductionNavigationTest#hostedApi36HomePickerAutoRecognitionCompletesResultHandoff"
INSTRUMENTATION_CASES = (
    ("Test A — Classifier TFLite", "classifier_tensor_binding", TEST_A),
    ("Test B — Production reveal", "production_night_catch_reveal", TEST_B),
    ("Test C — Home picker AUTO", "production_home_picker_auto", TEST_C),
)


def _case_passes(value: Any, expected_test: str) -> bool:
    return (
        isinstance(value, dict)
        and value.get("test") == expected_test
        and value.get("passed") is True
        and value.get("started") is True
        and value.get("completed") is True
        and value.get("command_exit_code") == 0
        and value.get("timed_out") is False
        and value.get("lmk_or_process_death_observed") is False
        and value.get("normal_summary") is True
        and value.get("instrumentation_codes") == [-1]
        and value.get("instrumentation_status_codes", []).count(0) == 1
    )


def _load_object(path: pathlib.Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def _sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _write_hashes(evidence_dir: pathlib.Path) -> None:
    sums_path = evidence_dir / "evidence-sha256sums.txt"
    rows = []
    for path in sorted(p for p in evidence_dir.rglob("*") if p.is_file() and p != sums_path):
        rows.append(f"{_sha256(path)}  {path.relative_to(evidence_dir).as_posix()}")
    sums_path.write_text("\n".join(rows) + ("\n" if rows else ""), encoding="utf-8")


def _summary_cell(value: Any, limit: int = 1200) -> str:
    if value is None:
        return "NOT_RECORDED"
    text = re.sub(r"\s+", " ", str(value)).strip()
    text = text.replace("|", "/").replace("`", "'")
    if len(text) > limit:
        text = text[:limit] + "…"
    return text or "NOT_RECORDED"


def _append_summary(summary_path: pathlib.Path, payload: dict[str, Any]) -> None:
    fields = [
        ("BUILD_PASS", payload.get("BUILD_PASS", False)),
        ("KVM_READY", payload.get("KVM_READY", False)),
        ("EMULATOR_BOOT_PASS", payload.get("EMULATOR_BOOT_PASS", "NOT_CONFIRMED")),
        ("AUTO_SMOKE_PASS", payload.get("AUTO_SMOKE_PASS", False)),
        ("DEVICE_QA_PENDING", payload.get("DEVICE_QA_PENDING", True)),
        ("classification", payload.get("classification", "UNKNOWN")),
        ("failure_reason", payload.get("failure_reason", "UNKNOWN")),
        ("validation_reason", payload.get("validation_reason", "NOT_CONFIRMED")),
        ("instrumentation_attempted", payload.get("instrumentation_attempted", "NOT_CONFIRMED")),
        ("instrumentation_started", payload.get("instrumentation_started", "NOT_CONFIRMED")),
        ("instrumentation_command_exit_code", payload.get("instrumentation_command_exit_code", "NOT_CONFIRMED")),
        ("instrumentation_timed_out", payload.get("instrumentation_timed_out", "NOT_CONFIRMED")),
        ("CLASSIFIER_TFLITE_RUNTIME_PASS", payload.get("CLASSIFIER_TFLITE_RUNTIME_PASS", False)),
        ("PRODUCTION_REVEAL_INSTRUMENTATION_PASS", payload.get("PRODUCTION_REVEAL_INSTRUMENTATION_PASS", False)),
        ("AUTO_PRODUCTION_SMOKE_PASS", payload.get("AUTO_PRODUCTION_SMOKE_PASS", False)),
        ("API36_INSTRUMENTATION_PASS", payload.get("API36_INSTRUMENTATION_PASS", False)),
        ("APP_FORCE_STOP_VERIFIED_BETWEEN_TESTS", payload.get("APP_FORCE_STOP_VERIFIED_BETWEEN_TESTS", False)),
        ("APP_FORCE_STOP_VERIFIED_BETWEEN_REVEAL_AND_AUTO", payload.get("APP_FORCE_STOP_VERIFIED_BETWEEN_REVEAL_AND_AUTO", False)),
        ("smoke_step_outcome", payload.get("smoke_step_outcome", "NOT_CONFIRMED")),
        ("source_sha", payload.get("source_sha", "UNKNOWN")),
        ("app_apk_sha256", payload.get("app_apk_sha256", "NOT_RECORDED")),
        ("android_test_apk_sha256", payload.get("android_test_apk_sha256", "NOT_RECORDED")),
        ("host_mem_available_kb", payload.get("host_mem_available_kb", "NOT_RECORDED")),
        ("host_swap_total_kb", payload.get("host_swap_total_kb", "NOT_RECORDED")),
        ("avd_ram_mb", payload.get("avd_ram_mb", "NOT_CONFIRMED")),
        ("avd_vm_heap_mb", payload.get("avd_vm_heap_mb", "NOT_CONFIRMED")),
        ("e2e_acceptance_mode", payload.get("e2e_acceptance_mode", "LEGACY_API36_SMOKE")),
        ("single_photo_e2e_status", payload.get("single_photo_e2e_status", "NOT_RUN")),
        ("single_photo_e2e_reason", payload.get("single_photo_e2e_reason", "NOT_RECORDED")),
        ("five_photo_e2e_status", payload.get("five_photo_e2e_status", "NOT_RUN")),
        ("API36_SINGLE_PHOTO_E2E_PASS", payload.get("API36_SINGLE_PHOTO_E2E_PASS", False)),
        ("API36_FIVE_PHOTO_E2E_PASS", payload.get("API36_FIVE_PHOTO_E2E_PASS", False)),
        ("API36_E2E_ACCEPTANCE_PASS", payload.get("API36_E2E_ACCEPTANCE_PASS", False)),
    ]
    markdown = [
        "## API 36 production AUTO smoke result",
        "",
        "| Field | Value |",
        "| --- | --- |",
    ]
    for name, value in fields:
        safe_value = str(value).replace("|", "/").replace("`", "'").replace("\r", " ").replace("\n", " ")
        markdown.append(f"| `{name}` | `{safe_value}` |")
    markdown.extend([
        "",
        "### Instrumentation case details",
        "",
        "| Case | Started | Completed | Passed | Failure phase | Failure reason | Root cause | Exception | Exit | Timed out | Relevant log excerpt |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- |",
    ])
    cases = payload.get("instrumentation_cases")
    if not isinstance(cases, dict):
        cases = {}
    cases_to_report = (
        ("Single photo production E2E", "production_home_picker_auto", TEST_C)
        if payload.get("e2e_acceptance_mode") == "API36_SINGLE_AND_FIVE_PHOTO"
        else INSTRUMENTATION_CASES
    )
    if payload.get("e2e_acceptance_mode") == "API36_SINGLE_AND_FIVE_PHOTO":
        cases_to_report = (cases_to_report,)
    for label, key, expected_test in cases_to_report:
        case = cases.get(key)
        if not isinstance(case, dict):
            case = {}
        values = (
            label,
            case.get("started", "NOT_RECORDED"),
            case.get("completed", "NOT_RECORDED"),
            case.get("passed", "NOT_RECORDED"),
            case.get("failure_phase", "NOT_RECORDED"),
            case.get("failure_reason", case.get("reason", "NOT_RECORDED")),
            case.get("root_cause", "NOT_RECORDED"),
            case.get("exception", "NOT_RECORDED"),
            case.get("command_exit_code", "NOT_RECORDED"),
            case.get("timed_out", "NOT_RECORDED"),
            case.get("relevant_log_excerpt", "NOT_RECORDED"),
        )
        markdown.append("| " + " | ".join(_summary_cell(value) for value in values) + " |")
        if case.get("test") not in (None, expected_test):
            markdown.append(f"\nExpected `{expected_test}`; observed `{_summary_cell(case.get('test'))}`.")
    markdown.extend([
        "",
        "Evidence hashes: `evidence/runtime/recognition-result-api36/evidence-sha256sums.txt`.",
    ])
    with summary_path.open("a", encoding="utf-8") as output:
        output.write("\n".join(markdown) + "\n")


def _error_annotation(classification: str, reason: str, payload: dict[str, Any] | None = None) -> None:
    def command_escape(value: str) -> str:
        return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

    title = (
        "API36 single/five-photo E2E failed"
        if isinstance(payload, dict) and payload.get("e2e_acceptance_mode") == "API36_SINGLE_AND_FIVE_PHOTO"
        else "API36 production AUTO smoke failed"
    )
    print(
        f"::error title={title}::"
        f"classification={command_escape(classification)}; reason={command_escape(reason)}"
    )
    if not isinstance(payload, dict):
        return
    if payload.get("e2e_acceptance_mode") == "API36_SINGLE_AND_FIVE_PHOTO":
        cases = payload.get("instrumentation_cases")
        case = cases.get("production_home_picker_auto", {}) if isinstance(cases, dict) else {}
        if not isinstance(case, dict):
            case = {}
        detail = (
            f"single_photo_status={payload.get('single_photo_e2e_status', 'NOT_RECORDED')}; "
            f"single_photo_reason={payload.get('single_photo_e2e_reason', 'NOT_RECORDED')}; "
            f"five_photo_status={payload.get('five_photo_e2e_status', 'NOT_RECORDED')}; "
            f"last_test_failure={case.get('reason', 'NOT_RECORDED')}; "
            f"failure_phase={payload.get('failure_phase', 'NOT_RECORDED')}"
        )
        print(f"::error title=API36 single/five-photo E2E::{command_escape(detail)}")
        return
    cases = payload.get("instrumentation_cases")
    if not isinstance(cases, dict):
        cases = {}
    for label, key, expected_test in INSTRUMENTATION_CASES:
        case = cases.get(key)
        if not _case_passes(case, expected_test):
            case = case if isinstance(case, dict) else {}
            details = (
                ("test", case.get("test", expected_test)),
                ("started", case.get("started", "NOT_RECORDED")),
                ("completed", case.get("completed", "NOT_RECORDED")),
                ("passed", case.get("passed", "NOT_RECORDED")),
                ("failure_phase", case.get("failure_phase", payload.get("failure_phase", "NOT_RECORDED"))),
                ("failure_reason", case.get("failure_reason", case.get("reason", "CASE_RESULT_MISSING"))),
                ("root_cause", case.get("root_cause", "NOT_RECORDED")),
                ("exception", case.get("exception", "NOT_RECORDED")),
                ("command_exit_code", case.get("command_exit_code", "NOT_RECORDED")),
                ("timed_out", case.get("timed_out", "NOT_RECORDED")),
                ("relevant_log_excerpt", case.get("relevant_log_excerpt", "NOT_RECORDED")),
            )
            data = "; ".join(f"{name}={command_escape(_summary_cell(value, 1400))}" for name, value in details)
            print(f"::error title=API36 {label} failed::{data}")


def _enrich(payload: dict[str, Any], args: argparse.Namespace) -> None:
    observed_source_sha = payload.get("source_sha")
    if observed_source_sha != args.source_sha:
        payload["classification"] = "INFRA_BLOCKED"
        payload["failure_reason"] = (
            "RUNTIME_RESULT_SOURCE_SHA_MISSING"
            if not observed_source_sha
            else "RUNTIME_RESULT_SOURCE_SHA_MISMATCH"
        )
        payload["AUTO_SMOKE_PASS"] = False

    payload["BUILD_PASS"] = args.build_result == "success"
    payload["smoke_step_outcome"] = args.smoke_result
    payload["KVM_READY"] = args.kvm_ready.lower() == "true"
    payload.setdefault("EMULATOR_BOOT_PASS", payload.get("emulator_boot_pass", "NOT_CONFIRMED"))
    payload.setdefault("AUTO_SMOKE_PASS", False)
    payload["DEVICE_QA_PENDING"] = True

    if payload.get("e2e_acceptance_mode") == "API36_SINGLE_AND_FIVE_PHOTO":
        single_pass = payload.get("single_photo_e2e_status") == "PASS" and \
            payload.get("API36_SINGLE_PHOTO_E2E_PASS") is True
        five_pass = payload.get("five_photo_e2e_status") == "PASS" and \
            payload.get("API36_FIVE_PHOTO_E2E_PASS") is True
        gate_failures = []
        if observed_source_sha != args.source_sha:
            gate_failures.append("SOURCE_SHA")
        if args.build_result != "success":
            gate_failures.append("BUILD")
        if args.kvm_ready.lower() != "true":
            gate_failures.append("KVM")
        if payload.get("EMULATOR_BOOT_PASS") is not True:
            gate_failures.append("EMULATOR_BOOT")
        if args.smoke_result != "success":
            gate_failures.append("API36_RUNNER")
        payload["API36_SINGLE_PHOTO_E2E_PASS"] = single_pass
        payload["API36_FIVE_PHOTO_E2E_PASS"] = five_pass
        payload["API36_E2E_ACCEPTANCE_PASS"] = single_pass and five_pass and not gate_failures
        if payload["API36_E2E_ACCEPTANCE_PASS"]:
            payload["classification"] = "YUJIAN_P0_API36_E2E_ACCEPTANCE_PASS"
        elif single_pass and five_pass and gate_failures:
            payload["classification"] = "INFRA_BLOCKED"
            payload["failure_reason"] = "API36_E2E_ACCEPTANCE_GATE_FAILED:" + ",".join(gate_failures)
        elif payload.get("classification") == "YUJIAN_P0_API36_E2E_ACCEPTANCE_PASS":
            # Never preserve a success label when the exact-head acceptance gates disagree.
            payload["classification"] = "INFRA_BLOCKED" if gate_failures else "EVIDENCE_INCOMPLETE"
            payload["failure_reason"] = (
                "API36_E2E_ACCEPTANCE_GATE_FAILED:" + ",".join(gate_failures)
                if gate_failures
                else "API36_E2E_ACCEPTANCE_PASS_CONTRADICTS_SINGLE_OR_FIVE_PHOTO_RESULT"
            )
    elif payload.get("classification") == "AUTO_SMOKE_PASS":
        failed_gates = [
            field
            for field in ("BUILD_PASS", "KVM_READY", "EMULATOR_BOOT_PASS", "AUTO_SMOKE_PASS")
            if payload.get(field) is not True
        ]
        if observed_source_sha != args.source_sha:
            failed_gates.append("SOURCE_SHA")
        if args.smoke_result != "success":
            failed_gates.append("SMOKE_RUNNER_STEP")
        cases = payload.get("instrumentation_cases")
        if not isinstance(cases, dict) or not _case_passes(cases.get("classifier_tensor_binding"), TEST_A):
            failed_gates.append("CLASSIFIER_TFLITE_RUNTIME_PASS")
        if not isinstance(cases, dict) or not _case_passes(cases.get("production_night_catch_reveal"), TEST_B):
            failed_gates.append("PRODUCTION_REVEAL_INSTRUMENTATION_PASS")
        if not isinstance(cases, dict) or not _case_passes(cases.get("production_home_picker_auto"), TEST_C):
            failed_gates.append("AUTO_PRODUCTION_SMOKE_PASS")
        if payload.get("CLASSIFIER_TFLITE_RUNTIME_PASS") is not True:
            failed_gates.append("CLASSIFIER_TFLITE_RUNTIME_PASS_FIELD")
        if payload.get("PRODUCTION_REVEAL_INSTRUMENTATION_PASS") is not True:
            failed_gates.append("PRODUCTION_REVEAL_INSTRUMENTATION_PASS_FIELD")
        if payload.get("AUTO_PRODUCTION_SMOKE_PASS") is not True:
            failed_gates.append("AUTO_PRODUCTION_SMOKE_PASS_FIELD")
        if payload.get("API36_INSTRUMENTATION_PASS") is not True:
            failed_gates.append("API36_INSTRUMENTATION_PASS")
        if payload.get("APP_FORCE_STOP_VERIFIED_BETWEEN_TESTS") is not True:
            failed_gates.append("APP_FORCE_STOP_VERIFIED_BETWEEN_TESTS")
        if payload.get("APP_FORCE_STOP_VERIFIED_BETWEEN_REVEAL_AND_AUTO") is not True:
            failed_gates.append("APP_FORCE_STOP_VERIFIED_BETWEEN_REVEAL_AND_AUTO")
        failed_gates = list(dict.fromkeys(failed_gates))
        if failed_gates:
            payload["classification"] = "INFRA_BLOCKED"
            payload["failure_reason"] = "AUTO_PASS_GATE_CONTRADICTION:" + ",".join(failed_gates)
            payload["AUTO_SMOKE_PASS"] = False
    if payload.get("classification") != "AUTO_SMOKE_PASS":
        payload["AUTO_SMOKE_PASS"] = False

    for field, filename in (
        ("app_apk_sha256", "app-debug.apk"),
        ("android_test_apk_sha256", "app-debug-androidTest.apk"),
    ):
        apk = args.apk_dir / filename
        if apk.is_file():
            payload[field] = _sha256(apk)

    host_json = args.evidence_dir / "host-memory-preflight.json"
    if host_json.is_file():
        host = _load_object(host_json)
        for field in ("mem_total_kb", "mem_available_kb", "swap_total_kb", "swap_free_kb"):
            if field in host:
                payload[f"host_{field}"] = host[field]

    avd_report = args.evidence_dir / "avd-config-before-launch.txt"
    if avd_report.is_file():
        avd_text = avd_report.read_text(encoding="utf-8", errors="replace")
        for key, field in (("ramSize", "avd_ram_mb"), ("heapSize", "avd_vm_heap_mb")):
            match = re.search(rf"^hw\.{key}=(\d+)(?:M)?$", avd_text, re.MULTILINE | re.IGNORECASE)
            if match:
                payload[field] = int(match.group(1))


def _finalize(args: argparse.Namespace) -> int:
    args.evidence_dir.mkdir(parents=True, exist_ok=True)
    try:
        payload = _load_object(args.result)
        _enrich(payload, args)
        args.result.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        _write_hashes(args.evidence_dir)
        _append_summary(args.summary, payload)
    except Exception as exc:  # Keep reporting fail-closed if the summary itself has bad input.
        try:
            try:
                payload = _load_object(args.result)
            except Exception:
                payload = {}
            reason = f"SMOKE_RESULT_FINALIZATION_FAILED:{type(exc).__name__}:{str(exc)[:400]}"
            payload.update({
                "source_sha": args.source_sha,
                "classification": "INFRA_BLOCKED",
                "failure_reason": reason,
                "validation_reason": payload.get("validation_reason", "NOT_CONFIRMED"),
                "BUILD_PASS": args.build_result == "success",
                "KVM_READY": args.kvm_ready.lower() == "true",
                "EMULATOR_BOOT_PASS": payload.get("EMULATOR_BOOT_PASS", payload.get("emulator_boot_pass", "NOT_CONFIRMED")),
                "AUTO_SMOKE_PASS": False,
                "DEVICE_QA_PENDING": True,
                "smoke_step_outcome": args.smoke_result,
            })
            error_path = args.evidence_dir / "summary-finalization-error.txt"
            error_path.write_text(traceback.format_exc(), encoding="utf-8")
            args.result.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
            try:
                _write_hashes(args.evidence_dir)
            except Exception as hash_exc:
                error_path.write_text(
                    traceback.format_exc() + f"\nHASH_WRITE_FAILED: {type(hash_exc).__name__}: {hash_exc}\n",
                    encoding="utf-8",
                )
            try:
                _append_summary(args.summary, payload)
            except Exception as summary_exc:
                print(f"API36_STEP_SUMMARY_WRITE_FAILED:{type(summary_exc).__name__}:{summary_exc}")
            _error_annotation("INFRA_BLOCKED", reason, payload)
            return 0
        except Exception as fallback_exc:
            reason = f"SMOKE_RESULT_FINALIZATION_AND_FALLBACK_FAILED:{type(fallback_exc).__name__}:{str(fallback_exc)[:400]}"
            print(f"API36_FINALIZATION_FATAL:{reason}")
            _error_annotation("INFRA_BLOCKED", reason, payload)
            return 0

    e2e_acceptance_pass = (
        payload.get("e2e_acceptance_mode") == "API36_SINGLE_AND_FIVE_PHOTO"
        and payload.get("API36_E2E_ACCEPTANCE_PASS") is True
    )
    if payload.get("classification") != "AUTO_SMOKE_PASS" and not e2e_acceptance_pass:
        _error_annotation(
            str(payload.get("classification", "UNKNOWN")),
            str(payload.get("failure_reason", "UNKNOWN")),
            payload,
        )
    elif e2e_acceptance_pass:
        print(f"API36_E2E_ACCEPTANCE_PASS source_sha={payload.get('source_sha')}")
    else:
        print(f"API36_PRODUCTION_AUTO_SMOKE=AUTO_SMOKE_PASS source_sha={payload['source_sha']}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--result", required=True, type=pathlib.Path)
    parser.add_argument("--evidence-dir", required=True, type=pathlib.Path)
    parser.add_argument("--summary", required=True, type=pathlib.Path)
    parser.add_argument("--apk-dir", required=True, type=pathlib.Path)
    parser.add_argument("--build-result", required=True)
    parser.add_argument("--smoke-result", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--kvm-ready", required=True)
    args = parser.parse_args()
    return _finalize(args)


if __name__ == "__main__":
    raise SystemExit(main())
