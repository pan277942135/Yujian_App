(() => {
  "use strict";
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
    }
    createPatches(random) {
      const sequence=zoneSequence(this.rendering.caustics);
      const angles=[-34,-27,-19,-7,15,23,31,-31,-25,-15,4,24,28,-38,-20,13,26,30].map(value=>value*Math.PI/180);
      return sequence.map((item,index)=>{
        const center=sampleZonePoint(this.zones[item.zone],random);
        const longW=this.rendering.caustics.lengths_w[0]+random()*(this.rendering.caustics.lengths_w[1]-this.rendering.caustics.lengths_w[0]);
        const ratioMin=Math.max(this.rendering.caustics.aspect_ratio[0],longW/.065);
        const ratioMax=Math.min(this.rendering.caustics.aspect_ratio[1],longW/.018);
        const ratio=ratioMin+random()*Math.max(.001,ratioMax-ratioMin);
        const shortW=clamp(longW/ratio,.018,.065);
        const group=item.group;
        const offsets=Array.from({length:13},()=>random()*2-1);
        const widthNoise=Array.from({length:13},()=>random()*2-1);
        const edgeNoise=Array.from({length:13},()=>random()*2-1);
        return {
          id:index+1,group,zone:item.zone,x:center.x,y:center.y,
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
        const range=large?spec.large_radius_w:spec.medium_radius_w;
        return {
          id:index+1,zone,center:sampleZonePoint(this.zones[zone],random),
          radiusW:range[0]+random()*(range[1]-range[0]),
          aspect:.48+random()*.35,angle:random()*Math.PI*2,
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
        return {
          id:index+1,zone,parentId:parent.id,
          positionU:.18+random()*.64,
          lengthW:spec.segment_length_w[0]+random()*(spec.segment_length_w[1]-spec.segment_length_w[0]),
          widthW:spec.segment_width_w[0]+random()*(spec.segment_width_w[1]-spec.segment_width_w[0]),
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
      const growth=smooth(timeMs,350,1200);
      const returnScale=smooth(timeMs,1200,2050);
      const exitScale=smooth(timeMs,2050,2800);
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
        const envelope=Math.pow(Math.max(0,Math.sin(Math.PI*u)),.46+zoneExit*.34);
        const wave=Math.sin(phase+u*patch.waveCycles+patch.phase)*.035;
        const bend=(patch.offsets[index]*patch.irregularity*.12+wave)*patch.longW*scale;
        const longitudinal=(u-.5)*patch.longW*scale;
        const x=center.x+tangent.x*longitudinal+normal.x*bend;
        const y=center.y+tangent.y*longitudinal+normal.y*bend;
        const broad=Math.sin(u*Math.PI*2+patch.phase+phase*.14)*.18;
        const fine=Math.sin(u*Math.PI*4.5+patch.phase*.7+phase*.10)*.10;
        const widthProfile=clamp(.74+broad+fine+patch.widthNoise[index]*patch.irregularity*.45,.34,1.20);
        const neck=1-.12*Math.exp(-Math.pow((u-.47)/.13,2));
        const half=patch.shortW*scale*envelope*widthProfile*neck*.5;
        const sideA=half*(1+patch.edgeNoise[index]*patch.irregularity*.40);
        const sideB=half*(1-patch.edgeNoise[index]*patch.irregularity*.38);
        centerline.push({x,y});
        positive.push({x:x+normal.x*sideA,y:y+normal.y*sideA});
        negative.push({x:x-normal.x*sideB,y:y-normal.y*sideB});
      }
      return {center,centerline,positive,negative,scale,angle,phase,appear,zoneExit};
    }
    drawAmbient(ctx,rect,timeMs,intensity,strengthValue,motionScale,zoneName) {
      const spec=this.rendering.ambient;
      for(const field of this.ambientFields){
        if(field.zone!==zoneName)continue;
        const zone=this.field.regions[field.zone];
        const zoneCoefficient=zone.strength_coefficient;
        const temporal=this.activation(field.zone,timeMs)*(1-this.exit(field.zone,timeMs,field.id*16));
        const alpha=Math.min(spec.p3_single_field_opacity_max,intensity*strengthValue*.30*field.weight)*zoneCoefficient*temporal;
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
        gradient.addColorStop(0,rgbaWithAlpha(spec.colors_rgba[field.colorIndex],.34));
        gradient.addColorStop(.34,rgbaWithAlpha(spec.colors_rgba[(field.colorIndex+1)%spec.colors_rgba.length],.28));
        gradient.addColorStop(.68,rgbaWithAlpha(spec.colors_rgba[(field.colorIndex+2)%spec.colors_rgba.length],.13));
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
        const alpha=Math.min(cap,intensity*strengthValue*.34*patch.weight)*zone.strength_coefficient*activation*zoneFade*zoneScale*reveal*pulse;
        if(alpha<.002||geometry.scale<.08)continue;
        const center=normalizedPoint([geometry.center.x,geometry.center.y],rect);
        const xScale=rect.width;
        const width=patch.shortW*geometry.scale*xScale*.62;
        const normalX=-Math.sin(geometry.angle),normalY=Math.cos(geometry.angle);
        const gradient=ctx.createLinearGradient(
          center.x-normalX*width,center.y-normalY*width,center.x+normalX*width,center.y+normalY*width
        );
        const color=spec.colors[patch.colorIndex];
        gradient.addColorStop(0,rgbaFromHex(color,0));
        gradient.addColorStop(.18,rgbaFromHex(color,.23));
        gradient.addColorStop(.38,rgbaFromHex(color,.61));
        gradient.addColorStop(.58,rgbaFromHex(color,.86));
        gradient.addColorStop(.80,rgbaFromHex(color,.42));
        gradient.addColorStop(1,rgbaFromHex(color,0));
        ctx.save();
        ctx.globalCompositeOperation="screen";
        ctx.globalAlpha=alpha;
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
      this.dpr=Math.max(1,window.devicePixelRatio||1);
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
      const angleBins=new Set(patches.map(patch=>Math.floor((((patch.angle%(Math.PI))+Math.PI)%(Math.PI))/(Math.PI)*8)));
      const nearHorizontal=patches.filter(patch=>Math.min(Math.abs(((patch.angle%(Math.PI))+Math.PI)%(Math.PI)),Math.PI-Math.abs(((patch.angle%(Math.PI))+Math.PI)%(Math.PI)))<=Math.PI/18).length/Math.max(1,patches.length);
      this.canvas.dataset.causticGeometryModel=this.scene.rendering.caustics.geometry_model;
      this.canvas.dataset.causticPatchCount=String(patches.length);
      this.canvas.dataset.causticDirectionBins=String(angleBins.size);
      this.canvas.dataset.causticNearHorizontalFraction=String(nearHorizontal);
      this.canvas.dataset.causticGroupCounts=JSON.stringify(patches.reduce((out,patch)=>{out[patch.group]=(out[patch.group]||0)+1;return out;},{}));
      this.canvas.dataset.highlightSegmentCount=String(this.scene.highlights.length);
      this.canvas.dataset.rendererStatus="READY";
      this.render(controller?controller.timeMs:0,layerStrengthsAt(controller?controller.timeMs:0),true,false);
      return true;
    }
    buildRegionMask() {
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
      const hasEnergy=layers.ambient>0||layers.caustic>0||layers.highlight>0;
      if(strengthEnabled&&hasEnergy&&waterEnabled&&this.contentRect.width){
        effect.save();
        effect.beginPath();
        effect.rect(this.contentRect.x,this.contentRect.y,this.contentRect.width,this.contentRect.height);
        effect.clip();
        effect.clip(this.regionPath,"nonzero");
        const motionScale=reducedMotion.matches?this.scene.contract.motion.reduced_motion_scale:1;
        this.scene.draw(effect,this.contentRect,timeMs,layers,strength,motionScale);
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
      this.canvas.dataset.activeIntensity=String(Math.round(strengthEnabled&&waterEnabled?layers.caustic*strength*100:0));
      this.canvas.dataset.layerStrengths=JSON.stringify(Object.fromEntries(Object.entries(layers).map(([key,value])=>[key,value*strength])));
      this.canvas.dataset.currentTime=String(Math.round(timeMs));
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
    const stageLabel=activeFrameLabel(timeMs);
    $("water-current-stage").textContent=stageLabel;
    $("water-current-time").textContent=Math.round(timeMs)+" ms";
    $("water-debug-stage").textContent=stageLabel;
    $("water-debug-time").textContent=Math.round(timeMs)+" ms / "+contract.timeline.duration_ms+" ms";
    $("water-debug-intensity").textContent=Math.round(amount*100)+"% caustic";
    $("water-progress-label").textContent=Math.round(timeMs/contract.timeline.duration_ms*100)+"%";
    $("water-play-state").textContent=playing?"播放中":"已暂停";
    timelineInput.value=String(Math.round(timeMs));
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
    updateReadout(timeMs,playing);
    if(renderer)renderer.render(timeMs,layerStrengthsAt(timeMs),true,playing);
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
      controller.reset();
      strength=1;strengthInput.value="100";$("water-strength-value").textContent="100%";
      waterEnabled=true;enabledInput.checked=true;
      debugRegion=false;debugRegionInput.checked=false;
      renderAt(0,false);
    });
    timelineInput.addEventListener("input",()=>{
      if(!controller)return;
      controller.pause();controller.seek(Number(timelineInput.value));
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
      const response=await timeoutFetch("../pages/recognition/web/candidates/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json?v=a-line-1",{cache:"no-cache",credentials:"same-origin"});
      if(!response.ok)throw new Error("候选水光合同加载失败 HTTP "+response.status);
      const loaded=await response.json();
      if(loaded.contract_id!=="FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE"||
         loaded.task_id!=="FISHTRACE_SWOS_A_LINE_CANVAS_WATER_FIDELITY_V1"||
         loaded.contract_version!=="CANDIDATE_A_LINE_V1"||
         loaded.rendering?.caustics?.geometry_model!=="a_line_organic_face_patches_v1"||
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
      timelineInput.max=String(contract.timeline.duration_ms);timelineInput.value="0";
      strengthInput.value="100";enabledInput.checked=true;debugRegionInput.checked=false;
      $("water-strength-value").textContent="100%";
      setMotionStatus();renderer.resize();renderAt(0,false);
      setControlsDisabled(false);setStatus("READY · Canvas 2D · DPR "+renderer.dpr);
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
