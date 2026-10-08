# YuJianCatchHeroCard Adaptive Media V1.1 — Design Candidate

Status: **CANDIDATE / NOT FROZEN**  
Scope: shared media presentation policy for `HOME` and `DETAIL` on FishRecord A-side only  
Supersedes: **none yet**; V1 remains frozen until user-approved screenshot parity  
Source authority: `Hero_Card.md`, `YuJianCatchHeroCard_Media_Edge_States_V1.md`  
Owner: shared component design system; implementation must not fork photo-fitting rules by page.

## 0. Purpose and invariant

A single genuine user catch photograph must work in the independently frozen HOME and DETAIL Hero containers, whether captured landscape, portrait, ultra-wide, tall, at night, with partial fish near the edge, or with an embedded letterbox.

**Outer geometry never adapts to the source image. Media presentation adapts to the container and source image.**

- HOME: frozen 1080×1920 reference uses approximately **740×880** Hero anchor (portrait-shaped).
- DETAIL: frozen 941×1672 source uses approximately **841×540** Hero anchor (landscape-shaped).
- Do not replace either with the landscape previews in the V1 media-edge-state board.
- All crop/fit/backdrop treatment is presentation-only; source bytes, EXIF-backed orientation truth, and upload identity are unchanged.
- The exact same source photo must be shown in the two page variants, with different non-destructive view transforms if necessary.
- A fish clipped by the *original photograph* cannot be reconstructed or promised complete; guarantee at minimum that the entire existing source content is visible in the fallback.

## 1. One media planner, two container geometries

Create a single, **pure and unit-testable** `CatchHeroMediaPlanner` invoked by HOME and DETAIL A-side. Its inputs:

`sourceWidthPx, sourceHeightPx, orientedSourceRect, viewportWidthPx, viewportHeightPx, trustedFishRect?, trustedFishQuality?, mediaKind, letterboxAnalysis`.

Outputs:

`mode, effectiveSourceRect, foregroundFit, backgroundPolicy, safeSubjectRect, reason, layoutTransform`.

Four modes:

1. `SUBJECT_CROP`: crop into target aspect *only when a verified fish bounding box and its safety padding fit inside the candidate source crop*.
2. `SAFE_FIT_AMBIENT`: fit the whole oriented source sharply; fill uncovered areas with blurred, subdued same-source backdrop (no hard color/black bars).
3. `EDGE_PROTECTED_FIT`: fish box touches, overlaps or nearly touches a source edge (2% guard band); never add secondary crop.
4. `EVIDENCE_FIT`: fish bbox absent/invalid, detection questionable, transformation ambiguous, source extremely narrow/wide, or letterbox analysis uncertain; fit the full original visible media. This is the **mandatory fallback**.

Mode names describe user-visible behavior. Modes 2–4 share the same foreground FIT transform.

### Decision precedence (mandatory)

1. Normalize orientation and viewport measurement before computing transforms. Do not infer photo orientation from device rotation or original file width alone if EXIF orientation has not been applied.
2. Verify solid embedded bars conservatively; trim presentation-only **only** when uniformly verified and trim cannot erase content. If doubt, keep bars in source and use full-source FIT.
3. If metadata is missing, bbox invalid, quality untrusted, source edge touched, or source-to-viewport mapping inconsistent: `EDGE_PROTECTED_FIT` or `EVIDENCE_FIT`; **never optimistic center-Crop**.
4. If trusted bbox exists, pad it horizontally by at least 14% of bbox width and vertically by at least 18% of bbox height; respect a final minimum **12dp** viewport inset where feasible.
5. Solve a candidate crop rect with exactly the viewport aspect ratio. Shift crop center to include the safe fish rect; clamp to source bounds.
6. Use `SUBJECT_CROP` only if the entire padded fish rect remains inside this crop, with 12dp final insets (small or already-edge-clipped images select FIT). Otherwise `SAFE_FIT_AMBIENT`.
7. HOME and DETAIL independently recompute their own presentation plan from the **same source** using their distinct viewport dimensions.

**Do not select `Crop` solely because source is landscape, portrait, or apparently close to target aspect.** The fish location is the controlling signal; without trustworthy location, select FIT.

## 2. HOME versus DETAIL acceptance table

| Source media | HOME ~740:880 | DETAIL ~841:540 |
| --- | --- | --- |
| 4:3 landscape | crop only if padded fish rect survives; otherwise fit+ambient | crop only if safe, else fit+ambient |
| 16:9 landscape | crop only if safe; else fit+ambient | crop only if safe; else fit+ambient |
| 3:4 portrait | crop only if safe; else fit+ambient | normally fit+ambient; crop only if proven safe |
| 9:16 portrait | crop only if safe; else fit+ambient | normally fit+ambient; crop only if proven safe |
| extreme >2.5:1 or <1:2.5 | evidence/safe fit | evidence/safe fit |
| fish touches source edge | edge-protected fit | edge-protected fit |
| missing/invalid box | evidence fit | evidence fit |
| verified embedded solid bars | source-preserving presentation trim if proven safe, then reassess | same |
| suspicious dark border/night image | do not auto-trim | do not auto-trim |
| corrupted/unavailable media | local neutral fallback, keep text/geometry | local neutral fallback, keep text/geometry |

This table is an expected priority guide, **not an orientation-only hard switch**.

## 3. Backdrop and readability

- Use one source image for sharp foreground and same-source ambient background, with subdued color, mild darkening and controlled blur; foreground remains unmistakably dominant.
- No static lake wallpaper, synthetic fish, generative replacement, duplicate hard silhouettes, high-saturation blur streaks, neon, HUD, black information strip or visible hard letterbox.
- Backdrop fills the whole fixed container behind the FIT photo. No user-image reencoding or upload changes.
- Text gradient is confined mainly to the lower text zone, adapted to actual photo luminance. Do not blanket-darken the fish.
- Fish remains first visual subject; time/location HOME and metadata/low-weight Edit DETAIL do not obscure fish head/tail. If necessary, relocate overlay within the existing allowed metadata zone, not outside the page geometry.
- Maintain accessible text contrast and touch target in all light/dark photo tests.

## 4. Reliability and truth source

Current Android implementation evidence:

- `RecentFishCard.kt` uses `RemoteImage(... ContentScale.Crop, trimVerifiedLetterbox=true)` for HOME.
- `FishRecordHeroCard.kt` uses `preservePortraitWithFitBackdrop = !showBside` for DETAIL: only portrait sources take the current FIT path; landscape still defaults to Crop.
- `RemoteCatch` parsed by `CatchRepository.kt` exposes no direct fish box, even though save requests contain a `detector_result` JSON. **Never assume that persisted/returned data includes a valid bbox.**

Implementation must therefore be **safe without bbox**. It may consume a separately verified, appropriately transformed persisted detector rectangle if available through a documented API contract; adding such a field is optional and a separate data/API change. Do not reopen recognition inference thresholds, camera, detector, classifier, or memory B-side behavior.

Do not silently run fresh costly recognition/inference in HOME scrolling. Do not block UI waiting for a bbox. If no trusted bbox when rendering, show FIT immediately; an optional later validated crop is allowed only with a non-jumping transition and approval.

Memory: downsample bitmap to destination size for display, use caching, handle EXIF and image loading/error paths deterministically. Do not repeatedly decode full-res bitmaps or reprocess blurred backdrops on every Compose recomposition.

## 5. Required real-device acceptance

Build a reproducible local fixture set from genuine photographs with: landscape 4:3, landscape 16:9, portrait 3:4, portrait 9:16, extreme portrait, extreme landscape, source-edge fish, embedded uniform black bars, dark night capture, absence of bbox, questionable bbox, no fish, and unavailable media.

For **every** media fixture, render **both HOME and DETAIL** using the frozen geometry and capture the rendered Android screenshot. Include 1080×1920 and 1080×2340 class coverage or explicitly mark the unavailable physical matrix **BLOCKED_INFRA**, never PASS.

Automated geometry/unit tests must assert:

- source bytes immutable and original image identity stable;
- mode, oriented mapping, normalized safe box, padding and final render bounds;
- no portion of trusted padded fish box lost in `SUBJECT_CROP`;
- when no trustworthy box exists, `EVIDENCE_FIT` keeps all original visible content;
- no transform stretches source aspect ratio;
- no hard black container gutters, no fish head/tail clipped by *additional* processing when safety guard can prevent it;
- page outer size, radius, text/footer footprint and CTA click semantics remain contract-compliant;
- dynamic data/media errors/recomposition/fast carousel swipe do not crash.

Visual screenshot review by user must validate real fish legibility, subdued ambient, text legibility and page-level parity against the corresponding independent HOME and DETAIL frozen references. Hash/manifest/PNG existence checks **do not** count as screenshot parity.

Final gate result for each variant: `PASS / FAIL_VISUAL / FAIL_GEOMETRY / FAIL_MEDIA / BLOCKED_INFRA`.

## 6. Allowed implementation scope and freeze

Target a new implementation branch, do not merge main or alter PR #97 without an explicit integration task. Do not change the existing V1 frozen raster/spec/hash or the HOME/DETAIL frozen reference rasters. B-side assets keep their existing independent semantics.

Before freezing V1.1, deliver:

- two actual-size visual authority boards for HOME and DETAIL using real source photos;
- deterministic fixture manifest (source, orientation, normalized fish box provenance, expected mode, hashes);
- unit/instrumentation results, build/lint results and before/after Android screenshots with page-level visual diff evidence;
- user approval of the screenshots.

Until then, this is `CANDIDATE` and the shared Design Manager item remains frozen at V1. No unreviewed registry promotion.
