# Integration test build marker

- Product source: `8be20f3ac949f379a867bf9623f06bc7c0e72fa6` (Main after PR #316 and PR #314)
- Product source tree: `25fb0971f72022f9837083ea56c1471006ac2173`
- Build branch: `integration/main-integration-test-20261104`
- Purpose: trigger the standard Android CI build for an installable integration-test APK when the Main push workflow is waiting in the Actions queue.
- Product code: unchanged from Main. This branch adds only this provenance marker; no application source, Gradle, model, resource, or runtime file is modified.
- Validation: compare this branch against Main and confirm this marker is the only changed path before using the APK.

This file is build provenance only. It is not a product change and is not intended to be merged back into Main.
