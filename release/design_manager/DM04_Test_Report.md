# DM04 · Design Manager 回归测试报告（提交前）

Date: 2026-10-16
Source baseline: `ae7f492a649d20dc23e7f04bcffa49749c7d9e70`
Scope: Static and artifact checks run locally; Chromium and live Pages checks are pending the PR/Pages workflows.

## Command results

| Command / gate | Result | Evidence |
|---|---|---|
| `python3 scripts/design_manager.py validate` | PASS | 16 shared systems and 15 page modules validate. |
| `python3 scripts/design_manager.py summary` | PASS | Existing module statuses rendered; no status semantics changed. |
| `python3 scripts/verify_design_manager_navigation.py` | PASS | Normal Home NH01–NH07 retained; Recognition overview / stable guide / boundary routes validated. |
| `python3 scripts/verify_design_manager_hifi_release.py` | PASS | Frozen PNG/SVG hashes and dimensions, registry, manifest, route, missing/review semantics, invalid-route guard and negative integrity cases. |
| `python3 scripts/validate_control_plane_admission.py` | PASS | Six-layer admission, feature matrix, task machine, unified contract registry; no Control Plane edits. |
| `node --check design/manager/app.js` | PASS | Application JavaScript syntax. |
| `node --check scripts/verify_design_manager_browser.mjs` | PASS | Browser suite JavaScript syntax. |
| `git diff --check` | PASS | No whitespace errors. |
| Local Playwright / Chromium suite | NOT_RUN | No local Chromium executable is installed; CI installs official Playwright Chromium and runs the browser gate. |
| Live Pages build marker / online source bytes | NOT_RUN | Must be verified by the deployment workflow after merge. |

## DM-T acceptance matrix

| ID | Requirement | Result before CI | Evidence boundary |
|---|---|---|---|
| DM-T01 | P01–P03 SHA and dimensions | PASS | Frozen source bytes match manifest. |
| DM-T02 | Registry / Navigation references | PASS | Existing guide route and children validated; page registry cross references present. |
| DM-T03 | Every displayed Frozen image exists | PASS | Validator fails on missing required Frozen images; expected-but-unimported images are represented as blocked. |
| DM-T04 | Menu and nested navigation | PASS | Existing NH and Recognition route IDs are protected. |
| DM-T05 | Stable direct Hash routes | PASS (static) | Hash targets and invalid-token handling checked; browser direct-open check awaits CI. |
| DM-T06 | Original image display / zoom | NOT_RUN (browser) | DOM and interaction checks run in Playwright after CI installs Chromium. |
| DM-T07 | REVIEW/MISSING not marked FROZEN | PASS | Manager-state mapping validated. |
| DM-T08 | Desktop / tablet / phone response | NOT_RUN (browser) | Playwright checks 1440/768/390/360/320px and horizontal overflow. |
| DM-T09 | Existing navigation regression | PASS (static) | Normal Home NH01–NH07 and RR/result IDs retained. |
| DM-T10 | Pages artifact resource completeness | NOT_RUN | Build artifact gate runs in `design-manager-pages.yml`. |
| DM-T11 | Actual GitHub Pages access | NOT_RUN | Live SHA / HTTP / browser evidence required after deployment. |
| DM-T12 | Authority and Governance | PASS (pre-PR) | Control Plane Admission and source integrity validator passed. |

## Negative tests

The release validator exercises failure cases using a temporary copy of the tree: a missing required Frozen image and a Frozen byte mutation must each produce a validation failure. The repository's source PNG/SVG bytes are not changed. Invalid route tokens and missing authority states are checked against explicit no-fallback/no-substitute UI rules.

## Pending CI evidence

The workflow runs must complete against the PR head before merge. Update this report with the PR SHA, Design Governance / Control Plane results, generated screenshot artifact, Pages build/deploy run, live PNG downloads and computed SHA values. Local results alone do not satisfy DM-T06, DM-T08, DM-T10 or DM-T11.
