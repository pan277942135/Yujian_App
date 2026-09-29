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

## 当前公共系统状态

背景系统当前为 **部分完成**：BG_ENV_HERO / BG_CONTENT / BG_DATA / BG_CAPTURE /
BG_SOLID_FALLBACK 的规则已经存在，但尚未形成一个独立、可复用的 Morning Lake Background Master。

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
