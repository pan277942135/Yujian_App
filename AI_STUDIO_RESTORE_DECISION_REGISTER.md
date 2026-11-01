# AI Studio Restore Decision Register

All code and source-backed changes that do not depend on these decisions continue independently. Decision state does not mark any unapproved asset as approved.

## D01 — Bobber tilt/rotation

- **Page:** Empty Home, W5 candidate Fishing Composition.
- **ZIP behavior:** periodic dip `4 * exp(-elapsed*3.6) * cos(elapsed*15)` for 1.4s, plus tilt `2 * exp(-elapsed*3.4) * sin(elapsed*13)` degrees and a `-0.4×` optical reflection counter-tilt.
- **Current Main contract:** measured canonical anchors are frozen; Main had a continuous idle bobber offset. The task allows event-driven Y motion but requires explicit approval before adding ZIP tilt/rotation.
- **Implemented candidate:** W5 center `(531,1161)`, contact `(531,1195)`, deterministic Y-only event motion, no rotation. This remains `USER_DIRECTED_CANDIDATE_DELTA_NOT_FROZEN`.
- **Options:** (A) Y-only event dip, preserving the light line/float silhouette; (B) add the ZIP tilt and reflection counter-tilt during each event.
- **Impact:** B changes the bobber silhouette and contact composition and requires updated geometry/runtime comparison. No business or capture behavior changes.
- **Work recommendation:** A until explicit approval for B.
- **Question:** Should the bobber stay Y-only, or should the ZIP tilt/rotation be enabled?
- **Status:** BLOCKED for the rotation choice; deterministic Y-only motion continues.

## D02 — Eight First Capture Guide image candidates

- **Page:** First Capture Guide P01/P02.
- **ZIP behavior:** adds two background JPEGs and six fish-example PNGs. They are visually compared with the exact frozen P01/P02/P03 screenshots. Frozen screenshot bytes from ZIP match Main's file blobs exactly.
- **Source/license evidence:** No source URL, creator, license, or usage permission appears in the ZIP. The two JPEGs are 941×1672 RGB with an ICC profile and no source location metadata. Six PNGs are RGB, no alpha, and contain no embedded metadata. Filenames call them candidates; provenance remains unverified.
- **Options:** (A) leave all eight blocked/out of Runtime; (B) approve only after supplying source, usage rights, and per-image page-role confirmation.
- **Impact:** approval affects P01/P02 examples/backgrounds only; P03 remains the existing landscape page and is not mapped to these images.
- **Work recommendation:** A until source and rights are confirmed. Current code keeps `ASSET_SOURCE_BLOCKED`.
- **Question:** Do you own or have permission to use each of the eight candidates, and do you approve their displayed P01/P02 role?
- **Status:** BLOCKED pending D02; contact sheet is provided separately and is not committed to Runtime/repository.

### Candidate inventory

| ZIP path | SHA-256 | Dimensions | Alpha/metadata | Current state |
|---|---|---|---|---|
| `app/src/main/res/drawable-nodpi/first_use_guide_bg_p01.jpg` | `544bbf91245ea0bc1a47fd5744effcd25b93f70748de926af1dd41c01e6498cf` | 941×1672 | RGB, no alpha; ICC profile only | CANDIDATE_NEEDS_APPROVAL |
| `app/src/main/res/drawable-nodpi/first_use_guide_bg_p02.jpg` | `75c61cd08ed4a7bf9fd522cdbaff564928a00198ccd96f54d65aa9ef00d8d823` | 941×1672 | RGB, no alpha; ICC profile only | CANDIDATE_NEEDS_APPROVAL |
| `app/src/main/res/drawable-nodpi/first_use_guide_p01_head_out.png` | `c0491f107e19da5753b6b94f9dd980ca9dc290b5e3c710e0e9e805f3a3dcddda` | 1448×1086 | RGB, no alpha; no metadata | CANDIDATE_NEEDS_APPROVAL |
| `app/src/main/res/drawable-nodpi/first_use_guide_p01_occlusion.png` | `3d2d84ec2b2979d75ed62517f4899785b7c2641b96b3f9188b7fafd320d9bacc` | 1448×1086 | RGB, no alpha; no metadata | CANDIDATE_NEEDS_APPROVAL |
| `app/src/main/res/drawable-nodpi/first_use_guide_p01_too_small.png` | `0233634df7918b5dc9b1f1fc9a9db1c37addf28efa07c849cb5710c65a2e3a0d` | 1448×1086 | RGB, no alpha; no metadata | CANDIDATE_NEEDS_APPROVAL |
| `app/src/main/res/drawable-nodpi/first_use_guide_p02_head_out.png` | `ac202c85d6d2379e168521bc42994c5af82a6216f21700b71a5d07cf2dbc8b31` | 1086×1448 | RGB, no alpha; no metadata | CANDIDATE_NEEDS_APPROVAL |
| `app/src/main/res/drawable-nodpi/first_use_guide_p02_occlusion.png` | `d5efedc6ed1659135d25ac2205c0fc3505b0bd4100039060f51036454af0d2c3` | 1086×1448 | RGB, no alpha; no metadata | CANDIDATE_NEEDS_APPROVAL |
| `app/src/main/res/drawable-nodpi/first_use_guide_p02_too_small.png` | `4cb42997eb7b01ff480c0fb36db3075c1698203277755f034b0731646829730f` | 1086×1448 | RGB, no alpha; no metadata | CANDIDATE_NEEDS_APPROVAL |

## D03 — HIGH result versus Frozen authority

- **Page:** HIGH recognition result.
- **ZIP behavior:** dark result styling and top-bar changes; its source removes Main WaterLight and includes a no-op voice action.
- **Current Main contract:** `05_Result_High_Frozen.png` is the visual authority; PR #294 provides the current result-ready/reveal protection.
- **Options:** (A) retain Frozen light style and current Main/PR #294 behavior; (B) approve a new dark HIGH authority and replace the Frozen contract after actual render comparison.
- **Impact:** B changes a frozen page visual and requires owner approval, new frozen design evidence, and Runtime screenshots. It must not weaken real-result, photo handoff, save, Orb/WaterLight, or RR09 contracts.
- **Work recommendation:** A.
- **Question:** Keep the existing light Frozen HIGH visual, or approve a new dark authority?
- **Evidence state:** source diff reviewed; Frozen preview is available. ZIP candidate render and current Main actual render are NOT_RUN, so no screenshot-level parity claim is made.
- **Status:** BLOCKED pending D03.
