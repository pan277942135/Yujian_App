# Delivered APK startup crash

Original delivered APK SHA-256: `3f52e452f38ea718f1191b677371eff02890ed9d377df82787c5cdb2b0d04d0f`.

Reproduced on API 35 emulator in Actions `41156940839`, job `124405700367`, artifact `13025920621`. Cold launch fails in Home before Processing is loaded:

```
ResourceResolutionException: Error attempting to load resource: res/drawable-nodpi-v4/capture_button_main_v1.png
 at YuJianCaptureButton.kt:159
Caused by: NullPointerException: null cannot be cast to non-null type android.graphics.drawable.BitmapDrawable
```

The committed file was 750063 bytes, lacked the PNG signature, and ImageMagick reported an improper image header. Android could not decode it. Static compilation and model-asset gates had not decoded this UI raster.

Restored the approved transparent button master (`透明玻璃水环相机按钮.png`, original SHA-256 `9c24d789078a83e9cf3eb16aa7e558eac8f23e4fd8de14ea0ab3116e427dbe13`), encoded it as a 384x384 alpha WebP under the same Android resource identifier, and deleted the invalid PNG. Source and actual-APK raster decoding checks now cover the capture button, water core and mint background. A focused Android resource decode instrumentation test and normal-Home cold/warm startup check cover the replacement package.

Startup exception reports are written before Compose and routed to a native recovery/export UI on the following launch; Android 11+ can also recover previous app exit traces. Fatal errors still reach Android's original handler. Recovery is not a normal Home PASS. User records, recognition semantics and model assets are not altered.

The original diagnostic runner's initial shell-variable-scope failure is preserved in Actions `41156661116` as `INFRA_NOT_RUN`; it is not a startup PASS. The runner now executes a single checked-in shell script.
