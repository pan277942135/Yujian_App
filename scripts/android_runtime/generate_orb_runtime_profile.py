#!/usr/bin/env python3
"""Generate/check the immutable Android projection of the AI Orb R02-R06 JSON."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
DESIGN = ROOT / "design/system/components/ai_orb_core"
OUTPUT = ROOT / "app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbRuntimeProfileV1.kt"
TEMPLATE = (ROOT / "scripts/android_runtime/OrbRuntimeProfileV1.kt.in").read_text(encoding="utf-8")


def read_json(name: str) -> tuple[dict, str]:
    path = DESIGN / name
    raw = path.read_bytes()
    return json.loads(raw), hashlib.sha256(raw).hexdigest()


def f(value: float) -> str:
    text = format(float(value), ".8g")
    if "." not in text and "e" not in text.lower():
        text += ".0"
    return text + "f"


def color(value: str) -> str:
    return "0x" + value.lstrip("#").upper() + "L"


def point(value: list[float]) -> str:
    return f"OrbPointV1({f(value[0])}, {f(value[1])})"


def list_of_points(values: list[dict], value_key: str, coordinate_key: str = "u") -> str:
    return "listOf(" + ", ".join(
        f"OrbCurvePointV1({f(item[coordinate_key])}, {f(item[value_key])})" for item in values
    ) + ")"


def quoted(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def generate() -> str:
    r02, sha02 = read_json("profiles/R02_orb_droplet_profile.json")
    r03, sha03 = read_json("profiles/R03_orb_sunlight_profile.json")
    r04, sha04 = read_json("profiles/R04_two_state_final_contract.json")
    r04t, sha04t = read_json("profiles/R04_transition_profile.json")
    r05, sha05 = read_json("profiles/R05_orb_caustics_profile.json")
    r06, sha06 = read_json("profiles/R06_orb_filament_profile.json")
    geometry, sha_geometry = read_json("assets/paths/R06_orb_filament_path_geometry.json")
    core_path = ROOT / "app/src/main/res/drawable-nodpi/processing_water_core_v1.webp"
    core_sha = hashlib.sha256(core_path.read_bytes()).hexdigest()

    if r04.get("life_states") != ["S1_IDLE", "S2_ACTIVE"]:
        raise SystemExit("R04 must define exactly S1_IDLE and S2_ACTIVE")
    if len(r02.get("droplets", [])) != r02.get("count"):
        raise SystemExit("R02 droplet count does not match its deterministic records")
    if {path["id"] for path in geometry.get("paths", [])} != {path["id"] for path in r06.get("paths", [])}:
        raise SystemExit("R06 profile and geometry path IDs differ")
    if core_sha != r04["core_asset"]["sha256"]:
        raise SystemExit(f"R00 WebP is not the immutable resource baseline: {core_sha}")
    reduced_match = re.search(r"phase\s+([0-9.]+)", r06["performance"]["reduce_motion"])
    if not reduced_match:
        raise SystemExit("R06 Reduce Motion phase is missing")

    replacements: dict[str, str] = {
        "__R02_SHA__": sha02,
        "__R03_SHA__": sha03,
        "__R04_SHA__": sha04,
        "__R04T_SHA__": sha04t,
        "__R05_SHA__": sha05,
        "__R06_SHA__": sha06,
        "__GEOMETRY_SHA__": sha_geometry,
        "__CORE_SHA__": core_sha,
        "__R04_ACTIVE_MS__": str(r04t["S1_to_S2"]["ms"]),
        "__R04_RETURN_MS__": str(r04t["S2_to_S1"]["ms"]),
        "__R04_ENTRANCE_MS__": str(r04t["entrance_morph_ms"]),
        "__R02_LOW_COUNT__": str(r02["low_performance_count"]),
        "__R02_VERTICAL_DRIFT__": f(r02["vertical_drift_max_D"])[:-1],
        "__R02_LATERAL_DRIFT__": f(r02["lateral_drift_max_D"])[:-1],
        "__R02_FIXED_AZIMUTH__": str(r02["sun_glint_shared_direction"]).lower(),
        "__R03_SOURCE_U__": f(r03["light"]["sourceUV"][0])[:-1],
        "__R03_SOURCE_V__": f(r03["light"]["sourceUV"][1])[:-1],
        "__R03_SUN_COLOR__": color(r03["light"]["color"])[:-1],
        "__R03_GLASS_ALPHA__": f(r03["layers"]["glass_specular"]["alpha_max"])[:-1],
        "__R03_GLASS_PERIOD__": str(r03["layers"]["glass_specular"]["period_ms"]),
        "__R03_GLASS_TRANSLATION__": f(r03["layers"]["glass_specular"]["max_translation_D"])[:-1],
        "__R03_REFRACTION_ALPHA__": f(r03["layers"]["core_refraction"]["alpha_max"])[:-1],
        "__R03_REFRACTION_PERIOD__": str(r03["layers"]["core_refraction"]["period_ms"]),
        "__R03_CAUSTIC_ALPHA__": f(r03["layers"]["caustic_response"]["alpha_max"])[:-1],
        "__R03_GRASS_ALPHA__": f(r03["layers"]["existing_grass_response"]["alpha_max"])[:-1],
        "__R03_DROPLET_ALPHA__": f(r03["layers"]["droplet_specular"]["alpha_max"])[:-1],
        "__R03_MAX_ADDITIVE__": f(r03["guards"]["max_extra_additive_alpha"])[:-1],
        "__R03_FIXED_AZIMUTH__": str(r03["light"]["fixed_azimuth"]).lower(),
        "__R05_INNER_RADIUS__": f(r05["mask"]["inner_radius_D"])[:-1],
        "__R05_EDGE_FADE__": f(r05["mask"]["edge_fade_D"])[:-1],
        "__R05_NORMAL_TEXTURE__": str(r05["performance"]["normal_texture_px_80dp"]),
        "__R05_LOW_TEXTURE__": str(r05["performance"]["low_texture_px_80dp"]),
        "__R05_NORMAL_INTERVAL__": str(r05["performance"]["normal_min_interval_ms"]),
        "__R05_LOW_INTERVAL__": str(r05["performance"]["low_min_interval_ms"]),
        "__R05_BUFFER_COUNT__": str(r05["performance"]["buffers"]),
        "__R06_MAX_OUTSIDE__": f(geometry["coordinate_system"]["max_outside_dp"])[:-1],
        "__R06_NORMAL_STEPS__": str(r06["performance"]["normal_steps_per_path"]),
        "__R06_LOW_STEPS__": str(r06["performance"]["low_steps_per_path"]),
        "__R06_REDUCED_PHASE__": f(float(reduced_match.group(1)))[:-1],
        "__R06_EVENT_SWITCH_MS__": str(r06["events"]["RETURN"]["switch_ms"]),
        "__R06_EXIT_TAIL_FADE__": str("fade" in r06["events"]["EXIT"]["rule"]).lower(),
    }
    replacements["__R02_DROPLETS__"] = "\n".join(
        "        OrbDropletSpecV1(%s, %s, %s, %s, %s, %s, %sL, %s)," % (
            quoted(item["id"]), f(item["uv"][0]), f(item["uv"][1]),
            f(item["diameter_dp_at_80"]), f(item["body_alpha"]), f(item["rim_alpha"]),
            item["period_ms"], f(item["phase"]),
        ) for item in r02["droplets"]
    )
    replacements["__R05_LAYERS__"] = "\n".join(
        "        OrbCausticLayerV1(%s, %sL, %s, %s, %s, %s)," % (
            quoted(layer["id"]), layer["period_ms"], f(layer["frequency"]), f(layer["alpha"]),
            f(layer["advection_D_s"][0]), f(layer["advection_D_s"][1]),
        ) for layer in r05["layers"]
    )
    replacements["__R06_MOTION_KEYS__"] = "\n".join(
        f"        MotionKey({f(progress)}, {f(distance)})," for progress, distance in r06["speed_phase_distance"]
    )

    materials: list[str] = []
    for name, material in r06["materials"].items():
        layers = []
        for layer in material["layer_composition"]:
            layers.append(
                "OrbMaterialLayerV1(%s, %s, %s, %s, %s, %s, %s, %s)" % (
                    quoted(layer["id"]), f(layer["width_multiplier"]), f(layer["alpha_max"]),
                    "null" if "blur_sigma_dp" not in layer else f(layer["blur_sigma_dp"]),
                    color(layer["color"]),
                    "null" if "offset_normal_fraction" not in layer else f(layer["offset_normal_fraction"]),
                    "null" if "length_fraction" not in layer else f(layer["length_fraction"]),
                    str(layer.get("travels_with_head", False)).lower(),
                )
            )
        spec = material["specular_highlight"]
        lum = material["luminance_profile"]
        feather = material["feather"]
        motion = material["motion_binding"]
        material_args = [
            quoted(material["material_id"]), color(material["base_color"]),
            color(material["highlight_color"]), color(material["refraction_color"]),
            f(material["material_width_multiplier"]), "listOf(" + ", ".join(layers) + ")",
            list_of_points(material["cross_section"], "opacity", "offset"),
            list_of_points(material["width_profile"], "width_multiplier"),
            list_of_points(material["opacity_profile"], "alpha_multiplier"),
            f(lum["head"]), f(lum["body"]), f(lum["tail"]), quoted(spec["shape"]),
            f(spec["head_radius_dp"][0]), f(spec["head_radius_dp"][1]), f(spec["head_alpha"]),
            f(spec["band_length_fraction"]), f(feather["sigma_dp"]), f(feather["alpha_max"]),
            color(feather["color"]), f(motion["trail_length_fraction"]),
            f(motion["deformation_amplitude_D"]),
        ]
        materials.append(f"        {quoted(name)} to OrbFilamentMaterialV1(" + ", ".join(material_args) + "),")
    replacements["__R06_MATERIALS__"] = "\n".join(materials)

    geometry_by_id = {item["id"]: item for item in geometry["paths"]}
    material_id_by_type = {"green": "green_water_ribbon", "gold": "warm_gold_filament", "cyan": "cyan_water_ribbon"}
    profile_by_id = {item["id"]: item for item in r06["paths"]}
    path_lines = []
    for path in r06["paths"]:
        geom = geometry_by_id[path["id"]]
        src = profile_by_id[path["id"]]
        segments = []
        for segment in geom["segments"]:
            segments.append("OrbCubicV1(%s, %s, %s)" % (
                point(segment["c1"]), point(segment["c2"]), point(segment["end"]),
            ))
        path_lines.append(
            "        OrbFilamentSpecV1(%s, %s, %s, %s, listOf(%s), %s, %s, %s, %sL, %s, %s, %s, %s, %s, %s, %sL, %s)," % (
                quoted(path["id"]), quoted(material_id_by_type[geom["type"]]),
                str(path["z"] == "FRONT").lower(), point(geom["start"]), ", ".join(segments),
                f(geom["arc_length_D"]), f(geom["max_radius_D"]), f(src["max_outer_radius_D"]),
                path["period_ms"], f(path["phase"]), f(path["width_dp_min_max"][0]),
                f(path["width_dp_min_max"][1]), f(path["alpha_min_max"][0]), f(path["alpha_min_max"][1]),
                f(path["wave_normal_amplitude_D"]), path["control_point_deformation"]["period_ms"],
                f(path["control_point_deformation"]["amplitude_D"]),
            )
        )
    replacements["__R06_PATHS__"] = "\n".join(path_lines)

    result = TEMPLATE
    for key, value in replacements.items():
        result = result.replace(key, value)
    unresolved = re.findall(r"__[A-Z0-9_]+__", result)
    if unresolved:
        raise SystemExit("Unresolved Kotlin template fields: " + ", ".join(sorted(set(unresolved))))
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if the checked-in Kotlin projection is stale")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    generated = generate()
    if args.check:
        existing = args.output.read_text(encoding="utf-8")
        if existing != generated:
            print(f"ORB_PROFILE_PARITY=FAIL stale generated Kotlin: {args.output}", file=sys.stderr)
            return 1
        print("ORB_PROFILE_PARITY=PASS R02-R06 JSON matches generated immutable Kotlin profile")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(generated, encoding="utf-8")
    print(f"Generated {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
