# YuJian Experience Manager

A small, zero-dependency repository tool for managing YuJian experience authority.

## Source of truth

The manager reads:

`design/registry/experience_registry_v1.json`

It does not replace feature-local Behavior / Visual / Motion / Haptic / Sound / Runtime / Evidence contracts.

## Commands

```bash
python3 scripts/experience_manager.py validate
python3 scripts/experience_manager.py summary
python3 scripts/experience_manager.py build
```

### validate

Fails only on governance integrity problems:

- malformed registry;
- duplicate feature IDs;
- unknown status values;
- missing required modality entries;
- a FROZEN modality with no authority;
- a registered authority path that does not exist.

Known product/design gaps such as MISSING, PARTIAL or ACTIVE_CLOSURE remain visible work and do not
make the registry invalid.

### summary

Prints the current feature × modality matrix and active gaps in Markdown.

### build

Generates two review surfaces:

- `design/registry/EXPERIENCE_STATUS.md` — convenient GitHub-readable matrix;
- `design/manager/index.html` — searchable/filterable local dashboard.

The generated files are views, not source of truth.

## CI

`.github/workflows/experience-governance.yml` validates the registry and builds/uploads the generated
views as a GitHub Actions artifact whenever design governance changes.

## Recommended daily workflow

1. Update/freeze a feature contract.
2. Update `experience_registry_v1.json`.
3. Run `python3 scripts/experience_manager.py validate`.
4. Run `python3 scripts/experience_manager.py summary`.
5. Run `python3 scripts/experience_manager.py build` when a local visual dashboard is useful.
6. Commit the contracts + registry. Generated views do not need to be committed.
