#!/usr/bin/env python3
"""Watch a central batch for API28 runner assignment and persist queue-timeout status."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import io
import json
import os
from pathlib import Path
import re
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import zipfile

import yaml

ROOT = Path(__file__).resolve().parents[1]


def parse_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def api(token: str, url: str, method: str = "GET"):
    request = Request(url, method=method, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "YuJian-API28-Batch-Monitor",
    })
    with urlopen(request, timeout=30) as response:
        raw = response.read()
        return json.loads(raw) if raw else {}


def api_bytes(token: str, url: str) -> bytes:
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "YuJian-API28-Batch-Monitor",
    })
    with urlopen(request, timeout=90) as response:
        return response.read()


def is_runtime_job(job: dict) -> bool:
    labels = {str(label).lower() for label in job.get("labels", [])}
    name = str(job.get("name", "")).lower()
    return (
        {"self-hosted", "yujian-android", "api28"} <= labels
        or "android-runtime" in name
        or "nh01 gemini native v3 runtime / runtime" in name
        or ("nh01" in name and "runtime" in name)
    )


def check_created_at(token: str, check_url: str | None) -> str | None:
    if not check_url:
        return None
    match = re.search(r"/check-runs/(\d+)$", check_url)
    if not match:
        return None
    try:
        check = api(token, check_url)
    except (HTTPError, URLError, TimeoutError, ValueError):
        return None
    return check.get("created_at")


def workflow_jobs(token: str, api_url: str, repository: str, run_id: str) -> list[dict]:
    owner, repo = repository.split("/", 1)
    response = api(token, f"{api_url}/repos/{owner}/{repo}/actions/runs/{run_id}/jobs?per_page=100")
    return response.get("jobs", [])


def list_artifacts(token: str, api_url: str, repository: str, run_id: str) -> list[dict]:
    owner, repo = repository.split("/", 1)
    response = api(token, f"{api_url}/repos/{owner}/{repo}/actions/runs/{run_id}/artifacts?per_page=100")
    return response.get("artifacts", [])


def artifact_json(token: str, artifacts: list[dict], prefix: str, suffix: str) -> dict | None:
    artifact = next((row for row in artifacts if row.get("name", "").startswith(prefix) and not row.get("expired")), None)
    if not artifact:
        return None
    try:
        archive = api_bytes(token, artifact["archive_download_url"])
        with zipfile.ZipFile(io.BytesIO(archive)) as contents:
            matching = [name for name in contents.namelist() if name.endswith(suffix)]
            if matching:
                return json.loads(contents.read(matching[0]).decode("utf-8"))
    except (HTTPError, URLError, TimeoutError, ValueError, KeyError, zipfile.BadZipFile):
        return None
    return None


def summarize_gate_artifacts(args, batch: dict) -> tuple[dict[str, str], str | None]:
    required = batch.get("required_gates", [])
    results = {gate: "NOT_RUN" for gate in required}
    artifacts = list_artifacts(args.token, args.api_url, args.repository, args.run_id)
    source_sha = batch.get("integration_sha") or ""
    apk_provenance = None
    for row in artifacts:
        if row.get("name", "").startswith(f"YuJian-runtime-apks-{source_sha}-"):
            apk_provenance = artifact_json(args.token, [row], row["name"], "apk_provenance.json")
            if apk_provenance:
                break
    provenance_valid = bool(
        apk_provenance
        and apk_provenance.get("source_sha") == source_sha
        and apk_provenance.get("artifact_class") == "API28_INTEGRATION_CANDIDATE"
        and apk_provenance.get("apk_sha256")
        and apk_provenance.get("android_test_apk_sha256")
    )
    app_sha = apk_provenance.get("apk_sha256") if provenance_valid else None
    test_sha = apk_provenance.get("android_test_apk_sha256") if provenance_valid else None
    for gate in required:
        prefix = (f"YuJian-NH01-Gemini-V3-{source_sha}-" if gate == "gemini-home-v2"
                  else f"runtime-gate-{gate}-{source_sha}-")
        record = artifact_json(args.token, artifacts, prefix, "runtime_gate_result.json")
        if not record:
            continue
        if (record.get("build_sha") != source_sha or record.get("api_level") != 28
                or not provenance_valid or record.get("app_apk_sha256") != app_sha
                or record.get("test_apk_sha256") != test_sha):
            results[gate] = "BLOCKED_INFRA"
        elif record.get("classification") == "PASS":
            results[gate] = "PASS"
        elif record.get("classification") in {"FAIL", "FAIL_TEST", "FAIL_ARTIFACT", "FAIL_EVIDENCE"}:
            results[gate] = "FAIL"
        elif record.get("classification") in {"BLOCKED_INFRA", "CANCELLED"}:
            results[gate] = "BLOCKED_INFRA"
    return results, app_sha


def persist_timeout(args, batch_id: str, queue_started: str, runner_started: str | None,
                    gate_results: dict[str, str], apk_sha: str | None, jobs: list[dict]) -> None:
    expected = json.loads(args.batch_record.read_text(encoding="utf-8"))
    result = {
        "batch_status": "BLOCKED_INFRA",
        "api28_status": "BLOCKED_INFRA",
        "runner_available": False,
        "reason": f"API28 runtime remained queued for more than {args.max_wait_minutes} minutes",
        "queue_started_at": queue_started,
        "runner_started_at": runner_started,
        "finished_at": utc_now(),
        "workflow_run_id": args.run_id,
        "gate_results": gate_results,
        "required_gates": expected.get("required_gates", []),
        "integration_sha": expected.get("integration_sha"),
        "base_sha": expected.get("base_sha"),
        "tree_sha": expected.get("tree_sha"),
        "integrated": expected.get("integrated", []),
        "blocked": expected.get("blocked", []),
        "deferred": expected.get("deferred", []),
        "apk_sha256": apk_sha,
        "artifact_class": "API28_INTEGRATION_CANDIDATE" if apk_sha else None,
        "source_sha": expected.get("integration_sha") if apk_sha else None,
        "apk_provenance_valid": bool(apk_sha),
        "component_results": [
            {"pr_number": item.get("pr_number"), "head_sha": item.get("head_sha"), "result": "NOT_INDEPENDENTLY_TESTED", "batch_result": "BLOCKED_INFRA", "integration_sha": expected.get("integration_sha")}
            for item in expected.get("integrated", [])
        ],
        "job_outcomes": [{"name": job.get("name"), "status": job.get("status"), "conclusion": job.get("conclusion"), "started_at": job.get("started_at"), "completed_at": job.get("completed_at")} for job in jobs],
        "evidence_links": [f"https://github.com/{args.repository}/actions/runs/{args.run_id}"],
    }
    final_path = args.ledger_dir / "monitor-final.json"
    final_path.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    subprocess.run([
        "python3", str(ROOT / "scripts/api28_batch_ledger.py"), "finalize",
        "--ledger-dir", str(args.ledger_dir), "--batch-id", batch_id,
        "--final-input", str(final_path),
    ], cwd=ROOT, check=True)
    subprocess.run([
        "python3", str(ROOT / "scripts/api28_batch_git.py"), "commit",
        "--ledger-dir", str(args.ledger_dir),
        "--message", f"API28 batch {batch_id} blocked by runner queue timeout",
    ], cwd=ROOT, check=True)
    owner, repo = args.repository.split("/", 1)
    api(args.token, f"{args.api_url}/repos/{owner}/{repo}/actions/runs/{args.run_id}/cancel", method="POST")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", default=os.getenv("GITHUB_REPOSITORY", ""), required=False)
    parser.add_argument("--api-url", default=os.getenv("GITHUB_API_URL", "https://api.github.com"))
    parser.add_argument("--run-id", default=os.getenv("GITHUB_RUN_ID", ""), required=False)
    parser.add_argument("--batch-id", required=True)
    parser.add_argument("--batch-record", type=Path, required=True)
    parser.add_argument("--ledger-dir", type=Path, required=True)
    parser.add_argument("--token", default=os.getenv("GITHUB_TOKEN", ""))
    parser.add_argument("--max-wait-minutes", type=int, default=None)
    parser.add_argument("--observe-minutes", type=int, default=300)
    parser.add_argument("--poll-seconds", type=int, default=60)
    args = parser.parse_args()
    if not args.repository or not args.run_id or not args.token:
        raise SystemExit("repository, run-id, and token are required")
    policy = yaml.safe_load((ROOT / "harness/ANDROID_BATCH_ACCEPTANCE_POLICY_V1_1.yaml").read_text(encoding="utf-8"))
    max_wait = args.max_wait_minutes or int(policy["batch"]["maximum_queue_wait_minutes"])
    args.max_wait_minutes = max_wait
    start_at: datetime | None = None
    queue_started: str | None = None
    runner_started: str | None = None
    observe_started = datetime.now(timezone.utc)
    batch_record = json.loads(args.batch_record.read_text(encoding="utf-8"))
    required_gates = set(batch_record.get("required_gates", []))
    owner, repo = args.repository.split("/", 1)
    while True:
        jobs = workflow_jobs(args.token, args.api_url, args.repository, args.run_id)
        runtime = [job for job in jobs if is_runtime_job(job)]
        running = [job for job in runtime if job.get("status") == "in_progress"]
        queued = [job for job in runtime if job.get("status") == "queued"]
        for job in runtime:
            if job.get("started_at") and runner_started is None:
                runner_started = job["started_at"]
        completed_names = [job.get("name", "").lower() for job in runtime if job.get("status") == "completed"]
        expected_android_gates = {gate for gate in required_gates if gate != "gemini-home-v2"}
        android_done = all(any(gate in name for name in completed_names) for gate in expected_android_gates)
        gemini_done = "gemini-home-v2" not in required_gates or any("nh01" in name for name in completed_names)
        if runtime and android_done and gemini_done and not queued and not running:
            print("API28_QUEUE_MONITOR=RUNTIME_GATES_COMPLETED")
            return 0
        if not runtime:
            builds = [job for job in jobs if "build" in job.get("name", "").lower()]
            if builds and all(job.get("status") == "completed" for job in builds):
                if any(job.get("conclusion") != "success" for job in builds):
                    print("API28_QUEUE_MONITOR=BUILD_FAILED_BEFORE_RUNTIME")
                    return 0
                build_finished = [parse_time(job["completed_at"]) for job in builds if job.get("completed_at")]
                if build_finished:
                    build_gate_start = max(build_finished)
                    if start_at is None or build_gate_start > start_at:
                        start_at = build_gate_start
                        queue_started = start_at.isoformat(timespec="seconds").replace("+00:00", "Z")
                    waited = (datetime.now(timezone.utc) - start_at).total_seconds() / 60
                    if waited >= max_wait:
                        gate_results, apk_sha = summarize_gate_artifacts(args, batch_record)
                        persist_timeout(args, batch_record.get("batch_id", args.batch_id), queue_started or utc_now(), runner_started, gate_results, apk_sha, jobs)
                        print(f"API28_QUEUE_STATUS=BLOCKED_INFRA waited_minutes={int(waited)} runtime_job=NOT_CREATED")
                        return 0
        if queued and not running:
            created = [check_created_at(args.token, job.get("check_run_url")) for job in queued]
            created = [value for value in created if value]
            created_times = [parse_time(value) for value in created]
            queued_at = min(created_times) if created_times else datetime.now(timezone.utc)
            completed_times = [parse_time(job["completed_at"]) for job in runtime if job.get("status") == "completed" and job.get("completed_at")]
            completed_floor = max(completed_times) if completed_times else None
            current_queue_start = max(queued_at, completed_floor) if completed_floor and completed_floor > queued_at else queued_at
            if start_at is None or current_queue_start > start_at:
                start_at = current_queue_start
                queue_started = start_at.isoformat(timespec="seconds").replace("+00:00", "Z")
            waited = (datetime.now(timezone.utc) - start_at).total_seconds() / 60
            if waited >= max_wait:
                gate_results, apk_sha = summarize_gate_artifacts(args, batch_record)
                persist_timeout(args, batch_record.get("batch_id", args.batch_id), queue_started or utc_now(), runner_started, gate_results, apk_sha, jobs)
                print(f"API28_QUEUE_STATUS=BLOCKED_INFRA waited_minutes={int(waited)}")
                return 0
        elif running:
            start_at = None
            queue_started = None

        if (datetime.now(timezone.utc) - observe_started).total_seconds() >= args.observe_minutes * 60:
            print("API28_QUEUE_MONITOR=HANDOFF_REQUIRED")
            return 0
        time.sleep(args.poll_seconds)


if __name__ == "__main__":
    raise SystemExit(main())
