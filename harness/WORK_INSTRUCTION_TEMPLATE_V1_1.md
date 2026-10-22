# Work P0 — <Task title>

**Task ID:** `<CANONICAL_TASK_ID>`
**Repository:** `pan277942135/Yujian_App`
**Priority:** `P0`
**Mode:** `Live Repository Audit → Admission → Minimal Change → Exact-SHA Validation → Evidence Closure`

## 0. Mandatory live startup

1. Run `git fetch origin main --prune`; record branch, `HEAD`, working tree, and exact `origin/main` SHA.
2. Read `AGENTS.md` and `CONTROL_PLANE_REGISTRY.yaml` first, then follow its six indexed layers and synchronization protocol.
3. Read `harness/WORK_INSTRUCTION_PROTOCOL_V1_1.md`, `harness/STATE_UPDATE_PROTOCOL.md`, and all task/contract/evidence authorities returned by the registry.
4. Run `python scripts/validate_control_plane_admission.py`; save the real output. Do not implement or test after a failing or unavailable Admission.
5. On resume, repeat the live startup and Admission before using the branch-local checkpoint.

## 1. Scope and boundaries

- Goal: <measurable outcome>
- In scope: <files, components, gates>
- Out of scope: main merge, product PR merge, frozen contract changes unless explicitly authorized.
- Baseline SHA: <refresh at execution; this value is a hint only>
- Required gates: <list exact IDs>
- PR candidate metadata: `Task ID: <ID>`; `API28 gates: <comma-separated IDs>`; `Depends on PRs: <#numbers or none>`
- Dependencies/blockers: <canonical task IDs and current status>

## 2. Work sequence

1. Audit live repository, workflow triggers, exact source SHA, contracts, and existing evidence.
2. Record implementation and quick-check status separately.
3. Generate `USER_PREVIEW` only after all applicable cloud quick checks pass. Include source branch/SHA, Actions Run ID, artifact URL, APK SHA-256, real build/test/lint results, and `api28_status: NOT_RUN` unless a controlled batch actually ran.
4. Enqueue API28 only through the main-branch central scheduler and only with exact-SHA admission/quick-check evidence plus `api28-ready` approval.
5. Bind any user feedback using the feedback schema; fix, rerun quick checks, publish a replacement preview, and keep the feedback open until user retest.
6. Update branch-local state/checkpoint and preserve all failed or blocked evidence.

## 3. Acceptance and delivery

- Exact source SHA and branch:
- Control-plane revision:
- Admission result and output location:
- Build / unit / AndroidTest compile / lint / static-check results:
- Artifact class, Run ID, download URL, APK SHA-256:
- API28 status (`NOT_RUN`, `QUEUED`, `RUNNING`, `PASS`, `FAIL`, `BLOCKED_INFRA`):
- Batch ID and Integration SHA (if applicable):
- Component PR/head SHAs tested in the batch:
- Gate-by-gate results and evidence links:
- User feedback and retest state:
- Blockers and exact unblock condition:
- Next executable action:

Never label a USER_PREVIEW as API28 PASS, Release PASS, or formal acceptance. A batch PASS is bound to its Integration SHA only. Keep the PR Draft/Open/Unmerged unless the user separately authorizes another state.
