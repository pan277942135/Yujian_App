# Frozen 01–03 implementation V2

Originals resolved directly from 冻结高保, SHA256/dimensions in frozen_resources.json. Full-page images are private references, not app backgrounds. Camera wallpaper is a 720x1279 aspect-preserving derivative of original501de58d...; shared transparent button remains the validated approved WebP restored by PR278.

Observed gaps: immediate navigation truncated Home activation;620ms camera flight plus1000ms loop conflicted with latest750+180ms contract; old dark camera base/panel, fake scenic gallery thumbnail and always-visible zoom presets diverged from03; old arc trigonometry placed most ticks outside its Canvas. Source now uses one shared button,750ms Home activation,500ms information-only fade/blur,180ms flight, independent150ms real-preview fade, original mint wallpaper/light panel, capability-filtered presets and logarithmic real zoom dial.

Physical camera color/flash/preview-FOV/JPEG boundary and visual acceptance require product-owner device testing; emulator observations are recorded separately. No Processing/RR09/model changes.


First exact-APK native run41169217525 exposed a real320x480 Home capture/album collision: capture[116,325]-[204,413], album[116,408]-[204,456]. Repair reserves8dp clearance and the actual font-scaled prompt line before laying out the enlarged CTA. It keeps the original center whenever it fits; no scenic/header/card structure changes. A single root-level shared button replaces two page-local copies during crossfade. The03 guide preserves its measured original680/850 height-to-width ratio; quick presets and fine dial are separate requested modes so compact screens do not stack both over the title.
