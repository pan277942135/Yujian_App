# High Result V1.1

Status: FROZEN

Authority: design/pages/recognition/design/05_Result_High_Frozen.png.

## Composition

Hero → Species Identity → vertical three-row Metadata Card → Story Card → Dual CTA.

At the canonical 360dp viewport the Hero target is approximately 322 × 210dp (aspect 1.53). The Hero is the original oriented photo, using the real bbox as FishSafeRect guidance and preferring safe Subject Crop Fill.

## Metadata

Rows are:

- [icon] 长度 — 请输入, example 42.6 cm;
- [icon] 重量 — 请输入, example 1.28 kg;
- [icon] 地点 — 请选择, example 千岛湖.

Each row opens the shared same-page editor. Location permission is requested only after the explicit current-location action.

## Story and actions

Story title: 写下这次鱼获的故事
Placeholder: 记录这一刻的感受……
Maximum: 300 Unicode code points; live counter is rendered.

Actions are equal-width, single-line, and use:

- 继续记忆 — light secondary action with memory icon;
- 保存本次鱼获 — Result Save light surface with DeepLakeBlue content and MorningGold edge/accent.

Top Navigation uses BACK_CENTER_TITLE. Species correction does not rerun Recognition and feedback remains attached to the eventual save.
