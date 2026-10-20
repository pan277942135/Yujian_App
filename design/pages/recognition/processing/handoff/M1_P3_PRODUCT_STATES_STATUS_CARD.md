# M1 P3 · Product States / Status Card

Status: **STATE CONTRACT MAPPED · COPY REPAIR REGISTERED · EVENT GATES UNCHANGED**

PR: [#166](https://github.com/pan277942135/Yujian_App/pull/166)  
P3 config commit: `a27e954995a75aec1b6b9675e264b3799530e236`  
Machine input: [Processing_Engineering_Import_Config_V1.json](Processing_Engineering_Import_Config_V1.json)

## Canonical 3-to-4 mapping

| User-visible product state | Frozen source | Internal pipeline phase(s) | Real gate / minimum |
| --- | --- | --- | --- |
| 图片识别中 · IMAGE_RECOGNIZING | `02_AI_Understanding_Frozen.png` | CAPTURED, DETECTING | detection-start / 220ms; real eligible fish focus / 320ms |
| 已定位到鱼体 · FISH_LOCATED | `03_Fish_Highlight_Frozen.png` | OUTLINE | classification-start / 350ms |
| 鱼种识别中 · SPECIES_RECOGNIZING | `04_Fish_Identifying_Frozen.png` | CLASSIFYING | real classifier result / 250ms |
| No Processing visual · RESULT_READY | none | result handoff | immediate, 0ms |

CAPTURED and DETECTING remain internal technical stages. No timer-only transition, fake progress, new product state, Early PNG or changed event gate was introduced. No Fish, Image Quality and Technical Failure retain their existing distinct exit routes.

## Status Card implementation trace

The shared component is Glass Container + AI Ring Indicator + Title + Subtitle. Current Compose values: 112dp height, 30dp corner radius, `#D91A2C35` surface, 0.75dp gradient border, 28dp horizontal inset, 54dp indicator with 4dp stroke, 112° sweep / 1600ms ring clock, 20sp Medium title and 14sp subtitle. Token references are carried through from V3.1 Layout Contract.

The Android copy resolver currently keys directly on `RecognitionPhase` and returns:

- CAPTURED: “正在准备识别 / AI 已获取这张照片”
- DETECTING: “正在理解这张照片 / 寻找这次鱼获的线索”

These phase-specific Early strings conflict with one IMAGE_RECOGNIZING product visual. V1.2 gives the shared display name “图片识别中”, but the text-readable contracts do not transcribe the exact title/subtitle shown on the Frozen 02 PNG. The repair is therefore registered precisely: key the resolver by the three product visual IDs, transcribe the retained 02 copy, and assert CAPTURED and DETECTING resolve identically. The current `RecognitionProcessingVisualTokensTest` explicitly asserts all four internal phases have distinct copy and includes `assertNotEquals(CAPTURED, DETECTING)`; replace that assertion with product-state equality while retaining distinct OUTLINE and CLASSIFYING copy. I did not invent substitute copy.

Status placement is already grouped for the shared first product state: safe bottom +84dp for both CAPTURED and DETECTING, and +34dp for OUTLINE/CLASSIFYING. This is not an internal-phase jump. Verify each product-state anchor against its retained Frozen composition; do not force the three distinct product states to share one position.

## Registered Android tasks

- **RP-A07:** unify CAPTURED/DETECTING copy by product visual ID; transcribe exact copy from retained Frozen 02, replace the current `assertNotEquals` copy test with an equality assertion, retain all state-controller event/minimum tests.
- **RP-V-A01:** compare the existing product-grouped 84dp/34dp anchors with the three Frozen PNGs when visual access returns; CAPTURED and DETECTING must remain on the same anchor.
- **RP-A09:** map the Layout Contract's Layout-Contract-referenced candidate Back asset to Compose after comparison; keep safe-area placement and 44×44dp minimum target.
- **RP-A10:** V1.1 calls for a lightweight DETECTING card, but V1.2 defines one shared IMAGE_RECOGNIZING visual. Use current V1.2/V3.1 authority; verify the card treatment against 02 when visual access returns.

Source audit: State Machine SHA `0be6a1685a91c0cb4940080b845ad6f7b4a3bf4c`; status overlay SHA `891caf7473bf2a46c5730bc3586df7bc069f575a`; status token/copy SHA `68a61dae637672337dd8117ae789493f112d97e3`. Existing runtime/controller tests were not run during this handoff.

## Gate

P3 mapping and repair acceptance are reviewable. The event gate contract is unchanged. Frozen copy transcription, native Figma components and visual QA remain pending; no Figma PASS is claimed.
