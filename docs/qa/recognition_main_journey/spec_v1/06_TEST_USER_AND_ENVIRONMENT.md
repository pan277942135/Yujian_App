# 06 · 固定测试用户与环境合同

## 固定 QA 用户

唯一测试身份为 `QA_RECOGNITION_001`（逻辑本地 Guest，不是真实账号、不连接生产账号）。权威 manifest：`app/src/androidTest/assets/qa/recognition/v1/test_users.json`，当前 dataset `1.1.0`。本阶段不修改它。

| 状态 | Guide 完成 | Host 初始 CAMERA | 初始页 | 基线数据 | 适用范围 |
|---|---:|---|---|---:|---|
| `FIRST_USE` | false | Host revoke/clean permission history | Empty Home | FishRecord=0 | 首次入口、首请求、首次指引 |
| `GUIDE_PENDING` | false | Host grant | Empty Home | FishRecord=0 | CameraX Ready 后出现教学 |
| `RETURNING` | true | Host grant | Empty Home | FishRecord=0 | 复访与重复识别 |
| `PERMISSION_DENIED` | false | Host revoke/denied | Empty Home | FishRecord=0 | 拒绝与恢复路径 |

每个 Case/E2E 只选一个 `initial_state`；不通过额外初始状态偷偷补测。测试 seed 只能改 manifest 明确允许的 app-private debug guest keys、guide completion 和 catch fixture store；不得写权限状态、真实账号、生产数据、模型或 Frozen 资产。Host 独立设置并观察 Android 权限状态。seed receipt 绑定用户 ID、state、manifest SHA、source SHA、APK SHA 和 run ID；seed 不是 API28/CameraX/模型证据。

## 设备与 API28 参考环境

- 标准目标别名：`API28_PIXEL_2_PRIMARY`，唯一主设备验收需要 API level 28，并由 Runner/设备清单证明实际型号为 Pixel 2 配置；禁止只凭命名写 Pixel_2。
- 当前 `.github/workflows/android.yml` 的 `android-runtime` 用自托管标签 `[self-hosted, yujian-android, api28]`，按 gate 顺序单并发运行 API28 emulator。脚本选择名称中首个含 `28` 的 AVD，找不到时退到首个 AVD；因此 workflow 标签本身不足以证明 AVD 是 Pixel_2。若实际设备 profile 不能证明 Pixel_2，结果仍可报告为 API28 runtime profile，但 Pixel_2 专属 Gate 为 `BLOCKED`。
- Workflow 识别 Gate `recognition-frozen` 配置 `-camera-back none`，相机 Runtime Gate `camera-runtime-v1` 配置 `-camera-back emulated`。`-camera-back emulated` 可供合成 CameraX 图像源，但仍须保存 CameraX capture trace；不能把图库图片或 Mock 冒充相机流。
- Runner 的基础显示 profile 逻辑为 1080×1920、`wm density 480`、`font_scale=1.0`。这些是 workflow 当前请求/检查的值，不是本次运行事实；证据须保存 `wm size`、density、font scale、`ro.build.version.sdk`、`ro.product.model`、AVD 名称、设备序列的受控摘要与 Runner 镜像版本。不得写入真实 Runner 观察值，直至实际执行。
- Camera runtime 测试必须在提供 CameraX 的 API28 环境；若特定相机行为不能由该 emulator 摄像头证明，应另登记获批的实体设备 profile，不降低场景的真实相机条件。
- 短屏、长屏、旋转、字体缩放及无障碍均是独立 profile/变体，不能以主机截图替代。具体尺寸按批准的设备配置文件记录，不在本阶段臆造 DPI/分辨率。

## 权限 Host 准备

1. 安装精确 commit 构建的 debug APK/AndroidTest APK，校验 APK SHA 与 runtime APK pair 清单。
2. 仅 Host 通过平台测试机制重置/授予/撤销 CAMERA；记录 `checkSelfPermission`、请求历史和可取得的系统权限 flags。App 测试 seed 不得设置系统权限。
3. `FIRST_USE` 首次请求必须从 Empty Home 上的 Camera 用户操作触发；`GUIDE_PENDING`/`RETURNING` 的 grant 必须先由 Host 设置后再启动 app。
4. 拒绝场景不根据 `shouldShowRequestPermissionRationale=false` 单独判断“永久拒绝”；需同时保留请求历史，测试 Host 能获得时读取 `USER_SET`/`USER_FIXED` 等 flags，无法可靠证明时状态记 `REQUESTABILITY_UNKNOWN`，阻止对“不可请求”分支的 PASS。
5. Settings 测试从当前 package 的 App Details Settings 打开；回前台后重新调用系统权限查询。若系统设置页无法启动、系统状态无法观测或重置失败，归为 `INFRA_BLOCKED`，不把系统工具故障记成产品 PASS。
6. Photo Picker 测试不得申请 CAMERA；测试报告保存 Picker 启动/选择/取消和 CameraX request/capture 零调用的独立 trace。

## Reset / 清理与数据隔离

- 每个 case 起始前校验上个 case 无未完成 Attempt、无 Pending URI lease、无记录新增；应用状态只按 QA manifest 配置重置。
- 权限历史和 runtime environment 由 Host reset；不要清除整个 runner 上与该 job 无关的设备/用户数据。
- 清理失败时立即标记环境不可复用；后续 case 不执行，并将 `BLOCKED` 传播到依赖场景。
- `QA_RECOGNITION_001` 的记录数起点为 0。拍照、推理、Back、Cancel、Retry 都必须保持 0；显式保存的独立测试应以 before/after 计数并验证 exactly-once，但保存后详情页不在本套 Main Journey 范围。
- 测试外部后台不可用、无获批照片、模型 SHA 不匹配或权限状态无法控制时应在执行前失败为 BLOCKED，而不是改期望值。

## 每次执行的固定清单

`run_id`, `source_sha`, `apk_sha256`, `android_test_apk_sha256`, `model_artifact_sha256`/model release ID, dataset version/manifest SHA, input ID/approved fixture SHA, AVD/device profile, API level, physical/logical size, density/font scale, permission pre/post state, CameraX/provider trace, Attempt/image IDs, pipeline phase/terminal events, assertion report, screenshots/visual review references, test runner version and evidence manifest SHA。每个值来自同一 run；空值保持显式 `UNAVAILABLE` 并按 Gate 分类处理。

## 证据隐私

原始个人照片、相机预览、含身份的日志和系统账户信息不得直接提交到公开 GitHub。运行时证据采用受控 CI artifact，最小化采集并设置保留/访问策略。公开 PR 只可提交已审核的非敏感验收摘要、可复算校验和及规范，不在本任务提交真实运行日志或图片。
