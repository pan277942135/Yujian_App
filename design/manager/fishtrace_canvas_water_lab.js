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

  function createMesh(region,rendering,serial) {
    const bounds = nativeBounds(region.points);
    const width = bounds.maxX-bounds.minX;
    const height = bounds.maxY-bounds.minY;
    const cols = clamp(Math.round(width/rendering.mesh_cell_width_source_px),3,10);
    const rows = clamp(Math.round(height/rendering.mesh_cell_height_source_px),5,12);
    const random = seeded(rendering.deterministic_seed + serial*104729);
    const nodes = [];
    for (let row=0;row<=rows;row+=1) {
      for (let col=0;col<=cols;col+=1) {
        const u = col/cols;
        const v = row/rows;
        const pitchX = width/cols;
        const pitchY = height/rows;
        const edge = col===0 || col===cols || row===0 || row===rows;
        nodes.push({
          id:row*(cols+1)+col,row,col,u,v,
          x:bounds.minX + u*width + (edge?0:(random()-.5)*pitchX*rendering.node_jitter_fraction),
          y:bounds.minY + v*height + (edge?0:(random()-.5)*pitchY*rendering.node_jitter_fraction),
          phase:random()*Math.PI*2,
          motionX:rendering.node_motion_x_source_px*(0.55+random()*0.9),
          motionY:rendering.node_motion_y_source_px*(0.55+random()*0.9)
        });
      }
    }
    const cells = [];
    for (let row=0;row<rows;row+=1) {
      for (let col=0;col<cols;col+=1) {
        const a=row*(cols+1)+col,b=a+1,c=b+cols+1,d=a+cols+1;
        cells.push({nodes:[a,b,c,d],row,col,u:(col+.5)/cols,v:(row+.5)/rows,weight:random(),color:Math.floor(random()*rendering.caustic_colors.length),phase:random()*Math.PI*2});
      }
    }
    const edgeMap = new Map();
    for (const cell of cells) {
      for (let side=0;side<4;side+=1) {
        const a=cell.nodes[side],b=cell.nodes[(side+1)%4];
        const key=Math.min(a,b)+":"+Math.max(a,b);
        if (!edgeMap.has(key)) edgeMap.set(key,{a:Math.min(a,b),b:Math.max(a,b),weight:random(),phase:random()*Math.PI*2});
      }
    }
    return {id:region.id,bounds,width,height,nodes,cells,edges:[...edgeMap.values()]};
  }
  function edgeControlPoints(a,b,edge,time,rendering) {
    const dx=b.x-a.x,dy=b.y-a.y;
    const length=Math.max(1,Math.hypot(dx,dy));
    const wave=Math.sin(time/1000*rendering.edge_wave_rate_rad_s+edge.phase);
    const bend=wave*Math.min(rendering.edge_bend_max_source_px,length*rendering.edge_bend_fraction);
    const nx=-dy/length,ny=dx/length;
    return [
      {x:a.x+dx/3+nx*bend,y:a.y+dy/3+ny*bend},
      {x:a.x+dx*2/3+nx*bend,y:a.y+dy*2/3+ny*bend}
    ];
  }
  function traceGridEdge(context,nodes,fromId,toId,edge,time,rendering,rect,asset) {
    const forward=fromId<toId;
    const first=nodes[forward?fromId:toId];
    const last=nodes[forward?toId:fromId];
    const [control1,control2]=edgeControlPoints(first,last,edge,time,rendering);
    const toDisplay=point=>({x:rect.x+point.x/asset.width*rect.width,y:rect.y+point.y/asset.height*rect.height});
    const c1=toDisplay(forward?control1:control2);
    const c2=toDisplay(forward?control2:control1);
    const end=toDisplay(nodes[toId]);
    context.bezierCurveTo(c1.x,c1.y,c2.x,c2.y,end.x,end.y);
  }
  function edgeRecord(region,a,b) {
    const low=Math.min(a,b),high=Math.max(a,b);
    return region.edgeMap.get(low+":"+high);
  }

  class WaterCausticsScene {
    constructor(value) {
      this.contract=value;
      this.rendering=value.rendering;
      this.regions=value.water_region.polygons.map((region,index)=>createMesh(region,this.rendering,index+1));
      for (const region of this.regions) {
        region.edgeMap=new Map(region.edges.map(edge=>[edge.a+":"+edge.b,edge]));
      }
      this.waterRegionPath=null;
    }
    setDisplayGeometry(rect) {
      this.waterRegionPath=makeRegionPath(this.contract.water_region.polygons,rect,this.contract.asset);
    }
    nodePositions(region,timeMs,motionScale,expansion) {
      const motion=this.contract.motion;
      const t=timeMs/1000;
      const centerX=(region.bounds.minX+region.bounds.maxX)/2;
      const centerY=(region.bounds.minY+region.bounds.maxY)/2;
      const recollection=smooth(timeMs,motion.recollection_start_ms,motion.recollection_end_ms)*motionScale;
      const returnScale=1-recollection*motion.recollection_scale_reduction;
      return region.nodes.map(node=>{
        const swayX=Math.sin(t*motion.sway_rate_rad_s+node.phase)*node.motionX*motionScale;
        const swayY=Math.cos(t*motion.vertical_sway_rate_rad_s+node.phase*.79)*node.motionY*motionScale;
        const upward=smooth(timeMs,motion.upward_start_ms,motion.upward_peak_ms)*motion.upward_fraction*region.height*(.58+node.v*.42)*motionScale;
        const scale=(1+expansion*motion.expansion_scale)*returnScale;
        const driftDown=recollection*motion.recollection_downward_fraction*region.height*node.v;
        return {
          x:centerX+(node.x-centerX)*scale+swayX,
          y:centerY+(node.y-centerY)*scale-upward+driftDown+swayY,
          u:node.u,v:node.v,phase:node.phase
        };
      });
    }
    drawAmbient(context,region,rect,timeMs,intensity,motionScale) {
      const rendering=this.rendering;
      const bounds=region.bounds;
      const x=rect.x+bounds.minX/this.contract.asset.width*rect.width;
      const y=rect.y+bounds.minY/this.contract.asset.height*rect.height;
      const width=region.width/this.contract.asset.width*rect.width;
      const height=region.height/this.contract.asset.height*rect.height;
      const drift=smooth(timeMs,this.contract.motion.expansion_start_ms,this.contract.motion.expansion_peak_ms)*this.contract.motion.upward_fraction*height;
      const centerX=x+width*(.46+.035*Math.sin(timeMs/1000*.42)*motionScale);
      const centerY=y+height*.67-drift;
      context.save();
      context.globalCompositeOperation="screen";
      context.globalAlpha=intensity*rendering.ambient_alpha;
      const gradient=context.createLinearGradient(x,y+height,x+width*.18,y);
      gradient.addColorStop(0,rendering.ambient_colors[0]);
      gradient.addColorStop(.34,rendering.ambient_colors[1]);
      gradient.addColorStop(.72,rendering.ambient_colors[2]);
      gradient.addColorStop(1,rendering.ambient_colors[3]);
      context.fillStyle=gradient;
      context.fillRect(x,y,width,height);
      context.globalAlpha=intensity*rendering.ambient_bloom_alpha;
      const bloom=context.createRadialGradient(centerX,centerY,0,centerX,centerY,Math.max(width,height)*.78);
      bloom.addColorStop(0,rendering.ambient_bloom_colors[0]);
      bloom.addColorStop(.52,rendering.ambient_bloom_colors[1]);
      bloom.addColorStop(1,"rgba(221,237,218,0)");
      context.fillStyle=bloom;
      context.fillRect(x,y,width,height);
      context.restore();
    }
    drawCells(context,region,positions,rect,timeMs,intensity,motionScale,expansion,recollection,exit) {
      const rendering=this.rendering;
      const motion=this.contract.motion;
      const asset=this.contract.asset;
      const revealDuration=smooth(timeMs,motion.reveal_start_ms,motion.reveal_end_ms);
      context.save();
      context.globalCompositeOperation="screen";
      for (const cell of region.cells) {
        const [topLeft,topRight,bottomRight,bottomLeft]=cell.nodes;
        const midU=cell.u,midV=cell.v;
        const revealThreshold=1-revealDuration;
        const reveal=smooth(midV,revealThreshold-.22,revealThreshold+.16);
        const middleDistance=Math.abs(midV-.5)*2;
        const peripheral=clamp(middleDistance*.64+Math.abs(midU-.5)*.36,0,1);
        const exitWeight=1-exit*(1-peripheral*.82);
        const recovery=1-recollection*.08;
        const wave=rendering.cell_alpha_wave_base+rendering.cell_alpha_wave_amplitude*Math.sin(timeMs/1000*motion.cell_wave_rate_rad_s+cell.phase);
        const opacity=intensity*rendering.caustic_cell_alpha*(.45+cell.weight*.75)*wave*reveal*exitWeight*recovery;
        if (opacity<.002) continue;
        const ids=[topLeft,topRight,bottomRight,bottomLeft];
        const first=positions[topLeft];
        const firstPoint={x:rect.x+first.x/asset.width*rect.width,y:rect.y+first.y/asset.height*rect.height};
        context.beginPath();
        context.moveTo(firstPoint.x,firstPoint.y);
        for (let side=0;side<4;side+=1) {
          const a=ids[side],b=ids[(side+1)%4];
          traceGridEdge(context,positions,a,b,edgeRecord(region,a,b),timeMs,rendering,rect,asset);
        }
        context.closePath();
        const tint=rendering.caustic_colors[cell.color];
        context.fillStyle=tint;
        context.globalAlpha=clamp(opacity,0,rendering.max_layer_alpha);
        context.fill();
      }
      context.restore();
      this.drawHighlights(context,region,positions,rect,timeMs,intensity,motionScale,exit);
    }
    drawHighlights(context,region,positions,rect,timeMs,intensity,motionScale,exit) {
      const rendering=this.rendering;
      const motion=this.contract.motion;
      const asset=this.contract.asset;
      context.save();
      context.globalCompositeOperation="screen";
      context.lineCap="round";
      context.lineJoin="round";
      for (const edge of region.edges) {
        if (edge.weight<rendering.highlight_edge_threshold) continue;
        const a=positions[edge.a],b=positions[edge.b];
        const averageV=(a.v+b.v)/2;
        const reveal=smooth(timeMs,motion.reveal_start_ms+80,motion.reveal_end_ms+80);
        const peripheral=clamp(Math.abs(averageV-.5)*1.45,0,1);
        const exitWeight=1-exit*(1-peripheral*.88);
        const drift=.82+.18*Math.sin(timeMs/1000*motion.highlight_wave_rate_rad_s+edge.phase);
        const alpha=intensity*rendering.refraction_alpha*edge.weight*drift*reveal*exitWeight;
        if (alpha<.004) continue;
        const [control1,control2]=edgeControlPoints(a,b,edge,timeMs,rendering);
        const toDisplay=point=>({x:rect.x+point.x/asset.width*rect.width,y:rect.y+point.y/asset.height*rect.height});
        const start=toDisplay(a),end=toDisplay(b),c1=toDisplay(control1),c2=toDisplay(control2);
        context.beginPath();
        context.moveTo(start.x,start.y);
        context.bezierCurveTo(c1.x,c1.y,c2.x,c2.y,end.x,end.y);
        context.strokeStyle=rendering.refraction_colors[edge.a%rendering.refraction_colors.length];
        context.lineWidth=rendering.refraction_width_source_px/asset.width*rect.width;
        context.globalAlpha=clamp(alpha,0,rendering.max_layer_alpha);
        context.stroke();
      }
      context.restore();
    }
    draw(context,rect,timeMs,baselineIntensity,strengthValue,motionScale) {
      const motion=this.contract.motion;
      const intensity=baselineIntensity*strengthValue;
      const expansion=smooth(timeMs,motion.expansion_start_ms,motion.expansion_peak_ms)*motionScale;
      const recollection=smooth(timeMs,motion.recollection_start_ms,motion.recollection_end_ms)*motionScale;
      const exit=smooth(timeMs,motion.exit_start_ms,motion.exit_end_ms);
      for (const region of this.regions) {
        context.save();
        this.drawAmbient(context,region,rect,timeMs,intensity,motionScale);
        const positions=this.nodePositions(region,timeMs,motionScale,expansion);
        this.drawCells(context,region,positions,rect,timeMs,intensity,motionScale,expansion,recollection,exit);
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
      controller.pause();controller.seek(Number(timelineInput.value));
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
      const response=await timeoutFetch("../pages/recognition/web/candidates/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json?v=2",{cache:"no-cache",credentials:"same-origin"});
      if (!response.ok) throw new Error("候选水光合同加载失败 HTTP "+response.status);
      const loaded=await response.json();
      if (loaded.contract_id!=="FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE"||
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
