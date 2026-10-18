# M1 Runtime Environment Migration Report

Task: M1_RUNTIME_ENV_MIGRATION_V1  
Repository and PR: pan277942135/Yujian_App / [Draft PR #163](https://github.com/pan277942135/Yujian_App/pull/163)  
Status: BLOCKED_INFRA — configuration is in the candidate worktree; no GitHub-hosted Preflight or exact-SHA Runtime run exists.

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

No Run ID has been issued for this workflow. Each check remains NOT_RUN; no environment PASS is claimed. A failed preflight records BLOCKED_INFRA and the formal gates as NOT_RUN.

## Build Once and evidence binding

Build Once checks out pull_request.head.sha, verifies source SHA and model snapshot binding, builds Debug APK and AndroidTest APK once, calculates each APK SHA-256 and uploads a manifest containing source SHA, workflow Run ID, runner and APK hashes. Preflight and each formal gate download and verify the same artifact pair.

The formal matrix uses existing recognition-frozen, camera-runtime-v1, empty-home-v2, normal-home-v1, login-v2, data-sanitization and fish-guide-v1 gates. Each returns an individual JSON classification. The catalog stays at 66 base cases and 10 E2E scenarios; these remain NOT_RUN until the applicable cases execute. Track C added a supplemental Save Catch → Detail A/B AndroidTest, not yet compiled or run.

## Historical runner and migration boundary

GCP run artifacts remain historical. PR #163 Android CI run 39086281726 built successfully but skipped Runtime jobs, so it proves neither hosted preflight nor product Runtime.

The canonical task machine still names self_hosted_api28_runner under P5-02. It was not edited in this product branch because that would bypass Control Plane authority and synchronization. The hosted workflow is not yet the approved P5-02 authority. The pre-existing android.yml and camera-runtime-harness.yml now use ubuntu-24.04, reactivecircus/android-emulator-runner@v2, Pixel 2 API28 profiles and ANDROID_HOME SDK paths. Their hosted configuration has not run on this candidate SHA. The canonical P5-02 task-machine runner binding still names the retired pool and awaits governed Control Plane synchronization. Overall migration and official Runtime remain BLOCKED_INFRA.

Local migration validation parsed all three workflow YAML files and ran `bash -n` on every `run` and emulator action script block. The first Android workflow parse failure was preserved and repaired in `M1_LOCAL_REPAIR_EVIDENCE.md`; the post-repair local syntax checks pass. This does not qualify the hosted runner: the KVM/API28/Pixel 2/CameraX/Instrumentation/APK/artifact Preflight still has no Run ID and remains NOT_RUN.

## Coverage difference

| Area | Before | Candidate configuration | Proven now |
|---|---|---|---|
| AVD creation | Self-hosted pre-created AVD and /opt/android-sdk | Hosted Pixel 2 created by android-emulator-runner@v2 | Not yet; Preflight NOT_RUN |
| APK production | Existing Build Once artifact | Exact PR head checkout, Build Once APK pair and source manifest | Not yet; workflow not run |
| Runtime assertions | Existing Runtime Harness gates | Reused without removing assertions; gates run after successful Preflight | Not yet; Runtime NOT_RUN |
| Evidence | Existing gate artifacts | Per-gate source/APK/Run/device manifest, screenshots and required logs | Not yet; no artifact |

No old GCP result is mixed into hosted evidence. This configuration report or a Build PASS is not a migration PASS.
