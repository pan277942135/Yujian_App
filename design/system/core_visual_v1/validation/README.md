# Core UI V1 Validation

The validation directory records implementation decisions without modifying frozen PNG
sources.

- component_map.json: shared component family, variants, tokens, and runtime notes
- token_usage_map.json: frozen-screen evidence, reason, and usage for existing tokens
- shared_asset_manifest.json: verified shared-bitmap decision
- page_asset_map.json: page-specific, variant, and reference-only asset decisions
- extraction_report.md: human-readable common-element analysis
- cross_page_validation_report.json: six-page visual-system acceptance gate
- recompose_validation_report.json: implementation-guidance completeness gate

Reference integrity is enforced separately by scripts/verify_core_ui_v1_references.py.
