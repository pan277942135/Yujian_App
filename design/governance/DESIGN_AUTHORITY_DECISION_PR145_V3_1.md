# Design Authority Decision — PR #145 V3.1 Reconciliation

| Field | Value |
|---|---|
| Decision ID | `AUTH-PR145-20261016-02` |
| Status | `USER DECISION RECORDED / DRAFT CONTROL PLANE CHANGE — EXACT-HEAD GATES REQUIRED` |
| Canonical baseline main | `2d7a75efba0ca1b66fc9be1e0860072a5f1b8a5d` |
| PR #151 current pre-change head | `ca2d2f8d56511f0fe3954d407081b04aad69cd61` |
| Product PR | `#145` — Draft / Open / Unmerged |
| Latest verified PR #145 head | `91240b89280cc51e2b5aa9c56f04e19a57453a6c` |
| User decision date | `2026-10-16` |

## Current first-use guide authority — exactly two pages

The latest user decision for this work sets P01 to portrait phone + horizontal fish and P02 to landscape phone + horizontal fish. The former portrait-phone + vertical-fish teaching page is canceled as active content and retained in the archive. The landscape/horizontal original bytes formerly labeled P03 are promoted unchanged as active P02.

| Page | Phone | Fish | Original SHA-256 | Git blob | Status |
|---|---|---|---|---|---|
| P01 | portrait | horizontal | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` | `3f73f744104777beee29bd2370ecac1f40197a1d` | active / FROZEN |
| P02 | landscape | horizontal | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | `2f46b2f2906f258eef8ca67181fc049a55d045af` | active / FROZEN |
| archived former P02 | portrait | vertical | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` | `9c2dd981a04792c6ac51e3925727b4e4e6704034` | archive only |

The V1.2 Guide Spec, manifest, navigation, Feature Matrix, P3-03/N010/P6-01 tasks, `G-FIRST-USE-GUIDE-ASSET`, Unified Contract, Experience Registry, Capture authority, Asset Manifest, and 15-state Master Board mapping have been synchronized to this page set. The resolved gate requires P01/P02 bytes and Design Governance evidence. Historical P03 identifiers appear only in provenance/archive fields.

## Frozen asset and capture source boundaries

The active P01 and P02 image payloads are byte-identical to the verified originals; only their semantic page mapping and repository path for active P02 change. The former vertical-fish P02 remains preserved under `archive/`. No Frozen image was redrawn, resampled, recompressed, or AI-generated.

Capture MB-05 remains `SOURCE_MISSING` for `a_realistic_high_resolution_vertical_smartphone_c.png`, expected SHA-256 `6d67d03045f5f3dfb66adad10921d7456a2c82620b7b68f156cb98be74f1908c`. Its original has not been found in the handoff ZIP, Library results, or reachable Git history; no substitute is generated. Board status remains PARTIAL.

## Work State provenance repair in this control-plane change

PR #150 already established source ancestry checks and contract alias resolution. This follow-up additionally separates the tested `source_sha`, derived `state_commit_sha`, and current branch HEAD; rejects a state-only commit as a source; and adds Git integration tests for submitted state, consecutive state-only updates, and checkpoint restore. PASS remains evidence-gated.

## Deferred work and merge boundary

- PR #145 remains untouched by this change and must remain Draft/Open/Unmerged.
- Figma remains untouched until canonical control-plane changes are approved, merged to main, synchronized to PR #145, and product-branch Admission passes.
- Final Figma fills, aspect/crop review, 5120×2880 PNG, and PDF exports are downstream deliverables, not claimed here.
- This Draft PR is not a merge authorization. Exact-head gates and human authorization remain required.

## Superseded review history

An earlier review snapshot in this file's prior revision accepted a three-page P01/P02/P03 interpretation based on task text then present on PR #145. That snapshot is superseded by the latest explicit user decision recorded above. Its existence is retained in Git history; it is not an active design authority.
