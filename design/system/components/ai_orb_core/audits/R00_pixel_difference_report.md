# R00 Runtime WebP pixel and alpha comparison

## Source and method

The current Main and PR branch returned the same Git blob SHA for `app/src/main/res/drawable-nodpi/processing_water_core_v1.webp`. Both binaries were materialized from the GitHub file endpoint and compared byte-for-byte. The frozen master PNG and Runtime WebP were decoded to RGBA. The master was resized to 384×384 with Lanczos while retaining the complete transparent canvas. No crop or alpha threshold was applied to conceal edge differences.

## Measurements

| Measure | Result |
|---|---:|
| Runtime WebP SHA-256 | `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09` |
| Git blob SHA | `a70a71c623d15355cecda5b0e834d9b0b871e568` |
| File size / dimensions | 27632 bytes / 384×384 |
| Decoded mode / alpha | RGBA / present |
| Alpha bounding box | [35, 37, 345, 345] |
| Alpha IoU, threshold 16 | 0.99825019 |
| Alpha MAE (0–255) | 0.170736 |
| Visible RGB MAE (0–255) | 8.545057 |
| Visible RGB P95 | 28 |
| Near-black semi-transparent share | 0.00165644 |
| Fully transparent RGB mean / max | 25.026575 / 255 |

## Background composites

- light_mint `#EAF4EF`: MAE 4.136642, P95 20.
- dark_lake `#052D31`: MAE 4.144945, P95 20.

The two GitHub binaries are byte-identical. The independently measured source/runtime comparison confirms very high alpha agreement; the color MAE includes the expected lossy WebP re-encoding and source resampling. The rendered boards show the actual decoded WebP over light and dark backgrounds. This is asset evidence, not Android rendering evidence.
