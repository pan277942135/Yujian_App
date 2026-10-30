# YuJian Shared Haptic System

Use this directory for haptic semantics intentionally reused across features.

Feature-specific haptic contracts remain inside the owning feature package under `haptic/`.

A shared haptic definition must cover:
- semantic intent;
- triggering event class;
- Android platform mapping;
- repetition/suppression rules;
- accessibility/settings behavior;
- device evidence expectations.

Absence of haptic is also an explicit product decision and should be recorded in the feature contract.
