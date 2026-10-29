# Login V2.1 shared structured candidate

Page ID: `auth_login_v2` · authority version: `V2.1` · lifecycle: `CANDIDATE / REVIEW_REQUIRED`.

This candidate is mounted from the existing Design Manager route:
`#page/auth_login_v2/hifi/structured_workspace`.

The frozen Login raster remains a visual comparison authority. It is not a renderer background. The shared Morning Lake BG_CONTENT master remains the only required rendering image bound to this candidate.

The W1 formal matrix contains one 360×640dp baseline case. A separate, explicitly non-frozen candidate matrix contains 12 CSS-pixel viewport/font-scale cases. Browser CSS pixels are not asserted to equal Android dp.

Current verification runs through `scripts/design/verify_login_structured_hifi.mjs` against the staged `_site` bundle and exact candidate SHA. It records the shared DOM component tree, Inspector selection, measured bounds, formal and supplemental cases, interaction states, fail-closed probes, recovery behavior, screenshots, and a native-size frozen-image comparison.

Open authority states remain unchanged:

- Shared icon files are present in the icon registry, but Login-specific publication authority is `NOT_CONFIRMED_IN_LOGIN_W2_BINDING`.
- The footer grass source is `NOT_VERIFIED` / `ASSET_SOURCE_REQUIRED`; the candidate CSS expression is not a frozen asset.
- The approved original upload source is `MISSING` and remains optional; the frozen repository raster remains the comparison authority.
- Safe-area ownership and Android IME behavior are `NOT_VERIFIED`.
- Product visual acceptance remains `USER_VISUAL_ACCEPTANCE_PENDING`; native Runtime remains `NOT_VERIFIED`.

No standalone preview page or second design workspace is part of this candidate.

All browser reports and screenshots are bound to the exact W4 source SHA and GitHub Run ID. The image comparison is measurement evidence only and does not grant visual acceptance.
