# Core UI V1 Frozen Reference Registry

Status: **FROZEN**

The six canonical page references in this directory are the exact PNG files supplied by
product and uploaded to GitHub. They were renamed with Git only: no PNG was redrawn,
resized, recompressed, re-exported, or otherwise changed.

The authoritative byte sizes, dimensions, SHA-256 values, source filenames, and freeze
state are recorded in reference_manifest.json. Run:

    python3 scripts/verify_core_ui_v1_references.py

to validate all six core references and both preserved supplemental references.

## Core references

- empty_home_v2.png
- normal_home_v1.png
- recognition_result_v1.png
- fish_record_detail_v2.png
- my_catches_v2.png
- fish_guide_v2.png

## Supplemental references

The supplemental directory is outside the 6/6 core freeze count, but its provenance and
integrity are tracked in the same manifest:

- supplemental/fish_memory_reference.png
- supplemental/fish_guide_unlit_state.png

A missing or changed frozen source is a source-gate blocker, never permission to create a
replacement visual.
