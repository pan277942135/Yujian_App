# Recognition Runtime V1.2 execution checkpoint

**Task:** `YUJIAN_RECOGNITION_RUNTIME_13_STAGE_INTEGRATION_V1_2`
**Repository / PR:** `pan277942135/Yujian_App` / #317 (`Draft`, `Open`, `Unmerged`)
**Branch:** `feature/orb-photo-stable-visual-closure-v1`
**Main baseline:** `74cad95b4f673bd9c8c60d16839f036d5886d933`
**Current validated source:** `ead45796f8332ef673fd615402c72b995fb5f09d`
**Current checkpoint:** CP05 complete; CP06 is next.

## CP01–CP04 status

| CP | Status | Source / evidence |
|---|---|---|
| CP01 PHOTO_STABLE | Implemented | `81a0c257417c0be050e8dd57ace95e97619babf4`; existing exact-source workflow passed. |
| CP02 ORB_WAKE | Implemented; device visual `NOT_RUN` | `ab2407ab42459f12ccb8178df7ebe3badc396c13`; Orb workflow `42010365270` passed. Core is independently drawn below optical effects. Debug A/B/C comparison states remain available. |
| CP03 ORB_RELEASE | Implemented; resource freeze identity `NOT_CONFIRMED`; device visual `NOT_RUN` | `4dc78515bb006f2952386d046db079eb2dce3162`; Orb workflow `42011287356` passed. The existing five R06 paths and one shared SceneClock remain in use. |
| CP04 PATH_TO_FISH | Implemented; mask-tail heuristic intentionally returns no anchor when ambiguous; device visual `NOT_RUN` | Code commit `9e8d99471c2db09c9e244d804a4cc8da3c5d3c2c`, assertion correction `84c2bf30d51d38c3720f4124315ec799de601bd1`; exact-head Orb workflow `42019562279` passed. |
| CP05 CONTOUR_A/B/C | Implemented from trusted current Mask; device visual `NOT_RUN` | Source `ead45796f8332ef673fd615402c72b995fb5f09d`; exact-head Orb workflow `42023472919` passed unit tests, Debug and AndroidTest APK builds, packaged asset checks, and artifact upload. |

## CP04 root cause and correction

`WaterLightMaskAdapter.toPhotoSpace` already converts the current SlimSAM ROI contour into canonical, EXIF-normalized whole-photo coordinates. The prior seek anchor then applied the detector BBox a second time and averaged contour points. That produced neither the fish-tail contact nor a stable source-photo coordinate. It could also move when the detector crop changed.

The corrected anchor resolver selects the largest closed outline, estimates its principal axis, and samples cross-section widths to identify a caudal-peduncle narrowing between a widened tail profile and the body. It returns no anchor when that shape is ambiguous. The resulting source-photo point uses the current `RecognitionImageTransform`; points outside the visible Crop viewport are rejected rather than clamped to a fabricated edge point.

The outward path ledger now binds its fish segment to session, attempt, image, photo SHA, geometry version, and a current Mask fingerprint. A return path can reuse the fish segment only when that Mask fingerprint still matches. This prevents stale attempt or Mask geometry from entering the Orb return route.

While Detector is ready and either Classifier or SlimSAM is still pending at `PATH_TO_FISH`, the existing perimeter shader remains active at low opacity (`0.12`, or `0.06` under reduced motion) and continues to sample the shared optical SceneClock. The controller keeps its existing Classifier/SlimSAM terminal join and FULL / ORB_ONLY decision; no completed release stage or second clock is replayed.

Changed CP04 files:

- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishTailAnchor.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderOverlay.kt`
- `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/WaterLightPathLedger.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionFishTailAnchorTest.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMathTest.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/WaterLightPathLedgerTest.kt`

## CP04 verification

- `git diff --check`: **PASS**.
- Control-plane admission: **PASS** on the CP04 source.
- First exact-source workflow `42018883250`: **FAIL** on a newly added assertion that assumed a fixed Crop/Fit pixel delta. The resolver and compilation passed; the assertion was changed to compare each result directly with the corresponding `RecognitionImageTransform` mapping.
- Rerun `42019562279` on `84c2bf30d51d38c3720f4124315ec799de601bd1`: **PASS**. Unit tests, Debug APK build, AndroidTest APK build, and packaged resource verification all passed.
- Workflow artifact ID `13411636116`, artifact ZIP size `143,349,184` bytes, artifact ZIP SHA-256 `88f63f470f1fb65693da1d15494250e934a6cf5b6081bdae334013457f84d452`. This is an intermediate CP04 build artifact, not the final V1.2 APK.
- Real-device visual checks (AGSL, Orb Wake, fish gold, Glyph, Result handoff): **NOT_RUN**.
- Full PR CI remains separate from this feature workflow; the known branch-state promotion gate and unrelated Empty Home V2 contract failure are not marked PASS here.

## CP05 root cause and implementation

The accepted SlimSAM Mask was already converted to canonical whole-photo coordinates, but `RecognitionContourPathCache.reveal()` started at the path extractor's first vertex. That vertex is not the resolved fish-tail contact, so the contour could begin at an arbitrary body point. The previous Core, Soft, and Halo layers also shared one intensity envelope and did not have independent reveal timing.

CP05 reuses the current Mask-derived caudal-peduncle anchor from CP04, maps it through the same current `RecognitionImageTransform`, and declines contour drawing if the visible tail contact is unavailable. The primary closed contour now grows in two wrapped directions from that point and meets at the opposite end. Secondary Mask islands wait until C. The existing A/B/C stage progress drives the path on the one SceneClock. Core, Soft, and Halo retain separate alpha envelopes and separate width/blur draws. The real Mask extractor remains the source of the path and continues its geometry-safe smoothing/simplification; no synthetic contour or BBox outline is introduced.

Changed CP05 files:

- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishTailAnchor.kt`
- `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionFishFocusTest.kt`

## CP05 verification

- Added unit coverage for tail-origin bidirectional contour progression, wraparound and convergence; independent Core/Soft/Halo envelopes; and the ORB_ONLY no-contour path. Existing tests cover EXIF-normalized Mask tail resolution, Crop/Fit mapping, ambiguous-mask rejection, identity scoping, and contour extraction.
- First exact-source Orb workflow `42022698826` on `479da6bec14a4e053a71c33bacf6eb95503de475` failed Kotlin compilation at `RecognitionFishTailAnchor.kt:132:5` (`Missing return statement`). The failure and source SHA were recorded using `failure_gate.py`. The isolated repair added the required `return` and was committed as `ead45796f8332ef673fd615402c72b995fb5f09d`.
- Exact-source Orb workflow `42023472919` on `ead45796f8332ef673fd615402c72b995fb5f09d`: **PASS**. Full `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, packaged Core/capture asset verification, and artifact upload passed.
- Artifact ID `13412648411`; ZIP size `143,353,050` bytes; ZIP SHA-256 `95ddc762487169e3a6ce6b059f37a65304e54b20485233e2f087fdb0a4a0348a`. This is an intermediate CP05 artifact, not the final V1.2 APK and not an APK checksum.
- `git diff --check`: **PASS**. Local Gradle was unavailable; the exact-source GitHub workflow ran the Android tasks above.
- Full PR CI is **FAIL/BLOCKED**, separate from the passing feature workflow: Android CI `42023480004` stopped at the existing `EMPTY_HOME_RUNTIME_V2` contract; control-plane admission `42023480030` rejected branch-local state files during canonical-promotion validation. The unrelated contracts were left unchanged and neither check is marked PASS.
- Device visual checks (AGSL, Orb Wake, fish gold, Glyph, Result handoff): **NOT_RUN**.

## Current recovery point

- Current CP: **CP06 READY**.
- Latest validated source: `ead45796f8332ef673fd615402c72b995fb5f09d`.
- APK state: CP04 and CP05 intermediate Debug and AndroidTest APKs built; final V1.2 APK **NOT_BUILT**.
- CP05 failure ledger: the missing-return compile failure on `479da6bec14a4e053a71c33bacf6eb95503de475` is closed with fix evidence on `ead45796f8332ef673fd615402c72b995fb5f09d`; closure is not a replacement for the passing exact-source workflow gate.
- CP06 next action: complete Orb Return and the real classifier-name Glyph transition as one identity, with six observable substeps, then add the phase/identity tests and exact-source verification.
- CP07 and final end-to-end tests remain **NOT_STARTED**.
