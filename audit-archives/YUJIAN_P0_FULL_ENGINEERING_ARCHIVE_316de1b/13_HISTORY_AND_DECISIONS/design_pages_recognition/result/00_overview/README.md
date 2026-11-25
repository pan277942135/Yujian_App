# Recognition Result Overview V1.1

Status: DESIGN CLOSED — FROZEN ASSET AUTHORITY

The five state-specific Frozen Result PNGs plus the Frozen Species Selector and Metadata Edit boards are authoritative. Runtime defaults never override them.

## States

- High: usable species result;
- Medium: plausible candidates requiring explicit confirmation;
- Low: unresolved species with editable draft metadata/story;
- No Fish: no usable fish subject;
- Image Quality: source quality blocks reliable recognition.

## Shared Result rules

- Result, Recovery, and Low pages use centered-title BACK_CENTER_TITLE;
- Species Selector remains left-title BACK_TITLE;
- Hero geometry is state-specific, not universal;
- High / Medium / Low use original oriented photo with bbox-protected Subject Crop Fill when safe;
- No Fish / Image Quality use full-source Evidence Fit;
- all Result panels use shared MistGlass with restrained Result radius;
- no Processing edge/focus effects survive into Result.

## Composition

- High: Hero → Species Identity → vertical Metadata → Story → Dual CTA;
- Medium: Hero → unified Candidate Glass Panel → resolved record flow;
- Low: Hero → message/manual/retake → Metadata → Story → Dual CTA;
- Recovery: Hero → one unified Recovery Glass Panel.

## Low pending save

Low CTA is visible before species resolution. Tapping it remembers Continue Memory or Save Catch, opens Species Selector with LOW_MANUAL, and resumes only after a species is selected. No unknown-species FishRecord is ever created. Returning unconfirmed preserves draft fields and clears the pending destination.

## Shared copy

Story title: 写下这次鱼获的故事
Placeholder: 记录这一刻的感受……
Limit: 300 Unicode code points with {count}/300 live counter.

Final physical/visual acceptance belongs to the User.

## Design-system scoped exceptions (audit 2026-10-10)

The five Frozen Result pages use a viewport-centered `BACK_CENTER_TITLE` composition, while the global Top Navigation V1 still has exactly three frozen generic variants; Species Selector stays `BACK_TITLE`. Confirmed Result dual CTA surfaces/widths are governed by Result V1.1 instead of shared-button historical example mapping. Neither exception becomes a new global component variant.

- `design/pages/recognition/result/review/Recognition_Result_Design_Audit_20261010.md`
- `design/system/components/top_navigation/exceptions/Recognition_Result_Centered_Title_V1.md`
- `design/system/components/action_button/exceptions/Recognition_Result_Actions_V1.md`

Runtime parity and physical screenshot acceptance are separately pending. The five state PNG assets and two edit boards are unchanged.


## 3+2 判定逻辑与阈值审议（2026-10-10）

- 当前程序的检测阈值、质量门分支顺序、分类器 `0.45 / 0.12` 与完整路由优先级，详见 `../spec/Recognition_Result_3plus2_Decision_Spec_V1_1.md`。
- 机器可读当前实现和未来审议项：`../spec/recognition_result_3plus2_decision_contract_v1_1.json`。
- **3+2 保留冻结；新数值阈值并未冻结。** 质量 WARNING 升 High、最小鱼体面积门绕过、图片质量文案误归因和候选集外物种，仍需真实样本校准与产品批准。
- 此项是只读审查当前 Android 源码后形成的设计规范文件，绝不代表修改 Android / Detector / Classifier / APK。
