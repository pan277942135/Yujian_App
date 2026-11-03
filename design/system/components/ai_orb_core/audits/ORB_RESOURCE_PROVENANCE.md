# R07 asset provenance · V2

- `透明湖水玻璃球.png` is the preserved 1254 × 1254 RGBA core master. SHA-256: `2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee`.
- `236493.png` is the preserved 1254 × 1254 water-ribbon reference. SHA-256: `adefad98ec7cfe32df1316a605ac7f3ded8a5a7b33e107f2380ecbf49f2f7c35`.
- `翡翠星光中的流光水晶球.png` and `orb_crop_12s.png` remain frozen V1 source/reference files. Their hashes are recorded in the primary V2 manifest and resource register.
- `assets/derived/processing_water_core_v1.webp` is an exact portable copy of the current Main Runtime WebP. SHA-256: `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`; Git blob SHA: `a70a71c623d15355cecda5b0e834d9b0b871e568`. The Android Runtime file itself is unchanged.
- R00 pixel and alpha measurements use the actual WebP bytes and full-canvas source master. See `R00_runtime_webp_integrity.json`.
- R06 keeps the five existing cubic centerlines. Static previews use deterministic samples. They are not Android runtime evidence.
- Historical R01 grass SVGs remain excluded from integration. S3/glyph resources are not present in the V2 package inventory.
- The earlier `PRELIMINARY_20261102_YuJian_AI_Orb_Gap_Closure_V1.zip` remains an unchanged historical Library item. The final archive is a separate deliverable and is identified by `ORB_RELEASE_RECORD_V1_2.json`.
