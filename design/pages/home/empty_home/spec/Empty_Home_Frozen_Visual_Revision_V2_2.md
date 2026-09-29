# Empty Home — Frozen Visual Revision V2.2

Status: **FROZEN / APPROVED**

Approved visual source:

- filename: `晨雾湖畔_静待第一条鱼.png`
- dimensions: `941 × 1672`
- SHA-256: `3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec`

## Authority rule

V2.2 is a **delta freeze over Empty_Home_Final_Design_V2**. The existing V2 canonical
scene remains authoritative for every pixel and behavior not explicitly listed below.
This revision freezes only the changes approved in the visual iteration: capture CTA
clarity/spacing and the rod-line-bobber-water-contact composition.

Reference coordinates below use the Android runtime canvas `1080 × 1920`.

## Capture CTA

- prompt top Y: `1448`
- camera bbox: `x=430, y=1537, w=220, h=220`
- album row top Y: `1780`
- prompt → camera clear gap: at least `25 px`
- camera → album clear gap: at least `23 px`
- camera stays horizontally centered
- retain the V2 glass / warm-gold-rim / pale camera-glyph treatment
- do not shrink the camera glyph or re-introduce the compact V2 spacing

## Rod, line, bobber and water contact

Rod:

- runtime layer origin: `(-96, 1172)`
- rod-tip anchor: `(335, 1180)`

Fishing line:

- start: `(335, 1180)` — exact rod tip
- cubic control 1: `(390, 1265)`
- cubic control 2: `(470, 1352)`
- end: `(560, 1328)` — below the bobber water-contact seam
- geometry: one smooth cubic Bézier with visible slack
- never render the previous tight/straight raster line

Bobber and ripple:

- bobber layer origin: `(548, 1236)`
- water contact: `(560, 1320)`
- ripple origin: `(441, 1279)`
- ripple center: `(560, 1320)`
- the bobber remains Y-only motion ±3 px / 4600 ms
- ripple remains 1.00 → 1.22, alpha 0.30 → 0, 3200 ms
- line endpoint remains visually tucked behind/below the bobber contact area

## Locked unchanged

Logo, subtitle, login/profile behavior, hero artwork, mountain/mist/sun/lake scene,
cloud/sun-particle behavior, recognition navigation, album navigation, Reduce Motion,
haptic behavior and all non-Empty-Home product logic remain unchanged from V2.
