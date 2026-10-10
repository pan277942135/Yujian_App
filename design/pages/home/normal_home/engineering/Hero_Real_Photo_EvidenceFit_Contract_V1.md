# Normal Home — Real Catch HOME Hero Media Contract V1
Status: FROZEN_PRESENTATION_RULES / INDEPENDENT_REAL_PHOTO_COVERAGE_NOT_YET_VERIFIED
Authority: NH01/NH02 Hero 740x880 reference outer frame, shared YuJianHeroCard/HOME, current Home screenshot and FishRecordDetail evidence semantics.

## Media principles and object priority

The Home Hero is a **presentation crop**, not an archived original photograph. The original fish-catch media and FishRecord identity MUST be preserved in the data layer and opened FishRecordDetail, unmodified. Do not fake/create/replace a fish. No portrait illustration, model-generated fish or other catch substituted into missing photos.

1. Use the exact real stored FishRecord photo, oriented according to its EXIF/decoded orientation. Keep photo color, species evidence, timestamp and original image provenance. Recognize source black bars before deciding crop; no stretched, rotated, synthesized scene.
2. For photos that safely support a center cover crop without cutting off the identified fish, use `ContentScale.Crop` (centered image-to-frame cover) with single rounded Hero clip and restrained lower legibility gradient. Crop-to-fill does not mean crop-away-the-fish.
3. When a verified fish bbox/segmentation is available, choose the smallest translate-only crop within the original photo that keeps the whole fish's verified silhouette and at least a small contextual margin, where feasible; do not resize the Hero card. Verified bbox MUST be in the actual decoded orientation and source photo coordinate frame; reject inconsistent boxes.
4. If object cannot fit within a cover crop or bbox is untrusted/missing and central cover visibly harms evidence, choose **EVIDENCE_FIT**: fit the entire unaltered image in a front layer and fill unused Hero area with a soft defocused crop of the **same photo**, bounded within the same rounded card. The back layer is explicitly a display derivative, never a source/archived original; source bitmap remains untouched. No black/pure page-background letterboxing. Do not invent image content.
5. If no bbox is available, do not pretend center crop is fish-safe. Use center Crop for non-extreme aspect similarity only where a QA/photo fixture establishes safety; otherwise default to EVIDENCE_FIT. Preserve real evidence over photographic full bleed. Exact mode and reason are required in per-record debug diagnostics.
6. If source media already contains black bars, distinguish actual captured black background from letterboxing using reliable edge/format evidence. Only trim **verified technical borders**, and retain original file. If uncertain, treat as captured content and use EVIDENCE_FIT; never blindly crop visually dark fish fins.
7. For portrait, landscape, near-edge fish, incomplete fish, flash/night capture: no hallucinated completion, inpainting or generated extra fish; do not zoom past the source's edge or rewrite the captured image.
8. Image unavailable: neutral NH03 placeholder with species and record ID preserved. Loading/recovery replaces media surface only, no entering celebration.

The Home outer card must remain reference 740x880 at S=1, radius32, footer metadata inset left56 bottom32 and 20px text row spacing. Preserve color/contrast of source fish photograph; lower readability treatment must keep metadata readable without opaque black strips. No additional inside-Hero navigation. Swipe gestures and clickable Hero retain full size.

## Deterministic decision state

- `VALID_CROP`: verified object box fully contained by displayed cover crop with margin; no source clipping of fish; output `CROP`.
- `SAFE_CENTER_CROP`: source aspect ratio near Hero aspect 740/880 with no evidence of subject clipping; output `CROP`. Near ratio is an *optimization hint*, not fish-validity proof: if missing trusted box and subject position uncertain choose EVIDENCE_FIT.
- `INCOMPATIBLE_CROP`: real source image would lose verified fish region under cover; output `EVIDENCE_FIT`.
- `BBOX_UNTRUSTED` or `BBOX_ABSENT` with extreme aspect/edge risk: `EVIDENCE_FIT`.
- `IMAGE_UNAVAILABLE`: approved NH03 placeholder.

No fixed numerical confidence threshold is invented here, because detector thresholds belong to the frozen Recognition pipeline. A trusted bbox is one already approved by that pipeline with coordinate/orientation consistency; it is NOT inferred by an image aesthetic classifier.

## Eight independent source cases for acceptance

F01 horizontal 4:3; F02 horizontal 16:9; F03 vertical 3:4; F04 vertical 9:16; F05 extreme vertical (<0.5 aspect); F06 fish touches capture edge; F07 technical letterbox; F08 no reliable bbox. Each case requires a real, individually identifiable original photo with clear provenance, SHA-256, width/height, source/usage permission and fish region ground-truth or inspection label. F07 may be a **clearly marked processed derivative** of an approved real photo to create known technical bars, not a falsely presented independent original. F08 can use an approved real photograph without bbox and record `bbox=ABSENT`.

Existing repository media `app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg` and `app/src/androidTest/assets/recognition_real_catch_fixture.jpg` require byte-hash comparison and license/source review; they must NOT be claimed as two independent real photos merely because two paths exist. Do not generate AI fish to fill missing slots. If provenance is absent, keep `FIXTURE_PROVENANCE_PENDING` and the test `NOT_RUN`.

## Android test and evidence

For each fixture/mode capture:
- source original SHA/dimensions/EXIF orientation/source and permission;
- fish bbox if valid, bbox orientation frame and whether crop would remove any verified fish pixels;
- chosen mode CROP/EVIDENCE_FIT/PLACEHOLDER and reason;
- resulting visible fish body coverage or full-image visibility;
- original-resolution HOME screenshot and matching DETAIL screenshot from SAME FishRecord ID; no confusing source photo crop with stored original;
- no black bar exposure, no nonuniform image warp, one rounded card clip, footer text legible at standard and fontScale1.3;
- overlays are display layers only, saved FishRecord image must have identical hash before/after;
- run output records APK commit and SHA, screenshot/fixture hashes, case ID and PASS/FAIL/BLOCKED_INFRA/NOT_RUN.

Minimum 8 fixture cases, at least 16 Android runtime screenshots (HOME+DETAIL) when the required distinct sources are available. Those numbers are evidence targets, not pre-claimed results.
