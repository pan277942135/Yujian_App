# Design Authority Decision — PR #145 V3.1 Reconciliation

| Field | Value |
|---|---|
| Decision ID | `AUTH-PR145-20261016-01` |
| Status | `REVIEWED / CONDITIONALLY APPROVED — EXACT-HEAD CHECKS REQUIRED BEFORE MERGE` |
| Baseline main | `2d7a75efba0ca1b66fc9be1e0860072a5f1b8a5d` (after PR #150) |
| Source Work checkpoint | `5701512dad61fa2b634293a542804c60b70ce7b3` |
| Product PR | `#145` — Draft / Open / Unmerged |
| Control Plane PR | `#151` |
| Review source | Prior blocking review on PR #151, exact file diffs, original Work Task V1, effective registry, Empty Home / Android CI failure logs |

## Path-level authority disposition

| Authority root | Added | Modified | Reviewed disposition |
|---|---:|---:|---|
| `design/system_v3/` | 153 | 1 | Approve additive V3.1 authority package and synchronize first-use-guide entry in the existing effective registry to three-page V1. Do not overwrite pre-existing Frozen PNGs or tokens. |
| `design/pages/` | 20 | 2 | Approve new guide, evidence, archive and V3.1 page assets; approve the additive RR08 Result navigation/menu links only. |
| `design/registry/` | 0 | 1 | Approve the three-page first-use guide Design Manager navigation and SHA declarations. |
| `design/governance/` | 1 | 0 | This decision record and review trail. |

Overall expected PR delta after reconciliation: 178 files: 174 added / 4 modified. The counts are a proposed post-fix diff and must be confirmed against the remote PR file list before merge.

### Empty Home — explicitly deferred to product runtime PR #145

The original authority proposal modified these two pre-existing V2.2 contracts without including corresponding packaged Android Runtime changes:

- `design/pages/home/empty_home/shared/contracts/responsive_mapping_contract.json`
- `design/pages/home/empty_home/shared/contracts/runtime_manifest.json`

This caused exact failures in **Empty Home V2 Design Assets** run `38911215172` and **Android CI** run `38911215197`: the responsive contract and manifest diverged from packaged Runtime.

**Decision:** Restore both files in PR #151 byte-for-byte to the current `main` blobs (`2ef308d0bf8d9f07319e88a6b1bbba80ce4afb7f` and `24fd2f74908f5cda1b704fd5cce074db0d183a8a`, respectively). Keep the revised Empty Home contract together with the matching Android implementation on product PR #145, subject to synchronized Exact-HEAD gates; do not suppress a validator or backport product assets into the Control Plane PR. This is a **defer**, not a rejection of the runtime revision.

### First-use Camera Guide — active three-page V1

The original `docs/qa/recognition_main_journey/WORK_TASK_FIRST_USE_CAMERA_GUIDE_V1.md` on PR #145 explicitly specifies **P01/P02/P03** and prohibits promotion of the historical two-page proposal. This is also consistent with `First_Use_Camera_Guide_Spec_V1.md`, `Freeze_Decision_20261016.md`, `Scope_Resolution_20261016.md`, `manifest.json`, `navigation.json`, the Capture authority and the product journey contract.

| Page | Phone orientation | Fish orientation | Bytes | Declared SHA-256 | Git blob |
|---|---|---|---:|---|---|
| P01 | portrait | horizontal | 1,988,159 | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` | `3f73f744104777beee29bd2370ecac1f40197a1d` |
| P02 | portrait | vertical | 2,042,852 | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` | `9c2dd981a04792c6ac51e3925727b4e4e6704034` |
| P03 | landscape | horizontal | 2,001,322 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | `2f46b2f2906f258eef8ca67181fc049a55d045af` |

The three assets' Git blob identities were read from the remote PR #151 files and matched the asset import report. **The SHA-256 values above are recorded source evidence; independent raw binary SHA-256 recomputation is not claimed here.** That verification must be performed using actual PNG bytes in CI / a byte-capable runner before final evidence sign-off.

**Decision:** The active Effective Authority Registry must include P01/P02/P03 and state the three-page sequence, with the same approved manifest and freeze decision. The historical V1.1/V2 two-page proposal remains under historical status and must not drive runtime implementation.

The Frozen full-page PNGs authorize Design Manager design display **only**; Android must use CameraX + Compose with independently sourced photo examples. Design approval is **not** Android Runtime approval.

### RR08 and V3.1 shared design package

The Result navigation and menu changes add a visual acceptance contract index (RR08), not a new Result product state. The 153 newly introduced V3.1 system authority files remain additive; no existing `main` Frozen PNG, SVG, token, or source asset SHA path is overwritten by this branch. Asset manifests remain source references without rewriting Frozen artwork.

## Mandatory merge gates

1. Verify final diff scope and that no `app/`, packaged Runtime, existing Frozen overwrite, or validator bypass entered PR #151.
2. Re-run on the **new exact head**: Control Plane Admission, Design Governance, Normal Home Spec Governance, Empty Home V2 Design Assets, and Android CI. An old successful run is not sufficient.
3. Verify P01/P02/P03 original PNG SHA-256/size/geometry from raw bytes in a trusted runner; inspect all three Design Manager authority bindings. Lack of a raw-asset check is a remaining evidence limitation, not a PASS.
4. Confirm main SHA immediately before merge. Merge PR #151 only after all required checks and the final authority review are satisfied.
5. Then synchronize Draft PR #145 from the **new** main SHA; re-run Project Master State and full Control Plane Admission, and continue N001 only after they pass.

**Boundary:** Do not merge PR #145 or declare N001, API28 Runtime, APK SHA, or Release PASS from this authority approval.
