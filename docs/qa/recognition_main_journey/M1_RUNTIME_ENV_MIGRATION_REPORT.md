# M1 Runtime Environment Migration Report

Task: M1_RUNTIME_ENV_MIGRATION_V1  
Repository and PR: pan277942135/Yujian_App / [Draft PR #163](https://github.com/pan277942135/Yujian_App/pull/163)  
Status: BLOCKED_INFRA — exact-SHA workflow run 39093186784 at `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e` scheduled on ubuntu-24.04, passed scope enforcement and SDK manager path lookup, then failed because `yes | sdkmanager --licenses` returned Broken pipe under pipefail. API28 Preflight and Runtime jobs were skipped.

## Target environment

| Setting | Required value | Candidate configuration |
|---|---|---|
| Runner | GitHub-hosted standard runner | ubuntu-24.04 |
| Emulator | API 28, google_apis, x86_64, Pixel 2 | reactivecircus/android-emulator-runner@v2 |
| Display | Native 1080×1920 | Assert wm size; record density, insets and device profile |
| Build | JDK 17, Gradle 8.9 | Build Once job |
| APK | Debug APK and AndroidTest APK from one source SHA | SHA256SUMS plus source/APK manifest |
| Harness | Existing Android Runtime Harness | Camera gate preflight plus seven formal gates |

## Preflight design and current evidence

The candidate workflow .github/workflows/m1-hosted-api28-runtime.yml checks KVM, API28 image availability, Pixel 2 AVD startup, SDK 28, native 1080×1920, density and insets. It installs both APKs, runs CameraX and Instrumentation through the existing camera runtime gate, captures native screenshots and uploads the APK pair and evidence.

Run 39092555676 at source `1d98f48d59132d3d1ea9e226d0e32342459eb002` first failed SDK manager lookup. Repair source `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e` resolved the tool but hosted Run 39093186784 failed when `yes` received Broken pipe under pipefail; the preflight job and formal gates were skipped. Therefore none of the KVM/API28/Pixel 2/1080×1920/install/CameraX/instrumentation/artifact checks ran, and the environment remains BLOCKED_INFRA. The next local repair captures the `sdkmanager` pipeline’s rightmost exit status so expected `yes` SIGPIPE does not mask success; it awaits a new exact-SHA run.

## Build Once and evidence binding

Build Once checks out pull_request.head.sha, verifies source SHA and model snapshot binding, builds Debug APK and AndroidTest APK once, calculates each APK SHA-256 and uploads a manifest containing source SHA, workflow Run ID, runner and APK hashes. Preflight and each formal gate download and verify the same artifact pair.

The formal matrix uses existing recognition-frozen, camera-runtime-v1, empty-home-v2, normal-home-v1, login-v2, data-sanitization and fish-guide-v1 gates. Each returns an individual JSON classification. The catalog stays at 66 base cases and 10 E2E scenarios; these remain NOT_RUN until the applicable cases execute. Track C added a supplemental Save Catch → Detail A/B AndroidTest, not yet compiled or run.

## Historical runner and migration boundary

GCP run artifacts remain historical. PR #163 Android CI run 39086281726 built successfully but skipped Runtime jobs, so it proves neither hosted preflight nor product Runtime.

The canonical task machine still names self_hosted_api28_runner under P5-02. It was not edited in this product branch because that would bypass Control Plane authority and synchronization. The hosted workflow is not yet the approved P5-02 authority. The pre-existing android.yml and camera-runtime-harness.yml now use ubuntu-24.04, reactivecircus/android-emulator-runner@v2, Pixel 2 API28 profiles and ANDROID_HOME SDK paths. The first run failed SDK manager lookup; the path fallback succeeded on 6d16bb7, but the license pipeline then failed under pipefail. Its status handling correction is staged locally and remains unverified. The canonical P5-02 task-machine runner binding still names the retired pool and awaits governed Control Plane synchronization. Overall migration and official Runtime remain BLOCKED_INFRA.

Local migration validation parsed all three workflow YAML files and ran `bash -n` on every `run` and emulator action script block. The first Android workflow parse failure and the first hosted SDK manager lookup failure are preserved in `M1_LOCAL_REPAIR_EVIDENCE.md`. The workflow now searches the configured Android SDK command-line-tools path. No KVM/API28/Pixel 2/CameraX/Instrumentation/APK/artifact Preflight evidence exists; the Preflight job was skipped and remains NOT_RUN.

## Coverage difference

| Area | Before | Candidate configuration | Proven now |
|---|---|---|---|
| AVD creation | Self-hosted pre-created AVD and /opt/android-sdk | Hosted Pixel 2 created by android-emulator-runner@v2 | NOT_RUN; Build Once failed before Preflight |
| APK production | Existing Build Once artifact | Exact PR head checkout, Build Once APK pair and source manifest | Hosted run 39093186784 failed before Gradle; Android CI 39093186726 built app APK but failed AndroidTest compile before staging/upload. No artifact. |
| Runtime assertions | Existing Runtime Harness gates | Reused without removing assertions; gates run after successful Preflight | Not yet; Runtime NOT_RUN |
| Evidence | Existing gate artifacts | Per-gate source/APK/Run/device manifest, screenshots and required logs | Not yet; no artifact |

No old GCP result is mixed into hosted evidence. This configuration report or a Build PASS is not a migration PASS.


## Latest exact-SHA checkpoint

## Latest exact-SHA runner result

Run 39093844697 checked out source ffe285aff3818435815f769532fa221d3157917e on `ubuntu-24.04`. Build Once successfully produced Debug and AndroidTest APKs with the following SHA-256 values:

- App: `bd98a57334d278e9a3a5129e9d344d8e0de4faf058b7294ee614921664058b3e`
- AndroidTest: `249334802bd6ffe7084b9b96b604123db3b7b171640eb86b18344fa3a51749bb`

Preflight then failed the required `/dev/kvm` character/read/write assertion on the standard GitHub-hosted runner. The job uploaded a BLOCKED_INFRA artifact. The emulator did not start; no API28/Pixel 2/API/size/CameraX/Instrumentation checks or formal Android Runtime gates ran. Job `api28-runtime` is SKIPPED. Run URL: https://github.com/pan277942135/Yujian_App/actions/runs/39093844697; Preflight evidence artifact: https://github.com/pan277942135/Yujian_App/actions/runs/39093844697/artifacts/12123731965.

The prior SDK lookup and license-pipe failures are retained as historical failures at their own source SHAs. Android CI Run 39093846259 now passes on ffe285aff3818435815f769532fa221d3157917e; it does not replace the failed device preflight.


## Latest hosted runner evidence — exact source 85dfe92

Run 39095194693 checked out 85dfe9217e879e0f87d19b41801beb05e502aaa2 on `ubuntu-24.04`. Build Once and APK pair upload passed (artifact 12124820081). The artifact ZIP digest is `ab298c711af40ef00cc9820e6e60926687b67ed67736d1454a2c15b18c253c97`; it is not an APK digest. Preflight failed the required `/dev/kvm` character/read/write check; API28/Pixel 2 boot, display, install, CameraX, Instrumentation and Runtime did not run; `api28-runtime` was SKIPPED. Preflight artifact 12124093937 and Runtime-not-run artifact 12124313389 are retained. Run: https://github.com/pan277942135/Yujian_App/actions/runs/39095194693.


## Hosted-runner migration checkpoint — b97f01f6790cea2e2f1cb4bfef07a38086223020

- Runner: `ubuntu-24.04`; Java 17 / Gradle 8.9. Workflow uses `reactivecircus/android-emulator-runner@v2`, API 28 `google_apis` x86_64, Pixel 2 profile and 1080×1920 assertion.
- Hosted Run 39095681151: Build Once succeeded; app APK `b6d37938cdc34230415b7aea7fbf36536803e3908944c6a9665367d7c01c0890`, AndroidTest APK `356dfcffa5ef98ef2d1f496a2a2835b97e50776a714dab358d71ac03c7844f02`; artifact [12124671626](https://github.com/pan277942135/Yujian_App/actions/runs/39095681151/artifacts/12124671626).
- Preflight failed the KVM availability/read-write check before API28 emulator, CameraX, instrumentation, native screenshot or Runtime Gate execution. Preflight artifact 12124194596; Runtime-not-run artifact 12124388894.
- Coverage delta: Build/JVM/unit/contract compilation and APK staging are verified at this SHA. Device API28 tests, install/instrumentation, CameraX capture, screenshot/bounds, Pixel Diff, Motion MP4 and end-to-end flows remain `NOT_RUN`.
- Migration disposition: `BLOCKED_INFRA`. Standard runner was actually attempted; no paid runner or replacement VM was enabled.



## Exact-SHA hosted runner result — c69196920a2f3d5fa04a6fd9985e3dadd10f6a3c

- Configured runner: GitHub-hosted `ubuntu-24.04`; Build Once used JDK 17 / Gradle 8.9. API28 workflow [39096212590](https://github.com/pan277942135/Yujian_App/actions/runs/39096212590) built source `c69196920a2f3d5fa04a6fd9985e3dadd10f6a3c` once and uploaded the APK pair.
- APK provenance: app SHA-256 `7e5ea493277dd42a2a7a72cdbce0c2d5c48273b62b81d39efd26d5e880149d37`; AndroidTest SHA-256 `988dc01f9c31ae6e8cd96856be7cda446b639afc6f0d96fe8f72d05eebbd0f46`; artifact [12124946578](https://github.com/pan277942135/Yujian_App/actions/runs/39096212590/artifacts/12124946578), ZIP SHA-256 `caac6f268a189328a053d93880f3e5b86495d4fa5f876376bc3224ab69fe7a71`.
- KVM preflight failed before the `reactivecircus/android-emulator-runner@v2` API28 Pixel 2 step. Emulator boot, SDK API query, 1080×1920/density/insets, APK installation, CameraX, Instrumentation and native screenshot checks did not execute.
- Runtime status: `BLOCKED_INFRA`; formal API28 Runtime `SKIPPED/NOT_RUN`. Preflight evidence [12124588053](https://github.com/pan277942135/Yujian_App/actions/runs/39096212590/artifacts/12124588053); explicit NOT_RUN evidence [12125106065](https://github.com/pan277942135/Yujian_App/actions/runs/39096212590/artifacts/12125106065).
- Coverage delta: hosted scheduling, source scope/Frozen guard, Build Once, APK hashes and artifact upload are verified. KVM/AVD/device configuration, install, CameraX, test execution, screenshot/bounds, Pixel Diff, Motion MP4 and journey evidence remain unverified.
