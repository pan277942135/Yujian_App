# Empty Home Haptic Spec V1.1 — Approved Bobber Event Amendment

Camera tap uses the Android light-impact equivalent with a 20 ms semantic hint. Album may use the
platform light-click convention.

An automatic bobber haptic is approved only as part of the scheduled natural bobber/ripple event:
one subtle double-pulse at each event start, synchronized to the first frame of the three-layer
3,600 ms ripple burst. The event schedule has five starts per 36,000 ms cycle at
1,500 / 6,800 / 13,600 / 22,400 / 28,200 ms, relative to the 420 ms ripple phase offset.
The waveform is 14 ms pulse, 31 ms pause, 18 ms settle pulse; where amplitude control is
unavailable, use one 20 ms system-default pulse or the platform clock-tick fallback.

The event haptic must:
- require the Empty Home scene to be visible and lifecycle-resumed;
- require motion to be enabled and Reduce Motion to be off;
- respect the system haptic-feedback setting;
- fire no more than once for each unique scheduled event, including lifecycle pause/resume;
- never run as a continuous idle vibration or on page entry.

The static contact ring remains visible when Reduce Motion is enabled, but animated waves and
automatic bobber haptics are disabled. The canonical machine-readable source is
`shared/contracts/haptic_contract.json`.
