# Recognition Capture · Camera Permission Denied · Visual Contract V1

Status: **FROZEN**
Page/state: `Recognition Capture → CAPTURE_PERMISSION_DENIED`
Design family: **Morning Lake / BG_CONTENT**

## 1. Scope

This is **one visual state/page only**.

There is no separate visual page for:
- first denial;
- permanent denial;
- photo-library permission.

The Android system Camera permission dialog remains platform-owned and is not reproduced by Yujian.

## 2. Background

Use the frozen Background System V1 preset:

- Master: `Morning_Lake_Master_V1`
- Preset: `BG_CONTENT`
- MistWhite veil: 15%
- Saturation: 91%
- Contrast: 89%
- Brightness: 103%
- Global Blur: OFF

Do not generate a new lake/background world.

## 3. Page composition

The state remains inside Recognition Capture.

Required visual elements:
- Back navigation;
- concise camera-permission-unavailable message;
- one camera-recovery CTA;
- one gallery fallback action;
- disabled camera shutter/capture affordance.

Recommended frozen copy:
- Title: `还没有相机权限`
- Supporting text: `开启后就能直接拍照识鱼`
- Recovery CTA: `去系统设置`
- Gallery action: `从相册选择照片`

The recovery CTA is runtime-adaptive:
- when Android still allows another permission request, the same CTA slot uses `重新开启相机` and invokes the platform Camera permission request;
- when another request is no longer available, it uses `去系统设置` and opens the platform app-permission settings.

The visual page does not split into two designs.

## 4. Gallery behavior

`从相册选择照片` invokes the platform Photo Picker / Content Picker.

No separate photo-library permission page is designed.

## 5. Prohibited treatments

- no custom imitation of Android permission dialogs;
- no black error-modal treatment;
- no neon/HUD/game styling;
- no template-heavy card stack;
- no second permission page;
- no separate first-denial/permanent-denial visual;
- no new background source.

## 6. Runtime rules

State: `CAPTURE_PERMISSION_DENIED`

- Camera capture is blocked.
- Shutter must be disabled.
- Gallery remains available.
- Back remains available.
- Active first-use guide P01/P02 must not appear while permission is denied.
- Runtime must use structured Compose UI; the frozen raster is visual authority only.

## 7. Frozen raster authority

Source file: `湖畔相机权限提示界面.png`
Dimensions: **941 × 1672**
Bytes: **1,794,959**
SHA-256: `1feced3a5d6f7a7d3ef968edcf8a5a834d216fab40707cf84c31658be5417d06`

The source-render hash above is retained as the design-authority fingerprint. Any repository preview/export used for distribution must not be treated as a replacement authority unless it matches the approved visual composition.

## 8. Freeze gate

- [x] Single page/state defined
- [x] BG_CONTENT binding defined
- [x] Platform permission dialog excluded from custom design
- [x] Recovery CTA behavior defined
- [x] Gallery fallback defined
- [x] Capture blocked state defined
- [x] First-use guide excluded while denied
- [x] Frozen source dimensions/hash recorded
