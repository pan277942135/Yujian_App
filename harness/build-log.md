# Branch-local Work Build Log

## 2026-10-27 — W3 shared workspace adapter checkpoint

- Work: `YUJIAN_PAGE_FREEZE_PIPELINE_W3_DESIGN_MANAGER_ADAPTER_V1`; branch `work/w3-design-manager-adapter-v1`; Main `42ad0d3fef23ea6576fd43dee781d253dcc11251`; implementation candidate `5f5fa84dc945cd479add37d4c7b8109827e3b64d`.
- Reused the existing Design Manager and NH01 structured renderer; added a shared `PageWorkspaceAdapters` API, page route, authority/profile/resource checks, inspector and release logic. NH01 remains on its existing frozen contract and renderer.
- Exact candidate Admission: PASS. Local source-matched checks: page adapter `PASS pages=2 negative_cases=7`; NH01 workspace test PASS; design Python suite 60/60 PASS; W1 three-page contract matrix PASS; W2 three-page resource validation PASS.
- Empty Home and Login currently expose only their own frozen visual reference image in the shared workspace. Their actual page components are not independently mapped to DOM; Login input/form/keyboard states are not implemented. `page_acceptance=NOT_VERIFIED` remains in effect.
- Login's source authority explicitly requires direct-image preview and disallows HTML reconstruction. Do not fabricate Login component DOM or geometry. The W3 actual-component acceptance is BLOCKED until authorized page-owned DOM/implementation authority is available.
- Local Chromium execution is `BLOCKED_INFRA` because the browser executable is absent. No non-NH01 Chromium render/selection/bounds evidence is claimed; exact GitHub Actions and combined W3/W4 verification remain pending.
- Next action: open the W3 Draft PR, run exact-HEAD Actions, and obtain approved non-NH01 component DOM authority before resolving the adapter acceptance blocker.

## 2026-10-25 — NH01 structured workspace reuse and visual parity repair

- Refreshed `origin/main` and ran `scripts/validate_control_plane_admission.py`: PASS, exit 0, six control-plane layers, synchronized baseline `772a206131d784d5c962a1eb62b5e43bca0f6534`.
- Confirmed PR #203 is merged at the current Main SHA. PR #176 remains Draft/Open and is used only as a selective reuse source.
- Live Design Manager at Main showed a flat semantic layer list, the blocked Hero/Peek placeholders, and the existing working responsive loader; the Main build marker was `772a206131d784d5c962a1eb62b5e43bca0f6534`.
- Replaced the flat layer model with a recursive mapping from the checked-in NH01 component tree. The CTA remains explicitly unmapped to an individual source node because the available structure reuses the capture-action group reference.
- Kept the PR #203 loader, routing and contract calculations. Bound the approved Normal Home V2 avatar source; preserved the five frozen NH01 screenshots and their hashes. The V2 avatar is not the illustrative person shown in the M03 screenshot.
- Searched registered and repository image assets for approved independent Hero/Peek originals; none were established. The workspace continues to show explicit blocked states and does not crop frozen page screenshots into photos.
- Added measured DOM rectangle evidence and multi-size screenshot comparison outputs for M01–M04. M05 remains a width-only boundary with no assumed height. Comparison output is diagnostic and never declares visual parity.
- Local checks: JavaScript syntax, `tests/nh01_structured_workspace.test.cjs`, navigation verification, frozen hi-fi integrity, Design Manager registry validation, and 320×480 comparator smoke all passed. Candidate Chromium and visual parity remain unverified pending exact-head workflow evidence.
