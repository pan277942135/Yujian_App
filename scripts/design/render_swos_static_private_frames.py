#!/usr/bin/env python3
"""Render deterministic private SWOS review frames from the real source photo and frozen page assets.

Outputs are intentionally written outside the repository. This is a static renderer,
not a playable demo and not a production fish-mask implementation.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import re
import shutil
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[2]
SOURCE_SHA = "ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987"
SCALE = 3
W, H = 1080, 1920
FRAME_IDS = ["P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "R0", "R0.5", "R1", "R2", "R3", "R4", "R5"]


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def save_verified_png(image: Image.Image, path: Path) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    raw = buffer.getvalue()
    # Validate the complete encoded stream before exposing it to later contact-sheet reads.
    Image.open(io.BytesIO(raw)).verify()
    path.write_bytes(raw)
    return raw


def rgba(color, alpha=255):
    return (color[0], color[1], color[2], max(0, min(255, int(alpha))))


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--photo", required=True, type=Path)
    p.add_argument("--output-dir", required=True, type=Path)
    p.add_argument("--font", type=Path, help="Noto Sans SC TTF (used only to rasterize Chinese review labels)")
    return p.parse_args()


def choose_font(path: Path | None, size: int, weight="Regular"):
    if path and path.is_file():
        font = ImageFont.truetype(str(path), size=size)
        try:
            names = font.get_variation_names()
            if names:
                wanted = weight.encode("utf-8")
                name = next((n for n in names if n.decode("utf-8") == weight), None)
                if name:
                    font.set_variation_by_name(name.decode("utf-8"))
        except Exception:
            pass
        return font
    fallback = Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")
    if fallback.is_file():
        return ImageFont.truetype(str(fallback), size=size)
    raise SystemExit("A Chinese-capable font is required. Pass --font /path/to/NotoSansSC.ttf.")


def path_points(svg_path: str, segments=32):
    tokens = re.findall(r"[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?\d*)", svg_path)
    i = 0
    command = None
    current = (0.0, 0.0)
    start = (0.0, 0.0)
    points = []
    while i < len(tokens):
        if tokens[i].isalpha():
            command = tokens[i]
            i += 1
            if command in ("Z", "z"):
                points.append(start)
                current = start
            continue
        if command in ("M", "L"):
            x, y = float(tokens[i]), float(tokens[i + 1]); i += 2
            current = (x, y)
            if command == "M":
                start = current
                command = "L"
            points.append(current)
        elif command == "C":
            x0, y0 = current
            x1, y1, x2, y2, x3, y3 = map(float, tokens[i:i + 6]); i += 6
            for step in range(1, segments + 1):
                t = step / segments
                q = 1 - t
                x = q**3*x0 + 3*q*q*t*x1 + 3*q*t*t*x2 + t**3*x3
                y = q**3*y0 + 3*q*q*t*y1 + 3*q*t*t*y2 + t**3*y3
                points.append((x, y))
            current = (x3, y3)
        else:
            raise ValueError(f"Unsupported SVG command {command} in source contour")
    return points


def scale_rect(rect):
    return {key: float(value) * SCALE for key, value in rect.items() if key in ("x", "y", "width", "height")}


def photo_fit(photo: Image.Image, rect, mode="contain"):
    rect = scale_rect(rect)
    rw, rh = int(round(rect["width"])), int(round(rect["height"]))
    fitted = ImageOps.contain(photo, (rw, rh), method=Image.Resampling.LANCZOS) if mode == "contain" else ImageOps.fit(photo, (rw, rh), method=Image.Resampling.LANCZOS)
    x = int(round(rect["x"] + (rw - fitted.width) / 2))
    y = int(round(rect["y"] + (rh - fitted.height) / 2))
    return fitted, x, y


def rounded_panel(base: Image.Image, box, radius, fill, outline=None, outline_width=0):
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=outline_width)
    return Image.alpha_composite(base.convert("RGBA"), layer)


def ambient_overlay(strength: int, fish_contour_points) -> Image.Image:
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if strength <= 0:
        return overlay
    s = SCALE
    f = strength / 100
    # Separate, irregular caustic patches rather than connected ribbons. They
    # occupy only the traced open-water/reflection pockets in this fixture.
    shapes = [
        ([(1,302),(5,295),(10,297),(13,291),(18,294),(21,300),(18,305),(13,307),(9,312),(4,309)], (255,235,190), .94),
        ([(25,291),(30,285),(36,287),(40,292),(47,291),(51,296),(47,301),(40,300),(35,304),(29,300)], (255,246,218), .84),
        ([(5,323),(10,318),(15,321),(18,317),(23,321),(25,327),(21,331),(15,329),(11,334),(6,330)], (178,213,220), .76),
        ([(31,313),(36,308),(42,310),(46,307),(52,312),(54,318),(49,321),(43,319),(39,324),(34,320)], (255,231,180), .73),
        ([(0,343),(4,338),(9,340),(12,336),(16,342),(14,347),(10,350),(5,347)], (242,220,174), .62),
        ([(55,276),(60,271),(65,273),(69,270),(74,275),(72,280),(66,281),(62,285),(57,282)], (198,220,217), .56),
        ([(19,340),(23,337),(28,339),(30,344),(27,348),(22,346)], (255,240,209), .50)
    ]
    for points, color, opacity in shapes:
        mask = Image.new("L", (W, H), 0)
        d = ImageDraw.Draw(mask)
        d.polygon([(int(x*s), int(y*s)) for x,y in points], fill=int(255*f*opacity))
        mask = mask.filter(ImageFilter.GaussianBlur(12 + 7*(1-f)))
        color_layer = Image.new("RGBA", (W,H), rgba(color,255))
        color_layer.putalpha(mask)
        overlay = Image.alpha_composite(overlay, color_layer)
    # One muted reflection feather near the lower edge gives spatial direction
    # without outlining the person, grass, or photo container.
    reflection = Image.new("L", (W,H), 0)
    d = ImageDraw.Draw(reflection)
    d.polygon([(0,423*s),(5,419*s),(9,423*s),(12,431*s),(9,439*s),(4,442*s),(0,438*s)], fill=int(88*f))
    reflection = reflection.filter(ImageFilter.GaussianBlur(4*s))
    reflection_layer = Image.new("RGBA", (W,H), (248,224,177,0))
    reflection_layer.putalpha(reflection)
    overlay = Image.alpha_composite(overlay, reflection_layer)
    # Fixture-specific review mask: light stays in open surroundings and avoids
    # the manual fish contour and visually traced person. It is not runtime
    # segmentation or a production foreground mask.
    safe = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(safe)
    d.polygon([(0, 235*s), (52*s, 238*s), (76*s, 249*s), (94*s, 270*s), (88*s, 300*s), (72*s, 329*s), (50*s, 350*s), (0, 355*s)], fill=255)
    d.polygon([(0, 400*s), (14*s, 402*s), (26*s, 419*s), (25*s, 442*s), (15*s, 463*s), (0, 465*s)], fill=255)
    photo_clip = Image.new("L", (W, H), 0)
    ImageDraw.Draw(photo_clip).rectangle((0, 80*s, 360*s, 560*s), fill=255)
    fish_mask = Image.new("L", (W, H), 0)
    fish_points = [((x * 360 / 1152) * s, (80 + y * 480 / 1536) * s) for x,y in fish_contour_points]
    ImageDraw.Draw(fish_mask).polygon(fish_points, fill=255)
    person_mask = Image.new("L", (W, H), 0)
    person_dp = [(154,170),(184,158),(226,167),(258,192),(279,228),(302,265),(325,324),(328,424),(314,510),(303,560),(128,560),(135,503),(151,434),(158,372),(147,309),(145,244)]
    ImageDraw.Draw(person_mask).polygon([(x*s,y*s) for x,y in person_dp],fill=255)
    protected = ImageChops.lighter(fish_mask,person_mask).filter(ImageFilter.MaxFilter(41))
    safe = ImageChops.multiply(safe, photo_clip)
    safe = ImageChops.multiply(safe, ImageChops.invert(protected))
    alpha = ImageChops.multiply(overlay.getchannel("A"), safe)
    overlay.putalpha(alpha)
    return overlay


def fish_gold_overlay(frame, points, transition, photo_sha):
    overlay = Image.new("RGBA", (W,H), (0,0,0,0))
    strength = int(frame.get("fish_gold_strength", 0))
    if strength <= 0 or photo_sha != transition["sample_source"]["source_photo_sha256"]:
        return overlay
    rects = photo_rects(frame, transition)
    fore = scale_rect(rects["foreground"])
    src_w = transition["sample_source"]["source_width"]
    src_h = transition["sample_source"]["source_height"]
    scale = min(fore["width"] / src_w, fore["height"] / src_h)
    xoff = fore["x"] + (fore["width"] - src_w*scale)/2
    yoff = fore["y"] + (fore["height"] - src_h*scale)/2
    path = [(xoff + x*scale, yoff + y*scale) for x,y in points]
    visual_strength = min(.82, .82 * strength / 100)
    # Shared geometry: broad halo, tight soft glow, and crisp core all use the same path.
    halo = Image.new("RGBA", (W,H), (0,0,0,0))
    ImageDraw.Draw(halo).line(path, fill=(250,204,92,int(255*.18*visual_strength)), width=max(1,int(45*scale)), joint="curve")
    halo = halo.filter(ImageFilter.GaussianBlur(18*scale))
    soft = Image.new("RGBA", (W,H), (0,0,0,0))
    ImageDraw.Draw(soft).line(path, fill=(255,224,154,int(255*.40*visual_strength)), width=max(1,int(20*scale)), joint="curve")
    soft = soft.filter(ImageFilter.GaussianBlur(8*scale))
    core = Image.new("RGBA", (W,H), (0,0,0,0))
    ImageDraw.Draw(core).line(path, fill=(255,212,111,int(255*.88*visual_strength)), width=max(2,int(7*scale)), joint="curve")
    return Image.alpha_composite(Image.alpha_composite(halo, soft), core)


def photo_rects(frame, transition):
    if frame["track"] == "PROCESSING" or frame["id"] == "R0":
        return {"outer": transition["samples"]["R0"]["outer_rect"], "foreground": transition["processing_photo_content_rect"]}
    if frame["id"] == "R0.5":
        return {"outer": transition["samples"]["R0.5"]["outer_rect"], "foreground": transition["samples"]["R0.5"]["foreground_rect"]}
    return {"outer": transition["samples"]["R1"]["outer_rect"], "foreground": transition["samples"]["R1"]["foreground_rect"]}


def add_photo_layer(base, photo, frame, transition):
    rects = photo_rects(frame, transition)
    outer = scale_rect(rects["outer"])
    fg_rect = rects["foreground"]
    box = (int(outer["x"]), int(outer["y"]), int(outer["x"]+outer["width"]), int(outer["y"]+outer["height"]))
    ow, oh = box[2]-box[0], box[3]-box[1]
    if frame["id"] != "R0":
        blurred = ImageOps.fit(photo, (max(1,ow), max(1,oh)), method=Image.Resampling.LANCZOS, centering=(.5,.5)).filter(ImageFilter.GaussianBlur(22)).convert("RGBA")
        # Keep the same source image as the Hero fallback background; do not synthesize pixels.
        if frame["track"] == "RR09":
            blurred = ImageEnhanceBrightness(blurred, .72)
        mask = Image.new("L", base.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(box, radius=(46 if frame["track"] == "RR09" else 0), fill=255)
        layer = Image.new("RGBA", base.size, (0,0,0,0))
        layer.paste(blurred, (box[0],box[1]), mask.crop(box))
        base = Image.alpha_composite(base.convert("RGBA"), layer)
    fitted, x, y = photo_fit(photo, fg_rect, "contain")
    photo_layer = Image.new("RGBA", base.size, (0,0,0,0))
    photo_layer.alpha_composite(fitted.convert("RGBA"), (x,y))
    base = Image.alpha_composite(base, photo_layer)
    if frame["track"] == "RR09" and frame["id"] != "R0":
        border = Image.new("RGBA", base.size, (0,0,0,0))
        ImageDraw.Draw(border).rounded_rectangle(box, radius=42, outline=(255,255,255,228), width=5)
        base = Image.alpha_composite(base, border)
    return base


def ImageEnhanceBrightness(image, factor):
    rgb = image.convert("RGB")
    from PIL import ImageEnhance
    return ImageEnhance.Brightness(rgb).enhance(factor).convert("RGBA")


def processing_screen(frame, photo, transition, path_points_data, photo_sha, fonts, frozen_status):
    # Neutral framing around a complete, orientation-normalized original photograph.
    bg = Image.new("RGBA", (W,H), (27,45,53,255))
    gradient = Image.new("RGBA", (W,H), (0,0,0,0))
    gd = ImageDraw.Draw(gradient)
    gd.rectangle((0,0,W,H), fill=(49,70,76,255))
    for y in range(H):
        a = int(72*y/H)
        ImageDraw.Draw(gradient).line((0,y,W,y), fill=(18,35,41,a))
    bg = Image.alpha_composite(bg, gradient)
    # Photo container is 360×480dp; contain preserves every source pixel and its aspect ratio.
    rect = transition["processing_photo_content_rect"]
    fitted, x, y = photo_fit(photo, rect, "contain")
    bg.alpha_composite(fitted.convert("RGBA"), (x,y))
    # Add subtle SWOS area caustics over the real photo, never overpaint its subject.
    bg = Image.alpha_composite(bg, ambient_overlay(frame["ambient_water_strength"], path_points_data))
    bg = Image.alpha_composite(bg, fish_gold_overlay(frame, path_points_data, transition, photo_sha))
    draw = ImageDraw.Draw(bg)
    draw.text((58,48), "‹", font=fonts["nav"], fill=(255,255,255,242), stroke_width=1, stroke_fill=(26,44,51,180))
    draw.text((123,63), "渔见 AI", font=fonts["small_bold"], fill=(255,255,255,242), stroke_width=1, stroke_fill=(26,44,51,170))
    draw.text((123,102), "PROCESSING", font=fonts["tiny"], fill=(239,242,236,206))
    # Existing Frozen status-card geometry and wording are reused by state; no fabricated model output.
    title, subtitle = frozen_status
    panel_box=(45,1350,1035,1645)
    panel = Image.new("RGBA", (W,H), (0,0,0,0))
    pd=ImageDraw.Draw(panel)
    pd.rounded_rectangle(panel_box, radius=54, fill=(14,31,40,229), outline=(198,220,214,170), width=3)
    pd.rounded_rectangle((54,1359,1026,1636), radius=47, outline=(118,170,172,96), width=2)
    bg=Image.alpha_composite(bg,panel)
    # One restrained static progress ring; it is a status affordance, not an optical scan.
    ring = Image.new("RGBA", (W,H), (0,0,0,0))
    rd=ImageDraw.Draw(ring)
    rd.arc((116,1415,250,1549), start=28, end=307, fill=(255,222,153,230), width=13)
    rd.arc((116,1415,250,1549), start=307, end=382, fill=(151,192,207,210), width=13)
    bg=Image.alpha_composite(bg,ring)
    draw=ImageDraw.Draw(bg)
    draw.text((302,1409), title, font=fonts["status"], fill=(246,249,244,255))
    draw.text((302,1483), subtitle, font=fonts["subtitle"], fill=(184,202,203,255))
    draw.rounded_rectangle((55,1837,311,1884),radius=13,fill=(16,30,34,178))
    draw.text((72,1846),"静态视觉样例 · 不代表模型事件",font=fonts["tiny"],fill=(255,255,255,225))
    return bg


def result_screen(frame, photo, transition, contour_points, photo_sha, frozen_result, fonts):
    result = frozen_result.copy().convert("RGBA")
    ui_opacity = .22 if frame["id"] == "R0" else .58 if frame["id"] == "R0.5" else 1.0
    if ui_opacity < 1:
        pale = Image.new("RGBA", (W,H), (238,244,240,255))
        result = Image.blend(pale, result, ui_opacity)
    # Cover the frozen sample name so it cannot be read as this photo's model result.
    title_box=(63,820,1017,966)
    patch = Image.new("RGBA", (W,H), (0,0,0,0))
    pd=ImageDraw.Draw(patch)
    pd.rounded_rectangle(title_box, radius=22, fill=(247,250,247,255), outline=(239,246,243,255), width=2)
    if frame["species_name_reveal"] == "CHINESE_ONLY_PLACEHOLDER":
        pd.text((82,838), "鱼种名称", font=fonts["result_title"], fill=(26,59,99,255))
    result=Image.alpha_composite(result,patch)
    # The source fixture has no verified fishing-location metadata. Reuse the
    # existing field's own placeholder pixels instead of presenting 千岛湖 as fact.
    location_placeholder=frozen_result.crop((812,983,1005,1075)).resize((193,92),Image.Resampling.LANCZOS)
    location_placeholder.putalpha(Image.new("L",location_placeholder.size,int(255*ui_opacity)))
    result.alpha_composite(location_placeholder,(812,1172))
    if frame["id"] == "R0":
        # The frozen Result screenshot has its own sample Hero photo. Replace that
        # underlay with a same-source blur so no second fish/photo peeks above the
        # Processing image during the first presented Result frame.
        hero=scale_rect(transition["result_hero_rect"])
        hero_box=(int(hero["x"]),int(hero["y"]),int(hero["x"]+hero["width"]),int(hero["y"]+hero["height"]))
        blurred=ImageOps.fit(photo,(hero_box[2]-hero_box[0],hero_box[3]-hero_box[1]),method=Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(22)).convert("RGBA")
        mask=Image.new("L",result.size,0)
        ImageDraw.Draw(mask).rounded_rectangle(hero_box,radius=48,fill=255)
        underlay=Image.new("RGBA",result.size,(0,0,0,0))
        underlay.paste(blurred,(hero_box[0],hero_box[1]),mask.crop(hero_box))
        result=Image.alpha_composite(result,underlay)
    result=add_photo_layer(result,photo,frame,transition)
    # The same manual contour follows the exact same foreground transform as the
    # photo at R0/R0.5/R1. It is clearly labelled as review-only in the workspace.
    if frame["fish_gold_strength"] > 0:
        result=Image.alpha_composite(result,fish_gold_overlay(frame,contour_points,transition,photo_sha))
    if frame["id"] == "R2" and photo_sha == transition["sample_source"]["source_photo_sha256"]:
        rects=photo_rects(frame,transition)
        fore=scale_rect(rects["foreground"])
        scale=min(fore["width"]/transition["sample_source"]["source_width"],fore["height"]/transition["sample_source"]["source_height"])
        sx=fore["x"]+fore["width"]/2
        sy=fore["y"]+1090*scale
        pts=[]
        for i in range(100):
            t=i/99; q=1-t
            # SVG cubic curve from the same foreground transform to the existing title bounds.
            p0=(sx,sy); p1=(sx+50,sy+88); p2=(650,735); p3=(500,866)
            x=q**3*p0[0]+3*q*q*t*p1[0]+3*q*t*t*p2[0]+t**3*p3[0]
            y=q**3*p0[1]+3*q*q*t*p1[1]+3*q*t*t*p2[1]+t**3*p3[1]
            pts.append((x,y))
        transfer=Image.new("RGBA",(W,H),(0,0,0,0))
        ImageDraw.Draw(transfer).line(pts,fill=(255,210,112,218),width=8,joint="curve")
        transfer=transfer.filter(ImageFilter.GaussianBlur(2.5))
        result=Image.alpha_composite(result,transfer)
    if frame["id"] == "R3":
        glyphs=Image.new("RGBA",(W,H),(0,0,0,0)); d=ImageDraw.Draw(glyphs)
        for p in [((451,880),(475,904)),((511,898),(530,870)),((564,877),(586,899)),((629,897),(650,873))]:
            d.line(p,fill=(223,180,75,225),width=9)
        result=Image.alpha_composite(result,glyphs.filter(ImageFilter.GaussianBlur(1.8)))
    return result


def main():
    args=parse_args()
    photo_path=args.photo.resolve(); out=args.output_dir.resolve(); out.mkdir(parents=True,exist_ok=True)
    raw=photo_path.read_bytes(); photo_sha=sha(raw)
    if photo_sha != SOURCE_SHA: raise SystemExit(f"Fixture SHA mismatch: {photo_sha}")
    photo=ImageOps.exif_transpose(Image.open(photo_path)).convert("RGB")
    if photo.size != (1152,1536): raise SystemExit(f"Fixture dimensions changed: {photo.size}")
    frame_contract=json.loads((ROOT/"design/pages/recognition/processing/optics/SWOS_15_Frame_Keyframe_Contract_V1_1.json").read_text())
    transition=json.loads((ROOT/"design/pages/recognition/continuity/SWOS_Shared_Photo_Transition_Contract_V1.json").read_text())
    frames=frame_contract["keyframes"]
    path_points_data=path_points(transition["fish_geometry"]["contour_svg_path"])
    font_path=args.font.resolve() if args.font else None
    fonts={
        "nav":choose_font(font_path,88,"Regular"),
        "small_bold":choose_font(font_path,39,"Bold"),
        "tiny":choose_font(font_path,27,"Regular"),
        "status":choose_font(font_path,51,"Bold"),
        "subtitle":choose_font(font_path,39,"Regular"),
        "result_title":choose_font(font_path,78,"Bold")
    }
    frozen_result=Image.open(ROOT/"design/pages/recognition/design/05_Result_High_Frozen.png").convert("RGB").resize((W,H),Image.Resampling.LANCZOS).convert("RGBA")
    frozen_statuses={
      "understanding":("正在理解照片","查找鱼获线索"),
      "located":("已定位到鱼体","正在分析这次鱼获"),
      "classifying":("正在认识这条鱼","分析鱼体特征")
    }
    outputs={}
    for frame in frames:
        if frame["track"] == "PROCESSING":
            status="understanding" if frame["global_nominal_ms"] < 1200 else "located" if frame["global_nominal_ms"] < 2050 else "classifying"
            image=processing_screen(frame,photo,transition,path_points_data,photo_sha,fonts,frozen_statuses[status])
        else:
            image=result_screen(frame,photo,transition,path_points_data,photo_sha,frozen_result,fonts)
        name=frame["id"].replace(".","_")+".png"
        target=out/name
        raw = save_verified_png(image.convert("RGB"), target)
        outputs[frame["id"]]={"filename":name,"width":W,"height":H,"sha256":sha(raw),"track":frame["track"],"global_nominal_ms":frame["global_nominal_ms"],"label_zh":frame["label_zh"]}

    # One original/composite comparison pair for source and optical layers.
    original_path=out/"SWOS_Source_Photo_Original.png"
    photo.save(original_path)
    shutil.copyfile(photo_path,out/"208758_Source_Original.jpg")
    p4=Image.open(out/"P4.png").convert("RGB")
    source_scaled=ImageOps.contain(photo,(540,720),method=Image.Resampling.LANCZOS)
    original_panel=Image.new("RGB",(1080,1920),"#f0f4f2")
    original_panel.paste(source_scaled,((1080-source_scaled.width)//2,96))
    ImageDraw.Draw(original_panel).text((40,28),"ORIGINAL PHOTO · SOURCE SHA MATCHED",font=choose_font(font_path,30,"Bold"),fill="#315755")
    save_verified_png(original_panel,out/"SWOS_Source_Photo_Original_Preview.png")
    shutil.copyfile(ROOT/"design/pages/recognition/design/05_Result_High_Frozen.png",out/"Frozen_Result_High_Source.png")

    # Two-row 8+7 contact sheet at 75% of individual exports; each full export remains available.
    tile_w,tile_h,gap,label_h=810,1440,20,42
    sheet=Image.new("RGB",(8*tile_w+7*gap,2*(tile_h+label_h)+3*gap),"#e7eeeb")
    draw=ImageDraw.Draw(sheet); label_font=choose_font(font_path,25,"Bold")
    for i,fid in enumerate(FRAME_IDS):
        row,col=divmod(i,8); x=col*(tile_w+gap); y=gap+row*(tile_h+label_h+gap)
        draw.rounded_rectangle((x,y,x+tile_w,y+label_h-4),radius=8,fill="#254d4e")
        frame_record=next(frame for frame in frames if frame["id"]==fid)
        draw.text((x+15,y+7),f"{fid} · {frame_record['global_nominal_ms']}ms",font=label_font,fill="white")
        screen=Image.open(out/(fid.replace(".","_")+".png")).convert("RGB").resize((tile_w,tile_h),Image.Resampling.LANCZOS)
        sheet.paste(screen,(x,y+label_h))
    contact=out/"SWOS_15_Frame_Contact_Sheet.png"; save_verified_png(sheet,contact)

    geometry_ids=["P7","R0","R0.5","R1"]
    geometry=Image.new("RGB",(4*1080+3*18,1920+48),"#e7eeeb"); gd=ImageDraw.Draw(geometry)
    for i,fid in enumerate(geometry_ids):
        x=i*(1080+18)
        gd.rounded_rectangle((x,0,x+1080,44),radius=8,fill="#254d4e")
        frame_record=next(frame for frame in frames if frame["id"]==fid)
        gd.text((x+14,6),f"{fid} · {frame_record['global_nominal_ms']}ms",font=choose_font(font_path,27,"Bold"),fill="white")
        geometry.paste(Image.open(out/(fid.replace(".","_")+".png")).convert("RGB"),(x,48))
    geometry_path=out/"SWOS_P7_R0_R0_5_R1_Geometry_Continuity.png"; save_verified_png(geometry,geometry_path)

    compare=Image.new("RGB",(2*540+24,960+70),"#e7eeeb"); cd=ImageDraw.Draw(compare)
    for i,(fid,label) in enumerate([("P0","P0 · 原始照片 / 无光效"),("P4","P4 · SWOS 光效合成")]):
        x=12+i*(540+12); cd.rounded_rectangle((x,10,x+540,66),radius=8,fill="#254d4e")
        cd.text((x+14,22),label,font=choose_font(font_path,25,"Bold"),fill="white")
        compare.paste(Image.open(out/(fid+".png")).convert("RGB").resize((540,960),Image.Resampling.LANCZOS),(x,70))
    compare_path=out/"SWOS_P0_vs_P4_Original_Composite.png"; save_verified_png(compare,compare_path)

    font_sha=sha(font_path.read_bytes()) if font_path else None
    frozen_result_path=ROOT/"design/pages/recognition/design/05_Result_High_Frozen.png"
    frozen_comparison=Image.new("RGB",(2*540+24,960+70),"#e7eeeb"); fd=ImageDraw.Draw(frozen_comparison)
    fd.rounded_rectangle((12,10,552,66),radius=8,fill="#254d4e"); fd.text((26,22),"Frozen HIGH · source",font=choose_font(font_path,25,"Bold"),fill="white")
    fd.rounded_rectangle((564,10,1104,66),radius=8,fill="#254d4e"); fd.text((578,22),"R5 · SWOS static result",font=choose_font(font_path,25,"Bold"),fill="white")
    frozen_comparison.paste(Image.open(frozen_result_path).convert("RGB").resize((540,960),Image.Resampling.LANCZOS),(12,70))
    frozen_comparison.paste(Image.open(out/"R5.png").convert("RGB").resize((540,960),Image.Resampling.LANCZOS),(564,70))
    frozen_comparison_path=out/"R5_vs_Frozen_Result_High_Comparison.png"; save_verified_png(frozen_comparison,frozen_comparison_path)
    report={
      "artifact":"SWOS_STATIC_VISUAL_V1_1_PRIVATE_REVIEW_PACK",
      "created_at_utc":datetime.now(timezone.utc).isoformat(),
      "renderer":"Pillow deterministic static compositor; no image-generation model",
      "photo":{"filename":photo_path.name,"sha256":photo_sha,"dimensions":[1152,1536],"orientation":1,"publication_permission":"NOT_CONFIRMED_PRIVATE_REVIEW_ONLY"},
      "font":{"family":"Noto Sans SC","source":"google/fonts@45e3d5269e4a17d823f6d84470bc084dc5ae46f6/ofl/notosanssc/NotoSansSC[wght].ttf","license":"OFL-1.1","sha256":font_sha,"font_file_in_pack":False},
      "frame_count":len(outputs),"frames":outputs,
      "contact_sheet":{"filename":contact.name,"dimensions":list(sheet.size),"sha256":sha(contact.read_bytes())},
      "geometry_comparison":{"filename":geometry_path.name,"dimensions":list(geometry.size),"sha256":sha(geometry_path.read_bytes()),"frames":geometry_ids},
      "original_vs_composite":{"filename":compare_path.name,"dimensions":list(compare.size),"sha256":sha(compare_path.read_bytes()),"frames":["P0","P4"]},
      "result_frozen_comparison":{"filename":frozen_comparison_path.name,"dimensions":list(frozen_comparison.size),"sha256":sha(frozen_comparison_path.read_bytes()),"frozen_source":"design/pages/recognition/design/05_Result_High_Frozen.png","static_frame":"R5"},
      "processing_frozen_references":["design/pages/recognition/design/02_AI_Understanding_Frozen.png","design/pages/recognition/design/03_Fish_Highlight_Frozen.png","design/pages/recognition/design/04_Fish_Identifying_Frozen.png"],
      "result_frozen_source":"design/pages/recognition/design/05_Result_High_Frozen.png",
      "fish_geometry":"MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK",
      "effect_strength_alpha_mapping":"ILLUSTRATIVE_STATIC_REVIEW_ONLY_NOT_PRODUCTION_ALPHA",
      "playback":False,"android_runtime":"NOT_RUN","public_repository_included":False
    }
    manifest=out/"SWOS_Static_Visual_Private_Asset_Manifest.json"; manifest.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (out/"SWOS_Static_Visual_Private_Review_Report.md").write_text(
      "# SWOS Static Visual V1.1 · Private Review\n\n"
      f"- 15 individual PNG exports: 1080×1920.\n- Contact sheet: `{contact.name}` ({sheet.width}×{sheet.height}).\n"
      f"- Geometry comparison: `{geometry_path.name}` ({geometry.width}×{geometry.height}).\n- Source SHA-256: `{photo_sha}`.\n"
      f"- Original/composite comparison: `{compare_path.name}`.\n- R5/Frozen HIGH comparison: `{frozen_comparison_path.name}`.\n"
      "- Fish outline: manual reference only, not a production mask.\n- Photo permission: not confirmed; keep exports private.\n"
      "- Renderer: deterministic Pillow composition; not browser runtime evidence or a playable animation.\n- Android Runtime: NOT_RUN.\n",
      encoding="utf-8")
    archive=out/"SWOS_Static_Visual_V1_1_Private_Review_Pack.zip"
    with zipfile.ZipFile(archive,"w",zipfile.ZIP_DEFLATED,compresslevel=7) as z:
        for path in sorted(out.iterdir()):
            if path.is_file() and path != archive: z.write(path,path.name)
    print(f"frames={len(outputs)} contact={sheet.size} geometry={geometry.size}")
    print(f"photo_sha256={photo_sha} archive_sha256={sha(archive.read_bytes())}")


if __name__=="__main__":
    main()
