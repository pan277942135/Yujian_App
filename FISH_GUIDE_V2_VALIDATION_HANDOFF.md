# Fish Guide V2 Android Validation Handoff

## A. Code and build

- Repository: `pan277942135/Yujian_App`
- Branch: `fix/fish-guide-v2-android-hifi-closure-v1`
- Base: `3cd0753be926c3a8cc94ac56c97c9103febc6663`
- Final HEAD: the branch tip in the linked Draft PR; the exact SHA and CI build SHA are recorded in the PR description and delivery note.
- Draft PR: [Fish Guide V2 Android Hi-Fi Development V1](https://github.com/pan277942135/Yujian_App/pulls?q=is%3Apr+is%3Aopen+head%3Afix%2Ffish-guide-v2-android-hifi-closure-v1)
- Build, JVM tests, AndroidTest compilation and Lint: Android CI build job; this Work does not run the Android runtime matrix.
- APK: the `YuJian-debug-<build-sha>-<model-id>` artifact from the build job. APK SHA-256 and download URL are in the final delivery note and the uploaded artifact checksums.

### Changed files

- `app/src/main/java/com/yujian/ai/knowledge/FishKnowledgeModels.kt` — public knowledge-asset model and detail payload field.
- `app/src/main/java/com/yujian/ai/knowledge/FishKnowledgeRepository.kt` — V1.3 `knowledge_assets` parsing, resource/version/status metadata, and safe blank handling for legacy card URLs.
- `app/src/main/java/com/yujian/ai/ui/fishguide/FishGuidePresentation.kt` — fixed five-role mapping, ACTIVE card fallback, URL validation, deterministic de-duplication and asset cache identity.
- `app/src/main/java/com/yujian/ai/ui/components/RemoteImage.kt` — cache identity participates in image load state and reload keys.
- `app/src/main/java/com/yujian/ai/ui/screens/FishSpeciesDetailScreen.kt` — V2 full-image card rendering with square Fit bounds and loading/missing/failure states; existing page shell and interactions remain in place.
- `app/src/test/java/com/yujian/ai/ui/fishguide/FishGuidePresentationTest.kt` — deterministic mapping, isolation, priority, fallback, URL and cache-identity tests.
- `app/src/androidTest/java/com/yujian/ai/FishKnowledgeContractTest.kt` — CMS JSON parser contract coverage, including legacy payloads.
- `app/src/androidTest/assets/fixtures/fish_knowledge_detail_v13_active_assets.json` — traceable CMS V1.3 test fixture.
- `app/src/androidTest/java/com/yujian/ai/FishGuideRuntimeTest.kt` — updates the detail card assertions for V2 image-only rendering while retaining shell and carousel checks.
- `FISH_GUIDE_V2_VALIDATION_HANDOFF.md` — this handoff.

## B. CMS asset mapping

The detail parser reads `species.id`, `knowledge_assets`, and `cards[]`. The UI always creates five positions in this order:

1. `HERO` — 鱼种名片
2. `IDENTIFICATION` — 辨识特征
3. `ECO` — 生态习性
4. `GEAR` — 装备建议
5. `SKILL` — 作钓要点

For each role, the public `knowledge_assets[ROLE].image_url` is preferred when its role and optional species ID match and an explicit status is `ACTIVE`. When that entry is absent or unusable, the mapper considers only `cards[]` with the exact current species ID, normalized target role, `ACTIVE` status and a nonblank supported URL. Draft cards, species cover images and cross-role substitutions are excluded. One resolved URL cannot fill multiple roles.

All selected URLs pass through the existing `resolveAssetUrl()` callback. HTTPS, existing HTTP compatibility, relative paths and the CMS-served WebP URL are supported; unsupported URI schemes are rejected. `asset_id`/`id`, version and URL form the cache identity, so a new asset version or resource causes a reload even when the URL stays the same. Structured knowledge remains in the domain model for nonvisual consumers.

## C. Handoff validation scope

Please verify in the Validation Work:

1. Production CMS ACTIVE records and public URL responses.
2. Availability of the full 20 × 5 species/role asset set.
3. White-strip fish, grass carp and tilapia.
4. At least 15 actual Android screenshots using the target device profiles.
5. Complete image integrity and 1254 × 1254 Fit behavior, without crop or stretch.
6. Pixel/ROI comparison with Frozen V2 authorities.
7. Frozen page-shell geometry, adjacent previews, finite paging, back navigation, vertical scroll and MySpecies interactions.
8. Installation and runtime acceptance of the delivered APK.

## D. Limits and risks

- Production CMS ACTIVE state and live image reachability have not been checked in this development Work.
- No runtime screenshots, emulator runs, Pixel Diff, Frozen comparison or device matrix were run here.
- The V2 frozen design assets were not changed or copied into Android resources; production rendering uses CMS image URLs.
- The source design package's asset manifest dimensions need independent byte-level reconciliation: the previous package review found manifest dimensions that differ from the PNG source dimensions. Validation should use the source bytes and preserve the Frozen artwork.
- This code treats an absent role image as “图片暂不可用” and does not infer whether the source is missing, draft or unreachable.
- No production code issue is currently known; build and static-check status is recorded in the final delivery note.

This handoff is for implementation validation only. It is not a visual acceptance or high-fidelity runtime result.
