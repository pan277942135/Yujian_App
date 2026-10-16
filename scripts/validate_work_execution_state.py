#!/usr/bin/env python3
"""Validate generic branch-local WORK_EXECUTION_STATE.json and current Work scope."""
from __future__ import annotations
import argparse,json,os,re,subprocess,sys
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
DEFAULT=ROOT/"WORK_EXECUTION_STATE.json"
MACHINE=ROOT/"harness/PHASE_TASK_STATE_MACHINE.yaml"
CONTRACT=ROOT/"harness/UNIFIED_CONTRACT_REGISTRY.yaml"
SHA=re.compile(r"^[0-9a-f]{40}$")
STATUS={"READY","RUNNING","VALIDATING","FAIL","RECOVERING","BLOCKED","PASS","CLOSED","FINAL_GATE","CANCELLED"}
def git(*args):
    return subprocess.check_output(["git",*args],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip()
def local_refs(value):
    refs=set()
    if isinstance(value,dict):
        for v in value.values(): refs |= local_refs(v)
    elif isinstance(value,list):
        for v in value: refs |= local_refs(v)
    elif isinstance(value,str):
        if value.startswith(("design/","scripts/","app/","harness/","AGENTS.md","CONTROL_PLANE_","PROJECT_MASTER_STATE.yaml","WORK_EXECUTION_STATE.json")):
            refs.add(value)
    return refs
def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--state",type=Path,default=DEFAULT)
    ap.add_argument("--machine",type=Path,default=MACHINE)
    ap.add_argument("--contract",type=Path,default=CONTRACT)
    args=ap.parse_args()
    p=args.state.resolve()
    if not p.exists():
        print("WORK_EXECUTION_STATE_VALIDATION=FAIL"); print(f"- missing: {p}",file=sys.stderr); return 1
    try:
        state=json.loads(p.read_text(encoding="utf-8"))
        machine=yaml.safe_load(args.machine.resolve().read_text(encoding="utf-8"))
        contracts=yaml.safe_load(args.contract.resolve().read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"ERROR: {exc}",file=sys.stderr); return 2
    errors=[]
    required=["schema_version","work_id","repository","branch","control_plane_revision","base_branch","base_sha","phase","task_id","stage","status","source_sha","checkpoint_updated_at","next_action","dependencies","blockers","evidence"]
    for key in required:
        if key not in state: errors.append(f"missing top-level key: {key}")
    if state.get("schema_version")!="1.0": errors.append("schema_version must be 1.0")
    if state.get("repository")!="pan277942135/Yujian_App": errors.append("repository mismatch")
    if state.get("base_branch")!="main": errors.append("base_branch must be main")
    for key in ("control_plane_revision","base_sha","source_sha"):
        value=state.get(key)
        if not isinstance(value,str) or not SHA.fullmatch(value): errors.append(f"{key} must be 40-char SHA")
    if state.get("status") not in STATUS: errors.append(f"unsupported status: {state.get('status')!r}")
    task_id=state.get("task_id"); phase=state.get("phase"); task=machine.get("tasks",{}).get(task_id,{})
    if not task_id or not task: errors.append(f"task_id not found in canonical machine: {task_id!r}")
    if task and task.get("phase")!=phase: errors.append(f"phase/task mismatch: {phase} != {task.get('phase')}")
    if task and set(state.get("dependencies",[])) != set(task.get("depends_on",[]) or []): errors.append("dependencies do not match canonical task definition")
    env_branch=os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME")
    if env_branch and env_branch not in {state.get("branch"),"HEAD"}: errors.append(f"workflow branch {env_branch!r} != state branch {state.get('branch')!r}")
    try:
        if git("rev-parse","HEAD") != state.get("source_sha"): errors.append("source_sha must equal current HEAD")
        if git("rev-parse","origin/main") != state.get("control_plane_revision"): errors.append("control_plane_revision must equal refreshed origin/main")
    except Exception as exc: errors.append(f"git provenance check failed: {exc}")
    binding=contracts.get("task_contract_bindings",{}).get(task_id)
    if not isinstance(binding,dict): errors.append(f"missing Unified Contract binding for {task_id}")
    else:
        required_refs=set(binding.get("contract_refs",[]) or [])
        feature_contracts=contracts.get("feature_contracts",{})
        shared=contracts.get("shared_contracts",{})
        gate_contracts=contracts.get("gate_contracts",{})
        resolved=[]
        for ref in required_refs:
            if ref in feature_contracts: resolved.append(feature_contracts[ref])
            elif ref in shared: resolved.append(shared[ref])
            elif ref in gate_contracts: resolved.append(gate_contracts[ref])
            else: errors.append(f"{task_id}: contract reference not found: {ref}")
        for item in resolved:
            for ref in local_refs(item):
                path=ROOT/ref
                if not path.exists(): errors.append(f"{task_id}: current-scope contract file missing: {ref}")
    if not isinstance(state.get("dependencies"),list): errors.append("dependencies must be array")
    if not isinstance(state.get("blockers"),list): errors.append("blockers must be array")
    if not isinstance(state.get("evidence"),dict): errors.append("evidence must be object")
    if not state.get("next_action"): errors.append("next_action is required")
    if errors:
        print("WORK_EXECUTION_STATE_VALIDATION=FAIL")
        for error in errors: print("- "+error,file=sys.stderr)
        return 1
    print("WORK_EXECUTION_STATE_VALIDATION=PASS")
    print(f"work_id={state['work_id']}"); print(f"task_id={state['task_id']}"); print(f"phase={state['phase']}"); print(f"status={state['status']}"); print(f"control_plane_revision={state['control_plane_revision']}")
    return 0
if __name__=="__main__": raise SystemExit(main())
