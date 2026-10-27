# Branch-local Work Build Log

## 2026-10-25 — NH01 structured workspace reuse and visual parity repair

- Refreshed `origin/main` and ran `scripts/validate_control_plane_admission.py`: PASS, exit 0, six control-plane layers, synchronized baseline `772a206131d784d5c962a1eb62b5e43bca0f6534`.
- Confirmed PR #203 is merged at the current Main SHA. PR #176 remains Draft/Open and is used only as a selective reuse source.
- Live Design Manager at Main showed a flat semantic layer list, the blocked Hero/Peek placeholders, and the existing working responsive loader; the Main build marker was `772a206131d784d5c962a1eb62b5e43bca0f6534`.
- Replaced the flat layer model with a recursive mapping from the checked-in NH01 component tree. The CTA remains explicitly unmapped to an individual source node because the available structure reuses the capture-action group reference.
- Kept the PR #203 loader, routing and contract calculations. Bound the approved Normal Home V2 avatar source; preserved the five frozen NH01 screenshots and their hashes. The V2 avatar is not the illustrative person shown in the M03 screenshot.
- Searched registered and repository image assets for approved independent Hero/Peek originals; none were established. The workspace continues to show explicit blocked states and does not crop frozen page screenshots into photos.
- Added measured DOM rectangle evidence and multi-size screenshot comparison outputs for M01–M04. M05 remains a width-only boundary with no assumed height. Comparison output is diagnostic and never declares visual parity.
- Local checks: JavaScript syntax, `tests/nh01_structured_workspace.test.cjs`, navigation verification, frozen hi-fi integrity, Design Manager registry validation, and 320×480 comparator smoke all passed. Candidate Chromium and visual parity remain unverified pending exact-head workflow evidence.

## 2026-10-27 — NH01 runtime repair checkpoint initialized

- Refreshed `origin/main` to `42ad0d3fef23ea6576fd43dee781d253dcc11251` and merged it into PR #226 branch with normal merge commit `b8a4ee3824b7bb2b764d16b1636b2d55d294fd37`; no reset, force-push, or history rewrite.
- Restored `scripts/run_android_runtime_gate.sh` byte-for-byte from current Main in sync commit `bbb7c85847f02f38c834f494169bc9ddcb5b49f3`; the prior branch swallowed post-instrumentation gate failures.
- Preserved prior runtime failure Run `40445415381`, source `58bb84a088ad864d6901216cefccd3aa7488365f`: 47/61 pass, 14/61 fail. Three distinct recorded symptoms cover clipped S/2 bounds, async waits, and M01 width.
- The first local Admission attempt after Main merge failed only because the gate script was stale and both required branch-local Work state files were absent. No Android tests or product-source repair ran before restoring Admission.
- Current pre-test source checkpoint: `bbb7c85847f02f38c834f494169bc9ddcb5b49f3`. Next action is rerun the single required Admission command; product/runtime testing remains blocked until it passes.

## 2026-10-27 — NH01 runtime measurement and synchronization repair checkpoint

- Source checkpoint `387c91f445407b148f4de29c7644662da3dea161` adds test-only `LayoutCoordinates` capture for full transformed bounds and clipped visible bounds, while keeping Semantics bounds separate for touch targets.
- Scrollable-profile checks now record scroll offset/range, verify un-clipped window bounds reconstruct the same logical card position before/after scrolling, and assert the visible card is clipped by the viewport.
- Photo readiness now waits on observable `READY`/`FALLBACK` state and requires the real bundled image node for success; timeout diagnostics include fixture existence/size and rendered/placeholder nodes.
- Pager checks expose current/target/settled indices, motion/offset, stable active/neighbor IDs and geometry, then require three consecutive settled frames. Timeouts retain the measured state snapshot.
- M01 diagnostics now compute the required 145dp Hero height at 247:293 and the safe-height deficit from measured Top/Footer/base gaps and the measured title/action geometry. No frozen contract or visual anchor was changed; feasibility remains pending the exact-SHA runtime result.
- `scripts/verify_nh01_gemini_native_source.py`: PASS, 33 checks. `git diff --check`: PASS. Local Gradle build was not available because this checkout has no Gradle wrapper and no installed `gradle`; exact-SHA CI build/runtime is pending fresh Admission and push.
- Historical API28 result `40445415381` (source `58bb84a088ad864d6901216cefccd3aa7488365f`, 47/61 PASS, 14 FAIL) remains historical and does not establish current status.
- Fresh Admission rerun completed successfully at checkpoint `c2f915c17be4a24d4b5bbc6fc730cc712a804be9` against Main `42ad0d3fef23ea6576fd43dee781d253dcc11251`: all six control-plane layers, unified contracts, state validators and 731-path sync passed with 0 exceptions/errors. Live PR #226 remains Draft/Open/Unmerged; remote branch still pointed to `58bb84a088ad864d6901216cefccd3aa7488365f` before the authorized fast-forward push.

- GitHub repository-connected object staging created merge commit `63401a62bd26132462df2fd9752ae9011353042b` with parents PR old head `58bb84a088ad864d6901216cefccd3aa7488365f` and Main `42ad0d3fef23ea6576fd43dee781d253dcc11251`. Its tree SHA `6a6fd40a40c0bd3e3b7592bd827a20d385080909` exactly matches the locally Admission-checked source tree. A child state checkpoint will bind the Work source SHA to this remote merge commit before the branch ref is advanced; no force update is used.

## 2026-10-27 — NH01 exact-SHA measurement, wait and build-tool repair

- Repair source commit: `ec42276647d0dacc5d9e54d6ffb06475c6c0af21` on the existing PR #226 branch; Main remains `42ad0d3fef23ea6576fd43dee781d253dcc11251`. The PR tree excludes root branch-local live state files to satisfy canonical promotion Admission; local state remains in the controlled worktree.
- Replaced the unsupported Compose bounds overload with `LayoutCoordinates.localBoundingBoxOf`; geometry evidence separates unclipped logical layout, viewport-clipped visibility, window-origin translation, measured scroll restoration and Semantics touch bounds.
- Pager waits now require target/current/settled page, no motion, zero offset, expected stable record IDs and three stable geometry frames. Photo waits require observable READY plus rendered Image state; fallback is a failure for the bundled-photo success case and its placeholder is measured against the media box.
- Normal Home API28 gate now preserves nested matrix failures through the instrumentation result consumed by the canonical runner; behavioral shell coverage checks both pass and failure propagation. PyYAML is added to the cloud runtime dependency set. No frozen contracts, visual anchors, or private fish photos changed.
- Local checks on the repair tree: NH01 source audit 33/33; NH01 runtime Python tests 29/29; design tests 60/60; shell contract 5/5 plus fake-adb classifications; Admission passed before this source commit. Android compilation, APK packaging and API28 runtime are pending exact-SHA GitHub Actions.
- Retained prior exact-source failures: run `40445415381` at `58bb84a088ad864d6901216cefccd3aa7488365f` (47/61 pass, 14 fail); runs `40466533050`, `40466533058`, and `40466533059` at PR head `5a538d893e28a7074b230bea70d4006a9af97ab4` respectively identified PR-tree state leakage, missing PyYAML and unsupported Compose bounds API.
- Current state: APK `BUILD_PENDING`; runtime `NOT_RUN_ON_THIS_EXACT_HEAD`; M01 width outcome remains open until the exact-source M01 feasibility report distinguishes an implementation defect from `SPEC_CONFLICT`.
