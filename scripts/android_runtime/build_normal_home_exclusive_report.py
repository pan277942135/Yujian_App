#!/usr/bin/env python3
"""Build comparison images and a machine-readable report from one Normal Home run."""

from __future__ import annotations

import csv
import hashlib
import html
import json
import os
import re
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFont

EVIDENCE = Path(sys.argv[1])
HOME = EVIDENCE / "normal-home-v1"
PRODUCT_HEAD = os.environ.get("PRODUCT_HEAD", "")
VALIDATION_HEAD = os.environ.get("VALIDATION_HEAD", "")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def json_file(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def image_info(path: Path):
    if not path.is_file():
        return None
    try:
        with Image.open(path) as image:
            image.verify()
        with Image.open(path) as image:
            return {
                "file": path.name,
                "sha256": sha256(path),
                "bytes": path.stat().st_size,
                "width": image.width,
                "height": image.height,
                "format": image.format,
            }
    except Exception as exc:
        return {"file": path.name, "invalid": str(exc)}


def parse_text_metrics():
    metrics = {}
    for log_name in ("targeted-instrumentation.log", "normal-home-suite-instrumentation.log", "android-logcat-measurements.log"):
        log = EVIDENCE / log_name
        if not log.is_file():
            continue
        for line in log.read_text(encoding="utf-8", errors="replace").splitlines():
            marker = "NORMAL_HOME_TEXT_LAYOUT "
            if marker not in line:
                continue
            record = dict(re.findall(r"([A-Za-z][A-Za-z0-9_]*)=([^\s]+)", line.split(marker, 1)[1]))
            if "tag" in record:
                record["source_log"] = log_name
                metrics[record["tag"]] = record
    return list(metrics.values())


def classify_text(record):
    width_overflow = record.get("didOverflowWidth")
    height_overflow = record.get("didOverflowHeight")
    glyph_parent = record.get("glyphOutlineInsideParent")
    if width_overflow == "true" or height_overflow == "true" or glyph_parent == "false":
        return "FAIL_PRODUCT"
    if width_overflow == "false" and height_overflow == "false" and glyph_parent == "true":
        return "PASS"
    return "NOT_MEASURED"


def family(tag):
    if "brand-title" in tag:
        return "Brand title"
    if "stat-" in tag:
        return "Statistics"
    if "recent-title" in tag or "recent-all" in tag:
        return "Recent catches heading"
    if "catch-species" in tag:
        return "Hero species"
    if "catch-measurement" in tag:
        return "Hero length / weight"
    if "catch-meta" in tag:
        return "Hero time / location"
    if "capture-cta" in tag:
        return "Record next fish CTA"
    return "Other"


metrics = parse_text_metrics()
result = json_file(EVIDENCE / "normal_home_brand_title_result.json")
device = json_file(EVIDENCE / "device_metrics.json")
home_gate = json_file(HOME / "runtime_gate_result.json")
visual_gate = json_file(HOME / "09_normal_home_visual_parity_report.json")
screenshot_meta = json_file(EVIDENCE / "runtime_screenshot.json")
photo_meta = json_file(EVIDENCE / "photo_provenance.json")

runtime_src = HOME / "04_normal_home_runtime_1080x1920.png"
frozen_src = HOME / "03_normal_home_frozen_1080x1920.png"
frozen_source_kind = "gate-copied"
if not frozen_src.is_file():
    frozen_src = Path("design/system/core_visual_v1/reference/normal_home_v1.png")
    frozen_source_kind = "repository-frozen-authority"
runtime_out = EVIDENCE / "runtime.png"
frozen_out = EVIDENCE / "frozen.png"
if runtime_src.is_file():
    shutil.copyfile(runtime_src, runtime_out)
if frozen_src.is_file():
    shutil.copyfile(frozen_src, frozen_out)

runtime_info = image_info(runtime_out)
frozen_info = image_info(frozen_out)
comparison = {
    "generated": [],
    "coordinate_transform": "none",
    "source_images_resized": False,
    "frozen_source_kind": frozen_source_kind,
    "frozen_source_path": frozen_src.as_posix(),
}
visual_classification = "REVIEW_REQUIRED"
if runtime_info and frozen_info and not runtime_info.get("invalid") and not frozen_info.get("invalid"):
    with Image.open(frozen_out) as frozen_image, Image.open(runtime_out) as runtime_image:
        frozen = frozen_image.convert("RGB")
        runtime = runtime_image.convert("RGB")
        comparison["frozen_dimensions"] = [frozen.width, frozen.height]
        comparison["runtime_dimensions"] = [runtime.width, runtime.height]
        comparison["aspect_ratio_match"] = abs(frozen.width / frozen.height - runtime.width / runtime.height) < 0.000001
        if frozen.size == runtime.size:
            width, height = frozen.size
            side = Image.new("RGB", (width * 2, height + 42), "#f3f5f6")
            draw = ImageDraw.Draw(side)
            draw.text((18, 13), "FROZEN AUTHORITY", fill="#153247")
            draw.text((width + 18, 13), "ANDROID RUNTIME", fill="#153247")
            side.paste(frozen, (0, 42))
            side.paste(runtime, (width, 42))
            side.save(EVIDENCE / "side-by-side.png")
            Image.blend(frozen, runtime, 0.5).save(EVIDENCE / "overlay.png")
            diff = ImageChops.difference(runtime, frozen)
            ImageEnhance.Contrast(diff).enhance(3.0).save(EVIDENCE / "diff.png")
            comparison["coordinate_transform"] = "identity; native dimensions match"
            comparison["generated"].extend(["side-by-side.png", "overlay.png", "diff.png"])

            lines = [
                "Measured Android TextLayoutResult records (Compose root coordinates):",
                "Exact bounds and overflow flags are in report.json / HTML table.",
                "The annotation panel is separate from the screenshots; no coordinate offset is assumed.",
            ]
            for record in metrics:
                state = classify_text(record)
                lines.append(
                    "{} | {} | font={} | line={} | effective_px={} | overflow_w={} overflow_h={} | glyph_inside_parent={}".format(
                        record.get("tag", "?"),
                        state,
                        record.get("actualFontSize", "NOT_MEASURED"),
                        record.get("lineHeight", "NOT_MEASURED"),
                        record.get("effectiveFontPx", "NOT_MEASURED"),
                        record.get("didOverflowWidth", "NOT_MEASURED"),
                        record.get("didOverflowHeight", "NOT_MEASURED"),
                        record.get("glyphOutlineInsideParent", "NOT_MEASURED"),
                    )
                )
            panel_height = max(210, 28 + 18 * len(lines))
            annotated = Image.new("RGB", (width * 2, height + panel_height), "#f3f5f6")
            annotated.paste(side, (0, 0))
            draw = ImageDraw.Draw(annotated)
            try:
                font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
            except OSError:
                font = ImageFont.load_default()
            y = height + 56
            for line in lines:
                draw.text((16, y), line, fill="#172b3a", font=font)
                y += 18
            annotated.save(EVIDENCE / "annotated-differences.png")
            comparison["generated"].append("annotated-differences.png")
        else:
            comparison["aspect_ratio_match"] = False
            comparison["coordinate_transform"] = "none; native dimensions differ; no stretch or overlay"
    required = ["runtime.png", "frozen.png", "side-by-side.png", "overlay.png", "diff.png", "annotated-differences.png"]
    comparison["all_required_images_generated"] = all((EVIDENCE / name).is_file() for name in required)
else:
    comparison["error"] = "Runtime or Frozen source PNG is missing or invalid"
    comparison["all_required_images_generated"] = False

text_rows = []
for record in metrics:
    text_rows.append({
        "component": family(record.get("tag", "")),
        "tag": record.get("tag", ""),
        "parent": record.get("parent", ""),
        "referenceFontSp": record.get("referenceFontSp", ""),
        "actualFontSize": record.get("actualFontSize", ""),
        "lineHeight": record.get("lineHeight", ""),
        "density": record.get("density", ""),
        "fontScale": record.get("fontScale", ""),
        "effectiveFontPx": record.get("effectiveFontPx", ""),
        "glyphOutlineRootBoundsPx": record.get("glyphOutlineRootBoundsPx", ""),
        "nodeBoundsPx": record.get("nodeBoundsPx", ""),
        "parentRectPx": record.get("parentRectPx", ""),
        "textParentWidthRatio": record.get("textParentWidthRatio", ""),
        "lineCount": record.get("lineCount", ""),
        "didOverflowWidth": record.get("didOverflowWidth", ""),
        "didOverflowHeight": record.get("didOverflowHeight", ""),
        "glyphOutlineInsideParent": record.get("glyphOutlineInsideParent", ""),
        "classification": classify_text(record),
        "source_log": record.get("source_log", ""),
    })

with (EVIDENCE / "typography-measurements.csv").open("w", newline="", encoding="utf-8") as handle:
    columns = list(text_rows[0].keys()) if text_rows else [
        "component", "tag", "parent", "referenceFontSp", "actualFontSize", "lineHeight",
        "density", "fontScale", "effectiveFontPx", "glyphOutlineRootBoundsPx",
        "nodeBoundsPx", "parentRectPx", "textParentWidthRatio", "lineCount",
        "didOverflowWidth", "didOverflowHeight", "glyphOutlineInsideParent",
        "classification", "source_log",
    ]
    writer = csv.DictWriter(handle, fieldnames=columns)
    writer.writeheader()
    writer.writerows(text_rows)

apk_text = (EVIDENCE / "apk_provenance.txt").read_text(encoding="utf-8", errors="replace") if (EVIDENCE / "apk_provenance.txt").is_file() else ""
apk_values = dict(re.findall(r"^([A-Z0-9_]+)=(.*)$", apk_text, re.MULTILINE))
identity = json_file(EVIDENCE / "validation_identity.json") or {}
result_status = result.get("status") if result else "NOT_RECORDED"
suite_rc = result.get("normal_home_suite_exit_code") if result else None
target_rc = result.get("targeted_exit_code") if result else None
visual_classification = "REVIEW_REQUIRED"
if result_status == "PASS" and comparison.get("all_required_images_generated") and suite_rc == 0 and target_rc == 0:
    visual_classification = "REVIEW_REQUIRED"

report = {
    "scope": "Normal Home existing-data page only",
    "product_head": PRODUCT_HEAD,
    "validation_head": VALIDATION_HEAD,
    "test_source_blob_sha": identity.get("test_source_blob_sha"),
    "build": {
        "app_apk_sha256": apk_values.get("APP_APK_SHA256"),
        "test_apk_sha256": apk_values.get("TEST_APK_SHA256"),
        "build_sha": apk_values.get("BUILD_SHA"),
        "result": result_status,
    },
    "device": device,
    "home_gate": home_gate,
    "visual_gate": visual_gate,
    "screenshot_provenance": screenshot_meta,
    "runtime_image": runtime_info,
    "frozen_image": frozen_info,
    "photo_provenance": photo_meta,
    "text_layout_measurements": text_rows,
    "comparison": comparison,
    "classifications": {
        "title_overflow": next((row["classification"] for row in text_rows if row["component"] == "Brand title"), "NOT_MEASURED"),
        "text_layout": "FAIL_PRODUCT" if any(row["classification"] == "FAIL_PRODUCT" for row in text_rows) else ("PASS" if text_rows else "NOT_MEASURED"),
        "hero_corner_alignment": "REVIEW_REQUIRED" if suite_rc == 0 else ("FAIL_PRODUCT" if suite_rc not in (None, 0) else "NOT_RUN"),
        "statistics_carousel_navigation": "REVIEW_REQUIRED" if suite_rc == 0 else ("FAIL_PRODUCT" if suite_rc not in (None, 0) else "NOT_RUN"),
        "visual_parity": visual_classification,
        "screenshot_evidence": "PASS" if runtime_info and frozen_info and comparison.get("all_required_images_generated") else "FAIL_EVIDENCE",
    },
    "notes": [
        "The source Android screenshot is copied byte-for-byte to runtime.png; comparison images are derived from the two originals.",
        "Frozen is never stretched. An overlay is generated only when native dimensions match exactly.",
        "Compose TextLayoutResult bounds are recorded in the test root coordinate space. They are not projected onto the full-screen screenshot without a measured inset transform.",
        "Synthetic portrait fixtures are confined to the instrumented cover-viewport geometry test. The production Home runtime capture is separately seeded with the packaged sample_recent_catch.jpg asset and its provenance is recorded.",
        "Visual parity remains REVIEW_REQUIRED pending human inspection of the generated comparison.",
        "If the existing gate exits before staging its frozen copy, frozen.png is copied byte-for-byte from the authoritative repository Frozen path; runtime evidence remains the native Android capture.",
    ],
}
(EVIDENCE / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def img_section(name, title):
    p = EVIDENCE / name
    if p.is_file():
        return '<section><h2>{}</h2><img src="{}" alt="{}"></section>'.format(html.escape(title), html.escape(name), html.escape(title))
    return '<section><h2>{}</h2><p>Not generated because required source evidence is missing or incompatible.</p></section>'.format(html.escape(title))

rows = []
for row in text_rows:
    rows.append("<tr>" + "".join("<td>{}</td>".format(html.escape(str(row.get(key, "")))) for key in (
        "component", "tag", "actualFontSize", "lineHeight", "effectiveFontPx",
        "glyphOutlineRootBoundsPx", "parentRectPx", "didOverflowWidth",
        "didOverflowHeight", "glyphOutlineInsideParent", "classification",
    )) + "</tr>")
table = (
    "<table><thead><tr><th>Component</th><th>Tag</th><th>Font</th><th>Line</th>"
    "<th>Effective px</th><th>Glyph bounds</th><th>Parent bounds</th>"
    "<th>Overflow W</th><th>Overflow H</th><th>Glyph inside parent</th><th>Result</th>"
    "</tr></thead><tbody>" + "".join(rows) + "</tbody></table>"
)
html_report = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Normal Home Exclusive Runtime Acceptance</title><style>
body{font:15px/1.5 system-ui,sans-serif;color:#173142;background:#f5f7f7;margin:0;padding:24px}
main{max-width:1440px;margin:auto}section,header{background:white;border:1px solid #dce5e7;border-radius:12px;padding:18px;margin:16px 0}
img{max-width:100%;height:auto;border:1px solid #dce5e7}table{border-collapse:collapse;display:block;overflow:auto;background:white}
th,td{border:1px solid #dce5e7;padding:6px;text-align:left;font-size:12px}th{position:sticky;top:0;background:#eaf1f2}
code{word-break:break-all}.badge{font-weight:700;color:#a04a00}
</style></head><body><main><header><h1>Normal Home Exclusive Runtime Acceptance</h1>
<p>Product HEAD: <code>""" + html.escape(PRODUCT_HEAD) + """</code><br>
Validation HEAD: <code>""" + html.escape(VALIDATION_HEAD) + """</code><br>
Android test result: <strong>""" + html.escape(str(result_status)) + """</strong><br>
Visual parity: <strong class="badge">""" + html.escape(visual_classification) + """</strong></p>
<p>Device: <code>""" + html.escape(json.dumps(device or {}, ensure_ascii=False)) + """</code></p></header>
""" + img_section("side-by-side.png", "Frozen (left) vs native Android runtime (right)") + """
""" + img_section("overlay.png", "Overlay") + """
""" + img_section("diff.png", "Pixel difference (dynamic photo content is not a UI defect by itself)") + """
""" + img_section("annotated-differences.png", "Annotated comparison and measured typography summary") + """
<section><h2>TextLayoutResult measurements</h2>""" + table + """</section>
<section><h2>Evidence</h2><ul>
<li>Original runtime screenshot: <a href="runtime.png">runtime.png</a></li>
<li>Frozen reference: <a href="frozen.png">frozen.png</a></li>
<li>Device metrics: <a href="device_metrics.json">device_metrics.json</a></li>
<li>Test result: <a href="normal_home_brand_title_result.json">normal_home_brand_title_result.json</a></li>
<li>SHA256SUMS: <a href="SHA256SUMS">SHA256SUMS</a></li>
</ul></section></main></body></html>"""
(EVIDENCE / "report.html").write_text(html_report, encoding="utf-8")
print(json.dumps({
    "product_head": PRODUCT_HEAD,
    "validation_head": VALIDATION_HEAD,
    "runtime_image": runtime_info,
    "frozen_image": frozen_info,
    "text_metric_count": len(text_rows),
    "comparison": comparison,
    "classification": report["classifications"],
}, ensure_ascii=False))
