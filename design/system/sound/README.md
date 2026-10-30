# YuJian Shared Sound System

Use this directory for reusable product sound semantics and shared canonical audio assets.

Feature-specific sound contracts remain inside the owning feature package under `sound/`.

A shared sound definition must cover:
- semantic intent and trigger class;
- canonical asset/hash;
- format/channels/sample rate/duration;
- gain, loop and fade rules;
- interruption/mixing behavior;
- silent-mode/media-volume behavior;
- accessibility/settings behavior.

Ambient or decorative audio must never auto-play unless explicitly frozen by the owning feature contract.
