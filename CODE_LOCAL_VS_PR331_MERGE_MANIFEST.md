# PR #331 × Code Local Merge Manifest

Integration branch: `qa/pr331-code-local-unified-20261115`
PR #331 source: `2e11dea1761296cdd19625bfc45a6c47be1376d9`
Code local source: `59e5c7b22c07ca512008b8376367126cf32df24f`
Actual common ancestor: `59e5c7b22c07ca512008b8376367126cf32df24f` (the expected `6692b5d` was not the actual merge base; the PR already contains Code HEAD).
Code snapshot: `c303acf5160ea37735490c0fdb4863f0449f8f1f`.

The original Code worktree was not changed. Generated worktrees, Python caches,
Gradle caches, APKs and branch-local dynamic state were preserved outside this
integration commit and are not product inputs. The only valid local delta was
`harness/build-log.md`; it was merged by preserving the PR log and appending the
two local 2026-11-07 entries.

| Class | File | PR #331 change | Code local change | Resolution | Verification |
|---|---|---|---|---|---|
| BOTH_OVERLAPPING | `harness/build-log.md` | Rebased/extended build evidence through 2026-11-15 | Two local 2026-11-07 evidence entries | Combined both append-only histories; no source behavior changed | Conflict resolved; `git diff --check` |

## PR331_ONLY — preserved unchanged

All files below were changed by PR #331 relative to the actual common ancestor,
and had no Code-local delta. They remain intact in the integration index.

| File | Resolution | Verification |
|---|---|---|
| `.github/workflows/android.yml` | PR331_ONLY | preserved |
| `app/build.gradle.kts` | PR331_ONLY | preserved |
| `app/src/androidTest/java/com/yujian/ai/OrbRenderIsolationRuntimeTest.kt` | PR331_ONLY | preserved |
| `app/src/androidTest/java/com/yujian/ai/RecognitionResultHandoffGuardrailRuntimeTest.kt` | PR331_ONLY | preserved |
| `app/src/androidTest/java/com/yujian/ai/RecognitionResultHandoffProductionNavigationTest.kt` | PR331_ONLY | preserved |
| `app/src/androidTest/java/com/yujian/ai/RecognitionRevealProductionRuntimeTest.kt` | PR331_ONLY | preserved |
| `app/src/main/assets/orb_fluid_v1/orb_fluid_refraction_v1.agsl` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/MainActivity.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/media/RecognitionImageStore.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/model/Models.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbCore.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbMotion.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/OrbStage02VisualEvidenceExport.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionStepDiagnosticControls.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionResultHandoffConsumer.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionResultProductionEvidenceWriter.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionResultRuntime.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealController.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealHandoff.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealOpticalRenderer.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealPlaybackLedger.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealSessionStore.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/screens/IdentifyScreen.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneAnimationController.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/core/WaterLightEnergy.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/core/WaterLightHost.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/core/WaterLightPathLedger.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbCausticsAdapter.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFilamentRenderer.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFluidRefractionProgramV1.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbStage02MaxVisualProfile.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbStage02RenderTrace.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbWakeBreathReleaseV11.kt` | PR331_ONLY | preserved |
| `app/src/main/java/com/yujian/ai/YujianApp.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/designsystem/components/aiorb/OrbRenderVisibilityContractTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/home/EmptyHomeMotionTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionFishFocusTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionPhotoFlowGeometryTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionVisualStateControllerTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/recognition/reveal/RecognitionResultRuntimeTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealPolicyTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/OrbFilamentRendererSamplingTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/OrbFluidOpticalIntegrationV1Test.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/OrbGrassFrozenAssetsV11Test.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/OrbStage02MaxVisualProfileTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/OrbWakeBreathReleaseV11Test.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/RecognitionSceneFrameTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/WaterLightEnergyTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/WaterLightHostBindingTest.kt` | PR331_ONLY | preserved |
| `app/src/test/java/com/yujian/ai/ui/waterlight/WaterLightPathLedgerTest.kt` | PR331_ONLY | preserved |
| `design/pages/account_privacy/Login/Page_Resource_Binding_V1.json` | PR331_ONLY | preserved |
| `design/pages/home/empty_home/Page_Resource_Binding_V1.json` | PR331_ONLY | preserved |
| `design/pages/home/normal_home/Page_Resource_Binding_V1.json` | PR331_ONLY | preserved |
| `design/pages/recognition/runtime/recognition_13_stage_timing_contract_v1_0.json` | PR331_ONLY | preserved |
| `design/pages/recognition/runtime/RECOGNITION_13_STAGE_TIMING_CONTRACT_V1_0.md` | PR331_ONLY | preserved |
| `design/recognition_structured_handoff_v1/wp1b_v3/FRAME_ASSET_BINDING_V3.csv` | PR331_ONLY | preserved |
| `design/recognition_structured_handoff_v1/wp1b_v3/FRAME_ASSET_BINDING_V3.json` | PR331_ONLY | preserved |
| `design/system/backgrounds/lake_glass_v2/evidence/android_shader_reuse/route_background_coverage_v2.json` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/iteration_001/change_manifest.json` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/iteration_001/iteration_report.md` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/iteration_001/next_iteration_plan.md` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/iteration_001/pixel_evidence_metrics.json` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/iteration_001/PR331_RENDER_EVIDENCE_INDEX_V1.json` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/iteration_001/PR331_RENDER_PIXEL_ANALYSIS_V1.csv` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/iteration_001/test_results.json` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/ORB_VISUAL_ISSUE_LEDGER.md` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/ORB_VISUAL_ITERATION_BASELINE.json` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/ORB_VISUAL_ITERATION_REPORT.md` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/R03_RENDER_CHAIN_AUDIT.md` | PR331_ONLY | preserved |
| `docs/qa/orb-stage02/pr331/R06_RENDER_CHAIN_AUDIT.md` | PR331_ONLY | preserved |
| `harness/PHASE_TASK_STATE_MACHINE.yaml` | PR331_ONLY | preserved |
| `harness/task_contracts/YUJIAN_ANDROID_DET_FISH_V03_PRODUCTION_RELEASE_AND_RUNTIME_INTEGRATION_V1.md` | PR331_ONLY | preserved |
| `harness/task_contracts/YUJIAN_EMPTY_HOME_V2_2_FROZEN_COMPOSITION_REPAIR_V1.md` | PR331_ONLY | preserved |
| `harness/task_contracts/YUJIAN_MAIN_BASELINE_RECOVERY_BOOTSTRAP_V1.md` | PR331_ONLY | preserved |
| `harness/task_contracts/YUJIAN_WP2_13_STAGE_RECOGNITION_EXPERIENCE_RUNTIME_CLOSURE_V3.md` | PR331_ONLY | preserved |
| `harness/UNIFIED_CONTRACT_REGISTRY.yaml` | PR331_ONLY | preserved |
| `MAIN_BASELINE_PRESERVATION_MANIFEST.json` | PR331_ONLY | preserved |
| `review/WP2_13_STAGE_VISUAL_CONSUMER_AUDIT_V1.md` | PR331_ONLY | preserved |
| `scripts/android_runtime/gates/recognition_result_handoff.sh` | PR331_ONLY | preserved |
| `scripts/android_runtime/tests/test_contract.sh` | PR331_ONLY | preserved |
| `scripts/android_runtime/verify_recognition_result_handoff.py` | PR331_ONLY | preserved |
| `scripts/bootstrap_android_recognition_assets.py` | PR331_ONLY | preserved |
| `scripts/design/tests/test_page_resource_binding.py` | PR331_ONLY | preserved |
| `scripts/design/validate_page_resource_binding.py` | PR331_ONLY | preserved |
| `scripts/export_apk_model_trace.py` | PR331_ONLY | preserved |
| `scripts/run_recognition_result_handoff_gate.sh` | PR331_ONLY | preserved |
| `scripts/test_bootstrap_android_recognition_assets.py` | PR331_ONLY | preserved |
| `scripts/verify_empty_home_runtime_v2.py` | PR331_ONLY | preserved |
| `scripts/verify_swos_static_visual_v1_1.py` | PR331_ONLY | preserved |

## Excluded from merge

No valid Code-local-only product source or resource file was found. The local
untracked set consists of branch-local state, existing worktrees and generated
Python bytecode. These are retained in the external snapshot and intentionally
not published to the QA branch.
