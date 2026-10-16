#!/usr/bin/env python3
"""Fail-closed synchronization check against canonical origin/main."""
from __future__ import annotations
import argparse,subprocess,sys
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
REG=ROOT/"CONTROL_PLANE_REGISTRY.yaml"
def run(*args):
    return subprocess.check_output(["git",*args],cwd=ROOT,text=True,stderr=subprocess.DEVNULL).strip()
def files(ref,path):
    out=run("ls-tree","-r","--name-only",ref,"--",path)
    if not out: raise RuntimeError(f"canonical path missing or empty: {path}")
    return set(out.splitlines())
def blob(ref,path):
    return run("rev-parse",f"{ref}:{path}")
def local_blob(path):
    return run("hash-object",str(path))
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
    errors=[]; compared=0
    seen=set()
    for path in compare_paths:
        if path in seen: continue
        seen.add(path)
        try:
            canonical=files(args.base,path) if path.endswith("/") else {path}
        except Exception as exc:
            errors.append(str(exc)); continue
        for item in sorted(canonical):
            compared+=1
            local=ROOT/item
            if not local.exists(): errors.append(f"missing canonical file on Work branch: {item}"); continue
            try:
                if local_blob(local)!=blob(args.base,item): errors.append(f"stale/diverged canonical file: {item}")
            except Exception as exc: errors.append(f"unable to compare {item}: {exc}")
    print("CONTROL_PLANE_SYNC="+("PASS" if not errors else "FAIL"))
    print(f"canonical_registry={args.base}:CONTROL_PLANE_REGISTRY.yaml")
    print(f"compared_paths={compared}")
    print(f"errors={len(errors)}")
    for error in errors: print("- "+error,file=sys.stderr)
    return 1 if errors else 0
if __name__=="__main__": raise SystemExit(main())
