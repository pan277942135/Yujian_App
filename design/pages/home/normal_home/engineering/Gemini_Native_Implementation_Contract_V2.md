# NH01 Gemini Native Home V2 Contract

**Task:** `YUJIAN_NH01_GEMINI_NATIVE_REBUILD_AVATAR_FIX_V2`  
**Android PR:** #167, Draft / Open / Unmerged  
**Structure handoff:** PR #164, HEAD `06308bd21e175e321e9f345dbf18174f5e0134f9`  
**Implementation starting HEAD:** `08ed847e711f8c7accd4ebba08a74e55e756882f`

## Authority and implementation boundary

The implementation follows the Gemini split-page tree recorded in PR #164's `NH01_GEMINI_STRUCTURE_ALIGNMENT_V1.json` and `NH01_GEMINI_REFERENCE_PROTOTYPE_NOT_VISUAL_PASS.html`. The raw Gemini input is `粘贴的文本 (1)(6).txt`, 18,331 bytes, SHA-256 `1988a9343a6e911c87f93871279ba6d5e161e2ef0651ecd0d783526c5bceb714`. The prototype is structure-only; neither it nor this native implementation claims a Figma live-node readback or a Frozen Pixel Parity pass.

`HomeScreen` routes the populated Normal Home state to the standalone `GeminiNormalHomeContent.kt` Compose tree. This tree owns its background, Top, Middle, and Footer containers. The previous `NormalHomeContent` implementation remains only as an uncalled rollback reference. It is not a parent, measurement source, positioning helper, or layout dependency of the Gemini page.

## Gemini layer map

| Gemini layer | Native Compose mapping |
|---|---|
| `Screen-Yujian-Home` | `GeminiNormalHomeContent`, tag `gemini-home-screen` |
| `BG-Scenic-Group` | full-screen background group, tag `gemini-layer-bg-scenic-group` |
| `BG-Mountain-Mist` | official Morning Lake bitmap in `ContentScale.Crop`, tag `gemini-layer-bg-mountain-mist` |
| `Top-Section-AutoLayout` | `GeminiTopSection`, tag `gemini-home-top-section` |
| `Header-Bar` | `gemini-home-header-bar` |
| `Brand-Title` | registered `R.drawable.brand_title_primary` |
| `User-Avatar-Btn` | `GeminiAvatarButton`; guest and signed-in actions have separate callbacks |
| `Stats-Bar` | `GeminiStatsBar`; server totals and local record-day fallback |
| `Middle-Carousel-Section` | weighted `GeminiMiddleSection`; short-height vertical scroll is local to this section |
| `Section-Header-Bar` | `GeminiSectionHeader` |
| `Carousel-Track-Container` | native `HorizontalPager` in `GeminiCatchCarousel` |
| `Card-Peek-Left` / `Card-Main-Active` / `Card-Peek-Right` | virtual previous, selected, and next pager pages with dedicated semantics tags |
| `Footer-CTA-Section` | bottom `GeminiFooterSection` |
| `CTA-Label-RecordNext` | dynamic CTA label above the capture control |
| `CTA-Camera-Button` | shared `HomeCameraButton` with registered raster assets; approved camera PNG is shown while masks load |

The Morning Lake master already contains the mountain-mist scene. The Compose layer preserves that artwork as one registered bitmap and does not add the prototype's unapproved atmospheric overlays or foreground reeds.

## Page assets and dynamic data

| Use | Registered source |
|---|---|
| Background | Morning Lake master copied byte-for-byte to `normal_home_runtime_v1/static/scene_base.png`, SHA-256 `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7` |
| Brand title | `brand_title_primary.png`, SHA-256 `2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc` |
| Camera | `normal_home_camera_primary_v1.png`, SHA-256 `fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2` |
| Fish card material | registered gradient, outline, and shadow runtime derivatives |
| Default avatar | original `normal_home_default_avatar_v2.png`, 1254×1254, SHA-256 `fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528` |

The page binds the real `species_count`, `catch_count`, `record_days`, `catch_image`, `species_name`, `length_cm`, `weight_kg`, `captured_at`, `location`, and `catch_id` values. The screenshot-matching evidence fixture uses 3 species, 3 catches, 1 record day, and the first 草鱼 record at 42.6 cm / 1.28 kg / 浙江 · 千岛湖 with the same registered `sample_recent_catch.jpg` photo as the baseline screenshot. Fish photos use `RemoteImage(preserveEvidenceWithFitBackdrop = true)`: the source photo remains fully visible with `Fit`, and a blurred crop of the same photo fills the side space. The photo bytes and fish subject are not edited or cropped.

## Avatar validation-page policy

The visible arrow/tower-like mark in PR #167's baseline API 28 screenshot (`NH01_Android_Runtime_1080x1920.png`, source `08ed847e711f8c7accd4ebba08a74e55e756882f`, run `39247751377`) was traced to the signed-in fallback branch: `HomeScreen` supplied the active session, `NormalHomeHeader` selected `NormalHomeDefaultAvatar`, and that function painted `R.drawable.nh07_default_avatar_3x_192` (SHA-256 `b79dc7eaa6da78722cedb240b35985871c422531b3d452efaa5fba9aa3eeb7c2`). At capture time that raster was visible; the screenshot cannot distinguish a missing URL from a still-pending or failed remote load. The separate old Guest branch used `NormalHomeRuntimeAssets.guestAvatar` → `normal_home_runtime_v1/avatar/guest_avatar.png` (SHA-256 `8a6219ee213af8693e7ca4ff39684fad607c0be891c6c5f31872f96a6ca0120b`). The Gemini runtime asset bundle does not load either old image.

For this NH01 Gemini V2 validation page only, both Guest and signed-in accounts without a usable remote avatar show the exact approved Home V2 bitmap named above. This is an explicit local visual-policy override: the existing global Home V2 and shared profile avatar manifests retain their separate Guest mapping. A usable signed-in remote avatar is drawn over the V2 fallback with circular clipping and `ContentScale.Crop`; while it loads, fails, or is replaced by another account, the V2 bitmap remains below it. URL, token, and signed-in state key the remote layer so logout or account switches cannot retain the previous account's image. Guest taps call login/register; signed-in taps call profile center.

The avatar is checked in three independent ways: the visual resource/crop, the circular visual boundary, and the clickable target bounds/action. The avatar button has a minimum 48dp target.

## Responsive rules

- Safe-drawing insets wrap the content; the scene bitmap remains edge-to-edge.
- The top section uses clamped vertical and horizontal insets and is capped at 560dp.
- The middle section receives the remaining column height. The active card width is `min(68.6% of track, 360dp)` and its height is capped at 480dp. Neighbor cards use `min(62% of track, 260dp)` and 67% of active height, capped at 340dp.
- In a short viewport, only the Middle section scrolls. The Footer remains in the bottom slot. Long screens do not stretch the card beyond its cap.
- Text uses height-responsive sizes, system font scaling, one-line ellipsis where the Gemini row is single-line, and 48dp minimum targets for actions.
- The capture target is at least 72dp, exceeding the 48dp minimum. The CTA label is laid out before it and cannot overlap the target.
- The horizontal catch pager owns horizontal gestures; vertical scrolling is confined to the Middle container.

## Intentional visual change

This is an intentional Gemini V2 page-tree rebuild. Its flex layout, carousel sizing, safe-inset handling, and bottom action slot differ from the previous Frozen coordinate composition. Frozen geometry is retained only as historical evidence. No Frozen Pixel Parity claim is made for this page.

## Evidence status

The exact-source CI report and APK artifact record the built HEAD, API 28 device run, APK SHA-256, run ID, runtime screenshots, Compose constraint screenshots, per-profile node Bounds, avatar transition evidence, and any visual review findings. Physical/device captures and Compose constraint simulations are labelled separately; any unexecuted physical profile is `NOT_RUN`.
