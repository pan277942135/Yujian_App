# Layer feasibility review

## Canonical runtime decision

`assets/android_runtime/layer_manifest_v1.json` is authoritative for every consumer. The supplied images are flattened RGB. No safe transparent cutout can be used as an independent visible runtime surface without risking edge contamination or duplication. The API 24–32 and universal fallback draw only `LAYER_STATIC_COMPOSITE`.

- Sunlight is `BAKED_IN_BASE`; its right-upper source and reflection remain in the static image. The optional API 33+ pass only multiplies existing pixels through the Master-derived green mask.
- Caustics are `SHADER_INPUTS_PROVISIONAL`; the pass modulates existing lower-water luminance through the red mask and never draws a second caustic sheet.
- Three droplets are `SHADER_INPUTS_PROVISIONAL`; fixed anchor/SDF refraction samples the same composite in place. The extracted RGBA and estimated normal maps are diagnostic only.
- Grass candidates are `NOT_RUNTIME_ELIGIBLE`; broad leaves and the root-bed overlaps remain represented by the static composite. Skeleton SVGs are estimated guides, not recovered source vectors.
- Base clean plate, standalone grass, droplet RGBA, caustic texture, and procedural sunlight field are not drawn over the static composite.

This is a complete static-first Android asset pack with optional provisional shader inputs. It does not claim device shader validation.
