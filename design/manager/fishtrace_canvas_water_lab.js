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
  let initialized = false;
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
  function colorWithAlpha(hex, alpha) {
    const normalized = hex.replace("#","");
    const number = parseInt(normalized,16);
    const r = (number >> 16) & 255;
    const g = (number >> 8) & 255;
    const b = number & 255;
    return "rgba(" + r + "," + g + "," + b + "," + Math.max(0,Math.min(1,alpha)) + ")";
  }
  function midpoint(a,b) { return {x:(a.x+b.x)/2,y:(a.y+b.y)/2}; }
  function traceSoftIrregularPath(context,width,height,random,profile) {
    const points = [];
    const count = profile.vertices;
    const rotation = random() * Math.PI * 2;
    for (let i=0;i<count;i+=1) {
      const angle = rotation + (Math.PI * 2 * i / count);
      const radius = profile.radius_range[0] + random() * (profile.radius_range[1]-profile.radius_range[0]);
      points.push({
        x:width/2 + Math.cos(angle) * width * profile.x_radius_fraction * radius,
        y:height/2 + Math.sin(angle) * height * profile.y_radius_fraction * radius
      });
    }
    context.beginPath();
    context.moveTo(midpoint(points[count-1],points[0]).x,midpoint(points[count-1],points[0]).y);
    for (let i=0;i<count;i+=1) {
      const next = points[(i+1)%count];
      const middle = midpoint(points[i],next);
      context.quadraticCurveTo(points[i].x,points[i].y,middle.x,middle.y);
    }
    context.closePath();
  }
  function makeTexture(layer,index,seed,profile) {
    const texture = document.createElement("canvas");
    texture.width = layer.texture_width;
    texture.height = layer.texture_height;
    const textureContext = texture.getContext("2d");
    const random = seeded(seed + index * 7919 + layer.id.length * 101);
    traceSoftIrregularPath(textureContext,texture.width,texture.height,random,profile);
    textureContext.save();
    textureContext.clip();
    const centerX = texture.width * (profile.gradient_center_x[0] + random() * (profile.gradient_center_x[1]-profile.gradient_center_x[0]));
    const centerY = texture.height * (profile.gradient_center_y[0] + random() * (profile.gradient_center_y[1]-profile.gradient_center_y[0]));
    const outerRadius = Math.max(texture.width,texture.height) * profile.gradient_radius_scale;
    const gradient = textureContext.createRadialGradient(centerX,centerY,outerRadius*0.05,centerX,centerY,outerRadius);
    const color = layer.colors[index % layer.colors.length];
    gradient.addColorStop(profile.gradient_stops[0],colorWithAlpha(color,layer.texture_alpha));
    gradient.addColorStop(profile.gradient_stops[1],colorWithAlpha(color,layer.texture_alpha*0.58));
    gradient.addColorStop(profile.gradient_stops[2],colorWithAlpha(color,layer.texture_alpha*0.17));
    gradient.addColorStop(profile.gradient_stops[3],colorWithAlpha(color,0));
    textureContext.fillStyle = gradient;
    textureContext.fillRect(0,0,texture.width,texture.height);
    for (let ribbon=0;ribbon<layer.ribbon_count;ribbon+=1) {
      textureContext.beginPath();
      textureContext.moveTo(texture.width*(0.16+random()*0.12),texture.height*(0.30+random()*0.30));
      textureContext.bezierCurveTo(
        texture.width*(0.30+random()*0.14),texture.height*(0.05+random()*0.25),
        texture.width*(0.61+random()*0.12),texture.height*(0.67+random()*0.20),
        texture.width*(0.82+random()*0.10),texture.height*(0.43+random()*0.25)
      );
      textureContext.strokeStyle = colorWithAlpha(layer.colors[(index+1)%layer.colors.length],layer.ribbon_alpha);
      textureContext.lineWidth = Math.max(1.2,texture.width*0.009);
      textureContext.lineCap = "round";
      textureContext.stroke();
    }
    textureContext.restore();
    return texture;
  }

  class WaterCausticsScene {
    constructor(value) {
      this.contract = value;
      this.patches = [];
      const layers = value.rendering.layers;
      let serial = 0;
      for (const layer of layers) {
        for (let index=0;index<layer.count;index+=1) {
          const patchRandom = seeded(value.rendering.deterministic_seed + serial * 104729);
          const sample = range => range[0] + patchRandom() * (range[1] - range[0]);
          const sizeX = sample(layer.size_x);
          const sizeY = sample(layer.size_y);
          this.patches.push({
            layer:layer,
            textures:[
              makeTexture(layer,index,value.rendering.deterministic_seed + serial * 104729,value.rendering.texture_profile),
              makeTexture(layer,index,value.rendering.deterministic_seed + serial * 104729 + 5381,value.rendering.texture_profile)
            ],
            x:sample(value.rendering.patch_position_range),
            y:sample(value.rendering.patch_position_range),
            width:sizeX,
            height:sizeY,
            phase:patchRandom()*Math.PI*2,
            rate:sample(value.rendering.animation_rate_range),
            weight:sample(value.rendering.patch_weight_range)
          });
          serial += 1;
        }
      }
    }
    draw(context,rect,timeMs,baselineIntensity,strengthValue,motionScale) {
      const motion = this.contract.motion;
      const t = timeMs / 1000;
      const expansion = smooth(timeMs,motion.expansion_start_ms,motion.expansion_peak_ms) * motionScale;
      const upward = smooth(timeMs,motion.upward_start_ms,motion.upward_peak_ms);
      const returnToWater = smooth(timeMs,motion.recollection_start_ms,motion.recollection_end_ms) * motionScale;
      const exit = smooth(timeMs,motion.exit_start_ms,motion.exit_end_ms);
      const exitMove = exit * motionScale;
      for (const patch of this.patches) {
        const layer = patch.layer;
        const swayX = Math.sin(t*patch.rate + patch.phase) * rect.width * motion.sway_x_fraction * motionScale;
        const swayY = Math.cos(t*patch.rate*motion.sway_y_frequency_ratio + patch.phase) * rect.height * motion.sway_y_fraction * motionScale;
        const upY = upward * motion.upward_fraction * rect.height * (motion.upward_top_bias + patch.y*(1-motion.upward_top_bias)) * motionScale;
        const returnY = returnToWater * motion.recollection_downward_fraction * rect.height;
        const outX = (patch.x-0.5) * motion.peripheral_exit_fraction * rect.width * exitMove;
        const outY = (patch.y-0.5) * motion.peripheral_exit_fraction * rect.height * exitMove;
        const scale = (1 + expansion * motion.expansion_scale) *
          (1 - returnToWater * motion.recollection_scale_reduction) *
          (1 - exitMove * motion.exit_scale_reduction);
        const modulation = motion.intensity_wave_base + motion.intensity_wave_amplitude*Math.sin(t*patch.rate*motion.intensity_wave_frequency_ratio+patch.phase);
        const opacity = baselineIntensity * strengthValue * layer.alpha * patch.weight * modulation * (1-exit*motion.exit_alpha_reduction);
        if (opacity <= 0.001) continue;
        const width = rect.width * patch.width * scale;
        const height = rect.height * patch.height * scale;
        const x = rect.x + patch.x*rect.width + swayX + outX;
        const y = rect.y + patch.y*rect.height - upY + returnY + swayY + outY;
        const morphWave = 0.5 + 0.5*Math.sin(t*motion.shape_morph_frequency_rad_s*patch.rate+patch.phase);
        const morph = motion.shape_morph_blend[0] + morphWave*(motion.shape_morph_blend[1]-motion.shape_morph_blend[0]);
        context.save();
        context.globalCompositeOperation = this.contract.rendering.blend_mode;
        context.translate(x,y);
        context.rotate(Math.sin(t*patch.rate*motion.rotation_frequency_ratio+patch.phase) * motion.max_rotation_radians * motionScale);
        context.globalAlpha = Math.min(this.contract.rendering.max_canvas_alpha,opacity)*(1-morph);
        context.drawImage(patch.textures[0],-width/2,-height/2,width,height);
        context.globalAlpha = Math.min(this.contract.rendering.max_canvas_alpha,opacity)*morph;
        context.drawImage(patch.textures[1],-width/2,-height/2,width,height);
        context.restore();
      }
    }
  }

  function smooth(time,start,end) {
    if (end <= start) return time >= end ? 1 : 0;
    const p = Math.max(0,Math.min(1,(time-start)/(end-start)));
    return p*p*(3-2*p);
  }
  class CanvasWaterRenderer {
    constructor(stageElement,imageElement,canvasElement,sceneValue) {
      this.stage = stageElement;
      this.image = imageElement;
      this.canvas = canvasElement;
      this.context = canvasElement.getContext("2d",{alpha:true,desynchronized:true});
      this.scene = sceneValue;
      this.width = 0;
      this.height = 0;
      this.dpr = 1;
      this.contentRect = {x:0,y:0,width:0,height:0};
    }
    resize() {
      const bounds = this.stage.getBoundingClientRect();
      if (!bounds.width || !bounds.height || !this.image.naturalWidth || !this.image.naturalHeight) return false;
      this.width = bounds.width;
      this.height = bounds.height;
      this.dpr = Math.max(1,window.devicePixelRatio || 1);
      const backingWidth = Math.max(1,Math.round(this.width*this.dpr));
      const backingHeight = Math.max(1,Math.round(this.height*this.dpr));
      if (this.canvas.width !== backingWidth || this.canvas.height !== backingHeight) {
        this.canvas.width = backingWidth;
        this.canvas.height = backingHeight;
      }
      const fit = getComputedStyle(this.image).objectFit;
      if (fit !== "contain") throw new Error("照片 object-fit 必须为 contain");
      const scale = Math.min(this.width/this.image.naturalWidth,this.height/this.image.naturalHeight);
      const contentWidth = this.image.naturalWidth*scale;
      const contentHeight = this.image.naturalHeight*scale;
      const objectPosition = getComputedStyle(this.image).objectPosition;
      const position = objectPosition.split(/\s+/);
      const px = position[0] === "left" ? 0 : position[0] === "right" ? 1 : parsePosition(position[0]);
      const py = position[1] === "top" ? 0 : position[1] === "bottom" ? 1 : parsePosition(position[1]);
      this.contentRect = {
        x:(this.width-contentWidth)*px,
        y:(this.height-contentHeight)*py,
        width:contentWidth,
        height:contentHeight
      };
      this.canvas.dataset.contentRect = JSON.stringify(this.contentRect);
      this.canvas.dataset.dpr = String(this.dpr);
      this.canvas.dataset.rendererStatus = "READY";
      this.render(controller ? controller.timeMs : 0,0,true);
      return true;
    }
    render(timeMs,intensity,strengthEnabled) {
      if (!this.width || !this.height) this.resize();
      if (!this.width || !this.height) return;
      const context = this.context;
      context.setTransform(this.dpr,0,0,this.dpr,0,0);
      context.clearRect(0,0,this.width,this.height);
      context.globalAlpha = 1;
      context.globalCompositeOperation = "source-over";
      if (!strengthEnabled || intensity <= 0 || !waterEnabled || !this.contentRect.width) {
        canvas.dataset.activeIntensity = "0";
        canvas.dataset.currentTime = String(Math.round(timeMs));
        return;
      }
      context.save();
      context.beginPath();
      context.rect(this.contentRect.x,this.contentRect.y,this.contentRect.width,this.contentRect.height);
      context.clip();
      const motionScale = reducedMotion.matches ? contract.motion.reduced_motion_scale : 1;
      scene.draw(context,this.contentRect,timeMs,intensity,strength,motionScale);
      context.restore();
      canvas.dataset.activeIntensity = String(Math.round(intensity*strength*100));
      canvas.dataset.currentTime = String(Math.round(timeMs));
      canvas.dataset.waterOn = String(waterEnabled);
      canvas.dataset.motionScale = String(motionScale);
    }
    inspect() { return {width:this.width,height:this.height,dpr:this.dpr,contentRect:this.contentRect}; }
  }
  function parsePosition(value) {
    if (!value || value === "center") return 0.5;
    if (value.endsWith("%")) return Math.max(0,Math.min(1,parseFloat(value)/100));
    const amount = parseFloat(value);
    return Number.isFinite(amount) ? Math.max(0,Math.min(1,amount/100)) : 0.5;
  }

  class WaterTimelineController {
    constructor(value,onFrame) {
      this.duration = value.timeline.duration_ms;
      this.frames = value.timeline.frames;
      this.onFrame = onFrame;
      this.timeMs = 0;
      this.playing = false;
      this.rafId = 0;
      this.lastTick = null;
      this.tick = this.tick.bind(this);
      document.addEventListener("visibilitychange",() => {
        if (document.hidden) {
          this.cancelFrame();
          this.lastTick = null;
        } else if (this.playing) {
          this.lastTick = null;
          this.rafId = requestAnimationFrame(this.tick);
        }
      });
    }
    cancelFrame() {
      if (this.rafId) cancelAnimationFrame(this.rafId);
      this.rafId = 0;
    }
    emit() { this.onFrame(this.timeMs,this.playing); }
    seek(timeMs) {
      this.timeMs = Math.max(0,Math.min(this.duration,timeMs));
      this.emit();
    }
    play() {
      if (this.playing || this.timeMs >= this.duration) return;
      this.playing = true;
      this.lastTick = null;
      this.emit();
      if (!document.hidden) this.rafId = requestAnimationFrame(this.tick);
    }
    pause() {
      this.playing = false;
      this.lastTick = null;
      this.cancelFrame();
      this.emit();
    }
    reset() {
      this.pause();
      this.seek(0);
    }
    tick(now) {
      if (!this.playing || document.hidden) return;
      if (this.lastTick === null) this.lastTick = now;
      else {
        this.timeMs += Math.min(50,Math.max(0,now-this.lastTick));
        this.lastTick = now;
      }
      if (this.timeMs >= this.duration) {
        this.timeMs = this.duration;
        this.playing = false;
        this.cancelFrame();
      }
      this.emit();
      if (this.playing) this.rafId = requestAnimationFrame(this.tick);
      else this.rafId = 0;
    }
  }

  function percentAt(timeMs) {
    const frames = contract.timeline.frames;
    for (let i=0;i<frames.length-1;i+=1) {
      const from = frames[i];
      const to = frames[i+1];
      if (timeMs <= to.ms) {
        const p = smooth(timeMs,from.ms,to.ms);
        return (from.intensity_percent+(to.intensity_percent-from.intensity_percent)*p)/100;
      }
    }
    return frames[frames.length-1].intensity_percent/100;
  }
  function activeFrameLabel(timeMs) {
    const frames = contract.timeline.frames;
    const exact = frames.find(frame => Math.abs(frame.ms-timeMs) < 0.5);
    if (exact) return exact.id;
    for (let i=0;i<frames.length-1;i+=1) {
      if (timeMs < frames[i+1].ms) return frames[i].id + " → " + frames[i+1].id;
    }
    return "P7";
  }
  function updateReadout(timeMs,playing) {
    const amount = percentAt(timeMs)*strength;
    const stageLabel = activeFrameLabel(timeMs);
    $("water-current-stage").textContent = stageLabel;
    $("water-current-time").textContent = Math.round(timeMs) + " ms";
    $("water-debug-stage").textContent = stageLabel;
    $("water-debug-time").textContent = Math.round(timeMs) + " ms / " + contract.timeline.duration_ms + " ms";
    $("water-debug-intensity").textContent = Math.round(amount*100) + "%";
    $("water-progress-label").textContent = Math.round(timeMs/contract.timeline.duration_ms*100) + "%";
    $("water-play-state").textContent = playing ? "播放中" : "已暂停";
    timelineInput.value = String(Math.round(timeMs));
    playButton.setAttribute("aria-pressed",String(playing));
    pauseButton.setAttribute("aria-pressed",String(!playing));
    const exact = contract.timeline.frames.find(frame => frame.ms === Math.round(timeMs));
    lab.querySelectorAll("[data-water-frame]").forEach(button => {
      const active = Boolean(exact && exact.id === button.dataset.waterFrame);
      button.classList.toggle("is-active",active);
      button.setAttribute("aria-pressed",String(active));
    });
  }
  function renderAt(timeMs,playing) {
    updateReadout(timeMs,playing);
    if (renderer) renderer.render(timeMs,percentAt(timeMs),true);
  }
  function bindControls() {
    root.querySelectorAll("[data-water-view]").forEach(button => {
      button.addEventListener("click",() => {
        const view = button.dataset.waterView;
        root.dataset.workspaceView = view;
        root.querySelectorAll("[data-water-view]").forEach(tab => {
          const active = tab === button;
          tab.classList.toggle("is-active",active);
          tab.setAttribute("aria-pressed",String(active));
        });
        root.querySelectorAll("[data-workspace-panel]").forEach(panel => {
          panel.hidden = panel.dataset.workspacePanel !== view;
        });
        if (view === "canvas-water" && renderer) {
          requestAnimationFrame(() => {
            renderer.resize();
            renderer.render(controller.timeMs,percentAt(controller.timeMs),true);
          });
        }
      });
    });
    playButton.addEventListener("click",() => controller?.play());
    pauseButton.addEventListener("click",() => controller?.pause());
    resetButton.addEventListener("click",() => {
      if (!controller) return;
      controller.reset();
      strength = 1;
      strengthInput.value = "100";
      $("water-strength-value").textContent = "100%";
      waterEnabled = true;
      enabledInput.checked = true;
      renderAt(0,false);
    });
    timelineInput.addEventListener("input",() => {
      if (!controller) return;
      const requestedTime = Number(timelineInput.value);
      controller.pause();
      controller.seek(requestedTime);
    });
    lab.querySelectorAll("[data-water-frame]").forEach(button => {
      button.addEventListener("click",() => {
        if (!controller) return;
        controller.pause();
        const frame = contract.timeline.frames.find(item => item.id === button.dataset.waterFrame);
        if (frame) controller.seek(frame.ms);
      });
    });
    enabledInput.addEventListener("change",() => {
      waterEnabled = enabledInput.checked;
      if (controller) renderAt(controller.timeMs,controller.playing);
    });
    strengthInput.addEventListener("input",() => {
      strength = Number(strengthInput.value)/100;
      $("water-strength-value").textContent = strengthInput.value + "%";
      if (controller) renderAt(controller.timeMs,controller.playing);
    });
    retryButton.addEventListener("click",() => init());
    reducedMotion.addEventListener?.("change",() => {
      setMotionStatus();
      if (controller) renderAt(controller.timeMs,controller.playing);
    });
  }
  async function init() {
    if (loading) return;
    loading = true;
    setControlsDisabled(true);
    retryButton.hidden = true;
    loadError.hidden = true;
    setStatus("加载中");
    $("water-asset-status").textContent = "SHA-256 校验中";
    try {
      const response = await timeoutFetch("../pages/recognition/web/candidates/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json?v=1", {cache:"no-cache",credentials:"same-origin"});
      if (!response.ok) throw new Error("候选水光合同加载失败 HTTP " + response.status);
      const loaded = await response.json();
      if (loaded.contract_id !== "FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE" ||
          loaded.asset.path !== "design/system/fish_media_display_v1/assets/real_catch_source.jpg" ||
          loaded.timeline.duration_ms !== 2800 || loaded.status !== "CANDIDATE_NOT_VISUALLY_APPROVED") {
        throw new Error("候选水光合同身份或边界不符");
      }
      contract = loaded;
      canvas.width = 1;
      canvas.height = 1;
      await verifyAsset();
      scene = new WaterCausticsScene(contract);
      renderer = new CanvasWaterRenderer(stage,image,canvas,scene);
      controller = new WaterTimelineController(contract,renderAt);
      controller.timeMs = 0;
      if (!resizeObserver) {
        resizeObserver = new ResizeObserver(() => {
          if (renderer) {
            renderer.resize();
            renderer.render(controller ? controller.timeMs : 0,controller ? percentAt(controller.timeMs) : 0,true);
          }
        });
        resizeObserver.observe(stage);
        window.addEventListener("resize",() => {
          if (renderer) renderer.resize();
        },{passive:true});
      }
      if (!initialized) {
        bindControls();
        initialized = true;
      }
      strength = 1;
      waterEnabled = true;
      timelineInput.max = String(contract.timeline.duration_ms);
      timelineInput.value = "0";
      strengthInput.value = "100";
      enabledInput.checked = true;
      $("water-strength-value").textContent = "100%";
      setMotionStatus();
      renderer.resize();
      renderAt(0,false);
      setControlsDisabled(false);
      setStatus("READY · Canvas 2D · DPR " + renderer.dpr);
      lab.dataset.rendererStatus = "READY";
      lab.dataset.assetSha256 = image.dataset.sha256;
      lab.dataset.durationMs = String(contract.timeline.duration_ms);
    } catch (error) {
      setStatus("加载失败");
      $("water-asset-status").textContent = "未通过 SHA-256 / 尺寸校验";
      loadError.textContent = "水光工作区加载失败：" + (error?.message || String(error)) + "。请检查网络后重试。";
      loadError.hidden = false;
      retryButton.hidden = false;
      lab.dataset.rendererStatus = "ERROR";
      lab.dataset.loadError = error?.message || String(error);
    } finally {
      loading = false;
    }
  }

  bindControls();
  init();
})();