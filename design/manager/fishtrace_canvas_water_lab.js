(() => {
  "use strict";
  /*
   * Third-party algorithm adaptation: ethanfox/water-caustics, MIT.
   * Pinned upstream commit: de856eec62819b7c4176366db73b8fd37a1b1455.
   * Sources: src/caustic-field.ts (blob 5b5a67298a8631892d5153f134ae3a17a348ec6b); src/canvas2d.ts (blob 441d39fb0aa8fd3f0960577713b0705c0423e598).
   * Copyright (c) 2026 water-caustics contributors; full license: design/manager/third_party/water-caustics/LICENSE.
   * This port removes TypeScript-only annotations and evaluates the original continuous sampleCaustic field
   * into a deterministic Canvas 2D texture. Local additions: same-field ambient/highlight derivations and
   * the contract-bound bottom-up reveal mask. The P3 preview does not call the preserved patch renderer.
   */
  const root = document.querySelector('[data-testid="fishtrace-sites-web-workspace"]');
  const lab = document.querySelector('[data-testid="canvas-water-lab"]');
  if (!root || !lab) return;

  const stage = document.getElementById("water-stage");
  const image = document.getElementById("water-source-image");
  const canvas = document.getElementById("water-overlay");
  const $ = id => document.getElementById(id);
  const timelineInput = $("water-timeline");
  const strengthInput = $("water-strength");
  const enabledInput = $("water-enabled");
  const debugRegionInput = $("water-region-debug");
  const playButton = $("water-play");
  const pauseButton = $("water-pause");
  const resetButton = $("water-reset");
  const retryButton = $("water-retry");
  const loadError = $("water-load-error");
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  let contract = null;
  let scene = null;
  let renderer = null;
  let controller = null;
  let resizeObserver = null;
  let strength = 1;
  let waterEnabled = true;
  let debugRegion = false;
  let loading = false;

  function setControlsDisabled(disabled) {
    lab.querySelectorAll(".water-control").forEach(control => { control.disabled = disabled; });
  }
  function setStatus(text) { $("water-renderer-status").textContent = text; }
  function setMotionStatus() {
    $("water-motion-status").textContent = reducedMotion.matches ? "已启用 · 低幅运动" : "未启用 · 完整运动";
    canvas.dataset.motionScale = String(reducedMotion.matches ? contract?.motion?.reduced_motion_scale || 0.12 : 1);
  }
  function timeoutFetch(url, options) {
    const aborter = new AbortController();
    const timer = setTimeout(() => aborter.abort(), 15000);
    return fetch(url, Object.assign({}, options || {}, {signal:aborter.signal}))
      .finally(() => clearTimeout(timer));
  }
  async function sha256(buffer) {
    const digest = await crypto.subtle.digest("SHA-256", buffer);
    return Array.from(new Uint8Array(digest)).map(byte => byte.toString(16).padStart(2,"0")).join("");
  }
  async function verifyAsset() {
    $("water-asset-status").textContent = "等待原图解码";
    if (image.complete && !image.naturalWidth) throw new Error("固定演示图片无法加载");
    if (!image.complete) {
      await new Promise((resolve, reject) => {
        image.addEventListener("load", resolve, {once:true});
        image.addEventListener("error", () => reject(new Error("固定演示图片无法加载")), {once:true});
      });
    }
    if (typeof image.decode === "function") await image.decode();
    if (image.naturalWidth !== contract.asset.width || image.naturalHeight !== contract.asset.height) {
      throw new Error("固定演示图片尺寸不符：" + image.naturalWidth + "×" + image.naturalHeight);
    }
    const response = await timeoutFetch(image.currentSrc || image.src, {cache:"force-cache",credentials:"same-origin"});
    if (!response.ok) throw new Error("固定演示图片请求失败 HTTP " + response.status);
    const actualSha = await sha256(await response.arrayBuffer());
    if (actualSha !== contract.asset.sha256) throw new Error("固定演示图片 SHA-256 不符");
    image.dataset.sha256 = actualSha;
    image.dataset.assetVerified = "true";
    $("water-asset-status").textContent = "SHA-256 已校验 · " + image.naturalWidth + "×" + image.naturalHeight + " · " + actualSha;
  }

  function seeded(seed) {
    let value = seed >>> 0;
    return () => {
      value = (value * 1664525 + 1013904223) >>> 0;
      return value / 4294967296;
    };
  }
  function smooth(time,start,end) {
    if (end <= start) return time >= end ? 1 : 0;
    const p = Math.max(0,Math.min(1,(time-start)/(end-start)));
    return p*p*(3-2*p);
  }
  function clamp(value,min,max) { return Math.max(min,Math.min(max,value)); }
  function rgbaFromHex(hex,alpha) {
    const value=hex.replace("#","");
    const number=parseInt(value,16);
    return "rgba("+((number>>16)&255)+","+((number>>8)&255)+","+(number&255)+","+alpha+")";
  }
  function rgbaWithAlpha(value,alpha) {
    const match=value.match(/rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/i);
    return match ? "rgba("+match[1]+","+match[2]+","+match[3]+","+alpha+")" : value;
  }
  function waterCausticRotate(x,y,a) {
    const c=Math.cos(a),s=Math.sin(a);
    return [c*x-s*y,s*x+c*y];
  }
  function waterCausticOctave(uvx,uvy,t,fi) {
    let [px,py]=waterCausticRotate(uvx,uvy,0.41+fi*2.39996323);
    for(let k=0;k<2;k++){
      const fk=k;
      const wx=Math.sin(py*(1.17+fi*.19+fk*.13)+px*(.23+fi*.07)+t*(.31+fi*.05)+fi*1.61+fk);
      const wy=Math.cos(px*(.89+fi*.17+fk*.11)-py*(.29+fi*.09)-t*(.27+fi*.04)+fi*1.13);
      const amp=.46/(1+fi*.16+fk*.11);
      px+=wx*amp;py+=wy*amp;
    }
    const d1x=1+fi*.21,d1y=.33+fi*.13,d2x=-.51-fi*.11,d2y=1.09+fi*.08;
    const ridge=Math.sin(px*d1x+py*d1y+t*.09)*Math.sin(px*d2x+py*d2y-t*.07);
    return .165/(Math.abs(ridge)*5.6+.22);
  }
  function waterCausticField(uvx,uvy,t) {
    const px=uvx+Math.sin(uvy*.37+t*.19)*.42;
    const py=uvy+Math.cos(uvx*.31-t*.15)*.42;
    const a=waterCausticOctave(px,py,t,0);
    const p2=waterCausticRotate(px*1.61803399+1.83,py*1.61803399-1.27,1.17809725);
    const b=waterCausticOctave(p2[0],p2[1],t*.79,1);
    const p3=waterCausticRotate(px*2.61803399+2.54,py*2.61803399-.74,1.54809725);
    const c=waterCausticOctave(p3[0],p3[1],t*.62,2);
    const acc=Math.max(a*1.12,b*.78)+c*.34;
    return Math.pow(clamp(acc,0,1.95),1.45);
  }
  function waterCausticSmoothstep(edge0,edge1,x) {
    const t=clamp((x-edge0)/(edge1-edge0),0,1);
    return t*t*(3-2*t);
  }
  function waterCausticRgb(hex) {
    const raw=hex.replace("#","");
    return [parseInt(raw.slice(0,2),16)/255,parseInt(raw.slice(2,4),16)/255,parseInt(raw.slice(4,6),16)/255];
  }
  function sampleCaustic(fragX,fragY,resX,resY,time,scale,intensity,color,tint,tintStrength) {
    const denom=Math.max(resY,1);
    const uvx=((fragX*2-resX)/denom)*1.72*scale;
    const uvy=((fragY*2-resY)/denom)*1.72*scale;
    const c=waterCausticField(uvx,uvy,time*.36);
    const veins=waterCausticSmoothstep(.15,1.08,c);
    const haze=waterCausticSmoothstep(.03,.78,c)*.3;
    const glow=(veins*.9+haze)*intensity;
    const mix=clamp(glow,0,1);
    const r=tint[0]+(color[0]-tint[0])*mix;
    const g=tint[1]+(color[1]-tint[1])*mix;
    const b=tint[2]+(color[2]-tint[2])*mix;
    const alpha=clamp(tintStrength+glow*(.88-tintStrength*.35),0,1);
    return [r,g,b,alpha];
  }
  function normalizedPoint(point,rect) {
    return {x:rect.x+point[0]*rect.width,y:rect.y+point[1]*rect.height};
  }
  function zonePolygonSets(field) {
    const c=field.regions.C.bounds;
    return {
      A:field.regions.A.polygons.map(polygon=>({id:polygon.id,points:polygon.points})),
      B:[field.regions.B.polygon],
      C:[field.regions.C.polygon || {id:"C1",points:[[c.x_min,c.y_min],[c.x_max,c.y_min],[c.x_max,c.y_max],[c.x_min,c.y_max]]}]
    };
  }
  function makeRegionPath(polygons,rect) {
    const path=new Path2D();
    for(const polygon of polygons){
      polygon.points.forEach((point,index)=>{
        const mapped=normalizedPoint(point,rect);
        if(index===0)path.moveTo(mapped.x,mapped.y);else path.lineTo(mapped.x,mapped.y);
      });
      path.closePath();
    }
    return path;
  }
  function allActivePolygons(sets) { return [...sets.A,...sets.B,...sets.C]; }
  function polygonBounds(polygons) {
    const points=polygons.flatMap(polygon=>polygon.points);
    return points.reduce((bounds,point)=>({
      minX:Math.min(bounds.minX,point[0]),minY:Math.min(bounds.minY,point[1]),
      maxX:Math.max(bounds.maxX,point[0]),maxY:Math.max(bounds.maxY,point[1])
    }),{minX:Infinity,minY:Infinity,maxX:-Infinity,maxY:-Infinity});
  }
  function polygonArea(points) {
    let area=0;
    for(let i=0;i<points.length;i++){
      const current=points[i],next=points[(i+1)%points.length];
      area+=current[0]*next[1]-next[0]*current[1];
    }
    return Math.abs(area/2);
  }
  function pointInsidePolygon(points,x,y) {
    let inside=false;
    for(let i=0,j=points.length-1;i<points.length;j=i++){
      const xi=points[i][0],yi=points[i][1],xj=points[j][0],yj=points[j][1];
      if((yi>y)!==(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;
    }
    return inside;
  }
  function sampleZonePoint(polygons,random) {
    const bounds=polygonBounds(polygons);
    for(let attempt=0;attempt<360;attempt++){
      const x=bounds.minX+random()*(bounds.maxX-bounds.minX);
      const y=bounds.minY+random()*(bounds.maxY-bounds.minY);
      if(polygons.some(polygon=>pointInsidePolygon(polygon.points,x,y)))return {x,y};
    }
    return {x:(bounds.minX+bounds.maxX)/2,y:(bounds.minY+bounds.maxY)/2};
  }
  function sampleSpreadZonePoint(polygons,random,existing,aspect) {
    if(!existing.length)return sampleZonePoint(polygons,random);
    const bounds=polygonBounds(polygons);
    let best=null,bestScore=-1;
    for(let attempt=0;attempt<48;attempt++){
      const candidate=sampleZonePoint(polygons,random);
      let nearest=Infinity;
      for(const point of existing){
        const dx=(candidate.x-point.x)*aspect,dy=candidate.y-point.y;
        nearest=Math.min(nearest,dx*dx+dy*dy);
      }
      const edgeDistance=Math.min(candidate.x-bounds.minX,bounds.maxX-candidate.x,candidate.y-bounds.minY,bounds.maxY-candidate.y);
      const score=nearest*(.92+random()*.16)+Math.max(0,edgeDistance)*.001;
      if(score>bestScore){best=candidate;bestScore=score;}
    }
    return best||sampleZonePoint(polygons,random);
  }
  function sampleGroupPatchPoint(polygons,random,anchor,aspect,radiusRange) {
    for(let attempt=0;attempt<360;attempt++){
      const radiusW=radiusRange[0]+random()*(radiusRange[1]-radiusRange[0]);
      const angle=random()*Math.PI*2;
      const candidate={x:anchor.x+Math.cos(angle)*radiusW,y:anchor.y+Math.sin(angle)*radiusW*aspect};
      if(polygons.some(polygon=>pointInsidePolygon(polygon.points,candidate.x,candidate.y)))return {point:candidate,radiusW};
    }
    return {point:anchor,radiusW:0};
  }
  function zoneSequence(rendering) {
    return [
      ...Array(rendering.groups.A).fill({group:"A",zone:"A"}),
      ...Array(rendering.groups.B).fill({group:"B",zone:"A"}),
      ...Array(rendering.groups.C).fill({group:"C",zone:"A"}),
      {group:"D",zone:"A"},{group:"D",zone:"B"},{group:"D",zone:"B"},
      {group:"E",zone:"B"},{group:"E",zone:"B"},{group:"E",zone:"C"}
    ];
  }
  function smoothClosedPolygon(ctx,points) {
    if(!points.length)return;
    const mid=(a,b)=>({x:(a.x+b.x)/2,y:(a.y+b.y)/2});
    const firstMid=mid(points[points.length-1],points[0]);
    ctx.beginPath();
    ctx.moveTo(firstMid.x,firstMid.y);
    for(let i=0;i<points.length;i++){
      const current=points[i],next=points[(i+1)%points.length],nextMid=mid(current,next);
      ctx.quadraticCurveTo(current.x,current.y,nextMid.x,nextMid.y);
    }
    ctx.closePath();
  }
  function traceSmoothPoints(ctx,points,rect,asset,startNew=true) {
    const mapped=points.map(point=>normalizedPoint([point.x,point.y],rect));
    if(!mapped.length)return;
    if(startNew)ctx.moveTo(mapped[0].x,mapped[0].y);else ctx.lineTo(mapped[0].x,mapped[0].y);
    for(let i=1;i<mapped.length-1;i++){
      const p=mapped[i],n=mapped[i+1];
      ctx.quadraticCurveTo(p.x,p.y,(p.x+n.x)/2,(p.y+n.y)/2);
    }
    if(mapped.length>1)ctx.lineTo(mapped[mapped.length-1].x,mapped[mapped.length-1].y);
  }
  function interpolatePoint(points,u) {
    const position=clamp(u,0,1)*(points.length-1),index=Math.floor(position),fraction=position-index;
    const a=points[index],b=points[Math.min(points.length-1,index+1)];
    return {x:a.x+(b.x-a.x)*fraction,y:a.y+(b.y-a.y)*fraction};
  }

  class WaterCausticsScene {
    constructor(value) {
      this.contract=value;
      this.rendering=value.rendering;
      this.field=value.water_field;
      this.asset=value.asset;
      this.zones=zonePolygonSets(this.field);
      this.zonePaths={};
      this.activePath=null;
      const random=seeded(this.rendering.deterministic_seed);
      this.patches=this.createPatches(random);
      this.groupMotion=this.createGroupMotion(random);
      this.ambientFields=this.createAmbientFields(random);
      this.highlights=this.createHighlights(random);
      this.p3FieldCanvas=null;
      this.p3HighlightCanvas=null;
      this.p3FieldKey="";
    }
    prepareP3Field(previewSpec) {
      const spec=previewSpec.parameters;
      const key=JSON.stringify(previewSpec);
      if(this.p3FieldCanvas&&this.p3FieldKey===key)return;
      const width=spec.compute_width;
      const height=Math.max(1,Math.round(width*this.asset.height/this.asset.width));
      const color=waterCausticRgb(spec.color),tint=waterCausticRgb(spec.tint);
      const fieldCanvas=document.createElement("canvas");
      fieldCanvas.width=width;fieldCanvas.height=height;
      const fieldContext=fieldCanvas.getContext("2d",{alpha:true,willReadFrequently:true});
      const highlightCanvas=document.createElement("canvas");
      highlightCanvas.width=width;highlightCanvas.height=height;
      const highlightContext=highlightCanvas.getContext("2d",{alpha:true,willReadFrequently:true});
      if(!fieldContext||!highlightContext)throw new Error("连续焦散纹理的 Canvas 2D 缓冲区初始化失败");
      const pixels=fieldContext.createImageData(width,height);
      const highlights=highlightContext.createImageData(width,height);
      const threshold=spec.derived_layers.highlight_alpha_threshold;
      const highlightOpacity=spec.derived_layers.highlight_opacity;
      const sampleTime=spec.speed===0?0:(spec.time_ms/1000)*spec.speed;
      for(let y=0;y<height;y++)for(let x=0;x<width;x++){
        const sample=sampleCaustic(x+.5,height-y-.5,width,height,sampleTime,spec.scale,spec.intensity,color,tint,spec.tintStrength);
        const offset=(y*width+x)*4;
        pixels.data[offset]=Math.round(sample[0]*255);
        pixels.data[offset+1]=Math.round(sample[1]*255);
        pixels.data[offset+2]=Math.round(sample[2]*255);
        pixels.data[offset+3]=Math.round(sample[3]*255);
        const highlight=waterCausticSmoothstep(threshold[0],threshold[1],sample[3])*highlightOpacity;
        highlights.data[offset]=220;highlights.data[offset+1]=252;highlights.data[offset+2]=247;
        highlights.data[offset+3]=Math.round(highlight*255);
      }
      fieldContext.putImageData(pixels,0,0);
      highlightContext.putImageData(highlights,0,0);
      this.p3FieldCanvas=fieldCanvas;
      this.p3HighlightCanvas=highlightCanvas;
      this.p3FieldKey=key;
    }
    drawP3(ctx,rect,strengthValue,previewSpec) {
      this.prepareP3Field(previewSpec);
      const layers=previewSpec.derived_layers;
      const params=previewSpec.parameters;
      ctx.save();
      ctx.globalCompositeOperation=params.blendMode;
      ctx.filter="blur("+(rect.width*layers.ambient_blur_fraction_w)+"px)";
      ctx.globalAlpha=strengthValue*layers.ambient_opacity;
      ctx.drawImage(this.p3FieldCanvas,rect.x,rect.y,rect.width,rect.height);
      ctx.filter="none";
      ctx.globalAlpha=strengthValue*layers.caustic_opacity;
      ctx.drawImage(this.p3FieldCanvas,rect.x,rect.y,rect.width,rect.height);
      ctx.globalCompositeOperation=layers.highlight_blend_mode;
      ctx.globalAlpha=strengthValue;
      ctx.drawImage(this.p3HighlightCanvas,rect.x,rect.y,rect.width,rect.height);
      ctx.restore();
    }
    createPatches(random) {
      const sequence=zoneSequence(this.rendering.caustics);
      const angles=[-34,-27,-19,-7,15,23,31,-31,-25,-15,4,24,28,-38,-20,13,26,30].map(value=>value*Math.PI/180);
      const groupAnchorsByZone={A:{},B:{},C:{}};
      const anchorsByZone={A:[],B:[],C:[]};
      return sequence.map((item,index)=>{
        const sizeRange=this.rendering.caustics.patch_sampling;
        const longW=sizeRange.length_w[0]+random()*(sizeRange.length_w[1]-sizeRange.length_w[0]);
        const shortW=sizeRange.short_axis_w[0]+random()*(sizeRange.short_axis_w[1]-sizeRange.short_axis_w[0]);
        const ratio=longW/shortW;
        if(ratio<this.rendering.caustics.aspect_ratio[0]||ratio>this.rendering.caustics.aspect_ratio[1])throw new Error("A-line caustic patch aspect ratio fell outside its contract");
        const group=item.group;
        const groupZoneKey=group+":"+item.zone;
        const aspect=this.asset.width/this.asset.height;
        let anchor=groupAnchorsByZone[item.zone][groupZoneKey];
        let center,clusterRadiusW=0;
        if(!anchor){
          anchor=sampleSpreadZonePoint(this.zones[item.zone],random,anchorsByZone[item.zone],aspect);
          groupAnchorsByZone[item.zone][groupZoneKey]=anchor;
          anchorsByZone[item.zone].push(anchor);
          center=anchor;
        }else{
          const clustered=sampleGroupPatchPoint(this.zones[item.zone],random,anchor,aspect,this.rendering.caustics.patch_sampling.group_cluster_radius_w);
          center=clustered.point;
          clusterRadiusW=clustered.radiusW;
        }
        const offsets=Array.from({length:13},()=>random()*2-1);
        const widthNoise=Array.from({length:13},()=>random()*2-1);
        const edgeNoise=Array.from({length:13},()=>random()*2-1);
        return {
          id:index+1,group,zone:item.zone,x:center.x,y:center.y,anchorX:anchor.x,anchorY:anchor.y,clusterRadiusW,
          angle:angles[index],longW,shortW,ratio,
          irregularity:this.rendering.caustics.edge_irregularity[0]+random()*(this.rendering.caustics.edge_irregularity[1]-this.rendering.caustics.edge_irregularity[0]),
          phase:random()*Math.PI*2,speed:.62+random()*.62,
          waveCycles:1.4+random()*1.3,
          offsets,widthNoise,edgeNoise,
          weight:.84+random()*.28,
          colorIndex:index===12?4:Math.floor(random()*4),
          riseP2:.008+random()*.014,riseP3:.018+random()*.017,
          driftP2:(random()*2-1)*.007,driftP3:(random()*2-1)*.012,
          revealDelayMs:random()*90,exitDelayMs:random()*100,
          peripheral:random()
        };
      });
    }
    createGroupMotion(random) {
      const result={};
      for(const name of ["A","B","C","D","E"]){
        const members=this.patches.filter(patch=>patch.group===name);
        result[name]={
          x:members.reduce((sum,item)=>sum+item.x,0)/members.length,
          y:members.reduce((sum,item)=>sum+item.y,0)/members.length,
          riseP2:members.reduce((sum,item)=>sum+item.riseP2,0)/members.length,
          riseP3:members.reduce((sum,item)=>sum+item.riseP3,0)/members.length,
          driftP2:members.reduce((sum,item)=>sum+item.driftP2,0)/members.length,
          driftP3:members.reduce((sum,item)=>sum+item.driftP3,0)/members.length,
          phase:random()*Math.PI*2,
          speed:.35+random()*.25
        };
      }
      return result;
    }
    createAmbientFields(random) {
      const spec=this.rendering.ambient;
      const distribution=spec.zone_distribution;
      const zones=[...Array(distribution.A).fill("A"),...Array(distribution.B).fill("B"),...Array(distribution.C).fill("C")];
      return zones.map((zone,index)=>{
        const large=index<spec.large_count;
        const range=large?spec.large_radius_sampling_w:spec.medium_radius_sampling_w;
        return {
          id:index+1,zone,center:sampleZonePoint(this.zones[zone],random),
          radiusW:range[0]+random()*(range[1]-range[0]),
          aspect:spec.aspect_ratio[0]+random()*(spec.aspect_ratio[1]-spec.aspect_ratio[0]),angle:random()*Math.PI*2,
          phase:random()*Math.PI*2,weight:.84+random()*.16,
          feather:spec.edge_feather_fraction[0]+random()*(spec.edge_feather_fraction[1]-spec.edge_feather_fraction[0]),
          lobe:Array.from({length:12},()=>random()*2-1),
          colorIndex:Math.floor(random()*spec.colors_rgba.length)
        };
      });
    }
    createHighlights(random) {
      const spec=this.rendering.highlights;
      const distribution=spec.zone_distribution;
      const zones=[...Array(distribution.A).fill("A"),...Array(distribution.B).fill("B"),...Array(distribution.C).fill("C")];
      const goldCount=Math.min(2,spec.warm_segment_count_max);
      return zones.map((zone,index)=>{
        const candidates=this.patches.filter(patch=>patch.zone===zone&&patch.zone!=="C");
        const parent=candidates[index%candidates.length]||this.patches[index%this.patches.length];
        const lengthFractionRange=spec.sampling_fraction?.length||[0,1];
        const widthFractionRange=spec.sampling_fraction?.width||[0,1];
        const lengthFraction=lengthFractionRange[0]+random()*(lengthFractionRange[1]-lengthFractionRange[0]);
        const widthFraction=widthFractionRange[0]+random()*(widthFractionRange[1]-widthFractionRange[0]);
        return {
          id:index+1,zone,parentId:parent.id,
          positionU:.18+random()*.64,
          lengthW:spec.segment_length_w[0]+lengthFraction*(spec.segment_length_w[1]-spec.segment_length_w[0]),
          widthW:spec.segment_width_w[0]+widthFraction*(spec.segment_width_w[1]-spec.segment_width_w[0]),
          blurW:spec.blur_w[0]+random()*(spec.blur_w[1]-spec.blur_w[0]),
          angleDelta:(random()*2-1)*.22,
          bendW:(random()*2-1)*.004,
          weight:.75+random()*.25,
          warm:index<goldCount
        };
      });
    }
    setDisplayGeometry(rect) {
      for(const zone of ["A","B","C"])this.zonePaths[zone]=makeRegionPath(this.zones[zone],rect);
      this.activePath=new Path2D();
      for(const zone of ["A","B","C"])this.activePath.addPath(this.zonePaths[zone]);
      this.rect=rect;
    }
    activation(zone,timeMs) {
      if(zone==="A")return smooth(timeMs,0,350);
      if(zone==="B")return smooth(timeMs,this.field.regions.B.activation.start_ms,this.field.regions.B.activation.full_ms);
      return smooth(timeMs,this.field.regions.C.activation.start_ms,this.field.regions.C.activation.full_ms);
    }
    exit(zone,timeMs,patchDelay=0) {
      const window=this.field.zone_exit[zone];
      return smooth(timeMs,window.start_ms+patchDelay,window.end_ms);
    }
    groupOffset(patch,timeMs,motionScale) {
      const group=this.groupMotion[patch.group];
      const p2=smooth(timeMs,350,950),p3=smooth(timeMs,950,1200);
      const rise=group.riseP2*p2+(group.riseP3-group.riseP2)*p3;
      const drift=group.driftP2*p2+(group.driftP3-group.driftP2)*p3;
      const slow=Math.sin(timeMs/1700*group.speed+group.phase)*.0018*motionScale;
      return {x:(drift+slow)*motionScale,y:-rise*motionScale};
    }
    patchGeometry(patch,timeMs,motionScale) {
      const appear=smooth(timeMs,260,950);
      const growth=Math.pow(smooth(timeMs,350,1200),1.85);
      const returnScale=smooth(timeMs,1200,2050);
      const exitScale=smooth(timeMs,2050,2800);
      const faceProfile=this.rendering.caustics.face_opacity_profile;
      const faceExpansion=smooth(timeMs,faceProfile.expand_start_ms,faceProfile.expand_end_ms);
      const faceSettlement=smooth(timeMs,faceProfile.settle_start_ms,faceProfile.settle_end_ms);
      const zoneExit=this.exit(patch.zone,timeMs,patch.exitDelayMs+patch.peripheral*80);
      let scale=.72+.38*growth;
      if(timeMs>1200)scale=1.10-.14*returnScale;
      if(timeMs>2050)scale=.96-.14*exitScale;
      const structureScale=1-zoneExit*(.12+patch.peripheral*.12);
      scale*=structureScale;
      const offset=this.groupOffset(patch,timeMs,motionScale);
      const center={x:patch.x+offset.x,y:patch.y+offset.y};
      const phase=patch.phase+timeMs/1000*patch.speed*.58*motionScale;
      const angle=patch.angle+Math.sin(phase*.31+patch.phase)*.045*motionScale;
      const tangent={x:Math.cos(angle),y:Math.sin(angle)*this.asset.width/this.asset.height};
      const normal={x:-Math.sin(angle),y:Math.cos(angle)*this.asset.width/this.asset.height};
      const centerline=[],positive=[],negative=[];
      for(let index=0;index<patch.offsets.length;index++){
        const u=index/(patch.offsets.length-1);
        const envelope=Math.pow(Math.max(0,Math.sin(Math.PI*u)),.34+zoneExit*.25);
        const wave=(Math.sin(phase+u*patch.waveCycles+patch.phase)*.052+Math.sin(phase*.63+u*patch.waveCycles*1.91+patch.phase*.41)*.027);
        const bend=(patch.offsets[index]*patch.irregularity*.31+wave)*patch.longW*scale;
        const longitudinal=(u-.5)*patch.longW*scale;
        const x=center.x+tangent.x*longitudinal+normal.x*bend;
        const y=center.y+tangent.y*longitudinal+normal.y*bend;
        const broad=Math.sin(u*Math.PI*2+patch.phase+phase*.14)*.19;
        const fine=Math.sin(u*Math.PI*4.5+patch.phase*.7+phase*.10)*.11;
        const detail=Math.sin(u*Math.PI*6.8+patch.phase*1.3+phase*.07)*.055;
        const widthProfile=clamp(.91+broad+fine+detail+patch.widthNoise[index]*patch.irregularity*.66,.38,1.24);
        const neck=1-.18*Math.exp(-Math.pow((u-.47)/.13,2));
        const half=patch.shortW*scale*envelope*widthProfile*neck*.5;
        const sideA=half*(1+patch.edgeNoise[index]*patch.irregularity*.72);
        const sideB=half*(1-patch.edgeNoise[index]*patch.irregularity*.61);
        centerline.push({x,y});
        positive.push({x:x+normal.x*sideA,y:y+normal.y*sideA});
        negative.push({x:x-normal.x*sideB,y:y-normal.y*sideB});
      }
      return {center,centerline,positive,negative,scale,angle,phase,appear,zoneExit,faceExpansion,faceSettlement};
    }
    drawAmbient(ctx,rect,timeMs,intensity,strengthValue,motionScale,zoneName) {
      const spec=this.rendering.ambient;
      for(const field of this.ambientFields){
        if(field.zone!==zoneName)continue;
        const zone=this.field.regions[field.zone];
        const zoneCoefficient=zone.strength_coefficient;
        const temporal=this.activation(field.zone,timeMs)*(1-this.exit(field.zone,timeMs,field.id*16));
        const alpha=Math.min(spec.p3_single_field_opacity_max,intensity*strengthValue*spec.field_opacity_response_multiplier*field.weight)*zoneCoefficient*temporal;
        if(alpha<.002)continue;
        const offset=this.groupOffset({group:field.zone==="A"?"A":field.zone==="B"?"D":"E"},timeMs,motionScale);
        const center={x:field.center.x+offset.x,y:field.center.y+offset.y};
        const rx=field.radiusW*rect.width;
        const ry=rx*field.aspect;
        const mapped=normalizedPoint([center.x,center.y],rect);
        const phase=field.phase+timeMs/1000*.16*motionScale;
        const lobes=field.lobe.map((value,index)=>{
          const angle=(index/field.lobe.length)*Math.PI*2;
          const radius=1+value*.08+Math.sin(angle*2+phase)*.025;
          const px=mapped.x+Math.cos(angle+field.angle)*rx*radius;
          const py=mapped.y+Math.sin(angle+field.angle)*ry*radius;
          return {x:px,y:py};
        });
        ctx.save();
        ctx.globalCompositeOperation="screen";
        ctx.globalAlpha=alpha;
        ctx.filter="blur("+(rx*field.feather*.12)+"px)";
        smoothClosedPolygon(ctx,lobes);
        ctx.clip();
        ctx.translate(mapped.x,mapped.y);
        ctx.rotate(field.angle);
        ctx.scale(rx,ry);
        const gradient=ctx.createRadialGradient(0,0,0,0,0,1);
        const opacityStops=spec.radial_gradient_opacity_stops;
        gradient.addColorStop(0,rgbaWithAlpha(spec.colors_rgba[field.colorIndex],opacityStops[0]));
        gradient.addColorStop(.34,rgbaWithAlpha(spec.colors_rgba[(field.colorIndex+1)%spec.colors_rgba.length],opacityStops[1]));
        gradient.addColorStop(.68,rgbaWithAlpha(spec.colors_rgba[(field.colorIndex+2)%spec.colors_rgba.length],opacityStops[2]));
        gradient.addColorStop(1,"rgba(102,224,214,0)");
        ctx.fillStyle=gradient;
        ctx.fillRect(-1.2,-1.2,2.4,2.4);
        ctx.restore();
      }
    }
    drawOrganicCaustics(ctx,rect,intensity,strengthValue,timeMs,motionScale,patchFrames) {
      const spec=this.rendering.caustics;
      for(const item of patchFrames){
        const {patch,geometry}=item;
        const zone=this.field.regions[patch.zone];
        const activation=this.activation(patch.zone,timeMs);
        const exit=this.exit(patch.zone,timeMs,patch.exitDelayMs);
        const zoneFade=1-exit;
        const zoneScale=patch.zone==="A"?1:patch.zone==="B"?1:spec.region_c_texture_opacity_scale;
        const cap=patch.zone==="B"?spec.secondary_opacity_max_p3:patch.zone==="C"?spec.primary_opacity_max_p3*spec.region_c_texture_opacity_scale:spec.primary_opacity_max_p3;
        const reveal=geometry.appear;
        const pulse=.98+.02*Math.sin(geometry.phase*.36+patch.phase);
        const alpha=Math.min(cap,intensity*strengthValue*spec.field_opacity_response_multiplier*patch.weight)*zone.strength_coefficient*activation*zoneFade*zoneScale*reveal*pulse;
        if(alpha<.002||geometry.scale<.08)continue;
        const center=normalizedPoint([geometry.center.x,geometry.center.y],rect);
        const xScale=rect.width;
        const width=patch.shortW*geometry.scale*xScale*spec.gradient_half_extent_multiplier;
        const normalX=-Math.sin(geometry.angle),normalY=Math.cos(geometry.angle);
        const gradient=ctx.createLinearGradient(
          center.x-normalX*width,center.y-normalY*width,center.x+normalX*width,center.y+normalY*width
        );
        const color=spec.colors[patch.colorIndex];
        const profile=spec.face_opacity_profile;
        for(let stopIndex=0;stopIndex<profile.initial_stops.length;stopIndex++){
          const initial=profile.initial_stops[stopIndex];
          const expanded=profile.expanded_stops[stopIndex];
          const settled=profile.settled_stops[stopIndex];
          const expandedOffset=initial[0]+(expanded[0]-initial[0])*geometry.faceExpansion;
          const expandedOpacity=initial[1]+(expanded[1]-initial[1])*geometry.faceExpansion;
          const offset=expandedOffset+(settled[0]-expandedOffset)*geometry.faceSettlement;
          const opacity=expandedOpacity+(settled[1]-expandedOpacity)*geometry.faceSettlement;
          gradient.addColorStop(offset,rgbaFromHex(color,opacity));
        }
        ctx.save();
        ctx.globalCompositeOperation="screen";
        ctx.globalAlpha=alpha;
        ctx.filter="blur("+(rect.width*spec.edge_softness_blur_w)+"px)";
        ctx.beginPath();
        traceSmoothPoints(ctx,geometry.positive,rect,this.asset,true);
        const last=geometry.negative[geometry.negative.length-1];
        const mappedLast=normalizedPoint([last.x,last.y],rect);
        ctx.lineTo(mappedLast.x,mappedLast.y);
        traceSmoothPoints(ctx,geometry.negative.slice().reverse(),rect,this.asset,false);
        ctx.closePath();
        ctx.fillStyle=gradient;
        ctx.fill();
        ctx.restore();
      }
    }
    drawHighlights(ctx,rect,intensity,strengthValue,timeMs,motionScale,patchFrames) {
      const spec=this.rendering.highlights;
      const density=1-.40*smooth(timeMs,1200,2050);
      for(const segment of this.highlights){
        const patch=this.patches[segment.parentId-1];
        const frame=patchFrames.find(item=>item.patch.id===patch.id);
        if(!frame)continue;
        const zone=this.field.regions[segment.zone];
        const activation=this.activation(segment.zone,timeMs);
        const exit=this.exit(segment.zone,timeMs,patch.exitDelayMs);
        const zoneFade=1-exit;
        const index=Math.floor(segment.positionU*(frame.geometry.centerline.length-1));
        const center=frame.geometry.centerline[index];
        const angle=frame.geometry.angle+segment.angleDelta;
        const dx=Math.cos(angle)*segment.lengthW*.5;
        const dy=Math.sin(angle)*segment.lengthW*.5*this.asset.width/this.asset.height;
        const normalX=-Math.sin(angle),normalY=Math.cos(angle);
        const points=[
          {x:center.x-dx,y:center.y-dy},
          {x:center.x+normalX*segment.bendW,y:center.y+normalY*segment.bendW*this.asset.width/this.asset.height},
          {x:center.x+dx,y:center.y+dy}
        ];
        const baseAlpha=Math.min(spec.segment_opacity_max_p3,intensity*strengthValue*.35*segment.weight);
        const alpha=baseAlpha*zone.strength_coefficient*activation*zoneFade*density*(segment.zone==="C"?.12:1);
        if(alpha<.002)continue;
        ctx.save();
        ctx.globalCompositeOperation="lighter";
        ctx.globalAlpha=alpha;
        ctx.filter="blur("+(segment.blurW*rect.width)+"px)";
        ctx.strokeStyle=segment.warm?spec.colors[1]:spec.colors[0];
        ctx.lineWidth=segment.widthW*rect.width;
        ctx.lineCap="round";
        ctx.lineJoin="round";
        ctx.beginPath();
        traceSmoothPoints(ctx,points,rect,this.asset,true);
        ctx.stroke();
        ctx.restore();
      }
    }
    draw(ctx,rect,timeMs,layers,strengthValue,motionScale) {
      for(const zoneName of ["A","B","C"]){
        ctx.save();
        ctx.clip(this.zonePaths[zoneName],"nonzero");
        const patches=this.patches.filter(patch=>patch.zone===zoneName);
        const patchFrames=patches.map(patch=>({patch,geometry:this.patchGeometry(patch,timeMs,motionScale)}));
        this.drawAmbient(ctx,rect,timeMs,layers.ambient,strengthValue,motionScale,zoneName);
        this.drawOrganicCaustics(ctx,rect,layers.caustic,strengthValue,timeMs,motionScale,patchFrames);
        this.drawHighlights(ctx,rect,layers.highlight,strengthValue,timeMs,motionScale,patchFrames);
        ctx.restore();
      }
    }
  }

  class CanvasWaterRenderer {
    constructor(stageElement,imageElement,canvasElement,sceneValue) {
      this.stage=stageElement;
      this.image=imageElement;
      this.canvas=canvasElement;
      this.context=canvasElement.getContext("2d",{alpha:true,desynchronized:true});
      this.scene=sceneValue;
      this.effectsCanvas=document.createElement("canvas");
      this.effectsContext=this.effectsCanvas.getContext("2d",{alpha:true,desynchronized:true});
      this.maskCanvas=document.createElement("canvas");
      this.maskContext=this.maskCanvas.getContext("2d",{alpha:true});
      this.softMaskCanvas=document.createElement("canvas");
      this.softMaskContext=this.softMaskCanvas.getContext("2d",{alpha:true});
      this.width=0;
      this.height=0;
      this.dpr=1;
      this.contentRect={x:0,y:0,width:0,height:0};
      this.regionPath=null;
      this.performanceSamples=[];
      this.previousRenderStart=null;
    }
    resize() {
      const bounds=this.stage.getBoundingClientRect();
      if(!bounds.width||!bounds.height||!this.image.naturalWidth||!this.image.naturalHeight)return false;
      this.width=bounds.width;
      this.height=bounds.height;
      const p3Spec=this.scene.rendering.p3_static_preview;
      this.dpr=p3Spec?.enabled?Math.min(p3Spec.parameters.maxDpr,Math.max(1,window.devicePixelRatio||1)):Math.max(1,window.devicePixelRatio||1);
      const backingWidth=Math.max(1,Math.round(this.width*this.dpr));
      const backingHeight=Math.max(1,Math.round(this.height*this.dpr));
      for(const surface of [this.canvas,this.effectsCanvas,this.maskCanvas,this.softMaskCanvas]){
        if(surface.width!==backingWidth)surface.width=backingWidth;
        if(surface.height!==backingHeight)surface.height=backingHeight;
      }
      const fit=getComputedStyle(this.image).objectFit;
      if(fit!=="contain")throw new Error("照片 object-fit 必须为 contain");
      const scale=Math.min(this.width/this.image.naturalWidth,this.height/this.image.naturalHeight);
      const contentWidth=this.image.naturalWidth*scale;
      const contentHeight=this.image.naturalHeight*scale;
      const position=getComputedStyle(this.image).objectPosition.split(/\s+/);
      const px=position[0]==="left"?0:position[0]==="right"?1:parsePosition(position[0]);
      const py=position[1]==="top"?0:position[1]==="bottom"?1:parsePosition(position[1]);
      this.contentRect={x:(this.width-contentWidth)*px,y:(this.height-contentHeight)*py,width:contentWidth,height:contentHeight};
      this.scene.setDisplayGeometry(this.contentRect);
      this.regionPath=this.scene.activePath;
      this.buildRegionMask();
      this.canvas.dataset.contentRect=JSON.stringify(this.contentRect);
      this.canvas.dataset.dpr=String(this.dpr);
      this.canvas.dataset.waterRegionVersion=this.scene.field.version;
      this.canvas.dataset.waterRegionPolygonCount=String(allActivePolygons(this.scene.zones).length);
      this.canvas.dataset.waterRegionPolygons=JSON.stringify(this.scene.zones.A);
      this.canvas.dataset.activeRoiPolygons=JSON.stringify(this.scene.zones.A);
      this.canvas.dataset.waterRegionZones=JSON.stringify(this.scene.zones);
      this.canvas.dataset.waterRegionSourceAreaPx=String(Math.round(allActivePolygons(this.scene.zones).reduce((sum,polygon)=>sum+polygonArea(polygon.points),0)*this.scene.asset.width*this.scene.asset.height));
      this.canvas.dataset.sourceDimensions=JSON.stringify({width:this.scene.asset.width,height:this.scene.asset.height});
      const patches=this.scene.patches;
      const ambientFields=this.scene.ambientFields;
      const directionBinCount=this.scene.rendering.caustics.direction_distribution.angular_bins;
      const angleBins=new Set(patches.map(patch=>Math.floor((((patch.angle%(Math.PI))+Math.PI)%(Math.PI))/(Math.PI)*directionBinCount)));
      const nearHorizontal=patches.filter(patch=>Math.min(Math.abs(((patch.angle%(Math.PI))+Math.PI)%(Math.PI)),Math.PI-Math.abs(((patch.angle%(Math.PI))+Math.PI)%(Math.PI)))<=Math.PI/18).length/Math.max(1,patches.length);
      this.canvas.dataset.causticGeometryModel=this.scene.rendering.caustics.geometry_model;
      this.canvas.dataset.causticPatchCount=String(patches.length);
      this.canvas.dataset.causticPatchSizes=JSON.stringify(patches.map(patch=>({id:patch.id,group:patch.group,zone:patch.zone,length_w:patch.longW,short_axis_w:patch.shortW,aspect_ratio:patch.ratio,center:{x:patch.x,y:patch.y},anchor:{x:patch.anchorX,y:patch.anchorY},cluster_radius_w:patch.clusterRadiusW})));
      this.canvas.dataset.causticDirectionBinCount=String(directionBinCount);
      this.canvas.dataset.causticDirectionBins=String(angleBins.size);
      this.canvas.dataset.causticNearHorizontalFraction=String(nearHorizontal);
      this.canvas.dataset.causticGroupCounts=JSON.stringify(patches.reduce((out,patch)=>{out[patch.group]=(out[patch.group]||0)+1;return out;},{}));
      this.canvas.dataset.ambientFieldCount=String(ambientFields.length);
      this.canvas.dataset.ambientFieldZoneCounts=JSON.stringify(ambientFields.reduce((out,field)=>{out[field.zone]=(out[field.zone]||0)+1;return out;},{}));
      this.canvas.dataset.ambientFieldGeometry=JSON.stringify(ambientFields.map(field=>({id:field.id,zone:field.zone,center:{x:field.center.x,y:field.center.y},radius_w:field.radiusW,aspect:field.aspect,feather:field.feather})));
      this.canvas.dataset.highlightSegmentCount=String(this.scene.highlights.length);
      this.canvas.dataset.highlightSegments=JSON.stringify(this.scene.highlights.map(segment=>({id:segment.id,length_w:segment.lengthW,width_w:segment.widthW,warm:segment.warm})));
      this.canvas.dataset.rendererStatus="READY";
      this.render(controller?controller.timeMs:0,layerStrengthsAt(controller?controller.timeMs:0),true,false);
      return true;
    }
    buildRegionMask() {
      if(this.scene.rendering.p3_static_preview?.enabled){this.buildP3BottomGradientMask();return;}
      const dpr=this.dpr,hard=this.maskContext,soft=this.softMaskContext;
      hard.setTransform(1,0,0,1,0,0);
      soft.setTransform(1,0,0,1,0,0);
      hard.clearRect(0,0,this.maskCanvas.width,this.maskCanvas.height);
      soft.clearRect(0,0,this.softMaskCanvas.width,this.softMaskCanvas.height);
      hard.setTransform(dpr,0,0,dpr,0,0);
      soft.setTransform(dpr,0,0,dpr,0,0);
      hard.fillStyle="#fff";
      hard.fill(this.regionPath,"nonzero");
      const fade=this.scene.field.edge_fade_width_w*this.contentRect.width;
      soft.filter="blur("+fade+"px)";
      soft.fillStyle="#fff";
      soft.fill(this.regionPath,"nonzero");
      soft.filter="none";
      hard.globalCompositeOperation="destination-in";
      hard.drawImage(this.softMaskCanvas,0,0,this.width,this.height);
      hard.globalCompositeOperation="source-over";
      hard.setTransform(1,0,0,1,0,0);
      soft.setTransform(1,0,0,1,0,0);
    }
    buildP3BottomGradientMask() {
      const hard=this.maskContext,rect=this.contentRect,spec=this.scene.rendering.p3_static_preview;
      hard.setTransform(1,0,0,1,0,0);
      hard.clearRect(0,0,this.maskCanvas.width,this.maskCanvas.height);
      hard.setTransform(this.dpr,0,0,this.dpr,0,0);
      const gradient=hard.createLinearGradient(0,rect.y,0,rect.y+rect.height);
      gradient.addColorStop(0,"rgba(255,255,255,0)");
      gradient.addColorStop(spec.spatial_gradient.feather_start,"rgba(255,255,255,0)");
      gradient.addColorStop(spec.spatial_gradient.feather_start+.03,"rgba(255,255,255,.12)");
      gradient.addColorStop(spec.spatial_gradient.feather_start+.07,"rgba(255,255,255,.48)");
      gradient.addColorStop(spec.spatial_gradient.feather_start+.12,"rgba(255,255,255,.86)");
      gradient.addColorStop(spec.spatial_gradient.feather_end,"rgba(255,255,255,1)");
      gradient.addColorStop(1,"rgba(255,255,255,1)");
      hard.fillStyle=gradient;
      hard.fillRect(0,0,this.width,this.height);
      hard.setTransform(1,0,0,1,0,0);
    }
    render(timeMs,layers,strengthEnabled,playing) {
      if(!this.width||!this.height)this.resize();
      if(!this.width||!this.height)return;
      const started=performance.now();
      if(playing&&this.previousRenderStart===null)this.performanceSamples=[];
      const interval=this.previousRenderStart===null?null:started-this.previousRenderStart;
      if(playing)this.previousRenderStart=started;
      const effect=this.effectsContext;
      effect.setTransform(this.dpr,0,0,this.dpr,0,0);
      effect.clearRect(0,0,this.width,this.height);
      effect.globalAlpha=1;
      effect.globalCompositeOperation="source-over";
      effect.filter="none";
      const p3Static=Boolean(this.scene.rendering.p3_static_preview?.enabled);
      const hasEnergy=p3Static?true:(layers.ambient>0||layers.caustic>0||layers.highlight>0);
      if(strengthEnabled&&hasEnergy&&waterEnabled&&this.contentRect.width){
        effect.save();
        effect.beginPath();
        effect.rect(this.contentRect.x,this.contentRect.y,this.contentRect.width,this.contentRect.height);
        effect.clip();
        if(p3Static)this.scene.drawP3(effect,this.contentRect,strength,this.scene.rendering.p3_static_preview);
        else{
          effect.clip(this.regionPath,"nonzero");
          const motionScale=reducedMotion.matches?this.scene.contract.motion.reduced_motion_scale:1;
          this.scene.draw(effect,this.contentRect,timeMs,layers,strength,motionScale);
        }
        effect.restore();
        effect.setTransform(1,0,0,1,0,0);
        effect.globalCompositeOperation="destination-in";
        effect.drawImage(this.maskCanvas,0,0);
        effect.globalCompositeOperation="source-over";
      }else{
        effect.setTransform(1,0,0,1,0,0);
      }
      const output=this.context;
      output.setTransform(1,0,0,1,0,0);
      output.clearRect(0,0,this.canvas.width,this.canvas.height);
      output.setTransform(this.dpr,0,0,this.dpr,0,0);
      output.globalCompositeOperation="source-over";
      output.globalAlpha=1;
      output.drawImage(this.effectsCanvas,0,0,this.width,this.height);
      if(debugRegion)this.drawDebugRegion(output);
      output.globalCompositeOperation="source-over";
      output.globalAlpha=1;
      output.setTransform(1,0,0,1,0,0);
      const motionScale=reducedMotion.matches?this.scene.contract.motion.reduced_motion_scale:1;
      this.canvas.dataset.activeIntensity=String(Math.round(strengthEnabled&&waterEnabled?(p3Static?strength:layers.caustic*strength)*100:0));
      this.canvas.dataset.layerStrengths=JSON.stringify(p3Static?{ambient:strength,caustic:strength,highlight:strength}:Object.fromEntries(Object.entries(layers).map(([key,value])=>[key,value*strength])));
      this.canvas.dataset.canvasBackend="2d";
      this.canvas.dataset.activeAlgorithm=p3Static?this.scene.rendering.p3_static_preview.algorithm:"legacy_a_line_patch_path";
      this.canvas.dataset.previewMode=p3Static?"P3_STATIC_1200MS":"TIMELINE";
      const faceProfile=this.scene.rendering.caustics.face_opacity_profile;
      this.canvas.dataset.causticFaceExpansion=String(p3Static?1:smooth(timeMs,faceProfile.expand_start_ms,faceProfile.expand_end_ms));
      this.canvas.dataset.causticFaceSettlement=String(p3Static?0:smooth(timeMs,faceProfile.settle_start_ms,faceProfile.settle_end_ms));
      this.canvas.dataset.currentTime=String(Math.round(p3Static?this.scene.rendering.p3_static_preview.time_ms:timeMs));
      this.canvas.dataset.waterOn=String(waterEnabled);
      this.canvas.dataset.motionScale=String(motionScale);
      this.canvas.dataset.debugRegion=String(debugRegion);
      this.canvas.dataset.renderDurationMs=(performance.now()-started).toFixed(3);
      if(playing){
        if(interval!==null)this.performanceSamples.push({frame_interval_ms:interval,draw_ms:Number(this.canvas.dataset.renderDurationMs)});
      }else if(this.previousRenderStart!==null){
        this.previousRenderStart=null;
        this.canvas.dataset.performanceSampleCount=String(this.performanceSamples.length);
        this.canvas.dataset.performanceSamples=JSON.stringify(this.performanceSamples);
      }
    }
    drawDebugRegion(context) {
      context.save();
      context.setTransform(this.dpr,0,0,this.dpr,0,0);
      const colors={A:"rgba(41,170,157,.95)",B:"rgba(66,127,205,.95)",C:"rgba(222,164,73,.95)"};
      for(const zone of ["A","B","C"]){
        context.fillStyle=zone==="A"?"rgba(42,185,166,.10)":zone==="B"?"rgba(81,151,222,.09)":"rgba(232,183,89,.08)";
        context.strokeStyle=colors[zone];
        context.lineWidth=1.7;
        context.setLineDash(zone==="C"?[3,3]:[7,4]);
        context.fill(this.scene.zonePaths[zone],"nonzero");
        context.stroke(this.scene.zonePaths[zone]);
      }
      context.restore();
    }
    inspect(){return {width:this.width,height:this.height,dpr:this.dpr,contentRect:this.contentRect};}
  }
  function parsePosition(value) {
    if(!value||value==="center")return .5;
    if(value.endsWith("%"))return clamp(parseFloat(value)/100,0,1);
    const amount=parseFloat(value);
    return Number.isFinite(amount)?clamp(amount/100,0,1):.5;
  }
  class WaterTimelineController {
    constructor(value,onFrame) {
      this.duration=value.timeline.duration_ms;
      this.frames=value.timeline.frames;
      this.onFrame=onFrame;
      this.timeMs=0;
      this.playing=false;
      this.rafId=0;
      this.lastTick=null;
      this.tick=this.tick.bind(this);
      document.addEventListener("visibilitychange",()=>{
        if(document.hidden){this.cancelFrame();this.lastTick=null;}
        else if(this.playing){this.lastTick=null;this.rafId=requestAnimationFrame(this.tick);}
      });
    }
    cancelFrame(){if(this.rafId)cancelAnimationFrame(this.rafId);this.rafId=0;}
    emit(){this.onFrame(this.timeMs,this.playing);}
    seek(timeMs){this.timeMs=clamp(timeMs,0,this.duration);this.emit();}
    play(){
      if(this.playing||this.timeMs>=this.duration)return;
      this.playing=true;this.lastTick=null;this.emit();
      if(!document.hidden)this.rafId=requestAnimationFrame(this.tick);
    }
    pause(){this.playing=false;this.lastTick=null;this.cancelFrame();this.emit();}
    reset(){this.pause();this.seek(0);}
    tick(now){
      if(!this.playing||document.hidden)return;
      if(this.lastTick===null)this.lastTick=now;
      else{this.timeMs+=Math.min(50,Math.max(0,now-this.lastTick));this.lastTick=now;}
      if(this.timeMs>=this.duration){this.timeMs=this.duration;this.playing=false;this.cancelFrame();}
      this.emit();
      if(this.playing)this.rafId=requestAnimationFrame(this.tick);else this.rafId=0;
    }
  }
  function layerStrengthsAt(timeMs) {
    const frames=contract.timeline.frames;
    for(let i=0;i<frames.length-1;i++){
      const from=frames[i],to=frames[i+1];
      if(timeMs<=to.ms){
        const p=smooth(timeMs,from.ms,to.ms);
        return {
          ambient:from.layers.ambient+(to.layers.ambient-from.layers.ambient)*p,
          caustic:from.layers.caustic+(to.layers.caustic-from.layers.caustic)*p,
          highlight:from.layers.highlight+(to.layers.highlight-from.layers.highlight)*p
        };
      }
    }
    return Object.assign({},frames[frames.length-1].layers);
  }
  function percentAt(timeMs){return layerStrengthsAt(timeMs).caustic;}
  function activeFrameLabel(timeMs) {
    const exact=contract.timeline.frames.find(frame=>Math.abs(frame.ms-timeMs)<.5);
    if(exact)return exact.id;
    for(let i=0;i<contract.timeline.frames.length-1;i++)if(timeMs<contract.timeline.frames[i+1].ms)return contract.timeline.frames[i].id+" → "+contract.timeline.frames[i+1].id;
    return "P7";
  }
  function updateReadout(timeMs,playing) {
    const layers=layerStrengthsAt(timeMs);
    const amount=layers.caustic*strength;
    const p3Static=Boolean(contract?.rendering?.p3_static_preview?.enabled);
    const displayTime=p3Static?contract.rendering.p3_static_preview.time_ms:timeMs;
    const stageLabel=p3Static?"P3 静态验收":activeFrameLabel(displayTime);
    const displayAmount=p3Static?strength:amount;
    $("water-current-stage").textContent=stageLabel;
    $("water-current-time").textContent=Math.round(displayTime)+" ms";
    $("water-debug-stage").textContent=stageLabel;
    $("water-debug-time").textContent=p3Static?"1200 ms · 静态预览 / "+contract.timeline.duration_ms+" ms":Math.round(displayTime)+" ms / "+contract.timeline.duration_ms+" ms";
    $("water-debug-intensity").textContent=Math.round(displayAmount*100)+"% · P3 静态水光";
    $("water-progress-label").textContent=Math.round(displayTime/contract.timeline.duration_ms*100)+"%";
    $("water-play-state").textContent=p3Static?"P3 静态预览 · 已暂停":(playing?"播放中":"已暂停");
    timelineInput.value=String(Math.round(displayTime));
    playButton.setAttribute("aria-pressed",String(playing));
    pauseButton.setAttribute("aria-pressed",String(!playing));
    const exact=contract.timeline.frames.find(frame=>frame.ms===Math.round(timeMs));
    lab.querySelectorAll("[data-water-frame]").forEach(button=>{
      const active=Boolean(exact&&exact.id===button.dataset.waterFrame);
      button.classList.toggle("is-active",active);
      button.setAttribute("aria-pressed",String(active));
    });
  }
  function renderAt(timeMs,playing) {
    const p3Static=Boolean(contract?.rendering?.p3_static_preview?.enabled);
    const displayTime=p3Static?contract.rendering.p3_static_preview.time_ms:timeMs;
    if(p3Static&&controller){controller.timeMs=displayTime;controller.playing=false;}
    updateReadout(displayTime,p3Static?false:playing);
    if(renderer)renderer.render(displayTime,layerStrengthsAt(displayTime),true,p3Static?false:playing);
  }
  function bindControls() {
    root.querySelectorAll("[data-water-view]").forEach(button=>{
      button.addEventListener("click",()=>{
        const view=button.dataset.waterView;
        root.dataset.workspaceView=view;
        root.querySelectorAll("[data-water-view]").forEach(tab=>{
          const active=tab===button;
          tab.classList.toggle("is-active",active);
          tab.setAttribute("aria-pressed",String(active));
        });
        root.querySelectorAll("[data-workspace-panel]").forEach(panel=>{panel.hidden=panel.dataset.workspacePanel!==view;});
        if(view==="canvas-water"&&renderer)requestAnimationFrame(()=>{
          renderer.resize();renderer.render(controller.timeMs,layerStrengthsAt(controller.timeMs),true,controller.playing);
        });
      });
    });
    playButton.addEventListener("click",()=>controller?.play());
    pauseButton.addEventListener("click",()=>controller?.pause());
    resetButton.addEventListener("click",()=>{
      if(!controller)return;
      const p3Static=Boolean(contract?.rendering?.p3_static_preview?.enabled);
      controller.pause();controller.timeMs=p3Static?contract.rendering.p3_static_preview.time_ms:0;
      strength=1;strengthInput.value="100";$("water-strength-value").textContent="100%";
      waterEnabled=true;enabledInput.checked=true;
      debugRegion=false;debugRegionInput.checked=false;
      renderAt(controller.timeMs,false);
    });
    timelineInput.addEventListener("input",()=>{
      if(!controller)return;
      const selectedTime=Number(timelineInput.value);
      controller.pause();controller.seek(selectedTime);
    });
    lab.querySelectorAll("[data-water-frame]").forEach(button=>button.addEventListener("click",()=>{
      if(!controller)return;
      controller.pause();
      const frame=contract.timeline.frames.find(item=>item.id===button.dataset.waterFrame);
      if(frame)controller.seek(frame.ms);
    }));
    enabledInput.addEventListener("change",()=>{waterEnabled=enabledInput.checked;if(controller)renderAt(controller.timeMs,controller.playing);});
    debugRegionInput.addEventListener("change",()=>{debugRegion=debugRegionInput.checked;if(controller)renderAt(controller.timeMs,controller.playing);});
    strengthInput.addEventListener("input",()=>{
      strength=Number(strengthInput.value)/100;
      $("water-strength-value").textContent=strengthInput.value+"%";
      if(controller)renderAt(controller.timeMs,controller.playing);
    });
    retryButton.addEventListener("click",()=>init());
    reducedMotion.addEventListener?.("change",()=>{setMotionStatus();if(controller)renderAt(controller.timeMs,controller.playing);});
  }
  async function init() {
    if(loading)return;
    loading=true;setControlsDisabled(true);retryButton.hidden=true;loadError.hidden=true;setStatus("加载中");
    $("water-asset-status").textContent="SHA-256 校验中";
    try{
      const response=await timeoutFetch("../pages/recognition/web/candidates/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json?v=a-line-open-source-p3-1",{cache:"no-cache",credentials:"same-origin"});
      if(!response.ok)throw new Error("候选水光合同加载失败 HTTP "+response.status);
      const loaded=await response.json();
      if(loaded.contract_id!=="FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE"||
         loaded.task_id!=="FISHTRACE_SWOS_A_LINE_CANVAS_WATER_FIDELITY_V1"||
         loaded.contract_version!=="CANDIDATE_A_LINE_V1"||
         loaded.rendering?.caustics?.geometry_model!=="a_line_organic_face_patches_v2_grouped"||
         loaded.rendering?.p3_static_preview?.enabled!==true||
         loaded.rendering?.p3_static_preview?.algorithm!=="ethanfox_sampleCaustic_canvas2d"||
         loaded.rendering?.p3_static_preview?.time_ms!==1200||
         loaded.rendering?.p3_static_preview?.parameters?.scale!==1||
         loaded.rendering?.p3_static_preview?.parameters?.intensity!==1||
         loaded.rendering?.p3_static_preview?.parameters?.speed!==0||
         loaded.third_party_algorithm?.upstream_commit!=="de856eec62819b7c4176366db73b8fd37a1b1455"||
         loaded.third_party_algorithm?.source_files?.[0]?.git_blob_sha!=="5b5a67298a8631892d5153f134ae3a17a348ec6b"||
         loaded.third_party_algorithm?.source_files?.[1]?.git_blob_sha!=="441d39fb0aa8fd3f0960577713b0705c0423e598"||
         loaded.asset.path!=="design/system/fish_media_display_v1/assets/real_catch_source.jpg"||
         loaded.asset.width!==1152||loaded.asset.height!==1536||loaded.timeline.duration_ms!==2800||
         loaded.status!=="CANDIDATE_NOT_VISUALLY_APPROVED"||
         loaded.water_field?.coordinate_system!=="source_image_normalized_top_left_0_1"||
         loaded.water_field?.asset_sha256!==loaded.asset.sha256||
         loaded.rendering?.caustics?.patch_count!==18||
         loaded.rendering?.highlights?.segment_count!==24||
         loaded.timeline.frames.length!==8||
         loaded.timeline.frames.some(frame=>!frame.layers||typeof frame.layers.ambient!=="number"||typeof frame.layers.caustic!=="number"||typeof frame.layers.highlight!=="number")){
        throw new Error("候选 A 线合同、素材身份、区域或图层参数不符");
      }
      contract=loaded;
      canvas.width=1;canvas.height=1;
      await verifyAsset();
      scene=new WaterCausticsScene(contract);
      renderer=new CanvasWaterRenderer(stage,image,canvas,scene);
      controller=new WaterTimelineController(contract,renderAt);
      controller.timeMs=0;
      if(!resizeObserver){
        resizeObserver=new ResizeObserver(()=>{
          if(renderer){renderer.resize();renderer.render(controller?controller.timeMs:0,layerStrengthsAt(controller?controller.timeMs:0),true,controller?.playing||false);}
        });
        resizeObserver.observe(stage);
        window.addEventListener("resize",()=>{if(renderer)renderer.resize();},{passive:true});
      }
      strength=1;waterEnabled=true;debugRegion=false;
      const staticP3=contract.rendering.p3_static_preview.enabled;
      const initialTime=staticP3?contract.rendering.p3_static_preview.time_ms:0;
      timelineInput.max=String(contract.timeline.duration_ms);timelineInput.value=String(initialTime);
      strengthInput.value="100";enabledInput.checked=true;debugRegionInput.checked=false;
      $("water-strength-value").textContent="100%";
      controller.timeMs=initialTime;controller.playing=false;
      setMotionStatus();renderer.resize();renderAt(initialTime,false);
      setControlsDisabled(false);
      if(staticP3){
        playButton.disabled=true;pauseButton.disabled=true;timelineInput.disabled=true;
        lab.querySelectorAll("[data-water-frame]").forEach(button=>{button.disabled=true;});
        debugRegionInput.disabled=true;
        resetButton.disabled=false;enabledInput.disabled=false;strengthInput.disabled=false;
        lab.dataset.previewMode="P3_STATIC_1200MS";
        lab.dataset.visualApprovalStatus="CANDIDATE_NOT_VISUALLY_APPROVED";
      }
      setStatus("READY · Canvas 2D · DPR "+renderer.dpr);
      lab.dataset.rendererStatus="READY";
      lab.dataset.assetSha256=image.dataset.sha256;
      lab.dataset.durationMs=String(contract.timeline.duration_ms);
      lab.dataset.waterRegionVersion=contract.water_field.version;
      lab.dataset.waterRegionValidation="USER_SPECIFIED_GEOMETRY_BROWSER_CHECK_REQUIRED";
    }catch(error){
      setStatus("加载失败");$("water-asset-status").textContent="未通过 SHA-256 / 尺寸校验";
      loadError.textContent="水光工作区加载失败："+(error?.message||String(error))+"。请检查网络后重试。";
      loadError.hidden=false;retryButton.hidden=false;lab.dataset.rendererStatus="ERROR";lab.dataset.loadError=error?.message||String(error);
    }finally{loading=false;}
  }

  bindControls();
  init();
})();
