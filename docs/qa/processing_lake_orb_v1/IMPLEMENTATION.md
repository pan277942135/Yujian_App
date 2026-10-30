# Processing lake orb presentation V1

The approved 2026-10-31 prototypes are applied to the formal Processing route. The mint background and transparent core are independent resources; all photos are the current SelectedImage original. Detector, classifier and SlimSAM weights, thresholds and input pixels are unchanged.

## Resources

- Background source: user attachment `朦胧薄荷青渐变壁纸.png`, SHA-256 `501de58d9660fe8bfbe67baaed00dc59778c99c9cbada56982b83b9aed00770b`. Delivery WebP 720x1279, SHA-256 `3fa3afca777631380a42573eabd1ff21087a6c7c342211040f6cd183700a648f`.
- Orb source reference: `翡翠星光中的流光水晶球.png`, SHA-256 `910d15313b39ee2afa277356c46290e2cb7739b80a60e9ab4efaa14a29d74122`. Built-in imagegen extracted a transparent water core; WebP 384x384 with alpha, SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`.
- Extraction instruction: preserve the blue/teal glass-water core, internal refraction and local warm glint; remove background, bokeh, detached particles and exterior ribbons. The resulting raster is a material reference, while the orbital ribbons are native animated paths.

## Presentation

- One 900ms entrance clock: 0-150ms button press, 150-350ms pearl/icon dissolves into water core, 350-650ms independent ribbons open, 650-900ms arrival settles. Actual camera shutter and PreviewView bounds are passed as transient presentation metadata on the same SelectedImage.
- Captured preview bitmap only freezes the real PreviewView during CameraX save/decode. It is never saved as the recognition image or model input.
- Original photo enters over 700ms from measured preview bounds (camera) or contained centered original (album). One source transform feeds photo, fish focus and RR09 snapshot.
- Orb core refraction period 4.2s, blue ribbon 4.5s, gold 5.5s, green 6.5s. Front/back paths are split by depth. No whole-sphere raster spinning, no particles or dark rectangle.
- Layout supports 320x480, 360x640, 390x844, 412x915, 430x932 and 800x360. Full source is contained; portrait state follows actual image bottom, landscape state sits on the right. Core 68/80dp; ribbons bounded to 14dp outside core.
- Debug flow defaults to automatic. The top-right explicit step-diagnostic toggle preserves layer inspection and export logging. The existing trusted-mask gate and adaptive visual stages remain active.
- Model and visual readiness trigger 280ms orb contraction before RR09. Reduce Motion removes spatial entrance and contraction duration.

## Verification boundary

Targeted geometry/motion tests plus existing recognition tests, Android compile and lint are required. RUNTIME_NOT_RUN and VISUAL_ACCEPTANCE_PENDING until real-device installation, actual JPEG/preview comparison and recorded visual review. Build or static evidence is not a native visual PASS.
