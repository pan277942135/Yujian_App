# Changelog｜YuJian AI V2 Six Core Contracts

## V1.0.0 — 2026-11-24

- 首次形成六大核心合同统一规范集。
- 统一 KF 编号为 KF00–KF25。
- 将 HOME/IDENTIFY 定义为 13 Stage 前置入口；6000ms 从 PHOTO_STABLE 起算。
- 明确 ModelClock、SceneClock、MotionClock 三时钟职责。
- 将 13.1–13.5 固定为 Stage13 子阶段。
- 区分 RecognitionFishPathSnapshot 与 RecognitionResultSnapshot。
- 统一 PhotoIdentity 的原始文件 SHA 与规范像素 SHA。
- 明确 MaskIdentity 不得依赖 Bitmap generationId。
- 将 GlyphPathProvider 从 Result Screen 生命周期中解耦。
- 明确五条 Resident 与 Task Energy 独立。
- 明确 Native 取消请求不等于 Native Call 已返回。
- 明确 ORT Operator Profile 不等于 Native Allocation 归因。
- 明确 Evidence 只读、PixelCopy/Video/ZIP 独立验证。
- 明确本次冻结不代表源码、设备或视觉验收通过。


## Repository integration — 2026-11-24

- Imported the six V1.0.0 core contracts and their cross-contract review from the verified frozen archive into `docs/contracts/yujian-ai-v2/`.
- Preserved the six contract bodies from the archive; only trailing-space Markdown hard breaks were normalized to explicit backslash hard breaks so `git diff --check` is clean.
- Added `CONTRACT_SOURCE_INVENTORY.md`, `CONTRACT_FREEZE_REPORT.md`, and `validate_contract_package.py` for repository provenance and repeatable static validation.
- Regenerated `CONTRACT_MANIFEST.json` and `SHA256SUMS` to cover the complete repository directory. The source archive SHA-256 is recorded in the freeze report.
- Existing design/runtime/evidence documents remain in place; their V2 core-contract status is classified in the source inventory. No production implementation was changed.
