#!/usr/bin/env python3
"""Fail-closed synchronization check against canonical origin/main."""
from __future__ import annotations
import argparse,json,re,subprocess,sys
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
REG=ROOT/"CONTROL_PLANE_REGISTRY.yaml"
ASSET_INTEGRITY=ROOT/"design/system_v3/06_Asset_System/Asset_Integrity_Register_V3_1.json"
SHA1=re.compile(r"^[0-9a-f]{40}$")
def run(*args):
    return subprocess.check_output(["git",*args],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip()
def files(ref,path):
    out=run("ls-tree","-r","--name-only",ref,"--",path)
    if not out: raise RuntimeError(f"canonical path missing or empty: {path}")
    return set(out.splitlines())
def blob(ref,path):
    return run("rev-parse",f"{ref}:{path}")

def local_files(path):
    out=run("ls-files","--",path)
    return set(out.splitlines()) if out else set()
def local_blob(path):
    return run("hash-object",str(path))

def load_worktree_exceptions():
    """Load the canonical, content-addressed non-frozen product exceptions."""
    try:
        document=json.loads(ASSET_INTEGRITY.read_text(encoding="utf-8"))
    except (OSError,json.JSONDecodeError) as exc:
        raise RuntimeError(f"worktree exception registry unavailable: {exc}") from exc
    rows=document.get("worktree_exceptions")
    if not isinstance(rows,list):
        raise RuntimeError("worktree exception registry must contain a list")
    exceptions={}
    for row in rows:
        if not isinstance(row,dict):
            raise RuntimeError("worktree exception row must be an object")
        path=row.get("path")
        expected=row.get("expected_current_git_blob_sha")
        if not isinstance(path,str) or not path.startswith("design/"):
            raise RuntimeError(f"invalid worktree exception path: {path!r}")
        if not isinstance(expected,str) or not SHA1.fullmatch(expected):
            raise RuntimeError(f"invalid expected worktree blob SHA for {path}")
        if row.get("is_frozen_asset") is True:
            raise RuntimeError(f"frozen asset cannot be a worktree exception: {path}")
        if path in exceptions:
            raise RuntimeError(f"duplicate worktree exception: {path}")
        exceptions[path]=expected
    return exceptions

def is_approved_worktree_exception(path,current_blob,exceptions):
    expected=exceptions.get(path)
    return expected is not None and current_blob==expected
def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--base",default="origin/main")
    ap.add_argument("--no-fetch",action="store_true")
    args=ap.parse_args()
    if not args.no_fetch:
        if subprocess.run(["git","fetch","origin","main","--prune"],cwd=ROOT).returncode!=0:
            print("CONTROL_PLANE_SYNC=FAIL"); print("- unable to refresh origin/main",file=sys.stderr); return 1
    try:
        registry=yaml.safe_load(REG.read_text(encoding="utf-8"))
        static=list(registry["canonical_static_control_plane"]["files"])
        design=list(registry["canonical_design_authorities"]["paths"])
    except Exception as exc:
        print(f"CONTROL_PLANE_SYNC=FAIL\n- registry parse failed: {exc}",file=sys.stderr); return 1
    compare_paths=["CONTROL_PLANE_REGISTRY.yaml"]+static+design
    try:
        worktree_exceptions=load_worktree_exceptions()
    except RuntimeError as exc:
        print(f"CONTROL_PLANE_SYNC=FAIL\n- {exc}",file=sys.stderr)
        return 1
    errors=[]; compared=0; allowed_exceptions=0
    seen=set()
    for path in compare_paths:
        if path in seen: continue
        seen.add(path)
        try:
            canonical=files(args.base,path) if path.endswith("/") else {path}
            local=local_files(path) if path.endswith("/") else ({path} if (ROOT/path).exists() else set())
        except Exception as exc:
            errors.append(str(exc)); continue
        missing=canonical-local
        extra=local-canonical
        for item in sorted(missing): errors.append(f"missing canonical file on Work branch: {item}")
        for item in sorted(extra): errors.append(f"extra/divergent canonical file on Work branch: {item}")
        for item in sorted(canonical & local):
            compared+=1
            local=ROOT/item
            if not local.exists(): continue
            try:
                current_blob=local_blob(local)
                canonical_blob=blob(args.base,item)
                if current_blob!=canonical_blob:
                    if is_approved_worktree_exception(item,current_blob,worktree_exceptions):
                        allowed_exceptions+=1
                    else:
                        errors.append(f"stale/diverged canonical file: {item}")
            except Exception as exc: errors.append(f"unable to compare {item}: {exc}")
    print("CONTROL_PLANE_SYNC="+("PASS" if not errors else "FAIL"))
    print(f"canonical_registry={args.base}:CONTROL_PLANE_REGISTRY.yaml")
    print(f"compared_paths={compared}")
    print(f"approved_worktree_exceptions={allowed_exceptions}")
    print(f"errors={len(errors)}")
    for error in errors: print("- "+error,file=sys.stderr)
    return 1 if errors else 0
if __name__=="__main__": raise SystemExit(main())
