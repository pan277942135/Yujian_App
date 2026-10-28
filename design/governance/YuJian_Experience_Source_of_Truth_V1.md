# YuJian Experience Source of Truth V1

Status: **ACTIVE GOVERNANCE**
Version: **1.0**
Scope: **All YuJian product experience assets and contracts**

## 1. Purpose

YuJian uses one governed source of truth for the complete product experience.

A page or feature is not considered fully frozen merely because a high-fidelity PNG exists.
The frozen experience may include visual appearance, motion, haptic feedback, sound,
behavior/state semantics, implementation mapping and acceptance evidence.

The goal is to ensure that Product, Design, Work and Android all resolve the same authority
without relying on chat history, screenshots, local files or undocumented runtime constants.

## 2. Authority model

Every feature package may contain these modalities:

1. **Behavior** — state machine, navigation, data semantics, copy behavior and interaction rules.
2. **Visual** — canonical frozen page/state references, layout, typography, materials and hierarchy.
3. **Motion** — timing, curves, phase, transforms, alpha, repeat policy and Reduce Motion behavior.
4. **Haptic** — event trigger, semantic intent, pattern, amplitude/duration where applicable and suppression rules.
5. **Sound** — event trigger, source asset, gain/loop/fade policy, interruption behavior and accessibility/settings behavior.
6. **Assets** — reusable source assets and approved runtime derivatives.
7. **Runtime** — platform-specific mapping from frozen contracts to implementation.
8. **Evidence** — screenshots, videos, traces, test output and acceptance results.

No modality may silently override another. Conflicts are resolved by the feature package authority
order recorded in its README/status file.

## 3. Canonical feature package layout

The preferred structure for every new or migrated feature is:

```text
design/pages/<domain>/<feature>/
├── README.md
├── status.json
├── source/
│   ├── frozen/
│   └── provenance/
├── behavior/
│   └── Behavior_Spec_V*.md
├── visual/
│   ├── Visual_Spec_V*.md
│   └── references/
├── motion/
│   └── Motion_Spec_V*.md
├── haptic/
│   └── Haptic_Spec_V*.md
├── sound/
│   ├── Sound_Spec_V*.md
│   └── assets/
├── shared/
│   ├── assets/
│   └── contracts/
├── platform/
│   └── android/
│       ├── runtime_map.json
│       └── README.md
└── evidence/
    ├── README.md
    └── manifest.json
```

Existing packages do not need a destructive move merely to match this shape.
Migration should preserve current canonical paths when other contracts reference them.
A package is compliant when its README/status file maps legacy paths into these semantic roles.

## 4. Global directories

```text
design/
├── governance/       # normative process and authority rules
├── registry/         # cross-feature inventory and modality completeness
├── templates/        # package templates
├── system/           # shared design system, tokens and shared experience components
├── platform/         # platform-wide assets/contracts such as launcher icon
└── pages/            # feature-owned experience packages
```

Feature-specific assets belong with the feature. Shared assets belong under `design/system/`
only when two or more features intentionally share the same semantic component or token.

## 5. Required status vocabulary

Each feature and each modality must use one of:

- `FROZEN` — authoritative, versioned and integrity-registered.
- `ACTIVE_CLOSURE` — authority exists; runtime/evidence closure is still in progress.
- `PARTIAL` — some authoritative material exists but the modality/package is incomplete.
- `RUNTIME_ONLY` — implementation exists but no approved design/experience authority is archived.
- `DESIGN_ONLY` — approved design exists but production runtime is intentionally absent/not yet implemented.
- `MISSING` — required authority has not been archived.
- `DEPRECATED` — retained only for history/compatibility and must not be used as current authority.

Do not use `PASS` as a replacement for freeze state. `PASS` is an acceptance result, not an authority state.

## 6. Versioning

Use semantic experience versions at feature/modality level:

- Major: product semantics or visual language changes materially.
- Minor: approved behavior/visual/motion/haptic/sound refinement without changing the feature identity.
- Revision suffix (`-r1`, `-r2`): closure correction against an already approved target.

Examples:

- `Empty_Home_Visual_Spec_V2.md`
- `Recognition_Processing_Motion_Spec_V1_1.md`
- `Recognition_Visual_Fidelity_V1_2-r1.md`

Never overwrite a frozen binary in place without updating its manifest/hash and provenance.

## 7. Integrity and provenance

Every frozen binary asset must have:

- canonical repository path;
- source/provenance description;
- dimensions/duration/sample rate where relevant;
- byte size;
- SHA-256;
- freeze version/state.

Recommended manifests:

- visual/reference manifest;
- motion asset manifest when motion uses rendered assets;
- sound asset manifest;
- shared asset manifest;
- evidence manifest.

Derived runtime assets must point back to their source authority.

## 8. Visual contract

A visual freeze must identify:

- canonical page/state references;
- reference canvas and adaptive-layout rule;
- component hierarchy;
- typography and spacing authority;
- color/material/glass/elevation rules;
- image treatment and cropping;
- state-specific variants;
- any region-level fidelity anchors needed for runtime validation.

Static screenshots are never sufficient authority for interaction semantics.

## 8.1 High-fidelity output and archive rule

High-fidelity visual authority follows these rules:

### Major pages

A major page must be generated and archived as one complete **9:16 App page per image**.

Required:

- no device frame;
- no poster title or design-description copy;
- no multi-page collage;
- use the approved Background / Component Authority;
- page copy, states, quantities, tags, spacing and interaction entry points must be implementation-accurate;
- a major page enters Design Manager only after visual confirmation;
- do not generate a multi-page board first and crop one panel into a major-page authority later.

### Secondary pages and states

Related secondary states may share one reference with **2–4 accurate panels** when this improves review efficiency.

Every panel must still contain the complete real UI state: correct background, components, copy, data, spacing, state and interaction entry points.

Acceptance rule:

> If any panel is cropped out by itself, development must still be able to use it as the high-fidelity authority for that state.

### Archive model

- Major page → standalone high-fidelity reference.
- Secondary state → accurate multi-state reference permitted.
- Text / behavior specification → independent authority.
- A batch enters Design Manager after its page/state references are confirmed.

Interaction-only specifications must not pretend to be App-page screenshots.

## 9. Motion contract

Every intentional animation must define:

- trigger/event;
- affected property/properties;
- start/end values;
- duration;
- delay;
- easing/interpolator;
- repeat/reverse policy;
- phase relationship to other motion;
- interruption/cancellation behavior;
- Reduce Motion behavior;
- deterministic test mode where needed.

Animations must be product-state-driven when the motion communicates state. Fake time-sliced
state progression is prohibited unless the product contract explicitly defines it.

## 10. Haptic contract

Every haptic must define:

- user or system event that triggers it;
- semantic class (selection, confirmation, warning, error, impact, custom);
- platform mapping;
- whether repetition is allowed;
- suppression rules;
- accessibility/settings behavior;
- whether automatic/background haptics are prohibited.

Default rule: no haptic fires merely because a screen appeared unless explicitly frozen.

## 11. Sound contract

Every product sound must define:

- trigger/event;
- canonical source asset and hash;
- format, channels, sample rate and duration;
- playback gain;
- loop policy;
- fade-in/fade-out;
- interruption/mixing behavior;
- silent-mode/media-volume behavior;
- accessibility/settings behavior;
- whether auto-play is allowed.

Default rule: no ambient or decorative sound auto-plays unless the feature contract explicitly allows it.

## 12. Behavior/state contract

Behavior authority must define:

- state model;
- transitions and guards;
- navigation;
- loading/error/empty/success semantics;
- persistence rules;
- user-edit/correction semantics;
- copy authority;
- data provenance and whether sample values are illustrative only.

Visual implementation must never invent new business states to solve a rendering problem.

## 13. Runtime mapping

Each implemented platform must maintain a mapping from experience authority to runtime code/assets.

For Android, the mapping should identify:

- Compose screen/component;
- runtime asset path;
- state controller/state machine;
- motion implementation;
- haptic implementation;
- sound implementation;
- tests/evidence jobs.

Runtime code is not itself the design authority unless the registry explicitly marks a modality `RUNTIME_ONLY`.

## 14. Evidence contract

Evidence must prove the modalities it claims to accept.

Typical evidence:

- static screenshots for visual state;
- video for motion and transition quality;
- event logs/traces for state timing;
- haptic instrumentation/manual device checklist where platform capture is impossible;
- audio capture or deterministic playback test for sound;
- screenshot/reference comparison for visual fidelity;
- emulator and physical-device coverage when required.

Evidence must record device/API/build/commit/run identifiers.

## 15. Freeze gate

A feature may be labeled `FROZEN` only when:

1. canonical authority exists for every required modality;
2. frozen binaries are integrity-registered;
3. behavior/state semantics are documented;
4. runtime mapping is known or the feature is explicitly `DESIGN_ONLY`;
5. acceptance criteria are defined;
6. unresolved conflicts are zero;
7. the global registry is updated.

A feature may be visually frozen while another modality remains `PARTIAL`; in that case the
overall feature status must not imply full experience closure.

## 16. Change control

Any change to a frozen modality requires:

1. identify the current canonical authority;
2. create a new version/revision;
3. preserve provenance of the previous authority;
4. update manifests/hashes;
5. update runtime mapping if affected;
6. regenerate evidence;
7. update the global registry;
8. merge only after conflict and acceptance gates are satisfied.

Chat attachments, local screenshots and generated previews are inputs until they are committed,
registered and declared canonical.

## 17. Migration rule for current YuJian repository

Existing authoritative paths remain valid.

Priority migration order:

1. Empty Home and Recognition: preserve current mature package structure; map into registry.
2. Core UI V1 pages: keep canonical images under `design/system/core_visual_v1/reference/`;
   page READMEs remain the owning semantic contracts.
3. Account & Privacy: keep the current frozen package; split modality contracts progressively.
4. Register, Edit Profile, User Agreement and B-side/Fish Memory: close current authority gaps
   before declaring full experience freeze.
5. New features: use the canonical package layout from the start.

## 18. Definition of done

For any future YuJian page/feature, "done" means the repository can answer, without chat history:

- What is the approved visual?
- What moves, when and how?
- What haptic fires, if any?
- What sound plays, if any?
- What states and transitions are real?
- Which assets are canonical?
- Which runtime code implements them?
- What evidence proves the implementation?
- Which commit/version is currently authoritative?
