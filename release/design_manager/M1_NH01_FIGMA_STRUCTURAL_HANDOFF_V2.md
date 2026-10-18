# M1 NH01 Figma Structural Handoff V2

Status: REVIEW_REQUIRED

## Baseline and delivery

- Repository: pan277942135/Yujian_App
- Canonical baseline/main SHA: 0d3238e8a41b87363e9a17b04c9cc45fca4c426d
- Branch: design/m1-nh01-figma-structural-handoff-v2 (created from the canonical baseline; no force push)
- Figma file: https://www.figma.com/design/VjN7iFviVN9aKeLv8kJivM/Recognition-Main-Journey-Master-Board-V1?node-id=41-30
- New page: NH01 / NORMAL HOME / STRUCTURAL HANDOFF (page node 25:2)
- Main frame: NH01_Normal_Home_1080x1920 (node 41:30), 1080 × 1920
- Responsive verification frame: NH01_LongScreen_Verification_1080x2340 (node 41:91); validation-only, not a new Frozen authority
- Master Board V1 page 0:1 was inspected before and after. It remains present and unchanged; post-write page child count is 1 and prior node-count audit remained 28 FRAME / 122 TEXT / 19 RECTANGLE / 14 LINE.
- Figma write preflight succeeded. A native test node was created on the new structural page; page remains separate from Master Board.

## Structural audit

| Check | Result | Evidence |
|---|---|---|
| Main frame size | PASS | Node 41:30 = 1080 × 1920 |
| Required six groups | PASS | 41:31, 41:33, 41:37, 41:49, 41:56, 41:85 |
| Native editable text | PASS (structure only) | 18 TEXT nodes; text is not rasterized |
| Reusable components and instances | PASS (structure only) | Statistic component 38:3; Hero component set 38:24; capture button 38:25; capture action group 38:28; instances on the page |
| Statistics Auto Layout | PASS | Horizontal layout, three statistic instances and two dividers |
| Hero selection geometry | PASS | Selected instance 41:67 = 740 × 880; 32 px corner radius; pager clips three instances |
| Main frame not a screenshot or PNG collage | PASS | It contains native frames, text, instances, vector, and slots; no full-page raster child |
| Source image fills | BLOCKED | Image fill count is 0; original PNG bytes could not be carried from GitHub binary fetch into Figma upload in the available connector path |
| Independent QA | NOT_RUN | Structural self-audit was performed; a separate QA sign-off is still required |

The page contains a component-source shelf at node 38:2 outside the exported frame. Exporting node 41:30 excludes that shelf.

## Native structure and Android mapping

| Figma node | Type / component | Contract responsibility | Android Compose mapping |
|---|---|---|---|
| 41:30 | FRAME, 1080 × 1920 | Frozen NH01 reference canvas | NormalHomeContent in app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt |
| 41:31 | FRAME, Environment | Environment/background binding | NormalHomeContent root/background layer |
| 41:32 | FRAME/SLOT, empty | Morning Lake background source slot | NormalHomeContent runtime background binding |
| 41:33 | FRAME, Header | Header placement, brand and avatar anchors | NormalHomeHeader in NormalHomeContent.kt |
| 41:34 | FRAME/SLOT, empty | Frozen brand artwork slot | NormalHomeHeader brand artwork |
| 41:35 | FRAME/SLOT, empty | Avatar image slot | NormalHomeDefaultAvatar in NormalHomeContent.kt |
| 41:36 | FRAME | Header avatar interaction target | NormalHomeHeader profile click target |
| 41:37 | FRAME, Statistics | Statistics geometry and hierarchy | HomeStats in app/src/main/java/com/yujian/ai/ui/home/HomeStats.kt |
| 41:38, 41:42, 41:46 | INSTANCE of 38:3 | Value/label statistic pair | HomeStats statistic items |
| 41:41, 41:45 | RECTANGLE | Statistic dividers | HomeStats separators |
| 41:49 | FRAME, RecentCatchHeader | Header / See All action | RecentCatchSectionHeader in NormalHomeContent.kt |
| 41:50, 41:52 | TEXT | Editable section title and See All label | RecentCatchSectionHeader Text nodes |
| 41:53–41:55 | GROUP / VECTOR | Registered chevron SVG | Existing registered icon vector; Android drawable mapping remains REVIEW_REQUIRED |
| 41:56–41:57 | FRAME, clipped pager | Catch card viewport and clipping | RecentCatchPager in NormalHomeContent.kt |
| 41:58, 41:67, 41:76 | INSTANCE of 38:24 | Neighbor / selected / neighbor variants | RecentFishCard.kt → YuJianHeroCard(variant=HOME) |
| 41:85–41:86 | FRAME / INSTANCE of 38:28 | CTA and capture button group | CTA Text in NormalHomeContent.kt plus HomeCameraButton.kt → YuJianCaptureButton.kt |
| 38:3 | COMPONENT | Reusable statistic value + label | HomeStats |
| 38:24 | COMPONENT_SET | YuJianCatchHeroCard / HOME, State=Selected/Neighbor | YuJianHeroCard in ui/designsystem/components/YuJianHeroCard.kt |
| 38:25 | COMPONENT | YuJianCaptureButton / NORMAL HOME | YuJianCaptureButton in ui/designsystem/components/YuJianCaptureButton.kt |
| 38:28 | COMPONENT | YuJianCaptureActionGroup / NORMAL HOME | NormalHomeContent CTA plus HomeCameraButton |

## Frozen authority and source asset register

Frozen page reference: design/system/core_visual_v1/reference/normal_home_v1.png — 1080 × 1920 — SHA-256 6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377. It was not imported as a child or modified.

| Asset | GitHub source / hash | Figma state |
|---|---|---|
| Morning Lake background | design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png — SHA-256 5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7 | SLOT 41:32 empty; BLOCKED_ASSET_IMPORT |
| Brand title | design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png — SHA-256 2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc | SLOT 41:34 empty; BLOCKED_ASSET_IMPORT |
| Avatar master | design/pages/home/normal_home/07_avatar_states/assets/nh07_default_avatar_master_1254.png — SHA-256 12ec5fc8c1a08723ea461edf327870df19b7dfe309587b18b6de28c9f701b643; runtime 3x export SHA-256 b79dc7eaa6da78722cedb240b35985871c422531b3d452efaa5fba9aa3eeb7c2 | SLOT 41:35 empty; BLOCKED_ASSET_IMPORT |
| Normal Home camera artwork | design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png — SHA-256 fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2; shared master SHA-256 501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327 | Camera component source SLOT 38:27 empty; BLOCKED_ASSET_IMPORT |
| Hero photo fixture | design/pages/home/normal_home/engineering/hero_real_photo_fixture_register_v1.json | F03/F08 rights and provenance NOT_VERIFIED; BLOCKED_ASSET_SOURCE; no fish photo placed |
| Hero shadow / gradient / outline | app/src/main/assets/normal_home_runtime_v1/fish_card/fish_card_shadow.png (blob 2a782f76a7880cf957b71ec718a0d260099e5f65); fish_card_gradient.png (blob b17776098b76ecc6ce7488fef70c6ad319d58a21); fish_card_outline.png (blob 6ca9464b44b5aadda2819bda74f1dc3244055815) | Runtime manifest has blob IDs only; no SHA-256 or Asset Registry row; BLOCKED_ASSET_HASH_AND_IMPORT |
| Chevron | design/system/icon_library_v0_1/assets/ic_chevron_right_v1.svg — registry SHA-256 3d8c0bf43500c40b636a08f50d6fedf4d5f0ae2fd984a7e6c16cd03f4130d4a9; blob de1fe83bfed413ddac8895d9cf02f3d450908dde | Imported as editable vector nodes 41:53–41:55; registry says FROZEN while SVG comment says candidate/not frozen: REVIEW_REQUIRED |

Binary import limitation: the GitHub file connector returns empty data for base64 PNG content and rejects binary blobs as non-UTF-8; the Figma upload operation requires raw bytes posted to a single-use URL. The available connector path did not provide a safe raw-byte transfer, so no image is represented as imported. This is a real blocker, not an asset-source or permission approval.

## Tokens, typography, and component bindings

- Local Figma collection: YuJian Design System V3.1 / NH01; collection VariableCollectionId:37:26; mode NH01 Default; 21 variables. Color aliases read back on text/shape nodes.
- Bound semantic colors include deep_lake_blue #0B2D4B, lake_white #F7FAFB, and mist_blue_gray #748897. Alpha variables were added for statistics labels, dividers, Hero measurement/metadata, recent title shadow, and CTA shadow.
- Eight local text styles were created from the V3.1 / current Compose typography references: statistics value 36/42, statistic label 28/32, recent title 48/52, action 40/44, Hero title 56/64, measurement 46/52, metadata 36/42, and CTA 36/40 px.
- CJK Figma face used in the structural page is Noto Sans SC Regular/Medium as a candidate. Android uses platform SansSerif with CJK system fallback and does not pin the same vendor font. Exact font/glyph equivalence: REVIEW_REQUIRED.
- Component-property definitions retain editable Value/Label statistic fields, selected/neighbor Hero fields and visibility toggles, and capture CTA/Artwork properties. Main-frame dynamic sample fields are blank to prevent placeholder tokens from appearing as sample content.

Text style node IDs:

| Style | Figma style ID |
|---|---|
| Statistics Value | S:24a2ed7d762d4841a897e9166e7197edcbd21230 |
| Statistics Label | S:d6db0e1d5f22b909ff6ad77abb498f188e88a88e |
| Recent Title | S:5138d80f719c1de2da1aa99a04090c5402eb93dc |
| Recent Action | S:2bba567766db56d42c7953f3936e3e781b148556 |
| Hero Title | S:965db469a3d33ea459e81e691859c7b0add96b14 |
| Measurement | S:5cf5980f31da35e919f858541b527ff06ebf04ee |
| Metadata | S:8020e72ee3a19160af7100d32040c0993c22ddb0 |
| CTA | S:09fcf1ffd6c4b9066fe638f8eec556de92f3435a |

## Geometry and authority conflicts

- Frozen/current contract anchors used to structure the page: header x88 y104 w904 h104; brand width 155 and height 80; avatar 92×92; stats y304, row h116 and content x150 w780; recent header x108 y494 w864 h70; selected Hero x170 y596 w740 h880, radius 32; CTA x330 y1495 w420 h58; footer inset left 56/bottom 32/gap 20; camera artwork 208 and touch box 216.
- Responsive verification frame uses the existing formula, not a new authority: Wsafe=max(0,W−L−R), Hsafe=max(0,H−T−B), S=Wsafe/1080, E=max(0,Hsafe−1920×S), delta=min(0.36×E,180×S), x=L+xFrozen×S, y=yFrozen×S+delta. At 1080×2340 with zero insets, delta=151.2 px; the main content groups shift by 151.2 px.
- CTA typography conflict: Compose NormalHomeTypography.kt is 36/40 px; Layout_Responsive_Contract_V1.md table states 40/44 px. REVIEW_REQUIRED; no frozen parameter was changed.
- Camera vertical anchor conflict: NormalHomeContent.kt uses cameraY=1583 px; Layout_Responsive_Contract_V1.md table states 1564 px. REVIEW_REQUIRED; no frozen parameter was changed.
- Chevron status conflict: Icon Registry says FROZEN; the source SVG comment calls it a candidate/not frozen. REVIEW_REQUIRED.
- Font binding conflict: Figma candidate Noto Sans SC vs Android system fallback. REVIEW_REQUIRED.

## Frozen comparison and evidence

- Figma export node: 41:30; 1080 × 1920 PNG; SHA-256 e068790742826d8328ea4c704eac1cbc8e76767191083bd9a3ed41f5208e761e; committed as a structure-only preview.
- Preview shows a transparent/empty canvas with only text and divider geometry visible; brand, background, avatar, camera artwork, and Hero photos are missing. The filename explicitly says STRUCTURE_ONLY_NOT_VISUAL_PASS.
- Geometry: PARTIAL_PASS at node frame/selected-card contract dimensions. The unavailable original fills prevent region-level geometry attribution.
- Glyph, ROI Pixel Diff, color, crop, and motion comparison: BLOCKED / NOT_RUN. No project threshold was relaxed and no pixel-diff PASS is claimed.
- Runtime motion, Android screenshots, Android boundsInWindow, Pixel Diff against Android, and Android Runtime: NOT_RUN in this Figma-only handoff.
- Frozen visual authority SHA is recorded above; design contracts consulted:
- design/pages/home/normal_home/spec/Visual_Spec_V1.md
- design/pages/home/normal_home/engineering/README.md
- design/pages/home/normal_home/engineering/Layout_Responsive_Contract_V1.md
- design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Component_Anchor_Map_V1.json
- design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Pixel_Targets_V1.json
- design/system_v3/09_Android_Implementation_Contracts/Normal_Home/Normal_Home_Resource_Binding_V1.json
- design/pages/home/normal_home/motion/Normal_Home_Motion_Spec_V1.md
- design/pages/home/normal_home/engineering/Normal_Home_Hero_EvidenceFit_Contract_V1.md

## Required closure items

| ID | Priority | Gap | State | Acceptance to close |
|---|---|---|---|---|
| NH01-A01 | P0 | Frozen source raster assets not imported into actual Figma image fills | BLOCKED_ASSET_IMPORT | Transfer canonical source bytes through a supported authorized route; verify source SHA and target node fill readback |
| NH01-A02 | P0 | No authorized real Hero photo fixture available for use | BLOCKED_ASSET_SOURCE | Approve a fixture with provenance and usage rights, or keep dynamic photo behavior outside static visual diff |
| NH01-A03 | P0 | No Frozen-vs-Figma ROI/glyph/pixel comparison; independent QA not run | NOT_RUN | Re-export after asset import, run existing acceptance thresholds, attach independent QA result |
| NH01-A04 | P1 | CTA size and camera Y differ between layout contract and Compose source | REVIEW_REQUIRED | Contract owner reconciles the existing authority conflict and records approved values; then update Figma/Compose consistently |
| NH01-A05 | P1 | Figma/Android CJK font equivalence is unpinned | REVIEW_REQUIRED | Confirm approved CJK font or explicitly accept platform fallback with glyph evidence |
| NH01-A06 | P1 | Chevron FROZEN registry conflicts with SVG candidate comment and current Android drawable mapping | REVIEW_REQUIRED | Confirm effective asset state and Android symbol; update registry/source comment only via approved change |

## Gate result

NH01_FIGMA_STRUCTURAL_HANDOFF = REVIEW_REQUIRED

Structural Figma content exists and is editable. It is not high-fidelity visual PASS and not Android Runtime PASS because the original image assets were not placed, the photo source is not approved, visual diff is blocked, and independent QA has not signed off. Master Board was preserved. No Frozen image or main branch was modified.