# Empty Home Acceptance Criteria V2

- Frozen source, normalized source, provenance and SHA256 are present and validated.
- Static recompose uses clean base plus independent rod, line, ripple and bobber masters; it is
  visually consistent with the frozen source and has no baked runtime ripple in the base.
- The runtime asset map contains only production assets and contracts, never design sources,
  validation previews, proof files, MP4 or GIF.
- Android reuses HomeScreen/HomeState and existing callbacks. EMPTY derives from empty fish records.
- One ripple group contains exactly three vector wave layers, centred on the frozen ripple anchor [562, 1320] and retaining its measured 3 px relationship to the bobber water-contact [561, 1323], with a 3,600 ms burst and 0 / 420 / 900 ms wave delays.
- One automatic double-pulse haptic is emitted only at each scheduled bobber/ripple event start; it is suppressed when the page is not resumed, motion is disabled, Reduce Motion is enabled, or system haptics are disabled.
- Motion, camera haptic, bobber-event haptic, semantics and Reduce Motion match the amended Motion V2.2 and Haptic V1.1 contracts.
- Android build, unit tests, instrumentation tests, V2 asset verifier and real runtime evidence pass.
- Runtime evidence includes static, 4-second, anchor overlay, three motion captures, full 15-second
  capture, runtime debug JSON and visual parity report. Large MP4 files are Actions artifacts.
