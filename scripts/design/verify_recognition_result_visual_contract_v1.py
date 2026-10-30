#!/usr/bin/env python3
"""Validate Recognition Result Visual Acceptance Contract V1 (design only)."""
from __future__ import annotations
import argparse, hashlib, json, re, sys
from pathlib import Path, PurePosixPath
from typing import Any

ROOT=Path(__file__).resolve().parents[2]
B="design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result"
F={k:f"{B}/{v}" for k,v in {
 "registry":"Recognition_Result_Visual_Contract_Registry_V1.json",
 "states":"Recognition_Result_State_Visual_Matrix_V1.json",
 "pixels":"Recognition_Result_Pixel_Targets_V1.json",
 "anchors":"Recognition_Result_Component_Anchor_Map_V1.json",
 "visual":"Recognition_Result_Visual_Contract_V1.md",
 "motion":"Recognition_Result_Motion_Contract_V2.md",
 "access":"Recognition_Result_Accessibility_Contract_V1.md",
 "runtime":"Recognition_Result_Runtime_Gate_V1.md"}.items()}
F["motion_spec"]="design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V2.md"
F["motion_json"]="design/pages/recognition/result/motion/recognition_result_motion_contract_v2.json"
PAGE_REG="design/system_v3/08_Page_Contracts/Page_Registry_V3_1.json"
PAGE_STATE="design/system_v3/08_Page_Contracts/recognition/result/State_Machine_V3_1.json"
LAYOUT="design/system_v3/08_Page_Contracts/recognition/result/Layout_Contract_V3_1.json"
ANDROID="design/system_v3/09_Android_Implementation_Contracts/Android_Implementation_Contract_Registry_V1.json"
COMPONENTS="design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json"
ASSETS="design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json"
BACKGROUND="design/system_v3/08_Background_System/Background_Registry_V3.json"
TOKENS=[
 "design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3_FINAL.json",
 "design/system_v3/04_Design_Tokens/Color/Color_Tokens_V3_FINAL.json",
 "design/system_v3/07_Responsive_System/Geometry_Tokens_V3_FINAL.json"]
STATES={"HIGH_CONFIDENCE","MEDIUM_CONFIDENCE","LOW_CONFIDENCE","NO_FISH","IMAGE_QUALITY_WARNING"}
ANCHORS={"header.back","header.title","result_hero.frame","result_hero.fish_safe_area","result_hero.fish_outline","result_card.species","result_card.confidence","result_card.metadata","candidate_card.image","candidate_card.text","candidate_card.selection","cta.visible_bounds","cta.touch_bounds","bottom_sheet.geometry","bottom_sheet.keyboard_safe_area"}
SHA=re.compile(r"^[0-9a-f]{64}$")

def safe(p:Any)->bool:
 if not isinstance(p,str) or not p:return False
 q=PurePosixPath(p)
 return not q.is_absolute() and all(x not in {"",".",".."} for x in q.parts)
def readj(p:str)->dict:
 if not safe(p):raise ValueError(f"unsafe path {p!r}")
 x=json.loads((ROOT/p).read_text(encoding="utf-8"))
 if not isinstance(x,dict):raise ValueError(f"{p}: JSON root is not an object")
 return x
def req(p:Any,issues:list[str],label:str)->None:
 if not safe(p):issues.append(f"{label}: unsafe/empty path {p!r}")
 elif not (ROOT/p).is_file():issues.append(f"{label}: missing file {p}")

def main()->int:
 ap=argparse.ArgumentParser(description=__doc__)
 ap.add_argument("--verify-bytes",action="store_true",help="Recompute referenced image SHA in a complete checkout.")
 args=ap.parse_args();issues=[]
 for p in [*F.values(),PAGE_REG,PAGE_STATE,LAYOUT,ANDROID,COMPONENTS,ASSETS,BACKGROUND,*TOKENS]:req(p,issues,"source")
 reg=readj(F["registry"]); sm=readj(F["states"]); px=readj(F["pixels"]); an=readj(F["anchors"]); motion_data=readj(F["motion_json"])
 pg=readj(PAGE_REG); pm=readj(PAGE_STATE); layout=readj(LAYOUT); android=readj(ANDROID)
 comps_doc=readj(COMPONENTS); assets_doc=readj(ASSETS); bg_doc=readj(BACKGROUND)
 token_docs=[readj(x) for x in TOKENS]
 if reg.get("registry_id")!="recognition_result_visual_contract_registry_v1" or reg.get("status")!="READY_FOR_VISUAL_IMPLEMENTATION" or reg.get("version")!="V1":issues.append("Visual Registry identity/status/version mismatch")
 if reg.get("contracts",{}).get("motion")!=F["motion"]:issues.append("Current Motion contract pointer must resolve to V2")
 if reg.get("authority_source",{}).get("result_motion")!=F["motion_spec"]:issues.append("Current Result motion spec pointer must resolve to V2")
 if motion_data.get("contract_id")!="recognition_result_motion_continuation_v2" or motion_data.get("status")!="FROZEN" or motion_data.get("total_duration_ms")!=850:issues.append("Motion V2 machine contract identity/status/timing mismatch")
 if motion_data.get("scope",{}).get("trigger_states")!=["RESULT_HIGH"] or len(motion_data.get("keyframes",[]))!=6:issues.append("Motion V2 trigger/keyframe contract incomplete")
 visual_sources=reg.get("frozen_visual_authority",[])
 if len(visual_sources)!=5:issues.append("Frozen visual authority must map exactly five Result states")
 source_by_path={x.get("path"):x for x in reg.get("authority_assets",[]) if isinstance(x,dict)}
 pixel_by_path={x.get("path"):x for x in px.get("source_assets",[]) if isinstance(x,dict)}
 seen_visual_states=set()
 for x in visual_sources:
  if not isinstance(x,dict):issues.append("frozen visual authority row must be an object");continue
  state=x.get("state");path=x.get("authority_asset");asset=source_by_path.get(path);pixel=pixel_by_path.get(path)
  if state not in STATES or state in seen_visual_states:issues.append(f"invalid/duplicate visual authority state: {state}")
  seen_visual_states.add(state)
  if not asset or asset.get("asset_id")!=x.get("asset_id") or asset.get("sha256")!=x.get("sha256"):issues.append(f"visual authority identity mismatch: {state}")
  if not pixel or pixel.get("sha256")!=x.get("sha256"):issues.append(f"pixel target source mismatch: {state}")
  if x.get("width")!=941 or x.get("height")!=1672 or x.get("aspect_ratio")!="941:1672 (0.562799)":issues.append(f"visual authority dimensions/aspect mismatch: {state}")
  if x.get("color_space")!="UNVERIFIED_NOT_RECORDED":issues.append(f"source color space must be recorded as unverified until measured: {state}")
  if not isinstance(x.get("safe_area"),str) or not x.get("safe_area"):issues.append(f"visual authority safe-area rule missing: {state}")
 if seen_visual_states!=STATES:issues.append("visual authority state coverage incomplete")
 gate=reg.get("readiness_gate",{})
 if gate.get("gate")!="RECOGNITION_RESULT_VISUAL_CONTRACT_READY" or gate.get("status")!="PASS":issues.append("Visual readiness gate must PASS")
 if reg.get("runtime_status")!="NOT_RUN":issues.append("Runtime status must remain NOT_RUN")
 readiness=reg.get("implementation_readiness",{})
 if readiness!={"design_contract":"PASS","android_implementation_contract":"PASS","visual_contract":"PASS","gate":"READY_FOR_ANDROID_IMPLEMENTATION","status":"PASS"}:issues.append("Aggregate implementation-readiness chain is incomplete or inconsistent")
 pg_gate=pg.get("freeze_gate",{})
 page=next((x for x in pg.get("pages",[]) if isinstance(x,dict) and x.get("page_id")=="recognition.result.v3_1"),None)
 if pg_gate.get("gate")!="PAGE_CONTRACT_READY" or pg_gate.get("status")!="PASS" or pg_gate.get("blocking_findings")!=0:issues.append("Upstream PAGE_CONTRACT_READY is not PASS")
 if not page or page.get("status")!="FROZEN":issues.append("Recognition Result Page Contract is not FROZEN")
 if not any(isinstance(x,dict) and x.get("page_id")=="recognition.result.v3_1" and x.get("status")=="READY_FOR_IMPLEMENTATION" for x in android.get("entries",[])):issues.append("Android Implementation Contract not READY_FOR_IMPLEMENTATION")
 canonical={x.get("id") for x in pm.get("states",[]) if isinstance(x,dict)}
 rows=sm.get("states",[]);ids=[x.get("state") for x in rows if isinstance(x,dict)]
 if set(reg.get("required_state_ids",[]))!=STATES or len(ids)!=5 or len(set(ids))!=5 or set(ids)!=STATES:issues.append("State Matrix must contain exactly the five canonical states")
 if not STATES.issubset(canonical):issues.append("Visual state IDs do not resolve through Page State Machine")
 comp={x.get("component_id"):x for x in comps_doc.get("entries",[]) if isinstance(x,dict)}
 assets={x.get("asset_id"):x for x in assets_doc.get("entries",[]) if isinstance(x,dict)}
 bgs={x.get("background_id"):x for x in bg_doc.get("backgrounds",[]) if isinstance(x,dict)}
 toks={}
 for doc in token_docs:
  for x in doc.get("tokens",[]):
   tid=x.get("id") if isinstance(x,dict) else None
   if isinstance(tid,str):
    if tid in toks:issues.append(f"duplicate token ID {tid}")
    toks[tid]=x
 for x in layout.get("component_refs",[]):
  cid=x.get("component") if isinstance(x,dict) else None
  if cid not in comp or comp[cid].get("status")!="FROZEN":issues.append(f"Layout component unresolved/not frozen: {cid}")
 for x in layout.get("token_refs",[]):
  tid=x.get("token_id") if isinstance(x,dict) else None
  if tid not in toks:issues.append(f"Layout token unresolved: {tid}")
 for x in layout.get("asset_refs",[]):
  a=assets.get(x.get("asset_id")) if isinstance(x,dict) else None
  if not a or a.get("path")!=x.get("path") or a.get("sha256")!=x.get("sha256"):issues.append(f"Layout asset unresolved: {x.get('asset_id') if isinstance(x,dict) else x}")
 for x in layout.get("background_refs",[]):
  b=bgs.get(x.get("background_id")) if isinstance(x,dict) else None
  if not b or b.get("master_path")!=x.get("master_path") or b.get("sha256")!=x.get("sha256"):issues.append(f"Background unresolved: {x.get('background_id') if isinstance(x,dict) else x}")
 authids=set()
 for x in reg.get("authority_assets",[]):
  if not isinstance(x,dict):issues.append("authority asset must be object");continue
  a=assets.get(x.get("asset_id"));digest=x.get("sha256")
  if not a or a.get("path")!=x.get("path") or a.get("sha256")!=digest:issues.append(f"Asset Registry identity mismatch: {x.get('asset_id')}");continue
  if not isinstance(digest,str) or not SHA.fullmatch(digest):issues.append(f"SHA-256 invalid/missing: {x.get('asset_id')}")
  if a.get("dimensions")!=x.get("dimensions"):issues.append(f"Asset dimensions mismatch: {x.get('asset_id')}")
  authids.add(x.get("asset_id"))
  if args.verify_bytes:
   p=ROOT/x["path"]
   if p.is_symlink() or not p.is_file():issues.append(f"Asset missing/symlink: {x['path']}")
   elif hashlib.sha256(p.read_bytes()).hexdigest()!=digest:issues.append(f"Asset SHA mismatch: {x['path']}")
 state_asset_ids=[]
 for x in rows:
  if not isinstance(x,dict):issues.append("state row is not an object");continue
  ar=x.get("authority_asset",{});aid=ar.get("asset_id");state_asset_ids.append(aid)
  if aid not in authids or ar.get("sha256")!=assets.get(aid,{}).get("sha256"):issues.append(f"State asset unresolved: {x.get('state')}")
  for tid in [*x.get("typography",[]),*x.get("color_tokens",[]),*x.get("geometry_tokens",[])]:
   if tid not in toks:issues.append(f"State token unresolved: {tid}")
  for cid in x.get("components",[]):
   if cid not in comp or comp[cid].get("status")!="FROZEN":issues.append(f"State component unresolved/not frozen: {cid}")
 if len(state_asset_ids)!=5 or len(set(state_asset_ids))!=5:issues.append("Five state PNGs must map one-to-one")
 ar=an.get("anchors",[]);aids=[x.get("id") for x in ar if isinstance(x,dict)]
 if len(aids)!=len(set(aids)) or set(aids)!=ANCHORS:issues.append("Anchor Map is incomplete or has duplicate IDs")
 for x in ar:
  if not isinstance(x,dict):issues.append("anchor row is not an object");continue
  cid=x.get("component")
  if cid not in comp or comp[cid].get("status")!="FROZEN":issues.append(f"Anchor component unresolved/not frozen: {cid}")
  if any(not x.get(k) for k in ("id","visual_bounds","touch_bounds","anchor","tolerance_px")):issues.append(f"Incomplete anchor row: {x.get('id')}")
 if an.get("coordinate_space",{}).get("reference_canvas_px")!={"width":1080,"height":1920}:issues.append("Anchor canvas must be 1080×1920")
 m=px.get("measurement",{})
 if m.get("geometry",{}).get("formula")!="max(2px, 0.002 × safeWidthPx)":issues.append("Geometry tolerance formula mismatch")
 if m.get("glyph",{}).get("formula")!="max(3px, 0.003 × safeWidthPx)":issues.append("Glyph tolerance formula mismatch")
 if px.get("reference_device",{}).get("width_px")!=1080 or px.get("reference_device",{}).get("height_px")!=1920:issues.append("Pixel baseline must be 1080×1920")
 if px.get("source_to_target",{}).get("crop")!="FORBIDDEN":issues.append("Cropping must be forbidden")
 if set(px.get("font_scales",[]))!={1.0,1.3,1.5,2.0}:issues.append("Font-scale matrix incomplete")
 for p in [*reg.get("contracts",{}).values(),*reg.get("authority_source",{}).values()]:req(p,issues,"Registry reference")
 nav="design/pages/recognition/result/navigation.json";req(nav,issues,"Design Manager navigation")
 if (ROOT/nav).is_file():
  j=readj(nav)
  if not any(x.get("id")=="RR08" and x.get("status")=="READY_FOR_VISUAL_IMPLEMENTATION" for x in j.get("level_2",[])):issues.append("Design Manager RR08 link missing")
  if not any(x.get("id")=="RR09" and x.get("status")=="FROZEN" and x.get("contract")==F["motion_spec"] and x.get("machine_contract")==F["motion_json"] for x in j.get("level_2",[])):issues.append("Design Manager RR09 Motion V2 link missing or inconsistent")
 if issues:
  print(f"FAIL: {len(issues)} Recognition Result Visual Contract issue(s)")
  for x in issues:print(" - "+x)
  return 1
 print(f"PASS: Recognition Result Visual Acceptance Contract V1 | states={len(ids)} anchors={len(aids)} assets={len(authids)}")
 print("Android project not inspected; runtime evidence NOT_RUN.")
 return 0
if __name__=="__main__":
 try:raise SystemExit(main())
 except (OSError,json.JSONDecodeError,ValueError) as exc:
  print(f"FAIL: {exc}",file=sys.stderr);raise SystemExit(1)
