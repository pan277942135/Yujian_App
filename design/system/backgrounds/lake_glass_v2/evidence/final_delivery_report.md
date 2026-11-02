# Lake Glass V2 asset pack delivery

**Status: ASSET_PACK_READY_FOR_ANDROID.** The validated full-scene static composite is the only visible runtime surface. API 33+ shader masks operate in place and do not duplicate any baked element. Grass and standalone drop cutouts remain diagnostic-only; sunlight remains baked.

- Master: 1440×3120, SHA-256 `d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12`.
- Static reconstruction: 1440×3120; run `python3 scripts/verify_lake_glass_android_runtime_v1.py` for current exact metrics and hashes.
- Runtime protocol: `assets/android_runtime/layer_manifest_v1.json` is authoritative; all consumer manifests pin it.
- Design status remains `CANDIDATE_VISUAL_NOT_FROZEN`; no design freeze is inferred.
- Android shader execution and physical device validation are separate gates and are not asserted by this asset status.
