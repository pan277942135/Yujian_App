# 我的鱼获 · 列表图片规范 V1

Status: **FROZEN**
Page: `my_catches_v2`
Scope: FishRecordRowCard list thumbnail

## 1. 设计目标

列表首先表达“这一次真实鱼获”，不是鱼种百科、AI 生成资产或 B-side 展示。

图片视觉优先级：

**真实现场照片 > 鱼种文字 > 长度/重量 > 地点/日期 > Growth Mark**

## 2. 唯一主来源

V1 列表缩略图必须使用该 FishRecord 的**原始鱼获现场照片**。

Current data source:

`RemoteCatch.imageUrl`

Current presentation path:

`record.imageUrl → resolveImageUrl(...) → FishRecordPresentation.imageUrl → RemoteImage`

允许：
- 用户当次识别 / 保存的真实鱼获照片；
- 本地原图（若仍存在）；
- 服务端返回的同一原始鱼获照片。

禁止作为列表主图：
- B-side 生成结果 `bsideUri`；
- 透明鱼体 / cutout；
- 标准化鱼体 / outlined fish；
- 鱼鉴物种图；
- AI 生成背景或卡片；
- 后续 Catch Memory 中新增的其它照片 / 视频封面；
- 与该 FishRecord 无关的图库图或占位物种图。

B-side 仅属于 FishRecordDetail 的 B 面，不改变列表的真实记录属性。

## 3. 列表与详情的一致性

点击列表卡进入同一个 `FishRecordDetail(recordId)`。

列表缩略图与 FishRecordDetail A 面属于同一条真实照片链路。

列表不因 B-side SUCCESS 自动替换封面。
列表不因用户后续添加记忆媒体自动替换封面。

如果未来支持用户手动选“封面图”，必须升级本规范版本，不在 V1 静默引入。

## 4. 缩略图容器

Current V2 row-card target:

- frame: **1:1**
- runtime target: **82dp × 82dp**
- radius: **14dp**
- display: **ContentScale.Crop**
- background fallback surface: `SoftWater`

旧版“双列大图卡”属于历史探索，不是当前 V2 Timeline Row Card Authority。

## 5. 裁切规则

目标不是“把鱼抠出来”，而是让真实现场照片在小尺寸下仍能快速辨认鱼体。

优先级：

1. 若存在可靠 fish bbox / subject metadata：
   - 以鱼体中心作为 crop anchor；
   - 尽量保留完整鱼头和鱼尾；
   - 在鱼体外保留适度现场上下文；
   - 不把手、鱼桶、水面、地面等真实环境全部裁掉。
2. 若没有可靠 subject metadata：
   - 使用原始照片中心裁切；
   - 禁止调用生成模型重构缩略图。
3. 如果智能裁切会切断主要鱼体：
   - 回退到更宽松 crop；
   - 不以“填满画框”为优先。

## 6. 加载 / 失败

加载失败只影响当前缩略图，不影响整条鱼获记录。

Fallback:
- `SoftWater` 容器；
- 中性鱼形占位；
- 不猜鱼种；
- 不使用 Fish Guide 物种图替代；
- 不使用 B-side 替代。

## 7. 真实性规则

禁止：
- 美化成棚拍产品图；
- 去掉所有现场环境；
- 自动换背景；
- 强 HDR / 强锐化；
- 给鱼体加发光轮廓；
- 黑金卡牌化；
- 水印 / 工具栏 / AI 标识烘焙进缩略图。

## 8. Development handoff

当前 Runtime 已正确使用 `record.imageUrl` 作为列表图片来源。

后续若实现 subject-aware crop，应视为显示增强，不改变原始媒体 Authority。
