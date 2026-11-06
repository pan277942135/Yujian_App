# AI Studio ZIP Full Restore Inventory — 2026-11-01 drop

- ZIP: `remix-remix-渔见-1011.zip`
- ZIP SHA-256: `01295638560875dcf65addd6e5c31216df733976ede0be4fe2ea8cfb680218fb`
- Main HEAD: `419f22a13be13fde87ca31e48e4a00da751bd8f8`
- Archive size: `317703550` bytes
- Entries: `1614` total; `1229` regular files; `385` directories; `0` symlinks.

## Comparison totals

| Result | Files |
|---|---:|
| Byte-identical to Main | 1104 |
| ZIP-only additions | 72 |
| Different from Main | 53 (includes one sensitive `.env`, whose contents and hashes are withheld) |
| Main-only paths absent from ZIP | 702 |
| ZIP Manifest rows / rows matching an actual diff | 19 / 15 |
| Non-sensitive differences omitted by ZIP Manifest | 109 |
| First Capture Guide candidate images | 8 |

The full JSON carries per-entry size, ZIP SHA-256, Main Git blob SHA, same/different status, Manifest membership, module, authorization state, recommendation, and final handling. The `.env` entry is recorded as sensitive with its content and hashes withheld.

## Three-source disposition

| Source | Authority | Result for this drop |
|---|---|---|
| AI Studio ZIP | Candidate code, layouts, motion and assets | Not treated as a complete snapshot. Select only file-level deltas that fit current contracts. |
| W5 candidate | Empty Home composition geometry | Main’s W5 candidate center `(531, 1161)` and event-driven Y-only motion remain authoritative for this integration. |
| Android Main | Product flow, frozen designs, camera readiness, security and runtime behavior | Retained where ZIP regresses or conflicts. Main baseline is `419f22a13be13fde87ca31e48e4a00da751bd8f8`. |

## High-impact findings

- Empty Home ZIP code returns to older coordinates and continuous sine motion, and drops the current event-start haptic guards. Keep Main’s W5/event implementation.
- First Capture Guide ZIP removes P03 and routes to the guide before the Camera Provider/Preview Ready gate. Keep the three-page and Ready gate. Eight added images remain `CANDIDATE_NEEDS_APPROVAL` because the ZIP has no source or license record.
- Build/security deltas change the application id, add global cleartext traffic, make model bootstrap fail open, and remove URL normalization/security handling. Keep Main.
- Design Manager changes remove fail-closed integrity/path checks. Keep Main.
- `RecognitionResultScreen.kt` has no ZIP difference; no HIGH Result restoration is claimed.
- The remaining capture-button redesign changes visual size, idle motion and Home→Capture transition beyond the frozen shared component contract. It is tracked separately as D04 and is not applied pending an explicit product decision.

## Handling

The ZIP is not merged as a tree. Generated `.aistudio` content and `public/assets/aistudio` exports are excluded. Runtime/contract regressions and unlicensed candidates are excluded. API 28 Runtime acceptance is `NOT_RUN` by owner instruction.