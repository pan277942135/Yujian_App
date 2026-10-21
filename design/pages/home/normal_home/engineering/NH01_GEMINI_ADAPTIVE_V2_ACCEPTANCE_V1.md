# NH01 Gemini Native V3 · Adaptive V2 — 统一验收 V1

Status: SPEC_FROZEN / RUNTIME_PENDING. Scope and conflicts: design/pages/home/normal_home/engineering/NH01_GEMINI_ADAPTIVE_V2_UNIFIED_AUTHORITY_V1.md. Machine matrix: design/pages/home/normal_home/engineering/NH01_GEMINI_ADAPTIVE_V2_ACCEPTANCE_MATRIX_V1.json.

## Required profile matrix and data integrity

Actual API28 native 320x480 / 360x640 / 390x844 / 430x932 dp x fontScale 1.0/1.3/1.5 x ADAPTIVE_V2/V3_BASELINE = 24 exact-git-SHA executions. Record APK and test APK SHA256, physical window screenshot dimensions/hash, actual WM overrides, density, safe insets, profile/fontScale, source commit, selected record ID, before+after swipe screenshot, all component boundsInWindow, text line ranges, pager/card/CTA/camera geometry. Host aggregator must refuse missing screenshot, missing case, missing actual device setting, nonmatching SHA or missing result. Static tests/CI build/HTML are not visual PASS.

## Mandatory scoped assertions

| Case | Acceptance and evidence |
| --- | --- |
| AUTH-01/02 | Typed current/legacy authority, no Frozen/binary asset mutation; original 46 matrix preserved |
| SP-01 | Recent TITLE TEXT layout bottom -> selected HERO TOP: 8/12/18/20dp ±2 (320/360/390/430) at 1.0x; not self-reference to previous policy. V3 Baseline remains control |
| SP-02 | Six adjacent interval bounds and screenshot review; no text crowding, unusable whitespace, or interactive overlap |
| GEO-01/02 | 247:293±1%, centered±2dp, capped hero, finite Pager, neighbor width<=82%, top+15dp, no >1dp overlap, real left/right peeks |
| SHORT-01 | 124dp floor ONLY 320x480/1.0 if width>=145dp, no Middle scroll or text clipping; other floor>=148dp; safe Footer+camera |
| TYPO-01 | system fontScale preserved, font-role sp and main card width non-decreasing on growing viewport profiles |
| TEXT-01/02 | Full address example ending 321号, time always first, no lone final 号 or punctuation, max 2 context lines; species and positive measurements complete with proper units; no fake missing values |
| PHOTO-01/02 | True fish photo identity and source hashes, safe BBox or whole-source seamless background; F01-F08 genuine originals, HOME+DETAIL evidence |
| AVATAR-01/BG-01 | Mist visual size 36-40dp, touch>=48dp; Guest/Login and signed-in/Profile, photo load/failed/logout; original Morning Lake TopCenter source+hash |
| STATE-01/PAGE-01 | Unresolved != Empty, refresh/error retain valid previous state, one-catch NH02 and finite multi-catch NH01 correct ID/taps |
| MOTION-01/A11Y-01 | Hero idle keyframes 0/3/6s, 16s camera sweep evidence, Reduce Motion, no page audio/extra haptic; hitbox, insets, RTL |
| EVID-01/02 | 24 fully paired same-SHA native executions AND crosswalk of applicable original 46 G/V/S/M/F cases; independent signoff |

## Anti-orphan test

Fixture: 今天 19:43 · 上海市青浦区岑卜村321号, longer Chinese/Latin/punctuation variants, time only, no location, no measurement, 1.0/1.3/1.5 fonts. Inspect actual TextLayoutResult line ranges AND Android screenshot. A last single Chinese character like 号, dangling separator, lost time or glyph clipping fails despite hasVisualOverflow=false. Prefer shorten/ellipsis of optional location; never modify original saved data.

## Exception and verdict rules

Legacy 46-case matrix stays unchanged; legacy A1/A2/center Crop/pixel composition conflicts are annotated SUPERSEDED_FOR_GEMINI in separate applicability crosswalk, NOT as PASS. SPEC_FROZEN is design only. PASS requires the exact APK/source native evidence and independent review. FAIL is measured violation. NOT_RUN is absent execution. BLOCKED_INFRA requires actual failed runner attempt/log. REVIEW_REQUIRED requires evidence of ambiguous visual decision. User authorizes specs and gates, NOT main merge or release.
