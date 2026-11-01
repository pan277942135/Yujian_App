# AI Studio / W5 / Main Three-Way Restore Matrix

Task: `YUJIAN_ANDROID_AISTUDIO_FULL_PAGE_RESTORE_AND_MAIN_INTEGRATION_V1`  
Repository: `pan277942135/Yujian_App`  
Current Main HEAD: `796106827987d2ccaddb43f8a5723b2cbf73a34e`  
Product tree used for file blobs: `afd9b5814da327a84cd41181acc08624fb5583ad`  
Historical ZIP baseline: `ea97aaa13922efdd8cb411df1a8a93438cb802ac`  
ZIP SHA-256: `fde0f4a93044e766f3b529b2fcb039bf3b0186972a8bf4bd223f65df301ebbab`

## Authority and source roles

| Source | Authority | Applied here |
|---|---|---|
| AI Studio ZIP | Candidate implementation, layout, event curve, asset bytes, and source material | Selectively integrated after per-file and authority checks; not used as a tree replacement |
| W5 mapping | Latest Empty Home composition candidate | Applied as a distinct unfrozen runtime geometry overlay, pinned to the mapping file SHA-256; canonical anchor contract stays unchanged |
| Android Main | Product flows, recognition/model behavior, camera gates, frozen contracts, and current shared components | Preserved except for explicitly integrated, source-backed changes listed below |

The current Main HEAD advanced from the product tree snapshot only through control-plane task registration; the 1,182 ZIP paths have the same per-path Main blobs as the recorded product tree. Every inventory row still records current Main HEAD and the corresponding Main blob SHA.

## File-level audit summary

| Finding | Count |
|---|---:|
| Regular ZIP files inventoried | 1182 |
| Byte-identical to current Main | 1082 |
| Different or new versus Main | 100 |
| Differences listed in `CHANGESET_MANIFEST.md` | 11 |
| Differences omitted by that manifest | 89 |
| ZIP equals historical baseline while Main advanced | 38 |
| ZIP-only changed files where Main stayed at baseline | 25 |
| New ZIP paths | 26 |
| Three-way divergences | 11 |

## Three-way divergences

| File | ZIP/Main relationship | Final disposition |
|---|---|---|
| `app/src/main/java/com/yujian/ai/ui/home/EmptyHomeMotion.kt` | ZIP, historical baseline, and Main each differ | Integrate deterministic event motion with W5 candidate geometry; preserve Main's shared scene clock; rotation disabled pending D01 |
| `app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt` | ZIP, historical baseline, and Main each differ | Integrate motion-event haptic binding, three existing 3.6s waves, candidate contact center, and the ZIP water-tap feedback; keep Main haptic implementation |
| `app/src/main/java/com/yujian/ai/ui/home/CaptureHaptics.kt` | ZIP, historical baseline, and Main each differ | Keep Main's system-setting check and guarded double pulse |
| `app/src/test/java/com/yujian/ai/ui/home/EmptyHomeMotionTest.kt` | ZIP, historical baseline, and Main each differ | Preserve Main coverage and add W5 geometry, periodic event, tap dip, and haptic-start guard coverage |
| `app/src/androidTest/java/com/yujian/ai/EmptyHomeV2RuntimeContractTest.kt` | ZIP, historical baseline, and Main each differ | Preserve old anchor assertions; validate separate W5 candidate contract and motion/haptic policy |
| `app/src/main/assets/empty_home_runtime_v2/config/motion_contract.json` | Contract versions differ | Update event schedule and candidate geometry without changing canonical anchor file |
| `app/src/main/assets/empty_home_runtime_v2/config/layer_contract.json` | Contract versions differ | Add candidate geometry and shared motion-event trigger; retain frozen layer rules |
| `app/src/main/assets/empty_home_runtime_v2/config/runtime_manifest.json` | Runtime hashes differ | Recompute all changed contract hashes and keep the new candidate contract explicitly listed |
| `design/pages/home/empty_home/shared/contracts/runtime_manifest.json` | Runtime hashes differ | Keep byte-identical with packaged runtime manifest |
| `design/pages/home/empty_home/Page_Resource_Binding_V1.json` | Main page binding, ZIP, and PR #254 snapshot differ | Update only current Main source hashes plus exact title bytes/SHA; do not import the separate PR #254 registry graph |
| `app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt` | ZIP result style differs from Main and PR #294 | Preserve current Main and PR #294; mark D03 pending because ZIP's dark style conflicts with Frozen HIGH |

## Integrated changes

| Area | Final choice | Evidence/status |
|---|---|---|
| Empty Home | W5 bobber bbox `(519,1122,24,78)`, center `(531,1161)`, contact `(531,1195)`, reflection bbox `(525,1194,12,52)`, ripple center `(531,1195)` | Candidate geometry contract is separate and `USER_DIRECTED_CANDIDATE_DELTA_NOT_FROZEN`; responsive `sceneTransform` preserved |
| Motion | 36s deterministic cycle; starts at 1.5, 6.8, 13.6, 22.4, 28.2s; 1.4s Y-only dip using ZIP curve | Event motion drives bobber, ripple, and automatic haptic; no continuous sine bob |
| Ripple | Main three-wave renderer retained with ZIP delays 0/0.42/0.90s, growth 270/190/120 reference px, 3.6s duration, contact ring | Click response retained as a separate 2.8s user action; automatic haptic does not fire for taps |
| Haptic | Main system preference and double-pulse helper retained | One haptic per motion event ID; initial render, background, Reduce Motion, missing assets, and late event observation are gated |
| Guide | ZIP vertical center, exact gradient stops, right-side capsule CTA, narrow-width/large-font stacking restored | P01/P02/P03 order and Main camera Preview-Ready gate retained |
| Hero title | ZIP resource bytes copied only after SHA-256 check | `36b7ad90c6b685d286ba54ef92659f58bfb5e28b29824848c6cfded06ecf4cf5`; linked contract and manifest chain updated |
| Network config | HTTP(S)-scheme check restored for four repositories | Shared normalization helper and unit tests added |

## Not migrated

- All eight First Capture Guide photos/backgrounds remain outside Android Runtime pending D02. The ZIP provides no source/rights record; background JPEGs have ICC profile metadata only, and example PNGs have no embedded metadata or alpha.
- ZIP's two-page policy/reference/test changes are rejected; Main's three-page P01/P02/P03 contract remains.
- ZIP-wide `usesCleartextTraffic=true` is rejected.
- ZIP's `preBuild` model bootstrap is not adopted: it can fail open after a warning; current Android CI runs the model bootstrap and hard verification before APK assembly.
- ZIP HIGH Result dark styling is not adopted while it conflicts with `05_Result_High_Frozen.png` and the active PR #294 result gate.
- Main-advanced model/session, camera, screen, recognition reveal, and Web/Design files remain current. ZIP copies matching the old baseline do not supersede newer Main.
- `.aistudio/` temporary items, public AI Studio media copies, and `CHANGESET_MANIFEST.md` remain audit-only.

## Open PR protection

| PR | Exact head | Status/overlap | Treatment |
|---|---|---|---|
| [#294](https://github.com/pan277942135/Yujian_App/pull/294) | `d97ebbdb20bc5cd0fe65c7c7397bd7d304caec7d` | Draft/Open; owns Android workflow, `RecognitionResultScreen.kt`, `RecognizingScreen.kt`, Orb/WaterLight reveal files and tests | No edits to its source files/workflow; retain current Main result behavior pending its merge and D03 |
| [#208](https://github.com/pan277942135/Yujian_App/pull/208) | `d8588432209405a7c97360b95f30a0077e5d6699` | Open; guide design structure/assets/registry | No guide asset or registry adoption |
| [#162](https://github.com/pan277942135/Yujian_App/pull/162) | `1d948b45a3ebc759acef72dd1f81769e9e832d0f` | Draft/Open; proposes removing P02 and a two-page guide | Main's three-page policy takes precedence; only visual layout parameters are restored |
| [#249](https://github.com/pan277942135/Yujian_App/pull/249) | `15ccbcf17750ab3e799729afd18283156ba78ccc` | Open; RR09 design contract | No result/reveal file edits |
| [#254](https://github.com/pan277942135/Yujian_App/pull/254) | `c1b2a47b55188f3a80c8833b737a93634f19dd4a` | Open; capture-to-result design graph includes Page Resource Binding | Keep Main's source authority graph and update only current hash edges for this task |

## Required files

- Full per-file JSON: `AI_STUDIO_ZIP_FULL_RESTORE_INVENTORY.json`
- Full per-file Markdown: `AI_STUDIO_ZIP_FULL_RESTORE_INVENTORY.md`
- Result conflict and owner decision: `AI_STUDIO_HIGH_RESULT_RESTORE_COMPARISON.md`
- Owner decisions: `AI_STUDIO_RESTORE_DECISION_REGISTER.md`
