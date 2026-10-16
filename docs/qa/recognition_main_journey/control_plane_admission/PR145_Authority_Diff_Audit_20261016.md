# PR #145 Control Plane and Design Authority Audit — 2026-10-16

## Snapshot

- Repository: `pan277942135/Yujian_App`
- Canonical `main`: `ae7f492a649d20dc23e7f04bcffa49749c7d9e70`
- PR #145: `5ea871cda79d6c9a14f184d4ee8de04d67c0909a` — Draft / Open / Unmerged at audit time
- PR #145 contains a regular merge of current `main`; merge-base is the main SHA above.
- Compared files: canonical static paths declared by both branch registries, plus `design/governance/`, `design/registry/`, `design/pages/`, and `design/system_v3/`.
- The matrix records exact Git blob SHAs and path identity. All comparison paths and their related task/contract/gate impact are listed in the CSV.

## Diff summary

| Result | Files |
|---|---:|
| Same path and Git blob | 614 |
| Identical bytes at moved path | 0 |
| Missing in PR #145 | 0 |
| Divergent | 2 |
| PR-only canonical/design paths | 0 |
| Total rows | 616 |

The two divergent paths are not unexplained drift: both PR blobs exactly match the non-frozen, content-addressed exceptions in main’s `Asset_Integrity_Register_V3_1.json`. The current `validate_control_plane_sync.py` recognizes those registered exact blobs. Preserve them and let the validator verify the exception; do not copy main over them. No unapproved divergence was found in the scoped matrix. This read-only comparison does not itself establish product Admission.

## Approved non-frozen worktree exceptions

| Path | PR #145 Git blob | Main approved SHA | Impact | Safe action |
|---|---|---|---|---|
| `design/pages/home/empty_home/shared/contracts/responsive_mapping_contract.json` | `4eaf3b7c17b8c4d93503bda5ade4537ac1326855` | `4eaf3b7c17b8c4d93503bda5ade4537ac1326855` | N001 (P8), `home_empty_v2`; responsive mapping; `G-CONTROL-PLANE-SYNC` and N001 / `G-API28-MATRIX` | Preserve exact PR blob; validator confirms content-addressed exception. |
| `design/pages/home/empty_home/shared/contracts/runtime_manifest.json` | `1d4a386373a080fc73fea071331e19fd11d968ee` | `1d4a386373a080fc73fea071331e19fd11d968ee` | N001 (P8), `home_empty_v2`; runtime manifest; `G-CONTROL-PLANE-SYNC` and N001 / `G-API28-MATRIX` | Preserve exact PR blob; validator confirms content-addressed exception. |

## Frozen asset integrity

The PR #145 design tree contains 259 image/vector/PDF files. The path/name and prior manifest classification identifies 51 Frozen or archived binaries; their source bytes, SHA-256, byte count and Git blob SHA are recorded in the companion inventories. Frozen matches main at the same path: 51 / 51.

The two-page first-use guide decision (P01 portrait phone + horizontal fish; P02 landscape phone + horizontal fish) remains a separate proposed canonical change in the control proposal branch. No Figma operation was performed.

## Work State and Admission boundary

PR #145's branch-local state is not reused as fresh execution evidence for this task. After the canonical proposal is approved and synchronized, create new branch-local `PROJECT_MASTER_STATE.yaml` and root `WORK_EXECUTION_STATE.json` for the actual Task/Phase and the then-current source SHA.

This audit does not establish product `CONTROL_PLANE_ADMISSION=PASS`. Product Admission requires approved canonical authority, ordinary synchronization, fresh branch-local state, and all required validators passing on exact PR #145 head.

## Figma and source status

MB-05 remains `SOURCE_MISSING` for `a_realistic_high_resolution_vertical_smartphone_c.png` (expected SHA-256 `6d67d03045f5f3dfb66adad10921d7456a2c82620b7b68f156cb98be74f1908c`). No substitute was generated. The Master Board remains `PARTIAL` until that exact source is recovered.

## Preservation backup

- Full history bundle: `PR145_full_history_5ea871cda79d6c9a14f184d4ee8de04d67c0909a.bundle`, SHA-256 `0584edeb40bff6c7668b4a31ca14bc642e70a26127446424dc09a0365d79f357`.
- Design patch against current main: `PR145_design_changes_5ea871cda79d6c9a14f184d4ee8de04d67c0909a.patch`, SHA-256 `e10ce2b39b71243133e6b82c2302d89f575bb9cc459260a52692cde7cfd7929c`.
- Earlier snapshots remain in backup storage and Git history; this report supersedes them for the current PR #145 head.
