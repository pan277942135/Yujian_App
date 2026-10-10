#!/usr/bin/env python3
"""Print source SHA, exact duplicate group and JPEG dimensions for repo media fixtures.
No synthetic/generated fish, no image mutation and no assertion about license.
"""
from __future__ import annotations
import hashlib,json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
FILES=[
"app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg",
"app/src/main/assets/home_normal/fish_record/sample_recent_catch_card_crop.jpg",
"app/src/androidTest/assets/recognition_real_catch_fixture.jpg",
"app/src/androidTest/assets/detector_case_b_night_flash.jpg",
"app/src/androidTest/assets/golden_yellow_catfish_224.jpg",
]
SOF={0xC0,0xC1,0xC2,0xC3,0xC5,0xC6,0xC7,0xC9,0xCA,0xCB,0xCD,0xCE,0xCF}
def size(b):
    if b[:2]!=b"\xff\xd8":return None
    i=2
    while i<len(b)-4:
        if b[i]!=255:i+=1;continue
        while i<len(b) and b[i]==255:i+=1
        if i>=len(b):break
        t=b[i];i+=1
        if t in (0xD8,0xD9):continue
        if t==0xDA:break
        if i+2>len(b):break
        n=int.from_bytes(b[i:i+2],"big")
        if n<2 or i+n>len(b):break
        if t in SOF:
            return (int.from_bytes(b[i+5:i+7],"big"),int.from_bytes(b[i+3:i+5],"big"))
        i+=n
    return None

records=[]
for p in FILES:
    path=ROOT/p
    if not path.exists():records.append(dict(path=p,error="MISSING"));continue
    b=path.read_bytes()
    w_h=size(b)
    sha=hashlib.sha256(b).hexdigest()
    rec={"path":p,"bytes":len(b),"sha256":sha,"dimensions_px":w_h,"aspect":round(w_h[0]/w_h[1],4) if w_h else None,"provenance":"NOT_VERIFIED"}
    records.append(rec)
groups={}
for rec in records:
    if rec.get("sha256"):groups.setdefault(rec["sha256"],[]).append(rec["path"])
for rec in records:
    rec["exact_duplicate_paths"]=groups.get(rec.get("sha256"),[]) if rec.get("sha256") else []
print("NORMAL_HOME_REAL_MEDIA_INVENTORY_BEGIN")
print(json.dumps({"unique_jpeg_sha":len(groups),"records":records},indent=2,ensure_ascii=False))
print("NORMAL_HOME_REAL_MEDIA_INVENTORY_END")
