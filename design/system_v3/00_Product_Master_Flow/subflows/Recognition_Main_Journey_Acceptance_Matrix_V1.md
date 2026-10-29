# Recognition Main Journey · Acceptance Matrix V1.0

**Contract status:** FROZEN · **Execution status at freeze:** NOT_RUN (implementation gates are separate).

| ID | Setup / action | Required assertion | Expected record delta |
|---|---|---|---:|
| J01 | Empty Home has zero valid FishRecord; tap camera | Opens Capture and creates one temporary Recognition Session | 0 |
| J02 | Camera permission allowed, denied, permanently denied; try Gallery in each case | Permission is requested only after camera intent; denial does not block system Photo Picker | 0 |
| J03 | First camera entry; Preview ready; guide flag false | Guide appears once only after Preview Ready; no early shutter or touch-through | 0 |
| J04 | Tap Skip then repeat separately with Start Capture | Both close guide and persist completion; neither captures without a separate shutter tap | 0 |
| J05 | Capture and valid Gallery image, including orientation normalization | Exactly one normalized image and new imageId/attemptId enter Processing | 0 |
| J06 | Back during each Processing phase; delay/call old completion afterward | Recognition cancels; stale callback cannot navigate or replace current state | 0 |
| J07 | Deterministic High result | Top-1 editable; initial CTAs idle; explicit Save intent required | 0 until confirmed; then +1 |
| J08 | Deterministic Medium result; tap no action, candidate, Other, and cancel selector | No implicit confirmation; selected candidate/other explicit commit enables saving | 0 until confirmed; then +1 |
| J09 | Deterministic Low result; edit metadata then attempt save without fish; manually select a species | Unresolved fish cannot save; fields survive selector cancel and confirmation | 0 until confirmed; then +1 |
| J10 | Real/deterministic NO_FISH fixture | RR04 state/copy; Retake/Gallery; no Image Quality misclassification or save | 0 |
| J11 | Quality Gate INVALID fixture, then WARNING fixture | Only actual INVALID (and not NO_FISH) reaches RR05; WARNING does not | 0 |
| J12 | Inject real pipeline/model/crop failure fixture | Technical Failure is distinct from 3+2, does not blame image, offers recovery only | 0 |
| J13 | From Result/Issue tap Back and “重新拍摄”; select/capture a second image | Both lead to Capture; Retake clears old result and requires a new imageId/attemptId | 0 |
| J14 | Change species/length/weight/location/story; cancel editor without change; attempt Back/Retake | Material changes prompt discard; cancel/no-change does not; continue preserves draft/focus | 0 |
| J15 | Delay save response; double tap save and system Back | One in-flight request only; navigation and repeat save blocked while Saving | 0 until verified |
| J16 | Definite save failure, upload error, 401, and ambiguous timeout | Draft and destination survive; identity recovery returns to draft; unknown outcome checks same key before retry | 0 or +1 only after lookup proves prior success |
| J17 | Save Catch from High and confirmed Medium/Low | Exactly one durable record, reread/validation succeeds, Home refreshes to NH02 when first | +1 |
| J18 | Continue Memory from High and confirmed Medium/Low | Exactly one durable record; correct detail opens with `section=memory` | +1 |
| J19 | After either successful save, press system Back from destination | Old Result, Processing and Capture are absent from back stack | unchanged |
| J20 | Cancel, abandon, process interruption before confirmed save, No Fish, Image Quality, Technical Failure | No silent persistence; Home record count remains unchanged | 0 |

## Test evidence rules

- Record one result per J ID: `PASS`, `FAIL`, `BLOCKED_INFRA`, `BLOCKED_ACCESS`, or `NOT_RUN` with exact commit, test/device, and evidence path.
- Use deterministic fakes for route/business branches. Use the authorized real-catch fixture only when validating the actual model/pipeline; never substitute generated art as recognition accuracy evidence.
- Verify record count and record identity before/after every save branch. A toast, CTA animation, network request start, or local screen transition is not persistence evidence.
- Runtime screenshots/logs must identify the APK SHA-256 and current attempt; do not expose private image metadata in published diagnostics.
