#!/usr/bin/env python3
"""Validate CONTROL_PLANE_REGISTRY.yaml as the single index-only entrypoint."""
from __future__ import annotations
import argparse,sys
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]; REG=ROOT/"CONTROL_PLANE_REGISTRY.yaml"
LAYERS=["01_project_master_state","02_feature_matrix","03_phase_task_state_machine","04_unified_contract","05_evidence_gate","06_automatic_recovery"]
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--registry",type=Path,default=REG); args=ap.parse_args()
    try:r=yaml.safe_load(args.registry.resolve().read_text(encoding="utf-8"))
    except Exception as exc: print(f"ERROR: {exc}",file=sys.stderr); return 2
    errors=[]
    if r.get("schema_version")!="1.1": errors.append("schema_version must be 1.1")
    if r.get("registry_id")!="YUJIAN-CONTROL-PLANE-V1": errors.append("registry_id mismatch")
    if r.get("role")!="INDEX_ONLY_SINGLE_ENTRYPOINT": errors.append("role must be INDEX_ONLY_SINGLE_ENTRYPOINT")
    if r.get("canonical_repository")!="pan277942135/Yujian_App": errors.append("canonical_repository mismatch")
    if r.get("canonical_branch")!="main": errors.append("canonical_branch must be main")
    if r.get("work_state_filename")!="WORK_EXECUTION_STATE.json": errors.append("work_state_filename must be generic")
    layers=r.get("layers",{})
    if list(layers)!=LAYERS: errors.append("layers must contain exactly six canonical layers in order")
    for layer in LAYERS:
        if not layers.get(layer,{}).get("authority"): errors.append(f"{layer}: authority missing")
    static=r.get("canonical_static_control_plane",{}).get("files",[])
    if len(static)!=len(set(static)): errors.append("duplicate canonical static paths")
    for path in static:
        if not (ROOT/path).exists(): errors.append(f"canonical static file missing: {path}")
    design=r.get("canonical_design_authorities",{}).get("paths",[])
    if len(design)!=len(set(design)): errors.append("duplicate design authority roots")
    for path in design:
        if not (ROOT/path).exists(): errors.append(f"canonical design authority root missing: {path}")
    admission=r.get("admission",{})
    if admission.get("script")!="scripts/validate_control_plane_admission.py": errors.append("admission script mismatch")
    if admission.get("fail_closed") is not True: errors.append("admission.fail_closed must be true")
    if r.get("integrity",{}).get("static_dynamic_boundary")!="ENFORCED": errors.append("static_dynamic_boundary must be ENFORCED")
    print("CONTROL_PLANE_REGISTRY_VALIDATION="+("FAIL" if errors else "PASS"))
    print("entrypoint=CONTROL_PLANE_REGISTRY.yaml"); print("layer_count=6"); print(f"static_file_count={len(static)}"); print(f"design_authority_root_count={len(design)}")
    for e in errors: print("- "+e,file=sys.stderr)
    return 1 if errors else 0
if __name__=="__main__": raise SystemExit(main())
