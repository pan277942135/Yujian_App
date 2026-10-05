#!/usr/bin/env python3
"""Write the stable, machine-readable result for one Android runtime gate."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def maybe_int(value: str) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def build_payload(args: argparse.Namespace) -> dict[str, object]:
    return {
        "gate": args.gate,
        "api_level": maybe_int(args.api_level),
        "build_sha": args.build_sha,
        "device": {
            "adb_state": args.adb_state,
            "boot_completed": args.boot_completed,
            "data_free_kb": maybe_int(args.data_free_kb),
        },
        "preflight": args.preflight,
        "install": args.install,
        "instrumentation": args.instrumentation,
        "evidence": args.evidence,
        "classification": args.classification,
    }


def write_summary(args: argparse.Namespace, payload: dict[str, object]) -> None:
    if not args.summary_file:
        return
    summary_path = Path(args.summary_file)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    reason = args.failure_reason or ""
    lines = [
        "## YuJian Android Runtime Gate",
        "",
        f"Gate: {args.gate}",
        f"API: {args.api_level}",
        f"SHA: {args.build_sha}",
        "",
        f"Preflight       {args.preflight}",
        f"APK Install     {args.install}",
        f"Instrumentation {args.instrumentation}",
        f"Evidence        {args.evidence}",
        f"Classification   {args.classification}",
    ]
    if reason:
        lines.append(f"Reason          {reason}")
    with summary_path.open("a", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--gate", required=True)
    parser.add_argument("--api-level", required=True)
    parser.add_argument("--build-sha", required=True)
    parser.add_argument("--adb-state", required=True)
    parser.add_argument("--boot-completed", required=True)
    parser.add_argument("--data-free-kb", required=True)
    parser.add_argument("--preflight", required=True)
    parser.add_argument("--install", required=True)
    parser.add_argument("--instrumentation", required=True)
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--classification", required=True)
    parser.add_argument("--failure-phase", default="")
    parser.add_argument("--failure-reason", default="")
    parser.add_argument("--summary-file", default="")
    args = parser.parse_args()

    payload = build_payload(args)
    if args.failure_phase:
        payload["failure_phase"] = args.failure_phase
    if args.failure_reason:
        payload["failure_reason"] = args.failure_reason

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_summary(args, payload)


if __name__ == "__main__":
    main()
