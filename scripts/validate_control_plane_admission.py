#!/usr/bin/env python3
"""Single admission entrypoint for all YuJian Work and Control Plane changes."""
from __future__ import annotations
import argparse,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def rev(*args):
    return subprocess.check_output(["git", "rev-parse", *args], cwd=ROOT, text=True).strip()

def run(label,args):
    print(f"\n=== {label} ===")
    return subprocess.run(args,cwd=ROOT).returncode==0
def branch_name():
    return os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME") or subprocess.check_output(["git","rev-parse","--abbrev-ref","HEAD"],cwd=ROOT,text=True).strip()
def validate_canonical_promotion_tree(root: Path = ROOT):
    """Canonical main must never ingest a product branch's live execution state.

    A pull request *to main* is a canonical promotion review, not a Work run
    on its source product branch. Static authorities remain validated strictly
    and branch-specific state must be absent from the promoted tree.
    """
    forbidden = (
        "PROJECT_MASTER_STATE.yaml",
        "WORK_EXECUTION_STATE.json",
        "harness/build-log.md",
        ".github/workflows/project-master-state.yml",
    )
    leaked = [path for path in forbidden if (root / path).exists()]
    if leaked:
        print("CANONICAL_PROMOTION_TREE=FAIL forbidden_branch_state=" + ",".join(leaked),
              file=sys.stderr)
        return False
    print("CANONICAL_PROMOTION_TREE=PASS branch_local_state_absent")
    return True


def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--no-fetch-main",action="store_true"); args=ap.parse_args()
    if not args.no_fetch_main and subprocess.run(["git","fetch","origin","main","--prune"],cwd=ROOT).returncode!=0:
        print("CONTROL_PLANE_ADMISSION=FAIL\n- unable to refresh origin/main",file=sys.stderr); return 1
    branch=branch_name(); event=os.environ.get("GITHUB_EVENT_NAME","")
    if event=="merge_group": branch="main"
    control_change=branch.startswith(("control/","design/")); ok=True
    target_main_pr = event == "pull_request" and os.environ.get("GITHUB_BASE_REF") == "main"
    if branch == "main" and event != "merge_group":
        local_main = rev("HEAD")
        canonical_main = rev("origin/main")
        print(f"LOCAL_MAIN_SHA={local_main}")
        print(f"ORIGIN_MAIN_SHA={canonical_main}")
        if local_main != canonical_main:
            print("CONTROL_PLANE_ADMISSION=FAIL\n- local main is stale or differs from refreshed origin/main", file=sys.stderr)
            return 1
    ok &= run("PYTHON_SYNTAX",["python","-m","compileall","-q","scripts"])
    ok &= run("CONTROL_PLANE_REGISTRY",["python","scripts/validate_control_plane_registry.py"])
    ok &= run("FEATURE_MATRIX",["python","scripts/validate_feature_matrix.py"])
    ok &= run("PHASE_TASK_STATE_MACHINE",["python","scripts/validate_phase_task_state_machine.py"])
    ok &= run("UNIFIED_CONTRACT",["python","scripts/validate_unified_contract.py"])
    ok &= run("WORK_LIFECYCLE_GOVERNANCE",["python","scripts/validate_work_lifecycle_governance.py"])
    if control_change:
        if target_main_pr:
            # A static control-plane proposal may carry dynamic state for its
            # own branch, but that state must not enter the canonical main tree.
            ok &= validate_canonical_promotion_tree()
        print(f"mode=CONTROL_PLANE_CHANGE branch={branch}")
        print("CONTROL_PLANE_ADMISSION="+("PASS" if ok else "FAIL")); return 0 if ok else 1
    if branch == "main" or target_main_pr:
        # Check the proposed destination tree; do not misclassify promotion
        # as a product Work session requiring branch-owned live state.
        # The same strict static registries/contracts are checked above.
        ok &= validate_canonical_promotion_tree()
        print(f"mode={'CANONICAL_MAIN_PROMOTION' if target_main_pr else 'CANONICAL_MAIN'} branch={branch}")
        print("CONTROL_PLANE_ADMISSION="+("PASS" if ok else "FAIL"))
        return 0 if ok else 1
    ok &= run("CONTROL_PLANE_SYNC",["python","scripts/validate_control_plane_sync.py","--base","origin/main"])
    ok &= run("PROJECT_MASTER_STATE",["python","scripts/validate_project_master_state.py"])
    ok &= run("WORK_EXECUTION_STATE",["python","scripts/validate_work_execution_state.py"])
    print(f"mode=PRODUCT_WORK branch={branch}")
    print("CONTROL_PLANE_ADMISSION="+("PASS" if ok else "FAIL")); return 0 if ok else 1
if __name__=="__main__": raise SystemExit(main())
