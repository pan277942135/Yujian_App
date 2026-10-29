> **DEPRECATED_NON_EFFECTIVE** — Historical Color system — use `Color_System_V3_FINAL.md` and `Color_Tokens_V3_FINAL.json` only.

# Color System V3

**状态：REVIEW。** Core Visual V1 palette、Android semantic mapping、Frozen Action / Text Action / Icon Action colors 已合并登记到 `Color_Tokens_V3.json`。每项包含 HEX、RGBA、opacity、usage 和来源。

语义组：Brand、Background、Surface、Glass、Text、Border、Status。Mist Glass treatment 仍由 GLASS_A/B/C tokens 控制。Capture 的 `#102D35` 只作临时缺图 fallback，不作常驻页面背景。金色仅供品牌、重要时刻和有意义鱼获。

页面不自行新增颜色；新值须先登记语义 token 和 authority。现有 Frozen 页的明确颜色例外映射至其页面 / 组件合同，不自动重绘。**尚未遍历全部 658 个设计文件的 CSS / SVG / Markdown / JSON 字面颜色**，所以不宣称无孤立色，完成扫描前保持 REVIEW。

Shared Status palette目前没有全局 Success / Warning / Error 值；tokens 用 MISSING 明确标出，不以视觉猜测补色。Color 保持 REVIEW。
