# R00 Runtime WebP alpha audit · V2

The current Main file and the PR branch file are byte-identical Git blob `a70a71c623d15355cecda5b0e834d9b0b871e568`. The packaged copy is 27,632 bytes, SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`, 384 × 384 RGBA with alpha extrema 0–255 and visible alpha bounds [35, 37, 345, 345].

The frozen 1254 × 1254 master was Lanczos-resampled to the complete 384 × 384 canvas. With threshold 16, alpha IoU is 0.99825019 and alpha MAE is 0.170736 / 255. Visible RGB MAE is 8.545057 / 255; P95 is 28. There are 73,652 semi-transparent pixels. Of those, 122 (0.165644%) have mean RGB below 20. RGB under fully transparent WebP pixels is nonzero (mean 25.026575, max 255); those values are invisible in alpha compositing but are preserved in the audit rather than scrubbed.

Light-mint and dark-lake composites are both compared in `R00_runtime_webp_integrity.json`. This checks the actual WebP binary and source pixels. It does not establish Android/device rendering acceptance.
