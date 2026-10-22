#!/usr/bin/env python3
"""Validate NH01 Responsive Layout V2 schema, source provenance and control-plane bindings."""

from __future__ import annotations
import hashlib, json, struct, sys
from pathlib import Path
try:
    import yaml
    from jsonschema import Draft202012Validator
except ImportError as exc:
    print("ERROR: install PyYAML and jsonschema before running this validator", file=sys.stderr)
    raise SystemExit(2) from exc

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_PATH = ROOT / "design/system_v3/07_Responsive_System/Responsive_Layout_Contract_Schema_V1.json"
CONTRACT_PATH = ROOT / "design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json"
PROFILES_PATH = ROOT / "design/system_v3/07_Responsive_System/Viewport_Profiles_V1.json"
MANIFEST_PATH = ROOT / "design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json"
AUTHORITY_PATH = ROOT / "design/pages/home/normal_home/authority/authority_map.json"
NAV_PATH = ROOT / "design/pages/home/normal_home/navigation.json"
FEATURE_PATH = ROOT / "harness/FEATURE_MATRIX.yaml"
PHASE_PATH = ROOT / "harness/PHASE_TASK_STATE_MACHINE.yaml"
REGISTRY_PATH = ROOT / "harness/UNIFIED_CONTRACT_REGISTRY.yaml"
errors: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition: errors.append(message)
def read_json(path: Path):
    try: return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"JSON read failed {path.relative_to(ROOT)}: {exc}"); return {}
def read_yaml(path: Path):
    try: return yaml.safe_load(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"YAML read failed {path.relative_to(ROOT)}: {exc}"); return {}
def sha256(path: Path) -> str: return hashlib.sha256(path.read_bytes()).hexdigest()
def png_size(path: Path):
    data=path.read_bytes()
    if len(data)<24 or data[:8]!=b"\x89PNG\r\n\x1a\n": return None
    return struct.unpack(">II",data[16:24])

schema=read_json(SCHEMA_PATH); contract=read_json(CONTRACT_PATH); profiles=read_json(PROFILES_PATH)
manifest=read_json(MANIFEST_PATH); authority=read_json(AUTHORITY_PATH); navigation=read_json(NAV_PATH)
feature_matrix=read_yaml(FEATURE_PATH); phase_machine=read_yaml(PHASE_PATH); registry=read_yaml(REGISTRY_PATH)
try:
    Draft202012Validator.check_schema(schema)
    for error in Draft202012Validator(schema).iter_errors(contract):
        location=".".join(str(part) for part in error.absolute_path) or "<root>"
        errors.append(f"contract schema violation at {location}: {error.message}")
except Exception as exc: errors.append(f"schema validation failed: {exc}")

require(contract.get("contract_id")=="NH01_RESPONSIVE_LAYOUT_V2","contract_id mismatch")
require(contract.get("status") in {"PROPOSED_FOR_CANONICAL_MAIN_REVIEW","SPEC_FROZEN"},"contract status invalid")
require(contract.get("responsive_model",{}).get("model")=="CONSTRAINT_DRIVEN_SINGLE_POLICY","must use one constraint-driven policy")
require(contract.get("responsive_model",{}).get("viewport_keyed_layout_overrides") is False,"per-viewport full-layout overrides are forbidden")
require(contract.get("native_acceptance",{}).get("status")=="NOT_VERIFIED","Android runtime must remain NOT_VERIFIED")
require(contract.get("native_acceptance",{}).get("required_case_count")==24,"current native matrix must remain 24 cases")
require(contract.get("acceptance",{}).get("runtime_status_independent") is True,"design status must be independent of runtime acceptance")

anchors=contract.get("visual_anchors",[])
require(len(anchors)==6,"exactly six frozen visual anchors must be registered (original + M01-M05)")
manifest_assets={item.get("asset_id"):item for item in manifest.get("assets",[])}
for anchor in anchors:
    rel=anchor.get("path",""); path=ROOT/rel
    if not path.is_file():
        errors.append(f"missing visual anchor: {rel}"); continue
    require(sha256(path)==anchor.get("sha256"),f"SHA-256 mismatch for {rel}")
    observed=png_size(path); expected=anchor.get("canvas_px",{})
    require(observed==(expected.get("width"),expected.get("height")),f"PNG canvas mismatch for {rel}: {observed}")
    asset_id=anchor.get("source_asset_id")
    if asset_id:
        asset=manifest_assets.get(asset_id)
        require(asset is not None,f"anchor {asset_id} absent from static manifest")
        if asset:
            require(asset.get("sha256")==anchor.get("sha256"),f"manifest SHA mismatch for {asset_id}")
            require(asset.get("actual_canvas_px")==expected,f"manifest canvas mismatch for {asset_id}")
    else:
        require(rel=="design/system/core_visual_v1/reference/normal_home_v1.png","only the original master may omit source_asset_id")

global_profiles={item.get("profile_id"):item for item in profiles.get("profiles",[])}
for profile in contract.get("preview_profiles",[]):
    pid=profile.get("profile_id"); ref=global_profiles.get(pid)
    require(ref is not None,f"unknown global viewport profile: {pid}")
    if ref:
        for key in ("width_dp","height_dp","native_matrix","preview_enabled"):
            require(profile.get(key)==ref.get(key),f"{pid}: {key} drift from global profile registry")
require(global_profiles.get("WIDTH-BOUNDARY-480DP",{}).get("height_dp") is None,"M05 must remain width-only")
require(global_profiles.get("WIDTH-BOUNDARY-480DP",{}).get("native_matrix") is False,"M05 must not enter native matrix")
require(global_profiles.get("WIDE-PREVIEW-600x960",{}).get("native_matrix") is False,"600x960 preview must remain outside current native matrix")

conflicts=contract.get("conflict_resolution",[])
require(len(conflicts)>=7,"authority conflict register must cover required domains")
require(all(item.get("status") in {"RESOLVED_BY_PROPOSED_CONTRACT","SCOPED_NOT_CONFLICTING"} for item in conflicts),"unresolved authority conflict remains")

gemini=authority.get("active_gemini_nh01_override",{})
require(gemini.get("authority")=="design/pages/home/normal_home/engineering/NH01_GEMINI_ADAPTIVE_V2_UNIFIED_AUTHORITY_V1.md","existing Gemini non-responsive authority must remain registered")
require(gemini.get("responsive_layout_authority")==str(CONTRACT_PATH.relative_to(ROOT)),"authority map responsive pointer mismatch")
require(gemini.get("runtime_verdict")=="NOT_RUN","authority map must not claim runtime PASS")
require(authority.get("engineering_binding",{}).get("layout")==str(CONTRACT_PATH.relative_to(ROOT)),"engineering binding layout must point to V2 contract")
menu=next((item for item in navigation.get("level_2",[]) if item.get("id")=="multi_screen_static_hifi"),None)
require(menu is not None,"NH01 static hi-fi/responsive menu entry missing")
if menu:
    require(menu.get("machine_authority")=="design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json","existing Design Manager renderer must retain the V1 static archive machine contract")
    require(menu.get("responsive_layout_authority")==str(CONTRACT_PATH.relative_to(ROOT)),"Design Manager menu must register the V2 responsive layout authority separately")
    require(menu.get("visual_manifest")==str(MANIFEST_PATH.relative_to(ROOT)),"Design Manager must retain frozen static manifest")
    require(menu.get("authority")=="design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md","existing Design Manager renderer must retain the V1 static archive reading guide")

fm=feature_matrix.get("features",{}).get("home_normal_v1",{})
required_refs=[str(CONTRACT_PATH.relative_to(ROOT)),str((ROOT/"design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.md").relative_to(ROOT)),str((ROOT/"design/pages/home/normal_home/responsive/NH01_RESPONSIVE_AUTHORITY_CROSSWALK_V1.md").relative_to(ROOT)),str(SCHEMA_PATH.relative_to(ROOT)),str(PROFILES_PATH.relative_to(ROOT)),str((ROOT/"design/system_v3/07_Responsive_System/Responsive_Layout_Standard_V1.md").relative_to(ROOT))]
for ref in required_refs: require(ref in (fm.get("design_archive_refs") or []),f"Feature Matrix missing ref: {ref}")

phase_tasks=phase_machine.get("tasks",{}); task=phase_tasks.get("P3-05",{})
require(task.get("gate_id")=="G-NH01-RESPONSIVE-CONTRACT","P3-05 must bind the responsive gate")
require("home_normal_v1" in (task.get("feature_refs") or []),"P3-05 must own home_normal_v1")
require("P3-05" in (phase_tasks.get("P3-02",{}).get("depends_on") or []),"NH01 workspace task must depend on responsive authority closure")
require("P3-06" in (phase_machine.get("feature_closure_rollup", {}).get("home_normal_v1", {}).get("depends_on") or []), "Normal Home closure rollup must depend on responsive preview/freeze validation")
require(phase_tasks.get("P3-06", {}).get("gate_id") == "G-NH01-RESPONSIVE-PREVIEW", "P3-06 must bind the responsive preview gate")
require("P3-05" in (phase_tasks.get("P3-06", {}).get("depends_on") or []), "P3-06 must depend on the candidate contract task")
require("P3-06" in (phase_tasks.get("P3-02", {}).get("depends_on") or []), "NH01 workspace implementation must wait for responsive contract freeze validation")

fc=registry.get("feature_contracts",{}).get("home_normal_v1",{}); ps=registry.get("page_contract_sets",{}).get("home_normal_v1",{})
require("responsive_layout" in (fc.get("page_contract_keys") or []),"Unified Contract Registry must declare responsive_layout key")
require(ps.get("refs",{}).get("responsive_layout")==str(CONTRACT_PATH.relative_to(ROOT)),"Unified Contract Registry page set must point to responsive contract")
require("RESPONSIVE-LAYOUT-STANDARD-V1" in (fc.get("additional_shared_contract_refs") or []),"Normal Home must inherit shared responsive standard")
require("P3-05" in registry.get("task_contract_bindings",{}),"Unified Contract Registry missing P3-05 binding")
require("G-NH01-RESPONSIVE-CONTRACT" in registry.get("gate_contracts",{}),"Unified Contract Registry missing responsive gate")
require("P3-06" in registry.get("task_contract_bindings", {}), "Unified Contract Registry missing P3-06 binding")
require("G-NH01-RESPONSIVE-PREVIEW" in registry.get("gate_contracts", {}), "Unified Contract Registry missing responsive preview gate")
require(registry.get("inventory",{}).get("task_count")==len(phase_tasks),"Unified Contract Registry task_count drift")
require(registry.get("inventory",{}).get("gate_count")==len(registry.get("gate_contracts",{})),"Unified Contract Registry gate_count drift")

if errors:
    print("NH01_RESPONSIVE_CONTRACT_VALIDATION=FAIL")
    for error in errors: print(f"- {error}")
    raise SystemExit(1)
print("NH01_RESPONSIVE_CONTRACT_VALIDATION=PASS")
print(f"visual_anchor_count={len(anchors)}")
print(f"preview_profile_count={len(contract.get('preview_profiles',[]))}")
print(f"native_matrix_cases={contract.get('native_acceptance',{}).get('required_case_count')}")
print("runtime_status=NOT_VERIFIED")
