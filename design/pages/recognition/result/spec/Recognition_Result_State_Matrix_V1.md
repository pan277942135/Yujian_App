# Recognition Result State Matrix V1.1

| State | Primary message | Species handling | Metadata / Story | Actions |
| --- | --- | --- | --- | --- |
| RESULT_HIGH | confirmed species identity | Top-1 selected; 修改鱼种 available | visible | 继续记忆 + 保存本次鱼获 |
| RESULT_MEDIUM | 帮我确认一下，这条鱼更像哪一种？ | explicit candidate or selector choice | after explicit selection | 继续记忆 + 保存本次鱼获 |
| RESULT_LOW | 无法确认是什么鱼 | manual selection; no automatic species | visible before and after selection | 手动选择鱼种 / 重新拍摄 / Dual CTA |
| ERROR_NO_FISH | 没有找到可识别的鱼 | none | recovery panel only | 重新拍摄 / 从相册选择 |
| ERROR_IMAGE_QUALITY | 照片不够清晰，无法识别 | none | recovery panel only | 重新拍摄 / 从相册选择 |

## Low

Low Metadata and Story are editable draft state before species resolution. If an
unresolved CTA is tapped, remember the requested destination, open Species
Selector with LOW_MANUAL, commit the species, and resume the destination. If the
user returns unconfirmed, preserve draft fields and clear the pending request.
No unknown-species FishRecord is created.

## Medium

The first candidate may be suggested but is not selected automatically. Medium
Selected uses restrained Gold; Species Selector Selected remains Teal.
