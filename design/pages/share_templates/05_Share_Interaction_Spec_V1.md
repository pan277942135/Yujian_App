# 分享模板 · 模板选择与分享交互 V1

Status: **FROZEN**

## 1. Entry

Share Template is reached from a share-capable FishRecord / My Catches context.

The share experience must preserve the current user task and return cleanly to its source.

## 2. Selection model

Two independent controls:

1. template:
   - T01 战绩卡
   - T02 水边故事
2. time range:
   - 今天
   - 本周
   - 本月
   - 自定义

Changing the time range updates the same selected template; it does not navigate to a different template family.

## 3. Preview

Preview reflects real selected data.

Do not:
- render hardcoded demo totals as if real;
- change species identity;
- invent story text.

## 4. V1 editing boundary

Fixed-template system.

No free-form canvas/editor.

Any limited content selection must preserve:
- factual data;
- template hierarchy;
- required species coverage.

## 5. Share action

The primary action exports/shares the selected template output.

It must not silently share before explicit user action.

System share sheet may be used as the external handoff.

## 6. Failure

If share/export preparation fails:
- keep selected template/range;
- show clear retryable feedback;
- do not lose the user’s selection.

## 7. Back

Back returns to the source context.

If no destructive unsaved editing exists, no discard confirmation is required.

## 8. Historical Runtime note

The old Android `ShareCenterScreen.kt`:
- uses six period chips;
- includes hardcoded sample totals for non-single ranges;
- shares text only.

It is historical Runtime evidence and does not define current V1 design authority.
