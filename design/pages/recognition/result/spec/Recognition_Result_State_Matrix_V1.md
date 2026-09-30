# Recognition Result State Matrix V1

| State | Primary message | Species handling | Metadata | Primary action | Secondary action |
| --- | --- | --- | --- | --- | --- |
| RESULT_HIGH | confirmed species identity | Top-1 selected by default; 修改鱼种 available | available | 保存本次鱼获 | 继续记录记忆 |
| RESULT_MEDIUM | 帮我确认一下，这条鱼更像哪一种？ | candidate confirmation + other-species entry | available after/alongside confirmation | 保存本次鱼获 after resolution | 继续记录记忆 |
| RESULT_LOW | 无法确认是什么鱼 | 鱼种待确认; manual selection optional | must not block initial recovery state | 保存本次鱼获 with pending species where product data contract supports it | 手动选择鱼种 / 重新拍摄 as lower-weight recovery |
| ERROR_NO_FISH | 没有找到可识别的鱼 | none | hidden | 重新拍摄 | 从相册选择 |
| ERROR_IMAGE_QUALITY | 照片不够清晰，无法识别 | none | hidden | 重新拍摄 | 从相册选择 |

## High

Required visible roles:
- species name
- `修改鱼种 ›`
- length
- weight
- location
- catch note
- voice-note affordance when supported
- dual bottom actions

Do not add a separate “已识别” label.

## Medium

Required behavior:
- hero photo stays the same
- candidate region is additional confirmation UI, not a replacement hero
- candidate cards are horizontally arranged according to Frozen composition
- “都不是 / 选择其他鱼种” remains visible as an escape
- explicit user confirmation must be distinguishable from model suggestion

The UI must not silently turn Medium into High merely because Top-1 is prefilled internally.

## Low

Required visible roles:
- `无法确认是什么鱼`
- `鱼种待确认`
- manual species recovery
- retake as a lower-priority recovery path
- save path remains product-priority

Design intent is that an uncertain species does not erase a real catch memory.

If the current persistence schema cannot save a pending species, that is a runtime/data-contract gap. The design must not be rewritten into “manual species is mandatory” merely to fit an implementation limitation.

## No Fish

Frozen copy:
- `没有找到可识别的鱼`
- `请让鱼完整出现在画面中，再试一次。`

Actions:
- primary `重新拍摄`
- secondary `从相册选择`

## Image Quality

Frozen copy:
- `照片不够清晰，无法识别`
- `请拍摄更清晰的照片，确保鱼的整体轮廓清晰、没有遮挡。`

Actions:
- primary `重新拍摄`
- secondary `从相册选择`

## Technical failure

`TECHNICAL_FAILURE` is outside the 3+2 frozen set.

Runtime-safe generic copy may remain:
- `识别没有完成`
- `请重新拍摄或选择照片。`

It must never expose stack traces, model names, HTTP codes or internal pipeline details.
