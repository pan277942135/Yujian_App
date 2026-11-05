# Recognition Runtime V1.2 execution checkpoint

**Task:** `YUJIAN_RECOGNITION_RUNTIME_13_STAGE_INTEGRATION_V1_2`
**Repository / PR:** `pan277942135/Yujian_App` / #317 (`Draft`, `Open`, `Unmerged`)
**Branch:** `feature/orb-photo-stable-visual-closure-v1`
**Main baseline:** `74cad95b4f673bd9c8c60d16839f036d5886d933`
**Current validated source:** `84c2bf30d51d38c3720f4124315ec799de601bd1`
**Current checkpoint:** CP04 complete; CP05 is next.

## CP01–CP04 status

| CP | Status | Source / evidence |
|---|---|---|
| CP01 PHOTO_STABLE | Implemented | `81a0c257417c0be050e8dd57ace95e97619babf4`; existing exact-source workflow passed. |
| CP02 ORB_WAKE | Implemented; device visual `NOT_RUN` | `ab2407ab42459f12ccb8178df7ebe3badc396c13`; Orb workflow `42010365270` passed. Core is independently drawn below optical effects. Debug A/B/C comparison states remain available. |
| CP03 ORB_RELEASE | Implemented; resource freeze identity `NOT_CONFIRMED`; device visual `NOT_RUN` | `4dc78515bb006f2952386d046db079eb2dce3162`; Orb workflow `42011287356` passed. The existing five R06 paths and one shared SceneClock remain in use. |
| CP04 PATH_TO_FISH | Implemented; mask-tail heuristic intentionally returns no anchor when ambiguous; device visual `NOT_RUN` | Code commit `9e8d99471c2db09c9e244d804a4cc8da3c5d3c2c`, assertion correction `84c2bf30d51d38c3720f4124315ec799de601bd1`; exact-head Orb workflow `42019562279` passed. |

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

## Current recovery point

- Current CP: **CP05 READY**.
- Latest validated source: `84c2bf30d51d38c3720f4124315ec799de601bd1`.
- APK state: CP04 intermediate Debug and AndroidTest APKs built; final V1.2 APK **NOT_BUILT**.
- CP05 next action: bind contour A to the current Mask-derived caudal contact, reveal both directions along the real closed contour through A/B/C, and give Core / Soft / Halo distinct reveal envelopes. Preserve real Mask trust and ensure ORB_ONLY draws no fish contour. Add stage and reveal tests, run the exact-head build workflow, commit, push, and update this checkpoint before CP06.
- CP06 / CP07 and final end-to-end tests remain **NOT_STARTED**.
