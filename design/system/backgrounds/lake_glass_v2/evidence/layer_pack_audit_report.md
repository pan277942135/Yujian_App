# Lake Glass V2 Layer Pack Audit

Task: YUJIAN_LAKE_GLASS_V2_MASTER_TO_LAYERED_ASSET_PACK_P0  
Status: **ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS**  
Input master SHA-256: `d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12`

## P0-1 — Inputs

PASS. The three supplied binary inputs match the contract SHA values and dimensions. Original bytes are archived under `assets/source_master/`. The source Manifest truthfully identifies Candidate 02 as an upscale/refinement from 852×1846, not a native layered source.

## P0-2 — Visual inventory

PASS. The annotated master, region map, color samples, inventory and feasibility matrix locate the upper-right light, three drops, both grass groups, lower caustics and central quiet area.

## P0-3 — Layer generation

PARTIAL BY SOURCE LIMITATION. A static master base, sunlight masks, caustics masks, separate droplet region masks, two diagnostic grass alpha candidates and editable estimated SVG guide paths were generated. The alpha candidates and guides are not runtime layers. No transparent droplet RGBA or clean plate is claimed because each droplet refracts underlying water and the flattened source contains no hidden pixels.

## P0-4 — Coordinates / manifest

PASS. Layer pack, anchors, material tokens, motion profiles, crop metadata and per-file SHA/size/dimensions are recorded. Visible stack contains only the exact master base; analysis layers have alpha 0 / visible false.

## P0-5 — Static reconstruction

PASS FOR STATIC BASE ONLY. The recomposition is pixel-identical (MAE 0, RMSE 0, PSNR Infinity, SSIM 1.0). This is because the original Master remains intact as the sole visible layer. It does not prove independent layer reconstruction. Difference image is all black and the overlay is identical.

## P0-6 — Crop preview

PASS FOR SIMULATION. CenterCrop, TopCenter and preliminary Center final candidate are shown at 320×480, 360×640, 390×844 and 430×932 dp. The 320×480 viewport cannot retain both the upper light field and lower grass under crop-only scaling. Center is the balanced preliminary recommendation; Android rendering/device validation remains pending.

## P0-7 — Automated validation

See `evidence/validation_report.json`. The package integrity and required files are checked by `scripts/verify_lake_glass_layer_pack_v2.py`.

## P0-8 — PR handoff

This pack is submitted as **ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS**, not ASSET_PACK_READY_FOR_ANDROID. Before Android runtime use of independent grass, drops, sunlight or caustics, obtain a native layered source or perform human-reviewed extraction with clean plate and edge/fidelity approval. Empty Home and Morning Lake V1 are protected and outside the change set.
