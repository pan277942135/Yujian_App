# M1 P2 · Fish Focus Engineering Trace

Status: **ENGINEERING TRACE COMMITTED · PRODUCTION-MASK VISUAL QA PENDING**

PR: [#166](https://github.com/pan277942135/Yujian_App/pull/166)  
P2 config commit: `7d680ca51bc302cdfbbd97a5b7a5a84e4312267f`  
Updated unified-import input: [Processing_Engineering_Import_Config_V1.json](Processing_Engineering_Import_Config_V1.json)

## Real-data gate

Fish Focus is absent in CAPTURED/DETECTING and is drawn only during OUTLINE/CLASSIFYING with a real detector bbox. The bbox, generated subject mask, contour and halo all share the P1 primary FIT transform. Missing bbox means no focus. A valid bbox without mask/contour falls back to Level B; the renderer never turns the bbox rectangle into a contour. Level A/B/C changes presentation only and cannot change detector, crop, classifier or result semantics.

The subject-mask contract is the actual `FishSubjectResult` bitmap alpha from the subject-generation pipeline. The current scene expands the detector bbox by 12% for the local mask coordinate box, thresholds alpha at 36/255, samples on a 2px grid, keeps connected components at least `max(4, largest/200)`, traces closed loops, and smooths them into Bezier paths. V1.1 describes 4px-equivalent sampling, so this implementation difference remains explicit for review.

## Levels and optical values

| Level | Entry condition | Rendering |
| --- | --- | --- |
| A | Real bbox + generated bitmap + nonempty contour; promoted from B after 420ms (or phase override) | bbox-derived halo + real mask contour |
| B | Real bbox, contour unavailable, or the A promotion delay | bbox-derived halo + restrained 1.2dp perimeter ellipse; no contour |
| C | Explicit D4 only | real-bbox center-biased halo, scaled to 0.88; no contour or perimeter |

A radius is `bbox width × .62 + 14dp` by `bbox height × .72 + 14dp`; B uses `.54/.60`. Halo is warm gold `#F6D99B`, with radial extent 1.18×. Level A contour uses `#FFE7AE`, 1.5dp, core alpha .36 in OUTLINE and .30–.42 in CLASSIFYING; its `#FFD887` outer glow is 11dp/5dp blur and mid glow 4dp/2dp blur. The machine config records the exact formulas and runtime alpha bounds.

## Differences and evidence limits

- B fallback runtime halo reaches .24–.36 and perimeter .42–.56, while the V1.1 Fish halo cap is .18. This needs Frozen-state optical review before reducing or accepting runtime values.
- The contour runtime test uses a synthetic 160×120 oval mask. It verifies a connected closed smooth path and rejects a bbox-corner contour; it is not production fish-mask evidence.
- The repository's 292,222-byte real-catch fixture is only a runtime photo source. Its static bbox/mask provenance and use rights are unverified, so it is not imported as a frozen design input.
- No Android test ran during this handoff. Existing contour test SHA: `a87290588da9c3ba24a40d17a560f229a4dbef11`; source implementation SHA: `151d3f8494dc0b9edc4165b0d5028a5faeed916c`.

## Gate

P2 data and rendering contract are structured and ready to feed the NH01 unified importer after its NH01 minimum import check passes. No contour, bbox, Figma node or optical comparison has been fabricated. Overall Figma structure and visual QA remain **NOT_PASS / NOT_RUN**.
