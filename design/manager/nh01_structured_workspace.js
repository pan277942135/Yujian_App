/* NH01 design-only structured workspace. Android geometry remains unverified. */
(() => {
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  function createResourceLoader({fetchImpl = window.fetch.bind(window), timeoutMs = 12000, onUpdate = () => {}, digestBytes = null} = {}) {
    let generation = 0;
    let activeController = null;
    const notify = snapshot => { try { onUpdate(snapshot); } catch (_) {} };
    const sha256 = async bytes => {
      if (digestBytes) return digestBytes(bytes);
      if (!globalThis.crypto?.subtle) throw Object.assign(new Error('Web Crypto SHA-256 is unavailable'), {failureKind:'integrity'});
      const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes);
      return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('');
    };
    async function load(specs, {retry = false} = {}) {
      if (activeController) activeController.abort();
      const requestGeneration = ++generation;
      const controller = new AbortController();
      activeController = controller;
      const resources = specs.map(spec => ({
        id:spec.id, label:spec.label || spec.id, url:spec.url, status:'IDLE', http_status:null,
        content_type:null, parse_result:'NOT_STARTED', sha256:null, bytes:null,
        started_at:null, completed_at:null, failure_kind:null, failure_reason:null
      }));
      const data = {};
      const timedOut = {value:false};
      const publish = state => {
        if (requestGeneration !== generation) return;
        notify({generation:requestGeneration,state,resources:resources.map(item=>({...item}))});
      };
      const timer = setTimeout(() => { timedOut.value = true; controller.abort(); }, timeoutMs);
      publish(retry ? 'RETRYING' : 'LOADING');
      await Promise.all(specs.map(async (spec, index) => {
        const record = resources[index];
        record.status = 'LOADING';
        record.started_at = new Date().toISOString();
        publish(retry ? 'RETRYING' : 'LOADING');
        try {
          const response = await fetchImpl(spec.url, {cache:'no-store', signal:controller.signal});
          record.http_status = Number(response.status);
          record.content_type = response.headers?.get?.('content-type') || 'unknown';
          if (!response.ok) throw Object.assign(new Error(`HTTP ${response.status}`), {failureKind:'http'});
          if (spec.mime && !spec.mime.test(record.content_type)) {
            throw Object.assign(new Error(`Unexpected Content-Type: ${record.content_type}`), {failureKind:'content_type'});
          }
          let value;
          if (spec.type === 'bytes') {
            const buffer = await response.arrayBuffer();
            record.bytes = buffer.byteLength;
            record.sha256 = await sha256(buffer);
            record.parse_result = 'BINARY_READ';
            if (spec.expectedSha256 && record.sha256.toLowerCase() !== spec.expectedSha256.toLowerCase()) {
              throw Object.assign(new Error(`SHA-256 mismatch; expected ${spec.expectedSha256}, got ${record.sha256}`), {failureKind:'integrity'});
            }
            value = {bytes:record.bytes,sha256:record.sha256};
          } else if (spec.type === 'json') {
            const source = await response.text();
            record.bytes = new TextEncoder().encode(source).length;
            try { value = JSON.parse(source); }
            catch (error) { throw Object.assign(new Error(`JSON parse failed: ${error.message}`), {failureKind:'parse'}); }
            record.sha256 = await sha256(new TextEncoder().encode(source));
            record.parse_result = 'VALID_JSON';
          } else {
            value = await response.text();
            record.bytes = new TextEncoder().encode(value).length;
            record.sha256 = await sha256(new TextEncoder().encode(value));
            record.parse_result = 'TEXT_READ';
          }
          if (spec.validate) {
            const validation = spec.validate(value);
            if (validation !== true && validation !== undefined) {
              throw Object.assign(new Error(typeof validation === 'string' ? validation : 'Resource validation failed'), {failureKind:'validation'});
            }
          }
          record.status = 'READY';
          data[spec.id] = value;
        } catch (error) {
          record.status = 'ERROR';
          record.failure_kind = timedOut.value ? 'timeout' : (error.failureKind || (controller.signal.aborted ? 'cancelled' : 'network'));
          record.failure_reason = timedOut.value ? `Request timed out after ${timeoutMs} ms` : String(error.message || error);
          if (record.failure_kind === 'parse') record.parse_result = 'INVALID';
        } finally {
          record.completed_at = new Date().toISOString();
          publish(retry ? 'RETRYING' : 'LOADING');
        }
      }));
      clearTimeout(timer);
      if (requestGeneration !== generation) return {state:'CANCELLED',generation:requestGeneration,resources:resources.map(item=>({...item})),data:{}};
      const state = resources.some(item => item.status !== 'READY') ? 'ERROR' : 'READY';
      publish(state);
      if (activeController === controller) activeController = null;
      return {state,generation:requestGeneration,resources:resources.map(item=>({...item})),data};
    }
    function abort() {
      generation += 1;
      if (activeController) activeController.abort();
      activeController = null;
    }
    return {load,abort};
  }
  const layers = [
    ['top','Top · 品牌与头像','A'], ['stats','Stats · 三项统计','A'],
    ['recent','Recent Catch · 标题与全部','B'], ['hero','Hero · 当前主卡','B'],
    ['peek-left','Peek-Left · 真实邻卡','B'], ['peek-right','Peek-Right · 真实邻卡','B'],
    ['footer','Footer · 操作区域','C'], ['cta','CTA · 记录下一条','C'],
    ['camera','Camera · 拍照按钮','C']
  ];
  const profiles = [[320,480],[360,640],[390,844],[430,932],[480,null]];
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
  function parseSpacingFormulae(contract) {
    const f=contract?.spacing?.existing_adaptive_formulas || {};
    const n='([0-9]+(?:\\.[0-9]+)?)';
    const parse=(value, pattern, map)=>{const m=String(value||'').match(pattern);return m?map(m.slice(1).map(Number)):null;};
    const pressure=parse(f.font_pressure,/^clamp\(\(systemFontScale-([\d.]+)\)\/([\d.]+),([\d.]+),([\d.]+)\)$/,([origin,range,lo,hi])=>({origin,range,lo,hi}));
    const shortPressure=parse(f.short_height_pressure,/^clamp\(\(([\d.]+)-safeHeightDp\)\/([\d.]+),([\d.]+),([\d.]+)\)$/,([limit,range,lo,hi])=>({limit,range,lo,hi}));
    const header=parse(f.header_to_stats,new RegExp('^max\\('+n+'dp, clamp\\(safeHeightDp\\*'+n+','+n+'dp,'+n+'dp\\) - fontPressure\\*'+n+'dp - shortHeightPressure\\*'+n+'dp\\)$'),([floor,mult,min,max,fontWeight,shortWeight])=>({floor,mult,min,max,fontWeight,shortWeight}));
    const statsTitle=parse(f.stats_to_title,new RegExp('^max\\('+n+'dp, base - fontPressure\\*'+n+'dp\\); base='+n+'dp if safeHeight<'+n+'dp, '+n+'dp if <'+n+'dp, else '+n+'dp$'),([floor,weight,short,threshold1,medium,threshold2,tall])=>({floor,weight,short,threshold1,medium,threshold2,tall}));
    const titleCard=parse(f.title_to_card_runtime,new RegExp('^max\\('+n+'dp, base - fontPressure\\*'+n+'dp\\); base='+n+'dp if safeHeight<'+n+'dp, '+n+'dp if <'+n+'dp, '+n+'dp if <'+n+'dp, else '+n+'dp$'),([floor,weight,short,threshold1,medium,threshold2,tall,threshold3,largest])=>({floor,weight,short,threshold1,medium,threshold2,tall,threshold3,largest}));
    const cardFooter=parse(f.card_to_footer,new RegExp('^max\\('+n+'dp, clamp\\(safeHeightDp\\*'+n+','+n+'dp,'+n+'dp\\) - fontPressure\\*'+n+'dp - shortHeightPressure\\*'+n+'dp\\)$'),([floor,mult,min,max,fontWeight,shortWeight])=>({floor,mult,min,max,fontWeight,shortWeight}));
    const cta=parse(f.cta_to_camera,new RegExp('^'+n+'dp if safeHeight<'+n+'dp, otherwise '+n+'dp$'),([compact,threshold,regular])=>({compact,threshold,regular}));
    const footerTop=parse(f.footer_top_padding,new RegExp('^'+n+'dp if safeHeight<'+n+'dp, otherwise '+n+'dp$'),([compact,threshold,regular])=>({compact,threshold,regular}));
    const footerBottom=parse(f.footer_bottom_padding,new RegExp('^'+n+'dp if safeHeight<'+n+'dp; else '+n+'dp if bottom safe inset>='+n+'dp; otherwise '+n+'dp$'),([compact,threshold,inset,regular])=>({compact,threshold,inset,regular}));
    return {pressure,shortPressure,header,statsTitle,titleCard,cardFooter,cta,footerTop,footerBottom};
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
    const rules=parseSpacingFormulae(contract), pressureRule=rules.pressure, shortRule=rules.shortPressure;
    const pressure=pressureRule?clamp((fontScale-pressureRule.origin)/pressureRule.range,pressureRule.lo,pressureRule.hi):0;
    const short=shortRule?clamp((shortRule.limit-safeHeight)/shortRule.range,shortRule.lo,shortRule.hi):0;
    const fontFormula=String(contract.responsive_monotonicity.font_viewport_scale_formula||'').match(/^min\(min\(safeWidthDp,([\d.]+)\)\/([\d.]+),safeHeightDp\/([\d.]+)\)\.coerceIn\(([\d.]+),([\d.]+)\)$/);
    const fontViewport=fontFormula?clamp(Math.min(Math.min(safeWidth,Number(fontFormula[1]))/Number(fontFormula[2]),safeHeight/Number(fontFormula[3])),Number(fontFormula[4]),Number(fontFormula[5])):1;
    const gap=(spec,fallback)=>spec?Math.max(spec.floor,clamp(safeHeight*spec.mult,spec.min,spec.max)-pressure*spec.fontWeight-short*spec.shortWeight):fallback;
    const headerGap=gap(rules.header,9);
    const baseAB=rules.statsTitle?Math.max(rules.statsTitle.floor,(safeHeight<rules.statsTitle.threshold1?rules.statsTitle.short:safeHeight<rules.statsTitle.threshold2?rules.statsTitle.medium:rules.statsTitle.tall)-pressure*rules.statsTitle.weight):12;
    const baseBC=gap(rules.cardFooter,3);
    const titleGap=rules.titleCard?Math.max(rules.titleCard.floor,(safeHeight<rules.titleCard.threshold1?rules.titleCard.short:safeHeight<rules.titleCard.threshold2?rules.titleCard.medium:safeHeight<rules.titleCard.threshold3?rules.titleCard.tall:rules.titleCard.largest)-pressure*rules.titleCard.weight):8;
    const heroFormula=String(contract.responsive_monotonicity.hero_width_formula||'').match(/^min\(min\(trackWidthDp,([\d.]+)dp\) \* ([\d.]+),([\d.]+)dp\)$/);
    const heroWidth=heroFormula?Math.min(Math.min(safeWidth,Number(heroFormula[1]))*Number(heroFormula[2]),Number(heroFormula[3])):0;
    const formulaConflict=!pressureRule||!shortRule||!rules.header||!rules.statsTitle||!rules.titleCard||!rules.cardFooter||!rules.cta||!rules.footerTop||!rules.footerBottom||!fontFormula||!heroFormula;
    const ratio = String(contract.hero_and_peek.hero_ratio || '247:293').split(':').map(Number);
    const desiredHeroHeight = Math.min(heroWidth * ratio[1] / ratio[0], Number(contract.hero_and_peek.hero_height_max_dp));
    const heading=(contract.typography.roles||[]).find(role=>role.role==='Recent Catch heading')||{};
    const headingSp=clamp(safeHeight*Number(heading.base_ratio||.027),Number(heading.minimum_sp||16),Number(heading.maximum_sp||19))*fontViewport*fontScale;
    const recentHeight=Math.max(19,headingSp*Number(heading.line_height_ratio||1.2));
    const cameraFormula=String(contract.responsive_monotonicity.camera_visual_formula||'').match(/^min\(min\(safeWidthDp,([\d.]+)dp\)\*([\d.]+),safeHeightDp\*([\d.]+),([\d.]+)dp\), with ([\d.]+)dp floor$/);
    const cameraSize=cameraFormula?Math.max(Number(cameraFormula[5]),Math.min(Math.min(safeWidth,Number(cameraFormula[1]))*Number(cameraFormula[2]),safeHeight*Number(cameraFormula[3]),Number(cameraFormula[4]))):Number(contract.camera_and_touch.camera_visual_size_dp.minimum);
    const cameraTouchSize=Math.max(cameraSize,Number(contract.camera_and_touch.camera_touch_target_dp.minimum_width),Number(contract.camera_and_touch.camera_touch_target_dp.minimum_height));
    const ctaCameraGap=rules.cta?(safeHeight<rules.cta.threshold?rules.cta.compact:rules.cta.regular):6;
    const footerTopPadding=rules.footerTop?(safeHeight<rules.footerTop.threshold?rules.footerTop.compact:rules.footerTop.regular):2;
    const footerBottomPadding=rules.footerBottom?(safeHeight<rules.footerBottom.threshold?rules.footerBottom.compact:Number(bottomInset)>=rules.footerBottom.inset?rules.footerBottom.inset:rules.footerBottom.regular):2;
    const footerHeight=footerTopPadding+48*fontScale+ctaCameraGap+cameraTouchSize+footerBottomPadding;
    const topHeight = 48 + headerGap + 55 * fontScale;
    const footerFormula=String(contract.responsive_monotonicity.camera_visual_formula||'');
    const cameraFormulaConflict=!cameraFormula||!footerFormula;
    const availableHero = safeHeight - topHeight - recentHeight - titleGap - footerHeight - baseAB - baseBC;
    const readableRange = String(contract.hero_and_peek.hero_readable_height_range_dp || '148-220').match(/(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)/);
    const minimumHeroBase = readableRange ? Number(readableRange[1]) : 148;
    const exception = contract.hero_and_peek.short_screen_exception || {};
    const exceptionViewport = String(exception.viewport || '').match(/(\d+)x(\d+)/i);
    const usesShortScreenException = !!exceptionViewport && safeWidth === Number(exceptionViewport[1]) &&
      Number(height) === Number(exceptionViewport[2]) && fontScale === Number(exception.font_scale);
    const minimumHero = usesShortScreenException ? Number(exception.height_dp) : minimumHeroBase;
    const heroHeight = Math.min(desiredHeroHeight, Math.max(minimumHero, availableHero));
    const actualHeroWidth = heroHeight * ratio[0] / ratio[1];
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
    const peek = contract.hero_and_peek.peek;
    const peekWidth = actualHeroWidth * peek.width_max_relative_to_main;
    const peekHeight = heroHeight * peek.height_relative_to_main;
    const peekY = heroY + peek.top_offset_dp;
    const peekGap = Math.max(0, Number(peek.max_geometry_overlap_dp));
    const rects = {
      top:{x:0,y:Number(topInset),w:safeWidth,h:topHeight},
      stats:{x:20,y:Number(topInset)+48+headerGap,w:safeWidth-40,h:55*fontScale},
      recent:{x:20,y:recentY,w:safeWidth-40,h:recentHeight},
      hero:{x:(safeWidth-actualHeroWidth)/2,y:heroY,w:actualHeroWidth,h:heroHeight},
      'peek-left':{x:(safeWidth-actualHeroWidth)/2-peekWidth-peekGap,y:peekY,w:peekWidth,h:peekHeight},
      'peek-right':{x:(safeWidth+actualHeroWidth)/2+peekGap,y:peekY,w:peekWidth,h:peekHeight},
      footer:{x:0,y:footerY,w:safeWidth,h:footerHeight},
      cta:{x:20,y:footerY+footerTopPadding,w:safeWidth-40,h:48*fontScale},
      camera:{x:(safeWidth-cameraTouchSize)/2,y:footerY+footerTopPadding+48*fontScale+ctaCameraGap,w:cameraTouchSize,h:cameraTouchSize}
    };
    // No verified independent FishRecord originals are registered for this design preview.
    const conflicts = [];
    if (authorityConflict) conflicts.push('SPEC_CONFLICT · 正式 Markdown A.2 与机器合同的剩余空间公式不一致或无法解析');
    if (safeWidth === 480) conflicts.push('SPEC_CONFLICT · M05 仅冻结 480dp 宽度；当前高度只是自由视口示例，24dp 静态间距不能推定为 Android Runtime 结果');
    if (formulaConflict) conflicts.push('SPEC_CONFLICT · 正式机器合同中的响应式公式无法解析；当前回退仅供诊断，不是合同计算结果');
    if (cameraFormulaConflict) conflicts.push('SPEC_CONFLICT · 正式机器合同相机尺寸公式无法解析');
    if (allocationInvalid) conflicts.push('SPEC_CONFLICT · 机器合同剩余空间分配表达式无法解析或总和不为 1');
    if (availableHero < minimumHero) conflicts.push('SPEC_CONFLICT · Hero 可读高度与安全可用高度不能同时满足');
    if (heroHeight < 148 && actualHeroWidth < 145) conflicts.push('SPEC_CONFLICT · 124dp 短屏特例的 Hero 宽度不足 145dp');
    if (footerY + footerHeight > height - bottomInset + .5) conflicts.push('SPEC_CONFLICT · Footer 超出底部安全区');
    if (heroHeight < 148 && !(safeWidth === 320 && height === 480 && fontScale === 1)) conflicts.push('SPEC_CONFLICT · Hero 低于 148dp');
    return {safeWidth,safeHeight,hA,hB,hC,baseAB,baseBC,S,gAB,gBC,titleGap,heroHeight,actualHeroWidth,rects,conflicts,
      fontViewport,fontSp:headingSp,cameraSize,cameraTouchSize,ctaCameraGap,footerTopPadding,footerBottomPadding,contractId:contract.foundation_layout_principles.foundation_id};
  }
  function mount(root, view, contract, manifest, build, markdownContract = '', resourceBinding = {}, resourceDiagnostics = []) {
    const authorityConflict = hasAuthorityConflict(markdownContract, contract);
    if (!root || !contract?.foundation_layout_principles || !Array.isArray(manifest?.assets)) throw new Error('NH01 workspace mount dependencies are incomplete');
    const workspace=document.createElement('section');
    workspace.className='nh01-structured'; workspace.dataset.testid='nh01-structured-workspace';
    const options=profiles.map(([w,h],i)=>`<option value="${i}">${i===4?'480 dp 宽度边界 · 高度保持当前值':`${w} × ${h} dp`}</option>`).join('');
    workspace.innerHTML=`<header class="nh01-structured-heading"><div><small>DESIGN WORKSPACE · ${contract.foundation_layout_principles.foundation_id} ${contract.foundation_layout_principles.version}</small><h2>结构化高保真 · 多屏静态与响应式规范</h2><p>图层树、响应式画布和属性检查器共享正式机器合同中的布局计算。照片素材未核准时保留阻塞占位；本工作区是 Web 设计预览，Android Runtime = NOT_VERIFIED。</p>${authorityConflict?'<p class="nh01-authority-conflict" role="alert">SPEC_CONFLICT · 正式 Markdown A.2 与机器合同的剩余空间公式不一致或无法解析；画布按可读取的机器合同计算并保留冲突标记。</p>':''}</div><code>源码 ${build}</code></header>
      <div class="nh01-structured-controls"><label>视口 <select data-control="profile">${options}</select></label><label>宽 <input data-control="width" type="number" min="280" max="900" value="320"> dp</label><label>高 <input data-control="height" type="number" min="400" max="1400" value="480"> dp</label><label>字体 <input data-control="font" type="range" min="1" max="1.5" step="0.05" value="1"><output data-output="font">1.00×</output></label><label>画布 <input data-control="zoom" type="range" min="0.4" max="1.2" step="0.05" value="0.8"><output data-output="zoom">80%</output></label><details><summary>安全区输入</summary><label>上 <input data-control="top" type="number" min="0" max="80" value="24"> dp</label><label>下 <input data-control="bottom" type="number" min="0" max="80" value="24"> dp</label></details></div>
      <div class="nh01-structured-grid"><nav class="nh01-layer-tree" aria-label="NH01 图层树"><h3>页面图层</h3><div data-layer-tree></div></nav><div class="nh01-canvas-area"><div class="nh01-canvas-scroll"><div class="nh01-artboard" data-artboard></div></div><div class="nh01-comparison" data-comparison></div></div><aside class="nh01-inspector" aria-label="图层属性检查器"><h3>属性与响应式规则</h3><div data-inspector></div></aside></div>
      <details class="nh01-development-basis"><summary>开发依据与验收要求</summary><p>正式：${contract.contract_id} ${contract.contract_version} / ${contract.foundation_layout_principles.foundation_id}；静态参考：${manifest.contract_id}。Android Runtime：NOT_VERIFIED。结构化预览不改变五张冻结原图的权威性。</p><p><a href="${'../../'+view.machine_authority}" target="_blank" rel="noreferrer">机器合同</a> · <a href="${'../../'+view.authority}" target="_blank" rel="noreferrer">正式文字合同</a> · <a href="${'../../'+view.visual_authority_manifest}" target="_blank" rel="noreferrer">五屏资产清单</a></p><div data-basis></div><details open><summary>资源加载审计 · READY</summary><div data-resource-report></div></details></details>`;
    root.querySelector('.nh01-static-workspace').prepend(workspace);
    const get=name=>workspace.querySelector(`[data-control="${name}"]`);
    let selected='hero';
    const assetByProfile=manifest.assets || [];
    const backgroundAsset=(resourceBinding.resources || []).find(item=>item.path?.endsWith('/Morning_Lake_Master_V1.png'));
    const avatarAsset=(resourceBinding.resources || []).find(item=>item.path?.endsWith('/nh07_default_avatar_1x_64.png'));
    const brandAsset=resourceBinding.brand_title_binding;
    const cameraAsset=resourceBinding.camera_primary_binding;
    const safeText=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const assetUrl=path=>'../../'+path+'?v='+encodeURIComponent(build);
    const resourceByPath=path=>resourceDiagnostics.find(item=>item.url?.includes(path));
    const resourceStatusMarkup=()=>resourceDiagnostics.map(item=>`<tr data-resource-id="${safeText(item.id)}"><td>${safeText(item.label||item.id)}</td><td>${safeText(item.status)}</td><td>${safeText(item.http_status??'—')}</td><td>${safeText(item.sha256||'—')}</td><td>Content-Type ${safeText(item.content_type||'未返回')}<br><code>${safeText(item.url)}</code><br>开始 ${safeText(item.started_at||'未开始')} · 完成 ${safeText(item.completed_at||'进行中')}<br>${safeText(item.failure_reason||'')}</td></tr>`).join('');
    workspace.querySelector('[data-resource-report]').innerHTML=`<table class="nh01-resource-table"><thead><tr><th>资源</th><th>状态</th><th>HTTP</th><th>SHA-256</th><th>URL / 失败原因</th></tr></thead><tbody>${resourceStatusMarkup()}</tbody></table>`;
    function render() {
      const profile=Number(get('profile').value), w=Number(get('width').value), h=Number(get('height').value), font=Number(get('font').value), zoom=Number(get('zoom').value);
      const result=calculate(w,h,font,Number(get('top').value),Number(get('bottom').value),contract,authorityConflict);
      workspace.dataset.loadState='READY';
      workspace.querySelector('[data-output="font"]').textContent=font.toFixed(2)+'×';
      workspace.querySelector('[data-output="zoom"]').textContent=Math.round(zoom*100)+'%';
      const board=workspace.querySelector('[data-artboard]'); board.style.width=result.safeWidth+'px';board.style.height=h+'px';board.style.transform=`scale(${zoom})`;board.style.transformOrigin='top left';
      board.style.backgroundImage=`linear-gradient(180deg,rgba(238,246,242,.12),rgba(234,244,240,.34)),url("${assetUrl(backgroundAsset.path)}")`;
      workspace.querySelector('.nh01-canvas-scroll').style.minHeight=(h*zoom+24)+'px';
      const rect=(id, extra='')=>{const r=result.rects[id];return `<button type="button" class="nh01-canvas-layer ${extra} ${selected===id?'selected':''}" data-layer="${id}" style="left:${r.x}px;top:${r.y}px;width:${r.w}px;height:${r.h}px" aria-label="${id}">`};
      board.innerHTML=rect('top','nh01-top')+`<img src="${assetUrl(brandAsset.registry_path)}" alt="渔见品牌标题"><img class="nh01-avatar" src="${assetUrl(avatarAsset.path)}" alt="默认头像 · FROZEN"></button>`+
        rect('stats','nh01-stats')+'<span>—<small>累计鱼获</small></span><span>—<small>鱼种</small></span><span>—<small>足迹</small></span></button>'+
        rect('recent','nh01-recent')+`<strong style="font-size:${result.fontSp}px">最近鱼获</strong><span>全部 ›</span></button>`+
        rect('hero','nh01-hero')+'<span>真实鱼获照片来源未核准<br>BLOCKED_ASSET_SOURCE</span><strong>鱼种与元数据：未提供</strong></button>'+
        rect('peek-left','nh01-peek nh01-peek-left')+'<span>缺少获批的真实邻卡素材<br>BLOCKED</span></button>'+rect('peek-right','nh01-peek nh01-peek-right')+'<span>缺少获批的真实邻卡素材<br>BLOCKED</span></button>'+
        rect('footer','nh01-footer')+'</button>'+rect('cta','nh01-cta')+'记录下一条鱼获</button>'+
        rect('camera','nh01-camera')+`<img style="width:${result.cameraSize}px;height:${result.cameraSize}px" src="${assetUrl(cameraAsset.registry_path)}" alt="拍照按钮视觉资产 · touch ${result.cameraTouchSize} dp"></button>`;
      workspace.querySelector('[data-layer-tree]').innerHTML=layers.map(([id,name,group])=>`<button type="button" data-select="${id}" class="${selected===id?'selected':''}" aria-pressed="${selected===id}"><span>${safeText(name)}</span><small>${group} · ${id.startsWith('peek')?'BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS':id==='hero'?'BLOCKED_ASSET_SOURCE':id==='top'?'FROZEN_COMPONENT_ASSETS':'DESIGN PREVIEW'}</small></button>`).join('');
      const current=result.rects[selected];
      const node=current?board.querySelector(`[data-layer="${selected}"]`):null;
      const measured=node?.getBoundingClientRect();
      const asset=profile<5?assetByProfile[profile]:null;
      const selectedAssets = selected==='top' ? [backgroundAsset, brandAsset, avatarAsset]
        : selected==='camera' ? [cameraAsset]
        : selected==='hero' ? [{asset_id:'fish_record_hero',path:'未提供',sha256:'未提供',status:'BLOCKED_ASSET_SOURCE'}]
        : selected.startsWith('peek') ? [{asset_id:'independent_fish_neighbor',path:'未提供',sha256:'未提供',status:'BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS'}]
        : [];
      const assetRows = selectedAssets.map(item=>{const path=item?.path||item?.registry_path||'';return `<p><strong>${safeText(item?.asset_id||item?.component||'未提供')}</strong> · ${safeText(item?.status||'未提供')} · ${safeText(resourceByPath(path)?.status||'未验证')}<br><code>${safeText(path||'未提供')}</code><br>SHA-256 ${safeText(item?.sha256||'未提供')}</p>`;}).join('') || '<p>位图资产：不适用；图层由页面结构和正式布局规则呈现。</p>';
      const referenceRow = asset ? `<p><strong>冻结静态对照</strong> · ${safeText(asset.status)} · version ${safeText(asset.version||'未提供')}<br><code>${safeText(asset.path)}</code><br>SHA-256 ${safeText(asset.sha256)}</p>` : '';
      const allocationA=contract.foundation_layout_principles.allocation_when_surplus_positive.a_to_b_final;
      const allocationB=contract.foundation_layout_principles.allocation_when_surplus_positive.b_to_c_final;
      const contractMeta=resourceDiagnostics.find(item=>item.id==='machine-contract');
      const manifestMeta=resourceDiagnostics.find(item=>item.id==='frozen-manifest');
      const blocked=selected==='hero'||selected.startsWith('peek');
      workspace.querySelector('[data-inspector]').innerHTML=`<dl><dt>图层 ID / 名称</dt><dd>${safeText(selected)} · ${safeText(layers.find(x=>x[0]===selected)?.[1])}</dd><dt>逻辑组</dt><dd>${safeText(layers.find(x=>x[0]===selected)?.[2])}</dd><dt>当前视口 / 安全区</dt><dd>${w} × ${h} dp · fontScale ${font} · top ${get('top').value} / bottom ${get('bottom').value} dp</dd><dt>计算位置 / 尺寸</dt><dd>${current?`${current.x.toFixed(1)}, ${current.y.toFixed(1)} · ${current.w.toFixed(1)} × ${current.h.toFixed(1)} dp`:'未提供'}</dd><dt>浏览器实测</dt><dd>${measured?`${(measured.width/zoom).toFixed(1)} × ${(measured.height/zoom).toFixed(1)} CSS px（除以画布缩放）`:'未提供'}</dd><dt>字号 / 相机</dt><dd>${['recent','hero'].includes(selected)?result.fontSp.toFixed(1)+' CSS px（预览）':'—'} · 相机视觉 ${result.cameraSize.toFixed(1)} / 触控 ${result.cameraTouchSize} dp</dd><dt>资产来源与状态</dt><dd>${assetRows}${referenceRow}${blocked?'<p role="status">ASSET_BLOCKED · 缺少已批准的真实 FishRecord 照片；未使用替代照片。</p>':''}</dd><dt>正式合同 / Manifest</dt><dd>${safeText(contract.contract_id)} · ${safeText(result.contractId)} ${safeText(contract.foundation_layout_principles.version)}<br><code>${safeText(view.machine_authority)}</code> · SHA-256 ${safeText(contractMeta?.sha256||'未验证')}<br><code>${safeText(view.visual_authority_manifest)}</code> · SHA-256 ${safeText(manifestMeta?.sha256||'未验证')}</dd><dt>正式公式</dt><dd>fontViewport：${safeText(contract.responsive_monotonicity.font_viewport_scale_formula)}<br>Hero：${safeText(contract.responsive_monotonicity.hero_width_formula)}<br>Camera：${safeText(contract.responsive_monotonicity.camera_visual_formula)}</dd><dt>布局约束</dt><dd>${result.conflicts.length?result.conflicts.join('; '):'当前模型未发现约束冲突'} · Android NOT_VERIFIED</dd></dl><h4>剩余空间计算</h4><code>H_safe=${result.safeHeight.toFixed(1)}<br>hA=${result.hA.toFixed(1)}, hB=${result.hB.toFixed(1)}, hC=${result.hC.toFixed(1)}<br>gAB_base=${result.baseAB.toFixed(1)}, gBC_base=${result.baseBC.toFixed(1)}<br>S=max(0,H_safe-Σ)=${result.S.toFixed(1)}<br>${safeText(contract.foundation_layout_principles.version)}: ${safeText(allocationA)} = ${result.gAB.toFixed(1)}; ${safeText(allocationB)} = ${result.gBC.toFixed(1)}</code><p>Stats→Title ${result.baseAB.toFixed(1)} dp · Title→Hero ${result.titleGap.toFixed(1)} dp · CTA→Camera ${result.ctaCameraGap} dp · Footer bottom ${result.footerBottomPadding} dp</p>`;
      workspace.querySelector('[data-comparison]').innerHTML=asset?`<strong>冻结高保真对照 · ${safeText(asset.asset_id)}</strong><span>目标 ${asset.target_viewport_dp.width} × ${asset.target_viewport_dp.height??'高度未冻结'} dp · 原图 ${asset.actual_canvas_px.width} × ${asset.actual_canvas_px.height} px</span><img src="${assetUrl(asset.path)}" alt="${safeText(asset.asset_id)} 冻结参考">`:'<span>无对应冻结稿</span>';
      workspace.querySelector('[data-basis]').textContent=`当前资产 ${asset?.path||'未提供'} · SHA-256 ${asset?.sha256||'未提供'} · 静态 ${asset?.status||'未验证'}；当前 Web 布局计算绑定源码 ${build}。视觉差异与 Android bounds：未验证。`;
      board.querySelectorAll('[data-layer]').forEach(node=>node.onclick=()=>{selected=node.dataset.layer;render();});
      workspace.querySelectorAll('[data-select]').forEach(node=>node.onclick=()=>{selected=node.dataset.select;render();board.querySelector(`[data-layer="${selected}"]`)?.scrollIntoView({block:'nearest',inline:'nearest'});});
    }
    get('profile').onchange=()=>{const [w,h]=profiles[Number(get('profile').value)];get('width').value=w;if(h!==null)get('height').value=h;render();};
    ['width','height','font','zoom','top','bottom'].forEach(name=>get(name).addEventListener('input',render));
    render();
  }
  window.NH01StructuredWorkspace={mount,calculate,hasAuthorityConflict,allocationShare,createResourceLoader};
})();
