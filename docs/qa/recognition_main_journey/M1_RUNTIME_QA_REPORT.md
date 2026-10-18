# M1 Runtime QA Report

Parent: YUJIAN_M1_PARALLEL_DELIVERY_V1 / PR #163
Audited initial remote source SHA: 81aefb1c0eae08df5ea6f451fc7c65be89a9c191
Current published candidate SHA: 6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e
APK SHA-256: NOT_RUN
GitHub Actions: Android CI Run `39093186726` (unit tests pass, AndroidTest compile failure); M1 Hosted API28 Runtime Run `39093186784` (license pipeline failure before Preflight).
Device: hosted Ubuntu 24.04 / API28 x86_64 Pixel 2 / 1080×1920 is configured; no emulator was launched.

## Individual N001–N010 classifications

| Gate | Result | Current evidence |
|---|---|---|
| N001 · API28 Runtime Matrix | BLOCKED_INFRA | M1 Hosted API28 Runtime run 39093186784 passed the scope guard and sdkmanager path resolution, then failed at the SDK license pipeline (`yes` Broken pipe under pipefail); API28 Preflight and formal matrix jobs were skipped. No Runtime PASS. |
| N002 · Recognition Frozen | NOT_RUN | V3.1 and guide assertions are in Track A/C candidate worktree; no exact-SHA API28 result. |
| N003 · J01–J20 journeys | NOT_RUN | Full journey execution record is absent; no candidate Runtime execution. |
| N004 · Normal Home A4.1 | NOT_RUN | P1 gate is not executed on candidate SHA; source fixture/provenance and device evidence remain outstanding. |
| N005 · Evidence package | NOT_RUN | No candidate screenshots, boundsInWindow, ROI Pixel Diff, Motion Trace, MP4, device profile artifact or runtime logs. |
| N006 · APK provenance | NOT_RUN | Build Once run 39093186784 stopped before Gradle/build; Android CI assembled Debug APK but AndroidTest compilation failed before staging/upload. No candidate APK pair, artifact, or exact APK SHA-256 exists. |
| N007 · Phase 3-B provenance | REVIEW_REQUIRED | Matrix records provenance/source gaps; no exact SHA mapping or independently accepted authority evidence. |
| N008 · Focused report | NOT_RUN | Reports describe static/audit evidence; no exact Runtime gate summary or APK provenance. |
| N009 · Human release | NOT_RUN | Release package is incomplete; no manual approval requested or granted. |
| N010 · First-use Guide asset authority | REVIEW_REQUIRED | Approved M1 decision is two pages, but static N010 still requires three PNGs; PR #162 is Draft and not integrated, so authority sync must be resolved. |

## Journey and catalog status

The catalog validator reports 66 base cases and 10 E2E scenarios with valid structure. Every execution result remains NOT_RUN. This is contract validation only.

| Journey family | Case status |
|---|---|
| J01–J02 · Empty Home/entry | NOT_RUN |
| J03–J10 · Permission, guide, capture, processing, recognition exceptions | NOT_RUN |
| J11–J16 · Result, edit, save and error recovery | NOT_RUN |
| J17–J18 · Home return, persistence and recreation | NOT_RUN |
| J19–J20 · FishRecordDetail A/B | NOT_RUN |
| M1-SUPP-SAVE-DETAIL-01 · Save → Home → same record A/B → Back | NOT_RUN; AndroidTest not yet compiled/run |

## Hosted runner and formal gate status

| Result | Classification |
|---|---|
| Build Once APK pair | FAIL — run 39093186784 at source `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e`; SDK manager resolved but license pipeline failed under pipefail |
| API28 hosted Preflight | NOT_RUN — job skipped after Build Once failure |
| Recognition Frozen | NOT_RUN |
| Camera Runtime | NOT_RUN |
| Empty Home | NOT_RUN |
| Normal Home | NOT_RUN |
| Login | NOT_RUN |
| Data Sanitization | NOT_RUN |
| Fish Guide | NOT_RUN |
| Visual Geometry / Glyph / ROI Pixel Diff | NOT_RUN |
| Motion timing / trace / MP4 | NOT_RUN |
| Accessibility | NOT_RUN |
| Independent review | NOT_RUN |

The CAPTURED timeline assertion failure from Android CI run 39092555645 was corrected and passed among all 211 JVM tests on source 6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e (Run 39093186726). The same run passed Camera Runtime contract tests and assembled Debug APK, then failed AndroidTest compilation at `fishFocusStableMs`, the `assertDoesNotExist` import, and the missing `onAllNodesWithTag` import; no artifact was uploaded. Hosted run 39093186784 passed C1 scope and SDK manager resolution but failed at `yes | sdkmanager --licenses` due Broken pipe under pipefail; API28 jobs skipped. Minimal corrections are staged for the next exact SHA. Run 39086281726 remains historical and is not reused. C01, C02 trace/Resolve, the 658/669 Page Contract validator failure and C03 hosted environment remain open.


## Latest exact-SHA checkpoint

## Exact-SHA update — ffe285aff3818435815f769532fa221d3157917e

- Android CI Run 39093846259: PASS (unit/controller and camera contract tests, Debug APK, AndroidTest compilation, package/lint steps).
- M1 Hosted API28 Runtime Run 39093844697: Build Once PASS; APK artifact https://github.com/pan277942135/Yujian_App/actions/runs/39093844697/artifacts/12124400453. App SHA-256 `bd98a57334d278e9a3a5129e9d344d8e0de4faf058b7294ee614921664058b3e`; AndroidTest APK SHA-256 `249334802bd6ffe7084b9b96b604123db3b7b171640eb86b18344fa3a51749bb`.
- API28 Preflight: FAIL/BLOCKED_INFRA at `test -c /dev/kvm -a -r /dev/kvm -a -w /dev/kvm`; the emulator, SDK/API assertion, 1080×1920 display, installation, CameraX, Instrumentation, screenshots and formal runtime jobs did not start. Formal `api28-runtime` is SKIPPED.
- N001 BLOCKED_INFRA; N002–N006 NOT_RUN; N007 REVIEW_REQUIRED; N008 NOT_RUN; N009 NOT_RUN; N010 REVIEW_REQUIRED because canonical and PR #163 guide remain three-page while Draft PR #162 contains the approved two-page proposal.
- Catalog shape validation, current-source execution, and Build Once packaging remain distinct. No existing 66+10, J01–J20 or save/detail test has an API28 execution result at this SHA.


## Exact-SHA result — 85dfe92

At 85dfe9217e879e0f87d19b41801beb05e502aaa2, Android CI Run 39095194583 passed build/unit/contract/AndroidTest compile and packaging. Hosted API28 Run 39095194693 Build Once passed and uploaded artifact 12124820081 (ZIP digest `ab298c711af40ef00cc9820e6e60926687b67ed67736d1454a2c15b18c253c97`). Preflight failed the KVM character/read/write assertion. No device started; formal Runtime, 66/10 execution, J01–J20, screenshots, bounds, Pixel Diff, Motion Trace or MP4 remain NOT_RUN. N001 is BLOCKED_INFRA; N009 NOT_RUN; N010 REVIEW_REQUIRED.
