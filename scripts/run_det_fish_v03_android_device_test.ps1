param(
    [string]$OutputDir = (Join-Path $PSScriptRoot "device-evidence")
)

$ErrorActionPreference = "Stop"
$AppId = "com.yujian.ai.uiv2"
$TestComponent = "com.yujian.ai.uiv2.test/androidx.test.runner.AndroidJUnitRunner"
$TestClass = "com.yujian.ai.DetectorOrientationRetryRuntimeTest#caseBNightFlashRunsV03DetectorClassifierAndMatchesPythonCpuReference"
$DeviceFiles = "/sdcard/Android/data/$AppId/files/det-fish-v03-evidence"
$AppApk = Join-Path $PSScriptRoot "app-debug.apk"
$TestApk = Join-Path $PSScriptRoot "app-debug-androidTest.apk"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "BLOCKED: Android Platform Tools (adb) is not installed or not on PATH."
}
if (-not (Test-Path $AppApk) -or -not (Test-Path $TestApk)) {
    throw "BLOCKED: app-debug.apk and app-debug-androidTest.apk must be beside this script."
}

$deviceLines = (& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\s+device$" }
if ($deviceLines.Count -ne 1) {
    throw "BLOCKED: connect exactly one unlocked Android device with USB debugging enabled. Found $($deviceLines.Count)."
}

if (Test-Path (Join-Path $PSScriptRoot "SHA256SUMS")) {
    Get-Content (Join-Path $PSScriptRoot "SHA256SUMS") | ForEach-Object {
        if ($_ -match "^([0-9a-fA-F]{64})\s+(.+)$") {
            $expected = $Matches[1].ToLowerInvariant()
            $relative = $Matches[2].TrimStart("*")
            $path = Join-Path $PSScriptRoot $relative
            if (-not (Test-Path $path)) { throw "Missing bundle file: $relative" }
            $actual = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actual -ne $expected) { throw "SHA256 mismatch: $relative expected=$expected actual=$actual" }
            Write-Host "SHA256 OK: $relative"
        }
    }
}

& adb wait-for-device
if ($LASTEXITCODE -ne 0) { throw "adb wait-for-device failed." }
$api = (& adb shell getprop ro.build.version.sdk | Out-String).Trim()
$model = (& adb shell getprop ro.product.model | Out-String).Trim()
$manufacturer = (& adb shell getprop ro.product.manufacturer | Out-String).Trim()
$boot = (& adb shell getprop sys.boot_completed | Out-String).Trim()
if ($api -ne "36" -or $boot -ne "1") {
    throw "BLOCKED: expected boot-complete Android API 36; got api=$api boot=$boot model=$model."
}
if ($model -notlike "*CMS-AL20*") {
    throw "BLOCKED: connected device is '$manufacturer $model', not the requested HUAWEI CMS-AL20."
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
@(
    "device_manufacturer=$manufacturer"
    "device_model=$model"
    "android_api=$api"
    "source_sha=$((Get-Content (Join-Path $PSScriptRoot 'apk_model_trace.json') -Raw | ConvertFrom-Json).build_sha)"
    "app_apk_sha256=$((Get-FileHash $AppApk -Algorithm SHA256).Hash.ToLowerInvariant())"
    "test_apk_sha256=$((Get-FileHash $TestApk -Algorithm SHA256).Hash.ToLowerInvariant())"
) | Set-Content (Join-Path $OutputDir "device-provenance.txt") -Encoding UTF8
(& adb version) | Out-File (Join-Path $OutputDir "adb-version.txt") -Encoding UTF8

& adb install -r $AppApk
if ($LASTEXITCODE -ne 0) { throw "App APK installation failed." }
& adb install -r $TestApk
if ($LASTEXITCODE -ne 0) { throw "AndroidTest APK installation failed." }
(& adb shell pm list instrumentation) | Out-File (Join-Path $OutputDir "installed-instrumentation.txt") -Encoding UTF8
(& adb shell pm path $AppId) | Out-File (Join-Path $OutputDir "installed-app-path.txt") -Encoding UTF8
& adb logcat -c

& adb shell am instrument -w -r -e class $TestClass $TestComponent 2>&1 |
    Tee-Object -FilePath (Join-Path $OutputDir "instrumentation.log")
$instrumentationExit = $LASTEXITCODE

(& adb logcat -d -v threadtime) | Out-File (Join-Path $OutputDir "logcat-full.txt") -Encoding UTF8
(& adb shell dumpsys package $AppId) | Out-File (Join-Path $OutputDir "package-dump.txt") -Encoding UTF8
(& adb shell dumpsys meminfo $AppId) | Out-File (Join-Path $OutputDir "memory-after-test.txt") -Encoding UTF8

$deviceEvidenceDir = Join-Path $OutputDir "android-app-files"
New-Item -ItemType Directory -Force -Path $deviceEvidenceDir | Out-Null
& adb pull $DeviceFiles $deviceEvidenceDir
if ($LASTEXITCODE -ne 0) {
    throw "Could not pull app evidence from $DeviceFiles."
}

$evidenceJson = Join-Path $deviceEvidenceDir "det-fish-v03-evidence\android_inference_evidence.json"
if ($instrumentationExit -ne 0 -or -not (Select-String -Path (Join-Path $OutputDir "instrumentation.log") -Pattern "OK \([0-9]+ tests?\)" -Quiet) -or
    (Select-String -Path (Join-Path $OutputDir "instrumentation.log") -Pattern "FAILURES!!!" -Quiet) -or
    -not (Select-String -Path (Join-Path $OutputDir "instrumentation.log") -Pattern "DET_FISH_V03_REAL_INFERENCE" -Quiet) -or
    -not (Test-Path $evidenceJson)) {
    throw "FAIL: Android instrumentation/evidence gate did not pass. Preserve $OutputDir for diagnosis."
}

$hashLines = Get-ChildItem $OutputDir -Recurse -File |
    Where-Object { $_.Name -ne "SHA256SUMS" } |
    ForEach-Object {
        $relative = $_.FullName.Substring((Resolve-Path $OutputDir).Path.Length + 1).Replace("\", "/")
        $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relative"
    }
$hashLines | Set-Content (Join-Path $OutputDir "SHA256SUMS") -Encoding ASCII
$archive = "$($OutputDir.TrimEnd('\','/')).zip"
if (Test-Path $archive) { Remove-Item $archive -Force }
Compress-Archive -Path $OutputDir -DestinationPath $archive -Force
Write-Host "ANDROID_DEVICE_INFERENCE_PASS"
Write-Host "EVIDENCE_DIR=$OutputDir"
Write-Host "EVIDENCE_ARCHIVE=$archive"
