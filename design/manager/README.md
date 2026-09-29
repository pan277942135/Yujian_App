# 渔见设计管理

这是一个轻量、零依赖的渔见设计治理工具。

V1 只管理设计侧内容：

- 行为
- 视觉
- 动效
- 震动
- 声音
- 资产
- 设计版本
- 权威来源与冻结状态

Runtime、Evidence 和 Work 交接暂不进入 V1 页面。

## 数据来源

设计管理读取：

`design/registry/experience_registry_v1.json`

各模块自己的规范与 manifest 仍然是真正的设计权威；Registry 只是跨模块索引。

## 本地打开

在仓库根目录执行：

```bash
python3 scripts/design_manager.py serve
```

然后打开：

`http://127.0.0.1:8765/design/manager/`

不需要 Node、Vite、数据库，也不需要安装额外依赖。

## 校验

```bash
python3 scripts/design_manager.py validate
```

校验内容包括：

- Design Manager 元数据；
- 每个模块只有一个展示名称和一个当前设计版本；
- 六个设计维度是否完整；
- 状态值是否合法；
- 所有已冻结维度是否存在权威来源；
- 所有登记的权威来源 / 合同路径是否真实存在；
- 版本视觉 / 规范权威路径是否真实存在。

`缺失`、`部分完成`、`收口中` 属于被管理的设计缺口，不会导致工具本身校验失败。

## 汇总

```bash
python3 scripts/design_manager.py summary
```

输出设计侧矩阵：

`行为 / 视觉 / 动效 / 震动 / 声音 / 资产`。

## 当前页面能力

1. 模块列表、搜索和状态筛选；
2. 当前设计版本和整体设计状态；
3. 当前视觉权威直接预览；
4. 六个设计维度及其权威来源；
5. 设计版本历史，包括当前版本、候选版本和废弃版本。

## Empty Home 样板

当前 Empty Home 的设计权威关系为：

- V2：已冻结基础视觉；
- V2.2：已冻结，当前版本；
- 历史 V3 收口文档：已废弃，不再作为当前权威。

## 编辑规则

不要把页面展示状态当成第二份 Source of Truth。

正式更新流程：

1. 修改模块自己的规范 / 资产 / manifest；
2. 更新 `design/registry/experience_registry_v1.json`；
3. 执行 `python3 scripts/design_manager.py validate`。

V1 浏览器页面为只读视图。
