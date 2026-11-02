# Main-bound asset audit · R00

Source Main: `f607432e262ee7fc1265479a82dd0a93cd4a5141`.

| Asset | Source / SHA-256 | Renderer | Disposition |
|---|---|---|---|
| Frozen master | exact 1254×1254 RGBA / 2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee | AIOrbCore visual reference | KEEP |
| S2 reference 236493.png | exact 1254×1254 RGBA / adefad98ec7cfe32df1316a605ac7f3ded8a5a7b33e107f2380ecbf49f2f7c35 | R06 form | KEEP |
| Current core WebP | `app/src/main/res/drawable-nodpi/processing_water_core_v1.webp` / Main documented daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09 | AIOrbCore | KEEP; local raw bytes unavailable for independent alpha check |
| Unified Orb core | `AIOrbCore.kt` | Core | KEEP |
| Existing 900ms | `AIOrbMotion.kt` | motion | KEEP |
| Compatibility | `ProcessingWaterOrb.kt` | adapter | KEEP |
| Legacy 3 ribbons | `OrbFilamentRenderer.kt` | ribbons | EXTEND after visual approval; do not double render |
| Droplet shapes | `WaterDropletRenderer.kt` | lens | EXTEND; no PNG |
| Caustics field | `WaterCausticsField.kt` | caustics | KEEP |
| Photo renderer | `WaterCausticsRenderer.kt` | photo | reference only |

The frozen source includes the internal aqua-green grass band. No new plant or per-leaf rig is introduced. Image boards/Excel/SHA register are delivered in the separate exact ZIP snapshot in the ChatGPT Library; they have NOT been committed in this text-only PR. This PR is not an Android runtime approval.
