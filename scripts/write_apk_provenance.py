#!/usr/bin/env python3
"""Write an APK provenance sidecar without promoting an untested artifact."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re

SHA = re.compile(r"^[0-9a-f]{40}$")
CLASSES = {"USER_PREVIEW", "API28_INTEGRATION_CANDIDATE"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--source-branch", required=True)
    parser.add_argument("--artifact-class", choices=sorted(CLASSES), required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--repository", default="pan277942135/Yujian_App")
    parser.add_argument("--artifact-name", required=True)
    parser.add_argument("--quick-check", action="append", default=[], help="NAME=STATUS")
    args = parser.parse_args()
    if not SHA.fullmatch(args.source_sha):
        parser.error("source-sha must be a 40-char SHA")
    if not args.apk.is_file():
        parser.error("APK file does not exist")
    if not args.test_apk.is_file():
        parser.error("Android test APK file does not exist")
    checks = {}
    for item in args.quick_check:
        name, sep, status = item.partition("=")
        if not sep or not name or status not in {"PASS", "FAIL", "NOT_RUN"}:
            parser.error(f"invalid quick-check value {item!r}")
        checks[name] = status
    apk_sha = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    test_apk_sha = hashlib.sha256(args.test_apk.read_bytes()).hexdigest()
    if args.artifact_class == "USER_PREVIEW":
        if any(status != "PASS" for status in checks.values()):
            parser.error("USER_PREVIEW requires all declared quick checks to PASS")
        api28_status = "NOT_RUN"
        formal_status = "NOT_ELIGIBLE"
        release_status = "NOT_ELIGIBLE"
    else:
        api28_status = "NOT_RUN"
        formal_status = "PENDING"
        release_status = "NOT_ELIGIBLE"
    run_url = f"https://github.com/{args.repository}/actions/runs/{args.run_id}"
    artifact_url = f"{run_url}/artifacts"
    report = {
        "schema_version": "1.0",
        "artifact_class": args.artifact_class,
        "source_sha": args.source_sha,
        "source_branch": args.source_branch,
        "github_actions_run_id": args.run_id,
        "github_actions_run_url": run_url,
        "github_actions_artifacts_url": artifact_url,
        "artifact_name": args.artifact_name,
        "apk_sha256": apk_sha,
        "android_test_apk_sha256": test_apk_sha,
        "api28_status": api28_status,
        "formal_status": formal_status,
        "release_status": release_status,
        "quick_checks": checks,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"APK_SHA256={apk_sha}")
    print(f"ARTIFACT_CLASS={args.artifact_class}")
    print(f"API28_STATUS={api28_status}")
    print(f"PROVENANCE_FILE={args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
