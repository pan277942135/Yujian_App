#!/usr/bin/env python3
"""CI checks for the pinned P0-2 R01B assets and common Runtime renderer."""

from __future__ import annotations

import hashlib
import json
import math
import re
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/orb_r01b_v1_1"
EXPECTED = {
    "ORB_GRASS_DERIVED_MASK_PATH_V1_1.json": "f16a55c960b39f499fa8e3283fbe56dc3d53b0671f65e7f183caf7ecafbd313b",
    "ORB_GRASS_DERIVED_PATHS_V1_1.svg": "f0cfadf977b10fa122f61bfbc55a8d0368dd859dd94173468d5a9dae36178440",
    "orb_grass_back_b1_mask_v1_1.png": "d172b189061cf0e7811d249719202b180ad18ee0c48caca591f5477dac143c43",
    "orb_grass_back_b2_mask_v1_1.png": "3f03448815d2a34c777ca27ae3732f958286b54ca606280b9c8c0f78f55a4d22",
    "orb_grass_front_f1_mask_v1_1.png": "52b9885939a1fbb02717c3832e74acbeb02949732022b9145e97423c028515d0",
    "orb_grass_front_f2_mask_v1_1.png": "9a6708996207d3b855d17a8d0010b1572133142c3d7951f8b0687786c31a5fee",
    "orb_grass_back_combined_mask_v1_1.png": "38921cbe9ee5de3b3d0964366af05a334ce1beb13fd035cc4fe13629a3560142",
    "orb_grass_front_combined_mask_v1_1.png": "1c46d4a1f6c045c17dc1f48e3c5889a22a4fb581e216874963904094b6d8551d",
}
LEAVES = {
    "BACK_B1": (6200, 0, 0.018, 0.0, "orb_grass_back_b1_mask_v1_1.png"),
    "BACK_B2": (6200, 100, 0.018, 0.0, "orb_grass_back_b2_mask_v1_1.png"),
    "FRONT_F1": (4800, 120, 0.030, 0.003, "orb_grass_front_f1_mask_v1_1.png"),
    "FRONT_F2": (4800, 240, 0.027, 0.003, "orb_grass_front_f2_mask_v1_1.png"),
}


def check(condition: bool, label: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {label}")
    print(f"PASS: {label}")


def main() -> None:
    for name, expected_hash in EXPECTED.items():
        path = ASSETS / name
        check(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected_hash,
              f"frozen asset SHA matches: {name}")

    definition = json.loads((ASSETS / "ORB_GRASS_DERIVED_MASK_PATH_V1_1.json").read_text())
    check(definition["source"]["dimensions"] == [1254, 1254] and
          definition["source"]["sha256"] == "2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee",
          "source master identity and dimensions remain pinned")
    mask_names = [leaf["maskFile"] for leaf in definition["leaves"].values()]
    mask_names += [group["maskFile"] for group in definition["groups"].values()]
    check(len(mask_names) == 6, "four single-leaf and two combined masks are declared")
    grass_profile = (ROOT / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbGrassMotionProfileV11.kt").read_text()
    for name in mask_names:
        image = Image.open(ASSETS / name)
        check(image.size == (1254, 1254) and image.mode == "L", f"complete grayscale mask: {name}")

    for leaf_id, expected in LEAVES.items():
        leaf = definition["leaves"][leaf_id]
        curve = leaf["curveUV"]
        points = [coordinate for point in curve for coordinate in point]
        check(len(curve) == 4 and all(0.0 <= coordinate <= 1.0 for coordinate in points),
              f"{leaf_id} root/tip/control UV coordinates are valid")
        check(curve[0] == leaf["rootUV"] and curve[-1] == leaf["tipUV"],
              f"{leaf_id} Bezier endpoints map to root and leaf tip")
        actual = (leaf["periodMs"], leaf["phaseDeg"], leaf["amplitudeD"], leaf["rootMaxD"], leaf["maskFile"])
        check(actual == expected, f"{leaf_id} frozen period/phase/amplitude/mask parameters match")
        runtime_match = re.search(rf'Leaf\("{leaf_id}",([^)]*)\)', grass_profile)
        check(runtime_match is not None, f"{leaf_id} runtime Bezier profile is declared")
        runtime_values = [part.strip() for part in runtime_match.group(1).split(",")]
        runtime_coords = [float(value.rstrip("f")) for value in runtime_values[:8]]
        frozen_coords = [coordinate for point in curve for coordinate in point]
        check(len(runtime_values) == 13 and all(math.isclose(a, b, abs_tol=1e-6)
              for a, b in zip(runtime_coords, frozen_coords)),
              f"{leaf_id} runtime root/control/tip coordinates match frozen JSON")

    mapping = json.loads((ASSETS / "orb_grass_runtime_mapping_v1_1.json").read_text())
    check(mapping["uvMapping"]["crop"] == "none" and
          mapping["uvMapping"]["map"] == ["u_runtime=u_source", "v_runtime=v_source"],
          "source-wide UV maps to the full 384x384 Core without crop")
    check(mapping["sourceAlphaBoundary"]["maxMaskOutsideSourceAlphaPx"] == 0,
          "six mask source-alpha boundary check is recorded at zero pixels")
    check(mapping["verification"]["runtimeFromMasterBilinearAlphaCorrelationForRuntimeAlphaGT4"] > 0.99,
          "Runtime WebP full-square alpha is aligned to the frozen master")

    runtime = ROOT / "app/src/main/res/drawable-nodpi/processing_water_core_v1.webp"
    check(hashlib.sha256(runtime.read_bytes()).hexdigest() ==
          "daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09" and
          Image.open(runtime).size == (384, 384), "immutable Android Core WebP remains 384x384 with pinned SHA")

    core = (ROOT / "app/src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbCore.kt").read_text()
    host = (ROOT / "app/src/main/java/com/yujian/ai/ui/recognition/ProcessingWaterOrb.kt").read_text()
    renderer = (ROOT / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFilamentRenderer.kt").read_text()
    timeline = (ROOT / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbWakeBreathReleaseV11.kt").read_text()
    shader = (ASSETS / "orb_grass_warp_v1_1.agsl").read_text()
    check(core.count("painterResource(R.drawable.processing_water_core_v1)") == 1 and "WaterGrassField" not in core,
          "one Core texture is sampled; there is no duplicate grass overlay")
    check("BuildConfig.DEBUG" not in core and "BuildConfig.DEBUG" not in host and
          "OrbFilamentRendererMode.ORB_V1_CANDIDATE" in core and
          "OrbFilamentRendererMode.ORB_V1_CANDIDATE" in host,
          "Debug and Release use the same candidate renderer")
    check("inputImage.eval(coord)" in shader and "inputImage.eval(displacedCoord)" in shader and
          "pow(smoothstep(0.10, 1.0, s), 1.6)" in shader and
          "normalize(bezierTangent" in shader and "clamp(coord - displacement * surfaceSize" in shader,
          "local normal bending clamps the single original Core sample to its source bounds")
    path_math = (ROOT / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFilamentPathMathV1.kt").read_text()
    check("localRelease -> OrbFilamentPathMathV1.localReleaseMovementForStage(" in renderer and
          "spec, sceneMs, sceneFrame.stage, sceneFrame.stageProgress" in renderer and
          "movement(spec, opticalMs, frame.reducedMotion, event, eventStartMs)" in renderer and
          "stage != RecognitionSceneStage.ORB_RELEASE" in path_math and
          "releaseOffset(stage, progress, peakArcFraction)" in path_math and
          "R06_MAX_EXTENSION_DP / (pathLengthD * diameterDp)" in
          path_math and
          all(token not in shader for token in ("R01_grass_back.svg", "R01_grass_front.svg")),
          "R06 release is stage-local, split from continuous motion, and revoked SVGs are absent")
    for parameter in ("R06_ALPHA_GAIN = 1.28f", "R06_ALPHA_CAP = .82f",
                      "R06_MOVING_HIGHLIGHT_GAIN = 1.15f", "R06_RELEASE_HEAD_BRIGHTNESS_GAIN = 1.18f",
                      "R06_MAX_EXTENSION_DP = 14f"):
        check(parameter in timeline, f"frozen R06 parameter: {parameter}")
    scene_frame = (ROOT / "app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt").read_text()
    wake_match = re.search(r"\bORB_WAKE\((\d+)L", scene_frame)
    release_match = re.search(r"\bORB_RELEASE\((\d+)L", scene_frame)
    return_match = re.search(r"RELEASE_RETURN_PROGRESS = ([0-9.]+)f / ([0-9.]+)f", timeline)
    transition_match = re.search(r"S2_TRANSITION_LOCAL_MS = ([0-9.]+)f", timeline)
    if wake_match and release_match and return_match:
        wake_ms = int(wake_match.group(1))
        release_ms = int(release_match.group(1))
        return_progress = float(return_match.group(1)) / float(return_match.group(2))
        frozen_return_ms = wake_ms + release_ms * return_progress
        frozen_end_ms = wake_ms + release_ms
        frozen_return_ramp_ms = release_ms * (1.0 - return_progress)
    else:
        wake_ms = release_ms = -1
        frozen_return_ms = frozen_end_ms = frozen_return_ramp_ms = -1.0
    transition_ms = float(transition_match.group(1)) if transition_match else -1.0
    check(wake_ms == 350 and release_ms == 600 and math.isclose(frozen_return_ms, 690.0) and
          math.isclose(frozen_end_ms, 950.0) and math.isclose(frozen_return_ramp_ms, 260.0),
          "frozen 350/690/950/260 ms R06 timing maps to semantic stage progress")
    check(math.isclose(transition_ms, 450.0), "frozen 450 ms filament transition remains intact")
    print("RESULT: P0-2 frozen asset CI checks passed")


if __name__ == "__main__":
    main()
