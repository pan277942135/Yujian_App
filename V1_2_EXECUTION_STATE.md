# Recognition Runtime V1.2 execution checkpoint

Task `YUJIAN_RECOGNITION_RUNTIME_13_STAGE_INTEGRATION_V1_2` continues on PR #317, branch `feature/orb-photo-stable-visual-closure-v1`.

Main baseline `74cad95b4f673bd9c8c60d16839f036d5886d933`. CP01 inherited from `81a0c257417c0be050e8dd57ace95e97619babf4`.

## Current checkpoint

CP02 is **VALIDATING**. Source commit `ab2407ab42459f12ccb8178df7ebe3badc396c13`.

The original Core Image now has its own graphics layer. The optional AGSL output draws over that image, so a transparent optical result cannot remove the source Core. Debug step diagnostics expose A `CORE_ONLY`, B `CORE_WITH_OPTICS`, and C `FULL_ORB_WAKE` in ORB_WAKE. They use the same painter, host and scene frame. Existing caustics, droplets, grass and ribbons remain in C. Shader creation failures log a warning and retain the original Core.

The reported observation is Core visible at PHOTO_STABLE and disappearing after enabling RenderEffect. The code also revealed that the only Core image previously carried that effect. The exact device-side AGSL failure mechanism remains unconfirmed.

Changed files: `AIOrbCore.kt`, `AIOrbMotion.kt`, `RecognitionStepDiagnosticControls.kt`, `RecognizingScreen.kt`, `AIOrbPhotoStableRenderingPolicyTest.kt`, `OrbGrassFrozenAssetsV11Test.kt`.

`git diff --check` passed before commit. Android unit tests, Debug compilation, resource validation and device visual A/B/C comparison are **NOT_RUN** at this checkpoint. This local runner has no Gradle executable, wrapper or Android SDK. The GitHub exact-HEAD workflow is the next available build path.

## Next exact action

1. Inspect exact-HEAD CI for CP02 source commit and fix any compile or unit test failures.
2. Preserve device AGSL visual comparison as `NOT_RUN` until actual evidence exists. Do not mark CP02 visual acceptance PASS based on static tests.
3. Inspect R06 V1.1 resource identity and implement CP03 on the same branch. Continue CP04–CP07 with separate source commits and state updates.
4. Build and hash the final Debug APK after CP07. APK state is **NOT_RUN**.

## CP03 code inspection and correction

The current renderer contains five Bezier path IDs `G01`, `G02`, `A01`, `A02`, `C01` and separate back/front depth planes. The shared stage frame drives the 600 ms local release with reversal after 340 ms. The photo perimeter overlay was nevertheless enabled throughout ORB_RELEASE. Its opacity is now held at zero until PERIMETER_ARRIVAL, including reduced motion. The B debug comparison now also hides the front ribbons. The perimeter transition test covers the dark release and later arrival.

The current Main resource SHA-256 values are geometry `1dfc85e651440d88c18d80daf51bbaa081e058fa60061bed6c8801aededf4cdc`, profile `8c7f998b043ddca8363054b75a93dbe6fab72cbdf31424b4fb71c4b502f21c10`, and SVG `d321c2bdd709145fadf73f52fc3f5d81a93c7e4e9d1074dc9adf783810287389`. They differ from the V1.1 expected prefixes in the handoff. These files remain current runtime candidates; their frozen-resource identity is **NOT_CONFIRMED**.

The first exact-HEAD PR CI on `3933778ef22140a83b45250d02c294c8ffe43ac9` failed before Android compilation. Control-plane admission treated the PR destination as canonical Main and rejected branch-local state files. Android CI stopped at the existing Empty Home V2 asset contract. Neither run validates CP02 code. The branch-specific Orb PHOTO_STABLE workflow is the appropriate independent unit/build path once a code change triggers it.

CP03 is **VALIDATING**. CP04, CP05, CP06 and CP07 are **NOT_STARTED**. FULL and ORB_ONLY integration acceptance is **NOT_RUN**.
