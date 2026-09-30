# Recognition Result Hero Media Contract V1

Status: **FROZEN**  
Version: **V1**  
Scope: **Recognition Result 3+2 / Android-first**  
Numeric authority: `hero_media_contract.json`

## 1. Purpose

Recognition Result must display user-shot photos with stable page geometry even when the source media has different aspect ratios, orientations, subject positions, capture distances, night exposure, partial fish framing, or gallery-origin dimensions.

This contract freezes **how dynamic user media is mapped into the already-frozen Hero container**.

It does **not** redefine:
- Result page layout geometry;
- Recognition thresholds;
- detector/classifier semantics;
- fish bbox generation;
- the five Frozen Result references;
- the user's original photo content.

The page owns the Hero viewport.  
This contract owns the **media transform inside that viewport**.

## 2. Core principle

Priority is fixed:

```
Fish integrity
↓
Real catch context
↓
Stable Hero composition
↓
Fill efficiency
```

A Hero may show restrained support surface when necessary.

It must never crop away the fish merely to fill the rectangle.

## 3. Source authority

### 3.1 Display source

The Hero display source is:

**the user's original captured/selected photo after orientation normalization.**

The following are **not** Hero display sources:

- detector crop;
- classifier crop;
- generated fish cutout;
- B-side asset;
- Fish Guide artwork;
- AI-completed image;
- screenshot-derived replacement;
- blurred duplicate used as a background fill.

The detector bbox is guidance only. It tells the Hero renderer where the recognized fish is located in the original photo.

### 3.2 Content preservation

V1 permits only:

- orientation correction;
- scale;
- translation;
- crop;
- clipping to the frozen Hero shape;
- shared support surface behind uncovered areas.

V1 prohibits:

- generative expand/outpaint;
- object removal;
- background replacement;
- AI retouch;
- beauty/filter treatment;
- saturation/contrast grading of the photo;
- synthetic sharpening;
- fake depth blur;
- Ken Burns/pan-zoom motion.

## 4. Input normalization

Before Hero layout:

1. Decode source metadata.
2. Apply EXIF rotation.
3. Apply EXIF mirror/flip when required by the stored image orientation.
4. Establish an oriented source coordinate space with top-left origin.
5. Transform the fish bbox into the same oriented coordinate space.
6. Perform all later crop calculations in this normalized space.

The Hero renderer must never use a bbox defined in a different orientation than the displayed source.

## 5. Required inputs

The media placement function receives:

- `state`: RESULT_HIGH / RESULT_MEDIUM / RESULT_LOW / ERROR_NO_FISH / ERROR_IMAGE_QUALITY;
- oriented source width/height;
- Hero viewport width/height;
- Hero clip shape/radius supplied by page geometry;
- optional fish bbox associated with the same recognition source;
- optional recognition source identity used to verify bbox/photo correspondence.

The algorithm must not depend on one hard-coded Hero aspect ratio.

If the Hero viewport changes because of device size or later Layout Geometry revisions, the media transform is recomputed.

## 6. State policy

### 6.1 RESULT_HIGH / RESULT_MEDIUM / RESULT_LOW

Policy:

**Subject First**

If a trustworthy fish bbox exists:
- use `SUBJECT_CROP_FILL` when the subject-safe region can remain protected;
- otherwise use `SUBJECT_SAFE_FIT`.

If no trustworthy bbox exists:
- fall back to `SUBJECT_SAFE_FIT`;
- do not invent a focal point.

The Result state itself is not changed merely because the media renderer fell back from Crop to Fit.

### 6.2 ERROR_NO_FISH / ERROR_IMAGE_QUALITY

Policy:

**Evidence First**

Always use `EVIDENCE_FIT`.

Reason:
- No Fish must show the actual framing that failed to contain a usable fish subject;
- Image Quality must show the blur/occlusion/framing problem the user can correct.

Do not use fish-aware Smart Crop for these two recovery states even if a stale/partial bbox happens to exist.

## 7. Trustworthy fish bbox

A bbox may guide Hero layout only when all are true:

- it belongs to the same source image/recognition request;
- coordinates are finite;
- width > 0;
- height > 0;
- the bbox can be transformed into the oriented source coordinate system;
- coordinates can be safely clamped to source bounds without collapsing the rectangle.

If any condition fails:

`bbox = unavailable`

and the renderer falls back to `SUBJECT_SAFE_FIT`.

Media fallback must never alter model confidence or Result state routing.

## 8. Fish Safe Rect

For RESULT_HIGH / MEDIUM / LOW with a trustworthy bbox:

Start with the oriented fish bbox.

Expand:

- left: **14% of bbox width**
- right: **14% of bbox width**
- top: **18% of bbox height**
- bottom: **18% of bbox height**

Then clamp the expanded rectangle to the source image bounds.

This expanded rectangle is:

`FishSafeRect`

Purpose:
- preserve fins/body edges;
- retain a small amount of real capture context;
- reduce the commercial-product-photo look;
- protect hand-held / bucket / grass / shoreline context when it sits immediately around the fish.

The renderer must not intentionally crop inside FishSafeRect when a safe alternative exists.

## 9. Source-edge protection

Real fishing photos may already contain a fish partially outside the source image.

If a bbox side lies within **2% of the corresponding source dimension** from an image edge, mark that side:

`SOURCE_CLIPPED`

Examples:
- fish head already touches the left edge;
- tail already extends beyond the right edge;
- dorsal fin is cut by the top source boundary.

Rule:

**Hero layout must not introduce additional crop inward from a SOURCE_CLIPPED side.**

If Crop Fill cannot respect this condition, use Safe Fit.

The UI must not imply that missing anatomy can be recovered.

## 10. SUBJECT_CROP_FILL algorithm

Use only for High / Medium / Low with a trustworthy bbox.

### Step 1 — normalize viewport

Let:

`HeroAspect = viewportWidth / viewportHeight`

### Step 2 — build FishSafeRect

Use Section 8.

### Step 3 — construct the minimum aspect-matched crop

Find the smallest rectangle that:

- has `HeroAspect`;
- fully contains FishSafeRect;
- remains inside the oriented source bounds after translation;
- respects SOURCE_CLIPPED edge anchors.

### Step 4 — visual safety inset

After mapping the candidate crop into the Hero viewport:

FishSafeRect must remain inside a **12dp visual safety inset** from the Hero's rectangular edges.

If the viewport has rounded corners, the subject-safe region must also remain outside the corner clipping risk area.

The 12dp inset is a minimum. It may become larger naturally.

### Step 5 — translate, do not distort

When the crop rectangle would extend beyond source bounds:

- translate it toward available source pixels;
- do not shrink/stretch the photo;
- do not move FishSafeRect outside the safety inset.

### Step 6 — decision

Use `SUBJECT_CROP_FILL` only when all hard containment checks pass.

Otherwise:

`SUBJECT_SAFE_FIT`.

## 11. SUBJECT_SAFE_FIT

Use when:

- bbox is unavailable/untrustworthy; or
- FishSafeRect cannot be preserved by Fill; or
- a SOURCE_CLIPPED edge would be cropped further; or
- rounded-corner safety cannot be maintained; or
- extreme source ratio makes a safe fill visually destructive.

Behavior:

- display the oriented original photo with Fit/Contain behavior;
- preserve the entire source image;
- center by the source image bounds;
- do not zoom beyond the source merely to remove support bars;
- do not substitute detector/classifier crop.

Uncovered Hero area uses the support surface defined in Section 13.

## 12. EVIDENCE_FIT

For ERROR_NO_FISH and ERROR_IMAGE_QUALITY:

- always display the entire oriented source;
- ContentScale = Fit/Contain;
- no bbox focal positioning;
- no Smart Crop;
- no synthetic zoom;
- no blurred photo background;
- preserve evidence of framing, blur, occlusion and context.

The recovery copy explains the problem. The renderer does not hide it.

## 13. Support surface

When Fit/Contain leaves uncovered Hero area:

Use:

**shared GLASS_A support surface over BG_CONTENT**

Authority:
- `design/system/core_visual_v1/tokens/glass_tokens.json`
- `design/system/backgrounds/morning_lake_v1/treatment_contract.json`

Rules:

- never fill bars using a blurred/scaled duplicate of the user photo;
- never sample a dominant photo color and flood the Hero;
- never create a page-specific gradient;
- no additional sun/landscape assets;
- support surface remains visually subordinate to the photo.

If true backdrop blur is unavailable at runtime, use the shared GLASS_A runtime fallback. Do not blur the user photo as a substitute.

## 14. Quality and decode

The Hero must not visibly soften a valid source through an unnecessarily small decode.

Implementation target:

- decode at least the physical rendered Hero viewport dimensions;
- prefer up to **1.25× viewport pixel dimensions** when source resolution and memory allow;
- never request pixels beyond source native resolution merely to manufacture detail;
- do not apply AI upscaling in Result V1;
- preserve original color intent;
- do not apply Result-specific saturation/contrast/brightness grading to the photo.

Original source file remains the record authority. Display decoding must not re-encode/overwrite it.

## 15. Subject occupancy guidance

Hard requirements are containment, not artificial zoom.

For QA guidance only:

- preferred fish bbox visible-area share inside Hero: **45%–70%**;
- acceptable soft band: **35%–80%**.

This is a **soft quality signal**, not an automatic FAIL.

Do not violate FishSafeRect or source-edge protection merely to force occupancy into the preferred band.

## 16. Multi-fish / primary fish

If the pipeline returns multiple detections:

- use only the bbox associated with the classification/result shown on the Result page;
- do not union unrelated fish boxes;
- do not auto-reframe between detections;
- do not animate focal-point changes.

If the displayed species/result cannot be mapped to one bbox, use `SUBJECT_SAFE_FIT`.

## 17. Hero stability

The outer Hero container:

- does not change height because the source image ratio changes;
- does not jump when switching from Crop to Fit;
- does not animate crop position after Result has settled;
- does not continuously pan/zoom.

Only the media transform changes.

The page layout remains owned by the frozen Result composition and future Layout Geometry contract.

## 18. Result entry / motion

On transition from Processing to Result:

- the final Processing resolve fade remains **200ms**;
- the chosen Hero transform is ready before the Result becomes dominant;
- do not first show CenterCrop and then visibly jump to Smart Crop;
- no post-entry re-centering animation.

If bbox/media metadata arrives too late to compute Subject Crop before Result display:
- enter with Safe Fit;
- keep that stable for the current page session;
- do not visibly jump after the user starts reading/interacting.

## 19. Accessibility semantics

Suggested descriptions:

High / Medium / Low:
- `本次鱼获照片`

No Fish / Image Quality:
- `待识别照片`

Do not announce:
- bbox coordinates;
- confidence;
- crop mode;
- image dimensions.

Decorative support surface is silent.

## 20. Missing/failed image

If the source cannot be decoded:

- do not invent or substitute fish imagery;
- use `BG_SOLID_FALLBACK` / shared missing-media treatment;
- retain the page's state identity and available recovery actions;
- log the media failure separately from recognition semantics.

## 21. Hard acceptance gates

For High / Medium / Low with trustworthy bbox:

1. fish bbox rectangular coverage before rounded mask: **100%**;
2. FishSafeRect rectangular coverage: **100%**;
3. FishSafeRect visible coverage after rounded-mask clipping: **>=98%**;
4. no additional crop on a SOURCE_CLIPPED edge;
5. no detector/classifier crop used as the display source;
6. no blurred duplicate-photo support fill;
7. no post-entry crop jump.

For No Fish / Image Quality:

1. full oriented source image visible: **100%**;
2. ContentScale = Fit/Contain;
3. no bbox-based crop;
4. no blurred duplicate-photo support fill.

For all states:

- original orientation correct;
- no stretch;
- no generative modification;
- Hero outer geometry stable across source ratios.

## 22. Required implementation test matrix

Minimum scenarios:

1. portrait 9:16 photo, centered fish bbox;
2. portrait 9:16 photo, fish near left/right edge;
3. landscape 16:9 photo, long horizontal fish;
4. square 1:1 photo;
5. panorama/extreme landscape;
6. tall narrow photo;
7. fish bbox touching source edge;
8. partial fish already clipped by source;
9. invalid/missing bbox on High/Medium/Low;
10. No Fish with wide scene;
11. Image Quality with blur/occlusion;
12. EXIF 90° rotated gallery photo;
13. mirrored/orientation metadata case;
14. low-resolution source;
15. dark/night fishing source.

Each scenario must prove:
- selected media mode;
- orientation;
- crop/fill/fit behavior;
- hard containment metrics where applicable;
- absence of blurred-photo background;
- stable outer Hero geometry.

## 23. Prohibited shortcuts

- universal CenterCrop;
- detector crop as Hero;
- classifier crop as Hero;
- blurred source copy behind Fit image;
- generated background extension;
- AI outpaint;
- visual state change caused by media aspect ratio;
- using No Fish/Image Quality bbox to hide the capture problem;
- cropping SOURCE_CLIPPED fish further;
- re-running recognition only to improve Hero framing.

## 24. Authority boundary

If contracts appear to conflict:

1. state Frozen PNG defines Hero container placement and page composition;
2. this Hero Media Contract defines dynamic user-photo placement **inside** that container;
3. shared BG_CONTENT / GLASS_A define support surface;
4. Recognition Runtime Contract defines whether a state/bbox exists;
5. runtime implementation must adapt to the above.

This contract does not authorize changing the five Frozen Result PNGs.
