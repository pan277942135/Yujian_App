/* NH01 design-only structured workspace. Android geometry remains unverified. */
(() => {
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const layers = [
    ['top','Top · 品牌与头像','A'], ['stats','Stats · 三项统计','A'],
    ['recent','Recent Catch · 标题与全部','B'], ['hero','Hero · 当前主卡','B'],
    ['peek-left','Peek-Left · 真实邻卡','B'], ['peek-right','Peek-Right · 真实邻卡','B'],
    ['footer','Footer · 操作区域','C'], ['cta','CTA · 记录下一条','C'],
    ['camera','Camera · 拍照按钮','C']
  ];
  const profiles = [[320,480],[360,640],[390,844],[430,932],[480,844]];
  function normalizeFormula(value) {
    return String(value || '').toLowerCase().replace(/[`$\s*]/g,'').replace(/\\times/g,'');
  }
  function allocationFormula(markdownContract, name) {
    const sectionA2 = String(markdownContract || '').match(/### A\.2 Long-screen surplus allocation([\s\S]*?)(?=\n### A\.3)/)?.[1] || '';
    return sectionA2.match(new RegExp(`^\\s*${name}\\s*=\\s*([^\\r\\n]+)$`, 'im'))?.[1]?.trim() || null;
  }
  function allocationShare(formula, baseName) {
    const normalized = normalizeFormula(formula);
    const prefix = `${String(baseName).toLowerCase()}+`;
    if (!normalized.startsWith(prefix)) return null;
    const match = normalized.slice(prefix.length).match(/^(\d+(?:\.\d+)?)?s(?:\/(\d+(?:\.\d+)?))?$/i);
    if (!match) return null;
    const numerator = Number(match[1] || 1), denominator = Number(match[2] || 1);
    return denominator > 0 ? numerator / denominator : null;
  }
  function hasAuthorityConflict(markdownContract, contract) {
    const allocation = contract?.foundation_layout_principles?.allocation_when_surplus_positive;
    const markdownAB = allocationFormula(markdownContract, 'gAB');
    const markdownBC = allocationFormula(markdownContract, 'gBC');
    return !allocation || !markdownAB || !markdownBC ||
      normalizeFormula(markdownAB) !== normalizeFormula(allocation.a_to_b_final) ||
      normalizeFormula(markdownBC) !== normalizeFormula(allocation.b_to_c_final);
  }
  function calculate(width, height, fontScale, topInset, bottomInset, contract, authorityConflict = false) {
    const safeWidth = clamp(Number(width), 280, 900);
    const safeHeight = Math.max(0, Number(height) - Number(topInset) - Number(bottomInset));
    const pressure = clamp((fontScale - 1) / .5, 0, 1);
    const short = clamp((620 - safeHeight) / 220, 0, 1);
    const fontViewport = clamp(safeWidth / 390, 1, 1.13);
    const brandGap = Math.max(9, clamp(safeHeight * .035, 14, 28) - pressure * 3 - short * 2);
    const baseAB = Math.max(12, (safeHeight < 700 ? 14 : safeHeight < 840 ? 16 : 18) - pressure * 2);
    const baseBC = Math.max(3, clamp(safeHeight * .018, 8, 14) - pressure * 3 - short * 2);
    const titleGap = Math.max(8, (safeHeight < 500 ? 8 : safeHeight < 700 ? 12 : safeHeight < 840 ? 18 : 20) - pressure * 4);
    const heroWidth = Math.min(Math.min(safeWidth, 560) * .7, 337.2);
    const desiredHeroHeight = Math.min(heroWidth * 293 / 247, 400);
    const topHeight = 48 + brandGap + 55 * fontScale;
    const recentHeight = Math.max(48, 19 * fontViewport * fontScale * 1.2 + 8);
    const footerHeight = 48 * fontScale + 8 + Math.max(72, clamp(Math.min(safeWidth, safeHeight) * .2, 56, 80)) + 8;
    const availableHero = safeHeight - topHeight - recentHeight - titleGap - footerHeight - baseAB - baseBC;
    const minimumHero = safeWidth === 320 && height === 480 && fontScale === 1 ? 124 : 148;
    const heroHeight = Math.min(desiredHeroHeight, Math.max(minimumHero, availableHero));
    const actualHeroWidth = heroHeight * 247 / 293;
    const hA = topHeight, hB = recentHeight + titleGap + heroHeight, hC = footerHeight;
    const S = Math.max(0, safeHeight - (hA + hB + hC + baseAB + baseBC));
    const allocation = contract.foundation_layout_principles.allocation_when_surplus_positive;
    const shareAB = allocationShare(allocation.a_to_b_final, 'gAB_base');
    const shareBC = allocationShare(allocation.b_to_c_final, 'gBC_base');
    const allocationInvalid = shareAB === null || shareBC === null || Math.abs(shareAB + shareBC - 1) > .000001;
    const gAB = baseAB + S * (shareAB ?? 0);
    const gBC = baseBC + S * (shareBC ?? 0);
    const recentY = Number(topInset) + hA + gAB;
    const heroY = recentY + recentHeight + titleGap;
    const footerY = heroY + heroHeight + gBC;
    const cameraSize = Math.max(72, clamp(Math.min(safeWidth, safeHeight) * .2, 56, 80));
    const rects = {
      top:{x:0,y:Number(topInset),w:safeWidth,h:topHeight},
      stats:{x:20,y:Number(topInset)+48+brandGap,w:safeWidth-40,h:55*fontScale},
      recent:{x:20,y:recentY,w:safeWidth-40,h:recentHeight},
      hero:{x:(safeWidth-actualHeroWidth)/2,y:heroY,w:actualHeroWidth,h:heroHeight},
      footer:{x:0,y:footerY,w:safeWidth,h:footerHeight},
      cta:{x:20,y:footerY,w:safeWidth-40,h:48*fontScale},
      camera:{x:(safeWidth-cameraSize)/2,y:footerY+48*fontScale+8,w:cameraSize,h:cameraSize}
    };
    // No verified independent FishRecord originals are registered for this design preview.
    const conflicts = [];
    if (authorityConflict) conflicts.push('SPEC_CONFLICT · 正式 Markdown A.2 与机器合同的剩余空间公式不一致或无法解析');
    if (allocationInvalid) conflicts.push('SPEC_CONFLICT · 机器合同剩余空间分配表达式无法解析或总和不为 1');
    if (availableHero < minimumHero) conflicts.push('SPEC_CONFLICT · Hero 可读高度与安全可用高度不能同时满足');
    if (heroHeight < 148 && actualHeroWidth < 145) conflicts.push('SPEC_CONFLICT · 124dp 短屏特例的 Hero 宽度不足 145dp');
    if (footerY + footerHeight > height - bottomInset + .5) conflicts.push('SPEC_CONFLICT · Footer 超出底部安全区');
    if (heroHeight < 148 && !(safeWidth === 320 && height === 480 && fontScale === 1)) conflicts.push('SPEC_CONFLICT · Hero 低于 148dp');
    return {safeWidth,safeHeight,hA,hB,hC,baseAB,baseBC,S,gAB,gBC,titleGap,heroHeight,actualHeroWidth,rects,conflicts,
      fontSp:16*fontViewport*fontScale, cameraSize, contractId:contract.foundation_layout_principles.foundation_id};
  }
  function mount(root, view, contract, manifest, build, markdownContract = '', resourceBinding = {}) {
    const authorityConflict = hasAuthorityConflict(markdownContract, contract);
    const workspace=document.createElement('section');
    workspace.className='nh01-structured'; workspace.dataset.testid='nh01-structured-workspace';
    const options=profiles.map(([w,h],i)=>`<option value="${i}">${i===4?'480 dp 宽度边界 · 示例高度':`${w} × ${h} dp`}</option>`).join('');
    workspace.innerHTML=`<header class="nh01-structured-heading"><div><small>DESIGN WORKSPACE · ${contract.foundation_layout_principles.foundation_id} ${contract.foundation_layout_principles.version}</small><h2>结构化高保真</h2><p>图层、响应式画布与属性检查器共用一次布局计算。照片来源未核准时显示占位状态；这是 Web 设计预览，Android Runtime = NOT_VERIFIED。</p>${authorityConflict?'<p class="nh01-authority-conflict" role="alert">SPEC_CONFLICT · 正式 Markdown A.2 与机器合同的剩余空间公式不一致或无法解析；画布按可读取的机器合同计算并保留冲突标记。</p>':''}</div><code>源码 ${build}</code></header>
      <div class="nh01-structured-controls"><label>视口 <select data-control="profile">${options}</select></label><label>宽 <input data-control="width" type="number" min="280" max="900" value="320"> dp</label><label>高 <input data-control="height" type="number" min="400" max="1400" value="480"> dp</label><label>字体 <input data-control="font" type="range" min="1" max="1.5" step="0.05" value="1"><output data-output="font">1.00×</output></label><label>画布 <input data-control="zoom" type="range" min="0.4" max="1.2" step="0.05" value="0.8"><output data-output="zoom">80%</output></label><details><summary>安全区输入</summary><label>上 <input data-control="top" type="number" min="0" max="80" value="24"> dp</label><label>下 <input data-control="bottom" type="number" min="0" max="80" value="24"> dp</label></details></div>
      <div class="nh01-structured-grid"><nav class="nh01-layer-tree" aria-label="NH01 图层树"><h3>页面图层</h3><div data-layer-tree></div></nav><div class="nh01-canvas-area"><div class="nh01-canvas-scroll"><div class="nh01-artboard" data-artboard></div></div><div class="nh01-comparison" data-comparison></div></div><aside class="nh01-inspector" aria-label="图层属性检查器"><h3>属性与响应式规则</h3><div data-inspector></div></aside></div>
      <details class="nh01-development-basis"><summary>开发依据与验收要求</summary><p>正式：${contract.contract_id} ${contract.contract_version} / ${contract.foundation_layout_principles.foundation_id}；静态参考：${manifest.contract_id}。Android Runtime：NOT_VERIFIED。候选规范不得覆盖正式数值。</p><p><a href="${'../../'+view.machine_authority}" target="_blank" rel="noreferrer">机器合同</a> · <a href="${'../../'+view.authority}" target="_blank" rel="noreferrer">正式文字合同</a> · <a href="${'../../'+view.visual_authority_manifest}" target="_blank" rel="noreferrer">五屏资产清单</a></p><div data-basis></div></details>`;
    root.querySelector('.nh01-static-workspace').prepend(workspace);
    const get=name=>workspace.querySelector(`[data-control="${name}"]`);
    let selected='hero';
    const assetByProfile=manifest.assets || [];
    const backgroundAsset=(resourceBinding.resources || []).find(item=>item.path?.endsWith('/Morning_Lake_Master_V1.png'));
    const brandAsset=resourceBinding.brand_title_binding;
    const cameraAsset=resourceBinding.camera_primary_binding;
    const assetPath='../../';
    const safeText=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    function render() {
      const profile=Number(get('profile').value), w=Number(get('width').value), h=Number(get('height').value), font=Number(get('font').value), zoom=Number(get('zoom').value);
      const result=calculate(w,h,font,Number(get('top').value),Number(get('bottom').value),contract,authorityConflict);
      workspace.querySelector('[data-output="font"]').textContent=font.toFixed(2)+'×';
      workspace.querySelector('[data-output="zoom"]').textContent=Math.round(zoom*100)+'%';
      const board=workspace.querySelector('[data-artboard]'); board.style.width=result.safeWidth+'px';board.style.height=h+'px';board.style.transform=`scale(${zoom})`;board.style.transformOrigin='top left';
      board.style.backgroundImage=`linear-gradient(180deg,rgba(238,246,242,.12),rgba(234,244,240,.34)),url("${assetPath}design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png")`;
      workspace.querySelector('.nh01-canvas-scroll').style.minHeight=(h*zoom+24)+'px';
      const rect=(id, extra='')=>{const r=result.rects[id];return `<button type="button" class="nh01-canvas-layer ${extra} ${selected===id?'selected':''}" data-layer="${id}" style="left:${r.x}px;top:${r.y}px;width:${r.w}px;height:${r.h}px" aria-label="${id}">`};
      board.innerHTML=rect('top','nh01-top')+`<img src="${assetPath}design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png" alt="渔见品牌标题"><span class="nh01-avatar">头像来源待核验</span></button>`+
        rect('stats','nh01-stats')+'<span>—<small>累计鱼获</small></span><span>—<small>鱼种</small></span><span>—<small>足迹</small></span></button>'+
        rect('recent','nh01-recent')+`<strong style="font-size:${result.fontSp}px">最近鱼获</strong><span>全部 ›</span></button>`+
        rect('hero','nh01-hero')+'<span>真实鱼获照片来源未核准<br>BLOCKED_ASSET_SOURCE</span><strong>鱼种与元数据：未提供</strong></button>'+
        rect('footer','nh01-footer')+'</button>'+rect('cta','nh01-cta')+'记录下一条鱼获</button>'+
        rect('camera','nh01-camera')+`<img src="${assetPath}design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png" alt="拍照按钮视觉资产"></button>`;
      workspace.querySelector('[data-layer-tree]').innerHTML=layers.map(([id,name,group])=>`<button type="button" data-select="${id}" class="${selected===id?'selected':''}" aria-pressed="${selected===id}"><span>${safeText(name)}</span><small>${group} · ${id.startsWith('peek')?'BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS':id==='hero'?'BLOCKED_ASSET_SOURCE':'DESIGN PREVIEW'}</small></button>`).join('');
      const current=result.rects[selected];
      const node=current?board.querySelector(`[data-layer="${selected}"]`):null;
      const measured=node?.getBoundingClientRect();
      const asset=profile<5?assetByProfile[profile]:null;
      const selectedAssets = selected==='top' ? [backgroundAsset, brandAsset, {asset_id:'profile_avatar',path:'未提供',sha256:'未提供',status:'未验证'}]
        : selected==='camera' ? [cameraAsset]
        : selected==='hero' ? [{asset_id:'fish_record_hero',path:'未提供',sha256:'未提供',status:'BLOCKED_ASSET_SOURCE'}]
        : selected.startsWith('peek') ? [{asset_id:'independent_fish_neighbor',path:'未提供',sha256:'未提供',status:'BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS'}]
        : [];
      const assetRows = selectedAssets.map(item=>`<p><strong>${safeText(item?.asset_id||'未提供')}</strong> · ${safeText(item?.status||'未提供')} · version ${safeText(item?.version||'未提供')}<br><code>${safeText(item?.path||item?.registry_path||'未提供')}</code><br>SHA-256 ${safeText(item?.sha256||'未提供')}</p>`).join('') || '<p>位图资产：不适用；图层由页面结构和正式布局规则呈现。</p>';
      const referenceRow = asset ? `<p><strong>冻结静态对照</strong> · ${safeText(asset.status)} · version ${safeText(asset.version||'未提供')}<br><code>${safeText(asset.path)}</code><br>SHA-256 ${safeText(asset.sha256)}</p>` : '';
      const allocationA=contract.foundation_layout_principles.allocation_when_surplus_positive.a_to_b_final;
      const allocationB=contract.foundation_layout_principles.allocation_when_surplus_positive.b_to_c_final;
      workspace.querySelector('[data-inspector]').innerHTML=`<dl><dt>图层 ID / 名称</dt><dd>${safeText(selected)} · ${safeText(layers.find(x=>x[0]===selected)?.[1])}</dd><dt>逻辑组</dt><dd>${safeText(layers.find(x=>x[0]===selected)?.[2])}</dd><dt>当前视口</dt><dd>${w} × ${h} dp · fontScale ${font}</dd><dt>计算位置 / 尺寸</dt><dd>${current?`${current.x.toFixed(1)}, ${current.y.toFixed(1)} · ${current.w.toFixed(1)} × ${current.h.toFixed(1)} dp`:'未提供'}</dd><dt>浏览器实测</dt><dd>${measured?`${(measured.width/zoom).toFixed(1)} × ${(measured.height/zoom).toFixed(1)} CSS px（除以画布缩放）`:'未提供'}</dd><dt>字号</dt><dd>${['recent','hero'].includes(selected)?result.fontSp.toFixed(1)+' CSS px（预览）':'未提供'}</dd><dt>截断</dt><dd>${selected==='hero'?'未验证 · 缺少获准鱼获数据':'未检测到；Android 未验证'}</dd><dt>资产来源与状态</dt><dd>${assetRows}${referenceRow}</dd><dt>正式合同</dt><dd>${safeText(contract.contract_id)} · ${safeText(result.contractId)} ${safeText(contract.foundation_layout_principles.version)}<br><code>${safeText(view.machine_authority)}</code></dd><dt>运行状态</dt><dd>${result.conflicts.length?result.conflicts.join('; '):'WEB PREVIEW · Android NOT_VERIFIED'}</dd></dl><h4>剩余空间计算</h4><code>H_safe=${result.safeHeight.toFixed(1)}<br>hA=${result.hA.toFixed(1)}, hB=${result.hB.toFixed(1)}, hC=${result.hC.toFixed(1)}<br>gAB_base=${result.baseAB.toFixed(1)}, gBC_base=${result.baseBC.toFixed(1)}<br>S=max(0,H_safe-Σ)=${result.S.toFixed(1)}<br>${safeText(contract.foundation_layout_principles.version)}: ${safeText(allocationA)} = ${result.gAB.toFixed(1)}; ${safeText(allocationB)} = ${result.gBC.toFixed(1)}</code><p>Title→Hero ${result.titleGap.toFixed(1)} dp · Hero ${result.heroHeight.toFixed(1)} dp · Camera ${result.cameraSize.toFixed(1)} dp · 约束：${result.conflicts.length?result.conflicts.join('; '):'当前模型未发现冲突'}</p>`;
      workspace.querySelector('[data-comparison]').innerHTML=asset?`<strong>冻结高保真对照 · ${safeText(asset.asset_id)}</strong><span>目标 ${asset.target_viewport_dp.width} × ${asset.target_viewport_dp.height??'高度未冻结'} dp · 原图 ${asset.actual_canvas_px.width} × ${asset.actual_canvas_px.height} px</span><img src="${assetPath+safeText(asset.path)}" alt="${safeText(asset.asset_id)} 冻结参考">`:'<span>无对应冻结稿</span>';
      workspace.querySelector('[data-basis]').textContent=`当前资产 ${asset?.path||'未提供'} · SHA-256 ${asset?.sha256||'未提供'} · 静态 ${asset?.status||'未验证'}；当前 Web 布局计算绑定源码 ${build}。视觉差异与 Android bounds：未验证。`;
      board.querySelectorAll('[data-layer]').forEach(node=>node.onclick=()=>{selected=node.dataset.layer;render();});
      workspace.querySelectorAll('[data-select]').forEach(node=>node.onclick=()=>{selected=node.dataset.select;render();board.querySelector(`[data-layer="${selected}"]`)?.scrollIntoView({block:'nearest',inline:'nearest'});});
    }
    get('profile').onchange=()=>{const [w,h]=profiles[Number(get('profile').value)];get('width').value=w;get('height').value=h;render();};
    ['width','height','font','zoom','top','bottom'].forEach(name=>get(name).addEventListener('input',render));
    render();
  }
  window.NH01StructuredWorkspace={mount,calculate,hasAuthorityConflict,allocationShare};
})();
