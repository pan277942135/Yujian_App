# YUJIAN_EMPTY_HOME_V2_2_FROZEN_COMPOSITION_REPAIR_V1

## Authority and scope

This narrowly scoped repair restores the Empty Home runtime to the existing
measured V2.2 anchors and compositing authority. It must not change the frozen
V2.2 product contracts, assets, design measurements, motion schedule, or
resource pins. It must not promote the W5 candidate geometry, whose status
remains review-only.

The exact Android app file set is:

- `app/src/main/java/com/yujian/ai/ui/home/EmptyHomeMotion.kt`
- `app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt`

The repair must use the current `anchor_contract.json` and `layer_contract.json`
as the only runtime geometry and compositing source, retain one bobber body with
the distinct approved faded reflection asset, and continue to pass the existing
Empty Home runtime verifier. No additional Android app paths are authorized by
this task.
