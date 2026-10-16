# DM01 · Design Manager 发布基线审计

- Audit date: 2026-10-16 (UTC)
- Repository: `pan277942135/Yujian_App`
- Local branch: `design/manager-high-fidelity-release-closure-20261016`
- Local HEAD / `origin/main`: `ae7f492a649d20dc23e7f04bcffa49749c7d9e70` (equal at audit)
- Control Plane admission: `PASS` (`scripts/validate_control_plane_admission.py`)

## GitHub and Authority state

| Item | Live state | Relevance |
|---|---|---|
| `main` | `ae7f492a649d20dc23e7f04bcffa49749c7d9e70` | Latest main at audit; local release branch starts here. |
| PR #145 | Open, Draft, Unmerged; base `main`; head `08ff3d96c61e9c0780b211b42f0e3bf0cc7d1dc4` | Product/runtime work remains isolated and untouched. |
| PR #150 | Merged; merge SHA `2d7a75efba0ca1b66fc9be1e0860072a5f1b8a5d` | Work-state validator repair. |
| PR #151 | Merged; Authority merge SHA `ebac477330350972ae05613fe7e8717f467eea8f` | Design V3.1 Authority set and related Registry changes. |
| PR #152 | Merged; merge SHA `ae7f492a649d20dc23e7f04bcffa49749c7d9e70` | Control Plane admission exception fix; no additional design-tree delta after #151. |

`AGENTS.md` directs all process admission through `CONTROL_PLANE_REGISTRY.yaml` and allows Project/Work execution state to remain branch-local. No root `PROJECT_MASTER_STATE.yaml` exists on `main`; no duplicate project authority is created by this release.

## Pages baseline

- Production URL: <https://pan277942135.github.io/Yujian_App/design/manager/>
- Visible build marker: `ebac47733035` (live browser inspection on 2026-10-16).
- Existing Pages run: [38916653740](https://github.com/pan277942135/Yujian_App/actions/runs/38916653740), success on `ebac477330350972ae05613fe7e8717f467eea8f`; the `build` and `deploy` jobs succeeded.
- Published `github-pages` artifact: 188,925,379 bytes, digest `sha256:8332efbc99951ec06b0d0d83e7b184575ad7c77b765df45699ca0e5be25fb178`.
- Direct URL `#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01` opens P01; existing menu already exposes P01/P02/P03 beneath `07 · 首次拍照教学`.
- Existing deployed files and latest `main` design tree agree for the Authority resources under review. The visible Manager build is behind the latest `main` marker, even though #152 changed only Control Plane files.

## Local / Registry / production comparison

| Area | Local latest `main` | Registry / Authority | Production baseline | Action |
|---|---|---|---|---|
| First-use guide | P01/P02/P03 Frozen PNGs, manifest and navigation present | Existing `recognition_flow_v1_2 → 07 · 首次拍照教学`; stable child IDs retained | P01 direct route and all three menu children work | Verify online byte hashes in deployment gate; do not recreate pages. |
| Journey overview | Approved PNG + SVG present | Registered as business-flow overview, not single-page authority | No independent overview route | Add a linked overview route and keep its scope disclaimer. |
| New-state board | PNG + SVG review source present | Partially superseded; review only | No review route or explicit status boundary | Expose as `PARTIAL`, never `FROZEN`. |
| Capture / permission / Master Board binaries | Five expected image outputs absent | Contracts or import gate record expected identity / pending import | No truthful preview exists | Show explicit missing/integrity state, no substitute and no broken link. |
| Preview controls / metadata | Existing code has no release modal, unified metadata panel, or full hash routes | Authority facts are in manifests/contracts | Not covered by prior Pages checks | Add original-ratio preview, zoom, original link, status/contract metadata, stable Hash routes. |
| Browser regression | No published browser evidence | Static validators cover Registry and basic route structure | Prior Pages run did not include browser/live-SHA checks | Add Chromium build and live tests with screenshots and SHA verification. |

## Release approach

1. Add manager presentation and contract coverage without changing source Frozen artwork or Android Runtime.
2. Run static, navigation, integrity, negative, Control Plane, build-artifact, and browser checks.
3. Publish through the existing Pages workflow on the merged design-only PR; record its exact commit and deployment run.
4. Keep final high-fidelity gate `BLOCKED` if the five absent source binaries remain absent. A successful Manager deployment does not make those assets present or Runtime-approved.
