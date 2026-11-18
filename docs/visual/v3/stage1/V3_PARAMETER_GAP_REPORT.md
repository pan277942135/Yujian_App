# YuJian V3 Stage 1｜Parameter Gap and Disposition

**Status:** CORE_VISUAL_SPEC_FROZEN_WITH_TUNABLES

| Item | Previous issue | Current disposition |
|---|---|---|
| KF source mapping | Contact sheet was mistaken for 26 independent masters | RESOLVED: all 26 process rows mapped to independent images, continuous-board frames, or explicitly reused behavior sources. The contact sheet remains a derived overview, not 26 source masters. |
| KF00 / KF01 | Only overview crops were referenced | RESOLVED: independent Home and Capture images added with dimensions and SHA-256. |
| KF14 | Return path continuity appeared missing | RESOLVED: reuse KF05 path identity and geometry; reverse direction. No separate image required. |
| KF25 | Save feedback appeared missing | RESOLVED: save success returns to KF00 Home; no separate save-success artwork required. |
| 12.2 Glyph Emergence | Not a standalone KF row | RESOLVED: six-frame Glyph board Frame 5, embedded between KF18 and KF19; no 27th KF. |
| Orb core geometry | Source and runtime asset correspondence | RESOLVED: transparent master and Runtime WebP alpha geometry measured; keep Runtime WebP byte-identical. |
| Orb screen anchor | Only contact-sheet approximate measurement | RESOLVED_APPROX: measured from three independent portrait references; use as composition target, not GPU uniform. |
| Resident identity / depth | Five paths could be confused with Task flow | RESOLVED: five Resident IDs and 2 BACK / 3 FRONT topology frozen; Task count remains dynamic. |
| Resident numeric curves | Old profile was pending review | CANDIDATE_ONLY: preserve old periods/phases/width/alpha with provenance; do not silently promote to frozen V3 values. |
| Internal fluid velocity | Static composites cannot reveal exact flow field | TUNABLE: freeze layered continuous-liquid visual behavior; calibrate numeric velocity/frequency in Stage 3. |
| Refraction / Fresnel / IOR | Cannot be uniquely inverted from composited PNG | TUNABLE / UNKNOWN: preserve optical layer order and visual constraints; calibrate on GPU. |
| Bloom | No approved numeric threshold/radius/intensity source | TUNABLE: preserve rim/fluid detail and avoid whole-ball flash. |
| Runtime Mask / P* | High-fidelity fish contour is only a visual reference | RUNTIME_ONLY: use trusted current-Attempt Mask; BBOX_ONLY cannot fabricate P*. |
| Old 3650ms timing | Historical overview conflicts with current contract | RESOLVED: retain 13-stage minimum budget total 6000ms and 60s abnormal timeout. |

## Rule

Stage 3 Orb-only GPU implementation may begin from the frozen core visual specification. Any numeric parameter marked CANDIDATE or TUNABLE must remain labeled in code/config and be included in the Stage 2 Evidence ZIP. No task energy, Mask/P*, contour, Glyph, or Result implementation is authorized in Stage 3.
