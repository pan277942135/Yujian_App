# Home Capture Activation Camera Runtime V2

User-approved frozen implementation input, 2026-10-31. Scope only 01 Home Capture, 02 activation and 03 Camera runtime. Reuse formal Android routes and shared YuJianCaptureButton; do not modify 04-10, model, Processing or RR09.

The five original Library images in 冻结高保 are the visual authority. Never publish whole-page references as app UI or reconstruct them as static screenshots. Measure normalized geometry. Preserve real record identity, photos, Home layout/background and existing short haptic. Home button 88-96dp, core58-64dp and glyph28-32dp subject to measured original proportions. Idle3000ms cycle with600ms local glint, scale1-1.012, no idle expansion.

V2 supersedes the earlier minimum1000ms looping activation: one750ms press/wake/outward+inward wave/intersection/settle activation, blue/gold/green weights40/40/20. Only one main wave,10-14dp outward,1-2dp strands,6-10dp feathering; no particles or decorative water plane. Upper Home content blur0-12dp and alpha1-0 over400-600ms, scenic background and action prompt remain clear. No Camera UI before activation completes. Then150-200ms shared-button geometry interpolation; CameraX first-frame readiness gates preview visibility, not activation loop. Waiting camera uses mint background and real UI, no black gap. Back cancels transition, resets idle and releases camera.

03 uses original mint wallpaper with aspect-preserving crop, deep-teal text, four white corners90%width/48-52dp arms/1.8-2dp line/12dp radius. Real preview extends outside guide and stops at independent bottom panel. Common CameraX viewport for real ImageCapture; no screenshots or guide-based crop. Default zoom1.0x pill76-84x40dp; reveal supported presets and draggable fine dial, share observed CameraX zoom and pinch. Panel approximately166dp plus actual bottom inset, translucent light mint82-88%,28-32dp top corners and subtle white edge. Shared shutter88-96dp, readiness effect half expansion5-7dp, real gallery thumbnail if accessible without broad permission otherwise neutral placeholder. Real hardware flash capability gates flash operation.

Required evidence:320x480,360x640,360x800,393x852,430x932 dp at font1/1.3/1.5; actual screenshots and continuous transition video, source/APK/Android resource decode, cold/warm launch, real camera capture JPEG/EXIF/viewport/zoom, gallery/flash, first-frame timestamps, lifecycle/rapid taps. Record PASS/FAIL/NOT_RUN honestly. Emulator camera evidence is not physical-device acceptance. Build and deliver one verified Debug APK with SHA256 and exact source.

## Frozen original identity

- `02 Capture Activation	点击激活、蓝金光丝.png`: 941x1672, SHA256 `5c9904243f1156f31945c1d83eefe67f6bccd33be388574e1fc46caf28e18960`
- `03 Camera Runtime	真实取景、操作区、变焦.png`: 942x1670, SHA256 `4d10908b2215a24b1bf3098a67576e223e6891a37705c7d95840c58332b809a9`
- `点击扩散：湖光金影动效分镜.png`: 1536x1024, SHA256 `f0855123d76ba3bac78f287094d7573fec98d81477d560221c069ae710e02b56`
- `朦胧薄荷青渐变壁纸.png`: 941x1672, SHA256 `501de58d9660fe8bfbe67baaed00dc59778c99c9cbada56982b83b9aed00770b`
- `01 Home Capture 首页默认按钮形态.png`: 864x1536, SHA256 `f529f5a8595572f02e9ea72f9a47f4f3f93aebb055699e2c9a210f087b379828`
