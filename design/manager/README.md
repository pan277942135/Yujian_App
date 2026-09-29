# 渔见设计管理

Design Manager V1 采用两层结构：

## 第一层：公共设计系统

跨页面复用、由页面引用：

- 背景系统
- 主拍摄按钮
- 顶部导航
- 雾面玻璃
- 颜色与字体
- 间距与圆角

数据来源：

`design/registry/shared_design_system_v1.json`

页面不能复制公共元素后私自修改。需要变化时，应升级公共系统版本或登记明确的页面例外。

## 第二层：页面模块

页面自己的设计状态、视觉权威、版本与设计维度：

`design/registry/experience_registry_v1.json`

每个页面通过 `shared_system_refs` 声明使用哪些公共系统和变体。

例如：

```text
空首页
├── 背景系统 / BG_ENV_HERO
├── 主拍摄按钮 / home_primary_capture
├── 颜色与字体
└── 间距与圆角
```

## 一级菜单折叠规则

Design Manager 左侧导航的一级菜单统一支持折叠，并且**默认收起**。

适用：

- 背景系统；
- 按钮规范；
- 拥有高保真子菜单的页面模块。

规则：

- 默认不展开子菜单；
- 点击一级菜单：进入一级总览，并切换展开 / 收起；
- 点击子菜单：自动保持对应一级菜单展开；
- 当前 URL / Hash 指向某个子项时，对应一级菜单自动展开；
- 搜索状态下自动展开匹配分支，避免匹配结果被折叠隐藏；
- 没有子菜单的一级项不显示展开箭头，也不增加无意义折叠行为。

## 背景系统导航结构

背景系统采用 **父级总览 + 子菜单直达工作区**，不在右侧重复生成一套 Variant 子页面。

```text
背景系统
├── BG_ENV_HERO
├── BG_CONTENT
├── BG_DATA
├── BG_CAPTURE
└── BG_SOLID_FALLBACK
```

交互规则：

- 点击「背景系统」：只显示系统总览、两张 Canonical Master、全局 Authority 与使用范围；
- 点击任一 BG 子菜单：右侧直接进入该背景类型工作区；
- 子菜单工作区只显示该类型的预览、参数合同、使用页面/场景和 Authority；
- 不再显示重复的「5 种背景类型」卡片索引；
- 子菜单页不混入父级总览和全局 Authority/Usage。

## 当前公共系统状态

背景系统 V1 已 **冻结**：

- Morning_Lake_Sunrise_Hero_V1：仅空首页；
- Morning_Lake_Master_V1：其余湖景页面统一母版；
- BG_ENV_HERO / BG_CONTENT / BG_DATA：只通过雾化、饱和度、对比度、亮度区分；
- BG_CAPTURE：用户当前照片；
- BG_SOLID_FALLBACK：固定浅色 / 深色兜底。

跨页面有内容验证板已归档到：
`design/system/backgrounds/morning_lake_v1/validation/Morning_Lake_Content_Adaptation_Approved_V1.png`。

主拍摄按钮已经拥有独立视觉、动效和震动合同，可作为公共组件管理。

## 本地打开

```bash
python3 scripts/design_manager.py serve
```

浏览器打开：

`http://127.0.0.1:8765/design/manager/`

## 校验

```bash
python3 scripts/design_manager.py validate
```

同时检查：

- 公共设计系统 Registry；
- 页面 Registry；
- Authority 路径；
- 公共系统 ID；
- 公共变体 ID；
- 页面 → 公共系统引用关系；
- 每个页面只有一个当前版本。

## 汇总

```bash
python3 scripts/design_manager.py summary
```

输出两段：

1. 公共设计系统；
2. 页面模块。

## 范围

V1 仍然只管理设计：

- 行为
- 视觉
- 动效
- 震动
- 声音
- 资产
- 公共系统
- 页面版本
- 权威与冻结状态

Runtime、Evidence 和 Work 交接暂不进入当前 Design Manager。
