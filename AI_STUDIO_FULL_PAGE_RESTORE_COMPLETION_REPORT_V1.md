# AI Studio Full Page Restore Completion Report V1

Task: `YUJIAN_ANDROID_AISTUDIO_FULL_PAGE_RESTORE_AND_MAIN_INTEGRATION_V1`  
Repository: `pan277942135/Yujian_App`  
Report date: 2026-11-01

## Evidence and status

| Required item | Status | Evidence |
|---|---|---|
| Original ZIP SHA-256 | PASS | `fde0f4a93044e766f3b529b2fcb039bf3b0186972a8bf4bd223f65df301ebbab`; `286588012` bytes |
| Full ZIP file inventory | PASS | `1182` files; JSON and Markdown inventory attached |
| Manifest omissions | PASS | `89` of `100` differences omitted by the 11-entry self-reported manifest |
| Per-file three-way classification | PASS | 38 old-baseline copies, 25 ZIP-only changed paths, 26 new paths, 11 three-way divergences |
| Empty Home source integration | PARTIAL | W5 geometry candidate, event motion, click ripple, haptic guard, exact title SHA, contracts, tests; W5 geometry remains unfrozen |
| First Capture Guide source integration | PARTIAL | ZIP layout parameters restored; P01/P02/P03 and Camera Preview-Ready gate retained; eight photos remain unapproved |
| HIGH Result source review | BLOCKED | ZIP dark style conflicts with Frozen and PR #294 behavior; D03 awaiting owner choice |
| Other changed screens/shared components | PASS | All ZIP differences triaged; Main-advanced recognition/model/camera/screens retained; four API repositories gained HTTP(S) URL validation |
| Image authorization | PARTIAL | Hero title SHA pinned; eight guide candidates have no source/license record and remain excluded |
| Design contract updates | PASS | Event/Haptic/Layer/Candidate/Runtime/hero hashes synchronized; canonical anchor contract unchanged |
| PR #294 protection | PASS | Exact PR head checked; no edits to its result/reveal/workflow files |
| Automated unit/instrumentation tests | NOT_RUN | Pending exact-head GitHub Actions run |
| API 28 Runtime | NOT_RUN | Pending emulator workflow evidence |
| Current Android Runtime screenshots/recording | NOT_RUN | No runtime capture available yet |
| Physical device haptic verification | BLOCKED | No physical Android device is connected to this work environment |
| Installable Debug APK | NOT_RUN | Pending exact-head build artifact |
| APK SHA-256 | NOT_RUN | Requires downloaded exact-head APK artifact |
| PR and merge status | NOT_RUN | Product commit/PR not yet created at report generation time |
| Exact Main SHA | PASS | `796106827987d2ccaddb43f8a5723b2cbf73a34e` was the checked current Main HEAD |
| Product-owner decisions | BLOCKED | D01 rotation, D02 photo source/rights, D03 HIGH visual authority are listed in the decision register |

Only PASS/PARTIAL/BLOCKED/NOT_RUN states are used. Source integration is not represented as Runtime acceptance.

## Audit totals

- ZIP SHA-256: `fde0f4a93044e766f3b529b2fcb039bf3b0186972a8bf4bd223f65df301ebbab`
- ZIP bytes: `286588012`
- ZIP entries including directories: `1559`
- Regular files: `1182`
- Same as Main: `1082`
- Different/new vs Main: `100`
- Listed in `CHANGESET_MANIFEST.md`: `11`
- Omitted by `CHANGESET_MANIFEST.md`: `89`
- Main HEAD: `796106827987d2ccaddb43f8a5723b2cbf73a34e`
- Product tree source: `afd9b5814da327a84cd41181acc08624fb5583ad`

## Page outcomes

### Empty Home

The W5 candidate geometry is applied as one fishing composition and uses the single responsive `sceneTransform`. Candidate bobber center is `(531,1161)`; water contact, line endpoint, and ripple center are `(531,1195)`. The deterministic motion keeps the ZIP 36-second event schedule and 1.4-second Y-only dip curve. Three 3.6-second ripples, contact ring, user tap response, and Main's guarded double pulse remain. Automatic haptics are tied to a fresh motion event and suppressed on initial render, background, missing runtime assets, Reduce Motion, system haptics off, and late event observation.

The canonical anchor contract remains unchanged. Candidate geometry status is `USER_DIRECTED_CANDIDATE_DELTA_NOT_FROZEN`; native Runtime measurement remains NOT_RUN.

The title resource was copied byte-for-byte from the ZIP and verified at SHA-256 `36b7ad90c6b685d286ba54ef92659f58bfb5e28b29824848c6cfded06ecf4cf5` (111,271 bytes, 625×311 RGBA). The Hero contract, page-scoped asset manifest, Runtime manifests, and Page Resource Binding pin the exact bytes and byte count. The global Asset Registry remains unchanged and keeps its separate record of the prior hero resource.

### First Capture Guide

The ZIP's exact gradient stops, vertical content centering, right-aligned capsule action, 16dp horizontal padding, and narrow-width/large-font stacking were integrated. Main's P01 → P02 → P03 flow, current Camera permission/provider/Preview Ready gates, skip/start completion behavior, and missing-image `ASSET_SOURCE_BLOCKED` status remain. The eight candidate image assets do not enter Runtime.

### HIGH Result

The ZIP has a real source difference, so the comparison is documented rather than presumed. Its dark styling conflicts with the Frozen light layout. Main/PR #294 behavior is preserved. ZIP/Main actual rendered screenshots are NOT_RUN; no static preview is called a Runtime screenshot. Owner decision D03 is pending.

### Other pages and shared components

The 38 ZIP files matching the historical baseline were kept out because Main has later product behavior. Web design records remain separate from the Android runtime. The four backend repository changes that validate HTTP(S) schemes were integrated with a shared normalization helper and unit tests. The fail-open Gradle bootstrap, global cleartext traffic, and ZIP dark-result TopBar dependency were not adopted.

## Open decisions

See `AI_STUDIO_RESTORE_DECISION_REGISTER.md` for D01, D02, D03, options, impacts, exact image digests, and owner questions. The visual review sheet for the frozen guide pages and candidate photos is supplied separately and is not committed while photo permission is unknown.

## Deliverables

- `AI_STUDIO_ZIP_FULL_RESTORE_INVENTORY.json`
- `AI_STUDIO_ZIP_FULL_RESTORE_INVENTORY.md`
- `AI_STUDIO_W5_MAIN_THREE_WAY_RESTORE_MATRIX.md`
- `AI_STUDIO_HIGH_RESULT_RESTORE_COMPARISON.md`
- `AI_STUDIO_RESTORE_DECISION_REGISTER.md`
- GitHub Actions, APK provenance, PR, and merge fields must be updated after exact-head runs.
