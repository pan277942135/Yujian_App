# Exact-HEAD 测试与构建结果

Commit：316de1b705fb54ee7d6e2114259ec1f87773aedf  
Gradle：9.3.1；JDK 17；Debug APK 由该 Commit 构建。  
最终命令日志：/tmp/yujian-final-gradle.log  
Gradle 结果：BUILD SUCCESSFUL（185 actionable tasks；41 executed，144 up-to-date）。

## Gradle 单元测试

| 模块 / Task | tests | passed | failed | skipped | XML 报告目录 |
|---|---:|---:|---:|---:|---|
| recognition-domain:test | 8 | 8 | 0 | 0 | recognition-domain/build/test-results/test |
| recognition-runtime:test | 57 | 57 | 0 | 0 | recognition-runtime/build/test-results/test |
| application-core:testDebugUnitTest | 62 | 62 | 0 | 0 | application-core/build/test-results/testDebugUnitTest |
| waterlight-renderer:test | 64 | 64 | 0 | 0 | waterlight-renderer/build/test-results/test |
| recognition-ui:testDebugUnitTest | 237 | 237 | 0 | 0 | recognition-ui/build/test-results/testDebugUnitTest |
| recognition-result-components:testDebugUnitTest | 0 | 0 | 0 | 0 | NO-SOURCE |
| recognition-result-ui:testDebugUnitTest | 20 | 20 | 0 | 0 | recognition-result-ui/build/test-results/testDebugUnitTest |
| app:testDebugUnitTest | 126 | 126 | 0 | 0 | app/build/test-results/testDebugUnitTest |
| 总计 | 574 | 574 | 0 | 0 | 共解析 110 个 XML 文件 |

本轮完整 Gradle 命令还执行并通过：

- :app:compileDebugKotlin
- :app:compileDebugAndroidTestKotlin（只编译，没有启动设备或 Emulator）
- :app:assembleDebug

AndroidTest Instrumentation 未运行。第一次编译发现 AndroidTest 调用了已移除的 onPhotoFramePresented 参数；测试夹具改用 onPhotoFrameCommitted，并把其断言语义改为 Frame Commit。最终整组 Exact-HEAD Gradle 任务成功。

## 其他执行过的检查

- docs/contracts/yujian-ai-v2/validate_contract_package.py：13 项通过；六份冻结核心合同 SHA256 与预期一致；23 行 Gate Policy 登记与文件 hash 一致。
- scripts.android_runtime.test_verify_orb_to_glyph_video：3 tests passed。
- bash -n scripts/android_runtime/capture_orb_to_glyph_video.sh：通过。
- APK：aapt package/version 解析通过；apksigner APK Signature Scheme v2 验证通过；DEX 中 SOURCE_REVISION 与最终 Commit 完全相同。
- APK 模型资产字节核对：classifier、metadata、class_map 和 tensor_contract 的包内 SHA256/bytes 与 model_release_contract 值一致；app/src/main/assets 相比原始基线未改动（.gitkeep 按 Android 资源规则未打包）。
- scripts/verify_production_model.py：失败，错误为 invalid asset id for fish_classifier_v0_2.tflite。原因是 model_release_contract.json 的 release_assets 行只有 name/release_id，缺少该验证器要求的 id、digest、size 等 Release API 信息。此失败保留，未修改模型清单冒充远端来源验证。
- 远端分支 ref 与 refs/pull/346/head 已核验到最终 Commit；Main ref 未变化。GitHub REST PR/Release 查询被 403 拒绝，PR Draft 状态和 Release 上传无法核验。
