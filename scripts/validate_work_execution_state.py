#!/usr/bin/env python3
"""Validate generic branch-local WORK_EXECUTION_STATE.json."""
from __future__ import annotations
import argparse,json,re,sys
from pathlib import Path
try: import yaml
except ImportError as exc:
    print("ERROR: PyYAML is required",file=sys.stderr); raise SystemExit(2) from exc
ROOT=Path(__file__).resolve().parents[1]
DEFAULT=ROOT/"WORK_EXECUTION_STATE.json"
MACHINE=ROOT/"harness/PHASE_TASK_STATE_MACHINE.yaml"
SHA=re.compile(r"^[0-9a-f]{40}$")
STATUS={"READY","RUNNING","VALIDATING","FAIL","RECOVERING","BLOCKED","PASS","CLOSED","FINAL_GATE","CANCELLED"}
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--state",type=Path,default=DEFAULT); ap.add_argument("--machine",type=Path,default=MACHINE); args=ap.parse_args()
    p=args.state.resolve()
    if not p.exists():
        print("WORK_EXECUTION_STATE_VALIDATION=FAIL"); print(f"- missing: {p}",file=sys.stderr); return 1
    try:
        state=json.loads(p.read_text(encoding="utf-8")); machine=yaml.safe_load(args.machine.resolve().read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"ERROR: {exc}",file=sys.stderr); return 2
    errors=[]; required=["schema_version","work_id","repository","branch","control_plane_revision","base_branch","base_sha","phase","task_id","stage","status","source_sha","checkpoint_updated_at","next_action","dependencies","blockers","evidence"]
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
    if task_id and not task: errors.append(f"task_id not found in canonical machine: {task_id}")
    if task and task.get("phase")!=phase: errors.append(f"phase/task mismatch: {phase} != {task.get('phase')}")
    if not isinstance(state.get("dependencies"),list): errors.append("dependencies must be array")
    if not isinstance(state.get("blockers"),list): errors.append("blockers must be array")
    if not isinstance(state.get("evidence"),dict): errors.append("evidence must be object")
    if errors:
        print("WORK_EXECUTION_STATE_VALIDATION=FAIL")
        for error in errors: print("- "+error,file=sys.stderr)
        return 1
    print("WORK_EXECUTION_STATE_VALIDATION=PASS")
    print(f"work_id={state['work_id']}"); print(f"task_id={state['task_id']}"); print(f"phase={state['phase']}"); print(f"status={state['status']}"); print(f"control_plane_revision={state['control_plane_revision']}")
    return 0
if __name__=="__main__": raise SystemExit(main())
