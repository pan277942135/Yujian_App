#!/usr/bin/env python3
"""Supplemental R00/R04/R06/manifest checks for the V2 design candidate.

This invokes the frozen V1 validator unchanged, then adds V2 assertions. It
does not make or imply Android runtime acceptance.
"""
from pathlib import Path
import hashlib, json, math, subprocess, sys, xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
PASS, FAIL, WARN = [], [], []

def check(name, ok, details=""):
    row = {"check": name, "passed": bool(ok)}
    if details:
        row["details"] = details
    (PASS if ok else FAIL).append(row)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def load(rel):
    return json.loads((ROOT / rel).read_text())

# Run and preserve every check in the original validator; do not edit it here.
legacy_script = ROOT / "tools/validate_orb_kit.py"
legacy_run = subprocess.run([sys.executable, str(legacy_script)], cwd=ROOT,
                            capture_output=True, text=True)
try:
    legacy = json.loads(legacy_run.stdout)
except Exception:
    legacy = {"status": "UNPARSEABLE", "passed_checks": 0,
              "failed_checks": 1, "failed": [legacy_run.stderr[-1200:]]}
check("unchanged V1 validator exits successfully", legacy_run.returncode == 0,
      f"{legacy.get('passed_checks')} passed / {legacy.get('failed_checks')} failed")
check("V1 validator retains original 231 baseline assertions",
      legacy.get("failed_checks") == 0 and legacy.get("passed_checks", 0) >= 231,
      "baseline was independently rerun on PRELIMINARY: 231 passed / 0 failed")

manifest = load("manifest_v1_2.json")
audit = load("audits/R00_runtime_webp_integrity.json")
runtime_rel = "assets/derived/processing_water_core_v1.webp"
runtime = ROOT / runtime_rel
runtime_sha = sha(runtime)
expected_sha = audit["runtime_webp"]["sha256"]
check("R00 packaged runtime copy matches independently audited SHA-256",
      runtime_sha == expected_sha, runtime_sha)
blob = hashlib.sha1(b"blob " + str(runtime.stat().st_size).encode() + b"\0" + runtime.read_bytes()).hexdigest()
check("R00 Git blob identity matches current Main/PR runtime file",
      blob == audit["runtime_webp"]["git_blob_sha"] == "a70a71c623d15355cecda5b0e834d9b0b871e568", blob)
with Image.open(runtime) as im:
    check("R00 runtime copy decodes as 384x384 RGBA with alpha",
          im.size == (384, 384) and im.mode == "RGBA" and im.getchannel("A").getextrema()[0] == 0,
          f"{im.size} {im.mode} alpha={im.getchannel('A').getextrema()}")
check("R00 audit reports high alpha agreement and measured edge residue",
      audit["comparison"]["alpha_iou_threshold_16"] >= 0.99 and
      audit["comparison"]["near_black_semi_transparent_pixel_count"] >= 0 and
      "fully_transparent_rgb_max" in audit["comparison"],
      f"alpha IoU={audit['comparison']['alpha_iou_threshold_16']}; transparent RGB max={audit['comparison']['fully_transparent_rgb_max']}")
check("R00 light and dark composites are both measured",
      {x["background"] for x in audit["background_composites"]} == {"light_mint", "dark_lake"})

geo = load("assets/paths/R06_orb_filament_path_geometry.json")
profile = load("profiles/R06_orb_filament_profile.json")
paths = geo["paths"]
expected_ids = {"G01", "G02", "A01", "A02", "C01"}
check("R06 preserves exactly the five frozen path IDs",
      len(paths) == 5 and {p["id"] for p in paths} == expected_ids)
material_for = {"green": "green_water_ribbon", "cyan": "cyan_water_ribbon", "gold": "warm_gold_filament"}
bindings = {p["path_id"]: p for p in profile["path_bindings"]}
check("R06 profile has one binding for every frozen path",
      set(bindings) == expected_ids and len(profile["path_bindings"]) == 5)
materials_ok = True
for key in set(material_for.values()):
    m = profile["materials"].get(key, {})
    required = {"base_color", "highlight_color", "refraction_color", "layer_composition",
                "cross_section", "width_profile", "opacity_profile", "luminance_profile",
                "specular_highlight", "feather", "blend_mode", "depth_model", "motion_binding"}
    materials_ok &= required <= set(m)
    materials_ok &= all(m.get(k, "").startswith("#") and len(m.get(k, "")) == 7
                        for k in ("base_color", "highlight_color", "refraction_color"))
    materials_ok &= m.get("blend_mode") == "SRC_OVER_PREMULTIPLIED"
check("R06 three numeric material contracts contain required layers and colors", materials_ok)

for p in paths:
    bid = bindings.get(p["id"], {})
    source_path = next((q for q in profile.get("paths", []) if q.get("id") == p["id"]), {})
    expected_material = material_for[p["type"]]
    check(f"R06 {p['id']} profile binding preserves material/depth/period/phase",
          bid.get("material_profile_id") == expected_material and bid.get("depth") == p["depth"] and
          bid.get("period_ms") == p["period_ms"] and bid.get("phase") == p["phase"] and
          bid.get("path_length_D", 0) > 0)
    # Sample every cubic segment densely and include the applied material width
    # multiplier plus deterministic deformation in the actual 14dp envelope.
    points = [p["start"]]
    for seg in p["segments"]:
        a, c1, c2, e = points[-1], seg["c1"], seg["c2"], seg["end"]
        for j in range(1, 513):
            t = j / 512; v = 1 - t
            points.append([v**3*a[k] + 3*v*v*t*c1[k] + 3*v*t*t*c2[k] + t**3*e[k] for k in (0, 1)])
    path_radius = max(math.hypot(x, y) for x, y in points)
    mat = profile["materials"][expected_material]
    multiplier = mat["material_width_multiplier"]
    deformation = max(bid["deformation_amplitude_D"],
                      source_path.get("wave_normal_amplitude_D", 0),
                      source_path.get("control_point_deformation", {}).get("amplitude_D", 0))
    actual_outer = path_radius + max(p["width_dp_min_max"]) * multiplier / 160 + deformation
    check(f"R06 {p['id']} full material stays within 14dp envelope",
          actual_outer <= profile["coordinate_contract"]["max_outer_radius_D"] + 1e-7,
          f"outer radius={actual_outer:.6f} D; expansion={(actual_outer-0.5)*80:.3f} dp")

svg = ET.parse(ROOT / "assets/paths/R06_orb_filament_paths.svg").getroot()
ns = {"s": "http://www.w3.org/2000/svg"}
svg_paths = svg.findall("s:path", ns)
svg_ids = {x.attrib.get("id") for x in svg_paths}
check("R06 SVG contains five editable cubic centerlines only",
      len(svg_paths) == 5 and svg_ids == expected_ids and
      all("C" in x.attrib.get("d", "") and "stroke" not in x.attrib and
          "stroke-width" not in x.attrib for x in svg_paths))
depth_ok = all(bindings[p["id"]]["depth"] == p["depth"] for p in paths)
check("R06 BACK/FRONT depth model matches frozen geometry", depth_ok)
motion = profile["motion_sampling"]
times = motion["sample_times_ms"]
positions = {}
for p in paths:
    vals=[]
    for t in times:
        phase=(p["phase"] + t/p["period_ms"]) % 1
        vals.append(0.5 - 0.5*math.cos(2*math.pi*phase))
    positions[p["id"]] = [round(x, 6) for x in vals]
check("R06 four declared sample times produce distinct moving-head positions",
      times == [0, 900, 1800, 2700] and all(len(set(v)) > 1 for v in positions.values()),
      json.dumps(positions, ensure_ascii=False))
check("R06 MotionClock, RELEASE and RETURN preserve same-state event binding",
      motion["clock"] == "MotionClock.opticalReferenceElapsedMs" and
      profile["events"]["RELEASE"]["state"] == "S2" and
      profile["events"]["RETURN"]["state"] == "S2" and
      profile["events"]["RETURN"]["clock_reset"] is False)

contract = load("profiles/R04_two_state_final_contract.json")
check("R04 contract contains only S1/S2 and explicitly excludes S3",
      contract["life_states"] == ["S1_IDLE", "S2_ACTIVE"] and "S3" in contract["states_excluded"])
check("R04 S1/S2 share the same immutable runtime WebP SHA",
      contract["core_asset"]["same_binary_for_all_states"] is True and
      contract["core_asset"]["immutable"] is True and contract["core_asset"]["sha256"] == runtime_sha)
check("R04 final state boards exist for both states and comparison",
      all((ROOT / "boards" / f).is_file() for f in
          ("R04_S1_idle_final.png", "R04_S2_active_final.png", "R04_S1_S2_comparison_final.png")))
check("R04 state images are readable and same canvas size",
      Image.open(ROOT/"boards/R04_S1_idle_final.png").size ==
      Image.open(ROOT/"boards/R04_S2_active_final.png").size)
check("R06 final material, depth, motion, and size boards exist",
      all((ROOT / "boards" / f).is_file() for f in (
          "R06_green_ribbon_material_breakdown.png", "R06_cyan_water_ribbon_material_breakdown.png",
          "R06_gold_filament_material_breakdown.png", "R06_front_back_spatial_relationship.png",
          "R06_four_motion_samples_final.png", "R06_68dp_80dp_light_dark_final.png",
          "R06_14dp_vs_extended_candidate.png")))

# Manifest integrity; the two validators rewrite their own result documents,
# so those report hashes are intentionally carried by the resource register and
# final release record rather than self-referenced in the manifest.
manifest_rows = {a.get("path"): a for a in manifest["assets"] if a.get("path")}
self_rewriting = {"audits/ORB_AUTOMATED_VALIDATION_REPORT.json",
                  "audits/ORB_AUTOMATED_VALIDATION_REPORT.md",
                  "audits/ORB_VALIDATION_REPORT_V2.json",
                  "audits/ORB_VALIDATION_REPORT_V2.md"}
rows_ok = True
external_repo_refs = {"app/src/main/res/drawable-nodpi/processing_water_core_v1.webp"}
for rel, rec in manifest_rows.items():
    file = ROOT / rel
    if rel in external_repo_refs:
        continue
    exists = file.is_file()
    if not exists:
        rows_ok = False
        continue
    if rel not in self_rewriting and rec.get("sha256"):
        rows_ok &= sha(file) == rec["sha256"] and file.stat().st_size == rec.get("file_size_bytes")
check("Manifest paths exist and every stored SHA/byte count matches", rows_ok)
missing = []
for file in ROOT.rglob("*"):
    if not file.is_file():
        continue
    rel=file.relative_to(ROOT).as_posix()
    if rel == "manifest_v1_2.json" or rel in self_rewriting or rel in external_repo_refs:
        continue
    if rel not in manifest_rows:
        missing.append(rel)
check("Manifest covers every package file except manifest/self-rewriting reports", not missing,
      ", ".join(missing[:12]))
check("Manifest has unique stable IDs and no duplicate SHA-256 payloads",
      len({a["id"] for a in manifest["assets"]}) == len(manifest["assets"]) and
      len([a["sha256"] for a in manifest["assets"] if a.get("sha256") and a.get("path") not in external_repo_refs]) ==
      len({a["sha256"] for a in manifest["assets"] if a.get("sha256") and a.get("path") not in external_repo_refs}))

WARN.append("Android runtime implementation and device rendering were intentionally not changed or claimed.")
WARN.append("V1 contains two inherited static warnings; R00 binary evidence supersedes the old missing-WebP note, while device acceptance remains pending.")
report = {
    "task_id": "YUJIAN_AI_ORB_RESOURCE_FINAL_CLOSURE_V2",
    "status": "PASS_WITH_REVIEW_PENDING" if not FAIL else "FAIL",
    "baseline": {"v1_original_assertions": 231, "preliminary_baseline": "231 PASS / 0 FAIL",
                 "current_v1_result": {k: legacy.get(k) for k in ("passed_checks", "failed_checks", "warnings")}},
    "v2_passed_checks": len(PASS), "v2_failed_checks": len(FAIL),
    "checks": PASS + FAIL, "warnings": WARN,
    "runtime": {"path": runtime_rel, "sha256": runtime_sha, "git_blob_sha": blob,
                "changed_in_android_runtime": False},
    "motion_sample_head_positions_u": positions,
    "visual_review": "PENDING_PRODUCT_OWNER",
    "legacy_validator_source_sha256": sha(legacy_script),
}
(ROOT / "audits/ORB_VALIDATION_REPORT_V2.json").write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n")
md = ["# Orb resource V2 validation", "", f"Status **{report['status']}** · {len(PASS)} passed / {len(FAIL)} failed.",
      "", "## V1 compatibility", "", "The unmodified baseline validator was run against the PRELIMINARY package and passed all 231 checks. The same script is run here unchanged; its dynamic total increases as new resources are added.",
      "", f"Current V1 result: {legacy.get('passed_checks')} passed / {legacy.get('failed_checks')} failed.",
      "", "## V2 checks", ""]
md += [f"- {'PASS' if r['passed'] else 'FAIL'} — {r['check']}" + (f" — {r['details']}" if r.get("details") else "") for r in PASS+FAIL]
md += ["", "## Warnings", ""] + [f"- {w}" for w in WARN] + [""]
(ROOT / "audits/ORB_VALIDATION_REPORT_V2.md").write_text("\n".join(md))
print(json.dumps(report, ensure_ascii=False, indent=2))
sys.exit(1 if FAIL or legacy_run.returncode else 0)
