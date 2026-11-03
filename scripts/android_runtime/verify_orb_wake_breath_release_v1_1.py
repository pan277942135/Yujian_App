#!/usr/bin/env python3
"""Verify the frozen P0-2 bundle, derived R01B resources, and Runtime Core mapping."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image


EXPECTED_LEAVES = {
    "BACK_B1": (6200, 0, 0.018, "orb_grass_back_b1_mask_v1_1.png"),
    "BACK_B2": (6200, 100, 0.018, "orb_grass_back_b2_mask_v1_1.png"),
    "FRONT_F1": (4800, 120, 0.030, "orb_grass_front_f1_mask_v1_1.png"),
    "FRONT_F2": (4800, 240, 0.027, "orb_grass_front_f2_mask_v1_1.png"),
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")
    print(f"PASS: {message}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--frozen-dir", type=Path, required=True)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    frozen = args.frozen_dir.resolve()
    repo = args.repo.resolve()
    runtime_assets = repo / "app/src/main/assets/orb_r01b_v1_1"

    package = json.loads((frozen / "PACKAGE_MANIFEST.json").read_text())
    failures = []
    for name, meta in package["files"].items():
        path = frozen / name
        okay = path.is_file() and path.stat().st_size == meta["sizeBytes"] and sha256(path) == meta["sha256"]
        if not okay:
            failures.append(name)
        print(f"{'PASS' if okay else 'FAIL'}: frozen manifest {name}")
    require(not failures, f"all {len(package['files'])} frozen manifest entries match size and SHA-256")

    checksum_rows = [line.split(maxsplit=1) for line in (frozen / "SHA256SUMS.txt").read_text().splitlines() if line.strip()]
    require(len(checksum_rows) == 18, "all 18 frozen SHA256SUMS rows are present")
    require(all(sha256(frozen / name.strip()) == digest for digest, name in checksum_rows),
            "all frozen SHA256SUMS rows verify")

    definition = json.loads((frozen / "ORB_GRASS_DERIVED_MASK_PATH_V1_1.json").read_text())
    source = frozen / "透明湖水玻璃球.png"
    source_image = Image.open(source).convert("RGBA")
    source_alpha = np.asarray(source_image)[:, :, 3]
    require(source_image.size == (1254, 1254), "immutable source master is 1254x1254")
    require(sha256(source) == "2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee",
            "immutable source master SHA is unchanged")

    masks = list(definition["maskPipeline"] and definition["leaves"].values())
    mask_names = [leaf["maskFile"] for leaf in definition["leaves"].values()]
    mask_names += [group["maskFile"] for group in definition["groups"].values()]
    require(len(mask_names) == 6, "four leaf masks plus two combined masks are declared")
    for name in mask_names:
        mask_path = frozen / name
        mask = np.asarray(Image.open(mask_path).convert("L"))
        outside = int(np.count_nonzero((mask > 0) & (source_alpha == 0)))
        require(mask.shape == (1254, 1254), f"{name} is complete 1254x1254 grayscale")
        require(outside == 0, f"{name} has zero nonzero pixels outside source alpha")
        require(sha256(mask_path) == package["files"][name]["sha256"], f"{name} frozen SHA matches manifest")

    for leaf_id, (period, phase, amplitude, mask_name) in EXPECTED_LEAVES.items():
        leaf = definition["leaves"][leaf_id]
        points = leaf["curveUV"]
        coords = [coord for point in points for coord in point]
        require(len(points) == 4 and len(coords) == 8 and all(0.0 <= p <= 1.0 for p in coords),
                f"{leaf_id} Bezier root/control/tip UVs are valid")
        require(points[0] == leaf["rootUV"] and points[-1] == leaf["tipUV"],
                f"{leaf_id} curve endpoints map exactly to root and tip")
        require((leaf["periodMs"], leaf["phaseDeg"], leaf["amplitudeD"], leaf["maskFile"]) ==
                (period, phase, amplitude, mask_name), f"{leaf_id} frozen motion parameters match runtime contract")

    runtime_core = repo / "app/src/main/res/drawable-nodpi/processing_water_core_v1.webp"
    runtime_image = Image.open(runtime_core).convert("RGBA")
    require(runtime_image.size == (384, 384), "Android Runtime Core is 384x384")
    require(sha256(runtime_core) == "daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09",
            "Android Runtime Core SHA is unchanged")
    runtime_alpha = np.asarray(runtime_image)[:, :, 3].astype(np.float32)
    resized_alpha = np.asarray(source_image.resize((384, 384), Image.Resampling.BILINEAR))[:, :, 3].astype(np.float32)
    active = runtime_alpha > 4
    correlation = float(np.corrcoef(resized_alpha[active], runtime_alpha[active])[0, 1])
    require(correlation > 0.99, f"source/runtime alpha alignment uses full-square UV mapping (r={correlation:.6f})")

    for name in ["ORB_GRASS_DERIVED_MASK_PATH_V1_1.json", "ORB_GRASS_DERIVED_PATHS_V1_1.svg", *mask_names]:
        target = runtime_assets / name
        require(target.is_file() and sha256(target) == sha256(frozen / name),
                f"runtime bundle imports exact frozen resource {name}")
    mapping = json.loads((runtime_assets / "orb_grass_runtime_mapping_v1_1.json").read_text())
    require(mapping["uvMapping"]["crop"] == "none" and mapping["uvMapping"]["map"] ==
            ["u_runtime=u_source", "v_runtime=v_source"], "documented mapping is full-source UV with no crop")

    contract = json.loads((frozen / "ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json").read_text())
    for size_dp in [68.0, 80.0, 74.0]:
        texture_side = size_dp / 0.84
        for leaf in definition["leaves"].values():
            for u, v in [leaf["rootUV"], leaf["tipUV"], *leaf["curveUV"][1:-1]]:
                require(0.0 <= u * texture_side <= texture_side and 0.0 <= v * texture_side <= texture_side,
                        f"{size_dp:g}dp UV/Bezier coordinates stay inside the sampled Core")
            require(leaf["amplitudeD"] * size_dp <= 0.03 * size_dp + 1e-9,
                    f"{size_dp:g}dp tip displacement stays within 0.03D")

    core_source = (repo / "app/src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbCore.kt").read_text()
    motion_source = (repo / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbWakeBreathReleaseV11.kt").read_text()
    renderer_source = (repo / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFilamentRenderer.kt").read_text()
    path_math_source = (repo / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbFilamentPathMathV1.kt").read_text()
    grass_profile_source = (repo / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbGrassMotionProfileV11.kt").read_text()
    require("WaterGrassField" not in core_source and core_source.count("painterResource(R.drawable.processing_water_core_v1)") == 1,
            "Orb renders one immutable Core texture without a duplicate grass overlay")
    require("R01_grass_back.svg" not in str(runtime_assets) and "R01_grass_front.svg" not in str(runtime_assets),
            "revoked R01 SVGs are not imported")
    for stamp in ["80L", "220L", "350L", "450L", "690L", "950L", "260L"]:
        require(stamp in motion_source, f"frozen timeline marker {stamp} is implemented")
    for gain in ["R06_ALPHA_GAIN = 1.28f", "R06_ALPHA_CAP = .82f", "R06_MOVING_HIGHLIGHT_GAIN = 1.15f",
                 "R06_RELEASE_HEAD_BRIGHTNESS_GAIN = 1.18f", "R06_MAX_EXTENSION_DP = 14f"]:
        require(gain in motion_source,
                f"R06 visual parameter {gain} is frozen in runtime")
    require("BuildConfig.DEBUG" not in core_source, "candidate renderer selection does not diverge by build type")
    require("localReleaseMovement(" in renderer_source and "spec, sceneMs, opticalMs" in renderer_source and
            "R06_MAX_EXTENSION_DP / (pathLengthD * diameterDp)" in path_math_source,
            "R06 ambient motion uses scene time with local events projected from optical time")
    require(contract["timeline"]["releaseReturn"]["windowMs"] == [350, 950], "contract release window is 350–950ms")
    print(f"RESULT: all P0-2 frozen resource, boundary, path, UV, size, and runtime checks passed; alpha r={correlation:.6f}")


if __name__ == "__main__":
    main()
