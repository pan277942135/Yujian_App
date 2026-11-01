# AI Studio / W5 / Main Three-Way Restore Matrix — 2026-11-01 drop

- Repository: `pan277942135/Yujian_App`
- AI Studio ZIP SHA-256: `01295638560875dcf65addd6e5c31216df733976ede0be4fe2ea8cfb680218fb`
- Main HEAD: `419f22a13be13fde87ca31e48e4a00da751bd8f8`
- ZIP entries: 1614 total, 1229 files.
- File comparison: 1104 identical, 72 ZIP-only additions, 53 different (one sensitive `.env` withheld), 702 Main-only paths.

## Authority assignment

| Source | Owns | Applied decision |
|---|---|---|
| AI Studio ZIP | Candidate implementation and assets | Per-file candidate source only; no whole-tree replacement. |
| W5 | Empty Home geometry candidate | Retain Main’s `(531, 1161)` bobber center and event-driven Y-only motion. |
| Android Main | Product behavior, privacy/security, camera readiness and frozen visuals | Preserve unless the exact delta is compatible with existing contracts. |

## Changed groups

| Group | ZIP delta | Main authority / disposition |
|---|---|---|
| Empty Home motion | Older coordinates; continuous sine replaces periodic event motion; event/haptic binding removed | Keep Main W5 coordinates, deterministic Y-only motion, lifecycle and haptic guards. |
| First Capture Guide | P03 removed; pre-Ready route; eight new images | Keep P01→P02→P03 and Provider/Preview Ready gate. Images stay `CANDIDATE_NEEDS_APPROVAL`. |
| Capture button and handoff | 88–96dp becomes 102–108dp; 5s ≤1.015 breathing becomes 4.5s up to 1.06; handoff peaks at 1.22 with new layered optics | Frozen shared Primary Capture Button contract remains; D04 requires product decision before changing it. |
| Build/network/security | App id, cleartext, fail-open bootstrap, URL normalization/security changes | Keep Main settings and fail-closed/error-safe behavior. |
| Design Manager | Integrity and safe-path guards removed | Keep Main fail-closed guards. |
| HIGH Result | No `RecognitionResultScreen.kt` difference | No ZIP HIGH Result change to integrate. Preserve Frozen/PR #294 behavior. |
| Generated media | `.aistudio` and `public/assets/aistudio` exports | Audit only; exclude from product runtime. |

## Cross-checks

- ZIP Manifest: 19 rows; 15 correspond to real ZIP/Main diffs; 109 non-sensitive file differences are omitted.
- Main-only files: 702; archive is not a complete source snapshot.
- PR #294 exact head `cd100fddc928edf3a0935b4b7dcd847c2a519eb0` remains open Draft and its recognition/reveal implementation is protected.
- No API 28 runtime acceptance was started.