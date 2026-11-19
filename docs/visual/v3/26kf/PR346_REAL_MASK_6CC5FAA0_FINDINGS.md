# PR #346 real Mask evidence, 2026-11-19

Source HEAD under test: `423f5c751228d646e04dc72dfd46cc18b52b834f`.
Evidence ZIP SHA256: `20e78a3aba409686ef2186505b150f2e4326e09d051bf1b00a05208905fd7e6c`.
Original `mask/original_mask.png` SHA256: `4b554bb4a2af48d139c2b7994c342961a42e9a0abe92800e7e96f9f72d403de4` (716 × 1440 RGBA).

| Observation | Actual evidence | Conclusion |
| --- | --- | --- |
| Alpha foreground | 400,064 / 1,031,040 pixels at alpha ≥128; 249 exactly 128 | Foreground is present. The default threshold 128 cannot by itself explain zero contour. |
| Raw boundary | Independent offline reconstruction found 5,758 edges and 22 closed loops | Diagnostic only; the Android extractor and trust gate are not verified. |
| Runtime | `READY`, `GOOD`, `contourPointCount=0`, `ORB_ONLY_READY`; later mask decoded but no accepted contour | Model readiness was incorrectly visible before PNG and contour acceptance finished. This race is proven by the source call order. |
| Identity | Exported snapshots show false identity/SHA/geometry flags, without the producer and consumer field values | Final mismatch is unverified: asynchronous snapshots can precede completion of the actual checks. Added detailed terminal comparison log. |
| Pixels | 33 PixelCopy requests, 9 successes, only 6 screenshot files exported | `EVIDENCE_INCOMPLETE`; no 26KF visual PASS. |

The original PNG contains pixels derived from a private photograph and is retained in the user Evidence ZIP, not copied into this public repository. The present change delays the UI's terminal Mask result until the original PNG, geometry, SHA, contour and trust decisions complete. It records the exact producer/consumer identity fields so that a later mismatch is actionable. A fresh device run is still required to verify Android contour extraction, P*, Fish Path, all 26 screenshots and final Result stability.
