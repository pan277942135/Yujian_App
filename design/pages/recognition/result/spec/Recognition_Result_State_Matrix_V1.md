# Recognition Result State Matrix V1

| State | Primary message | Species handling | Metadata | Initial action | Resolved action |
| --- | --- | --- | --- | --- | --- |
| RESULT_HIGH | confirmed species identity | Top-1 selected by default; 修改鱼种 available | available | normal record flow | 继续记录记忆 + 保存本次鱼获 |
| RESULT_MEDIUM | 帮我确认一下，这条鱼更像哪一种？ | candidate confirmation + other-species entry | after confirmation | confirm/select species | 继续记录记忆 + 保存本次鱼获 |
| RESULT_LOW | 无法确认是什么鱼 | no species claimed; manual selection optional | hidden until species resolution | 手动选择鱼种 / 重新拍摄 | reuse normal resolved-species record flow |
| ERROR_NO_FISH | 没有找到可识别的鱼 | none | hidden | 重新拍摄 / 从相册选择 | N/A |
| ERROR_IMAGE_QUALITY | 照片不够清晰，无法识别 | none | hidden | 重新拍摄 / 从相册选择 | N/A |

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
- confirmation prompt is one line
- candidate cards are horizontally arranged according to Frozen composition
- “都不是 / 选择其他鱼种” remains visible as an escape
- explicit user confirmation must be distinguishable from model suggestion
- resolved Medium reuses High's record/CTA contract

## Low

Required visible roles:
- `无法确认是什么鱼`
- `手动选择鱼种`
- `重新拍摄`

Before manual species selection:
- no normal metadata block
- no save CTA
- no pending/unknown species record

After selection:
- reuse the normal resolved-species Result contract.

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
