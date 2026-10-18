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
