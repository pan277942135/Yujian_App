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
  function hexColor(hex) { return hex.replace("#",""); }
  function nativeBounds(points) {
    return points.reduce((bounds,point) => ({
      minX:Math.min(bounds.minX,point[0]),minY:Math.min(bounds.minY,point[1]),
      maxX:Math.max(bounds.maxX,point[0]),maxY:Math.max(bounds.maxY,point[1])
    }),{minX:Infinity,minY:Infinity,maxX:-Infinity,maxY:-Infinity});
  }
  function polygonArea(points) {
    let area = 0;
    for (let i=0;i<points.length;i+=1) {
      const current = points[i];
      const next = points[(i+1)%points.length];
      area += current[0]*next[1] - next[0]*current[1];
    }
    return Math.abs(area/2);
  }
  function makeRegionPath(regions,rect,asset) {
    const path = new Path2D();
    for (const region of regions) {
      for (let index=0;index<region.points.length;index+=1) {
        const point = region.points[index];
        const x = rect.x + point[0]/asset.width*rect.width;
        const y = rect.y + point[1]/asset.height*rect.height;
        if (index === 0) path.moveTo(x,y);
        else path.lineTo(x,y);
      }
      path.closePath();
    }
    return path;
  }

  function pointInsidePolygon(points,x,y) {
    let inside=false;
    for(let i=0,j=points.length-1;i<points.length;j=i++){
      const xi=points[i][0],yi=points[i][1],xj=points[j][0],yj=points[j][1];
      const crosses=(yi>y)!==(yj>y);
      if(crosses&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;
    }
    return inside;
  }
  function createCausticPatches(region,rendering,serial) {
    const bounds=nativeBounds(region.points);
    const width=bounds.maxX-bounds.minX;
    const height=bounds.maxY-bounds.minY;
    const area=polygonArea(region.points);
    const random=seeded(rendering.deterministic_seed+serial*104729);
    const count=clamp(Math.round(area/rendering.patch_density_target_area_source_px2),rendering.patch_min_count,rendering.patch_max_count);
    const centerX=(bounds.minX+bounds.maxX)/2,centerY=(bounds.minY+bounds.maxY)/2;
    const patches=[];
    const minimumSpacing=Math.sqrt(area/count)*.30;
    for(let index=0;index<count;index+=1){
      let chosen=null,best=null,bestScore=-1;
      for(let attempt=0;attempt<56;attempt+=1){
        const candidate={x:bounds.minX+random()*width,y:bounds.minY+random()*height};
        if(!pointInsidePolygon(region.points,candidate.x,candidate.y))continue;
        let nearest=Infinity;
        for(const existing of patches)nearest=Math.min(nearest,Math.hypot(candidate.x-existing.x,candidate.y-existing.y));
        const score=nearest+random()*2;
        if(score>bestScore){best=candidate;bestScore=score;}
        if(nearest>=minimumSpacing){chosen=candidate;break;}
      }
      const center=chosen||best||{x:centerX,y:centerY};
      const direction=random()*Math.PI*2;
      const radialX=center.x-centerX,radialY=center.y-centerY;
      const radialLength=Math.hypot(radialX,radialY)||1;
      const segments=rendering.patch_curve_segments;
      const centerlineOffsets=Array.from({length:segments+1},()=>random()*2-1);
      const widthOffsets=Array.from({length:segments+1},()=>random()*2-1);
      const leftEdgeOffsets=Array.from({length:segments+1},()=>random()*2-1);
      const rightEdgeOffsets=Array.from({length:segments+1},()=>random()*2-1);
      patches.push({
        id:serial*100+index,
        x:center.x,y:center.y,
        angle:direction,
        length:rendering.patch_length_min_source_px+random()*(rendering.patch_length_max_source_px-rendering.patch_length_min_source_px),
        width:rendering.patch_width_min_source_px+random()*(rendering.patch_width_max_source_px-rendering.patch_width_min_source_px),
        bend:rendering.patch_center_bend_min_source_px+random()*(rendering.patch_center_bend_max_source_px-rendering.patch_center_bend_min_source_px),
        phase:random()*Math.PI*2,
        phase2:random()*Math.PI*2,
        speed:.72+random()*.56,
        waveCycles:2.8+random()*2.7,
        deformationAmplitude:rendering.patch_deform_amplitude_source_px*(.55+random()*.75),
        widthVariation:rendering.patch_width_variation_fraction*(.55+random()*.8),
        edgeVariation:rendering.patch_edge_variation_fraction*(.55+random()*.8),
        neckU:.30+random()*.42,
        neckDepth:rendering.patch_neck_depth_fraction*(.35+random()*.9),
        weight:.62+random()*.38,
        color:Math.floor(random()*rendering.caustic_colors.length),
        radialX:radialX/radialLength,radialY:radialY/radialLength,
        peripheral:clamp(radialLength/(Math.hypot(width,height)*.43),0,1),
        revealDelayMs:random()*rendering.patch_reveal_delay_max_ms,
        exitDelayMs:random()*rendering.patch_exit_delay_max_ms,
        highlightScore:random(),
        centerlineOffsets,widthOffsets,leftEdgeOffsets,rightEdgeOffsets
      });
    }
    return {id:region.id,bounds,width,height,centerX,centerY,area,patches};
  }
  function rgbaFromHex(hex,alpha) {
    const value=hex.replace("#","");
    const number=parseInt(value,16);
    return "rgba("+((number>>16)&255)+","+((number>>8)&255)+","+(number&255)+","+alpha+")";
  }
  function traceSmoothPoints(context,points,rect,asset,startNewSubpath=true) {
    const mapped=points.map(point=>({x:rect.x+point.x/asset.width*rect.width,y:rect.y+point.y/asset.height*rect.height}));
    if(!mapped.length)return;
    if(startNewSubpath)context.moveTo(mapped[0].x,mapped[0].y);else context.lineTo(mapped[0].x,mapped[0].y);
    for(let index=1;index<mapped.length-1;index+=1){
      const point=mapped[index],next=mapped[index+1];
      context.quadraticCurveTo(point.x,point.y,(point.x+next.x)/2,(point.y+next.y)/2);
    }
    if(mapped.length>1)context.lineTo(mapped[mapped.length-1].x,mapped[mapped.length-1].y);
  }

  class WaterCausticsScene {
    constructor(value) {
      this.contract=value;
      this.rendering=value.rendering;
      this.regions=value.water_region.polygons.map((region,index)=>createCausticPatches(region,this.rendering,index+1));
      this.waterRegionPath=null;
    }
    setDisplayGeometry(rect) {
      this.waterRegionPath=makeRegionPath(this.contract.water_region.polygons,rect,this.contract.asset);
    }
    patchGeometry(region,patch,timeMs,motionScale,recollection) {
      const rendering=this.rendering;
      const grow=smooth(timeMs,rendering.patch_growth_start_ms,rendering.patch_growth_peak_ms);
      const spread=smooth(timeMs,rendering.patch_spread_start_ms,rendering.patch_spread_end_ms);
      const reveal=smooth(timeMs,rendering.patch_reveal_start_ms+patch.revealDelayMs,rendering.patch_reveal_end_ms+patch.revealDelayMs);
      const exit=smooth(timeMs,rendering.patch_exit_start_ms+patch.exitDelayMs,rendering.patch_exit_end_ms);
      const rawScale=rendering.patch_growth_min_scale+(1-rendering.patch_growth_min_scale)*grow+rendering.patch_midstage_spread_fraction*spread;
      const scale=rawScale*(1-recollection*rendering.patch_recollection_shrink_fraction)*(1-exit*rendering.patch_exit_shrink_fraction*(.4+patch.peripheral*.6));
      const outward=grow*.36+spread*.64;
      const radialTravel=Math.max(region.width,region.height)*(
        rendering.patch_spread_distance_fraction*outward-
        rendering.patch_recollection_distance_fraction*recollection-
        rendering.patch_exit_distance_fraction*exit
      )*motionScale;
      const centerX=patch.x+patch.radialX*radialTravel;
      const centerY=patch.y+patch.radialY*radialTravel;
      const phase=patch.phase+timeMs/1000*rendering.patch_deform_rate_rad_s*patch.speed*motionScale;
      const movementScale=motionScale*(.20+.80*Math.max(grow,spread))*(1-recollection*.42)*(1-exit*.58);
      const angle=patch.angle+Math.sin(phase*.34+patch.phase2)*rendering.patch_angle_drift_rad*movementScale;
      const tangentX=Math.cos(angle),tangentY=Math.sin(angle);
      const normalX=-tangentY,normalY=tangentX;
      const points=[],positive=[],negative=[];
      for(let index=0;index<patch.centerlineOffsets.length;index+=1){
        const u=index/(patch.centerlineOffsets.length-1);
        const envelope=Math.pow(Math.max(0,Math.sin(Math.PI*u)),.58);
        const temporalBend=Math.sin(phase+u*patch.waveCycles+patch.phase2)*patch.deformationAmplitude*movementScale;
        const bend=(patch.centerlineOffsets[index]*patch.bend+temporalBend)*scale;
        const longitudinal=(u-.5)*patch.length*scale;
        const x=centerX+tangentX*longitudinal+normalX*bend;
        const y=centerY+tangentY*longitudinal+normalY*bend;
        const broad=Math.sin(u*Math.PI*2+patch.phase2+phase*.18)*.15;
        const fine=Math.sin(u*Math.PI*5+patch.phase+phase*.11)*.10;
        const widthShape=clamp(.72+broad+fine+patch.widthOffsets[index]*patch.widthVariation,.30,1.18);
        const neck=1-patch.neckDepth*Math.exp(-Math.pow((u-patch.neckU)/.105,2));
        const half=patch.width*scale*envelope*widthShape*neck*.5;
        const sideA=half*(1+patch.leftEdgeOffsets[index]*patch.edgeVariation);
        const sideB=half*(1+patch.rightEdgeOffsets[index]*patch.edgeVariation);
        const point={x,y};
        points.push(point);
        positive.push({x:x+normalX*sideA,y:y+normalY*sideA});
        negative.push({x:x-normalX*sideB,y:y-normalY*sideB});
      }
      return {points,positive,negative,centerX,centerY,normalX,normalY,scale,reveal,exit,phase};
    }
    drawAmbient(context,region,rect,timeMs,intensity,motionScale,exit) {
      const rendering=this.rendering;
      const bounds=region.bounds;
      const x=rect.x+bounds.minX/this.contract.asset.width*rect.width;
      const y=rect.y+bounds.minY/this.contract.asset.height*rect.height;
      const width=region.width/this.contract.asset.width*rect.width;
      const height=region.height/this.contract.asset.height*rect.height;
      const drift=smooth(timeMs,rendering.patch_growth_start_ms,rendering.patch_growth_peak_ms)*height*.025*motionScale;
      const centerX=x+width*(.46+.025*Math.sin(timeMs/1000*.32)*motionScale);
      const centerY=y+height*.67-drift;
      const exitDamping=1-exit*.62;
      context.save();
      context.globalCompositeOperation="screen";
      context.globalAlpha=intensity*rendering.ambient_alpha*exitDamping;
      const gradient=context.createLinearGradient(x,y+height,x+width*.18,y);
      gradient.addColorStop(0,rendering.ambient_colors[0]);
      gradient.addColorStop(.34,rendering.ambient_colors[1]);
      gradient.addColorStop(.72,rendering.ambient_colors[2]);
      gradient.addColorStop(1,rendering.ambient_colors[3]);
      context.fillStyle=gradient;
      context.fillRect(x,y,width,height);
      context.globalAlpha=intensity*rendering.ambient_bloom_alpha*exitDamping;
      const bloomRadius=Math.max(width,height)*(.78-exit*.18);
      const bloom=context.createRadialGradient(centerX,centerY,0,centerX,centerY,bloomRadius);
      bloom.addColorStop(0,rendering.ambient_bloom_colors[0]);
      bloom.addColorStop(.52,rendering.ambient_bloom_colors[1]);
      bloom.addColorStop(1,"rgba(221,237,218,0)");
      context.fillStyle=bloom;
      context.fillRect(x,y,width,height);
      context.restore();
    }
    drawOrganicCaustics(context,region,rect,intensity,patchFrames) {
      const rendering=this.rendering,asset=this.contract.asset;
      const xScale=rect.width/asset.width;
      for(const item of patchFrames){
        const {patch,geometry}=item;
        const pulse=.94+.06*Math.sin(geometry.phase*.42+patch.phase2);
        const exitWeight=1-geometry.exit*rendering.patch_exit_alpha_fraction*(.35+patch.peripheral*.65);
        const alpha=clamp(intensity*rendering.caustic_patch_alpha*patch.weight*pulse*geometry.reveal*exitWeight,0,rendering.max_layer_alpha);
        if(alpha<.003||geometry.scale<.08)continue;
        const centerX=rect.x+geometry.centerX/asset.width*rect.width;
        const centerY=rect.y+geometry.centerY/asset.height*rect.height;
        const halfDisplay=patch.width*geometry.scale*xScale*.62;
        const gradient=context.createLinearGradient(
          centerX-geometry.normalX*halfDisplay,centerY-geometry.normalY*halfDisplay,
          centerX+geometry.normalX*halfDisplay,centerY+geometry.normalY*halfDisplay
        );
        const tint=rendering.caustic_colors[patch.color];
        gradient.addColorStop(0,rgbaFromHex(tint,0));
        gradient.addColorStop(.22,rgbaFromHex(tint,rendering.patch_edge_alpha));
        gradient.addColorStop(.50,rgbaFromHex(tint,rendering.patch_core_alpha));
        gradient.addColorStop(.78,rgbaFromHex(tint,rendering.patch_edge_alpha*.92));
        gradient.addColorStop(1,rgbaFromHex(tint,0));
        context.save();
        context.globalCompositeOperation="screen";
        context.globalAlpha=alpha;
        context.beginPath();
        traceSmoothPoints(context,geometry.positive,rect,asset);
        const last=geometry.negative[geometry.negative.length-1];
        context.lineTo(rect.x+last.x/asset.width*rect.width,rect.y+last.y/asset.height*rect.height);
        traceSmoothPoints(context,geometry.negative.slice().reverse(),rect,asset,false);
        context.closePath();
        context.fillStyle=gradient;
        context.fill();
        context.restore();
      }
    }
    drawHighlights(context,rect,intensity,patchFrames) {
      const rendering=this.rendering,asset=this.contract.asset;
      for(const item of patchFrames){
        const {patch,geometry}=item;
        if(patch.highlightScore<rendering.highlight_patch_weight_threshold)continue;
        const exitWeight=1-geometry.exit*(rendering.patch_exit_alpha_fraction+.08*patch.peripheral);
        const alpha=clamp(intensity*rendering.refraction_alpha*patch.weight*geometry.reveal*exitWeight*.64,0,rendering.max_layer_alpha*.60);
        if(alpha<.003||geometry.scale<.08)continue;
        const start=Math.max(1,Math.floor(geometry.points.length*.20));
        const end=Math.min(geometry.points.length-1,Math.ceil(geometry.points.length*.78));
        context.save();
        context.globalCompositeOperation="screen";
        context.globalAlpha=alpha;
        context.strokeStyle=rendering.refraction_colors[patch.color%rendering.refraction_colors.length];
        context.lineWidth=rendering.refraction_width_source_px/asset.width*rect.width;
        context.lineCap="round";
        context.lineJoin="round";
        context.beginPath();
        traceSmoothPoints(context,geometry.points.slice(start,end+1),rect,asset);
        context.stroke();
        context.restore();
      }
    }
    draw(context,rect,timeMs,baselineIntensity,strengthValue,motionScale) {
      const rendering=this.rendering;
      const intensity=baselineIntensity*strengthValue;
      const recollection=smooth(timeMs,rendering.patch_recollection_start_ms,rendering.patch_recollection_end_ms);
      const exit=smooth(timeMs,rendering.patch_exit_start_ms,rendering.patch_exit_end_ms);
      for(const region of this.regions){
        context.save();
        this.drawAmbient(context,region,rect,timeMs,intensity,motionScale,exit);
        const patchFrames=region.patches.map(patch=>({patch,geometry:this.patchGeometry(region,patch,timeMs,motionScale,recollection)}));
        this.drawOrganicCaustics(context,region,rect,intensity,patchFrames);
        this.drawHighlights(context,rect,intensity,patchFrames);
        context.restore();
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
      if (!bounds.width || !bounds.height || !this.image.naturalWidth || !this.image.naturalHeight) return false;
      this.width=bounds.width;
      this.height=bounds.height;
      this.dpr=Math.max(1,window.devicePixelRatio||1);
      const backingWidth=Math.max(1,Math.round(this.width*this.dpr));
      const backingHeight=Math.max(1,Math.round(this.height*this.dpr));
      for (const surface of [this.canvas,this.effectsCanvas,this.maskCanvas,this.softMaskCanvas]) {
        if (surface.width!==backingWidth) surface.width=backingWidth;
        if (surface.height!==backingHeight) surface.height=backingHeight;
      }
      const fit=getComputedStyle(this.image).objectFit;
      if (fit!=="contain") throw new Error("照片 object-fit 必须为 contain");
      const scale=Math.min(this.width/this.image.naturalWidth,this.height/this.image.naturalHeight);
      const contentWidth=this.image.naturalWidth*scale;
      const contentHeight=this.image.naturalHeight*scale;
      const objectPosition=getComputedStyle(this.image).objectPosition;
      const position=objectPosition.split(/\s+/);
      const px=position[0]==="left"?0:position[0]==="right"?1:parsePosition(position[0]);
      const py=position[1]==="top"?0:position[1]==="bottom"?1:parsePosition(position[1]);
      this.contentRect={x:(this.width-contentWidth)*px,y:(this.height-contentHeight)*py,width:contentWidth,height:contentHeight};
      this.regionPath=makeRegionPath(this.scene.contract.water_region.polygons,this.contentRect,this.scene.contract.asset);
      this.scene.setDisplayGeometry(this.contentRect);
      this.buildRegionMask();
      this.canvas.dataset.contentRect=JSON.stringify(this.contentRect);
      this.canvas.dataset.dpr=String(this.dpr);
      this.canvas.dataset.waterRegionVersion=this.scene.contract.water_region.version;
      this.canvas.dataset.waterRegionPolygonCount=String(this.scene.contract.water_region.polygons.length);
      this.canvas.dataset.waterRegionSourceAreaPx=String(Math.round(this.scene.contract.water_region.polygons.reduce((sum,region)=>sum+polygonArea(region.points),0)));
      this.canvas.dataset.waterRegionPolygons=JSON.stringify(this.scene.contract.water_region.polygons.map(region=>({id:region.id,points:region.points})));
      this.canvas.dataset.sourceDimensions=JSON.stringify({width:this.scene.contract.asset.width,height:this.scene.contract.asset.height});
      const patches=this.scene.regions.flatMap(region=>region.patches);
      const angleBins=new Set(patches.map(patch=>Math.floor((((patch.angle%Math.PI)+Math.PI)%Math.PI)/Math.PI*8)));
      const nearHorizontal=patches.filter(patch=>{const angle=((patch.angle%Math.PI)+Math.PI)%Math.PI;return Math.min(angle,Math.PI-angle)<.30;}).length/Math.max(1,patches.length);
      this.canvas.dataset.causticGeometryModel=this.scene.rendering.caustic_geometry_model;
      this.canvas.dataset.causticPatchCount=String(patches.length);
      this.canvas.dataset.causticDirectionBins=String(angleBins.size);
      this.canvas.dataset.causticNearHorizontalFraction=String(nearHorizontal);
      this.canvas.dataset.rendererStatus="READY";
      this.render(controller?controller.timeMs:0,0,true,false);
      return true;
    }
    buildRegionMask() {
      const dpr=this.dpr;
      const hard=this.maskContext;
      const soft=this.softMaskContext;
      hard.setTransform(1,0,0,1,0,0);
      soft.setTransform(1,0,0,1,0,0);
      hard.clearRect(0,0,this.maskCanvas.width,this.maskCanvas.height);
      soft.clearRect(0,0,this.softMaskCanvas.width,this.softMaskCanvas.height);
      hard.setTransform(dpr,0,0,dpr,0,0);
      soft.setTransform(dpr,0,0,dpr,0,0);
      hard.fillStyle="#fff";
      hard.fill(this.regionPath,"nonzero");
      const sourceScale=this.contentRect.width/this.scene.contract.asset.width;
      soft.filter="blur("+(this.scene.contract.water_region.edge_fade_source_px*sourceScale)+"px)";
      soft.fillStyle="#fff";
      soft.fill(this.regionPath,"nonzero");
      soft.filter="none";
      hard.globalCompositeOperation="destination-in";
      hard.drawImage(this.softMaskCanvas,0,0,this.width,this.height);
      hard.globalCompositeOperation="source-over";
      hard.setTransform(1,0,0,1,0,0);
      soft.setTransform(1,0,0,1,0,0);
    }
    render(timeMs,intensity,strengthEnabled,playing) {
      if (!this.width||!this.height) this.resize();
      if (!this.width||!this.height) return;
      const started=performance.now();
      if (playing&&this.previousRenderStart===null) this.performanceSamples=[];
      const interval=this.previousRenderStart===null?null:started-this.previousRenderStart;
      if (playing) this.previousRenderStart=started;
      const effect=this.effectsContext;
      effect.setTransform(this.dpr,0,0,this.dpr,0,0);
      effect.clearRect(0,0,this.width,this.height);
      effect.globalAlpha=1;
      effect.globalCompositeOperation="source-over";
      effect.filter="none";
      if (strengthEnabled&&intensity>0&&waterEnabled&&this.contentRect.width) {
        effect.save();
        effect.beginPath();
        effect.rect(this.contentRect.x,this.contentRect.y,this.contentRect.width,this.contentRect.height);
        effect.clip();
        effect.clip(this.regionPath,"nonzero");
        const motionScale=reducedMotion.matches?this.scene.contract.motion.reduced_motion_scale:1;
        this.scene.draw(effect,this.contentRect,timeMs,intensity,strength,motionScale);
        effect.restore();
        effect.setTransform(1,0,0,1,0,0);
        effect.globalCompositeOperation="destination-in";
        effect.drawImage(this.maskCanvas,0,0);
        effect.globalCompositeOperation="source-over";
      } else {
        effect.setTransform(1,0,0,1,0,0);
      }
      const output=this.context;
      output.setTransform(1,0,0,1,0,0);
      output.clearRect(0,0,this.canvas.width,this.canvas.height);
      output.setTransform(this.dpr,0,0,this.dpr,0,0);
      output.globalCompositeOperation="source-over";
      output.globalAlpha=1;
      output.drawImage(this.effectsCanvas,0,0,this.width,this.height);
      if (debugRegion) this.drawDebugRegion(output);
      output.globalCompositeOperation="source-over";
      output.globalAlpha=1;
      output.setTransform(1,0,0,1,0,0);
      const motionScale=reducedMotion.matches?this.scene.contract.motion.reduced_motion_scale:1;
      this.canvas.dataset.activeIntensity=String(Math.round(strengthEnabled&&waterEnabled?intensity*strength*100:0));
      this.canvas.dataset.currentTime=String(Math.round(timeMs));
      this.canvas.dataset.waterOn=String(waterEnabled);
      this.canvas.dataset.motionScale=String(motionScale);
      this.canvas.dataset.debugRegion=String(debugRegion);
      this.canvas.dataset.renderDurationMs=(performance.now()-started).toFixed(3);
      if (playing) {
        if (interval!==null) this.performanceSamples.push({frame_interval_ms:interval,draw_ms:Number(this.canvas.dataset.renderDurationMs)});
      } else if (this.previousRenderStart!==null) {
        this.previousRenderStart=null;
        this.canvas.dataset.performanceSampleCount=String(this.performanceSamples.length);
        this.canvas.dataset.performanceSamples=JSON.stringify(this.performanceSamples);
      }
    }
    drawDebugRegion(context) {
      context.save();
      context.setTransform(this.dpr,0,0,this.dpr,0,0);
      context.globalCompositeOperation="source-over";
      context.fillStyle="rgba(50,169,148,.14)";
      context.fill(this.regionPath,"nonzero");
      context.strokeStyle="rgba(231,104,52,.96)";
      context.lineWidth=1.8;
      context.setLineDash([7,4]);
      context.stroke(this.regionPath);
      context.restore();
    }
    inspect() { return {width:this.width,height:this.height,dpr:this.dpr,contentRect:this.contentRect}; }
  }
  function parsePosition(value) {
    if (!value||value==="center") return .5;
    if (value.endsWith("%")) return clamp(parseFloat(value)/100,0,1);
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
        if (document.hidden) { this.cancelFrame();this.lastTick=null; }
        else if (this.playing) { this.lastTick=null;this.rafId=requestAnimationFrame(this.tick); }
      });
    }
    cancelFrame() { if (this.rafId) cancelAnimationFrame(this.rafId);this.rafId=0; }
    emit() { this.onFrame(this.timeMs,this.playing); }
    seek(timeMs) { this.timeMs=clamp(timeMs,0,this.duration);this.emit(); }
    play() {
      if (this.playing||this.timeMs>=this.duration) return;
      this.playing=true;this.lastTick=null;this.emit();
      if (!document.hidden) this.rafId=requestAnimationFrame(this.tick);
    }
    pause() { this.playing=false;this.lastTick=null;this.cancelFrame();this.emit(); }
    reset() { this.pause();this.seek(0); }
    tick(now) {
      if (!this.playing||document.hidden) return;
      if (this.lastTick===null) this.lastTick=now;
      else { this.timeMs+=Math.min(50,Math.max(0,now-this.lastTick));this.lastTick=now; }
      if (this.timeMs>=this.duration) { this.timeMs=this.duration;this.playing=false;this.cancelFrame(); }
      this.emit();
      if (this.playing) this.rafId=requestAnimationFrame(this.tick);
      else this.rafId=0;
    }
  }

  function percentAt(timeMs) {
    const frames=contract.timeline.frames;
    for (let i=0;i<frames.length-1;i+=1) {
      const from=frames[i],to=frames[i+1];
      if (timeMs<=to.ms) {
        const p=smooth(timeMs,from.ms,to.ms);
        return (from.intensity_percent+(to.intensity_percent-from.intensity_percent)*p)/100;
      }
    }
    return frames[frames.length-1].intensity_percent/100;
  }
  function activeFrameLabel(timeMs) {
    const frames=contract.timeline.frames;
    const exact=frames.find(frame=>Math.abs(frame.ms-timeMs)<.5);
    if (exact) return exact.id;
    for (let i=0;i<frames.length-1;i+=1) if (timeMs<frames[i+1].ms) return frames[i].id+" → "+frames[i+1].id;
    return "P7";
  }
  function updateReadout(timeMs,playing) {
    const amount=percentAt(timeMs)*strength;
    const stageLabel=activeFrameLabel(timeMs);
    $("water-current-stage").textContent=stageLabel;
    $("water-current-time").textContent=Math.round(timeMs)+" ms";
    $("water-debug-stage").textContent=stageLabel;
    $("water-debug-time").textContent=Math.round(timeMs)+" ms / "+contract.timeline.duration_ms+" ms";
    $("water-debug-intensity").textContent=Math.round(amount*100)+"%";
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
    if (renderer) renderer.render(timeMs,percentAt(timeMs),true,playing);
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
        root.querySelectorAll("[data-workspace-panel]").forEach(panel=>{ panel.hidden=panel.dataset.workspacePanel!==view; });
        if (view==="canvas-water"&&renderer) requestAnimationFrame(()=>{
          renderer.resize();renderer.render(controller.timeMs,percentAt(controller.timeMs),true,controller.playing);
        });
      });
    });
    playButton.addEventListener("click",()=>controller?.play());
    pauseButton.addEventListener("click",()=>controller?.pause());
    resetButton.addEventListener("click",()=>{
      if (!controller) return;
      controller.reset();
      strength=1;strengthInput.value="100";$("water-strength-value").textContent="100%";
      waterEnabled=true;enabledInput.checked=true;
      debugRegion=false;debugRegionInput.checked=false;
      renderAt(0,false);
    });
    timelineInput.addEventListener("input",()=>{
      if (!controller) return;
      const requestedTimeMs=Number(timelineInput.value);
      controller.pause();
      controller.seek(requestedTimeMs);
    });
    lab.querySelectorAll("[data-water-frame]").forEach(button=>button.addEventListener("click",()=>{
      if (!controller) return;
      controller.pause();
      const frame=contract.timeline.frames.find(item=>item.id===button.dataset.waterFrame);
      if (frame) controller.seek(frame.ms);
    }));
    enabledInput.addEventListener("change",()=>{ waterEnabled=enabledInput.checked;if (controller) renderAt(controller.timeMs,controller.playing); });
    debugRegionInput.addEventListener("change",()=>{ debugRegion=debugRegionInput.checked;if (controller) renderAt(controller.timeMs,controller.playing); });
    strengthInput.addEventListener("input",()=>{
      strength=Number(strengthInput.value)/100;
      $("water-strength-value").textContent=strengthInput.value+"%";
      if (controller) renderAt(controller.timeMs,controller.playing);
    });
    retryButton.addEventListener("click",()=>init());
    reducedMotion.addEventListener?.("change",()=>{ setMotionStatus();if (controller) renderAt(controller.timeMs,controller.playing); });
  }
  async function init() {
    if (loading) return;
    loading=true;setControlsDisabled(true);retryButton.hidden=true;loadError.hidden=true;setStatus("加载中");
    $("water-asset-status").textContent="SHA-256 校验中";
    try {
      const response=await timeoutFetch("../pages/recognition/web/candidates/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json?v=5",{cache:"no-cache",credentials:"same-origin"});
      if (!response.ok) throw new Error("候选水光合同加载失败 HTTP "+response.status);
      const loaded=await response.json();
      if (loaded.contract_id!=="FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE"||
          loaded.contract_version!=="CANDIDATE_V5"||
          loaded.rendering?.caustic_geometry_model!=="organic_anisotropic_face_patches_v5"||
          loaded.asset.path!=="design/system/fish_media_display_v1/assets/real_catch_source.jpg"||
          loaded.asset.width!==1152||loaded.asset.height!==1536||loaded.timeline.duration_ms!==2800||
          loaded.status!=="CANDIDATE_NOT_VISUALLY_APPROVED"||
          loaded.water_region?.coordinate_system!=="source_image_pixels_top_left"||
          loaded.water_region?.asset_sha256!==loaded.asset.sha256||
          !Array.isArray(loaded.water_region?.polygons)||loaded.water_region.polygons.length<2) {
        throw new Error("候选水光合同、素材身份或 water_region 几何不符");
      }
      contract=loaded;
      canvas.width=1;canvas.height=1;
      await verifyAsset();
      scene=new WaterCausticsScene(contract);
      renderer=new CanvasWaterRenderer(stage,image,canvas,scene);
      controller=new WaterTimelineController(contract,renderAt);
      controller.timeMs=0;
      if (!resizeObserver) {
        resizeObserver=new ResizeObserver(()=>{
          if (renderer) { renderer.resize();renderer.render(controller?controller.timeMs:0,controller?percentAt(controller.timeMs):0,true,controller?.playing||false); }
        });
        resizeObserver.observe(stage);
        window.addEventListener("resize",()=>{ if (renderer) renderer.resize(); },{passive:true});
      }
      strength=1;waterEnabled=true;debugRegion=false;
      timelineInput.max=String(contract.timeline.duration_ms);timelineInput.value="0";
      strengthInput.value="100";enabledInput.checked=true;debugRegionInput.checked=false;
      $("water-strength-value").textContent="100%";
      setMotionStatus();renderer.resize();renderAt(0,false);
      setControlsDisabled(false);setStatus("READY · Canvas 2D · DPR "+renderer.dpr);
      lab.dataset.rendererStatus="READY";lab.dataset.assetSha256=image.dataset.sha256;lab.dataset.durationMs=String(contract.timeline.duration_ms);
      lab.dataset.waterRegionVersion=contract.water_region.version;lab.dataset.waterRegionValidation=contract.water_region.validation.status;
    } catch (error) {
      setStatus("加载失败");$("water-asset-status").textContent="未通过 SHA-256 / 尺寸校验";
      loadError.textContent="水光工作区加载失败："+(error?.message||String(error))+"。请检查网络后重试。";
      loadError.hidden=false;retryButton.hidden=false;lab.dataset.rendererStatus="ERROR";lab.dataset.loadError=error?.message||String(error);
    } finally { loading=false; }
  }

  bindControls();
  init();
})();
