# Core UI V1 Reference Registry

Canonical reference sources were supplied in the product-design conversation.

This directory intentionally does **not** fabricate replacement visuals. The exact source filenames, dimensions and SHA-256 values are registered in `reference_manifest.json`.

## Required binary close-out

Before Core UI V1 is marked fully FROZEN in Git:
- copy each exact canonical source image into this directory using the target filename in the manifest;
- verify the registered source SHA-256 (or record an explicitly approved lossless packaging transformation);
- do not regenerate, recolor or reconstruct the image during migration.

A missing binary is a source-gate blocker, not permission to synthesize a substitute.
