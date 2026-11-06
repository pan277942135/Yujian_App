# 参数注册表

`FROZEN` 表示合同中的数值权威，不表示同名仓库文件的二进制身份已匹配。`PROPOSED`、`PENDING_PRODUCT_REVIEW`、`RUNTIME_TUNABLE` 不可擅自升级为冻结。

| 参数 | 值 | 状态 | 来源 |
| --- | --- | --- | --- |
| S1_CORE | 唯一 Asset01；Core alpha 源保持 | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| S1_BREATH | ±0.8% / 6000ms | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| S1_R06 | hidden | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| S1_S2_WAKE | 0–350ms internal water/caustics/drops/grass | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| R06_ALPHA | 0–450ms | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| S2_BREATH | ±1.8% / 4800ms | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| R06_EXTEND | 350–450ms | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| R06_LOCAL_RETURN | 690–950ms / 260ms C1 | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| R06_PATH_IDS | G01,G02,A01,A02,C01 | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| R01B | six source-derived masks + JSON + SVG; scheme B | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| R06_RENDER_ORDER | G01/A01 BACK → Core → G02/A02/C01 FRONT | FROZEN | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |
| ORB_TO_FISH_CAUSALITY | AI WaterLight starts at Orb | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| PERIMETER_SCOPE | photo edge only, no whole-photo gold | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| SEEK | 1–2 broken gold filaments + varied translucent drops | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| CONTOUR | real trusted Mask, blue/gold alternation | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| FISH_GOLD | Core/Soft/Halo three layers | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| NOMINAL_PROCESSING_REFERENCE | 2800ms; not hard deadline | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| RR09_REFERENCE | R0 2800, R0.5 2900, R1 3000, R2 3180, R3 3350, R4 3480, R5 3650ms; not hard deadline | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| CLOCK_ORIGIN | first actual stable photo frame | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| DEBUG_STEP | MANUAL default, 3000ms per stage then pause; AUTO gated | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| WATCHDOG | 60s model timeout | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| MODEL_JOIN | Classifier true name + SlimSAM terminal; trusted Mask FULL else ORB_ONLY | FROZEN | User-approved recognition lifecycle contract; app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt |
| SUBFRAME_11_13_TIMING | 11:3,12:3,13:5 internal design substates inside existing 13 enum | PENDING_PRODUCT_REVIEW | V3/V3.1 issues and KF06–KF10 composition reference |
| GLYPH_MORPH_GEOMETRY | same Orb water to live Classifier name path | PENDING_PRODUCT_REVIEW | V3/V3.1 issues and KF06–KF10 composition reference |
| PHOTO_CARD_RESULT_GEOMETRY | KF06–KF10 composition, same confirmed photo | PENDING_PRODUCT_REVIEW | V3/V3.1 issues and KF06–KF10 composition reference |
| MASK_TRUST_THRESHOLD | must validate real fish boundary and same-attempt transform | PENDING_PRODUCT_REVIEW | V3/V3.1 issues and KF06–KF10 composition reference |
| RESULT_INFO_ENTRANCE_ALPHA | progressive alpha and translation | RUNTIME_TUNABLE | design handoff candidate |
| AMBIENT_CAUSTIC_STRENGTH | within V1.1 optical caps and tested device performance | RUNTIME_TUNABLE | design handoff candidate |
| R06_CURRENT_MAIN_BINARY_IDENTITY | repository R06 JSON/Profile/SVG differ from V1.1 hashes | PENDING_PRODUCT_REVIEW | Library libfile_a47a721a52588191a0df9f8d8a46a3bf / ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json |

## R06 源身份冲突

| 文件 | V1.1 SHA-256 | 当前 Main SHA-256 | 匹配 |
| --- | --- | --- | --- | --- |
| `design/system/components/ai_orb_core/assets/paths/R06_orb_filament_path_geometry.json` | `a37d947d32cdf6902cabe87be03458ab6d0dd236c4d033564a98f8a5a1a601a5` | `1dfc85e651440d88c18d80daf51bbaa081e058fa60061bed6c8801aededf4cdc` | False |
| `design/system/components/ai_orb_core/profiles/R06_orb_filament_profile.json` | `075e8390aa08638ba3baca5a12f9b92b73718dc87ec1175fe3325e4cc7cad864` | `8c7f998b043ddca8363054b75a93dbe6fab72cbdf31424b4fb71c4b502f21c10` | False |
| `design/system/components/ai_orb_core/assets/paths/R06_orb_filament_paths.svg` | `66966e0b9aaf1fe6cbb3930ae99d382a1d3a9d292ef8c3911a9e2ef45c4db52b` | `d321c2bdd709145fadf73f52fc3f5d81a93c7e4e9d1074dc9adf783810287389` | False |

## Orb L01–L07 图层

| 顺序 | 层 | 来源与 Alpha / Blend / 坐标 / 动态 |
| --- | --- | --- |
| L01 | 环境光晕 | procedural；S1=0、S2≤0.08；SCREEN；OrbLocal；柔和环境响应 |
| L03 BACK | G01/A01 | R06 五路径源；Profile Alpha；OrbLocal；共享 opticalReferenceElapsedMs |
| L06 | 唯一玻璃 Core | Asset01/Runtime WebP；源 Alpha 保持；OrbLocal；均匀呼吸 |
| L04 | 内部水体与焦散 | Core+WaterCausticsField；深流≤0.07、表层≤0.12；内部 Mask 裁切；共享时钟 |
| L05 | R01B 水草 | 四路径六 Mask；反射≤0.10；源 UV 局部位移，根部固定 |
| L02 | 三水滴 | R02 固定 UV；body≤0.23/0.26/0.19；内部水体裁切；确定性漂移 |
| L07 internal | 自然阳光 | 左上 UV(0.24,0.20)；淡金 #D8BC87；SCREEN/折射混合；不重复烘焙耀斑 |
| L03 FRONT | G02/A02/C01 | R06 Profile Alpha；OrbLocal；同路径释放回收 |
| L07 final | 玻璃高光 | 额外 glass specular≤0.11；球体 Alpha 裁切；固定入射方位 |

V1.1 的 S3_GLYPH 被明确排除；11–12 采用独立设计合同，形态细节仍待产品审视。03 的 690–950ms 局部回收与 10 鱼体后的全局回馈分别实现；两者共用 SceneClock，不共用同一事件语义。
