#!/usr/bin/env python3
"""Render reviewable, additive hi-fi states for the Recognition journey."""

from __future__ import annotations

import base64
import hashlib
import html
import io
import json
import subprocess
from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[4]
ASSET_DIR = ROOT / "design/system_v3/00_Product_Master_Flow/assets"
SVG_PATH = ASSET_DIR / "Recognition_New_States_HiFi_V1.svg"
PNG_PATH = ASSET_DIR / "Recognition_New_States_HiFi_V1.png"
MANIFEST_PATH = ASSET_DIR / "Recognition_New_States_Manifest_V1.json"
SOURCE_PATH = "design/pages/recognition/design/05_Result_High_Frozen.png"
WIDTH, HEIGHT = 2800, 1600
FONT = "WenQuanYi Micro Hei, Noto Sans CJK SC, Microsoft YaHei, sans-serif"


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def label(x: int, y: int, value: str, size: int, color: str = "#15373d", weight: int = 400) -> str:
    return f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{color}">{esc(value)}</text>'


def rounded(x: int, y: int, w: int, h: int, fill: str, radius: int = 28, stroke: str | None = None) -> str:
    border = f' stroke="{stroke}" stroke-width="2"' if stroke else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}"{border}/>'


def draw() -> None:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    source = ROOT / SOURCE_PATH
    source_image = Image.open(source).convert("RGB")
    source_width, source_height = source_image.size
    thumb = ImageOps.fit(source_image, (680, 1160), method=Image.Resampling.LANCZOS)
    image_buffer = io.BytesIO()
    thumb.save(image_buffer, format="JPEG", quality=82, optimize=True)
    source_data = "data:image/jpeg;base64," + base64.b64encode(image_buffer.getvalue()).decode("ascii")
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()

    p: list[str] = [f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}" role="img" aria-labelledby="title desc">
<title id="title">渔见拍照识鱼主链路新增状态高保真评审稿</title>
<desc id="desc">左侧是覆盖在实时相机预览上的首次引导组件；右侧是覆盖在已冻结 High 结果页上的脏草稿放弃确认。均为新增交互状态，不替代既有 Frozen 页面截图。</desc>
<defs>
  <linearGradient id="page" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#edf5f1"/><stop offset="1" stop-color="#f7f3e9"/></linearGradient>
  <linearGradient id="camera" x1="0" y1="0" x2="0.8" y2="1"><stop offset="0" stop-color="#244c53"/><stop offset="0.52" stop-color="#346c69"/><stop offset="1" stop-color="#102d35"/></linearGradient>
  <filter id="soft"><feGaussianBlur stdDeviation="14"/></filter>
  <clipPath id="high-clip"><rect x="1715" y="228" width="680" height="1160" rx="34"/></clipPath>
</defs>
<rect width="{WIDTH}" height="{HEIGHT}" fill="url(#page)"/>
''']

    p.append(label(72, 84, "渔见 · 拍照识鱼主链路新增界面", 48, weight=700))
    p.append(label(76, 132, "HI-FI REVIEW V1 · 新增组件状态，不改写既有 Frozen 页面 authority", 23, "#527076", 700))

    # Capture guide: show the overlay against a neutral live-preview placeholder.
    p.append(rounded(72, 175, 1300, 1300, "#ffffff", 34, "#d5e2de"))
    p.append(label(112, 232, "01 · 首次拍照引导", 31, "#2d6d70", 700))
    p.append(label(112, 270, "相机入口 · Preview Ready 后仅展示一次", 22, "#527076"))
    p.append(rounded(112, 300, 1220, 1135, "url(#camera)", 30))
    p.append('<circle cx="560" cy="640" r="190" fill="#ffffff" opacity=".045"/>')
    p.append('<path d="M145 970 C370 850 510 1090 730 985 S1110 910 1300 1015 L1300 1405 L145 1405 Z" fill="#7db3a3" opacity=".20"/>')
    p.append(label(168, 355, "‹", 44, "#ffffff", 500))
    p.append(label(232, 350, "拍照识鱼", 25, "#ffffff", 700))
    p.append(label(160, 1398, "CameraX 实时预览上下文（此渐变为布局占位，不是截图）", 17, "#ffffff", 500))
    p.append('<rect x="112" y="300" width="1220" height="1135" rx="30" fill="#07181d" opacity=".46"/>')
    p.append(rounded(245, 625, 955, 515, "#ffffff", 34, "#e3ebe8"))
    p.append(label(302, 704, "开始拍照识鱼", 35, "#18383d", 700))
    p.append(label(302, 782, "尽量让鱼体轮廓清晰，手持鱼获也可以。", 23, "#52686d"))
    p.append(label(302, 825, "拍摄后 AI 会自动识别，结果可确认或修改。", 23, "#52686d"))
    p.append('<line x1="278" y1="890" x2="1168" y2="890" stroke="#e7eeeb" stroke-width="2"/>')
    p.append(label(620, 1012, "跳过", 22, "#287176", 600))
    p.append(rounded(826, 948, 310, 64, "#e9f3f0", 22))
    p.append(label(891, 990, "开始拍照", 22, "#21656a", 700))
    p.append(label(302, 1085, "两个动作都只关闭引导；之后由用户主动点快门。", 17, "#788b8b"))

    # Dirty draft prompt over the real Frozen High screenshot as its context.
    p.append(rounded(1428, 175, 1300, 1300, "#ffffff", 34, "#d5e2de"))
    p.append(label(1468, 232, "02 · 草稿放弃确认", 31, "#a97839", 700))
    p.append(label(1468, 270, "Result 页面 · 仅当有实质修改或保存待核验时出现", 22, "#527076"))
    p.append(f'<image x="1715" y="228" width="680" height="1160" xlink:href="{source_data}" preserveAspectRatio="xMidYMid slice" clip-path="url(#high-clip)"/>')
    p.append('<rect x="1715" y="228" width="680" height="1160" rx="34" fill="#07181d" opacity=".56"/>')
    p.append(rounded(1582, 615, 990, 520, "#ffffff", 34, "#e3ebe8"))
    p.append(label(1645, 700, "放弃本次修改？", 34, "#18383d", 700))
    p.append(label(1645, 780, "尚未保存的鱼种与鱼获信息会被清除。", 23, "#52686d"))
    p.append('<line x1="1618" y1="886" x2="2536" y2="886" stroke="#e7eeeb" stroke-width="2"/>')
    p.append(label(1855, 1003, "继续编辑", 22, "#287176", 600))
    p.append(rounded(2116, 936, 388, 68, "#e9f3f0", 22))
    p.append(label(2160, 980, "放弃并返回拍摄", 21, "#21656a", 700))
    p.append(label(1645, 1080, "确认重拍时按钮文案为“放弃并重新拍摄”。", 17, "#788b8b"))

    p.append(rounded(72, 1500, 2656, 54, "#e5efeb", 20))
    p.append(label(100, 1536, "边界：左侧预览是占位场景，不是 Capture 截图；右侧底图是 Frozen High 的缩略上下文；两个弹框都是新增 UI，视觉稿不替代运行时测试。", 18, "#527076", 500))
    p.append("</svg>")
    SVG_PATH.write_text("\n".join(p), encoding="utf-8")
    subprocess.run([
        "inkscape", str(SVG_PATH), "--export-type=png", f"--export-filename={PNG_PATH}",
        f"--export-width={WIDTH}", f"--export-height={HEIGHT}",
    ], check=True)

    def sha(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    manifest = {
        "schema_version": "1.0",
        "asset_id": "recognition_new_states_hifi_v1",
        "status": "HI_FI_REVIEW_READY",
        "runtime_visual_authority": False,
        "surface_states": [
            {"id": "capture_first_guide_overlay", "path": "design/system_v3/00_Product_Master_Flow/assets/Recognition_New_States_HiFi_V1.svg", "artboard": {"x": 72, "y": 175, "width": 1300, "height": 1300}, "status": "ADDITIVE_UI_REVIEW"},
            {"id": "dirty_draft_discard_confirmation", "path": "design/system_v3/00_Product_Master_Flow/assets/Recognition_New_States_HiFi_V1.svg", "artboard": {"x": 1428, "y": 175, "width": 1300, "height": 1300}, "status": "ADDITIVE_UI_REVIEW"},
        ],
        "png": {"path": PNG_PATH.relative_to(ROOT).as_posix(), "sha256": sha(PNG_PATH), "width": WIDTH, "height": HEIGHT, "bytes": PNG_PATH.stat().st_size},
        "svg": {"path": SVG_PATH.relative_to(ROOT).as_posix(), "sha256": sha(SVG_PATH), "width": WIDTH, "height": HEIGHT, "bytes": SVG_PATH.stat().st_size},
        "frozen_context_source": {"path": SOURCE_PATH, "sha256": source_hash, "width": source_width, "height": source_height},
        "capture_background_is_live_context_placeholder": True,
        "frozen_assets_modified": False,
        "generated_by": "design/system_v3/00_Product_Master_Flow/tools/render_recognition_new_states_hifi_v1.py",
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"svg": SVG_PATH.as_posix(), "png": PNG_PATH.as_posix(), "manifest": MANIFEST_PATH.as_posix(), "png_sha256": sha(PNG_PATH), "source_sha256": source_hash}, ensure_ascii=False))


if __name__ == "__main__":
    draw()
