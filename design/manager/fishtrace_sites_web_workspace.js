(() => {
  const root = document.querySelector('[data-testid="fishtrace-sites-web-workspace"]');
  if (!root) return;
  const samplePhoto = '../system/fish_media_display_v1/assets/real_catch_source.jpg';
  const stateLabels = {
    'entry':'01 · 入口',
    'source-choice':'02 · 来源选择',
    'preview':'03 · 照片预览',
    'processing':'04 · 识别中',
    'result':'05 · 识别结果',
    'error':'06 · 异常恢复'
  };
  const frames = [
    {id:'P0',ms:0,water:0,gold:0,label:'照片进入 · 原始光场'},
    {id:'P1',ms:350,water:10,gold:0,label:'晨光出现'},
    {id:'P2',ms:950,water:45,gold:15,label:'面状焦散'},
    {id:'P3',ms:1200,water:75,gold:45,label:'光场聚焦候选'},
    {id:'P4',ms:1550,water:60,gold:100,label:'金光峰值候选'},
    {id:'P5',ms:2050,water:35,gold:100,label:'稳定阶段'},
    {id:'P6',ms:2600,water:10,gold:100,label:'外围退场'},
    {id:'P7',ms:2800,water:0,gold:100,label:'外围归零'}
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
  let motionHandle = 0;
  let motionStart = 0;
  let transitionTimer = 0;

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
    ['preview','processing','result','error'].forEach(scene => {
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
    if (state !== 'processing') stopMotion();
    activeState = state;
    if (['preview','processing','result','error'].includes(state)) ensurePhoto();
    $$('.product-scene').forEach(scene => { scene.hidden = scene.dataset.scene !== state; });
    $$('.state-tab').forEach(button => button.classList.toggle('is-active', button.dataset.state === state));
    $('#preview-heading').textContent = stateLabels[state];
    $('#timeline-inspector').hidden = state !== 'processing';
    $('#product-device').querySelector('.product-page').dataset.currentState = state;
    if (state === 'processing') setFrame(motionFrame);
    renderTree();
    selectLayer(layerTree()[0]?.id || 'page-shell');
    switchInspector(inspectorMode);
    if (options?.scrollPreview) $('#viewport-canvas').scrollIntoView({behavior:'smooth',block:'nearest'});
  }
  function setFrame(index) {
    motionFrame = Math.max(0, Math.min(frames.length - 1, Number(index) || 0));
    const frame = frames[motionFrame];
    $$('#timeline-frames [data-frame]').forEach(button => button.classList.toggle('is-active', button.dataset.frame === frame.id));
    $$('.ambient-caustics').forEach(layer => layer.style.setProperty('--water-alpha', String(frame.water / 100)));
    const title = $('#processing-status-title');
    const copy = $('#processing-status-copy');
    if (title) title.textContent = frame.label;
    if (copy) copy.textContent = '水面光场 · ' + frame.id + ' · ' + frame.ms + 'ms';
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
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setFrame(7);
      return;
    }
    motionStart = performance.now();
    const tick = now => {
      const elapsed = Math.max(0, Math.min(2800, now - motionStart));
      let index = 0;
      for (let i = 0; i < frames.length; i++) if (elapsed >= frames[i].ms) index = i;
      setFrame(index);
      if (elapsed < 2800) motionHandle = requestAnimationFrame(tick);
      else motionHandle = 0;
    };
    motionHandle = requestAnimationFrame(tick);
  }
  function beginDemoRecognition() {
    clearTimeout(transitionTimer);
    stopMotion();
    motionFrame = 0;
    setState('processing');
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setFrame(7);
      transitionTimer = setTimeout(() => setState('result'), 120);
      return;
    }
    const start = performance.now();
    const tick = now => {
      const elapsed = Math.min(2800, now - start);
      let index = 0;
      for (let i = 0; i < frames.length; i++) if (elapsed >= frames[i].ms) index = i;
      setFrame(index);
      if (elapsed < 2800) {
        motionHandle = requestAnimationFrame(tick);
      } else {
        motionHandle = 0;
        transitionTimer = setTimeout(() => setState('result'), 850);
      }
    };
    motionHandle = requestAnimationFrame(tick);
  }
  function reset() {
    clearTimeout(transitionTimer);
    stopMotion();
    revokePhotoUrl();
    selectedFile = null;
    photoObjectUrl = null;
    photoUrl = samplePhoto;
    sourceKind = 'design-sample';
    motionFrame = 0;
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
      if (state === 'preview' || state === 'processing' || state === 'result' || state === 'error') ensurePhoto();
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
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (!action) return;
    if (action === 'start') setState('source-choice');
    else if (action === 'camera') $('#camera-input').click();
    else if (action === 'gallery') $('#gallery-input').click();
    else if (action === 'recognize') beginDemoRecognition();
    else if (action === 'source-choice') setState('source-choice');
    else if (action === 'back-entry' || action === 'close' || action === 'reset') reset();
    else if (action === 'play-motion') playMotion();
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