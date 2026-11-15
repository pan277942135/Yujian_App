# Next iteration — exact-source device evidence and first-failing-layer decision

## Current entry status

`DEVICE_QA_PENDING_USER`. Source-level fixes are present; the visual delta is not measured. Keep the current R03 and R06 changes intact until current-source evidence establishes a regression or a remaining failure.

## Product-owner capture request

After Work delivers the final exact-head Debug APK:

1. Install that APK on HUAWEI CMS-AL20 / Android 16 / API36 and record a normal recognition attempt from photo presentation through Stage02. The normal recording is the evidence for the production 350 ms flow.
2. In the same Debug session/Attempt when possible, enter Stage02 step diagnostics, choose MAX and 100%, confirm candidate Shader route/READY and Reduced Motion off, then export the Orb evidence ZIP. The ZIP will state if a production AUTO Stage02 trace was actually present; a MANUAL preview must never be relabeled AUTO.
3. Preserve the screen recording and the exported ZIP. Upload only those two artifacts; the ZIP contains the anonymous trace, capture PNGs/metadata, hashes and metrics.

If PixelCopy fails, retain and upload the ZIP anyway: its manifest and `logs/errors.json` name the exact failed capture. Do not recapture with a different progress/mode while treating the images as one comparison.

## Automated next-step analysis

On receipt, first verify the APK source SHA against the ZIP `build_info.json`, trace source SHA, and the PR head. Then inspect:

- **R03:** AUTO SceneElapsed checkpoints → calculated flow phase → uniform revision → matching R03 draw submission → fixed-progress PixelCopy frame differences and local NCC. Report projected diagnostic samples separately from AUTO clock behavior.
- **R06:** for G01, G02, A01, A02, C01 individually, compare the capture-bound valid samples/runs/body/alpha/stroke/transmission with Full-vs-without pixel count, bounds and connected extent. Only the screen recording can establish human discernibility.
- **Evidence integrity:** reject pairs whose Attempt, image, StageProgress, geometry, source SHA, crop or required optical sample do not align. Record missing/failed captures without substituting stale images.

## Single priority decision after evidence

Choose only the first failing link:

1. R03 time/phase/uniform/draw/pixel chain, if the earliest corresponding AUTO trace element does not advance.
2. G01/A01 transmission, if valid path/body records are normal but per-path pixel contribution remains low.
3. One of G02/A02/C01, if its own valid body and per-path ablation show where contribution is lost.
4. Visual contrast calibration only if all mechanical and pixel contribution checks pass but the user still cannot discern the strand or flow.

Do not change a module whose per-path or R03 evidence already passes. Do not tune all MAX strengths globally.
