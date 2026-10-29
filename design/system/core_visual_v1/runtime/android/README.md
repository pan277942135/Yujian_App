# YuJian Core UI V1 Android runtime evidence

These artifacts are captured from the debug-only `DesignSystemPreviewActivity`
using the native Compose components in `app/src/main/java/com/yujian/ai/ui/designsystem/`.
The gallery is not a product route and does not load the frozen page reference
PNGs.

Capture locally after building the debug APK:

```bash
bash scripts/capture_designsystem_runtime_evidence.sh \
  app/build/outputs/apk/debug/app-debug.apk \
  design/system/core_visual_v1/runtime/android
```

CI writes the same gallery to `build/designsystem-evidence` and uploads it as
the `YuJian-designsystem-runtime-evidence` artifact. `manifest.json` records
the SHA-256 digest for each screenshot and motion clip.
