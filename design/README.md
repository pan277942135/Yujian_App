# YuJian Experience Source of Truth

This directory is the governed source of truth for the complete YuJian / 渔见 product experience.

It covers more than static visual design. Feature authority may include:

- Behavior / state semantics
- Visual
- Motion
- Haptic
- Sound
- Assets
- Runtime mapping
- Evidence / acceptance

## Governance

Normative governance:

`design/governance/YuJian_Experience_Source_of_Truth_V1.md`

Global feature/modality inventory:

`design/registry/experience_registry_v1.json`

New feature package template:

`design/templates/FEATURE_EXPERIENCE_PACKAGE_TEMPLATE.md`

## Principles

1. Frozen experience contracts are product inputs, not suggestions.
2. Product behavior and visual hierarchy must not be silently redesigned during implementation.
3. Motion, haptic and sound are first-class experience contracts, not implementation decoration.
4. "No haptic" and "no sound/autoplay" are explicit product decisions when applicable; Runtime must not infer them.
5. Shared tokens/components are preferred over page-local lookalikes.
6. Nature → real catch → memory → information → interaction → data → achievement is the required visual hierarchy.
7. 1080×1920 / 9:16 is the primary design reference canvas where applicable; runtime layouts must remain adaptive.
8. Source/reference assets do not ship inside the APK unless explicitly mapped to runtime assets.
9. Chat attachments, generated previews and local files are inputs until committed, registered and declared canonical.
10. A feature is not fully frozen merely because a high-fidelity PNG exists.

## Repository structure

```text
design/
├── governance/       # experience authority and change-control rules
├── registry/         # global feature × modality completeness inventory
├── templates/        # canonical feature package starter
├── system/           # shared visual/motion/haptic/sound systems and components
├── platform/         # platform-wide assets/contracts
└── pages/            # feature-owned experience packages
```

Preferred feature package:

```text
design/pages/<domain>/<feature>/
├── README.md
├── status.json
├── source/
├── behavior/
├── visual/
├── motion/
├── haptic/
├── sound/
├── shared/
├── platform/android/
└── evidence/
```

Existing authoritative packages are migrated non-destructively. Paths are not moved merely for cosmetic consistency when current contracts already reference them.

## Core UI V1

Canonical visual references remain frozen for:

- Empty Home
- Normal Home
- Recognition Result
- FishRecordDetail
- My Catches
- Fish Guide

Visual-system specification:

`design/system/core_visual_v1/YuJian_Core_Visual_System_V1.md`

The Core UI V1 freeze is a visual authority. Full experience completeness for motion, haptic,
sound, runtime and evidence is tracked independently in the global registry.

## Mature feature packages

### Empty Home V2

`design/pages/home/empty_home` is the current mature design-to-runtime package for Empty Home.
It contains frozen source, reusable masters, motion/haptic contracts, runtime mapping and evidence.

### Recognition

`design/pages/recognition` contains the nine frozen Recognition states plus processing visual/motion,
runtime and evidence contracts. Newer physical-device fidelity closure remains versioned separately
from the original V1.1 package.

## Shared non-visual systems

- `design/system/motion/`
- `design/system/haptic/`
- `design/system/sound/`

Use these only for semantics intentionally shared by multiple features. Feature-specific experience
contracts remain inside the owning page/feature package.
