#!/usr/bin/env python3
"""Fail-closed single-test result parser for the API36 hosted emulator smoke."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def evaluate_case(
    *,
    class_name: str,
    method_name: str,
    raw_log: str,
    auxiliary_log: str = "",
    command_exit_code: int,
    timed_out: bool = False,
    app_pids: tuple[str, ...] = (),
    instrumentation_pids: tuple[str, ...] = (),
    start_time_utc: str = "",
    end_time_utc: str = "",
) -> dict[str, object]:
    status_events: list[dict[str, object]] = []
    pending_status: dict[str, str] = {}
    for line in raw_log.splitlines():
        match = re.match(r"^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$", line)
        if match:
            pending_status[match.group(1)] = match.group(2)
            continue
        match = re.match(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", line)
        if match:
            status_events.append({
                "class": pending_status.get("class"),
                "test": pending_status.get("test"),
                "code": int(match.group(1)),
            })
            pending_status = {}
    status_codes = [int(event["code"]) for event in status_events]
    started = any(
        event["class"] == class_name and event["test"] == method_name and event["code"] == 1
        for event in status_events
    )
    final_codes = [int(value) for value in re.findall(r"^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$", raw_log, re.M)]
    lmk_pattern = re.compile(
        r"(?is)(?:lowmemorykiller|\blmkd\b|\bam_kill\b|\bam_proc_died\b|\bSIGKILL\b|process crashed|fatal exception).*"
        r"(?:com\.yujian\.ai\.uiv2(?:\.test)?|instrumentation)|"
        r"(?:com\.yujian\.ai\.uiv2(?:\.test)?|instrumentation).*"
        r"(?:lowmemorykiller|\blmkd\b|\bam_kill\b|\bam_proc_died\b|\bSIGKILL\b|process crashed|fatal exception)",
    )
    lmk_or_process_death = bool(lmk_pattern.search(raw_log + "\n" + auxiliary_log))
    failed_status = any(code < 0 and code != -1 for code in status_codes)
    failed_marker = bool(
        re.search(r"(?i)FAILURES!!!|INSTRUMENTATION_RESULT: shortMsg=|test run failed|instrumentation aborted", raw_log)
    )
    normal_summary = bool(re.search(r"^OK \(1 test\)\s*$", raw_log, re.M))
    passed = (
        command_exit_code == 0
        and not timed_out
        and started
        and status_events == [
            {"class": class_name, "test": method_name, "code": 1},
            {"class": class_name, "test": method_name, "code": 0},
        ]
        and final_codes == [-1]
        and not failed_status
        and not failed_marker
        and not lmk_or_process_death
        and normal_summary
    )
    if passed:
        reason = "NONE"
    elif timed_out:
        reason = "INSTRUMENTATION_TIMEOUT"
    elif lmk_or_process_death:
        reason = "PROCESS_DEATH_OR_LOW_MEMORY_MANAGER_EVENT"
    elif command_exit_code != 0:
        reason = f"INSTRUMENTATION_COMMAND_EXIT_{command_exit_code}"
    elif not started:
        reason = "EXPECTED_TEST_NOT_STARTED"
    elif final_codes != [-1]:
        reason = "INSTRUMENTATION_CODE_NOT_EXACTLY_MINUS_ONE"
    elif status_codes.count(0) != 1:
        reason = "EXPECTED_ONE_COMPLETED_TEST"
    elif failed_status or failed_marker:
        reason = "INSTRUMENTATION_REPORTED_FAILURE"
    else:
        reason = "NORMAL_ONE_TEST_SUMMARY_MISSING"
    return {
        "class": class_name,
        "test": f"{class_name}#{method_name}",
        "test_method": method_name,
        "started": started,
        "completed": status_codes.count(0) == 1,
        "instrumentation_status_codes": status_codes,
        "instrumentation_events": status_events,
        "instrumentation_codes": final_codes,
        "command_exit_code": command_exit_code,
        "timed_out": timed_out,
        "lmk_or_process_death_observed": lmk_or_process_death,
        "normal_summary": normal_summary,
        "passed": passed,
        "reason": reason,
        "app_pids": list(app_pids),
        "instrumentation_pids": list(instrumentation_pids),
        "start_time_utc": start_time_utc,
        "end_time_utc": end_time_utc,
    }


def write_junit(result: dict[str, object], path: Path) -> None:
    passed = result["passed"] is True
    suite = ET.Element(
        "testsuite",
        {
            "name": str(result["class"]),
            "tests": "1",
            "failures": "0" if passed else "1",
            "errors": "0",
            "skipped": "0",
        },
    )
    case = ET.SubElement(
        suite,
        "testcase",
        {"classname": str(result["class"]), "name": str(result["test_method"])},
    )
    if not passed:
        failure = ET.SubElement(case, "failure", {"message": str(result["reason"])})
        failure.text = f"API36 instrumentation case failed: {result['reason']}"
    root = ET.Element("testsuites", tests="1", failures="0" if passed else "1", errors="0")
    root.append(suite)
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--class-name", required=True)
    parser.add_argument("--method-name", required=True)
    parser.add_argument("--log", required=True, type=Path)
    parser.add_argument("--auxiliary-log", type=Path)
    parser.add_argument("--command-exit-code", required=True, type=int)
    parser.add_argument("--timed-out", action="store_true")
    parser.add_argument("--app-pids", type=Path)
    parser.add_argument("--instrumentation-pids", type=Path)
    parser.add_argument("--start-time-utc", default="")
    parser.add_argument("--end-time-utc", default="")
    parser.add_argument("--json-out", required=True, type=Path)
    parser.add_argument("--xml-out", required=True, type=Path)
    args = parser.parse_args()

    raw_log = args.log.read_text(encoding="utf-8", errors="replace") if args.log.is_file() else ""
    auxiliary_log = (
        args.auxiliary_log.read_text(encoding="utf-8", errors="replace")
        if args.auxiliary_log and args.auxiliary_log.is_file()
        else ""
    )

    def pid_rows(path: Path | None) -> tuple[str, ...]:
        if path is None or not path.is_file():
            return ()
        return tuple(line.strip() for line in path.read_text(encoding="utf-8", errors="replace").splitlines() if line.strip())

    result = evaluate_case(
        class_name=args.class_name,
        method_name=args.method_name,
        raw_log=raw_log,
        auxiliary_log=auxiliary_log,
        command_exit_code=args.command_exit_code,
        timed_out=args.timed_out,
        app_pids=pid_rows(args.app_pids),
        instrumentation_pids=pid_rows(args.instrumentation_pids),
        start_time_utc=args.start_time_utc,
        end_time_utc=args.end_time_utc,
    )
    args.json_out.parent.mkdir(parents=True, exist_ok=True)
    args.json_out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    write_junit(result, args.xml_out)
    print(json.dumps(result, sort_keys=True))
    return 0 if result["passed"] is True else 1


if __name__ == "__main__":
    raise SystemExit(main())
