# Orb resource V2 validation

Status **PASS_WITH_REVIEW_PENDING** · 32 passed / 0 failed.

## V1 compatibility

The unmodified baseline validator was run against the PRELIMINARY package and passed all 231 checks. The same script is run here unchanged; its dynamic total increases as new resources are added.

Current V1 result: 344 passed / 0 failed.

## V2 checks

- PASS — unchanged V1 validator exits successfully — 344 passed / 0 failed
- PASS — V1 validator retains original 231 baseline assertions — baseline was independently rerun on PRELIMINARY: 231 passed / 0 failed
- PASS — R00 packaged runtime copy matches independently audited SHA-256 — daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09
- PASS — R00 Git blob identity matches current Main/PR runtime file — a70a71c623d15355cecda5b0e834d9b0b871e568
- PASS — R00 runtime copy decodes as 384x384 RGBA with alpha — (384, 384) RGBA alpha=(0, 255)
- PASS — R00 audit reports high alpha agreement and measured edge residue — alpha IoU=0.99825019; transparent RGB max=255
- PASS — R00 light and dark composites are both measured
- PASS — R06 preserves exactly the five frozen path IDs
- PASS — R06 profile has one binding for every frozen path
- PASS — R06 three numeric material contracts contain required layers and colors
- PASS — R06 G01 profile binding preserves material/depth/period/phase
- PASS — R06 G01 full material stays within 14dp envelope — outer radius=0.663772 D; expansion=13.102 dp
- PASS — R06 G02 profile binding preserves material/depth/period/phase
- PASS — R06 G02 full material stays within 14dp envelope — outer radius=0.672927 D; expansion=13.834 dp
- PASS — R06 A01 profile binding preserves material/depth/period/phase
- PASS — R06 A01 full material stays within 14dp envelope — outer radius=0.656731 D; expansion=12.538 dp
- PASS — R06 A02 profile binding preserves material/depth/period/phase
- PASS — R06 A02 full material stays within 14dp envelope — outer radius=0.616402 D; expansion=9.312 dp
- PASS — R06 C01 profile binding preserves material/depth/period/phase
- PASS — R06 C01 full material stays within 14dp envelope — outer radius=0.593607 D; expansion=7.489 dp
- PASS — R06 SVG contains five editable cubic centerlines only
- PASS — R06 BACK/FRONT depth model matches frozen geometry
- PASS — R06 four declared sample times produce distinct moving-head positions — {"G01": [0.047586, 0.398732, 0.830849, 0.998428], "G02": [0.767913, 0.99905, 0.713962, 0.207548], "A01": [0.922164, 0.250245, 0.056636, 0.712121], "A02": [0.315938, 0.136258, 0.953332, 0.528151], "C01": [0.437333, 0.958308, 0.820907, 0.222512]}
- PASS — R06 MotionClock, RELEASE and RETURN preserve same-state event binding
- PASS — R04 contract contains only S1/S2 and explicitly excludes S3
- PASS — R04 S1/S2 share the same immutable runtime WebP SHA
- PASS — R04 final state boards exist for both states and comparison
- PASS — R04 state images are readable and same canvas size
- PASS — R06 final material, depth, motion, and size boards exist
- PASS — Manifest paths exist and every stored SHA/byte count matches
- PASS — Manifest covers every package file except manifest/self-rewriting reports
- PASS — Manifest has unique stable IDs and no duplicate SHA-256 payloads

## Warnings

- Android runtime implementation and device rendering were intentionally not changed or claimed.
- V1 contains two inherited static warnings; R00 binary evidence supersedes the old missing-WebP note, while device acceptance remains pending.
