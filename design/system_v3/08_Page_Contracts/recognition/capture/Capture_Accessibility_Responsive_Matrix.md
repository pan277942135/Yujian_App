# Recognition Capture Accessibility / Responsive Matrix V3.1

**Status:** FROZEN contract matrix  
**Contract result:** PASS  
**Runtime execution:** NOT_RUN (this phase excludes Android build and device testing)

| Dimension | Contract check | Status |
|---|---|---|
| 320dp | Flexible preview preserves source ratio; overlays stay in safe content bounds; fixed Bottom Action Area stays above navigation inset without overlap. | PASS |
| 360dp | Canonical width; preview crop follows the runtime stream; Capture and Gallery retain registered roles. | PASS |
| 411dp | Stress width within the 412dp foundation reference; controls stay anchored to the content root and preview is not stretched. | PASS |
| fontScale 1.0 | Title, state feedback and action labels use registered typography and remain readable. | PASS |
| fontScale 1.3 | Text containers may grow; actions remain reachable and do not overlap the fixed action area. | PASS |
| fontScale 2.0 | Content reflows/scrolls; actions remain reachable without shrinking/cancelling OS font scale. | PASS |
| TalkBack | Focus order is Top Title/Back → preview state → Gallery → Capture; state changes are announced once, not per frame. | PASS |
| Touch target | Back, Gallery and Capture actionable bounds are each at least touch.minimum = 44×44dp. | PASS |

PASS certifies that the contract defines the required cases and measurable conditions. It does not claim Android rendering or device execution.
