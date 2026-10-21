# NH01 GitHub-Native High-Fidelity Closure Workflow V1

**Status:** IMPLEMENTATION / REVIEW_REQUIRED  
**Purpose:** Complete the NH01 design-to-Android verification loop without requiring Figma MCP, Figma Desktop, or a paid design-tool API quota.

## Authority and non-goals

1. The registered Frozen NH01 PNG remains the read-only composition reference until an explicitly approved replacement target is registered.
2. GitHub source assets, JSON models, Markdown contracts, Compose source, workflows, and exact-SHA artifacts are the reproducible working set.
3. The historical PR #164 Figma node map is retained as provenance only. No step in this workflow requires live Figma access or a Figma write.
4. The structured HTML preview is an editable engineering model, not an approved design target by itself. Its differences from Frozen are diagnostic; never tune the reference or thresholds merely to make the preview pass.
5. Local screenshot comparison runs entirely in the browser. It does not upload a user's screenshot, commit it, or promote local results to CI/runtime PASS.

## Canonical end-to-end workflow

### G0 — Freeze source identity
- Check out one exact Git commit.
- Validate Frozen PNG SHA-256 and dimensions (1080×1920).
- Validate each registered source asset SHA and dimension.
- Record the exact asset and contract revision used for the run.
- Preserve Frozen assets; never replace them with a screenshot of the implementation.

### G1 — GitHub-native structure and binding
- Build/validate design/manager/nh01_acceptance/data/ from the registered source model and existing contracts.
- For each visible region, record structure ID, Compose symbol/source file, asset binding, render mode, geometry authority, responsive rule, and acceptance test.
- Keep unknown or conflicting bindings as REVIEW_REQUIRED / BLOCKED_MISSING_EVIDENCE; do not invent a Figma readback.
- Hero-photo provenance/usage rights and avatar authority must be resolved before those assets are treated as accepted visual evidence.

### G2 — Browser render and preview diagnostics
Run the existing Design Manager Pages workflow. It validates the JSON bundle, renders the structured model in Chromium at 1080×1920, captures DOM bounds, and publishes Frozen/structured overlay, heatmap and ROI diagnostics as workflow artifacts.
- Browser DOM bounds are not Android boundsInWindow.
- Browser pixel differences are DIAGNOSTIC_ONLY_REVIEW_REQUIRED, not Android parity.
- The local screenshot tool below is a user-facing diagnostic convenience, not a gate.

### G3 — Exact-source Android build/runtime
Run the NH01 Android Runtime workflow against the same exact source SHA. It must produce:
- installable Debug APK and APK SHA-256;
- Android build/unit/lint results;
- API 28 emulator instrumentation results;
- native screenshots and actual boundsInWindow;
- run ID, artifact URL, source SHA and device/window metadata.

A skipped runtime job is not a runtime pass. Evidence from a different SHA cannot close the current SHA.

### G4 — Responsive and state matrix
Run the required compact/medium/expanded and short/standard/long profiles, font scales 1.0×/1.3×/1.5×/2.0×, safe-inset/window cases, and catch counts 0/1/2/3/10. Record actual runtime screenshots, measured bounds, scroll/overflow results, card aspect ratio, carousel IDs, CTA/camera touch geometry, and photo render mode. Candidate adaptive numbers remain candidates until the matrix is reviewed.

### G5 — Visual diagnosis and repair
For each failing ROI:
1. identify the expected target and whether it is authoritative for the tested layout;
2. record measured bounds, expected bounds and delta;
3. classify the cause as asset, geometry, typography, crop, material, state/data, or test/infra;
4. fix the source component or asset binding;
5. rerun affected cases at the new exact SHA;
6. preserve the failed and passing evidence lineage.

Dynamic photo pixels are not judged against an unrelated static fish photo. Geometry, glyph masks, source identity and material regions are evaluated separately under the existing quantitative contract.

### G6 — Release decision
The task is not closed by JSON generation, browser success, CI build success, or an APK existing. Closure requires the current-source APK, exact-SHA Android runtime, complete matrix, traceable screenshots/bounds, visual report, and no unexplained required ROI failures. Physical handset acceptance is recorded separately from emulator evidence.

## Current known blockers carried forward

- Historical Figma page has incomplete background/avatar/camera slots; these are no longer blockers to the GitHub-native browser tool itself.
- The approved authority for any intentional Gemini-native composition change must be explicit; do not assume the structured preview is approved merely because it renders.
- Hero photo fixture register says source provenance/usage rights are not verified; keep the Hero image placeholder in the structured preview until resolved.
- Avatar authority, typography and CTA/camera anchor conflicts remain review items until reconciled against canonical contracts and runtime evidence.
- Prior PR #172 API28 matrix and APK belong to its recorded source SHA only; they do not close a different current SHA.

## Entry points

- Browser review center: design/manager/nh01_acceptance/
- Bundle builder: scripts/design/build_nh01_cloud_bundle.py
- Bundle validator: scripts/design/validate_nh01_cloud_bundle.py
- Browser verifier: scripts/design/verify_nh01_viewer_browser.mjs
- Browser preview diff: scripts/design/compare_nh01_structured_preview.py
- Android/Frozen diagnostic: scripts/design/compare_nh01_android_runtime.py
- Android runtime workflow: .github/workflows/nh01-cloud-runtime.yml
- Pages/browser workflow: .github/workflows/design-manager-pages.yml

## Local screenshot compare

Open the existing Viewer → Visual Compare → “本地 Android 截图对照”. Select a screenshot. Strict mode only computes pixel metrics when it is already 1080×1920. The optional contain mode preserves aspect ratio and adds padding; it is diagnostic only. The report is downloadable JSON and stays in the browser. It does not upload, persist, or modify project files, and never changes any gate status.
