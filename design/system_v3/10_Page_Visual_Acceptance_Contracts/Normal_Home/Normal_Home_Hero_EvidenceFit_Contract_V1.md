# Normal Home Hero EvidenceFit Contract V1

**Status:** `FROZEN_PRESENTATION_RULES / FIXTURE_PROVENANCE_PENDING`  
**Component authority:** `media.hero` page variant `home_normal_v1`; `design/pages/home/normal_home/engineering/Hero_Real_Photo_EvidenceFit_Contract_V1.md`.

## Source and record invariants

- The Hero is a presentation of the exact stored FishRecord photo, not the archived original itself.
- Respect decoded EXIF orientation. Preserve the source file bytes, color, timestamp, species evidence, and FishRecord ID.
- The page-owned Normal Home outer frame is 740×880 reference px at `S=1`, radius 32 reference px, with the existing footer region. Do not resize the card to fit a photo or its metadata.
- No substitute fish, synthesized pixels, inpainting, generative fill, recoloring, distortion, or crop beyond the source's verified fish-safe region.
- A failed image keeps the record and card; only the media subview changes to the registered neutral NH03 fallback. Recovery replaces only that subview.

## Deterministic mode selection

| Condition | Mode | Acceptance rule |
|---|---|---|
| Approved fish bbox/segmentation is correctly oriented and full fish plus contextual margin fits in a cover crop | `SAFE_CROP` | Use the smallest translate-only safe crop supported by the verified bbox. No fish pixel may be cut. |
| Source has a compatible aspect and known subject-safe framing | `SAFE_CROP` | Center cover only when source-specific evidence demonstrates the fish remains complete. Aspect similarity alone is not proof. |
| A cover crop would remove fish pixels or context, or a bbox is absent/untrusted and the subject may be near an edge | `EVIDENCE_FIT` | Show the full unchanged photo in the front layer; fill remaining card area with a low-salience blur of the same source. |
| Verified technical black bars | `EVIDENCE_FIT` unless independent media evidence proves the bars are technical and a source-preserving trim is registered | Retain the original. Never strip an uncertain dark region or fish silhouette. |
| Photo absent/unreadable | `MEDIA_FALLBACK` | Keep species, metadata and record identity; use only registered NH03 neutral media fallback. |

For `EVIDENCE_FIT`, `geometry.media.evidence_fit` and `geometry.media.blur_background` govern. The background blur is display-only and cannot be saved, uploaded as the original, or used as a new asset. `geometry.media.cover` is allowed only after the safe-crop condition above; `geometry.media.fit` preserves aspect and the full source.

## Required fixture matrix

| Case | Input | Expected decision | Evidence status at contract freeze |
|---|---|---|---|
| F01 | 4:3 landscape | SAFE_CROP only with approved complete-fish evidence; otherwise EVIDENCE_FIT | `SOURCE_FIXTURE_PENDING` |
| F02 | 16:9 landscape | SAFE_CROP only with approved complete-fish evidence; otherwise EVIDENCE_FIT | `SOURCE_FIXTURE_PENDING` |
| F03 | 3:4 portrait | SAFE_CROP only with approved complete-fish evidence; otherwise EVIDENCE_FIT | `CANDIDATE_ONLY / SOURCE_PERMISSION_PENDING` |
| F04 | 9:16 portrait | SAFE_CROP only with approved complete-fish evidence; otherwise EVIDENCE_FIT | `SOURCE_FIXTURE_PENDING` |
| F05 | Fish close to or touching an image edge | EVIDENCE_FIT when cover risks cutting the fish | `SOURCE_FIXTURE_PENDING` |
| F06 | Black-border image | Preserve uncertain bars; choose EVIDENCE_FIT until technical border is proven | `SOURCE_FIXTURE_PENDING` |
| F07 | Low-confidence, missing or inconsistent fish box | Treat bbox as untrusted; use EVIDENCE_FIT | `CANDIDATE_ONLY / GROUND_TRUTH_PENDING` |

The upstream frozen fixture register contains eight tests, has only four unique candidate JPEG hashes, and states that rights/provenance/bbox ground truth are unverified. No candidate is promoted to an approved original here. Runtime image-case verdicts remain `NOT_RUN`.

## Runtime assertions

For every fixture record: source SHA, dimensions, EXIF orientation, provenance/permission, bbox and coordinate frame if available, chosen mode/reason, whole-fish visibility, source-file SHA before/after, and matched Home/Detail screenshots with the same record ID. Check original pixels remain unchanged. A missing source, provenance, box trust or screenshot blocks that case; it is never a visual PASS by default.
