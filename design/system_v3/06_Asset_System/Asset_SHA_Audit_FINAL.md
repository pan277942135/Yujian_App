# Asset SHA Audit V3.1 Final

**Identity status: READY — 658 / 658.** Baseline source commit: 7737f293ea3831aeea2c6f58308961cdb2ba7d01. SHA-256 values were computed over raw file bytes read from each baseline Git path; Git blob SHA-1 is retained only as separate object identity.

| Check | Result |
|---|---:|
| Registered files | 658 |
| Raw-byte SHA-256 computed and stored | 658 / 658 |
| Recorded size matched | 658 / 658 |
| Visual dimensions read from raster/SVG metadata | 244 |
| Non-visual files marked NOT_APPLICABLE | 414 |
| Duplicate paths | 0 |
| Duplicate asset IDs after registry correction | 0 |
| SHA mismatches | 0 |

The original V3 Asset Registry had 579 raw-computed values, 29 direct declarations and 50 absent SHA fields. All 658 were recomputed from bytes in the frozen baseline. No filename, screenshot or Git SHA was used as a substitute. One pre-existing duplicate slug on two distinct Unicode filenames was corrected to a path-specific asset ID; the asset paths and bytes are unchanged.

## Priority asset families

| Family | Entries / check | Page mapping |
|---|---|---|
| Background masters | 2 | Background_Registry_V3.json; 5 frozen roles, including dynamic Capture and Solid token role. |
| Icon SVG masters | 43 | 43/43 Icon Registry entries; exact file/SHA/viewBox mapping. |
| Empty Home | 55 | home_empty_v2; source and intermediate rows retain their per-entry status. |
| Normal Home | 58 | home_normal_v1; no Frozen design asset changed. |
| FishRecordDetail | 17 | fish_record_detail_v2; Frozen/reference visuals retain their current content. |
| Fish Guide | 15 | fish_guide_v2; species states and knowledge assets retain their current content. |
| My Habitat | 10 | my_habitat is design-only and remains a child reference of My Catches. |

The V3.1 final registry records source path, type, byte size, dimensions, raw SHA-256, provenance field, consumer field and per-entry status. Per-entry REVIEW/FROZEN/APPROVED states are preserved; READY here means the identity and byte-hash gate passes.

## Reproduction

Run scripts/design/verify_shared_foundation_v3.py from a repository checkout. The script resolves each path at the declared baseline commit, hashes raw bytes with SHA-256, checks size and compares the separately recorded Git object identity.

## Phase 1 branch extension — brand title

The original baseline SHA audit remains 658/658. The current branch adds 8 derivation/evidence files with raw-byte SHA-256 and Git blob identity recorded in the Asset Registry and Integrity Register (666 rows total). The derived RGBA asset is `2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc` (155×80); its frozen source SHA is `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`. The source raster remains unchanged.
