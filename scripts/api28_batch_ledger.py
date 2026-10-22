#!/usr/bin/env python3
"""Maintain appendable candidate and batch records on the dedicated ledger branch."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys

try:
    from .work_lifecycle import candidate_set_key, reconcile_queue, assert_public_ledger_paths
except ImportError:  # direct script execution
    from work_lifecycle import candidate_set_key, reconcile_queue, assert_public_ledger_paths


def read_json(path: Path, default):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else default


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def prepare(args) -> int:
    ledger = args.ledger_dir / "ledger"
    queue_path = ledger / "queue.json"
    batch_dir = ledger / "batches"
    batch = read_json(args.batch_input, {})
    current = read_json(args.current_input, [])
    observed = set(int(value) for value in read_json(args.observed_prs, []))
    if not isinstance(batch, dict) or not batch.get("batch_id"):
        raise SystemExit("batch input must include batch_id")
    if not isinstance(current, list):
        raise SystemExit("current candidate input must be an array")

    previous_queue = read_json(queue_path, {"schema_version": "1.1", "records": []})
    records = previous_queue.get("records", [])
    if not isinstance(records, list):
        raise SystemExit("ledger queue records must be an array")
    now = batch.get("queue_started_at") or iso_now()
    next_records = reconcile_queue(records, current, now=now, observed_prs=observed)
    write_json(queue_path, {"schema_version": "1.1", "updated_at": now, "records": next_records})

    eligible = batch.get("eligible_candidates", [])
    key = candidate_set_key(batch["base_sha"], batch["scope"], eligible) if eligible else None
    duplicate_of = None
    if key:
        for prior_path in sorted(batch_dir.glob("*.json")):
            prior = read_json(prior_path, {})
            if prior.get("candidate_set_key") == key and prior.get("batch_status") in {"PASS", "CORE_PASS"}:
                duplicate_of = prior.get("batch_id")
                break
    batch["candidate_set_key"] = key
    batch["duplicate_of"] = duplicate_of
    batch["batch_status"] = "DUPLICATE_SKIPPED" if duplicate_of else batch.get("batch_status", "PREPARED")
    batch["ledger_updated_at"] = now
    write_json(batch_dir / f"{batch['batch_id']}.json", batch)
    write_json(args.batch_output, batch)
    write_json(args.queue_output, {"schema_version": "1.1", "updated_at": now, "records": next_records})
    print(f"QUEUE_RECORDS={len(next_records)}")
    print(f"ELIGIBLE_COUNT={len(eligible)}")
    print(f"DUPLICATE_OF={duplicate_of or ''}")
    return 0


def finalize(args) -> int:
    ledger = args.ledger_dir / "ledger"
    batch_path = ledger / "batches" / f"{args.batch_id}.json"
    batch = read_json(batch_path, None)
    if not isinstance(batch, dict):
        raise SystemExit(f"missing prepared batch record: {args.batch_id}")
    final = read_json(args.final_input, {})
    if not isinstance(final, dict):
        raise SystemExit("final result must be an object")
    batch.update(final)
    batch["ledger_updated_at"] = iso_now()
    write_json(batch_path, batch)

    queue_path = ledger / "queue.json"
    queue = read_json(queue_path, {"schema_version": "1.1", "records": []})
    records = queue.get("records", [])
    by_key = {(int(row.get("pr_number", 0)), row.get("head_sha")): row for row in records}
    batch_status = batch.get("batch_status")
    for item in batch.get("integrated", []):
        row = by_key.get((int(item["pr_number"]), item.get("head_sha")))
        if row is None:
            continue
        row["last_batch_id"] = batch["batch_id"]
        row["last_integration_sha"] = batch.get("integration_sha")
        row["last_batch_result"] = batch_status
        row["independent_pr_result"] = "NOT_TESTED"
        if batch_status == "BLOCKED_INFRA":
            row["status"] = "BLOCKED_INFRA"
            row["reason"] = batch.get("reason", "API28 runner or infrastructure unavailable")
        elif batch_status == "FAIL":
            row["status"] = "FAIL"
            row["reason"] = "integration candidate failed one or more required gates"
        elif batch_status in {"PASS", "CORE_PASS"}:
            row["status"] = "INTEGRATION_PASS_UNMERGED" if batch_status == "PASS" else "CORE_PASS_UNMERGED"
            row["reason"] = "integration result belongs to the recorded Integration SHA; component PR is not independently passed"
        else:
            row["status"] = batch_status or "NOT_RUN"

    for item in batch.get("blocked", []) + batch.get("deferred", []):
        row = by_key.get((int(item.get("pr_number", 0)), item.get("head_sha")))
        if row:
            row["status"] = item.get("status", "BLOCKED")
            row["reason"] = item.get("reason") or item.get("status")
            row["updated_at"] = batch["ledger_updated_at"]

    queue["updated_at"] = batch["ledger_updated_at"]
    write_json(queue_path, queue)

    paths = [str(path.relative_to(args.ledger_dir)).replace("\\", "/") for path in args.ledger_dir.rglob("*") if path.is_file()]
    forbidden = assert_public_ledger_paths(paths)
    if forbidden:
        raise SystemExit("forbidden live-state files in public ledger: " + ", ".join(forbidden))
    print(f"BATCH_STATUS={batch_status}")
    print(f"LEDGER_BATCH={batch_path}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    prep = sub.add_parser("prepare")
    prep.add_argument("--ledger-dir", type=Path, required=True)
    prep.add_argument("--batch-input", type=Path, required=True)
    prep.add_argument("--current-input", type=Path, required=True)
    prep.add_argument("--observed-prs", type=Path, required=True)
    prep.add_argument("--batch-output", type=Path, required=True)
    prep.add_argument("--queue-output", type=Path, required=True)
    prep.set_defaults(run=prepare)
    finish = sub.add_parser("finalize")
    finish.add_argument("--ledger-dir", type=Path, required=True)
    finish.add_argument("--batch-id", required=True)
    finish.add_argument("--final-input", type=Path, required=True)
    finish.set_defaults(run=finalize)
    args = parser.parse_args()
    return args.run(args)


if __name__ == "__main__":
    raise SystemExit(main())
