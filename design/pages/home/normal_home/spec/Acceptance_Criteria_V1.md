# Normal Home Acceptance Criteria V1

> **Implementation and independent acceptance binding:** [engineering/Visual_Runtime_Acceptance_V1.md](../engineering/Visual_Runtime_Acceptance_V1.md) and its 38-case machine matrix own executable IDs, native evidence schema, profile matrix, and PASS/FAIL/BLOCKED_INFRA taxonomy. This V1 list is the design-level intent and must not independently imply runtime PASS.

A design or implementation is conformant only when all applicable criteria pass.

## A. Authority

- canonical reference path matches `normal_home_v1.png`
- SHA-256 matches the registered frozen source
- no unregistered screenshot crop is treated as source authority
- shared component contracts are not forked

## B. State and behavior

- one or more valid FishRecords selects Normal Home
- unresolved initial archive load does not select Empty Home
- valid record requires a nonblank ID and a species ID or name; image failure does not invalidate it
- refresh loading/error retains the last successfully resolved Home state
- authentication does not select Home state
- recent records are newest-first by resolved timestamp
- one-record and multiple-record states preserve the same hierarchy
- multiple records are user-swiped, never auto-advanced
- catch tap opens FishRecordDetail
- 鱼种 opens Fish Guide
- 鱼获 and 全部 open My Catches
- 记录天数 has no V1 navigation
- 记录天数 has no click semantics, ripple or haptic
- primary capture uses the existing identify flow

## C. Visual

- real catch outranks statistics
- page reads as morning-lake memory before dashboard
- hero card uses dynamic real media
- shared capture button visual language is preserved
- gold remains scarce
- no HUD/gamification drift
- taller ratios preserve hierarchy without stretching the hero card
- runtime screenshots are captured at actual target dimensions; no screenshot is resized to pass

## D. Motion

- no page-wide synchronized looping
- selected hero micro-breath stays within the frozen limits
- pager transforms are user-driven
- capture-button motion uses the shared contract
- Reduce Motion stops decorative looping

## E. Haptic and sound

- no automatic haptic on entry/idle
- no haptic coupled to hero breathing
- no ambient or UI sound is introduced
- camera haptic inherits the shared capture-button convention only

## F. Evidence target

The existing Normal Home runtime gate remains the implementation evidence path:

- 1080×1920 frozen-geometry capture
- 19.5:9 single-record state
- 20:9 multiple-record state
- 21:9 multiple-record state
- frozen/runtime side-by-side
- 10-second motion evidence

Design closure itself does not claim a new runtime PASS.
