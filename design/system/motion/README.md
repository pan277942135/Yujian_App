# YuJian Shared Motion System

Use this directory only for motion semantics/tokens intentionally shared by multiple features.

Feature-specific animation contracts remain inside the owning feature package under `motion/`.

Shared motion definitions must cover:
- semantic name;
- duration/easing;
- repeat/phase rules;
- Reduce Motion mapping;
- platform/runtime mapping;
- version and provenance.

Do not move a one-off page animation here merely for convenience.
