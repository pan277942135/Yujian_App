# Login V2.1 structured high-fidelity candidate

This page-owned module is a W6-A local candidate. It uses the shared W3 `createStructuredDomAdapter` shell and reads the current Login W1 draft and W2 binding. It does not add a Design Manager route, change production navigation, or connect authentication.

## Run in a local browser

From the repository root, install the W2 validator dependencies and Playwright/Chromium, then run:

```bash
python -m pip install -r scripts/design/requirements_nh01_cloud.txt PyYAML==6.0.3
PLAYWRIGHT_MODULE_DIR=/path/to/node_modules node scripts/design/tests/w6a_login_candidate.mjs --output-dir /tmp/login-w6a-evidence
python scripts/design/compare_login_candidate_visual.py \
  --reference design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png \
  --render /tmp/login-w6a-evidence/evidence/screenshots/login-v2_1-structured-864x1536.png \
  --output-dir /tmp/login-w6a-evidence/evidence/visual-comparison \
  --source-sha "$(git rev-parse HEAD)"
```

The PR workflow `W6-A Login Structured Candidate` runs the same real Chromium candidate matrix on the exact PR head and uploads the resulting screenshots, DOM geometry, negative resource results, and visual comparison as a GitHub Actions artifact.

## Authority and release state

- `authority_audit_v1.json` keeps the V2.1 frozen visual, current revision, older behavior reference, W1 draft, and W2 resolution statuses distinct.
- `responsive_candidate_v1.json` is explicitly review-required. Its 12 viewport/font combinations are candidate experiments, not a frozen acceptance matrix.
- `component_mapping_v1.json` gives each rendered component a unique DOM mapping and approximate native-canvas reference bounds. The reference bounds are not a new numeric authority.
- The full frozen PNG is an independent comparison input. The Renderer uses the W2-resolved `Morning_Lake_Master_V1.png` background asset instead.
- The approved original upload is missing; the footer has no independent W2 resource binding. A low-contrast CSS grass sketch is marked `CANDIDATE_UNAPPROVED_ASSET` and is loopback-only.
- Shared person, lock, and visibility SVGs are byte-checked against the frozen icon library, but Login publication is not confirmed by W2. The harness refuses to render off loopback.
- Public preview and W6 closure remain blocked by W5 #224. Product visual acceptance is pending; Native Runtime remains `NOT_VERIFIED`.
