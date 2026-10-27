# Page Freeze Pipeline W4 Runner V1

## Ownership

The runner composes the existing W1 responsive envelope/profile validator and W2 page resource resolver/validator. It does not define another page registry, contract schema, asset registry, or gate taxonomy. The Experience Registry remains the page index; the W1 matrix provides the example envelope/profile pair for each registered page.

The evidence manifest is a W4-owned record. A manifest `PASS` requires the current exact source SHA, a numeric GitHub Actions Run ID, matching W1 and W2 results, and the W2 scan report from the actual staged Pages directory for the same SHA and Run ID. The Pages workflow generates it after scanning `_site` and before uploading the Pages artifact.

## CLI

Run page contract and binding validation with the page's W1 example pair resolved by `page_id`:

```bash
python scripts/design/page_freeze_runner.py validate-page \
  --page-id home_empty_v2 \
  --source-sha "$(git rev-parse HEAD)" \
  --output-dir /tmp/page-freeze-evidence
```

For the evidence gate, supply the W2 report generated from the actual staged Pages bundle and require evidence:

```bash
python scripts/design/page_freeze_runner.py validate-page \
  --page-id home_empty_v2 \
  --source-sha "$SITE_BUILD_SHA" \
  --publication-report "$RUNNER_TEMP/page-resource-binding-report.json" \
  --output-dir "$RUNNER_TEMP/page-freeze-evidence" \
  --require-evidence
```

Each invocation writes its own `<page-id>_Evidence` directory by default, containing the manifest, W1 report, W2 report, and validation summary. `--evidence-name` changes only that output label; it does not affect page identity. The existing Pages workflow uses `NH01_Evidence`, `EmptyHome_Evidence`, and `Login_Evidence`.

Validate a completed manifest against the checked out source and observed CI run:

```bash
python scripts/design/page_freeze_runner.py validate-manifest \
  --manifest "$RUNNER_TEMP/page-freeze-evidence/EmptyHome_Evidence/evidence-manifest.json" \
  --expected-source-sha "$SITE_BUILD_SHA" \
  --expected-run-id "$GITHUB_RUN_ID"
```

After W3 exposes the shared DOM markers, run a browser workspace inspection:

```bash
PLAYWRIGHT_MODULE_DIR="$RUNNER_TEMP/dm-playwright/node_modules" \
python scripts/design/page_freeze_runner.py verify-page-workspace \
  --page-id home_empty_v2 \
  --candidate-url http://127.0.0.1:8765/design/manager/ \
  --source-sha "$SITE_BUILD_SHA" \
  --output-dir "$RUNNER_TEMP/EmptyHome_Workspace"
```

To bind that report and its raw screenshot/DOM bytes into the page manifest, invoke `validate-page` for the same page, source SHA, and CI Run ID with `--browser-report "$RUNNER_TEMP/EmptyHome_Workspace/workspace-browser-report.json"`. A local report without a CI Run ID remains diagnostic and cannot produce a manifest `PASS`.

This browser command records route/build identity, actual DOM bounds, component-tree-to-DOM selection, console/network errors, horizontal overflow, a raw screenshot and DOM JSON. Its visual classification is always `MEASUREMENT_ONLY_NOT_VISUAL_PASS`; this command cannot accept a page or promote Android Runtime.

## Matrix behavior

The planned matrix is derived from the page's bound numeric authority and any page-owned matrix file. It never adds a viewport, font scale, state, or expected count that the authority does not declare. Planned but unexecuted cases are explicitly `NOT_RUN`.

At the Main baseline `42ad0d3fef23ea6576fd43dee781d253dcc11251`, the generated plans were:

| Page | Source authority | Planned cases | Status |
|---|---|---:|---|
| `home_normal_v1` | NH01 acceptance matrix: 12 declared profiles × 2 variants | 24 | `NOT_RUN` by this generic command; existing NH01 browser/native owners remain separate |
| `home_empty_v2` | `responsive_profiles_to_verify` × `font_scale_profiles` | 8 | `NOT_RUN`; behavior state reference does not provide machine-readable state IDs |
| `auth_login_v2` | Login V2.1 visual-spec reference | 0 | `NOT_RUN`; no machine-readable viewport authority is bound |

Empty Home and Login W1 profiles remain `DRAFT`. Their schema-valid static checks do not mean browser acceptance. Login's missing responsive matrix is an explicit gap for its owning pilot; the runner does not fabricate one.

## Evidence semantics

- Local W1/W2 validation can report `status=PASS` in its command summary, but the manifest remains `NOT_RUN` without a same-run Pages scan and CI Run ID.
- W4 manifest `PASS` means exact-SHA contract/resource/Pages bundle checks passed. `browser_geometry` may still be `NOT_RUN`.
- Browser DOM inspection is structural measurement evidence only. Screenshot similarity requires a separately owned comparator and never turns this structural result into visual acceptance.
- `page_acceptance` is fixed at `NOT_VERIFIED`; `native_runtime` is fixed at `NOT_VERIFIED`.
- A matrix `PASS` requires per-case exact-SHA evidence; planned cases or a screenshot alone cannot promote it.
- The W2 Pages bundle report is copied into each page's evidence directory so its denylist, permission, and published-file scan can be inspected with the manifest.
