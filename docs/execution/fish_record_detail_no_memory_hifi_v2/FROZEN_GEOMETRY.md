# FishRecordDetail V2 frozen geometry mapping

Authority: `design/system/core_visual_v1/reference/fish_record_detail_v2.png` (941 × 1672 px). The detail page’s frozen Hero frame is x=50..891 px and y=159..699 px, so the source frame is 841 × 540 px (aspect 1.5574). The frozen No Uploaded Memory state remains at `design/pages/fish_record/detail/frozen/states/FishRecordDetail_State_No_Uploaded_Memory_V1_Frozen.png`.

The source files are treated as frozen inputs. This change does not edit either PNG. Expected SHA-256 values supplied by the task are:

- Core visual reference: `3bb0fd5fd38d0721f5ac89489c224deae29c09401e2c9239fcf9435b320eeb58`
- No Uploaded Memory state: `ca585b85d1c6fec224e402d368e60d122913ca6c0cb6e5cc6750c266d7fb8a93`

## Source pixels and runtime units

Frozen coordinates are source-image pixels, not Compose dp or sp. The Hero aspect ratio is transferred into the runtime with `widthDp / (841 / 540)`; the implementation never copies 841 px or 540 px directly into dp. A device-specific conversion from source px to dp requires the reference rendering density. No density is encoded in the PNG, so an absolute px-to-dp conversion would be an assumption.

At a nominal 393 dp safe viewport, proportional scaling would map each source pixel to 393/941 ≈ 0.4176 dp. That makes the source Hero about 351 × 225 dp. The responsive implementation instead preserves its existing 20 dp side margins at that width, producing a 353 × 227 dp Hero (rounding to whole dp). The difference is below 0.6% per dimension.

## Responsive mapping

The resolver receives the available safe width after start/end safe insets. It uses 16 dp page margins through 320 dp, 20 dp through 393 dp, and 24 dp above 393 dp. The Hero height is rounded from the frozen 841:540 aspect ratio.

| Safe viewport width | Page margin | Hero width | Hero height |
| ---: | ---: | ---: | ---: |
| 320 dp | 16 dp | 288 dp | 185 dp |
| 360 dp | 20 dp | 320 dp | 205 dp |
| 393 dp | 20 dp | 353 dp | 227 dp |
| 411 dp | 24 dp | 363 dp | 233 dp |

The top bar and safe drawing insets remain separate from scroll content. The Hero retains its aspect at short viewport heights; the `LazyColumn` scrolls the About, media and generation sections rather than shrinking or clipping them.

## Text and accessibility scale

Compose typography values are sp. User font scale applies to sp at runtime and does not alter the frozen pixel measurements or the Hero aspect. The Hero title, measurement and location occupy separate lines. Only the location is ellipsized. Media actions use a minimum 44 dp touch height and 13 sp labels; the row stacks when its available inner width cannot satisfy the 280 dp baseline scaled by font scale. This keeps the standard 360 dp layout in one row at 1.0 scale and stacks at 1.15/1.3 where space is insufficient.

## Component mapping

- `FishRecordDetailGeometryResolver`: safe width → responsive margins and Hero dp size.
- `FishRecordHeroCard`: 20 dp rounded clipping, full-photo evidence fit, separate title/measurement/location, and the shared NORMAL/ON_MEDIA Text Action for edit.
- `RemoteImage`: the detail A-side uses the same real source for the opaque blurred cover and the full-frame fitted foreground. Other callers keep the default image behavior.
- `FishMediaPicker`: frozen no-uploaded-memory copy/actions, actual media counts and thumbnails, width/font-scale-aware button arrangement.
- `FishMemorySection`: separate B-side generation state and controls, retained below the uploaded-memory section.

The change preserves the real record model, photo, measurements, location, story, media and B-side status. No photo, fish identity or record data is introduced from the frozen reference.
