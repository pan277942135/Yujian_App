# YuJian Design Manager

A small, zero-dependency design-governance tool for YuJian / 渔见.

The V1 scope is intentionally limited to **design management**:

- Behavior
- Visual
- Motion
- Haptic
- Sound
- Assets
- Design versions
- Authority / freeze state

Runtime, Evidence and Work handoff are deliberately outside the V1 UI.

## Source of truth

The manager reads:

`design/registry/experience_registry_v1.json`

Feature-local specs and manifests remain the actual authority. The registry is the cross-feature index.

## Open the manager

From the repository root:

```bash
python3 scripts/design_manager.py serve
```

Then open:

`http://127.0.0.1:8765/design/manager/`

No Node, Vite, database or package installation is required.

## Validate

```bash
python3 scripts/design_manager.py validate
```

Validation checks:

- Design Manager metadata;
- one display name and one current design version per module;
- design-only modality completeness;
- allowed status values;
- every FROZEN modality has an Authority;
- every registered Authority / Contract path exists;
- version visual/spec Authority paths exist.

Known design gaps such as MISSING, PARTIAL and ACTIVE_CLOSURE do not fail the tool. They are managed work.

## Summary

```bash
python3 scripts/design_manager.py summary
```

Prints the design-only matrix:

`Behavior / Visual / Motion / Haptic / Sound / Assets`.

## UI

The manager currently provides:

1. module list + search + status filter;
2. current design version and design overall state;
3. direct Visual Authority preview when the Authority is an image;
4. six design-modality cards with Authority links;
5. design version history, including Current and Candidate versions.

## Current Empty Home example

The first complete sample is Empty Home:

- V2: FROZEN and current canonical authority;
- V3: CANDIDATE / active visual-fidelity closure;
- V3 does not become current until a superseding canonical PNG/manifest is registered.

## Editing rule

Do not edit UI state as a second source of truth.

Update:

1. feature-local specs/assets/manifests;
2. `design/registry/experience_registry_v1.json`;
3. run `python3 scripts/design_manager.py validate`.

The browser UI is a read-only view of repository authority in V1.
