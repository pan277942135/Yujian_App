#!/usr/bin/env python3
"""Audit visible Normal Home CTA and camera pixels from SHA-verified Frozen PNG.
Read-only, stdlib-only; outputs candidate optical masks, never touch regions.
"""
import hashlib
import json
import struct
import zlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
PNG=ROOT/"design/system/core_visual_v1/reference/normal_home_v1.png"
EXPECTED="6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"

def rgb_png(path):
    data=path.read_bytes()
    if hashlib.sha256(data).hexdigest()!=EXPECTED: raise RuntimeError("FROZEN_SHA_MISMATCH")
    if data[:8]!=b"\x89PNG\r\n\x1a\n": raise RuntimeError("PNG_SIGNATURE")
    pos=8
    payload=[]
    while pos<len(data):
        n=int.from_bytes(data[pos:pos+4],"big"); typ=data[pos+4:pos+8]
        val=data[pos+8:pos+8+n];pos+=n+12
        if typ==b"IHDR":w,h,depth,ctype,_,_,interlace=struct.unpack(">IIBBBBB",val)
        elif typ==b"IDAT":payload.append(val)
        elif typ==b"IEND":break
    if (w,h)!=(1080,1920) or depth!=8 or ctype not in (2,6) or interlace: raise RuntimeError(f"UNSUPPORTED_PNG:{w}x{h}/{depth}/{ctype}/{interlace}")
    bpp=3 if ctype==2 else 4
    width_bytes=w*bpp
    raw=zlib.decompress(b"".join(payload))
    prior=bytearray(width_bytes);pos=0;pixels=[]
    for y in range(h):
        filt=raw[pos];pos+=1
        scan=bytearray(raw[pos:pos+width_bytes]);pos+=width_bytes
        for i in range(width_bytes):
            a=scan[i-bpp] if i>=bpp else 0
            b=prior[i]; c=prior[i-bpp] if i>=bpp else 0
            if filt==1:scan[i]=(scan[i]+a)&255
            elif filt==2:scan[i]=(scan[i]+b)&255
            elif filt==3:scan[i]=(scan[i]+((a+b)//2))&255
            elif filt==4:
                p=a+b-c;pa=abs(p-a);pb=abs(p-b);pc=abs(p-c)
                pred=a if pa<=pb and pa<=pc else (b if pb<=pc else c)
                scan[i]=(scan[i]+pred)&255
            elif filt!=0: raise RuntimeError(f"UNSUPPORTED_FILTER_{filt}")
        pixels.append(scan)
        prior=scan
    return pixels,bpp

def get(row,x,bpp):
    offset=x*bpp
    return row[offset],row[offset+1],row[offset+2]

def metrics(pix,bpp,box,label):
    x0,y0,x1,y1=box
    print(f"REGION {label} bbox={box}")
    for level in (165,190,210,225,240):
        stats=[]
        for y in range(y0,y1):
            count=0
            for x in range(x0,x1):
                r,g,b=get(pix[y],x,bpp)
                if min(r,g,b)>=level and max(r,g,b)-min(r,g,b)<52:
                    count+=1
            stats.append(count)
        ys=[y0+i for i,c in enumerate(stats) if c>=max(3,int((x1-x0)*.012))]
        if ys: print(f"  nearwhite_{level}: y={min(ys)}..{max(ys)}, maxRow={max(stats)}, nonemptyRows={len(ys)}")
        else: print(f"  nearwhite_{level}: no significant rows")
    for y in range(y0,y1,5):
        count=0;peak=0;gold=0;white=0
        for x in range(x0,x1):
            r,g,b=get(pix[y],x,bpp)
            peak=max(peak,r+g+b)
            if min(r,g,b)>=215 and max(r,g,b)-min(r,g,b)<50:white+=1
            if r>g+15 and g>b+5 and r>110: gold+=1
        if white>=3 or gold>=6:print(f"  ROW y={y} white>=215:{white} gold:{gold} maxRGBsum:{peak}")
    # terminal monochrome display of brightness within ROI, robust against no image downloads
    nx=48;ny=18
    print("  ASCII: white mask overview (x increases right, y down):")
    for i in range(ny):
        yy=y0+int((i+.5)*(y1-y0)/ny)
        line=[]
        for j in range(nx):
            xx=x0+int((j+.5)*(x1-x0)/nx)
            r,g,b=get(pix[yy],xx,bpp)
            c="#" if min(r,g,b)>=222 and max(r,g,b)-min(r,g,b)<50 else ("+" if min(r,g,b)>=175 and max(r,g,b)-min(r,g,b)<50 else ".")
            line.append(c)
        print("   ","".join(line))

def main():
    pix,bpp=rgb_png(PNG)
    print("NORMAL_HOME_FROZEN_SHA256",EXPECTED,"dimensions=1080x1920","RGBchannels",bpp)
    metrics(pix,bpp,(400,1492,680,1571),"CTA")
    metrics(pix,bpp,(400,1550,680,1820),"CAMERA")
    # Isolate camera pixels below CTA and near center; summarize exact threshold candidates.
    print("CAMERA_EDGE_SCAN x=430..650, y=1560..1810")
    for threshold in (12, 18, 25):
        gold_rows=[]; white_rows=[]
        for y in range(1560,1810):
            gold=white=0
            for x in range(430,651):
                r,g,b=get(pix[y],x,bpp)
                if r-g>=threshold and g-b>=5 and r>=125 and g>=105:gold+=1
                if min(r,g,b)>=210 and max(r,g,b)-min(r,g,b)<=46:white+=1
            if gold>=6:gold_rows.append(y)
            if white>=12:white_rows.append(y)
        print(f"  gold delta={threshold} rows=", (min(gold_rows),max(gold_rows)) if gold_rows else None,
              "core-white rows=",(min(white_rows),max(white_rows)) if white_rows else None)
    print("CAMERA_EDGE_PROBE per row y=1575..1610 and y=1770..1800")
    for y in list(range(1575,1611,2))+list(range(1770,1801,2)):
        gold=white=0
        for x in range(430,651):
            r,g,b=get(pix[y],x,bpp)
            if r-g>=18 and g-b>=5 and r>=125 and g>=105:gold+=1
            if min(r,g,b)>=210 and max(r,g,b)-min(r,g,b)<=46:white+=1
        print(f"  y={y} gold={gold} white={white}")
    print("NOTE: masks are reproducible threshold candidates. Flattened raster alone cannot establish invisible touch rectangle.")

if __name__=="__main__":main()
