#!/usr/bin/env python3
"""Static validator for the YuJian AI V2 six-contract freeze package."""
from __future__ import annotations
import csv, hashlib, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
EXPECTED_IDS = [
    "YUJIAN_RECOGNITION_RUNTIME_CONTRACT_V1",
    "YUJIAN_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1",
    "YUJIAN_REVEAL_TIMELINE_CONTRACT_V1",
    "YUJIAN_ORB_TO_GLYPH_RENDERING_CONTRACT_V1",
    "YUJIAN_RESULT_HANDOFF_CONTRACT_V1",
    "YUJIAN_RUNTIME_EVIDENCE_CONTRACT_V1",
]
EXPECTED_SUBSTAGES = [
    "13.1 NAME_STABLE", "13.2 PHOTO_SETTLE", "13.3 RESULT_SCENE_HANDOFF",
    "13.4 RESULT_INFO_ARRIVAL", "13.5 RESULT_SCENE_STABLE",
]
errors: list[str] = []
checks: list[str] = []
def ok(label: str) -> None: checks.append(label)
def require(condition: bool, message: str) -> None:
    if not condition: errors.append(message)
def sha(path: Path) -> str: return hashlib.sha256(path.read_bytes()).hexdigest()

# Markdown existence and local links.
md_files = sorted(ROOT.glob("*.md"))
require(bool(md_files), "No Markdown files found")
for path in md_files:
    content = path.read_text(encoding="utf-8")
    require(bool(content.strip()), f"Empty Markdown: {path.name}")
    for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", content):
        target = target.strip().split("#", 1)[0]
        if not target or re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target) or target.startswith("//"):
            continue
        require((ROOT / target).resolve().exists(), f"Broken local Markdown link in {path.name}: {target}")
ok(f"Markdown non-empty/local links ({len(md_files)} files)")

# JSON parse.
json_files = sorted(ROOT.glob("*.json"))
json_data = {}
for path in json_files:
    try: json_data[path.name] = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc: errors.append(f"Invalid JSON {path.name}: {exc}")
ok(f"JSON parse ({len(json_files)} files)")

registry = json_data.get("10_CONTRACT_REGISTRY.json", {})
contracts = registry.get("contracts", [])
ids = [c.get("id") for c in contracts]
require(ids == EXPECTED_IDS, f"Registry IDs/order mismatch: {ids}")
require(len(ids) == len(set(ids)) == 6, "Registry must contain six unique Contract IDs")
for c in contracts:
    require(c.get("version") == "1.0.0", f"Contract version mismatch: {c.get('id')}")
    require(c.get("status") == "FROZEN_SPECIFICATION", f"Contract status mismatch: {c.get('id')}")
    f = ROOT / str(c.get("file", ""))
    require(f.is_file(), f"Registry contract file missing: {f.name}")
    if f.is_file():
        require(c.get("id") in f.read_text(encoding="utf-8"), f"Contract ID absent from {f.name}")
        require(c.get("sha256") == sha(f), f"Registry contract SHA mismatch: {f.name}")
ok("Registry: six unique Contract IDs, V1.0.0 and files present")

# Timeline: exact 13 stage rows and sum.
timeline = (ROOT / "04_REVEAL_TIMELINE_CONTRACT_V1.md").read_text(encoding="utf-8")
stage_section = timeline.split("## 3. 13 Stage 与时长", 1)[1].split("## 4. 26KF 唯一语义", 1)[0]
stage_rows = re.findall(r"^\|\s*(\d{2})\s*\|\s*([^|]+?)\s*\|\s*(\d+)ms\s*\|", stage_section, re.M)
require(len(stage_rows) == 13, f"Expected 13 Stage rows, found {len(stage_rows)}")
require([int(r[0]) for r in stage_rows] == list(range(1,14)), "Stage IDs must be 01..13 in order")
require(sum(int(r[2]) for r in stage_rows) == 6000, f"Stage duration sum is {sum(int(r[2]) for r in stage_rows)}ms, expected 6000ms")
require("60,000ms" in timeline and "不计入 6000ms" in timeline, "Model 60s deadline and 6000ms presentation budget must remain separate")
ok("Timeline: 13 ordered Stages, 6000ms total, 60,000ms model deadline separate")

# KF00-KF25 exact matrix.
kf_section = timeline.split("## 4. 26KF 唯一语义", 1)[1].split("## 5. 三时钟统一合同", 1)[0]
kfs = re.findall(r"^\|\s*(KF\d{2})\s*\|", kf_section, re.M)
expected_kfs = [f"KF{i:02d}" for i in range(26)]
require(kfs == expected_kfs, f"KF matrix mismatch: {kfs}")
ok("KF matrix: exact KF00-KF25, 26 ordered rows")

# Stage 13 substage order.
sub_section = timeline.split("## 8. 13.1–13.5 与 Stage13 的关系", 1)[1].split("## 9.", 1)[0]
sub_names = re.findall(r"^\d+\.\s+(13\.\d\s+[A-Z_]+)", sub_section, re.M)
require(sub_names == EXPECTED_SUBSTAGES, f"Stage13 substage order mismatch: {sub_names}")
ok("Stage13 sub-stages: exact 13.1-13.5 order")

# Snapshot, rendering and evidence ownership.
snapshot = (ROOT / "03_RECOGNITION_RESULT_SNAPSHOT_CONTRACT_V1.md").read_text(encoding="utf-8")
orb = (ROOT / "05_ORB_TO_GLYPH_RENDERING_CONTRACT_V1.md").read_text(encoding="utf-8")
evidence = (ROOT / "07_RUNTIME_EVIDENCE_CONTRACT_V1.md").read_text(encoding="utf-8")
require(all(x in snapshot for x in ["FULL_REVEAL", "ORB_ONLY", "MASK_PENDING", "不可原地更新", "Result Geometry"]), "Snapshot branch/immutability/geometry rules missing")
require(all(x in orb for x in ["G01", "G02", "A01", "A02", "C01", "Task Energy 数量不冻结为五条"]), "Resident/Task Energy separation missing")
require(all(x in evidence for x in ["不得修改业务状态", "补造 Glyph Receipt", "生成 Handoff ACK", "FIRST_FAILURE", "PixelCopy", "Orb-to-Glyph Video", "Native Memory Profile"]), "Evidence read-only/diagnostic requirements missing")
review = (ROOT / "01_CROSS_CONTRACT_REVIEW.md").read_text(encoding="utf-8")
require("Evidence 只读订阅业务事件" in evidence and "Evidence 不得成为任何业务 Gate 的 Producer" in review, "Evidence read-only/non-producer ownership assertion missing")
ok("Snapshot immutability, FULL_REVEAL/ORB_ONLY, Resident/Task Energy and Evidence read-only rules")

# Acceptance matrix.
with (ROOT / "09_CROSS_CONTRACT_ACCEPTANCE_MATRIX.csv").open(newline="", encoding="utf-8-sig") as f:
    rows = list(csv.DictReader(f))
row_ids = [r.get("ID") for r in rows]
require(row_ids == [f"A{i:02d}" for i in range(1,20)], f"Acceptance matrix IDs mismatch: {row_ids}")
ok("Acceptance matrix: A01-A19 unique and ordered")

# Manifest and checksums.
manifest = json_data.get("CONTRACT_MANIFEST.json", {})
manifest_files = manifest.get("files", [])
manifest_paths = [x.get("path") for x in manifest_files]
require(len(manifest_paths) == len(set(manifest_paths)), "Manifest contains duplicate paths")
for item in manifest_files:
    path = ROOT / str(item.get("path", ""))
    require(path.is_file(), f"Manifest file missing: {item.get('path')}")
    if path.is_file():
        require(path.stat().st_size == item.get("sizeBytes"), f"Manifest size mismatch: {path.name}")
        require(sha(path) == item.get("sha256"), f"Manifest SHA mismatch: {path.name}")
require("CONTRACT_MANIFEST.json" not in manifest_paths and "SHA256SUMS" not in manifest_paths, "Manifest must not self-reference or include SHA256SUMS")
ok(f"Manifest: {len(manifest_files)} files, size and SHA256 match")

sum_file = ROOT / "SHA256SUMS"
entries = []
for line in sum_file.read_text(encoding="utf-8").splitlines():
    m = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
    require(m is not None, f"Malformed SHA256SUMS line: {line}")
    if m: entries.append((m.group(2), m.group(1)))
require(len(entries) == len({n for n,_ in entries}), "SHA256SUMS has duplicate file paths")
for name, digest in entries:
    path = ROOT / name
    require(path.is_file(), f"SHA256SUMS target missing: {name}")
    if path.is_file(): require(sha(path) == digest, f"SHA256SUMS hash mismatch: {name}")
require("SHA256SUMS" not in {n for n,_ in entries}, "SHA256SUMS cannot include itself")
ok(f"SHA256SUMS: {len(entries)} entries verified")

# Added-line whitespace equivalent check.
for path in sorted(ROOT.iterdir()):
    if path.is_file() and path.name != "SHA256SUMS":
        try: text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError: continue
        for no, line in enumerate(text.splitlines(), 1):
            if line.rstrip(" \t") != line:
                errors.append(f"Trailing whitespace: {path.name}:{no}")
ok("Whitespace scan")

for item in checks: print(f"PASS  {item}")
if errors:
    for err in errors: print(f"FAIL  {err}", file=sys.stderr)
    sys.exit(1)
print(f"CONTRACT_PACKAGE_STATIC_VALIDATION_PASS checks={len(checks)}")
