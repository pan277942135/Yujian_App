#!/usr/bin/env python3
"""Frozen-vs-runtime Android acceptance report. Missing proof is never a pass."""
from __future__ import annotations

import argparse
import hashlib
import html
import json
from pathlib import Path
import sys

from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "design/qa/android_hifi_manifest_v1.json"
BAD = {"FAIL_TEST", "FAIL_EVIDENCE", "FAIL_VISUAL", "FAIL_AUTHORITY", "BLOCKED"}
RANK = {"FAIL_TEST": 0, "FAIL_EVIDENCE": 1, "FAIL_VISUAL": 2,
        "FAIL_AUTHORITY": 3, "BLOCKED": 4, "REVIEW_REQUIRED": 5,
        "AUTO_CHECK_PASS": 6}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def safe_path(root: Path, relative: str) -> Path:
    p = (root / relative).resolve()
    if not p.is_relative_to(root.resolve()):
        raise ValueError("path escapes declared root: " + relative)
    return p


def load_manifest(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1 or not data.get("package"):
        raise ValueError("invalid manifest schema / package")
    seen = set()
    for item in data["surfaces"]:
        if item["id"] in seen:
            raise ValueError("duplicate surface: " + item["id"])
        seen.add(item["id"])
        if item["module"] not in data["required_modules"]:
            raise ValueError("unregistered module: " + item["module"])
        if item["mode"] not in ("auto", "review"):
            raise ValueError("unknown comparison mode: " + item["id"])
        for roi in item["rois"]:
            x, y, w, h = roi["rect"]
            if min(x, y, w, h) < 0 or x + w > 1.00001 or y + h > 1.00001 or w == 0 or h == 0:
                raise ValueError("invalid ROI: " + item["id"])
        if item["reference"] and not safe_path(ROOT, item["reference"]).is_file():
            raise ValueError("missing Frozen authority: " + item["reference"])
    if {x["module"] for x in data["surfaces"]} != set(data["required_modules"]):
        raise ValueError("required module without a snapshot")
    return data


def find_gates(artifact_root: Path, expected_sha: str) -> dict:
    gates = {}
    for result in artifact_root.rglob("runtime_gate_result.json"):
        try:
            doc = json.loads(result.read_text(encoding="utf-8"))
            name = doc["gate"]
            if doc.get("build_sha") != expected_sha:
                continue
            if name in gates:
                gates[name] = {"error": "DUPLICATE_GATE", "folder": None}
            else:
                gates[name] = {"doc": doc, "folder": result.parent}
        except (ValueError, KeyError, OSError):
            continue
    return gates


def provenance_valid(screen: Path, expected_sha: str, package: str) -> tuple[bool, str]:
    proof = screen.with_name(screen.name + ".provenance.json")
    if not proof.is_file():
        return False, "MISSING_CAPTURE_PROVENANCE"
    try:
        doc = json.loads(proof.read_text(encoding="utf-8"))
        method = doc.get("capture_method")
        valid_capture = (
            method == "adb-exec-out-screencap"
            or (method == "instrumentation-ui-automation"
                and doc.get("activity_window_focus") is True
                and doc.get("test_assertions_passed") is True)
            or (method == "instrumentation-uiautomator-cropped"
                and doc.get("activity_window_focus") is True
                and doc.get("test_assertions_passed") is True
                and doc.get("source_surface_mapped") is True)
        )
        if (doc.get("package") != package or doc.get("build_sha") != expected_sha
                or doc.get("foreground_verified") is not True
                or not valid_capture
                or not str(doc.get("resumed_activity", "")).startswith(package + "/")
                or doc.get("screenshot_sha256") != sha256(screen)):
            return False, "INVALID_CAPTURE_PROVENANCE"
    except (ValueError, OSError):
        return False, "INVALID_CAPTURE_PROVENANCE"
    return True, "VERIFIED_APP_FOREGROUND"


def compare_images(reference: Path, screenshot: Path, output: Path, item: dict) -> dict:
    ref = Image.open(reference).convert("RGB")
    runtime = Image.open(screenshot).convert("RGB")
    if min(runtime.size) < 200:
        raise ValueError("INVALID_CAPTURE_DIMENSIONS")
    aspect_difference = abs(ref.width / ref.height - runtime.width / runtime.height)
    if aspect_difference > 0.09:
        raise ValueError("ASPECT_RATIO_MISMATCH")
    scaled = ref.resize(runtime.size, Image.Resampling.LANCZOS)
    delta = ImageChops.difference(scaled, runtime)
    if max(ImageStat.Stat(runtime).stddev) < 4.0:
        raise ValueError("BLANK_OR_UNIFORM_SCREENSHOT")

    output.mkdir(parents=True, exist_ok=True)
    scaled.save(output / "frozen.png")
    runtime.save(output / "runtime.png")
    Image.blend(scaled, runtime, 0.5).save(output / "overlay.png")
    delta.save(output / "diff.png")
    side = Image.new("RGB", (runtime.width * 2, runtime.height))
    side.paste(scaled, (0, 0))
    side.paste(runtime, (runtime.width, 0))
    side.save(output / "side-by-side.png")
    scores = []
    for region in item["rois"]:
        x, y, w, h = region["rect"]
        bounds = (int(x * runtime.width), int(y * runtime.height),
                  int((x + w) * runtime.width), int((y + h) * runtime.height))
        actual = ImageStat.Stat(delta.crop(bounds)).mean
        mae = sum(actual) / 3.0
        scores.append({"name": region["name"], "mae": round(mae, 3),
                       "limit": region["max_mae"], "pass": mae <= region["max_mae"]})
    return {"reference_dimensions": list(ref.size), "runtime_dimensions": list(runtime.size),
            "reference_sha256": sha256(reference), "runtime_sha256": sha256(screenshot),
            "reference_scaled_for_comparison": ref.size != runtime.size,
            "rois": scores, "image_paths": ["frozen.png", "runtime.png",
                                        "overlay.png", "diff.png", "side-by-side.png"]}


def evaluate(manifest: dict, artifact_root: Path, output: Path, build_sha: str) -> dict:
    gates = find_gates(artifact_root, build_sha)
    entries = []
    for item in manifest["surfaces"]:
        e = {"id": item["id"], "module": item["module"], "gate": item["gate"],
             "authority": item["reference"], "screenshot": item["screenshot"],
             "mode": item["mode"], "status": "BLOCKED", "reason": "",
             "gate_classification": "NOT_RUN", "comparison": None,
             "note": item.get("note", "")}
        entries.append(e)
        reference = safe_path(ROOT, item["reference"]) if item["reference"] else None
        if reference is None or not reference.is_file():
            e.update(status="FAIL_AUTHORITY", reason="FROZEN_AUTHORITY_UNMAPPED")
            continue
        if not item["gate"]:
            e["reason"] = "NO_RUNTIME_GATE_FOR_SURFACE"
            continue
        gate = gates.get(item["gate"])
        if not gate:
            e["reason"] = "GATE_NOT_EXECUTED_OR_OTHER_SHA"
            continue
        if gate.get("error"):
            e["reason"] = gate["error"]
            continue
        doc = gate["doc"]
        e["gate_classification"] = doc.get("classification", "UNKNOWN")
        screen = safe_path(gate["folder"], item["screenshot"])
        if screen.is_file():
            try:
                e["comparison"] = compare_images(reference, screen, output / item["id"], item)
            except (OSError, ValueError) as ex:
                e.update(status="FAIL_EVIDENCE", reason=str(ex))
                continue
        if doc.get("classification") == "FAIL_TEST":
            e.update(status="FAIL_TEST", reason=doc.get("failure_reason", "TEST_FAILED"))
            continue
        if doc.get("classification") in ("FAIL_EVIDENCE", "FAIL_ARTIFACT"):
            e.update(status="FAIL_EVIDENCE", reason=doc.get("failure_reason", "GATE_EVIDENCE_FAILED"))
            continue
        if doc.get("classification") != "PASS":
            e.update(status="BLOCKED", reason="GATE_" + str(doc.get("classification", "UNKNOWN")))
            continue
        if not screen.is_file():
            e.update(status="BLOCKED", reason="SCREENSHOT_MISSING")
            continue
        valid, reason = provenance_valid(screen, build_sha, manifest["package"])
        if not valid:
            e.update(status="BLOCKED", reason=reason)
            continue
        if any(not roi["pass"] for roi in e["comparison"]["rois"]):
            e.update(status="FAIL_VISUAL", reason="ROI_THRESHOLD_EXCEEDED")
        elif item["mode"] == "review":
            e.update(status="REVIEW_REQUIRED", reason="USER_PHYSICAL_VISUAL_SIGNOFF_REQUIRED")
        else:
            e.update(status="AUTO_CHECK_PASS", reason="AUTOMATED_ONLY_NOT_USER_ACCEPTANCE")

    modules = {}
    for name in manifest["required_modules"]:
        entries_for_module = [x for x in entries if x["module"] == name]
        modules[name] = sorted(entries_for_module, key=lambda x: RANK.get(x["status"], 0))[0]["status"]
    statuses = [x["status"] for x in entries]
    # The CI automated gate can be ready even when human photo review is pending.
    # This is NEVER a user visual PASS.
    readiness = ("INCOMPLETE_OR_FAIL" if any(s in BAD for s in statuses)
                 else "READY_FOR_USER_REVIEW" if "REVIEW_REQUIRED" in statuses
                 else "AUTO_CHECK_PASS")
    return {"schema_version": 1, "build_sha": build_sha,
            "summary": {"surfaces": len(entries), "modules": modules,
                        "status": readiness, "user_physical_acceptance": "NOT_PERFORMED"},
            "surfaces": entries}


def render(result: dict, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    (output / "report.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    lines = ["# YuJian Frozen / Android Runtime Hi-Fi Audit", "",
             "Build: " + result["build_sha"], "",
             "AUTO_CHECK_PASS is NOT user physical acceptance. Missing evidence never passes.", "",
             "| Module | Surface | Gate | Status | Reason |", "|---|---|---|---|---|"]
    blocks = []
    for e in result["surfaces"]:
        vals = [e["module"], e["id"], e["gate"] or "(missing)", e["status"], e["reason"]]
        lines.append("| " + " | ".join(str(v).replace("|", "/") for v in vals) + " |")
        title = html.escape(e["module"] + " / " + e["id"])
        body = "<h2>" + title + "</h2><p><b>" + html.escape(e["status"]) + "</b> — " + html.escape(e["reason"]) + "</p>"
        if e["comparison"]:
            img = html.escape(e["id"] + "/side-by-side.png", quote=True)
            body += '<img class="compare" src="' + img + '" alt="Frozen left; actual runtime right">'
            body += "<p>ROI differences (RGB MAE): " + html.escape(
                json.dumps(e["comparison"]["rois"], ensure_ascii=False)) + "</p>"
            body += '<div class="tiles">'
            for kind in ("overlay", "diff"):
                src = html.escape(e["id"] + "/" + kind + ".png", quote=True)
                body += '<figure><img src="' + src + '"><figcaption>' + kind + "</figcaption></figure>"
            body += "</div>"
        blocks.append("<section>" + body + "</section>")
    lines += ["", "Review each artifact image and complete user physical acceptance separately.", ""]
    (output / "report.md").write_text("\n".join(lines), encoding="utf-8")
    style = "body{font:15px system-ui;margin:2rem auto;max-width:1120px;padding:0 1rem;background:#f7faf9;color:#243632}section{background:#fff;border:1px solid #d9e3df;border-radius:14px;padding:20px;margin:20px 0}img{max-width:100%;height:auto}.compare{width:100%}.tiles{display:flex;gap:12px}.tiles figure{width:50%;margin:0}.tiles img{width:100%}h1{font-size:26px}p{overflow-wrap:anywhere}"
    doc = "<!doctype html><html lang='zh'><meta charset='utf-8'><title>YuJian Hi-Fi Audit</title><style>" + style + "</style><h1>YuJian · Frozen vs Android Runtime</h1><p>Commit: " + html.escape(result["build_sha"]) + "</p><p>Automated scores never equal user visual acceptance.</p>" + "\n".join(blocks) + "</html>"
    (output / "index.html").write_text(doc, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--artifacts", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--build-sha", default="")
    parser.add_argument("--validate-manifest", action="store_true")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    manifest = load_manifest(args.manifest)
    if args.validate_manifest:
        print("HIFI_MANIFEST_VALID surfaces=" + str(len(manifest["surfaces"])))
        return 0
    if not args.artifacts or not args.output or len(args.build_sha) != 40:
        parser.error("--artifacts, --output and a 40-character --build-sha are required")
    result = evaluate(manifest, args.artifacts, args.output, args.build_sha)
    render(result, args.output)
    for e in result["surfaces"]:
        print("HIFI_SURFACE " + e["id"] + " " + e["status"] + " " + e["reason"])
    print("HIFI_REPORT=" + str(args.output / "index.html"))
    if args.strict and result["summary"]["status"] == "INCOMPLETE_OR_FAIL":
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
