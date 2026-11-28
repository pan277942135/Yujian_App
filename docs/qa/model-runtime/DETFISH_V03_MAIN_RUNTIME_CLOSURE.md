# Main model runtime closure

Source baseline: `03db3692669847084d7e48fc3128f85cbf832af9`.

The Android build verifies the Main-committed `det-fish-v03.zip` and the existing production ONNX bytes before packaging. The compatibility asset filename still contains `v0_1`, but its verified contents are DET_FISH v0.3 (`98257b953b0e9f8aa673572fe4a9749a730ed8a2c014a42b7c492a0e3592c9a1`). The classifier uses the Main-committed MODEL_CROP_M1 v0.2 TFLite (`29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18`). Bootstrap no longer downloads or overwrites either model from a mutable Release. It generates a build-scoped schema 2 snapshot of those actual bytes. Runtime accepts this snapshot and validates model, metadata, class map and tensor contract before inference. Existing schema 1 Release snapshots remain readable.

The v0.3 ZIP contains no Golden Manifest or test images. `verify_production_detector.py` reports `golden_cases=NOT_RUN_MISSING_MAIN`; it does not certify output parity. Device inference, BBox/crop/Top1 parity and physical performance remain `NOT_RUN` until actual v0.3 Golden input or user-device evidence exists. A CI build and APK trace verify packaging and provenance, not inference output correctness. The original Admission result remains FAIL for missing dynamic state files; no PASS is inferred from this work.

To reproduce the basic checks: `python3 -m unittest discover -s scripts -p 'test_*.py' -q`, `BUILD_SHA=$(git rev-parse HEAD) python3 scripts/bootstrap_android_recognition_assets.py`, then run the Android CI workflow on the exact branch HEAD and inspect its `apk_model_trace.json` and `SHA256SUMS` artifacts. The generated `model_release_contract.json` is build scoped and is not committed.
