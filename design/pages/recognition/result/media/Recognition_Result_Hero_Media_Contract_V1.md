# Recognition Result Hero Media Contract V1.2

Status: **FROZEN — MEDIA POLICY UPDATE**

Machine-readable authority: `hero_media_contract.json`.

This V1.2 contract supersedes conflicting V1.1 media fallback rules. Existing state-specific Result layout and controls remain unchanged. The Hero width responds to available screen content width, while each result state retains its own approved aspect ratio. Photo orientation must not switch the ratio.

## High / Medium / Low

1. Start from the original photo after EXIF rotation/mirroring is normalized.
2. Use a verified detector BBox in the same oriented-source coordinate space.
3. Expand around the BBox, initially 10% horizontally and 12% vertically.
4. If one side reaches the source edge, stop expansion on that side and transfer the missing expansion amount to the opposite side.
5. If the crop window is still infeasible, retry margins in this order: 10%/12%, 8%/8%, 4%/4%, 0%/0%.
6. Use a crop only if the complete valid BBox is contained, the target ratio is exact, and the crop window remains within the source after pixel rounding.
7. If all safe crop profiles fail, use the complete oriented source as a foreground layer and a low-contrast blurred cover of the same source as the background.

A missing, invalid, or untrusted BBox must not trigger aggressive center crop. It directly uses the same-source background fallback with the complete source foreground.

## No Fish / Image Quality

Do not infer a fish location and do not run fish-BBox crop. Keep the complete oriented original photo visible in the foreground and use a same-source blurred background to fill the rest of the fixed state-specific Hero container. This removes blank/solid borders without hiding evidence or pretending that the background is new source content.

## Responsive geometry

- Keep existing state-specific Hero aspect ratios and surrounding layout.
- Width follows the available window content width and current page insets.
- Do not switch the Hero aspect ratio based on portrait/landscape photo orientation.
- On short screens or enlarged system fonts, allow content scrolling; keep primary actions reachable.
- Image loading and cache changes must not produce a layout jump.

## Shared invariants

- Original source bytes remain unchanged; all crops/composites are presentation derivatives.
- No stretch, generated fill, outpainting, fish repair, detector/classifier input crop, or solid letterbox.
- No post-entry crop jump when candidate selection changes.
- EXIF orientation and BBox coordinate transforms must agree.
- Static CSS examples are not Android runtime evidence.
