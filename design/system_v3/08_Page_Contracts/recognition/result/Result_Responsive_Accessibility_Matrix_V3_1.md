# Recognition Result Responsive / Accessibility Matrix V3.1

**Status:** FROZEN contract matrix  
**Contract result:** PASS  
**Runtime execution:** NOT_RUN (this phase excludes Android build/device testing)  
**Authority:** Result Frozen PNGs + Layout Contract + Shared Foundation.

| Dimension | Candidate Card | CTA | Metadata Row | Bottom Sheet | Status |
|---|---|---|---|---|---|
| 320dp | Single-column cards; text wraps without clipping; selector uses registered 320dp variant. | Equal-width pair uses available content width; actions remain vertically reachable. | Labels/values wrap in order; no horizontal clipping. | Content scrolls; title and confirm/close actions stay reachable above IME. | PASS |
| 360dp | Preserve canonical Frozen composition and candidate hierarchy. | Preserve state-specific Frozen CTA geometry and 12dp page gap. | Preserve registered row order/alignment. | Preserve registered sheet geometry and keyboard behavior. | PASS |
| 411dp | Keep content column capped at the 360dp Frozen authority width; do not scale card text or crop media. | Preserve equal-width CTA and page-owned spacing. | Keep row alignment; long values wrap. | Clamp to safe width and retain modal/focus behavior. | PASS |
| Large fontScale 1.3 / 2.0 | Candidate names and species labels grow/reflow; cards expand vertically. | Labels remain readable; content scrolls instead of shrinking text. | Values wrap and preserve label/value association. | Sheet scrolls; focused field and confirm/close actions remain available. | PASS |
| TalkBack | Candidate exposes species, suggestion/selected state and one action. | Save Catch / Continue Memory announce distinct names and enabled/loading state. | Each label/value pair is read together in declared order. | Modal title is announced; focus enters and returns to invoker; no focus trap. | PASS |
| Keyboard / IME | Candidate selection remains state-safe while IME is open. | CTA is not obscured; after IME dismissal it remains reachable without route change. | Focused row scrolls into view. | Back hides IME before dismissing; numeric fields use decimal keyboard; draft is preserved. | PASS |
| Orientation | Card width follows safe content bounds; vertical order is unchanged. | CTA stays reachable after vertical scroll. | Rows remain associated; no forced landscape redesign. | Sheet follows resolved width/insets and preserves focus. | PASS |

Touch targets use the Shared Foundation minimum of 44×44dp wherever actionable. PASS certifies contract coverage only; runtime screenshots, TalkBack, keyboard and orientation tests are NOT_RUN here.
