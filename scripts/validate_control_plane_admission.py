#!/usr/bin/env python3
"""Single admission entrypoint for all YuJian Work and Control Plane changes."""
from __future__ import annotations
import argparse,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def run(label,args):
    print(f"\n=== {label} ===")
    return subprocess.run(args,cwd=ROOT).returncode==0
def branch_name():
    return os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME") or subprocess.check_output(["git","rev-parse","--abbrev-ref","HEAD"],cwd=ROOT,text=True).strip()
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--no-fetch-main",action="store_true"); args=ap.parse_args()
    if not args.no_fetch_main and subprocess.run(["git","fetch","origin","main","--prune"],cwd=ROOT).returncode!=0:
        print("CONTROL_PLANE_ADMISSION=FAIL\n- unable to refresh origin/main",file=sys.stderr); return 1
    branch=branch_name(); event=os.environ.get("GITHUB_EVENT_NAME","")
    if event=="merge_group": branch="main"
    control_change=branch.startswith(("control/","design/")); ok=True
    ok &= run("PYTHON_SYNTAX",["python","-m","compileall","-q","scripts"])
    ok &= run("CONTROL_PLANE_REGISTRY",["python","scripts/validate_control_plane_registry.py"])
    ok &= run("FEATURE_MATRIX",["python","scripts/validate_feature_matrix.py"])
    ok &= run("PHASE_TASK_STATE_MACHINE",["python","scripts/validate_phase_task_state_machine.py"])
    ok &= run("UNIFIED_CONTRACT",["python","scripts/validate_unified_contract.py"])
    if control_change:
        print(f"mode=CONTROL_PLANE_CHANGE branch={branch}")
        print("CONTROL_PLANE_ADMISSION="+("PASS" if ok else "FAIL")); return 0 if ok else 1
    if branch=="main":
        print("mode=CANONICAL_MAIN")
        print("CONTROL_PLANE_ADMISSION="+("PASS" if ok else "FAIL")); return 0 if ok else 1
    ok &= run("CONTROL_PLANE_SYNC",["python","scripts/validate_control_plane_sync.py","--base","origin/main"])
    ok &= run("PROJECT_MASTER_STATE",["python","scripts/validate_project_master_state.py"])
    ok &= run("WORK_EXECUTION_STATE",["python","scripts/validate_work_execution_state.py"])
    print(f"mode=PRODUCT_WORK branch={branch}")
    print("CONTROL_PLANE_ADMISSION="+("PASS" if ok else "FAIL")); return 0 if ok else 1
if __name__=="__main__": raise SystemExit(main())
