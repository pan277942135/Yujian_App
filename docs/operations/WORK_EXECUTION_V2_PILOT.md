# Work Execution V2 — pilot

This document describes an optional executable pilot. The existing Control Plane Registry and Evidence Gate retain authority.

## Scope

- Bounded retry ledger keyed by task, gate and stable symptom, not by commit SHA.
- Environment preflight for Android, browser rendering and detector data work.
- Narrow PR-based NH01 and Android workflow path filters so standalone EdgeGlow files do not trigger unrelated emulator work.
- An isolated unit-test workflow validates these conditions.

## Validation

Run:
```bash
python3 -m unittest discover -s tools/work_execution_v2 -p 'test_*.py' -v
python3 tools/work_execution_v2/work_control.py preflight --profile android
python3 tools/work_execution_v2/work_control.py preflight --profile optical
python3 tools/work_execution_v2/work_control.py preflight --profile detector
git diff --name-only main...HEAD > changed_paths.txt
python3 tools/work_execution_v2/work_control.py route --repo-root . --changed-file changed_paths.txt
```

Failure events are recorded using the `failure` subcommand and must refer to actual evidence. Store the resulting ledger in an approved, durable checkpoint location. A ledger event never makes runtime or release PASS.

## Acceptance

1. Three occurrences of a stable failure across distinct commits yield one issue ID and an escalation decision on attempt three.
2. Optical-only changes do not trigger Android CI or NH01 runtime PR events.
3. Android app changes still select the required workflows.
4. New tests pass in GitHub Actions; previous Android and visual gates remain separate.
5. Keep the PR Draft and do not merge main until an authorized review.

## Stateful execution guard (new)

Use the existing branch-local WORK_EXECUTION_STATE.json (not a second state file).
Before **each** repeated-failure repair, create a real event JSON describing the
same task_id, gate_id, symptom_key, exact 40-char source_sha, current hypothesis
and evidence reference, then run:

```bash
python3 tools/work_execution_v2/failure_gate.py check --state WORK_EXECUTION_STATE.json --event failure-event.json
```

After the actual failed run, set result=FAIL in the event and record it:

```bash
python3 tools/work_execution_v2/failure_gate.py record --state WORK_EXECUTION_STATE.json --event failure-event.json
```

A return code of 3 is a mandatory stop: do not implement another repair
for the same failure ID. At the second failure the next attempt must change
hypothesis; at the third the Work status is BLOCKED and next_action becomes
an evidence-backed escalation. Fourth attempts are denied across source SHAs.
Reobserving the same source SHA is deduplicated. FIX_VERIFIED is a local
evidence record, never a Runtime/Release PASS.

The existing state validator now checks the optional execution_v2 ledger,
and the Work State JSON schema allows only three attempts per issue.
Persist that Work state only in the approved destination and follow the
existing State Update Protocol for related state/log/checkpoint changes.
Do not publish secrets or sensitive execution data in public GitHub.

## Superseded PR CI runs

Pull request events for Android CI and NH01 Runtime cancel older same-PR
runs when a new head arrives; manual and push Android CI runs remain
non-cancelling. This prevents a queue of stale PR heads. Any manually
requested exact-SHA release/evidence gate remains a separate process.

## Limits

This pilot only modifies two pull-request path filters, not push triggers, deployment workflows or model factory code. The environment check reports capabilities but does not prove access to actual datasets or start training. Next work is integration with the existing Work execution state and actual workflow scheduler.
