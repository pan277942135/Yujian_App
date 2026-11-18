# V3 Stage 1｜26KF Core Role Matrix

**Canonical sequence:** KF00–KF25. This matrix corrects the earlier numbering mismatch; KF01 is CAPTURE and KF02 is PHOTO_STABLE.

| KF | Core visual role | Core freeze |
|---|---|---|
| KF00 | HOME | Independent Home source; destination for KF25 return |
| KF01 | CAPTURE | Independent Camera Runtime source; separate from PHOTO_STABLE |
| KF02 | PHOTO_STABLE | Stable original photo + Orb ready |
| KF03 | ORB_WAKE | Orb wakes; immutable core remains visible |
| KF04 | RESIDENT_ACTIVE | G01/G02/A01/A02/C01 become visible Resident structure |
| KF05 | ORB_RELEASE | Task energy branches from Orb; Resident is not reduced |
| KF06 | PERIMETER_ARRIVAL | WaterLight first touches rounded photo perimeter |
| KF07 | PERIMETER_COVERAGE | WaterLight propagates around real photo perimeter |
| KF08 | TASK_ENERGY_SPLIT | Dynamic Task branch leaves perimeter toward fish |
| KF09 | PATH_TO_FISH | Task seeks trusted Mask-derived P*; visual image is not Runtime Mask |
| KF10 | CONTOUR_A | Trusted Attempt Mask contour begins revealing |
| KF11 | CONTOUR_B | Same Mask Geometry continues |
| KF12 | CONTOUR_C | Same Mask Geometry completes |
| KF13 | GOLD_PEAK | Core / Soft / Halo on the same trusted Mask path |
| KF14 | ORB_RETURN | Same KF05 path identity and geometry, reversed direction |
| KF15 | 11.1 WATER_CORE_STRETCH | Orb core prepares to stretch; six-frame board Frame 1 is primary |
| KF16 | 11.2 HORIZONTAL_STRETCH | Water core extends horizontally |
| KF17 | 11.3 WATERLIGHT_DECONSTRUCTION | Liquid energy deconstructs into stroke gestures |
| KF18 | 12.1 STROKE_AGGREGATION | Glyph strokes aggregate |
| KF19 | 12.3 SPECIES_NAME_FINAL | Real classifier species Glyph stabilizes; 12.2 is six-frame board Frame 5 |
| KF20 | 13.1 RESIDUE_SETTLE | Recognition Scene residue settles |
| KF21 | 13.2 RESULT_TRANSITION | Processing transitions toward Result |
| KF22 | 13.3 RESULT_HANDOFF | Result takes ownership with identity preserved |
| KF23 | 13.4 INFO_ARRIVAL | Result metadata arrives |
| KF24 | 13.5 RESULT_STABLE | Result stable gate |
| KF25 | SAVE_AND_RETURN_HOME | Save succeeds, then return to KF00 Home; no separate success animation |

Rules: 13 runtime stages remain unchanged; 26KF rows are visual mapping points. Five R06 paths are Resident, not Task. Task count is dynamic. Visual fish contours never substitute for Runtime Mask/P*.
