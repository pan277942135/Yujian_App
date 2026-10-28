# NH01 · Avatar Visual V1.1 — FROZEN

**Contract ID:** `NH01_AVATAR_VISUAL_V1_1`  
**Status:** `SPEC_FROZEN` (the visual decision is frozen; Android Runtime PASS is not yet claimed).  
**Applies only to:** Normal Home · Gemini Native V3 / Adaptive V2 / top-right `User-Avatar-Btn`.  
**Approved choice:** 方案一 · 轻改 / 薄雾玻璃 / 极简人物线描. Do not substitute the branded-wave or real-photo-only alternative.

## 1. Authoritative tokens

| Property | Frozen value |
| --- | --- |
| Artwork diameter | `clamp(safeWidthDp × 0.10, 36dp, 40dp)` |
| Tap bounds | Minimum `48 × 48dp`, independent of artwork |
| Background | `#EEF3F2`, 36% opacity over the actual lake scene |
| Rim | One circle, white / 48% alpha / `0.8dp` |
| Person | Line-drawn head circle and single shoulder curve, `#214454` |
| Line | `1.65dp`, round ends and joins |
| Shape | Circular clip |
| Position | Header right, vertically centered with `brand_title_primary` |
| Prohibited | Thick opaque white rings, shadow, glossy highlights, HUD, neon, animated decoration |

Use a Compose `Canvas` vector, not a synthesized portrait or photo asset. Preserve recorded camera and card authorities.

## 2. Identity and interaction states

- Guest → quiet mist avatar → login/register.
- Signed-in, no valid avatar → identical mist avatar → profile center.
- Signed-in, photo loaded → circular user photo over mist fallback, retaining the single thin rim → profile center.
- Loading/error → mist fallback behind the remote image, no flashing, no old-account image.
- Account switch/logout → key remote content on signed-in state, URL and access token; clear old photo.

Button accessibility keeps state labels and role. Decorative fallback illustration has no additional speech semantics.

## 3. Authority and preservation

- Implementation: `app/src/main/java/com/yujian/ai/ui/home/GeminiNormalHomeContent.kt`, `GeminiHomeAdaptiveLayoutPolicy.kt`.
- Parent implementation contract: `design/pages/home/normal_home/engineering/Gemini_Native_Implementation_Contract_V3.md`, avatar paragraph superseded only.
- Retain `app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png` **unchanged**, SHA-256 `fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528`. Keep legacy rollback and other-page/global avatar manifests unchanged.
- Do not change hero sizing, carousel geometry, camera, footer, CTA, backend, assets, or user session logic.

## 4. Acceptance gate

1. Pixel-compare before/after of original screenshot on the **same real Android** viewport; differences limited to top-right avatar.
2. Test `320×480`, `360×640`, `390×844`, `430×932` at `1.0× / 1.3× / 1.5×` font: visual width 36–40dp, tap >=48dp, correct header alignment and safe area.
3. Confirm Guest/login, signed-in/profile, valid remote photo, bad remote image, logout/account switch, no stale pixels; preserve circular crop and thin rim.
4. Record commit SHA, build and test outcomes, APK SHA-256, `boundsInWindow` and native screenshots. Browser preview or build-only success is not Runtime PASS.
5. PR remains **Draft/Open/Unmerged**; merge and release require separate approval.

**Change control:** Freeze the visual tokens/states. Any future change requires an explicitly reviewed contract revision.
