# GitHub Continuous Delivery Guard V1

This helper protects branch-local work around GitHub transport failures. It does
not replace Control Plane Admission, Work V2 failure accounting, branch rules,
or exact-head CI. It never force-pushes and never stores credentials.

## Writer and checkpoint location

The active Work records one canonical writer worktree in
`PRODUCTION_WORKSPACE_LOCK.json`. `delivery_guard.py` serializes commit, push,
and queue-resume operations through a file lock and rejects a different writer
path or branch. Keep the checkpoint root on persistent task storage, outside
the repository. Do not copy APKs, private photos/videos, model files, Evidence
ZIPs, tokens, or build caches into it.

## Stage sequence

1. Read `CONTROL_PLANE_REGISTRY.yaml`, refresh `origin/main`, and run
   `python scripts/validate_control_plane_admission.py`.
2. Run `python tools/work_execution_v2/failure_gate.py check --state WORK_EXECUTION_STATE.json --event <current-gate-event.json>`.
   The event must use the active Work's exact `task_id` and `source_sha`, with
   a stable `gate_id`, `symptom_key`, hypothesis, and evidence reference. A
   stop result is final for that failure key; this tool cannot reset it.
3. Run issue-specific targeted and cross-layer tests. Record the root cause,
   source SHA, test commands, test report, Admission result, and safe evidence
   reference in a JSON manifest.
4. Run `delivery_guard.py commit-stage` with the exact changed path list. The
   command runs the tests, checks whitespace and staged scope, scans staged
   files for credential material, and creates one commit with Issue,
   Root-Cause, Source-SHA, Parent-SHA, Tests, and Evidence trailers. A test
   failure creates a recoverable checkpoint and no source commit.
5. Update the approved branch-local Work state to point to the code commit,
   create its state-only checkpoint commit, and rerun Admission. Keep the
   product-source SHA separate from the checkpoint commit SHA.
6. Run `delivery_guard.py push-stage` with metadata naming only the authorized
   PR branch. The helper fetches the target, verifies ancestry, runs a
   non-mutating dry-run, performs a normal push, and rereads the remote SHA.
7. Record exact-head CI with `record-ci`. A CI failure is retained with
   `continue_research=true`; it does not close the Master Goal.

## Push failure and resume

Each failed stage writes a timestamped directory containing
`goal_checkpoint.bundle`, `pending_commits.patch`, `pending_commits.json`,
`WORK_STATE.json`, `TEST_REPORT.md`, and `SHA256SUMS.txt`. The bundle is verified
before the checkpoint is reported. Safe, small text files may be copied under
`untracked/`; files with sensitive names/content, private media, APKs, models,
build output, symlinks, and files over the configured limit are skipped and
listed in the manifest. Existing untracked files are never deleted.

`PENDING_COMMITS_QUEUE.json` is the machine-readable queue. After the external
credential/network issue is resolved, run:

```bash
python tools/work_execution_v2/delivery_guard.py resume-queue \
  --repo /path/to/canonical/worktree \
  --checkpoint-root /persistent/path/to/checkpoints \
  --target-branch fix/26kf-path-boundary-and-fish-gate-v1
```

The command restores missing Git objects only from a verified bundle under the
checkpoint root. It refuses multiple branches, disconnected histories, dirty
writer worktrees, and unsafe divergence. A safe rebase is preceded by another
bundle and must pass recorded tests plus Admission before the ordinary push.
Missing test commands, a rebase conflict, a failed test, Admission failure,
authentication failure, network failure, or remote SHA mismatch stays queued
and is reported with a distinct status. No invalid credential is retried by
snapshot generation.

## CI continuation

```bash
python tools/work_execution_v2/delivery_guard.py record-ci \
  --checkpoint-root /persistent/path/to/checkpoints \
  --commit-sha <exact-40-character-sha> \
  --status FAIL --run-url <exact-run-url> --raw-log <saved-raw-log>
```

CI status is SHA-bound. `FAIL`, `UNAVAILABLE`, and `PENDING` are not converted
to PASS. Continue independent source diagnosis after a CI failure, while
respecting the repository failure gate and the Goal's cycle/time/commit limits.

## Local verification

```bash
python -m unittest tools/work_execution_v2/test_delivery_guard.py -v
```

The tests use temporary local Git repositories and bare remotes. They do not
use GitHub credentials, update a real remote, create a PR, or assert that a
push dry-run proves write permission.
