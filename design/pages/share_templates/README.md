# 分享模板 · Design Authority Index V1

Status: **PARTIAL — EXISTING DESIGN RECOVERED / VISUAL FREEZE PENDING**

## 1. Current product model

Current V1 keeps exactly two template families:

- **T01 · 战绩卡**
- **T02 · 水边故事**

The time range is independent from the template family.

Current time-range set:

- 今天
- 本周
- 本月
- 自定义

The historical P08 model that treated `单条 / 今日 / 本周 / 本月 / 本年 / 累计` as six separate templates is preserved only as historical context and is superseded by the current two-template model.

## 2. Current menu

1. 01 · 总览与产品模型
2. 02 · T01 · 战绩卡
3. 03 · T02 · 水边故事
4. 04 · 时间范围与数据规则
5. 05 · 模板选择与分享交互
6. 06 · 响应式与无障碍

## 3. Existing visual material

Recovered historical high-fidelity outputs are displayed directly in Design Manager.

They are not regenerated and are not silently promoted to Frozen Authority.

T01 recovered iterations:
- 千岛湖九月钓鱼战绩 инфографик.png
- 千岛湖九月战绩·渔见奢华玻璃卡.png

T02 recovered iterations:
- 千岛湖九月垂钓记.png
- 千岛湖水边故事垂钓纪念卡.png
- 千岛湖九月水边故事.png
- 水边故事：千岛湖九月垂钓记.png
- 千岛湖九月钓鱼故事.png

Repository images under `assets/recovered/` are web-display derivatives only:
- same composition/content;
- no crop;
- no redesign;
- resized for Design Manager display;
- WebP conversion only.

The original PNG filename, dimensions and SHA-256 remain recorded in `assets/manifest.json`.

## 4. Visual status

Behavior / product model: **FROZEN**

Visual: **PARTIAL**

Reason:
- multiple real historical visual iterations exist;
- no reliable evidence was found that one named binary was explicitly approved as the unique final visual authority.

Do not fabricate a final authority from memory.

## 5. Scope boundary

This design package does not:
- modify Android Runtime;
- redefine FishRecord;
- create new images;
- create a free-form poster editor;
- invent missing catch data;
- auto-write personal story copy.

## 6. Runtime boundary

The current Android `ShareCenterScreen.kt` is historical implementation evidence only.

Its six-period chip model and hardcoded sample statistics do not override this current design authority.

Original 941×1672 PNG files are stored under `assets/originals/`. They are the recovered historical source files, byte-verified against the SHA-256 values in `assets/manifest.json`. The existing 120×213 WebP files under `assets/recovered/` remain display thumbnails only. No image was generated or visually edited in this update.
