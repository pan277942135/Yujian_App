#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.FishGuideRuntimeTest'
}

gate_collect_evidence() {
  local out="$YUJIAN_EVIDENCE_DIR/fish-guide-v1"
  mkdir -p "$out"
  local remote="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/fish_guide_v1"

  "$YUJIAN_ADB_BIN" shell ls -la "$remote" > "$out/device_files.txt" 2>&1 || {
    runtime_set_failure "EVIDENCE" "FISH_GUIDE_DEVICE_EVIDENCE_DIRECTORY_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  }
  "$YUJIAN_ADB_BIN" pull "$remote/." "$out/" > "$out/adb_pull.log" 2>&1 || {
    runtime_set_failure "EVIDENCE" "FISH_GUIDE_SCREENSHOT_PULL_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  }

  local missing=0 file
  for file in     fish_guide_lit.png     fish_guide_unlit.png     fish_guide_error.png     fish_species_detail.png     fish_species_detail_catch.png
  do
    if [[ ! -s "$out/$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$out/$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "FISH_GUIDE_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  python3 -     "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference/fish_guide_v2.png"     "$out/fish_guide_lit.png"     "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png"     "$out/fish_guide_unlit.png"     "$out" <<'PY'
from __future__ import annotations
import json, sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageStat

lit_ref, lit_run, unlit_ref, unlit_run, out = map(Path, sys.argv[1:6])
out.mkdir(parents=True, exist_ok=True)

regions = {
    "header": (0.02, 0.02, 0.96, 0.18),
    "progress": (0.42, 0.14, 0.54, 0.16),
    "card": (0.10, 0.24, 0.80, 0.56),
}
thresholds = {"header": 105.0, "progress": 105.0, "card": 115.0}

def mae(a,b):
    return sum(ImageStat.Stat(ImageChops.difference(a,b)).mean)/3.0

def compare(label, ref_path, run_path):
    ref=Image.open(ref_path).convert("RGB")
    run=Image.open(run_path).convert("RGB").resize(ref.size, Image.Resampling.LANCZOS)
    metrics={}
    for name,(x,y,w,h) in regions.items():
        box=(int(ref.width*x),int(ref.height*y),int(ref.width*(x+w)),int(ref.height*(y+h)))
        metrics[name]=round(mae(ref.crop(box),run.crop(box)),4)
    status="PASS" if all(metrics[k] <= thresholds[k] for k in thresholds) else "FAIL"
    report={"status":status,"reference":str(ref_path),"runtime":str(run_path),"metrics_mae":metrics,"thresholds":thresholds}
    (out/f"{label}_visual_parity_report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    canvas=Image.new("RGB",(ref.width*2,ref.height),"white")
    canvas.paste(ref,(0,0)); canvas.paste(run,(ref.width,0))
    draw=ImageDraw.Draw(canvas); draw.text((8,8),"Frozen",fill="black"); draw.text((ref.width+8,8),"Runtime",fill="black")
    canvas.save(out/f"{label}_frozen_side_by_side.png")
    print("FISH_GUIDE_VISUAL_PARITY",label,json.dumps(report,ensure_ascii=False))
    return status

statuses=[
    compare("fish_guide_lit",lit_ref,lit_run),
    compare("fish_guide_unlit",unlit_ref,unlit_run),
]
if any(x!="PASS" for x in statuses):
    raise SystemExit("FISH_GUIDE_VISUAL_PARITY_FAILED")
PY
  local rc=$?
  if (( rc != 0 )); then
    runtime_set_failure "EVIDENCE" "FISH_GUIDE_VISUAL_PARITY_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
