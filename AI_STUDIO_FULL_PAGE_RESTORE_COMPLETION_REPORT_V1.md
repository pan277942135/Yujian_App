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
| Automated unit/instrumentation tests | PASS | Android CI PR run `41297647690` succeeded for PR head `c34aea6a2de0cae636d350a677f3629630f82d25` (merge build SHA `4cfa30352485992aa2738f28bdffdbaa91d8b79f`): unit tests, Camera runtime harness contract tests, AndroidTest APK compilation, packaged resource checks, model trace export, and Android lint passed. |
| API 28 Runtime | BLOCKED | Exact-head push run `41297644192` has no jobs. The preceding push run `41297092913` has eight API 28 jobs queued for the self-hosted runner; the repository currently has no in-progress Android Actions runs and a large existing queue. |
| Current Android Runtime screenshots/recording | NOT_RUN | No API 28 device gate has started, so no current runtime screenshot or recording is available. |
| Physical device haptic verification | BLOCKED | No physical Android device is connected to this work environment |
| Installable Debug APK | PASS | Full-Surface artifact `13080470873` from Android CI run `41297647690`, associated with PR head `c34aea6a2de0cae636d350a677f3629630f82d25`; build SHA is PR merge commit `4cfa30352485992aa2738f28bdffdbaa91d8b79f`. The APK is `121651606` bytes. |
| APK SHA-256 | PASS | `2a6ed707ceb2e05862c7c0897e14c66134ceb68958c3e60d71ba81543c38b490`; independently matches the Full-Surface bundle `SHA256SUMS` and APK model trace. |
| PR and merge status | BLOCKED | PR #296 is open, Draft, and unmerged. Keep it Draft while API 28 runtime evidence and D01/D02/D03 owner decisions remain unresolved. |
| Exact Main SHA | PASS | `796106827987d2ccaddb43f8a5723b2cbf73a34e` was the checked current Main HEAD |
| Product-owner decisions | BLOCKED | D01 rotation, D02 photo source/rights, D03 HIGH visual authority are listed in the decision register |

Only PASS/PARTIAL/BLOCKED/NOT_RUN states are used. Source integration is not represented as Runtime acceptance.

## Exact-head CI and artifact evidence

The Android CI `pull_request` run completed successfully for PR head `c34aea6a2de0cae636d350a677f3629630f82d25` against Main `796106827987d2ccaddb43f8a5723b2cbf73a34e`. GitHub built the PR merge result at `4cfa30352485992aa2738f28bdffdbaa91d8b79f`; all build, unit, AndroidTest compilation, asset, model trace, and lint steps passed.

Design Governance, Design Manager Pages, W1, W4, W5, Empty Home V2 Design Assets, and control-plane admission all passed on the same PR head. Run IDs: `41297647689`, `41297647715`, `41297647670`, `41297647740`, `41297647678`, `41297647675`, and `41297647652`.

The Full-Surface artifact is `13080470873`. Its ZIP SHA-256 is `e296f38be49655981410b12f1d242703aa62c8e670630ad2921d9886c2ec06da`. The extracted APK is `121651606` bytes with SHA-256 `2a6ed707ceb2e05862c7c0897e14c66134ceb68958c3e60d71ba81543c38b490`; this matches both the bundle checksum and APK model trace.

The exact-head push run `41297644192` has not created jobs. Older Android push runs remain queued for the repository's self-hosted API 28 runner, and no Android workflow is currently in progress. Device runtime gates and runtime captures therefore remain blocked/not run.

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
- Android CI PR run `41297647690` passed; design/admission runs `41297647652`, `41297647670`, `41297647675`, `41297647678`, `41297647689`, `41297647715`, and `41297647740` passed on PR head `c34aea6a2de0cae636d350a677f3629630f82d25`.
- Full-Surface artifact ZIP SHA-256: `e296f38be49655981410b12f1d242703aa62c8e670630ad2921d9886c2ec06da` (`119217740` bytes); APK SHA-256: `2a6ed707ceb2e05862c7c0897e14c66134ceb68958c3e60d71ba81543c38b490` (`121651606` bytes); model `MODEL_CROP_M1_v0.2`.
- API 28 is blocked by the self-hosted runner queue; PR and merge status remain Draft/open/unmerged pending device evidence and owner decisions.
