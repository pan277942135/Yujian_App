(() => {
  const root = document.querySelector('[data-testid="fishtrace-sites-web-workspace"]');
  if (!root) return;
  const samplePhoto = '../system/fish_media_display_v1/assets/real_catch_source.jpg';
  const stateLabels = {
    'entry':'01 · 入口',
    'source-choice':'02 · 来源选择',
    'preview':'03 · 照片预览',
    'processing':'04 · 识别中',
    'reveal':'05 · 结果揭晓',
    'result':'06 · 识别结果',
    'error':'07 · 异常恢复'
  };
  const frames = [
    {id:'P0',ms:0,water:0,gold:0,label:'原图进入 · 光场归零'},
    {id:'P1',ms:350,water:10,gold:0,label:'晨光出现'},
    {id:'P2',ms:950,water:45,gold:15,label:'面状焦散扩散'},
    {id:'P3',ms:1200,water:75,gold:45,label:'水光向上扩散'},
    {id:'P4',ms:1550,water:60,gold:100,label:'金光目标峰值 · Mask NONE'},
    {id:'P5',ms:2050,water:35,gold:100,label:'外围水光回落 · 鱼体描光关闭'},
    {id:'P6',ms:2600,water:10,gold:100,label:'外围水光退场'},
    {id:'P7',ms:2800,water:0,gold:100,label:'外围水光归零'}
  ];
  const rr09Frames = [
    {id:'R0',ms:0,globalMs:2800,titleOpacity:0,offset:12,label:'原图接续'},
    {id:'R0.5',ms:100,globalMs:2900,titleOpacity:0,offset:12,label:'照片几何中点'},
    {id:'R1',ms:200,globalMs:3000,titleOpacity:0,offset:10,label:'结果 Hero 落位'},
    {id:'R2',ms:380,globalMs:3180,titleOpacity:0,offset:8,label:'名称区域准备'},
    {id:'R3',ms:550,globalMs:3350,titleOpacity:0.18,offset:5,label:'中文名称渐显（未完整）'},
    {id:'R4',ms:680,globalMs:3480,titleOpacity:1,offset:2,label:'中文名称揭晓'},
    {id:'R5',ms:850,globalMs:3650,titleOpacity:1,offset:0,label:'稳定结果状态'}
  ];
  let contract = null;
  let activeState = 'entry';
  let activeLayer = 'page-shell';
  let inspectorMode = 'layers';
  let deviceMode = 'desktop';
  let sourceKind = 'design-sample';
  let photoUrl = samplePhoto;
  let photoObjectUrl = null;
  let selectedFile = null;
  let motionFrame = 0;
  let revealFrame = 0;
  let motionHandle = 0;
  let motionStart = 0;
  let transitionTimer = 0;
  const maskTrustState = "NONE"; // Public design fixture: no production-qualified Mask.

  const $ = selector => root.querySelector(selector);
  const $$ = selector => Array.from(root.querySelectorAll(selector));
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const layerDefs = () => contract?.layer_definitions || {};
  const layerTree = () => contract?.layer_tree_by_state?.[activeState] || [];
  const layerLabel = id => layerDefs()[id]?.label || id;
  const statusLabel = status => status || 'DESIGN ONLY';

  function revokePhotoUrl() {
    if (photoObjectUrl) URL.revokeObjectURL(photoObjectUrl);
    photoObjectUrl = null;
  }
  function setPhoto(src, kind, file) {
    revokePhotoUrl();
    selectedFile = file || null;
    sourceKind = kind || 'design-sample';
    if (file) {
      photoObjectUrl = URL.createObjectURL(file);
      photoUrl = photoObjectUrl;
    } else {
      photoUrl = src || samplePhoto;
    }
    syncPhotoNodes();
    updatePhotoMetadata();
  }
  function syncPhotoNodes() {
    ['preview','processing','reveal','result','error'].forEach(scene => {
      const foreground = $('#photo-foreground-' + scene);
      const backdrop = $('#photo-backdrop-' + scene);
      if (foreground) foreground.src = photoUrl;
      if (backdrop) backdrop.src = photoUrl;
    });
    const tag = $('#preview-fixture-tag');
    if (tag) tag.textContent = sourceKind === 'local-file' ? '本地照片 · 未上传' : '设计样例照片';
  }
  function updatePhotoMetadata() {
    const fileName = $('#photo-file-name');
    const source = $('#photo-source-label');
    const dims = $('#photo-dimensions');
    if (fileName) fileName.textContent = selectedFile?.name || 'real_catch_source.jpg';
    if (source) source.textContent = sourceKind === 'local-file' ? '本机选择' : (sourceKind === 'camera-input' ? '拍摄 / 本机选择' : '设计样例');
    const img = $('#photo-foreground-preview');
    if (img && img.complete && img.naturalWidth) {
      if (dims) dims.textContent = img.naturalWidth + ' × ' + img.naturalHeight;
    } else if (dims) dims.textContent = '原图完整显示';
  }
  function ensurePhoto() {
    if (!photoUrl) setPhoto(samplePhoto, 'design-sample', null);
    syncPhotoNodes();
    updatePhotoMetadata();
  }
  function selectLayer(id) {
    activeLayer = id;
    $$('.layer-highlight').forEach(node => node.classList.remove('layer-highlight'));
    $$('[data-layer-id="' + id + '"]').forEach(node => node.classList.add('layer-highlight'));
    const def = layerDefs()[id] || {label:id,description:'此图层没有额外的属性说明。',status:'DESIGN ONLY',tokens:[]};
    $('#selected-layer-label').textContent = def.label || id;
    $('#selected-layer-status').textContent = statusLabel(def.status);
    $('#selected-layer-description').textContent = def.description || '—';
    renderProperties();
    $$('.layer-node').forEach(node => {
      const selected = node.dataset.layerId === id;
      node.classList.toggle('is-selected', selected);
      if (selected) node.setAttribute('aria-current','true');
      else node.removeAttribute('aria-current');
    });
  }
  function renderTreeNodes(nodes, depth) {
    return '<ul>' + nodes.map(node => {
      const def = layerDefs()[node.id] || {};
      const hasChildren = Array.isArray(node.children) && node.children.length;
      return '<li role="treeitem" aria-expanded="' + (hasChildren ? 'true' : 'false') + '">' +
        '<button type="button" class="layer-node ' + (node.id === activeLayer ? 'is-selected' : '') + '" data-layer-id="' + esc(node.id) + '" aria-label="选择图层 ' + esc(layerLabel(node.id)) + '">' +
          '<span class="node-icon" aria-hidden="true">' + (hasChildren ? '⌄' : '·') + '</span>' +
          '<span class="node-label">' + esc(layerLabel(node.id)) + '</span>' +
          '<span class="node-status">' + esc((def.status || 'DESIGN').replace('_ONLY','')) + '</span>' +
        '</button>' +
        (hasChildren ? renderTreeNodes(node.children, depth + 1) : '') +
      '</li>';
    }).join('') + '</ul>';
  }
  function renderTree() {
    const container = $('#layer-tree');
    const nodes = layerTree();
    if (!container) return;
    container.innerHTML = nodes.length ? renderTreeNodes(nodes,0) : '<div class="tree-loading">当前状态没有登记图层。</div>';
    const count = container.querySelectorAll('.layer-node').length;
    $('#layer-count').textContent = count + ' 层';
    $('#tree-state-label').textContent = stateLabels[activeState] + ' · 图层结构';
    $$('.layer-node').forEach(button => button.addEventListener('click', () => {
      selectLayer(button.dataset.layerId);
      switchInspector('properties');
    }));
  }
  function renderProperties() {
    const def = layerDefs()[activeLayer] || {label:activeLayer,description:'—',status:'DESIGN ONLY',tokens:[]};
    const geom = def.geometry || '由页面状态与响应式规则决定';
    const tokens = (def.tokens || []).map(token => '<span>' + esc(token) + '</span>').join('');
    $('#layer-properties').innerHTML =
      '<div class="property-title">' + esc(def.label || activeLayer) + '</div>' +
      '<div class="property-row"><span>Layer ID</span><strong>' + esc(activeLayer) + '</strong></div>' +
      '<div class="property-row"><span>Role</span><strong>' + esc(def.role || 'Layer') + '</strong></div>' +
      '<div class="property-row"><span>Description</span><p>' + esc(def.description || '—') + '</p></div>' +
      '<div class="property-row"><span>Geometry</span><p>' + esc(geom) + '</p></div>' +
      '<div class="property-row"><span>Status</span><strong>' + esc(statusLabel(def.status)) + '</strong></div>' +
      '<div class="property-row"><span>Tokens</span><div class="token-list">' + (tokens || '<span>—</span>') + '</div></div>';
  }
  function switchInspector(mode) {
    inspectorMode = mode;
    $$('.inspector-tab').forEach(button => {
      const selected = button.dataset.inspector === mode;
      button.classList.toggle('is-active', selected);
      button.setAttribute('aria-selected', selected ? 'true' : 'false');
    });
    $$('[data-inspector-view]').forEach(panel => {
      const selected = panel.dataset.inspectorView === mode;
      panel.hidden = !selected;
      panel.classList.toggle('is-active', selected);
    });
  }
  function setDevice(mode) {
    deviceMode = mode;
    const device = $('#product-device');
    device.classList.toggle('is-phone', mode === 'phone');
    device.classList.toggle('is-desktop', mode === 'desktop');
    device.dataset.device = mode;
    $$('[data-device]').forEach(button => {
      const selected = button.dataset.device === mode;
      button.setAttribute('aria-pressed', selected ? 'true' : 'false');
    });
    $('#viewport-caption').textContent = mode === 'phone' ? '手机 · 390 × 844 逻辑视口' : '桌面 · ≥1024 CSS px';
  }
  function setState(state, options) {
    if (!stateLabels[state]) return;
    clearTimeout(transitionTimer);
    if (state !== 'processing' && !options?.preserveMotion) stopMotion();
    if (!options?.preserveTransition) clearPhotoTransition();
    activeState = state;
    if (['preview','processing','reveal','result','error'].includes(state)) ensurePhoto();
    $$('.product-scene').forEach(scene => { scene.hidden = scene.dataset.scene !== state; });
    $$('.state-tab').forEach(button => button.classList.toggle('is-active', button.dataset.state === state));
    $('#preview-heading').textContent = stateLabels[state];
    $('#timeline-inspector').hidden = state !== 'processing';
    $('#rr09-timeline-inspector').hidden = state !== 'reveal';
    $('#product-device').querySelector('.product-page').dataset.currentState = state;
    if (state === 'processing') setFrame(motionFrame);
    if (state === 'reveal') setRevealFrame(revealFrame);
    renderTree();
    selectLayer(layerTree()[0]?.id || 'page-shell');
    switchInspector(inspectorMode);
    if (options?.scrollPreview) $('#viewport-canvas').scrollIntoView({behavior:'smooth',block:'nearest'});
  }
  function frameIndexAt(samples, elapsed) {
    let index = 0;
    for (let i = 0; i < samples.length; i++) {
      if (elapsed >= samples[i].ms) index = i;
      else break;
    }
    return index;
  }
  function interpolateAt(samples, elapsed, field) {
    if (elapsed <= samples[0].ms) return samples[0][field];
    for (let i = 1; i < samples.length; i++) {
      const right = samples[i];
      if (elapsed <= right.ms) {
        const left = samples[i - 1];
        const span = Math.max(1, right.ms - left.ms);
        const ratio = Math.max(0, Math.min(1, (elapsed - left.ms) / span));
        return left[field] + (right[field] - left[field]) * ratio;
      }
    }
    return samples[samples.length - 1][field];
  }
  function setWaterField(intensity, elapsed) {
    const bounded = Math.max(0, Math.min(1, Number(intensity) || 0));
    const phase = Math.max(0, Math.min(1, elapsed / 2800));
    const opacity = bounded * 0.20;
    const shiftX = Math.sin(phase * Math.PI * 2) * 7;
    const shiftY = Math.cos(phase * Math.PI * 2) * 4;
    $$('.ambient-caustics').forEach(layer => {
      layer.style.setProperty('--water-opacity', opacity.toFixed(4));
      layer.style.setProperty('--water-opacity-soft', (opacity * 0.68).toFixed(4));
      layer.style.setProperty('--water-front', Math.round(18 + 52 * phase) + '%');
      layer.style.setProperty('--water-shift-x', shiftX.toFixed(2) + 'px');
      layer.style.setProperty('--water-shift-y', shiftY.toFixed(2) + 'px');
      layer.style.setProperty('--water-shift-x-alt', (-shiftX * 0.62).toFixed(2) + 'px');
      layer.style.setProperty('--water-shift-y-alt', (shiftY * 0.7).toFixed(2) + 'px');
      layer.style.setProperty('--fish-gold-opacity', maskTrustState === 'TRUSTED_MASK' ? '0.18' : '0');
    });
  }
  function setFrame(index) {
    motionFrame = Math.max(0, Math.min(frames.length - 1, Number(index) || 0));
    const frame = frames[motionFrame];
    $$('#timeline-frames [data-frame]').forEach(button => button.classList.toggle('is-active', button.dataset.frame === frame.id));
    setWaterField(frame.water / 100, frame.ms);
    const title = $('#processing-status-title');
    const copy = $('#processing-status-copy');
    if (title) title.textContent = frame.label;
    if (copy) copy.textContent = '水面光场 · ' + frame.id + ' · ' + frame.ms + 'ms';
  }
  function setProcessingElapsed(elapsed) {
    const bounded = Math.max(0, Math.min(2800, elapsed));
    motionFrame = frameIndexAt(frames, bounded);
    const frame = frames[motionFrame];
    $$('#timeline-frames [data-frame]').forEach(button => button.classList.toggle('is-active', button.dataset.frame === frame.id));
    setWaterField(interpolateAt(frames, bounded, 'water') / 100, bounded);
    const title = $('#processing-status-title');
    const copy = $('#processing-status-copy');
    if (title) title.textContent = frame.label;
    if (copy) copy.textContent = '水面光场 · ' + frame.id + ' · ' + Math.round(bounded) + 'ms';
  }
  function setRevealFrame(index, elapsedOverride) {
    revealFrame = Math.max(0, Math.min(rr09Frames.length - 1, Number(index) || 0));
    const frame = rr09Frames[revealFrame];
    const sampleTime = elapsedOverride == null ? frame.ms : Math.max(0, Math.min(850, Number(elapsedOverride) || 0));
    $$('#rr09-timeline-frames [data-rr09-frame]').forEach(button => button.classList.toggle('is-active', button.dataset.rr09Frame === frame.id));
    setWaterField(0, 2800 + sampleTime);
    const title = $('#reveal-species-title');
    if (title) {
      title.style.setProperty('--reveal-title-opacity', String(interpolateAt(rr09Frames, sampleTime, 'titleOpacity')));
      title.style.setProperty('--reveal-title-offset', interpolateAt(rr09Frames, sampleTime, 'offset') + 'px');
    }
    const summary = $('.reveal-summary');
    if (summary) {
      summary.style.setProperty('--reveal-details-opacity', sampleTime >= 850 ? '1' : '0');
      summary.style.setProperty('--reveal-details-visibility', sampleTime >= 850 ? 'visible' : 'hidden');
    }
    const resultStatus = $('#reveal-result-status');
    if (resultStatus) resultStatus.textContent = sampleTime < 680 ? '识别结果正在揭晓' : '识别完成';
    const statusTitle = $('#rr09-status-title');
    const statusCopy = $('#rr09-status-copy');
    if (statusTitle) statusTitle.textContent = frame.label;
    if (statusCopy) statusCopy.textContent = 'RR09 · ' + frame.id + ' · ' + Math.round(2800 + sampleTime) + 'ms';
  }
  function setRevealElapsed(elapsed) {
    const bounded = Math.max(0, Math.min(850, elapsed));
    setRevealFrame(frameIndexAt(rr09Frames, bounded), bounded);
  }
  function clearPhotoTransition() {
    const overlay = $('#photo-transition-overlay');
    if (overlay) {
      overlay.hidden = true;
      overlay.removeAttribute('data-progress');
      overlay.style.cssText = '';
    }
    const targetMedia = $('.reveal-photo .photo-media');
    if (targetMedia) targetMedia.style.visibility = '';
  }
  function renderPhotoTransition(transition, progress) {
    if (!transition || !transition.overlay) return;
    const p = Math.max(0, Math.min(1, progress));
    const lerp = (a, b) => a + (b - a) * p;
    transition.overlay.style.left = lerp(transition.from.left, transition.to.left).toFixed(2) + 'px';
    transition.overlay.style.top = lerp(transition.from.top, transition.to.top).toFixed(2) + 'px';
    transition.overlay.style.width = lerp(transition.from.width, transition.to.width).toFixed(2) + 'px';
    transition.overlay.style.height = lerp(transition.from.height, transition.to.height).toFixed(2) + 'px';
    transition.overlay.dataset.progress = p.toFixed(3);
    transition.overlay.dataset.transitionRendered = "true";
  }
  function startPhotoTransition(fromRect) {
    const target = $('.reveal-photo');
    const targetMedia = target?.querySelector('.photo-media');
    const targetRect = target?.getBoundingClientRect();
    const overlay = $('#photo-transition-overlay');
    const foreground = $('#photo-transition-image');
    const backdrop = $('#photo-transition-backdrop-image');
    if (!fromRect || !targetRect || targetRect.width <= 0 || targetRect.height <= 0 || !overlay || !foreground || !backdrop) return null;
    foreground.src = photoUrl;
    backdrop.src = photoUrl;
    if (targetMedia) targetMedia.style.visibility = 'hidden';
    overlay.hidden = false;
    overlay.style.borderRadius = '17px';
    const transition = {overlay,targetMedia,from:{left:fromRect.left,top:fromRect.top,width:fromRect.width,height:fromRect.height},to:{left:targetRect.left,top:targetRect.top,width:targetRect.width,height:targetRect.height}};
    renderPhotoTransition(transition, 0);
    return transition;
  }
  function finishPhotoTransition(transition) {
    const overlay = transition?.overlay || $('#photo-transition-overlay');
    if (overlay) { overlay.hidden = true; overlay.removeAttribute('data-progress'); overlay.style.cssText = ''; }
    const targetMedia = transition?.targetMedia || $('.reveal-photo .photo-media');
    if (targetMedia) targetMedia.style.visibility = '';
  }
  function stopMotion() {
    if (motionHandle) cancelAnimationFrame(motionHandle);
    motionHandle = 0;
  }
  function playMotion() {
    stopMotion();
    setState('processing');
    motionFrame = 0;
    setFrame(0);
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setFrame(7); return; }
    const start = performance.now();
    const tick = now => {
      const elapsed = Math.max(0, Math.min(2800, now - start));
      setProcessingElapsed(elapsed);
      if (elapsed < 2800) motionHandle = requestAnimationFrame(tick);
      else { setFrame(7); motionHandle = 0; }
    };
    motionHandle = requestAnimationFrame(tick);
  }
  function playReveal() {
    clearTimeout(transitionTimer);
    stopMotion();
    revealFrame = 0;
    setState('reveal');
    setRevealFrame(0);
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setRevealFrame(6,850); setState('result'); return; }
    const start = performance.now();
    const tick = now => {
      const elapsed = Math.max(0, Math.min(850, now - start));
      setRevealElapsed(elapsed);
      if (elapsed < 850) motionHandle = requestAnimationFrame(tick);
      else { setRevealFrame(6,850); motionHandle = 0; setState('result'); }
    };
    motionHandle = requestAnimationFrame(tick);
  }
  function beginDemoRecognition() {
    clearTimeout(transitionTimer);
    stopMotion();
    clearPhotoTransition();
    const transitionOverlay = $('#photo-transition-overlay');
    if (transitionOverlay) transitionOverlay.removeAttribute('data-transition-rendered');
    motionFrame = 0;
    revealFrame = 0;
    setState('processing');
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setFrame(7); setState('reveal',{preserveMotion:true}); setRevealFrame(6,850); setState('result'); return;
    }
    const start = performance.now();
    let phase = 'processing';
    let photoTransition = null;
    const tick = now => {
      const elapsed = Math.max(0, Math.min(3650, now - start));
      if (elapsed < 2800) setProcessingElapsed(elapsed);
      else {
        const revealElapsed = elapsed - 2800;
        if (phase !== 'reveal') {
          const sourcePhoto = $('.processing-photo');
          const fromRect = sourcePhoto ? sourcePhoto.getBoundingClientRect() : null;
          phase = 'reveal';
          setState('reveal',{preserveMotion:true});
          photoTransition = fromRect ? startPhotoTransition(fromRect) : null;
        }
        setRevealElapsed(revealElapsed);
        if (photoTransition) {
          renderPhotoTransition(photoTransition, Math.min(1,revealElapsed/200));
          if (revealElapsed >= 200) { finishPhotoTransition(photoTransition); photoTransition = null; }
        }
      }
      if (elapsed < 3650) motionHandle = requestAnimationFrame(tick);
      else {
        if (photoTransition) finishPhotoTransition(photoTransition);
        setRevealFrame(6,850);
        motionHandle = 0;
        setState('result');
      }
    };
    motionHandle = requestAnimationFrame(tick);
  }
  function reset() {
    clearTimeout(transitionTimer);
    stopMotion();
    clearPhotoTransition();
    revokePhotoUrl();
    selectedFile = null;
    photoObjectUrl = null;
    photoUrl = samplePhoto;
    sourceKind = 'design-sample';
    motionFrame = 0;
    revealFrame = 0;
    syncPhotoNodes();
    updatePhotoMetadata();
    setDevice('desktop');
    switchInspector('layers');
    setState('entry');
  }
  function handleFile(input, kind) {
    const file = input.files && input.files[0];
    if (!file) return;
    if (!file.type || !file.type.startsWith('image/')) {
      setState('error');
      return;
    }
    setPhoto(null, kind, file);
    input.value = '';
    setState('preview');
  }
  root.addEventListener('click', event => {
    const stateButton = event.target.closest('[data-state]');
    if (stateButton && stateButton.classList.contains('state-tab')) {
      const state = stateButton.dataset.state;
      if (state === 'preview' || state === 'processing' || state === 'reveal' || state === 'result' || state === 'error') ensurePhoto();
      setState(state);
      return;
    }
    const deviceButton = event.target.closest('[data-device]');
    if (deviceButton && deviceButton.classList.contains('tool-button')) {
      setDevice(deviceButton.dataset.device);
      return;
    }
    const inspectorButton = event.target.closest('[data-inspector]');
    if (inspectorButton) {
      switchInspector(inspectorButton.dataset.inspector);
      return;
    }
    const frameButton = event.target.closest('[data-frame]');
    if (frameButton) {
      setFrame(frames.findIndex(frame => frame.id === frameButton.dataset.frame));
      return;
    }
    const revealFrameButton = event.target.closest('[data-rr09-frame]');
    if (revealFrameButton) {
      setRevealFrame(rr09Frames.findIndex(frame => frame.id === revealFrameButton.dataset.rr09Frame));
      return;
    }
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (!action) return;
    if (action === 'start') setState('source-choice');
    else if (action === 'camera') $('#camera-input').click();
    else if (action === 'gallery') $('#gallery-input').click();
    else if (action === 'recognize') beginDemoRecognition();
    else if (action === 'source-choice') setState('source-choice');
    else if (action === 'back-entry' || action === 'close' || action === 'reset') reset();
    else if (action === 'play-motion') playMotion();
    else if (action === 'play-reveal') playReveal();
  });
  $('#gallery-input').addEventListener('change', () => handleFile($('#gallery-input'),'local-file'));
  $('#camera-input').addEventListener('change', () => handleFile($('#camera-input'),'camera-input'));
  $('#photo-foreground-preview').addEventListener('load', updatePhotoMetadata);

  fetch('../pages/recognition/web/contracts/FishTrace_Sites_Web_Structured_Prototype_V1.json', {cache:'no-store'})
    .then(response => {
      if (!response.ok) throw new Error('HTTP ' + response.status);
      return response.json();
    })
    .then(value => {
      if (value.prototype_id !== 'FISHTRACE-SITES-WEB-STRUCTURED-PROTOTYPE-V1') throw new Error('Prototype ID mismatch');
      contract = value;
      renderTree();
      renderProperties();
      selectLayer(activeLayer);
    })
    .catch(error => {
      $('#layer-tree').innerHTML = '<div class="tree-loading">结构合同加载失败：' + esc(error.message) + '</div>';
      $('#layer-count').textContent = '未载入';
    });

  syncPhotoNodes();
  updatePhotoMetadata();
  setDevice('desktop');
  setState('entry');
})();