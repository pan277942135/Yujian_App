# 渔见 · 拍照识鱼主链路｜Master Board V1

Status: **PARTIAL · TWO-PAGE AUTHORITY PROPOSAL**

The board mapping is a main-first control proposal. Figma remains unchanged until canonical approval and Admission PASS. Canvas: 5120×2880; board frame: `6:2`.

## Primary flow (15 states)

| Board | PAGE / STATE | Source / status | Figma slot |
|---|---|---|---|
| MB-01 | HOME / EMPTY | design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png · FROZEN_SOURCE_AVAILABLE | 6:7 |
| MB-02 | IDENTIFY / CAMERA_PERMISSION | SYSTEM · SYSTEM | 6:14 |
| MB-03 | GUIDE / P01 | design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:21 |
| MB-04 | GUIDE / P02 · landscape phone + horizontal fish | design/pages/recognition/first_use_camera_guide/frozen/P02_Landscape_Horizontal_Fish_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:28 |
| MB-05 | IDENTIFY / CAPTURE_READY_PORTRAIT_HORIZONTAL | design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Portrait_Horizontal_Fish_Frozen.png · SOURCE_MISSING | 6:35 |
| MB-06 | IDENTIFY / CAPTURE_READY_LANDSCAPE_HORIZONTAL | design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:42 |
| MB-07 | RECOGNIZING / PROCESSING_IMAGE_RECOGNIZING_EARLY | design/pages/recognition/design/01_Capture_Transition_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:49 |
| MB-08 | RECOGNIZING / PROCESSING_IMAGE_RECOGNIZING_LATE | design/pages/recognition/design/02_AI_Understanding_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:56 |
| MB-09 | RECOGNIZING / PROCESSING_FISH_LOCATED | design/pages/recognition/design/03_Fish_Highlight_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:63 |
| MB-10 | RECOGNIZING / PROCESSING_SPECIES_RECOGNIZING | design/pages/recognition/design/04_Fish_Identifying_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:70 |
| MB-11 | RESULT / RESULT_HIGH | design/pages/recognition/design/05_Result_High_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:77 |
| MB-12 | RESULT / RESULT_MEDIUM | design/pages/recognition/design/06_Result_Medium_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:84 |
| MB-13 | RESULT / RESULT_LOW | design/pages/recognition/design/07_Result_Low_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:91 |
| MB-14 | RESULT / NO_FISH | design/pages/recognition/design/08_Error_No_Fish_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:98 |
| MB-15 | RESULT / IMAGE_QUALITY | design/pages/recognition/design/09_Error_Image_Quality_Frozen.png · FROZEN_SOURCE_AVAILABLE | 6:105 |

Guide sequence: P01 portrait phone + horizontal fish → P02 landscape phone + horizontal fish. The former portrait-phone / vertical-fish P02 is archived and inactive; former P03 bytes are promoted unchanged to P02.

MB-05 source: `a_realistic_high_resolution_vertical_smartphone_c.png`, expected SHA-256 `6d67d03045f5f3dfb66adad10921d7456a2c82620b7b68f156cb98be74f1908c`, status **SOURCE_MISSING**. No replacement is generated. The board remains **PARTIAL** while this original is missing.

Recovery lane: 8 states, IDs MB-R01 through MB-R08, unchanged from the existing mapping. Frozen thumbnails are source previews only and never modify their original PNGs.

## Import and export boundary

Each image slot records the expected source SHA and Figma node. `actual_upload_sha256`, image fill confirmation, aspect/crop review, and final exports remain pending until Admission PASS. The 5120×2880 PNG and PDF are not yet exported.
