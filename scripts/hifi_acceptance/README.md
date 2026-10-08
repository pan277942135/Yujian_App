# YuJian Android Hi-Fi Acceptance V1

This directory is the implementation of Frozen Design -> Real Android Render -> Visual Report -> User Physical Sign-off. It does NOT change page layouts or design authority.

## Authority

- The source manifest is design/qa/android_hifi_manifest_v1.json.
- All mapped Frozen raster files are read from the exact checked-out commit.
- The existing Empty Home / Recognition / Auth parity scripts remain the per-page technical gates.
- A screenshot and an APK build are not evidence of visual parity unless their provenance and matching commit SHA are established.
- No fake, resized or assistant-generated screenshots may be used as runtime input. The reference may be scaled for a matching aspect ratio; runtime input is never resized.
- The Camera capture screen has no confirmed static Frozen authority mapping; V1 deliberately reports FAIL_AUTHORITY until its correct visual authority is mapped. Capture Transition is NOT an acceptable Camera surrogate.
- My Catches PA-06 has a Frozen image but no included runtime screenshot producer; this is BLOCKED, not PASS.

## Capture rule

After navigating the actual installed application to the required surface on the API 28 runner, capture with:

    python3 scripts/hifi_acceptance/capture_verified_surface.py \
      --adb /opt/android-sdk/platform-tools/adb \
      --package com.yujian.ai \
      --build-sha "$GITHUB_SHA" \
      --output evidence/runtime/normal-home-v1/normal-home-v1/04_normal_home_runtime_1080x1920.png \
      --width 1080 --height 1920

The helper validates resumed Android Activity, focused Window, process liveness, and screenshot bytes. It produces a sibling .provenance.json containing screenshot SHA-256 and commit SHA. It aborts if the foreground app changes during capture. A missing or altered provenance record causes BLOCKED.

Existing Normal Home captures are now routed through this verified helper. Other gates must add the helper (or equivalent authentic producer with the same proof schema) as their instrumentation screenshots become stable; absent provenance is not grandfathered in.

## CI behavior

Android CI still builds a single APK / androidTest APK pair using its existing Build Job. The new fail-closed unit tests execute during Build.

Once self-hosted API 28 jobs finish, a separate ubuntu-latest hifi-audit job downloads available runtime-gate artifacts and produces:
- report.json: machine-readable per-surface classification and ROI MAE
- report.md: GitHub Actions job summary
- index.html: side-by-side comparison report
- per-surface frozen.png, runtime.png, overlay.png, diff.png, side-by-side.png

The report job does not start emulators or duplicate the heavy runtime matrix. On main, it enforces --strict and fails if a required case is BLOCKED, FAIL_* or REVIEW_REQUIRED. A green Build Job is NOT a visual sign-off; a green report job would only mean all automated checks passed, not that a person accepted the UX.

## Status contract

- FAIL_TEST: Instrumentation failed. Visual images cannot mask this.
- FAIL_EVIDENCE: Gate failed evidence capture, capture is blank, image dimensions invalid, or structure impossible.
- FAIL_AUTHORITY: No correct Frozen reference mapped.
- FAIL_VISUAL: Authenticated runtime image exceeds a named ROI threshold.
- BLOCKED: No matching run, screenshot, run hash, foreground proof, or capture producer.
- REVIEW_REQUIRED: Data-driven / photographic surface has authentic screenshot evidence but needs human comparison.
- AUTO_CHECK_PASS: Automated ROI and provenance checks passed; user physical visual acceptance remains pending.

The six required modules are Camera, Result, FishRecordDetail, Auth, Home and MyCatches. UI correctness is NOT inferred from unit-test pass percentages.

## Run locally

    python3 -m pip install Pillow==11.3.0
    python3 scripts/hifi_acceptance/report.py --validate-manifest
    python3 -m unittest discover -s scripts/hifi_acceptance/tests -p 'test_*.py' -v
    python3 scripts/hifi_acceptance/report.py \
      --artifacts /path/to/downloaded/runtime-artifacts \
      --output build/hifi-report \
      --build-sha <40-character-commit-sha>

Outputs are inspectable offline; index.html uses only relative assets. For manually reviewed states, the user owns the final PASS/FAIL decision and a separate signed/annotated acceptance record must be retained with device information, screenshots and the exact APK SHA-256.
