# FishRecordDetail Populated Memory Media V1

Status: **FROZEN VISUAL AUTHORITY**  
Parent: **02 · B 面 · 鱼获记忆**  
Raster: `design/pages/fish_record/detail/frozen/states/FishRecordDetail_State_Populated_Memory_V1_Frozen.png`

## Child states

- 1 photo
- Multiple photos
- Photos + video
- Overflow / grid continuation

These remain children of the existing B-side. They add no 00–05 root entry and do not turn FishRecordDetail into a social gallery.

## Frozen rules

- Preserve `鱼获记忆`, photo/video count semantics, `添加照片/视频`, `继续拍照`, and `录制视频`.
- Counts derive from attached original media; photos and videos are counted separately.
- Display original user media in the grid. Do not rewrite source bytes or substitute generated content.
- Continue the grid vertically and indicate overflow count without adding social-feed behavior.
- Keep the same FishRecordDetail shell, Hero, and record identity.

The raster embeds one genuine repository catch photo as a visual fixture. The remaining positions are neutral source-fill slots because this repository contains no distinct second catch photo or video fixture. They are not duplicated or generated media; runtime fills them only with attached user originals.
