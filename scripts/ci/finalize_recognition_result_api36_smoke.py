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
        "Evidence hashes: `evidence/runtime/recognition-result-api36/evidence-sha256sums.txt`.",
    ])
    with summary_path.open("a", encoding="utf-8") as output:
        output.write("\n".join(markdown) + "\n")


def _error_annotation(classification: str, reason: str) -> None:
    def command_escape(value: str) -> str:
        return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

    print(
        "::error title=API36 production AUTO smoke failed::"
        f"classification={command_escape(classification)}; reason={command_escape(reason)}"
    )


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

    if payload.get("classification") == "AUTO_SMOKE_PASS":
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
            _error_annotation("INFRA_BLOCKED", reason)
            return 0
        except Exception as fallback_exc:
            reason = f"SMOKE_RESULT_FINALIZATION_AND_FALLBACK_FAILED:{type(fallback_exc).__name__}:{str(fallback_exc)[:400]}"
            print(f"API36_FINALIZATION_FATAL:{reason}")
            _error_annotation("INFRA_BLOCKED", reason)
            return 0

    if payload.get("classification") != "AUTO_SMOKE_PASS":
        _error_annotation(str(payload.get("classification", "UNKNOWN")), str(payload.get("failure_reason", "UNKNOWN")))
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
