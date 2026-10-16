# A.4.1 checkpoint — actual evidence, not closure

Base branch HEAD: `f9dd9777bfb7826b5ac9a871abbf1215fff7818a`.
PR 145 remains Draft / Open / Unmerged. No product Kotlin, frozen raster,
registered asset, pixel target, tolerance, or model changed in this checkpoint.

## Camera diagnosis

Evidence: [Android CI 38509685002](https://github.com/pan277942135/Yujian_App/actions/runs/38509685002),
artifact `11869482905` (original native API28 screenshots, not regenerated images).
The APK was built from the PR merge ref `5ab2e0e0140335d4004a5ddc403698ca493c580d`,
not directly from the branch HEAD. Do not relabel this as a new-head device run.

- APK SHA256: `dd805da025f06da5f40bcc37f4dd9c12718bc012ca2241451de285fd24bd8f70`
- AndroidTest APK SHA256: `7b32df126994117387e30da5223dba324354b51eb1adf3f71dfa76ffbb0ecfa8`
- Screenshot SHA256: `9dc2c53d1c4cece2db4d80cf50cabd1be4ddfcbc42359317dd6015725b609a21`
- Profile: API28, 1080×1920, 480dpi, fontScale 1.0.

Frozen near-white bbox: `[450,1590,628,1773]`.
Runtime near-white bbox: `[452,1591,626,1783]`.
Delta: `[2,1,-2,10]`; unchanged tolerance 2.16px: **FAIL**.
Gold bbox remains within tolerance. The largest connected white component has
edge delta `[2,0,-1,-2]`, but this is diagnostic only and does not replace the
frozen full-mask gate. The bottom outliers are disconnected highlights at the
camera rim, including x557–559/y1783; blanket attribution to lake background
would be incorrect. No component filtering, ROI clipping, threshold change,
or camera resize was used to manufacture a PASS.

Reproduce:

```sh
python scripts/audit_normal_home_camera_mask.py --screenshot /path/to/native-runtime.png --output /tmp/Camera_Core_Mask_Report.json
```

Exit 1 means the original optical gate fails; exit 0 means it passes. Source-vs-
source self-check passes. Wrong-size images are rejected rather than resized.
Runtime camera styling/motion-state investigation remains open. Diagnostic
agreement of the main white surface alone does not authorize a visual fix.

## Hero preflight

`Hero_Fixture_Preflight_Report.json` enumerates all eight immutable contract
slots, actual candidate hashes, full JPEG decode results, decoded orientation,
source dimensions and missing provenance/permission/inspection evidence.

The repeated carp photo and its crop are not independent source coverage.
The night photo is a distinct candidate, not automatically approved provenance.
`golden_yellow_catfish_224.jpg` fails full Pillow decode; it is excluded as a
usable candidate without modifying this unrelated test asset.

```sh
python scripts/verify_normal_home_hero_fixtures.py --output /tmp/Hero_Fixture_Preflight_Report.json
```

Exit 2 means blocked source preflight. Exit 0 only means SOURCE_READY, never
runtime PASS. Each registered source needs the existing SHA/oriented dimensions,
`photo_provenance` and `source_usage_rights` VERIFIED/APPROVED plus inspectable
`source_evidence` and `usage_evidence` references. Verified fish cases also need
`bbox_ground_truth` VERIFIED/APPROVED and `fish_region_inspection`; an optional
`fish_bbox_xyxy` is checked in the oriented image frame. F08 explicitly records
bbox ABSENT. F07 records its approved-parent derivation. These evidence fields
are execution metadata, not new visual targets or loosened acceptance rules.

No original files were generated, padded, cropped or relabeled to fill missing
independent slots. The runtime gate now emits these diagnostics automatically;
its existing Hero BLOCKED status remains until real per-case device evidence
can be produced. New manifests alone must never promote it to PASS.

## Verification performed

- 4 negative validator tests: corrupt original, invalid bbox, duplicate matrix,
  and path escape: PASS.
- Python compile / Bash syntax / git whitespace checks: PASS.
- Frozen Normal Home contract validator: 55/55 STATIC_PASS.
- Complete A.4 script replay on the downloaded original API28 evidence: executes
  successfully, retaining Hero BLOCKED and camera mask FAIL. This is an offline
  replay, not a new Android run.
- New product build/device run: NOT_RUN at this checkpoint; no Kotlin changed.

## Remaining executable work

1. Supply/register real independent F01–F06 originals with traceable permission
   and fish-region inspection; derive F07 explicitly and exercise F08 absent bbox.
2. Capture HOME + DETAIL from the same record for each actual fixture; record
   exact APK and screenshot hashes. Synthetic aspect fixtures remain layout-only.
3. Resolve the camera rim-highlight discrepancy using the unchanged frozen mask
   and actual settled runtime rendering. Do not delete outlier pixels from the
   validator or replace the full-mask target with the largest component.

`NORMAL_HOME_RUNTIME_PARITY = BLOCKED`.
