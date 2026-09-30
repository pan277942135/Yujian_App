# YuJian Android Full-Surface Runtime — Implementation Matrix

Task: LONG-RUNNING EPIC / IMPLEMENTATION + VALIDATION + GITHUB DELIVERY
Platform: Android only
Repository: https://github.com/pan277942135/Yujian_App
Frozen Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
Epic branch: `feature/android-full-surface-runtime-v1`

## Authority and status rules

Design authority order: frozen machine-readable contract → frozen canonical visual authority → frozen page/interaction spec → current Design Manager registration → shared design system/components → latest runtime → legacy material.

Epic status vocabulary: `NOT_STARTED`, `IN_PROGRESS`, `IMPLEMENTED`, `PUSHED`, `PASS`, `FAIL_PRODUCT`, `FAIL_EVIDENCE`, `BLOCKED_INFRA`, `DESIGN_GAP`.

“Baseline runtime” records repository evidence only; it does not count as Epic completion or acceptance. The matrix scope comes from the 13 records in `design/registry/experience_registry_v1.json`, current Android navigation, and the task’s user-journey coverage. Fish Record B-side remains a child surface of Fish Record Detail, not a new top-level destination.

## Phase matrix

| Phase | Module / page / state | Design authority / registry record | Baseline runtime evidence at frozen base | Targeted gate | Epic status |
|---|---|---|---|---|---|
| P00 | Inventory, matrix, shared infrastructure | `design/registry/experience_registry_v1.json`; `design/registry/shared_design_system_v1.json`; governance README | Registry and runtime harness inspected; no existing full-surface ledger found | Registry/path audit; current build/runtime infrastructure inventory | PUSHED |
| P01 | Authentication: Login, Register, entry/back/error/loading | `auth_login_v2`; `auth_register_v2`; their frozen manifests/specs | Existing `auth/login` and `register` screens plus 13 Login/Register instrumentation tests; Register registry source pointer corrected at `009a85f` | `login-v2`; frozen Login/Register screenshots | BLOCKED_INFRA |
| P02 | Empty Home | `home_empty_v2`; V2.2 contracts and evidence manifest | Current asset verifier PASS (11 assets/16 files); API 28 runtime PASS on `38ba0e5`, artifact `11011440570`; Empty Home path unchanged since that evidence run | `empty-home-v2` inherited evidence + current verifier | PASS |
| P03 | Normal Home, First Catch Home, resolving/refresh/error/avatar/media states, responsive layout | `home_normal_v1`; NH01–NH06 specs/contracts and `default_avatar_contract.json` | PR #76 baseline merged; current NH02–NH06 fixes and targeted tests authored; Normal Home asset/source verifier PASS | Normal Home Android suite and current 1080×2340 evidence; inherited physical capture remains BLOCKED_INFRA | BLOCKED_INFRA |
| P04 | Capture entry, camera/gallery handoff, permission/failure/retry | Current navigation and shared Capture authority; Recognition contracts | Existing `identify` route handles CameraX and `GetContent`; this checkpoint adds safe bind/capture failure handling and camera/gallery normalization tests | `recognition-frozen` includes `RecognitionImageStoreTest`; Android execution unavailable here | BLOCKED_INFRA |
| P05 | Recognition Processing and failure/retry boundary | `recognition_flow_v1_2`; runtime/state/motion contracts | Existing three-state flow retained; user Back cancellation now propagates without becoming technical failure; targeted instrumentation authored | `recognition-frozen`, runtime evidence and visual parity | BLOCKED_INFRA |
| P06 | Recognition Result: high/medium/low/no-fish/image-quality states | `recognition_result_v1`; behavior spec and authority map | Source checkpoint `bf470596c35f39adccb185be04f3da774f0220a7`; direct 3+2 instrumentation authored; large-font candidate row scroll support added | `recognition-frozen`, result visual parity and runtime evidence | BLOCKED_INFRA |
| P07 | Species selector; length, weight, location editing; result save/error | Recognition result authority; Species Selector V1; Metadata Edit Flow V1; Metadata Input Contract V1 | Source checkpoint `3063074c34f4f596a559cc35f68c20ddd7765859` adds contextual selector, pinyin/alias search, device-local recent choices, numeric focus/IME/validation, location search/current/recent flows, safe save errors and duplicate-submit coverage | `recognition-frozen` + Fish Knowledge contract tests authored; design/runtime static verifiers and source Actions pass; Android compile/runtime evidence unavailable. API catalog entries without pinyin remain a named metadata coverage gap | BLOCKED_INFRA |
| P08 | My Catches: timeline, filter, search, populated/empty/loading/error states | `my_catches_v2`; list, Filter V1, Search V1, Timeline Scroll and Growth Mark specs | Source `3a412ed1a3531b8438ff241837a39d2abc40b9e4` adds BG_DATA, focused in-page search/recents, four-dimension inline F1, timeline grouping/folds/date detail, corrected Growth Marks and exact empty copy | Unit tests authored; source Actions pass; Android compile/runtime and populated visual evidence unavailable. API currently returns all catches without paging; F2–F7 and timeline high-fi visuals remain open | BLOCKED_INFRA |
| P09 | Fish Record Detail A-side, B-side, flip lifecycle, asset generation, edit, media and no-upload-memory states | `fish_record_detail_v2`; `fish_memory_bside_v1` as child; Detail specs 00–05 | Source `bce336955f49532e6ef6061cbabbc145639b88d3`; implementation COMPLETE, GitHub checkpoint PASS, static contract checks PASS | Android runtime validation BLOCKED_INFRA: CI runs #1105/#1106/#1109 terminate with 0 jobs. Backend dependencies OPEN: no update-existing-catch, supplemental media association/list, server first_b_reveal_done, note/weather/delete | PUSHED |
| P10 | Fish Guide home, lit/unlit, species detail, zero-catch, five knowledge cards | `fish_guide_v2`; content, state, responsive and motion specs | Home/detail behavior, original-catch previews, species-filter navigation and five fixed knowledge positions implemented; targeted tests authored; static checks PASS; gate dispatch repaired | Android runtime validation carries the shared `REPOSITORY_ANDROID_CI_ORCHESTRATION_FAILURE`; no per-phase retry | BLOCKED_INFRA |
| P11 | Account, profile/avatar, security/password, consent, Privacy Policy, User Agreement | `account_privacy_v1`; `auth_login_v2`; `auth_register_v2`; `profile_edit_v1`; `user_agreement_v1` | Routes/screens exist for account/profile/password/data privacy/legal docs; registry records are partial or runtime-only; agreement visual/design authority is missing | Targeted account/auth tests; legal content and API behavior contract | NOT_STARTED |
| P12 | Shared navigation, buttons, Capture Button, backgrounds/glass, accessibility/responsive parity | Shared design-system registry and component contracts | Shared Compose components and Android design tokens exist | Component tests, accessibility spot checks, representative-size screenshots | NOT_STARTED |
| P13 | Cross-journey integration | Current product navigation + frozen module contracts | Existing app routes connect home, capture, recognition, save, catches, detail, guide and account | Journey instrumentation/smoke with existing test seams | NOT_STARTED |
| P14 | Final Android Runtime Matrix | Existing `.github/workflows/android.yml` and `scripts/run_android_runtime_gate.sh` | Build job plus self-hosted `yujian-android/api28` matrix; gates include home, recognition, login, fish guide and parity | Full required matrix once after module work | NOT_STARTED |
| P15 | APK, evidence, Draft PR closure and terminal report | Epic acceptance criteria in task | Existing Actions artifact/evidence workflow; final full-surface APK artifact must be added/verified | APK installability, artifact SHA/ID, evidence-to-commit trace, PR review/closure | NOT_STARTED |

## Baseline issues to carry forward

1. P10 restored the `fish-guide-v1` dispatch case in `scripts/run_android_runtime_gate.sh`; workflow matrix, GCP runner, API 28 and Android Runtime Harness remain unchanged.
2. P09 wired Fish Record Detail behaviors supported by the existing APIs. Its remaining edit/media/reveal/note/weather/delete gaps are Product/API Dependencies, not infrastructure; see `PROGRESS.md`.
3. User Agreement is registered as runtime-only with no visual authority. Implement existing legal content/navigation conservatively; record any missing product/legal authority as `DESIGN_GAP` and do not invent policy wording or backend behavior.
4. Local execution tools lack Gradle, adb and emulator access, but that is not the primary Android blocker: repository Android workflow runs currently terminate before any job is created. Continue with the existing GitHub Actions build and self-hosted API 28 runner when orchestration recovers.

## Gate classification and recovery

Runtime terminal classification: `PASS`, `FAIL_PRODUCT`, `FAIL_EVIDENCE`, `BLOCKED_INFRA`. A module blocked by a dependency is recorded separately as `DESIGN_GAP` or a named dependency blocker in `PROGRESS.md`; unrelated phases continue. The bounded retry budget applies to the underlying blocker, not once per phase. Carry the shared 0-job Android orchestration blocker through P10–P15 without repeated retries; revisit it only when new evidence shows runner scheduling has recovered. Do not chase newer `main`; reconcile only if a real merge conflict exists at final integration.
