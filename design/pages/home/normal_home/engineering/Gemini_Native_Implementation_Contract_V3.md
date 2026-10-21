# NH01 Gemini Native Home V3 Contract

**Task:** `YUJIAN_NH01_GEMINI_NATIVE_REBUILD_AVATAR_FIX_V3`
**Android PR:** #167, Draft / Open / Unmerged
**Structure handoff:** PR #164, HEAD `06308bd21e175e321e9f345dbf18174f5e0134f9`
**Implementation starting HEAD:** `25b8c4d50b2d979e380dd76bdb38cde8b80f5f2c`

This revision supersedes the V2 layout/runtime behavior for the active Gemini Normal Home route. It retains the same approved bitmap sources and the avatar-specific V2 policy override.

## Authority and implementation boundary

The implementation follows the Gemini split-page tree recorded in PR #164's `NH01_GEMINI_STRUCTURE_ALIGNMENT_V1.json` and `NH01_GEMINI_REFERENCE_PROTOTYPE_NOT_VISUAL_PASS.html`. The raw Gemini input is `粘贴的文本 (1)(6).txt`, 18,331 bytes, SHA-256 `1988a9343a6e911c87f93871279ba6d5e161e2ef0651ecd0d783526c5bceb714`. The prototype is structure-only; neither it nor this native implementation claims a Figma live-node readback or a Frozen Pixel Parity pass.

`HomeScreen` routes the populated Normal Home state to the standalone `GeminiNormalHomeContent.kt` Compose tree. This tree owns its background, Top, Middle, and Footer containers. The previous `NormalHomeContent` implementation remains only as an uncalled rollback reference. It is not a parent, measurement source, positioning helper, or layout dependency of the Gemini page.

## Gemini layer map

| Gemini layer | Native Compose mapping |
|---|---|
| `Screen-Yujian-Home` | `GeminiNormalHomeContent`, tag `gemini-home-screen` |
| `BG-Scenic-Group` | full-screen background group, tag `gemini-layer-bg-scenic-group` |
| `BG-Mountain-Mist` | official Morning Lake bitmap in top-centered `ContentScale.Crop`, tag `gemini-layer-bg-mountain-mist` |
| `Top-Section-AutoLayout` | `GeminiTopSection`, tag `gemini-home-top-section` |
| `Header-Bar` | `gemini-home-header-bar` |
| `Brand-Title` | registered `R.drawable.brand_title_primary` |
| `User-Avatar-Btn` | `GeminiAvatarButton`; guest and signed-in actions have separate callbacks |
| `Stats-Bar` | `GeminiStatsBar`; server totals and local record-day fallback |
| `Middle-Carousel-Section` | weighted `GeminiMiddleSection`; short-height vertical scroll is local to this section |
| `Section-Header-Bar` | `GeminiSectionHeader` |
| `Carousel-Track-Container` | native `HorizontalPager` in `GeminiCatchCarousel` |
| `Card-Peek-Left` / `Card-Main-Active` / `Card-Peek-Right` | finite previous, selected, and next pages from the real catch list, with dedicated semantics tags |
| `Footer-CTA-Section` | bottom `GeminiFooterSection` |
| `CTA-Label-RecordNext` | dynamic CTA label above the capture control |
| `CTA-Camera-Button` | shared `HomeCameraButton` with registered raster assets; approved camera PNG is shown while masks load |

The Morning Lake master already contains the mountain-mist scene. The Compose layer preserves that artwork as one registered bitmap, aligns the crop to TopCenter for short-window framing, and does not add the prototype's unapproved atmospheric overlays or foreground reeds.

## Page assets and dynamic data

| Use | Registered source |
|---|---|
| Background | Morning Lake master copied byte-for-byte to `normal_home_runtime_v1/static/scene_base.png`, SHA-256 `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7` |
| Brand title | `brand_title_primary.png`, SHA-256 `2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc` |
| Camera | `normal_home_camera_primary_v1.png`, SHA-256 `fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2` |
| Fish card material | registered gradient, outline, and shadow runtime derivatives |
| Default avatar | original `normal_home_default_avatar_v2.png`, 1254×1254, SHA-256 `fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528` |

The page binds the real `species_count`, `catch_count`, `record_days`, `catch_image`, `species_name`, `length_cm`, `weight_kg`, `captured_at`, `location`, and `catch_id` values. The screenshot-matching evidence fixture uses 3 species, 3 catches, 1 record day, and the first 草鱼 record at 42.6 cm / 1.28 kg / 浙江 · 千岛湖 with the same registered `sample_recent_catch.jpg` photo as the baseline screenshot.

The pager has exactly one page per distinct valid stable `catch_id`: counts 0, 1, 2, 3, and 10 are covered; older duplicate IDs collapse to the newest record; there are no cloned end pages. Page count, current ID, and position are exposed through Compose semantics. Pager stable keys retain the selected record when items are inserted or reordered, and click callbacks return the selected record's ID. Previous/next gestures stop at finite boundaries.

Fish photos use `RemoteImage(preserveEvidenceWithFitBackdrop = true)`: the source photo remains fully visible with `Fit`, and a blurred crop of that same decoded source fills the remaining area. While blur is prepared, a same-source `Crop` fills the background so no flat gray letterbox appears. The crop is behind the unchanged fitted photo; source bytes, fish subject, and foreground dimensions are not edited or cropped. Successful decoded sources are held in a bounded process memory cache whose key hashes (and does not retain) the authorization token.

## Avatar validation-page policy

The visible arrow/tower-like mark in PR #167's baseline API 28 screenshot (`NH01_Android_Runtime_1080x1920.png`, source `08ed847e711f8c7accd4ebba08a74e55e756882f`, run `39247751377`) was traced to the signed-in fallback branch: `HomeScreen` supplied the active session, `NormalHomeHeader` selected `NormalHomeDefaultAvatar`, and that function painted `R.drawable.nh07_default_avatar_3x_192` (SHA-256 `b79dc7eaa6da78722cedb240b35985871c422531b3d452efaa5fba9aa3eeb7c2`). At capture time that raster was visible; the screenshot cannot distinguish a missing URL from a still-pending or failed remote load. The separate old Guest branch used `NormalHomeRuntimeAssets.guestAvatar` → `normal_home_runtime_v1/avatar/guest_avatar.png` (SHA-256 `8a6219ee213af8693e7ca4ff39684fad607c0be891c6c5f31872f96a6ca0120b`). The Gemini runtime asset bundle does not load either old image.

For this NH01 Gemini V3 validation page only, both Guest and signed-in accounts without a usable remote avatar show the exact approved Home V2 bitmap named above. This is an explicit local visual-policy override: the existing global Home V2 and shared profile avatar manifests retain their separate Guest mapping. A usable signed-in remote avatar is drawn over the V2 fallback with circular clipping and `ContentScale.Crop`; while it loads, fails, or is replaced by another account, the V2 bitmap remains below it. URL, token, and signed-in state key the remote layer so logout or account switches cannot retain the previous account's image. Guest taps call login/register; signed-in taps call profile center.

The avatar is checked in three independent ways: the visual resource/crop, the circular visual boundary, and the clickable target bounds/action. The avatar button has a minimum 48dp target.

## Responsive rules

- Safe-drawing insets wrap the content; the scene bitmap remains edge-to-edge.
- The top section uses bounded vertical and horizontal spacing and is capped at 560dp. Its brand/avatar row, stats row, section title, card and footer remain grouped in the Gemini Top / Middle / Footer hierarchy. Stats separators are 26dp tall.
- The active card always preserves the Gemini reference ratio `247:293` (`width / height = 0.843`). **Adaptive V2 is the default:** target width is `min(70% of track, 337.2dp)`; target height derives from that width and is capped at 400dp. Its readable minimum is calculated from the font-scaled line budget for species, up to two measurement lines, up to two metadata lines, footer padding and font-metric safety allowance, bounded to 148–220dp. Final height is `min(desiredHeight, max(minReadableHeight, availableCardHeight))`; it never restores an oversized desired height when space is tight. V3 Baseline retains its 68.6% / 337.2dp target and 220dp comparison floor.
- Adaptive V2 uses continuous font-pressure and short-height-pressure inputs for bounded spacing instead of a band × font-scale lookup table. The title-to-card visible gap is still calculated from Compose's measured text height and platform font metrics. V3 Baseline retains its 13dp visible title gap. Neighbor cards use `min(62% of track, 260dp)`, remain 66.7% of the main card height, and are translated so their visible top edge is 15dp below the active card's top edge.
- When the complete group fits, Adaptive V2 centers it in the remaining Middle space. If the readable minimum cannot fit, only the Middle section scrolls and is top-aligned; the Footer remains outside the scroll viewport in the bottom slot. The card's aspect ratio and text sizing are not sacrificed to make the screen appear to fit. Hero footer content uses card-height-derived padding so all three metadata rows stay inside the card.
- Bottom action padding derives from safe-drawing insets: legacy three-button navigation keeps a compact inner clearance; gesture navigation retains the system inset plus a visible breathing gap. Camera artwork uses `min(min(safeWidth, 560dp) × 0.18, safeHeight × 0.10, 80dp)`, bounded to 56–80dp; the independent touch target is `max(72dp, visualSize)`. Runtime evidence records the artwork bounds separately from the touch bounds. CTA text stays above the target.
- Text uses height-responsive sizes, system font scaling, one-line ellipsis where the Gemini row is single-line, and 48dp minimum targets for actions.
- The finite horizontal catch pager owns horizontal gestures; vertical scrolling is confined to the Middle container.

### Fish photo framing boundary

The responsive card ratio removes the unbounded-tall-card failure that created top/bottom filler bands on long screens. The Home card still follows `Hero_Real_Photo_EvidenceFit_Contract_V1.md`. The current Home data has no trusted fish bounding box or per-record crop-safety signal, so the runtime continues to use `EVIDENCE_FIT`: the complete original photo over its same-source soft crop. A future crop path needs verified crop-safe fixtures; the recommendation's unconditional `centerCrop`, Unsplash demo photo, and hand-drawn camera SVG are not used.

## Intentional visual change

This is an intentional Gemini V3 page-tree rebuild. Its flex layout, finite carousel sizing, tighter top spacing, safe-inset handling, and bottom action slot differ from the previous Frozen coordinate composition. Frozen geometry is retained only as historical evidence. No Frozen Pixel Parity claim is made for this page.

## Evidence status

The exact-source CI report and APK artifact record the built HEAD, API 28 device run, APK SHA-256, run ID, runtime screenshots, Compose constraint screenshots, per-profile node Bounds, avatar transition evidence, finite carousel cases, and any visual review findings. Physical/device captures and Compose constraint simulations are labelled separately; any unexecuted physical profile is `NOT_RUN`. Required physical captures are 1080×1920, 1080×2340 display override, and 720×1280 emulator override; Compose simulations cover 320×480, 360×640, 390×844, 430×932, and 480dp width at font scales 1.0, 1.3, and 1.5.
