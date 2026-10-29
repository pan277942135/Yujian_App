# 分享模板 · 响应式与无障碍 V1

Status: **FROZEN**

## 1. Output canvas

Primary frozen output direction:
- portrait 9:16.

Existing recovered boards are 941×1672 references.

The pixel size is not the runtime/device contract.

## 2. Preview scaling

In-app preview may scale the share artifact to available width.

Rules:
- preserve aspect ratio;
- no stretch;
- no crop that removes required content;
- preview scaling must not alter export composition.

## 3. Source photos

T02 real photos:
- preserve important fish/person subject;
- avoid aggressive fish-head/fish-tail crop;
- support portrait/landscape source material within the fixed output composition.

## 4. Text

Support text wrapping and localized expansion.

Do not shrink critical metrics/copy into illegibility to preserve a screenshot.

## 5. Accessibility

Template/range controls:
- minimum intended 44dp target;
- selected state exposed semantically;
- selected state not color-only.

Preview itself needs an accessible description summarizing:
- template;
- time range;
- key statistics.

## 6. Share action

Share/export action:
- explicit accessible label;
- disabled/loading state exposed;
- result/failure announced.

## 7. Reduce Motion

No custom decorative motion is required by the recovered V1 share design.

Any standard picker/transition behavior must not be required to understand template state.

## 8. High contrast / color

Text over photography requires sufficient readable contrast/support surface.

Do not rely on thin gold decoration for meaning.
