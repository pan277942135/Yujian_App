# NH01 V4 — Round 1 Full Audit and Fix Report

## Execution record

| Item | Result |
|---|---|
| Scope | NORMAL/M04 360×640dp, LONG/M07 390×844dp, WIDE/M10 430×932dp; FontScale 1.0; ADAPTIVE_V2 only |
| Frozen visual authority | `渔见湖光鱼获首页.png`, SHA-256 `8bb3bb1b4a2303d006bd3f6dbd373a11a02997d97ca64a1d5130c9432847e37c` |
| Source under test | `2ac1a24402cd73986fe45cec813b377feb4ea4c3` |
| API/AVD | API 28 / Pixel 2 AVD; macOS 15 Intel runner; physical panel 1080×1920 px, 480 dpi |
| Workflow run | [41598157524](https://github.com/pan277942135/Yujian_App/actions/runs/41598157524), job `125790914095` |
| Workflow artifact | [Round 1 runtime package](https://github.com/pan277942135/Yujian_App/actions/runs/41598157524/artifacts/13213434452) |
| Emulator boot / APK install / representative cases | 322 s / 65 s / 99 s |

## Full device run

The Debug APK and AndroidTest APK builds passed. Gradle unit tests passed, the existing `GeminiNormalHomeResponsiveTest` instrumentation passed, and all three requested ADAPTIVE_V2 configurations were invoked on the same API 28 AVD.

| Device | Requested logical size | FontScale | Matrix result | Screenshot pair | Layout bounds/data audit |
|---|---:|---:|---|---|---|
| NORMAL / M04 | 360×640dp | 1.0 | FAIL before composition | Missing initial and after-swipe captures | Blocked before component capture |
| LONG / M07 | 390×844dp | 1.0 | FAIL before composition | Missing initial and after-swipe captures | Blocked before component capture |
| WIDE / M10 | 430×932dp | 1.0 | FAIL before composition | Missing initial and after-swipe captures | Blocked before component capture |

Each failure was `android.view.ViewRootImpl$CalledFromWrongThreadException` at `COMPOSE_PRODUCTION_HOME_SCREEN`: “Only the original thread that created a view hierarchy can touch its views.” The test called `WindowCompat.setDecorFitsSystemWindows` on the instrumentation thread before `compose.setContent`. The fixture IDs and page contents therefore were not rendered or verified. No visual comparison or responsive target projection is claimed for Round 1.

The runtime wrapper also ran the older fixed 1080×1920 `gemini-home-v2` smoke gate before the three-device matrix. That out-of-scope path failed with `GEMINI_REFERENCE_HOME_DATA_OR_ROUTE_NOT_VERIFIED_1080X1920`. Round 2 removes this extra page run and retains its useful preflight/install responsibilities without launching the legacy screen.

## Findings and Round 1 disposition

1. **NH01-VIS-001 — P0 test-thread violation.** All three runs failed before `HomeScreen` composition. The Round 2 harness removes its test-thread Window mutation and launches the installed `MainActivity`, whose production lifecycle applies edge-to-edge settings on the UI thread.
2. **NH01-VIS-002 — P1 evidence unavailable.** The pre-composition failure prevented the required initial screenshot, bounds, and post-swipe screenshot. Round 2 reruns the same three cases and preserves evidence on failure.
3. **NH01-VIS-003 — P1 out-of-scope extra display gate.** The workflow invoked the legacy 1080×1920 page test. Round 2 installs the APK pair once, then executes only M04/M07/M10 ADAPTIVE_V2.
4. **Data/layout/visual assertions — NOT VERIFIED.** Deterministic fixture setup was reached, but rendering, unique IDs in the semantics tree, side-card visibility, camera/CTA geometry, swipe switching, clipping, and frozen-image comparison were blocked by finding 001.

### Round 1 gate summary

| Gate | Result |
|---|---|
| API 28 AVD boot and ADB readiness | PASS |
| Debug APK / AndroidTest APK build | PASS |
| Unit tests / existing responsive instrumentation | PASS |
| M04, M07, M10 ADAPTIVE_V2 | FAIL, 0/3 |
| Six required initial/swipe screenshots | FAIL, 0/6 |
| Component bounds and fixture switching | BLOCKED |
| Visual comparison to frozen design | BLOCKED; no valid NH01 captures |
| Overall Round 1 | FAIL; findings retained for Round 2 |

The run above is the immutable Round 1 evidence snapshot. A local corrective checkpoint `fcb29605a5cb35966800a26ab2e2226afe44f4b9` was created after that run but was not pushed or evaluated by Round 1. Round 2 must be evaluated by a fresh three-device workflow run; no later result is retroactively counted as a Round 1 pass.
