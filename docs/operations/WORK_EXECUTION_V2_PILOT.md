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

## Limits

This pilot only modifies two pull-request path filters, not push triggers, deployment workflows or model factory code. The environment check reports capabilities but does not prove access to actual datasets or start training. Next work is integration with the existing Work execution state and actual workflow scheduler.
