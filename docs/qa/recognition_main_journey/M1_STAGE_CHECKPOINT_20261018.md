# M1 Stage Checkpoint — 2026-10-18

## Scope and source identity

- Canonical baseline remains `main@0d3238e8a41b87363e9a17b04c9cc45fca4c426d`.
- PR #163 remains Draft/Open/Unmerged at `08263fd378c6b2d51fc6b9a48df7c54daeeccd4f`.
- PR #162 remains Draft/Open/Unmerged at `1d948b45a3ebc759acef72dd1f81769e9e832d0f`.
- The experienceable two-page M1 candidate is `fix/m1-experienceable-two-page-v1@52f411b3625dcf4edf03f5382794a3296bb39cd8`. It combines the existing M1 source with the approved two-page candidate from PR #162. This candidate is not main or either PR head.

## Runner diagnosis

- Minimal hosted-runner probe commit: `850a3a8330489a433ce308c1d612d676ec7d12ab`.
- Run [39119043478](https://github.com/pan277942135/Yujian_App/actions/runs/39119043478) executed on GitHub-hosted `ubuntu-24.04.5` / x64.
- The runner reports an AMD `svm` CPU flag and `/dev/kvm` exists as `root:kvm` mode `0660`; the job's read/write test fails. Probe result: `KVM_STATUS=BLOCKED_INFRA`.
- Probe artifact: [12133661634](https://github.com/pan277942135/Yujian_App/actions/runs/39119043478/artifacts/12133661634), ZIP digest `15493824735341e1b63e09b65200802dabb3a8aaf4f78719f542e3f2497c5e79`.
- No additional same-source hosted KVM build was started.

## Two-page candidate and build evidence

- The candidate uses P01 portrait-phone/horizontal-fish and P02 landscape-phone/horizontal-fish. Active P02 reuses the former P03 Frozen PNG byte-identically; its SHA-256 is `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`.
- The first candidate CI attempt at `3a37b262a3423f4373b3b9fa774d3fd741aa9a41`, Run [39119180816](https://github.com/pan277942135/Yujian_App/actions/runs/39119180816), failed because the fake-ADB contract fixture still referenced the retired old P02 image path. The failure was preserved.
- Commit `52f411b3625dcf4edf03f5382794a3296bb39cd8` updates that fixture to the active landscape P02 path.
- Android CI Run [39119488635](https://github.com/pan277942135/Yujian_App/actions/runs/39119488635), build job `117651986481`: **PASS** at exact source SHA `52f411b3625dcf4edf03f5382794a3296bb39cd8`. Unit tests, camera runtime harness contract tests, debug APK assembly, AndroidTest APK compilation, asset packaging checks and lint passed.
- The installable app APK SHA-256 is `44b63e8c49955f9be021276d9397782f1269685e7cc27fb0c2935e2698b1a3c8`.
- Download: [YuJian-debug artifact 12133133667](https://github.com/pan277942135/Yujian_App/actions/runs/39119488635/artifacts/12133133667). Artifact ZIP digest: `6c91190b680ee9c832cc748a0916373ebd5a5cd15bdaea91e21c508c0d8da20d`.
- APK pair for a later Runtime run: [artifact 12133313073](https://github.com/pan277942135/Yujian_App/actions/runs/39119488635/artifacts/12133313073), ZIP digest `49109b81f217fd512efadfb199cce44d0f1cb207fc29d93f1cfa7872aafeeb9d`.

## Runtime disposition

- The Android CI workflow started eight API28 matrix jobs after the build. The run was cancelled after the build and APK artifacts completed, to stop redundant Runtime attempts on the diagnosed blocked runner. The build job passed; all eight device jobs are **CANCELLED / NOT_RUN**.
- No API28 screenshots, boundsInWindow, Pixel Diff, motion trace/MP4, or on-device journey evidence is claimed for SHA `52f411b3625dcf4edf03f5382794a3296bb39cd8`.
- Gate A: **REVIEW_REQUIRED** — two-page synchronization remains Draft; main and PR #163 still show the older three-page state.
- Build/unit/contract checks: **PASS** for the exact candidate SHA. Current-sha Product Work Admission/recovery: **NOT_RUN**.
- Gate C: **BLOCKED_INFRA**. Gate D: **REVIEW_REQUIRED**. Release: **BLOCKED**. No `M1_RELEASE_PASS`.

## Remaining blockers

1. API28 Runtime needs an existing KVM-capable Linux developer workstation with accessible `/dev/kvm`, JDK 17, Gradle 8.9, and the API28 x86_64 Pixel_2 AVD at 1080×1920. This is the no-cost replacement environment; the existing Runtime Harness and exact candidate APK pair can be reused.
2. The two-page proposal is implemented in the experienceable candidate branch but remains Draft in PR #162; it is not effective on main or the unchanged PR #163 head.
3. First-use guide Hero/error runtime photos still lack approved independent source/rights evidence.
4. PROC-07 timeout remains `BLOCKED_PARAMETER`; it must use an approved value and is not inferred from visual minimum durations.
5. Visual geometry/ROI, motion, Accessibility, and save/detail/back Runtime acceptance remain unexecuted for the candidate SHA.

## Next action

Install the candidate APK for product experience. When an existing KVM-capable workstation or API28 Pixel_2 device is available, run the existing Runtime Harness once against source SHA `52f411b3625dcf4edf03f5382794a3296bb39cd8` and its APK pair; then bind all resulting Runtime evidence to that exact SHA. Keep PR #162 and PR #163 Draft and keep main unchanged.
