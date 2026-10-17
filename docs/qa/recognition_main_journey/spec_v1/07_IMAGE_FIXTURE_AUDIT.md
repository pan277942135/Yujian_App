# 07 · 标准图片与首用指引资源审计

## 审计边界与方法

- 只读扫描本仓库中的栅格文件，并对列明的候选文件计算 SHA-256、读取尺寸、格式和 EXIF Orientation；未重新编码、覆盖、下载或生成任何图片。
- 在本工作区找到 256 个栅格文件（扩展名：PNG/JPG/JPEG/WEBP/GIF/BMP/TIF/TIFF）。没有配置或提供仓库外的授权素材目录，因此统计范围不包含未知的个人/组织资产库。
- `image_sha256` 是当前路径中精确文件字节的 SHA-256；它既不证明作者/来源，也不证明授权，且不等于 Git Blob SHA。登记为候选的文件一律保留 `source_sha256: null`，直至来源和授权审核完成。
- 唯一机器可读标准输入清单为 `../test_inputs_v1.json`。此审计文档与 JSON 不另建平行 fixture 清单。

## IMG-01–IMG-08 标准矩阵

| ID | 所需图像场景 | 候选路径 | 当前文件 SHA-256 / 维度 / EXIF | 来源授权 | 预期质量 / Pipeline / Result | 用例绑定 | 状态与缺口 |
|---|---|---|---|---|---|---|---|
| IMG-01 | 清晰、横向、全鱼体、自然全尺寸；无水印、无可识别旁人 | `app/src/androidTest/assets/recognition_real_catch_fixture.jpg`；相同字节副本见下方 | `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef`；1152×1536；无 Orientation 标签 | 未验证；图中有可识别人物及平台水印 | 仅观察真实 Gate / pipeline / 模型输出；不得预设 HIGH 或物种 | CAP-05, PROC-04, E2E 仅按机器清单 | `CANDIDATE_REVIEW`；实际鱼体纵向、人物入镜，不满足横向标准且无授权证据 |
| IMG-02 | 完整竖向鱼体、头尾均入框 | 无 | — | 无候选 | 实际质量与模型分支，无预置物种/置信度 | CAP-06, RES-07 | `MISSING`；缺少授权全尺寸原片 |
| IMG-03 | 鱼体部分出框，标注被裁边界 | 无 | — | 无候选 | 实测检测框、Crop 和模型行为；不得用输入标签强制 NO_FISH/INVALID | CAP-06 | `MISSING`；无具有场景说明和授权的图像 |
| IMG-04 | 低光夜景、复杂背景、记录光照/闪光条件、全尺寸原图 | `app/src/androidTest/assets/detector_case_b_night_flash.jpg` | `a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b`；1152×1536；无 Orientation 标签 | 未验证；图中有平台水印/标识 | 仅观察实际 Gate / pipeline / 模型结果，不从夜景标签推断 INVALID | CAP-07 | `CANDIDATE_REVIEW`；没有来源、授权和完整拍摄元数据 |
| IMG-05 | 自然真实无鱼场景，非空背景，可独立复核场景标签 | 无 | — | 无候选 | 真实 Detector/quality gate；只在实际输出 NO_FISH 时满足目标，不得替换模型响应 | PROC-08, E2E-06 | `MISSING`；无授权素材与模型实际行为核验 |
| IMG-06 | 有客观模糊/抖动记录的全尺寸图；最好有同场景清晰对照 | 无 | — | 无候选 | 当前代码合同未发现独立 blur 指标/阈值；真实输出观察，不得因人工称“模糊”而预期 INVALID | E2E-07 | `MISSING` + `REVIEW_REQUIRED`；需先明确模糊标注和当前模型/Gate 可支持的目标 |
| IMG-07 | 保留原始 EXIF Orientation 6 或 8 的 JPEG；冻结解码前原像素尺寸和纠正后参考方向 | 无 | — | 无候选 | 断言图像归一化后的朝向/几何；Result 由真实模型决定 | RES-07 | `MISSING`；无保留 EXIF 的获批原图 |
| IMG-08 | 固定不可解码字节、长度、类型/扩展名及预期解码错误类别；不是照片 | 无（本阶段不造字节） | — | 尚无素材；未来可在获批测试阶段制定最小合成数据 | 解码失败必须在 Detector 前；进入对应技术恢复，不伪装 NO_FISH | CAP-08 | `SPEC_ONLY`；本阶段只定义数据合同，未生成输入 |

完整的逐字段 `candidate_asset_path`、`source_type`、`source_authorization`、`dimensions`、`exif_orientation`、`expected_quality_branch`、`expected_pipeline_behavior`、`expected_result_branch`、`model_version_dependency`、`test_case_bindings`、`asset_status`、`missing_reason` 和 `remediation_plan` 见 `../test_inputs_v1.json` 的对应 IMG 记录。

## 仓库内其他识别相关候选

| 文件 | 当前字节 SHA-256 | 尺寸 | 观察 / 适用边界 |
|---|---|---:|---|
| `app/src/androidTest/assets/recognition_real_catch_fixture.jpg` | `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef` | 1152×1536 | 与下面两路径字节完全相同；识别相关候选，但含人物/平台水印、来源授权未知 |
| `app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg` | 同上 | 1152×1536 | 是同一原始文件的第二路径，不计作第二张独立照片；Home 资产不可自动升格为测试输入 |
| `design/system/fish_media_display_v1/assets/real_catch_source.jpg` | 同上 | 1152×1536 | 同一文件第三路径；设计素材归档不构成来源授权 |
| `app/src/main/assets/home_normal/fish_record/sample_recent_catch_card_crop.jpg` | `ed6c946b456c3f98cac7c132a2661b813073e5702b294e1d66fa34c041b09838` | 900×1125 | 派生裁剪图，非独立原始来源；不用于全图相机/裁剪回归 |
| `app/src/androidTest/assets/detector_case_b_night_flash.jpg` | `a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b` | 1152×1536 | Case B/夜拍候选；有水印且来源未知。Case A 原始文件在仓库扫描中未发现 |
| `app/src/androidTest/assets/golden_yellow_catfish_224.jpg` | `df7edde6123bedddc1ca637232e3c4cc9cb42bc522b898e46e863eac0274ec62` | 224×224 | 分类模型 Golden / 预处理回归样本，不是 CameraX 或全分辨率用户照片；不得用作真实拍照证据 |

当前对唯一相同照片内容的三条路径只计 **1 个照片内容**；加夜拍图为 2 个独立候选照片内容，但两者均未授权。224×224 Golden 是模型用途独立样本，不是用户场景 fixture。仓库没有可追溯的 Case A 原图。

## P01/P02/P03 Frozen 设计页

| 页 | Frozen 整页参考 PNG | 尺寸 | SHA-256 | 资源解释 |
|---|---|---:|---|---|
| P01 | `design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png` | 941×1671 | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` | 整页高保真参考图；不是可单独运行时展示的 Hero 原图 |
| P02 | `design/pages/recognition/first_use_camera_guide/frozen/P02_First_Use_Camera_Guide_Frozen.png` | 941×1671 | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` | 同上 |
| P03 | `design/pages/recognition/first_use_camera_guide/frozen/P03_First_Use_Camera_Guide_Frozen.png` | 941×1672 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | 同上 |

全页 PNG 只用于 Frozen 视觉对照。当前盘点没有发现可证实为 P01/P02/P03 运行时 Hero 的独立、原始照片，也没有可分别引用的错误示例图片。它们不能被登记为 IMG-01…08 标准照片输入。

## 通过来源/版本冻结的条件

1. 记录原始来源 URL/档案 ID、权利人、授权范围、是否可在仓库/CI 留存，以及涉及人物时的可验证肖像授权；先完成隐私与版权审核。
2. 原始文件按原字节保存并算 SHA-256；另记录 Git Blob SHA、但两个字段不得互换。记录 MIME、字节数、宽高、EXIF、色彩配置与采集条件。
3. 有独立审核者确认场景标签，不用模型输出反向证明图片标签。目标 Result 由真实模型实测；不满足目标结果就保留未满足/blocked。
4. 固定输入版本，保留原始文件；衍生 Crop 需引用父级原图哈希和裁剪变换，不覆盖来源文件。
5. 将通过授权的源哈希、测试用户 Dataset 版本、APK/模型 SHA 与运行证据绑定；设计状态与执行状态保持分离。只有审核批准后才将 `status` 更新到 `APPROVED`。
6. 本规范阶段不补齐图片、不移除候选图水印、不重新编码、不写入任何新图片。


## Runtime asset delivery contract addendum · 2026-10-17

The page-level Frozen PNGs above remain visual references only. The Android runtime demonstration images are a separate asset class and must not be derived by cropping those full-page screenshots.

The normative delivery contract is now:
- `docs/qa/recognition_main_journey/spec_v1/15_ANDROID_VISUAL_ASSET_DELIVERY_CONTRACT.md`
- `docs/qa/recognition_main_journey/spec_v1/16_MISSING_ASSET_FAIL_CLOSED_GATE.md`
- `design/pages/recognition/first_use_camera_guide/runtime_asset_manifest_v1.schema.json`
- `design/pages/recognition/first_use_camera_guide/runtime_asset_manifest_v1.json`

The runtime manifest defines 12 required slots: one correct Hero and three error examples for each of P01/P02/P03. Its initial status is `BLOCKED_ASSET_SOURCE`; all 12 slots are currently `MISSING`, with no approved source path, source hash, Android resource binding, or semantic review. This is an honest inventory state, not a claim that assets have been delivered.

The source audit remains unchanged: existing photo candidates have unresolved provenance/authorization and cannot be promoted merely because they are in the repository. Asset completeness must be decided per slot and then by the fail-closed G03 sub-gates.