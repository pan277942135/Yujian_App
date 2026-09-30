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
| P07 | Species selector; length, weight, location editing; result save/error | Recognition result authority; existing selectors/edit contracts in repository | Existing result flow is present; exact interaction/state coverage to be verified | Targeted edit/save unit + instrumentation | IN_PROGRESS |
| P08 | My Catches: timeline, filter, search, populated/empty/loading/error states | `my_catches_v2`; list, Filter V1, Search V1 specs | `my_catches` route and presentation/filter/search code exist; registry runtime marked PARTIAL; Filter F1 is frozen, later filter work remains | Targeted list/filter/search/empty-state tests and screenshots | NOT_STARTED |
| P09 | Fish Record Detail A-side, B-side, flip lifecycle, asset generation, edit, media and no-upload-memory states | `fish_record_detail_v2`; `fish_memory_bside_v1` as child; Detail specs 00–05 | `catch/{catchId}` route and detail screen exist; registry partial; current route wires share/edit/add-media callbacks as no-ops | Targeted detail state/instrumentation and visual evidence | NOT_STARTED |
| P10 | Fish Guide home, lit/unlit, species detail, zero-catch, five knowledge cards | `fish_guide_v2`; content, state, responsive and motion specs | `guide` and `species/{key}` routes, repository and runtime test exist; registry runtime/evidence marked PARTIAL | `fish-guide-v1` plus targeted state evidence | NOT_STARTED |
| P11 | Account, profile/avatar, security/password, consent, Privacy Policy, User Agreement | `account_privacy_v1`; `auth_login_v2`; `auth_register_v2`; `profile_edit_v1`; `user_agreement_v1` | Routes/screens exist for account/profile/password/data privacy/legal docs; registry records are partial or runtime-only; agreement visual/design authority is missing | Targeted account/auth tests; legal content and API behavior contract | NOT_STARTED |
| P12 | Shared navigation, buttons, Capture Button, backgrounds/glass, accessibility/responsive parity | Shared design-system registry and component contracts | Shared Compose components and Android design tokens exist | Component tests, accessibility spot checks, representative-size screenshots | NOT_STARTED |
| P13 | Cross-journey integration | Current product navigation + frozen module contracts | Existing app routes connect home, capture, recognition, save, catches, detail, guide and account | Journey instrumentation/smoke with existing test seams | NOT_STARTED |
| P14 | Final Android Runtime Matrix | Existing `.github/workflows/android.yml` and `scripts/run_android_runtime_gate.sh` | Build job plus self-hosted `yujian-android/api28` matrix; gates include home, recognition, login, fish guide and parity | Full required matrix once after module work | NOT_STARTED |
| P15 | APK, evidence, Draft PR closure and terminal report | Epic acceptance criteria in task | Existing Actions artifact/evidence workflow; final full-surface APK artifact must be added/verified | APK installability, artifact SHA/ID, evidence-to-commit trace, PR review/closure | NOT_STARTED |

## Baseline issues to carry forward

1. `fish-guide-v1` is present in the Android workflow matrix and has a gate script, but `scripts/run_android_runtime_gate.sh` currently lacks a dispatch case for it. Reconcile this narrowly when P10 reaches its gate; do not modify unrelated CI/Runner infrastructure.
2. Fish Record Detail’s current navigation wiring leaves share, edit, and add-media callbacks empty. P09 must implement only behavior supported by the frozen Detail contracts and existing backend capabilities.
3. User Agreement is registered as runtime-only with no visual authority. Implement existing legal content/navigation conservatively; record any missing product/legal authority as `DESIGN_GAP` and do not invent policy wording or backend behavior.
4. Local execution environment has Java 17 but no `gradle`, `adb`, or `emulator`. Use the repository’s existing GitHub Actions build and self-hosted API 28 runner. An external runner failure is classified and isolated under the bounded-retry contract.

## Gate classification and recovery

Runtime terminal classification: `PASS`, `FAIL_PRODUCT`, `FAIL_EVIDENCE`, `BLOCKED_INFRA`. A module blocked by a dependency is recorded separately as `DESIGN_GAP` or a named dependency blocker in `PROGRESS.md`; unrelated phases continue. Each gate gets one initial attempt and at most two repair attempts. Do not chase newer `main`; reconcile only if a real merge conflict exists at final integration.
