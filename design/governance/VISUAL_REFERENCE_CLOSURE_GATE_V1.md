# Visual reference closure gate V1

任何新增或晋升为 `VISUAL_FROZEN` 的页面，必须在现有页面登记中引用 `visual_reference_manifest` 并附 `visual_closure_evidence`。复用页面注册表，不建立第二份全局注册表。已有冻结项不因本次补档自动重新验收；对其后续晋升/修改适用本门禁。

七项必要证据：用户确认参考登记、原始SHA准确、合规可打开、关键组件可定位、机器结构一致、现行/历史版本明确、未经许可的图像及派生物排除公开发布。

`visual_closure_evidence` 包含 `source_sha`、`browser_report`、`checks`。checks 的七项键为 `user_reference_registered`、`original_sha_verified`、`compliant_viewer_opened`、`components_located`、`machine_structure_reviewed`、`versions_distinguished`、`unlicensed_images_excluded`；必须全部为 true。browser_report 必须指向真实执行报告，记录源码SHA、视口、原图SHA、实际断言及合规查看方式；私有图不得进入公开报告或截图。

`scripts/verify_private_visual_references.py` 检查当前登记和公开排除规则，检查所有已声明 `VISUAL_FROZEN` 的视图证据字段。后续登记晋升时由相同校验器阻止无证据的声明。本次原图登记为用户确认视觉参考，状态仍 DESIGN_ONLY，不产生新的 VISUAL_FROZEN 宣称。

静态 PASS 仅代表资源登记结构符合门禁；不能替代浏览器打开、人工结构评审或运行视觉验收。公开许可 NOT_CONFIRMED 时，SHA与元数据可以公开，图像字节、派生截图与ZIP不允许提交。后续公开必须单独核实权利并审核。

公开排除门禁不可能仅凭哈希穷尽任意新生成的派生截图。因此新二进制图片变更必须由审阅者核实来源和许可；浏览器测试对私有输入禁止截图、视频或trace导出。CI仅使用合成测试图，私有原图只在本地测试环境可选传入。
