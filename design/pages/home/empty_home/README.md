# Empty Home V2

Role: **Brand / Environment Baseline**
Status: **FROZEN**

## Frozen visual references

- Core UI V1 canonical reference: `design/system/core_visual_v1/reference/empty_home_v2.png`
- Core UI V1 SHA-256: `30f95fc68b65d5a55552ba279753cac1fd979216679217377e43cea8e33cb85b`
- Immutable page source: `source/frozen/Empty_Home_Final_Design_V2.png`
- System authority: YuJian Core Visual System V1
- Verification: `reference_manifest.json` and `verify_core_ui_v1_references.py`

`intermediate` retains reproducible layer masters and validation; `shared/contracts` is the
Android traceability contract; Android-only runtime derivatives live under
`app/src/main/assets/empty_home_runtime_v2`.

## Product state and navigation

The page is the existing `HomeScreen` EMPTY state, shown only when `fishRecords.isEmpty()`.
Login/session state does not choose Empty vs Normal. It reuses the production capture/identify,
album-picker, and account-entry callbacks; it does not introduce a second Home, camera, or
gallery flow.

## Visual contract

- quiet morning lake
- brand copy is the emotional focus
- rod/line/bobber/ripple support atmosphere
- shared `YuJianPrimaryCaptureButton`
- no dashboard / no empty-data utility tone

## Motion

Use the Empty Home values in `design/system/core_visual_v1/tokens/motion_tokens.json` exactly.
