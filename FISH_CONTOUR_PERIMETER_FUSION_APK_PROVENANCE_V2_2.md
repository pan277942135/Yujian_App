# V2.2 APK Provenance

## 产物约束

- Repository: `pan277942135/Yujian_App`
- PR: [#316](https://github.com/pan277942135/Yujian_App/pull/316)
- Build workflow: `.github/workflows/android.yml` → `Android CI` → `Assemble debug APK`
- 构建必须 checkout PR source HEAD；工作流会断言 checkout SHA 等于 `SOURCE_SHA`。
- Debug APK 必须与 exact source HEAD、APK 内模型追踪和 CI artifact 同一 run 对应。基线旧 APK 不作为本次产物。

## 后构建记录

本报告先与源代码一并提交；APK 的字节数、SHA-256 和 exact-head CI run 只能在该提交的 CI 构建完成后得到。为保持最终 APK 对应报告所在的 exact source HEAD，后构建的二进制摘要记录在 PR #316 的同一 exact-head 构建/交付记录中，而不制造第二个不同 source HEAD 的 APK 声明。

| 字段 | 值 |
|---|---|
| Source HEAD SHA | 见 PR #316 本次 Android CI checkout 输出 |
| CI Run ID | 见 PR #316 本次 Android CI run |
| APK 文件 | `app-debug.apk`，从 exact-head Android CI APK artifact 解出 |
| APK bytes | CI 下载后记录 |
| APK SHA-256 | CI 下载后对 APK 本体计算；不使用 Artifact ZIP SHA |
| Unit Test | exact-head `Unit tests` job |
| Lint | exact-head `Android lint` job |
| Build | exact-head `Assemble debug APK` job |
| Shader runtime compile | 仅设备运行时编译；CI build 不代表该项 PASS |
| Device / Visual QA | NOT RUN |

PR #316 保持 Draft / Open / Unmerged，等待产品负责人用该 exact-head APK 完成真机视觉验收。
