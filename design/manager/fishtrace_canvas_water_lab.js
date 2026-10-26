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
      this.canvas.dataset.activeIntensity=String(Math.round(strengthEnabled&&waterEnabled?layers.c