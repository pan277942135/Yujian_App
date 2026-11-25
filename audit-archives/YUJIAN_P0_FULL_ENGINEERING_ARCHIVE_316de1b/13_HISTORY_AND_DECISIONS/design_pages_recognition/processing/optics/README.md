# Orb × WaterLight Recognition Runtime Authorities

## Canonical frozen sources

| Authority | Path | Scope |
|---|---|---|
| 13-stage state machine V3 | `AI_ORB_WATERLIGHT_13_STAGE_STATE_MACHINE_V3.md` | Existing 13 semantic stages, event gates, shared SceneClock and identity binding |
| Unified lifecycle V3 | `AI_ORB_WATERLIGHT_UNIFIED_LIFECYCLE_V3.md` | Orb, WaterLight, real model branch, Mask trust, Glyph and Result lifecycle |
| Visual and Result handoff addendum V1 | `YUJIAN_RECOGNITION_VISUAL_CONTRACT_FREEZE_ADDENDUM_V1.md` | Frozen Stage 05–13 recent-contour contact, shared Mask geometry, visible Orb deconstruction, real Glyph, NAME_STABLE and ordered Result handoff |
| 21-frame matrix | `design/recognition_structured_handoff_v1/stage_matrix/recognition_21_frame_matrix.csv` and `.json` | Structured frame mapping copied byte-for-byte from PR #318; its source review status is retained |
| Component Runtime mapping | `design/recognition_structured_handoff_v1/component_mapping/component_runtime_mapping.md` | Component-to-Android mapping copied byte-for-byte from PR #318 |

The addendum is a supplemental frozen product decision. It preserves the existing 13 stages, 21-frame matrix, `RecognitionSceneAnimationController`, and single SceneClock. It does not change any frozen numeric visual parameter or settle the 3650ms / 6000ms timing dispute.

## Acceptance boundary

The contract can be frozen while Runtime remains unverified. Unit tests, compilation, APK packaging, device execution, and visual acceptance are separate evidence gates. Visual acceptance requires a real Android Runtime video and keyframes. No untrusted or missing Mask may be replaced by synthetic geometry, a BBox outline, or a fabricated species name.
