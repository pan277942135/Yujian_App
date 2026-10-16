#!/usr/bin/env python3
"""Validate CONTROL_PLANE_REGISTRY.yaml as the single index-only entrypoint."""
from __future__ import annotations
import argparse,sys
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]; REG=ROOT/"CONTROL_PLANE_REGISTRY.yaml"
LAYERS=["PROJECT_MASTER_STATE","FEATURE_MATRIX","PHASE_TASK_STATE_MACHINE","UNIFIED_CONTRACT","EVIDENCE_GATE","RECOVERY"]
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--registry",type=Path,default=REG); args=ap.parse_args()
    try:r=yaml.safe_load(args.registry.resolve().read_text(encoding="utf-8"))
    except Exception as exc: print(f"ERROR: {exc}",file=sys.stderr); return 2
    errors=[]
    if r.get("schema_version")!="1.0": errors.append("schema_version must be 1.0")
    if r.get("registry_id")!="yujian-control-plane-registry": errors.append("registry_id mismatch")
    if r.get("role")!="INDEX_ONLY_SINGLE_ENTRYPOINT": errors.append("role must be INDEX_ONLY_SINGLE_ENTRYPOINT")
    p=r.get("project",{})
    if p.get("repository")!="pan277942135/Yujian_App": errors.append("project.repository mismatch")
    if p.get("canonical_branch")!="main": errors.append("project.canonical_branch must be main")
    if list(r.get("layers",{}))!=LAYERS: errors.append("layers must contain exactly six canonical layers in order")
    for layer in LAYERS:
        if not r.get("layers",{}).get(layer,{}).get("path"): errors.append(f"{layer}: path missing")
    static=r.get("canonical_static_control_plane",{}).get("files",[])
    if len(static)!=len(set(static)): errors.append("duplicate canonical static paths")
    for path in static:
        if not (ROOT/path).exists(): errors.append(f"canonical static file missing: {path}")
    for section in ("canonical_design_authorities","state_resolution","admission","integrity"):
        if section not in r: errors.append(f"missing registry section: {section}")
    if r.get("state_resolution",{}).get("work_execution_state")!="current_work_branch:WORK_EXECUTION_STATE.json":
        errors.append("work execution state resolution must be generic")
    if r.get("admission",{}).get("script")!="scripts/validate_control_plane_admission.py":
        errors.append("admission script mismatch")
    print("CONTROL_PLANE_REGISTRY_VALIDATION="+("FAIL" if errors else "PASS"))
    print("entrypoint=CONTROL_PLANE_REGISTRY.yaml"); print("layer_count=6"); print(f"static_file_count={len(static)}")
    for e in errors: print("- "+e,file=sys.stderr)
    return 1 if errors else 0
if __name__=="__main__": raise SystemExit(main())
