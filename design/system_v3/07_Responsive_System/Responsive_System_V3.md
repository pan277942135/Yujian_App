# Responsive System V3

**状态：REVIEW；只冻结设计规则，不修代码。** 基准宽度 360 / 393 / 412 / 480dp；参考画布 1080×1920 px 不等同设备 dp。所有 layout 用 dp、文字用 sp；页面冻结 raster 转换使用页面自己的公式。

| Layout | Rule |
|---|---|
| Fixed | 锚定到页面 Frozen slot；除合同明示外不表示 window sticky。 |
| Flexible | Hero / Content 仅按 page authority 扩展；禁止图像拉伸填满剩余高度。 |
| Scroll | Detail 与密集内容优先纵向滚动，不压缩事实文字和可达性。 |
| Adaptive | 卡片/行可增高换行，保持语义顺序；Fish Guide 保持 carousel。 |

Status bar、cutout、navigation bar 由宿主动态 Insets 管理且只计算一次。IME 出现时重算可用视口并 resize/scroll，使焦点字段与主操作可见。尊重系统 fontScale，容器增长、长页滚动；不静默缩字或用静态 screenshot spacer。

历史问题规则：Normal Home 使用已批准 NH05 单一整页偏移与 window origin，runtime anchor 待测；Recognition Result 按状态保留 Hero / Mist Glass 几何并滚动，不拉伸玻璃面板；FishRecordDetail 用 HERO_ADAPTIVE 安全填充或 Safe Fit；Login 采用动态 IME inset + resize/scroll。细节列于 `Geometry_Tokens_V3.json`。这些 page-level 核验仍待 Page Contract / Validation，不构成 runtime PASS。
