# Page Contract Layer V3.1

**Authority:** Shared Foundation V3.1 plus page-specific Frozen authorities listed by each contract.
**Phase:** 3-A · Recognition core loop.
**Framework status:** READY; page freezes are evaluated independently.
**Scope:** Contracts only. This folder does not implement Android/Compose, Backend, API or database behavior, and does not certify runtime or device evidence.

## Canonical inputs

Shared Foundation values are read only from the paths listed in the Effective Authority Registry:
- design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json
- design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3_FINAL.json
- design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json
- design/system_v3/07_Responsive_System/Geometry_Tokens_V3_FINAL.json
- design/system_v3/08_Background_System/Background_Registry_V3.json
- design/system_v3/09_Icon_System/Icon_Registry_V3.json
- design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json
- design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json

A page contract may cite an existing page-specific Frozen visual or behavior authority only for the properties that source owns. It may not create page-private tokens, add a shared component outside the Component Registry, or alter Frozen artwork. When authorities disagree, the contract records the exact conflict and remains REVIEW until its owner resolves it.

## Contract package

Each registered page has:
- a human-readable page contract;
- a machine-readable layout dependency map;
- a state machine with explicit transitions;
- a data mapping including empty, loading, error, partial-data and network-failure behavior;
- an engineering contract with layout, insets, media, input and interaction rules;
- an executable acceptance contract with visual, interaction, accessibility and error checks.

Use Page_Contract_Template_V3_1.md for subsequent pages. Page_Registry_V3_1.json is the index and source of truth for each page status.

## Freeze rule

A page is FROZEN only when its visual authority is identifiable and consistent, every layout dependency resolves through the shared registries, data/error behavior is mapped, engineering rules are implementable, and all acceptance checks are measurable. A REVIEW page can contain complete artifacts while one or more explicit blockers remain open. REVIEW is not an implementation-ready approval.

## Phase 3-A status

Three recognition contracts are registered. The Recognition Processing and Recognition Result design packages remain independently marked FROZEN at their source; this does not imply that the compiled V3.1 page contracts or Android runtime are frozen/passed. Capture, Processing and Result contracts currently remain REVIEW for the blockers listed in the registry and report. Home, Fish Record, My Catches, Record Date, Fish Guide, Habitat, Account and Share have not been opened in this batch.
