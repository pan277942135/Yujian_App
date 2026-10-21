#!/usr/bin/env python3
"""Render the editable 5K recognition journey SVG and its PNG companion."""

from __future__ import annotations

import base64
import hashlib
import html
import json
import subprocess
from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[4]
BASE = ROOT / "design/system_v3/00_Product_Master_Flow"
ASSET_DIR = BASE / "assets"
SVG_PATH = ASSET_DIR / "Recognition_Main_Journey_Overview_V1.svg"
PNG_PATH = ASSET_DIR / "Recognition_Main_Journey_Overview_V1.png"
MANIFEST_PATH = ASSET_DIR / "Recognition_Main_Journey_Asset_Manifest_V1.json"
WIDTH, HEIGHT = 5120, 2880
FONT = "WenQuanYi Micro Hei, Noto Sans CJK SC, Microsoft YaHei, sans-serif"

COLORS = {
    "bg": "#eff5f1", "ink": "#15373d", "muted": "#527076", "line": "#d5e2de",
    "surface": "#ffffff", "teal": "#2d6d70", "teal_pale": "#e8f2ef",
    "gold": "#c4964f", "gold_pale": "#fff6e7", "green": "#467656",
    "green_pale": "#eaf4eb", "blue": "#446d86", "blue_pale": "#edf3f8",
    "red": "#9d5648", "red_pale": "#fff0ec", "purple": "#705c80",
    "purple_pale": "#f3eff8", "white": "#ffffff"
}

SOURCES = {
    "home": "design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png",
    "processing": "design/pages/recognition/design/04_Fish_Identifying_Frozen.png",
    "high": "design/pages/recognition/design/05_Result_High_Frozen.png",
    "medium": "design/pages/recognition/design/06_Result_Medium_Frozen.png",
    "low": "design/pages/recognition/design/07_Result_Low_Frozen.png",
    "no_fish": "design/pages/recognition/design/08_Error_No_Fish_Frozen.png",
    "quality": "design/pages/recognition/design/09_Error_Image_Quality_Frozen.png",
}

parts: list[str] = []
embedded_sources: dict[str, dict[str, str | int]] = {}


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def text(x: int, y: int, value: str | list[str], size: int = 32, fill: str | None = None,
         weight: int = 400, line: int | None = None, anchor: str = "start", opacity: float = 1.0) -> None:
    fill = fill or COLORS["ink"]
    rows = value if isinstance(value, list) else [value]
    if len(rows) == 1:
        parts.append(f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{fill}" text-anchor="{anchor}" opacity="{opacity}">{esc(rows[0])}</text>')
        return
    line = line or int(size * 1.38)
    tspans = "".join(f'<tspan x="{x}" dy="{0 if i == 0 else line}">{esc(row)}</tspan>' for i, row in enumerate(rows))
    parts.append(f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" fill="{fill}" text-anchor="{anchor}" opacity="{opacity}">{tspans}</text>')


def rect(x: int, y: int, w: int, h: int, fill: str, stroke: str | None = None,
         radius: int = 24, stroke_width: int = 2, opacity: float = 1.0) -> None:
    stroke_attr = f' stroke="{stroke}" stroke-width="{stroke_width}"' if stroke else ""
    parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}"{stroke_attr} opacity="{opacity}"/>')


def pill(x: int, y: int, w: int, label: str, fill: str, ink: str, size: int = 25, h: int = 42) -> None:
    rect(x, y, w, h, fill, radius=h // 2)
    text(x + w // 2, y + h // 2 + size // 3, label, size=size, fill=ink, weight=700, anchor="middle")


def arrow(x1: int, y: int, x2: int, label: str) -> None:
    parts.append(f'<line x1="{x1}" y1="{y}" x2="{x2}" y2="{y}" stroke="{COLORS["teal"]}" stroke-width="6" marker-end="url(#arrow)"/>')
    if not label:
        return
    mid = (x1 + x2) // 2
    width = max(140, len(label) * 38 + 38)
    rect(mid - width // 2, y - 29, width, 58, COLORS["white"], COLORS["line"], radius=22, stroke_width=2)
    text(mid, y + 10, label, size=26, fill=COLORS["teal"], weight=700, anchor="middle")


def panel(x: int, y: int, w: int, h: int, number: str, title: str, status: str | None = None) -> None:
    rect(x, y, w, h, COLORS["surface"], COLORS["line"], radius=30, stroke_width=3)
    parts.append(f'<circle cx="{x + 42}" cy="{y + 56}" r="23" fill="{COLORS["teal"]}"/>')
    text(x + 42, y + 65, number, size=25, fill=COLORS["white"], weight=700, anchor="middle")
    text(x + 78, y + 69, title, size=42, weight=700)
    if status:
        text(x + w - 30, y + 64, status, size=22, fill=COLORS["muted"], weight=700, anchor="end")


def image_data(key: str) -> str:
    path = ROOT / SOURCES[key]
    image = Image.open(path).convert("RGB")
    src_w, src_h = image.size
    target = ImageOps.contain(image, (180, 320), method=Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (180, 320), "#f7faf8")
    canvas.paste(target, ((180 - target.width) // 2, (320 - target.height) // 2))
    import io
    out = io.BytesIO()
    canvas.save(out, format="JPEG", quality=76, optimize=True)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    embedded_sources[key] = {
        "path": SOURCES[key], "sha256": digest, "width": src_w, "height": src_h,
        "thumbnail_width": 180, "thumbnail_height": 320
    }
    return "data:image/jpeg;base64," + base64.b64encode(out.getvalue()).decode("ascii")


def thumbnail(key: str, x: int, y: int, w: int, h: int, alt: str) -> None:
    clip_id = f"clip_{key}_{x}_{y}"
    href = image_data(key)
    parts.append(f'<defs><clipPath id="{clip_id}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="18"/></clipPath></defs>')
    parts.append(f'<image x="{x}" y="{y}" width="{w}" height="{h}" xlink:href="{href}" preserveAspectRatio="xMidYMid meet" clip-path="url(#{clip_id})" aria-label="{esc(alt)}"/>')
    parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="18" fill="none" stroke="{COLORS["line"]}" stroke-width="2"/>')


def section_title(x: int, y: int, eyebrow: str, title: str, color: str) -> None:
    text(x, y, eyebrow, size=25, fill=color, weight=700)
    text(x, y + 55, title, size=37, weight=700)


def draw() -> None:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    parts.append(f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}" role="img" aria-labelledby="title desc">
<title id="title">渔见 · 拍照识鱼主链路 V1.0</title>
<desc id="desc">从 Empty Home 到 Capture、Processing、五类识别结果和技术异常，再到保存与 FishRecord 的横向业务总图。只有核验到持久化成功才创建鱼获。</desc>
<defs>
  <linearGradient id="background" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#edf5f1"/><stop offset="0.55" stop-color="#f7faf8"/><stop offset="1" stop-color="#f4f0e6"/></linearGradient>
  <marker id="arrow" markerWidth="16" markerHeight="16" refX="13" refY="7" orient="auto"><path d="M0,0 L14,7 L0,14 z" fill="{COLORS['teal']}"/></marker>
</defs>
<rect width="{WIDTH}" height="{HEIGHT}" fill="url(#background)"/>
''')
    # Header and legend.
    text(90, 108, "渔见 · 拍照识鱼主链路", size=78, weight=700)
    text(94, 166, "从用户发起到正式 FishRecord：权限、识别决策、恢复、保存、导航与数据生命周期", size=34, fill=COLORS["muted"])
    pill(3660, 76, 310, "V3.1 已冻结页面", COLORS["teal_pale"], COLORS["teal"], 24, 48)
    pill(3988, 76, 320, "V1 新增业务规则", COLORS["gold_pale"], "#876225", 24, 48)
    pill(4326, 76, 300, "系统自有界面", COLORS["blue_pale"], COLORS["blue"], 24, 48)
    pill(4644, 76, 360, "持久化前记录数不变", COLORS["green_pale"], COLORS["green"], 24, 48)
    text(5005, 166, "WORKING FLOW · 2026.10.15", size=22, fill=COLORS["muted"], weight=700, anchor="end")

    # Main route bar.
    route_centers = [340, 917, 1595, 3012, 4555]
    arrow(route_centers[0] + 65, 286, route_centers[1] - 72, "拍照 / 选图")
    arrow(route_centers[1] + 65, 286, route_centers[2] - 72, "新图片")
    arrow(route_centers[2] + 65, 286, route_centers[3] - 72, "真实 pipeline 输出")
    arrow(route_centers[3] + 70, 286, route_centers[4] - 82, "确认后保存")

    # Main sequence panels.
    panel(80, 340, 520, 1110, "01", "首页入口", "EMPTY HOME")
    thumbnail("home", 245, 435, 190, 338, "Empty Home V2.2 Frozen reference")
    pill(128, 803, 424, "0 条有效 FishRecord", COLORS["teal_pale"], COLORS["teal"], 27, 48)
    section_title(125, 914, "USER INTENT", "用户点击拍照", COLORS["teal"])
    text(125, 1045, ["进入现有 Capture / Identify", "开启一笔临时 Recognition Session", "登录状态不决定首页空 / 数据态"], size=27, line=44, fill=COLORS["muted"])
    rect(122, 1230, 434, 155, COLORS["green_pale"], radius=22)
    text(146, 1278, ["记录数保持 0", "没有保存意图，不创建 FishRecord"], size=27, weight=700, fill=COLORS["green"], line=42)

    panel(630, 340, 575, 1110, "02", "权限与 Capture", "CAMERA RUNTIME")
    pill(667, 428, 230, "相机路径", COLORS["teal_pale"], COLORS["teal"], 25)
    text(672, 518, ["用户发起后请求 CAMERA 权限", "授权且 Preview Ready → 可拍摄", "拒绝 / 永久拒绝 → 不启动相机"], size=27, line=42)
    pill(914, 428, 252, "相册路径", COLORS["blue_pale"], COLORS["blue"], 25)
    text(918, 518, ["系统 Photo Picker", "独立于 Camera 权限", "取消 → 回 Capture，不推理"], size=27, line=42, fill=COLORS["blue"])
    rect(663, 690, 505, 320, COLORS["gold_pale"], "#ead6ad", radius=24)
    pill(687, 710, 250, "首次引导 · 新 UI", "#f8e8c9", "#805c25", 23)
    text(689, 797, "仅首次相机 Preview Ready 后显示", size=27, weight=700)
    text(689, 844, ["开始拍照识鱼。尽量让鱼体轮廓清晰，", "手持鱼获也可以。拍摄后 AI 自动识别，", "结果可确认或修改。"], size=24, line=37, fill=COLORS["muted"])
    text(689, 974, "跳过 / 开始拍照：只关闭引导，不自动拍摄", size=22, fill="#805c25", weight=700)
    rect(663, 1030, 505, 168, COLORS["teal_pale"], radius=22)
    text(690, 1080, ["Capture Runtime", "CameraX 实时预览；相机和 Photo Picker", "保持系统自有，流程图不是 Capture UI。"], size=24, line=35, weight=700, fill=COLORS["teal"])
    pill(663, 1228, 505, "拍摄 / 选择 1 张并完成方向归一化", COLORS["blue_pale"], COLORS["blue"], 22, 60)
    text(687, 1372, "新 imageId + recognitionAttemptId", size=24, weight=700, fill=COLORS["muted"])

    panel(1235, 340, 720, 1110, "03", "Recognition Processing", "V3.1 FROZEN")
    thumbnail("processing", 1270, 435, 174, 310, "Processing Identifying frozen reference")
    text(1470, 468, ["原图保持主体", "只呈现真实", "识别事件", "不承诺 ETA"], size=27, line=41, weight=700, fill=COLORS["teal"])
    phases = [
        ("CAPTURED", "≥220ms", "接受归一化照片"),
        ("DETECTING", "≥320ms", "真实检测开始"),
        ("OUTLINE", "≥350ms", "真实可分类鱼体"),
        ("CLASSIFYING", "≥250ms", "真实分类事件"),
    ]
    y = 785
    for i, (name, duration, detail) in enumerate(phases):
        rect(1270, y, 650, 110, COLORS["teal_pale"] if i < 3 else COLORS["gold_pale"], radius=20)
        parts.append(f'<circle cx="1308" cy="{y + 37}" r="20" fill="{COLORS["teal"] if i < 3 else COLORS["gold"]}"/>')
        text(1308, y + 45, str(i + 1), size=23, fill=COLORS["white"], weight=700, anchor="middle")
        text(1342, y + 41, name, size=25, weight=700)
        text(1893, y + 41, duration, size=24, fill=COLORS["teal"], weight=700, anchor="end")
        text(1342, y + 81, detail, size=22, fill=COLORS["muted"])
        y += 124
    rect(1270, 1295, 650, 112, COLORS["red_pale"], radius=20)
    text(1295, 1340, ["Processing Back → 取消当前 Attempt", "丢弃迟到结果；不保存鱼获"], size=24, line=35, weight=700, fill=COLORS["red"])

    panel(1985, 340, 2055, 1110, "04", "识别结果决策 · 3+2 + Technical Failure", "FROZEN STATES + SEPARATE TECHNICAL")
    text(2020, 438, "由真实 pipeline 状态分流；不显示虚构鱼种、置信度数字或图像诊断。", size=25, fill=COLORS["muted"])
    result_cards = [
        ("high", "HIGH", "High · 默认推荐", ["Top-1 可修改", "用户明确保存后", "才创建记录"], COLORS["green_pale"], COLORS["green"]),
        ("medium", "MEDIUM", "Medium · 候选待确认", ["候选只是建议", "必须主动确认鱼种", "才能保存"], COLORS["gold_pale"], "#805c25"),
        ("low", "LOW", "Low · 手动选鱼", ["无已确认鱼种", "Metadata / Story 可暂存", "手动选择后才能保存"], COLORS["blue_pale"], COLORS["blue"]),
        ("no_fish", "NO FISH", "No Fish · RR04", ["没有找到可识别的鱼", "重新拍摄 / 相册", "不允许保存"], COLORS["red_pale"], COLORS["red"]),
        ("quality", "IMAGE QUALITY", "Image Quality · RR05", ["仅真实 INVALID", "且不是 NO_FISH", "重新拍摄 / 相册 · 不保存"], COLORS["red_pale"], COLORS["red"]),
        (None, "TECHNICAL", "Technical Failure · 独立", ["真实模型 / 运行时故障", "不归咎照片质量", "恢复拍摄 / 相册 · 不保存"], COLORS["purple_pale"], COLORS["purple"]),
    ]
    cell_w, cell_h, gap_x, gap_y = 648, 405, 24, 18
    start_x, start_y = 2012, 480
    for index, (img_key, label, title, lines, bg, ink) in enumerate(result_cards):
        col, row = index % 3, index // 3
        x, y = start_x + col * (cell_w + gap_x), start_y + row * (cell_h + gap_y)
        rect(x, y, cell_w, cell_h, bg, radius=24)
        pill(x + 18, y + 16, max(148, len(label) * 24 + 48), label, COLORS["white"], ink, 20, 37)
        if img_key:
            thumbnail(img_key, x + 20, y + 66, 150, 267, f"{title} Frozen UI thumbnail")
            tx = x + 190
        else:
            rect(x + 20, y + 66, 150, 267, COLORS["white"], radius=18)
            parts.append(f'<circle cx="{x + 95}" cy="{y + 159}" r="45" fill="{COLORS["purple_pale"]}" stroke="{ink}" stroke-width="4"/>')
            text(x + 95, y + 171, "!", size=64, fill=ink, weight=700, anchor="middle")
            text(x + 95, y + 291, "NO FROZEN UI", size=16, fill=COLORS["muted"], weight=700, anchor="middle")
            tx = x + 190
        text(tx, y + 110, title, size=29, weight=700, fill=ink)
        text(tx, y + 171, lines, size=25, line=42, fill=COLORS["ink"])
        text(tx, y + 365, "已冻结页面缩略参考" if img_key else "技术恢复不属于 3+2", size=19, fill=COLORS["muted"], weight=700)
    text(2023, 1409, "HIGH 可直接保存  ·  MEDIUM / LOW 必须先显式确认鱼种  ·  三类恢复异常一律不保存", size=24, fill=COLORS["teal"], weight=700)

    panel(4070, 340, 970, 1110, "05", "确认与保存", "ONE COMMIT POINT")
    rect(4105, 435, 900, 235, COLORS["gold_pale"], "#ead6ad", radius=24)
    text(4130, 480, "鱼种确认门", size=29, weight=700, fill="#805c25")
    text(4130, 534, ["High：Top-1 可改后保存", "Medium：主动确认候选 / 其他鱼种", "Low：手动选鱼；未选不能保存"], size=25, line=39)
    rect(4105, 693, 900, 235, COLORS["blue_pale"], radius=24)
    text(4130, 738, "保存状态", size=29, weight=700, fill=COLORS["blue"])
    text(4130, 792, ["明确 Save Catch / Continue Memory", "→ Saving → 核验持久化成功", "双击拦截 · clientRecordId 幂等"], size=25, line=39)
    text(4108, 992, "成功后分流（都只创建 1 条 FishRecord）", size=25, weight=700)
    rect(4105, 1016, 430, 205, COLORS["green_pale"], radius=22)
    text(4320, 1065, "保存本次鱼获", size=28, fill=COLORS["green"], weight=700, anchor="middle")
    text(4320, 1115, ["→ Home / NH02", "首条鱼获显示在首页"], size=23, line=34, anchor="middle")
    rect(4555, 1016, 450, 205, COLORS["teal_pale"], radius=22)
    text(4780, 1065, "继续记录记忆", size=28, fill=COLORS["teal"], weight=700, anchor="middle")
    text(4780, 1115, ["→ FishRecordDetail", "section=memory"], size=23, line=34, anchor="middle")
    rect(4105, 1247, 900, 160, COLORS["red_pale"], radius=22)
    text(4130, 1294, ["失败 / 超时 → 留在 Result，保留草稿与目标。", "超时先按同一 clientRecordId 查找；未核验不重复写入。"], size=23, line=37, weight=700, fill=COLORS["red"])

    # Decisions lane.
    rect(80, 1490, 4960, 330, "#e5efeb", radius=28)
    text(112, 1552, "泳道 2 · 关键决策和守卫", size=38, weight=700)
    decision_cards = [
        ("权限分支", ["相机许可只在用户发起时请求", "相册不依赖 Camera 权限"], COLORS["teal"]),
        ("首次引导", ["camera + Preview Ready + 未完成", "跳过 / 开始都不自动拍照"], "#805c25"),
        ("质量优先级", ["NO_FISH 优先于 IMAGE_QUALITY", "仅真实 INVALID 进入 RR05"], COLORS["red"]),
        ("鱼种确认", ["MEDIUM 候选必须主动确认", "LOW 必须手选，空种不能存"], COLORS["blue"]),
        ("保存准入", ["明确用户意图 + 持久化核验", "之前正式记录数始终不变"], COLORS["green"]),
    ]
    dw, dg = 940, 25
    for i, (title, lines, ink) in enumerate(decision_cards):
        x = 112 + i * (dw + dg)
        rect(x, 1600, dw, 185, COLORS["white"], COLORS["line"], radius=20)
        text(x + 22, 1644, title, size=28, weight=700, fill=ink)
        text(x + 22, 1691, lines, size=23, line=34, fill=COLORS["muted"])

    # Recovery lane.
    rect(80, 1850, 4960, 410, COLORS["white"], COLORS["line"], radius=28, stroke_width=3)
    text(112, 1912, "泳道 3 · 返回、恢复与异常闭环", size=38, weight=700)
    recovery = [
        ("Processing 返回", ["取消当前 attempt", "忽略迟到回调", "回到 Capture"], COLORS["red_pale"], COLORS["red"]),
        ("Result 返回", ["无编辑：回 Capture", "有改动：继续编辑 / 放弃", "放弃不保存"], COLORS["gold_pale"], "#805c25"),
        ("重新拍摄", ["先清除旧结果", "回到相机 Capture", "必须拿到新照片"], COLORS["blue_pale"], COLORS["blue"]),
        ("识别异常", ["No Fish / INVALID / 技术失败", "均可重拍或用相册", "不创建记录"], COLORS["red_pale"], COLORS["red"]),
        ("相册取消 / 权限拒绝", ["回原 Capture 上下文", "预览可恢复；允许再选", "不启动推理"], COLORS["teal_pale"], COLORS["teal"]),
        ("保存失败 / 超时", ["草稿、照片、目标保留", "同一 key 查询 / 幂等重试", "不盲目新增"], COLORS["purple_pale"], COLORS["purple"]),
    ]
    rw, rg, rh = 790, 23, 270
    for i, (title, lines, bg, ink) in enumerate(recovery):
        x = 108 + i * (rw + rg)
        rect(x, 1960, rw, rh, bg, radius=22)
        text(x + 24, 2010, title, size=27, weight=700, fill=ink)
        text(x + 24, 2063, lines, size=24, line=40, fill=COLORS["ink"])

    # Data lane.
    rect(80, 2290, 4960, 420, "#e8f0ed", radius=28)
    text(112, 2350, "泳道 4 · 数据生命周期（照片 ≠ 推理尝试 ≠ 鱼获记录）", size=38, weight=700)
    entities = [
        ("Recognition Session", ["一次用户旅程", "可包含多次重拍"], COLORS["teal_pale"], COLORS["teal"]),
        ("Attempt × N", ["每张新照片", "独立 imageId / attemptId"], COLORS["blue_pale"], COLORS["blue"]),
        ("Recognition Draft", ["结果 + 已确认鱼种", "metadata / story"], COLORS["gold_pale"], "#805c25"),
        ("Save Intent", ["目标 + 稳定 clientRecordId", "双击 / 重试共用此键"], COLORS["purple_pale"], COLORS["purple"]),
        ("Verified FishRecord", ["核验持久化成功后 +1", "成功结束识别 Session"], COLORS["green_pale"], COLORS["green"]),
    ]
    ex, ew, eg = 112, 920, 50
    for i, (title, lines, bg, ink) in enumerate(entities):
        x = ex + i * (ew + eg)
        rect(x, 2390, ew, 180, bg, radius=22)
        text(x + 25, 2443, title, size=27, weight=700, fill=ink)
        text(x + 25, 2494, lines, size=23, line=34, fill=COLORS["muted"])
        if i < len(entities) - 1:
            arrow(x + ew + 4, 2480, x + ew + eg - 4, "")
    text(112, 2633, "取消 / 放弃 / No Fish / Image Quality / Technical Failure / 明确失败 = 0 条新增记录", size=28, fill=COLORS["red"], weight=700)
    text(5000, 2633, "持久化前 FishRecord 数不变", size=28, fill=COLORS["green"], weight=700, anchor="end")

    # Footer.
    parts.append(f'<line x1="90" y1="2750" x2="5030" y2="2750" stroke="{COLORS["line"]}" stroke-width="3"/>')
    text(94, 2802, "既有 UI authority：Capture / Processing / Result V3.1；屏幕缩略图来自 Frozen PNG。首次引导与草稿确认是新增应用 UI，系统权限/Photo Picker 使用 OS UI。", size=22, fill=COLORS["muted"])
    text(5010, 2802, "SVG 可编辑 · PNG 5120×2880 · BUSINESS CONTRACT V1.0", size=22, fill=COLORS["teal"], weight=700, anchor="end")
    parts.append("</svg>")
    SVG_PATH.write_text("\n".join(parts), encoding="utf-8")

    # Render with Inkscape so the PNG comes from the same exact editable SVG.
    subprocess.run(["inkscape", str(SVG_PATH), "--export-type=png", f"--export-filename={PNG_PATH}", f"--export-width={WIDTH}", f"--export-height={HEIGHT}"], check=True)

    def sha(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    manifest = {
        "schema_version": "1.0",
        "asset_id": "recognition_main_journey_overview_v1",
        "status": "FROZEN_BUSINESS_OVERVIEW",
        "scope": "communication_flow_not_runtime_visual_authority",
        "source_svg": {"path": SVG_PATH.relative_to(ROOT).as_posix(), "sha256": sha(SVG_PATH), "width": WIDTH, "height": HEIGHT},
        "png": {"path": PNG_PATH.relative_to(ROOT).as_posix(), "sha256": sha(PNG_PATH), "width": WIDTH, "height": HEIGHT, "color_mode": "RGB"},
        "frozen_thumbnail_sources": embedded_sources,
        "new_visual_surfaces": [
            {"id":"capture_first_guide_overlay","status":"NEW_HIFI_REQUIRED_STAGE_2","is_runtime_authority":False},
            {"id":"dirty_draft_discard_confirmation","status":"NEW_HIFI_REQUIRED_STAGE_2","is_runtime_authority":False}
        ],
        "system_owned_surfaces": ["android_camera_permission_dialog", "android_photo_picker"],
        "frozen_assets_modified": False,
        "generated_by": "design/system_v3/00_Product_Master_Flow/tools/render_recognition_main_journey_overview.py"
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"svg":str(SVG_PATH),"png":str(PNG_PATH),"manifest":str(MANIFEST_PATH),"dimensions":[WIDTH,HEIGHT],"svg_sha256":sha(SVG_PATH),"png_sha256":sha(PNG_PATH),"png_bytes":PNG_PATH.stat().st_size},ensure_ascii=False))


if __name__ == "__main__":
    draw()
