# Main-bound existing resource audit · R00

Current Main baseline: `10cda91218b1b8d5b1600ef95a9d676b2c040a07`.

| Asset | Verified source / SHA-256 | Disposition |
|---|---|---|
| Frozen core master `透明湖水玻璃球.png` | 1254 × 1254 RGBA · `2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee` | Preserve as source |
| Water-ribbon reference `236493.png` | 1254 × 1254 RGBA · `adefad98ec7cfe32df1316a605ac7f3ded8a5a7b33e107f2380ecbf49f2f7c35` | Preserve as reference |
| Current Runtime WebP `app/src/main/res/drawable-nodpi/processing_water_core_v1.webp` | 384 × 384 RGBA · SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09` · Git blob `a70a71c623d15355cecda5b0e834d9b0b871e568` | Preserve unchanged; independently decoded and compared |
| Existing 900 ms motion / compatibility adapter / caustics field | Existing Main implementation | Preserve unchanged in this design-only task |
| Legacy 3-path ribbon renderer | Existing Main implementation | Do not double-draw with the five-path candidate |
| Historical R01 grass SVGs | Existing audit marks `DEPRECATED_DO_NOT_INTEGRATE` | Keep out of the candidate |

The frozen core includes its existing internal aqua-green band. No new plant geometry or leaf rig is introduced. Main and the current PR branch return the same runtime WebP Git blob. The package includes a copy of those exact bytes for offline review. Pixel and alpha measurements, including the small near-black semi-transparent population and RGB residue under fully transparent pixels, are recorded in `R00_runtime_webp_integrity.json`.

The source comparison and static boards are asset evidence. They are not Android Runtime or device-render evidence. Product visual acceptance remains pending.
