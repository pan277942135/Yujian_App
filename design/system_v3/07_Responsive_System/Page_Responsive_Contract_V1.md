# Page Responsive Contract V1

**Owner:** W1, Issue #220
**Gate:** `G-PAGE-FREEZE-PIPELINE-W1-CONTRACT`
**Scope:** generic machine-contract structure and page adapter boundary. This document defines validation inputs; it does not approve a page design or runtime.

## Contract layers

`Page_Responsive_Envelope_Schema_V1.json` defines shared structure: page identity, registered route and implementation entrypoints, contract ID/version/status, exactly one registered visual-authority path and SHA-256, other authority references, included/excluded state-scope references, coordinate system, inset ownership, viewport/font-scale inputs, profile binding, validator adapter, structural resource references, validation bindings, acceptance domains, and evidence references.

`Page_Conformance_Profile_Schema_V1.json` defines page-owned categories: regions, component relationships, responsive rules, content priorities, page states, overflow/scroll policies, visual anchors, and page-specific validation conditions. Every profile item binds to an authority ID and locator with `resolution_mode: REFERENCE_ONLY`. The profile carries no executable geometry or threshold values. A page's named contract remains its only numeric authority.

The envelope requires none of NH01's A/B/C regions, `S/2` allocation, Hero or Pager fields, Neighbor Peek, M01–M05 anchors, or fish-record state. Those remain inside NH01's existing authority and its adapter.

## Interfaces and ownership

| Interface | Input | Result and owner |
|---|---|---|
| `loadPageContract(pageId, sourceSha)` | Registered page ID and exact checked-out source SHA | W3 can load the envelope and its SHA-bound profile; this W1 CLI resolves explicit matrix pairs and checks the expected Git SHA. |
| `validateEnvelope(envelope)` | One envelope | W1 checks its schema, canonical schema reference, page registration, route owner, entrypoints, authority allowlist and file SHA, profile binding, status, and duplicate numeric authority. |
| `validateProfile(profile, envelope)` | Profile and its envelope | W1 checks the profile schema, page/profile identity, registered authority bindings, reference-only locators, and one-or-zero numeric authority according to profile status. |
| `resolveProfileConstraints(profile)` | Reference-only profile | Later workspace/test tasks resolve values from the cited authorities. W1 does not copy or execute those values. |
| Page validator adapter | Envelope/profile pair | NH01 calls the existing NH01 V2 validator unchanged. Draft adapters report `NOT_APPLICABLE`; they cannot turn schema validation into page acceptance. |

W1 owns the structure and validator. W2 owns resource provenance, resource SHA-256, lifecycle state, and publication permission. `resource_binding_refs` are structural pointers only and intentionally have no hash or permission fields. W3 owns the shared workspace shell and page renderer adapter. W4 owns the test matrix, browser/geometry runner, visual diagnostic, evidence manifest, and gate result. W5/W6 own their page pilot profiles and acceptance.

## Status and acceptance boundary

Envelope status is one of `DRAFT`, `PROPOSED_FOR_CANONICAL_MAIN_REVIEW`, `SPEC_FROZEN`, or `DEPRECATED`. Profile status is `DRAFT` or `FROZEN_BINDING`. These statuses describe the contract binding only.

The validator report has `page_acceptance: NOT_VERIFIED` for every sample. The draft Home Empty and Login envelopes also mark `assets` and `page_state` as `NOT_VERIFIED`. In particular, `PASS` from `G-PAGE-FREEZE-PIPELINE-W1-CONTRACT` means structural validation and the declared adapter check passed. It does not mean browser, human, W5/W6 pilot, or Android runtime acceptance passed. Browser evidence never implies native runtime evidence. Any acceptance `PASS` requires a matching evidence reference with a 40-character source Git SHA, workflow Run ID, and report path; the report itself records its checked-out source SHA and Run ID.

The `home_normal_v1` registry identity is explicitly adapted to the legacy NH01 JSON identity `normal_home_v1`. The adapter is read-only and invokes `scripts/design/validate_nh01_responsive_layout_contract_v2.py`; it does not rewrite NH01's contract or assertions.

## Examples and validation

The schema validation matrix contains three different shapes:

- `home_normal_v1`: frozen NH01 envelope bound to the existing V2 authority and legacy adapter.
- `home_empty_v2`: draft empty-state profile referencing its page-owned responsive mapping; it has no Pager fields.
- `auth_login_v2`: draft form profile with form-specific component relationships.

The last two examples validate the W1 contract shape only. They remain `DRAFT` with acceptance domains `NOT_VERIFIED`; neither is formal W5/W6 acceptance.

Run locally from the repository root:

```bash
python -m pip install --disable-pip-version-check -r scripts/design/requirements_nh01_cloud.txt PyYAML==6.0.3
python scripts/validate_control_plane_admission.py
python -m unittest discover -s scripts/design/tests -p 'test_page_responsive_contract.py' -v
python scripts/design/validate_page_responsive_contract.py \
  --expected-source-sha "$(git rev-parse HEAD)" \
  --report /tmp/page-responsive-contract-report.json
```

The validator rejects an unknown page/route, absent or mismatched profile, illegal lifecycle status, missing required fields, noncanonical schema reference, unregistered authority, byte/SHA mismatch, profile identity mismatch, duplicate executable numeric authority, and inline numeric profile values. A report is source-SHA bound. In Actions, the same report is uploaded with the workflow Run ID and exact PR HEAD SHA.
