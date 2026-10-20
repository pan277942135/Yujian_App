# NH01 Cloud Acceptance Center

This route adds a browser viewer to the existing Design Manager. It imports the PR #164 Node Mapping V2 as a byte-preserving source snapshot and derives the structured NH01 model from that mapping plus the frozen Normal Home contracts.

## Local preview

From the repository root, run:

```bash
python3 scripts/design_manager.py serve
```

Open `http://127.0.0.1:8765/design/manager/nh01_acceptance/`. The Viewer is static HTML/CSS/JavaScript and needs no upload or Figma access.

## Build and validate the structured data

```bash
python3 scripts/design/build_nh01_cloud_bundle.py
python3 -m pip install -r scripts/design/requirements_nh01_cloud.txt
python3 scripts/design/validate_nh01_cloud_bundle.py
```

The build script checks that the imported mapping snapshot remains byte-identical to PR #164 HEAD `77a9aabc2648bda2942575d6600b74c9fe7d3ad7`. The validator checks each JSON document with Draft 2020-12 JSON Schema and verifies the Frozen, Morning Lake, Brand and Camera SHA-256 values.

## Evidence boundaries

- The Frozen PNG is shown only in the Frozen Reference and comparison panels; it is never a layer in the Structured Preview.
- The Morning Lake, Brand and Camera files are loaded as separate original assets. The Hero photo is intentionally an explicit placeholder because the registered fixture has unverified provenance and usage rights. The Avatar uses a diagnostic placeholder because the effective authority remains under review.
- PR #164 records a confirmed Brand IMAGE/FIT readback, but no current Figma export. Figma visual acceptance remains `REVIEW_REQUIRED` and does not block the browser Viewer or Android Runtime work.
- Android Runtime stays `NOT_RUN` until an exact-source API 28 Pixel 2 workflow records a real native screenshot, bounds and device metadata.
- Pixel and responsive statuses never default to PASS. The existing Normal Home quantitative and responsive contracts remain the threshold authorities.
- The exact-source Chromium build captures an editable structured preview and shows a Frozen/preview heatmap, overlay, and per-ROI diagnostic table. Those browser metrics remain `DIAGNOSTIC_ONLY_REVIEW_REQUIRED`; they do not close Android parity.
- Runtime screenshots, bounds, Pixel Diff, responsive reports, and motion are published with their source SHA. Instrumentation logs remain in the GitHub Actions evidence artifact and are not copied into the public Pages site.

The Viewer's Release panel links to GitHub Actions and the Draft PR. It has no local-only approval control or false acceptance backend.
