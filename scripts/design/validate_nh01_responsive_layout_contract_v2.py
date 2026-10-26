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

schema=read_json(SCHEMA_PATH); contract=read_json(CONTRACT_PATH); profiles=read_json(PROFILES_PATH); preview_rules=read_json(STRUCTURED_PREVIEW_RULES_PATH)
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
require(preview_rules.get("rules_id")=="NH01_STRUCTURED_PREVIEW_LAYOUT_RULES_V2","structured preview metadata ID mismatch")
require(preview_rules.get("status")=="PREVIEW_ONLY","structured preview metadata must remain PREVIEW_ONLY")
require(preview_rules.get("responsive_authority")==CONTRACT_PATH.relative_to(ROOT).as_posix(),"structured preview must reference the V2 machine contract")
require(preview_rules.get("numeric_authority")=="NONE" and preview_rules.get("no_numeric_overrides") is True,"structured preview file must not contain duplicate responsive numeric rules")
require(not any(key in preview_rules for key in ("hero","pager","vertical_allocation","capture_action_group")),"structured preview file contains a duplicate numeric rule section")
require(contract.get("native_acceptance",{}).get("status")=="NOT_VERIFIED","Android runtime must remain NOT_VERIFIED")
require(contract.get("native_acceptance",{}).get("required_case_count")==24,"current native matrix must remain 24 cases")
require(contract.get("acceptance",{}).get("runtime_status_independent") is True,"design status must be independent of runtime acceptance")

logical_allocation = contract.get("logical_allocation", {})
require(logical_allocation.get("safe_height_formula") == "H_safe = viewport_height_dp - safe_insets_top_dp - safe_insets_bottom_dp; consume each safe inset exactly once before computing H_safe.", "logical allocation must consume safe insets exactly once")
bands = {item.get("id"): item for item in logical_allocation.get("bands", [])}
require(set(bands) == {"A", "B", "C"}, "logical allocation must declare exactly bands A, B and C")
require(bands.get("A", {}).get("logical_content") == ["brand", "statistics"], "band A must contain brand and statistics")
require(bands.get("B", {}).get("logical_content") == ["recent-catch-heading", "selected-hero"], "band B must contain Recent Catch heading and Hero")
require(bands.get("C", {}).get("logical_content") == ["footer"], "band C must contain footer")
require(bands.get("A", {}).get("physical_region") == "top", "band A must remain in physical Top")
require(bands.get("B", {}).get("physical_region") == "top+middle", "band B must preserve Top/Middle physical split")
require(bands.get("C", {}).get("physical_region") == "footer", "band C must remain in physical Footer")
require(logical_allocation.get("surplus_formula") == "S = max(0, H_safe - (hA + hB + hC + gAB_base + gBC_base))", "logical surplus formula mismatch")
require(logical_allocation.get("allocation_when_positive_surplus", {}).get("a_to_b") == "gAB_base + S/2", "positive surplus A-to-B allocation mismatch")
require(logical_allocation.get("allocation_when_positive_surplus", {}).get("b_to_c") == "gBC_base + S/2", "positive surplus B-to-C allocation mismatch")
require(logical_allocation.get("allocation_when_zero_surplus", {}).get("a_to_b") == "gAB_base", "zero-surplus A-to-B allocation mismatch")
require(logical_allocation.get("allocation_when_zero_surplus", {}).get("b_to_c") == "gBC_base", "zero-surplus B-to-C allocation mismatch")
require("title_to_card_runtime remains the internal Title-to-Hero gap and is not modified by surplus allocation." in logical_allocation.get("invariants", []), "internal title-to-Hero gap invariant missing")

# The interactive browser preview may execute only generic calculations whose numeric inputs
# come from this page-level machine contract. Keep the shared schema generic and reject
# viewport-keyed policy branches here.
preview_engine = contract.get("preview_engine", {})
preview_parameters = preview_engine.get("parameters", {})
coordinate_space = preview_engine.get("coordinate_space", {})
require(preview_engine.get("engine_id")=="NH01_ADAPTIVE_V2_CONTRACT_PREVIEW_V1","responsive preview engine id mismatch")
require(preview_engine.get("schema_version")=="1.0","responsive preview engine schema version mismatch")
require(coordinate_space.get("runtime_evidence") is False,"browser preview engine must not claim Android runtime evidence")
require(coordinate_space.get("default_insets_are_browser_fixture") is True,"default safe insets must be labelled as a browser fixture")
require(set(coordinate_space.get("default_safe_insets_dp",{}))=={"top","bottom","start","end"},"preview engine must declare four safe inset inputs")
require(all(isinstance(preview_parameters.get(section),dict) for section in ("content","viewport_scale","brand_title","avatar","hero","neighbor","camera","card_content","spacing")),"preview engine is missing a required numeric policy section")
require(preview_parameters.get("content",{}).get("max_width_dp")==560,"preview engine must retain the 560dp maximum content width")
require("systemFontScale" in preview_engine.get("inputs",[]),"preview engine must bind system font scale as an input")
require(set(preview_engine.get("role_bindings",{}))=={"stat_value","stat_label","section_title","section_action","cta","species","measurement","metadata"},"preview engine typography role bindings are incomplete")
card_content=preview_parameters.get("card_content",{})
require(card_content.get("species_wrap_width_threshold_dp")==240,"preview engine must use the contract species wrap width threshold")
require(card_content.get("large_font_scale_threshold")==1.3,"preview engine must use the contract large-font wrap threshold")
require(card_content.get("maximum_lines_species")==2 and card_content.get("maximum_lines_measurements")==2 and card_content.get("maximum_lines_metadata")==2,"preview engine text wrap line limits must match the contract")
hero_policy=preview_parameters.get("hero",{})
short_exception=hero_policy.get("short_screen_exception",{})
require(hero_policy.get("minimum_readable_height_dp")==148 and hero_policy.get("maximum_readable_height_dp")==220,"normal Hero readable-height range must remain 148-220dp")
require(short_exception.get("minimum_readable_floor_dp")==124 and short_exception.get("minimum_width_dp")==145,"M01 124dp exception floor and 145dp width minimum must remain explicit")
require(short_exception.get("exact_viewport_width_dp")==320 and short_exception.get("exact_viewport_height_dp")==480 and short_exception.get("exact_font_scale")==1,"M01 short-screen exception must match only 320x480 at fontScale 1.0")
require(short_exception.get("requires_no_middle_scroll") is True and short_exception.get("requires_all_copy_readable") is True,"M01 must preserve NO_SCROLL and readable required copy")
neighbor_policy=preview_parameters.get("neighbor",{})
peek_gap=neighbor_policy.get("peek_gap",{})
require(peek_gap.get("minimum_dp")==8 and peek_gap.get("track_width_fraction")==0.025 and peek_gap.get("maximum_dp")==12,"Peek gap must be the declared 8-12dp clamp of 2.5% track width")
require(neighbor_policy.get("maximum_geometry_overlap_dp")==1,"maximum geometry overlap must remain separate from peek gap")
require(bool(neighbor_policy.get("peek_visible_width_formula")),"neighbor visible-width formula is required")
require(neighbor_policy.get("indicator_height_dp",0)>0 and neighbor_policy.get("indicator_gap_after_hero_dp",0)>=0,"Pager indicator must be explicitly included in the pager budget")
allocation=preview_parameters.get("spacing",{}).get("surplus_allocation",{})
require(allocation.get("a_to_b_added")=="S/2" and allocation.get("b_to_c_added")=="S/2","preview engine must apply the equal 1:1 surplus split")
require(allocation.get("additional_gap_cap_dp") is None and allocation.get("outer_breathing_remainder_dp")==0,"preview may not cap added gaps or create outer-breathing remainder")
action=preview_parameters.get("capture_action_group",{})
require(action.get("cta_semantics")=="STATIC_TEXT_LABEL_NOT_AN_INDEPENDENT_ACTION" and action.get("cta_touch_target_dp") is None,"CTA label must not receive a duplicate touch target")
require(action.get("camera_artwork_inside_touch_target") is True,"camera artwork must be centered within its separate touch target")

def check_sorted_curve(points, x_key, label):
    require(isinstance(points,list) and len(points)>=2,f"{label} interpolation curve must have at least two points")
    if isinstance(points,list) and len(points)>=2:
        xs=[item.get(x_key) for item in points]
        require(all(isinstance(value,(int,float)) for value in xs),f"{label} interpolation curve has a missing/non-numeric coordinate")
        if all(isinstance(value,(int,float)) for value in xs):
            require(xs==sorted(xs) and len(xs)==len(set(xs)),f"{label} interpolation curve coordinates must be strictly increasing")

for key in ("stats_to_title","title_to_card"):
    check_sorted_curve(preview_parameters.get("spacing",{}).get(key,{}).get("base_curve",[]),"safe_height_dp",key)
check_sorted_curve(preview_parameters.get("spacing",{}).get("footer",{}).get("transition_curve",[]),"safe_height_dp","footer")

forbidden_policy_keys={"profile_id","viewport_id","viewport_key","viewport_override","viewport_overrides","fixed_layout","per_viewport_layout"}
def find_forbidden_policy_keys(value, path="preview_engine.parameters"):
    if isinstance(value,dict):
        for key,item in value.items():
            if key in forbidden_policy_keys: errors.append(f"viewport-keyed responsive rule is forbidden: {path}.{key}")
            find_forbidden_policy_keys(item,f"{path}.{key}")
    elif isinstance(value,list):
        for index,item in enumerate(value): find_forbidden_policy_keys(item,f"{path}[{index}]")
find_forbidden_policy_keys(preview_parameters)


workspace_source=STRUCTURED_WORKSPACE_PATH.read_text(encoding="utf-8") if STRUCTURED_WORKSPACE_PATH.is_file() else ""
manager_source=DESIGN_MANAGER_APP_PATH.read_text(encoding="utf-8") if DESIGN_MANAGER_APP_PATH.is_file() else ""
require("layoutContract?.preview_engine?.parameters" in workspace_source,"structured workspace must calculate from the V2 preview_engine parameters")
require("previewRules.vertical_allocation" not in workspace_source and "previewRules.pager?." not in workspace_source,"structured workspace must not read duplicate PREVIEW_ONLY numeric rules")
require('id:"responsive-contract"' in manager_source and 'baseResult.data["responsive-contract"]' in manager_source,"Design Manager loader must load and pass the V2 responsive contract")

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
require(contract.get("authority",{}).get("viewport_profiles")==PROFILES_PATH.relative_to(ROOT).as_posix(),"responsive contract must reference the page-independent viewport profile registry")
registered_probes={item.get("probe_id"):item for item in profiles.get("interpolation_probes",[])}
continuity_ids=contract.get("acceptance",{}).get("continuity_probe_ids",[])
require(len(continuity_ids)==len(set(continuity_ids)),"continuity probe IDs must be unique")
require(bool(continuity_ids) and all(probe_id in registered_probes for probe_id in continuity_ids),"contract continuity_probe_ids must all resolve to the page-independent viewport registry")
for profile in contract.get("preview_profiles",[]):
    pid=profile.get("profile_id"); ref=global_profiles.get(pid)
    require(ref is not None,f"unknown global viewport profile: {pid}")
    if ref:
        for key in ("width_dp","height_dp","native_matrix","preview_enabled"):
            require(profile.get(key)==ref.get(key),f"{pid}: {key} drift from global profile registry")
require(global_profiles.get("WIDTH-BOUNDARY-480DP",{}).get("height_dp") is None,"M05 must remain width-only")
require(global_profiles.get("WIDTH-BOUNDARY-480DP",{}).get("native_matrix") is False,"M05 must not enter native matrix")
require(global_profiles.get("WIDE-PREVIEW-600x960",{}).get("native_matrix") is False,"600x960 preview must remain outside current native matrix")
default_insets=coordinate_space.get("default_safe_insets_dp",{})
inset_total=default_insets.get("top",0)+default_insets.get("bottom",0)
title_curve={item.get("safe_height_dp"):item.get("value_dp") for item in preview_parameters.get("spacing",{}).get("title_to_card",{}).get("base_curve",[])}
for target in contract.get("spacing",{}).get("title_to_card_anchor_targets",[]):
    profile=global_profiles.get(target.get("profile_id"),{})
    height=profile.get("height_dp")
    if isinstance(height,(int,float)):
        safe_height=height-inset_total
        require(title_curve.get(safe_height)==target.get("target_dp"),f"title-to-card curve does not pass through {target.get('profile_id')} anchor target")
require(global_profiles.get("WIDTH-BOUNDARY-480DP",{}).get("height_dp") is None,"M05 must not gain a rendered viewport height")

conflicts=contract.get("conflict_resolution",[])
require(len(conflicts)>=7,"authority conflict register must cover required domains")
require(all(item.get("status") in {"RESOLVED_BY_PROPOSED_CONTRACT","SCOPED_NOT_CONFLICTING"} for item in conflicts),"unresolved authority conflict remains")

gemini=authority.get("active_gemini_nh01_override",{})
require(gemini.get("authority")=="design/pages/home/normal_home/engineering/NH01_GEMINI_ADAPTIVE_V2_UNIFIED_AUTHORITY_V1.md","existing Gemini non-responsive authority must remain registered")
require(gemini.get("responsive_layout_authority")==CONTRACT_PATH.relative_to(ROOT).as_posix(),"authority map responsive pointer mismatch")
require(gemini.get("runtime_verdict")=="NOT_RUN","authority map must not claim runtime PASS")
require(authority.get("engineering_binding",{}).get("layout")==CONTRACT_PATH.relative_to(ROOT).as_posix(),"engineering binding layout must point to V2 contract")
menu=next((item for item in navigation.get("level_2",[]) if item.get("id")=="multi_screen_static_hifi"),None)
require(menu is not None,"NH01 static hi-fi/responsive menu entry missing")
if menu:
    require(menu.get("machine_authority")=="design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json","existing Design Manager renderer must retain the V1 static archive machine contract")
    require(menu.get("responsive_layout_authority")==CONTRACT_PATH.relative_to(ROOT).as_posix(),"Design Manager menu must register the V2 responsive layout authority separately")
    require(menu.get("visual_manifest")==MANIFEST_PATH.relative_to(ROOT).as_posix(),"Design Manager must retain frozen static manifest")
    require(menu.get("authority")=="design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md","existing Design Manager renderer must retain the V1 static archive reading guide")

fm=feature_matrix.get("features",{}).get("home_normal_v1",{})
required_refs=[CONTRACT_PATH.relative_to(ROOT).as_posix(),(ROOT/"design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.md").relative_to(ROOT).as_posix(),(ROOT/"design/pages/home/normal_home/responsive/NH01_RESPONSIVE_AUTHORITY_CROSSWALK_V1.md").relative_to(ROOT).as_posix(),SCHEMA_PATH.relative_to(ROOT).as_posix(),PROFILES_PATH.relative_to(ROOT).as_posix(),(ROOT/"design/system_v3/07_Responsive_System/Responsive_Layout_Standard_V1.md").relative_to(ROOT).as_posix()]
for ref in required_refs: require(ref in (fm.get("design_archive_refs") or []),f"Feature Matrix missing ref: {ref}")

phase_tasks=phase_machine.get("tasks",{})
contract_task_id="NH01-RESPONSIVE-CONTRACT-V2"
preview_task_id="NH01-RESPONSIVE-PREVIEW-V2"
contract_gate_id="G-NH01-RESPONSIVE-CONTRACT-V2"
preview_gate_id="G-NH01-RESPONSIVE-PREVIEW-V2"
task=phase_tasks.get(contract_task_id,{})
require(task.get("gate_id")==contract_gate_id,f"{contract_task_id} must bind the responsive contract gate")
require("home_normal_v1" in (task.get("feature_refs") or []),f"{contract_task_id} must own home_normal_v1")
require(contract_task_id in (phase_tasks.get("P3-02",{}).get("depends_on") or []),"NH01 workspace task must depend on responsive authority closure")
require(preview_task_id in (phase_machine.get("feature_closure_rollup", {}).get("home_normal_v1", {}).get("depends_on") or []), "Normal Home closure rollup must depend on responsive preview/freeze validation")
require(phase_tasks.get(preview_task_id, {}).get("gate_id") == preview_gate_id, f"{preview_task_id} must bind the responsive preview gate")
require(contract_task_id in (phase_tasks.get(preview_task_id, {}).get("depends_on") or []), f"{preview_task_id} must depend on the candidate contract task")
require(preview_task_id in (phase_tasks.get("P3-02",{}).get("depends_on") or []), "NH01 workspace implementation must wait for responsive contract freeze validation")
require(phase_tasks.get("P3-05",{}).get("gate_id") == "G-SWOS-PHASE0-CONTRACT", "P3-05 SWOS gate binding must remain canonical")
require(phase_tasks.get("P3-06",{}).get("gate_id") == "G-WEB-HIFI-CONTRACT", "P3-06 Web hi-fi gate binding must remain canonical")
require(phase_tasks.get("P3-07",{}).get("gate_id") == "G-SWOS-WEB-HIFI-MOTION-V1", "P3-07 SWOS Web gate binding must remain canonical")

fc=registry.get("feature_contracts",{}).get("home_normal_v1",{}); ps=registry.get("page_contract_sets",{}).get("home_normal_v1",{})
require("responsive_layout" in (fc.get("page_contract_keys") or []),"Unified Contract Registry must declare responsive_layout key")
require(ps.get("refs",{}).get("responsive_layout")==CONTRACT_PATH.relative_to(ROOT).as_posix(),"Unified Contract Registry page set must point to responsive contract")
require("RESPONSIVE-LAYOUT-STANDARD-V1" in (fc.get("additional_shared_contract_refs") or []),"Normal Home must inherit shared responsive standard")
require(contract_task_id in registry.get("task_contract_bindings",{}),"Unified Contract Registry missing NH01 responsive contract binding")
require(contract_gate_id in registry.get("gate_contracts",{}),"Unified Contract Registry missing responsive contract gate")
require(preview_task_id in registry.get("task_contract_bindings", {}), "Unified Contract Registry missing NH01 responsive preview binding")
require(preview_gate_id in registry.get("gate_contracts", {}), "Unified Contract Registry missing responsive preview gate")
require("P3-05" in registry.get("task_contract_bindings",{}) and registry.get("task_contract_bindings",{}).get("P3-05",{}).get("gate_id")=="G-SWOS-PHASE0-CONTRACT", "Unified Contract Registry SWOS P3-05 binding must remain canonical")
require("P3-06" in registry.get("task_contract_bindings",{}) and registry.get("task_contract_bindings",{}).get("P3-06",{}).get("gate_id")=="G-WEB-HIFI-CONTRACT", "Unified Contract Registry Web P3-06 binding must remain canonical")
require("P3-07" in registry.get("task_contract_bindings",{}) and registry.get("task_contract_bindings",{}).get("P3-07",{}).get("gate_id")=="G-SWOS-WEB-HIFI-MOTION-V1", "Unified Contract Registry SWOS Web P3-07 binding must remain canonical")
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
