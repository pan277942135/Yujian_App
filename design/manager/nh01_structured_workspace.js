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
  const layerIdsBySource = {
    '41:30':'page','41:31':'environment','41:32':'background','41:33':'header',
    '41:34':'brand','41:35':'avatar','41:37':'stats','41:49':'recent-header',
    '41:53':'recent-chevron','41:56':'pager','41:58':'peek-left','41:67':'hero',
    '41:76':'peek-right','41:86':'camera'
  };
  const layerIdsByName = {
    'Species Statistic':'stat-species','Catch Statistic':'stat-catch',
    'Record Days Statistic':'stat-days','Title / 最近鱼获':'recent-title',
    'All action':'recent-all'
  };
  const layerGroups = {
    page:'PAGE',environment:'BACKGROUND',background:'BACKGROUND',
    header:'A',brand:'A',avatar:'A',stats:'A','stat-species':'A','stat-catch':'A','stat-days':'A',
    'recent-header':'B','recent-title':'B','recent-all':'B','recent-chevron':'B',
    pager:'B','peek-left':'B',hero:'B','peek-right':'B','pager-indicator':'B',
    'capture-action-group':'C',cta:'C',camera:'C'
  };
  function createLayerTree(componentTree, structure) {
    if (componentTree?.document_type !== 'component_tree' || componentTree?.root?.id !== '41:30') {
      throw new Error('NH01 组件树不能映射到正式页面根节点 41:30');
    }
    function convert(node, parentId = null) {
      const sourceId = node.id ? String(node.id) : null;
      let id = sourceId ? layerIdsBySource[sourceId] : layerIdsByName[node.name];
      if (sourceId === '41:85') id = parentId === 'page' ? 'capture-action-group' : 'cta';
      if (!id) throw new Error(`NH01 组件树节点未绑定稳定 DOM 图层：${node.name || sourceId || 'unknown'}`);
      const children = (node.children || []).map(child => convert(child, id));
      return {
        id,
        name:node.name || id,
        sourceNodeId:sourceId,
        sourceAssetId:node.asset_id||null,
        componentId:node.component_id || null,
        kind:node.type || 'UNKNOWN',
        mappingStatus:node.node_status || node.status || (sourceId ? 'SOURCE_NODE_MAPPED' : 'NOT_IN_MAPPING_SNAPSHOT'),
        logicalGroup:layerGroups[id] || 'UNASSIGNED',
        parentId,
        children
      };
    }
    const root=convert(componentTree.root);
    const captureAction=findLayer(root,'capture-action-group');
    const cta=(structure?.components||[]).find(component=>component.id==='cta');
    if (captureAction && cta && !captureAction.children.some(child=>child.id==='cta')) {
      captureAction.children.unshift({
        id:'cta',name:cta.name||'Capture CTA',sourceNodeId:null,sourceReference:cta.figma_node_id||null,
        componentId:cta.component_id||null,kind:cta.kind||'text_action',
        mappingStatus:'IMPLEMENTATION_COMPONENT · individual Figma child ID is not mapped',logicalGroup:'C',
        parentId:'capture-action-group',children:[]
      });
    }
    const pager=findLayer(root,'pager');
    if (pager && !pager.children.some(child=>child.id==='pager-indicator')) {
      pager.children.push({id:'pager-indicator',name:'Pager indicator · preview fixture',sourceNodeId:null,
        sourceReference:null,componentId:null,kind:'PREVIEW_ONLY',mappingStatus:'PREVIEW_ONLY',
        logicalGroup:'B',parentId:'pager',children:[]});
    }
    return root;
  }
  function flattenLayerTree(root) {
    const output=[];
    const walk=node=>{output.push(node);node.children.forEach(walk);};
    if (root) walk(root);
    return output;
  }
  function findLayer(root, id) {
    return flattenLayerTree(root).find(layer=>layer.id===id) || null;
  }
  function measuredLayerBounds(node, board, zoom) {
    if (!node || !board) return null;
    const rect=node.getBoundingClientRect(), origin=board.getBoundingClientRect(), scale=Number(zoom)||1;
    return {x:(rect.left-origin.left)/scale,y:(rect.top-origin.top)/scale,w:rect.width/scale,h:rect.height/scale};
  }
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
  function resultLineHeight(size, ratio, fallback) {
    const value=Number(ratio);
    return Number(size)*(Number.isFinite(value)&&value>0?value:fallback);
  }
  function calculate(width, height, fontScale, topInset, bottomInset, contract, authorityConflict = false, previewRules = {}) {
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
    const roleSize=(name,fallback,min,max)=>{
      const role=(contract.typography.roles||[]).find(item=>item.role===name)||{};
      return clamp(safeHeight*Number(role.base_ratio||fallback),Number(role.minimum_sp||min),Number(role.maximum_sp||max))*fontViewport*fontScale;
    };
    const fontSizes={
      statisticsValue:roleSize('Statistics number',.03,17,21),
      statisticsLabel:roleSize('Statistics label',.019,11,13),
      recentHeading:headingSp,
      recentAction:roleSize('All entry',.021,13,15),
      heroSpecies:roleSize('Species name',.036,20,26),
      heroMeasurement:roleSize('Measurements',.023,13,16),
      heroMetadata:roleSize('Time and location metadata',.019,11,13),
      cta:roleSize('Record-next-catch CTA',.021,12,14)
    };
    const brandWidth=clamp(safeWidth*(155/1080),46,70);
    const brandHeight=brandWidth*(80/155);
    const avatarSize=clamp(safeWidth*.10,36,40);
    const avatarTouchSize=Math.max(48,avatarSize);
    const cameraFormula=String(contract.responsive_monotonicity.camera_visual_formula||'').match(/^min\(min\(safeWidthDp,([\d.]+)dp\)\*([\d.]+),safeHeightDp\*([\d.]+),([\d.]+)dp\), with ([\d.]+)dp floor$/);
    const cameraSize=cameraFormula?Math.max(Number(cameraFormula[5]),Math.min(Math.min(safeWidth,Number(cameraFormula[1]))*Number(cameraFormula[2]),safeHeight*Number(cameraFormula[3]),Number(cameraFormula[4]))):Number(contract.camera_and_touch.camera_visual_size_dp.minimum);
    const cameraTouchSize=Math.max(cameraSize,Number(contract.camera_and_touch.camera_touch_target_dp.minimum_width),Number(contract.camera_and_touch.camera_touch_target_dp.minimum_height));
    const ctaCameraGap=rules.cta?(safeHeight<rules.cta.threshold?rules.cta.compact:rules.cta.regular):6;
    const footerTopPadding=rules.footerTop?(safeHeight<rules.footerTop.threshold?rules.footerTop.compact:rules.footerTop.regular):2;
    const footerBottomPadding=rules.footerBottom?(safeHeight<rules.footerBottom.threshold?rules.footerBottom.compact:Number(bottomInset)>=rules.footerBottom.inset?rules.footerBottom.inset:rules.footerBottom.regular):2;
    const ctaRole=(contract.typography.roles||[]).find(role=>role.role==='Record-next-catch CTA')||{};
    const ctaLineHeight=resultLineHeight(fontSizes.cta,ctaRole.line_height_ratio,1.25);
    const footerHeight=footerTopPadding+ctaLineHeight+ctaCameraGap+cameraTouchSize+footerBottomPadding;
    const topHeight = 48 + headerGap + 55 * fontScale;
    const footerFormula=String(contract.responsive_monotonicity.camera_visual_formula||'');
    const cameraFormulaConflict=!cameraFormula||!footerFormula;
    const indicatorHeight=Number(previewRules.pager?.indicator_height_dp??8);
    const indicatorGap=Number(previewRules.pager?.indicator_gap_after_hero_dp??8);
    const indicatorStack=indicatorHeight+indicatorGap;
    const pagerGapRule=String(previewRules.pager?.peek_gap_formula||'').match(/^clamp\(([\d.]+)dp,trackWidthDp\*([\d.]+),([\d.]+)dp\)$/);
    const peekGap=pagerGapRule?clamp(safeWidth*Number(pagerGapRule[2]),Number(pagerGapRule[1]),Number(pagerGapRule[3])):clamp(safeWidth*.025,8,12);
    const maxGeometryOverlap=Number(previewRules.pager?.max_geometry_overlap_dp??1);
    const gapABCap=Number(previewRules.vertical_allocation?.additional_gap_ab_max_dp??24);
    const gapBCCap=Number(previewRules.vertical_allocation?.additional_gap_bc_max_dp??40);
    const pagerVisibleWidthFormula=String(previewRules.pager?.peek_visible_width_formula||'');
    const previewRulesConflict=!previewRules.rules_id||previewRules.status!=='PREVIEW_ONLY'||!pagerGapRule||pagerVisibleWidthFormula!=='min(peekWidthDp,max(0,(trackWidthDp-heroWidthDp)/2-peekGapDp))'||!Number.isFinite(indicatorHeight)||!Number.isFinite(indicatorGap);
    const fixedBeforePager=topHeight+recentHeight+titleGap+footerHeight+baseAB+baseBC;
    const pagerBudgetRaw=safeHeight-fixedBeforePager;
    const pagerBudget=Math.max(0,pagerBudgetRaw);
    const availableHero=Math.max(0,pagerBudget-indicatorStack);
    const readableRange = String(contract.hero_and_peek.hero_readable_height_range_dp || '148-220').match(/(\d+(?:\.\d+)?)-(\d+(?:\.\d+)?)/);
    const minimumHeroBase = readableRange ? Number(readableRange[1]) : 148;
    const exception = contract.hero_and_peek.short_screen_exception || {};
    const exceptionViewport = String(exception.viewport || '').match(/(\d+)x(\d+)/i);
    const usesShortScreenException = !!exceptionViewport && safeWidth === Number(exceptionViewport[1]) &&
      Number(height) === Number(exceptionViewport[2]) && fontScale === Number(exception.font_scale);
    const previewHeroMinWidth=Number(previewRules.hero?.short_320x480_font_1_minimum_width_dp??exception.min_width_dp??0);
    const minimumHero=Math.max(minimumHeroBase,usesShortScreenException?previewHeroMinWidth*ratio[1]/ratio[0]:0);
    const heroHeight=pagerBudget<=0?minimumHero:Math.min(desiredHeroHeight,Math.max(minimumHero,availableHero));
    const actualHeroWidth = heroHeight * ratio[0] / ratio[1];
    const targetPagerContentHeight=desiredHeroHeight+indicatorStack;
    const targetTotal=topHeight+baseAB+recentHeight+titleGap+baseBC+footerHeight+targetPagerContentHeight;
    const targetSurplus=safeHeight-targetTotal;
    const S=Math.max(0,targetSurplus);
    const gapABExtra=Math.min(S/2,gapABCap);
    const gapBCExtra=Math.min(Math.max(0,S-gapABExtra),gapBCCap);
    const outerBreathing=(S-gapABExtra-gapBCExtra)/2;
    const gAB=baseAB+gapABExtra;
    const gBC=baseBC+gapBCExtra;
    const pagerHeight=targetSurplus>=0?targetPagerContentHeight:pagerBudget;
    const pagerContentHeight=heroHeight+indicatorStack;
    const pagerNeedsScroll=pagerContentHeight>pagerHeight+.1;
    const hA=topHeight,hB=recentHeight+titleGap+pagerHeight,hC=footerHeight;
    const recentY=Number(topInset)+outerBreathing+hA+gAB;
    const heroY = recentY + recentHeight + titleGap;
    const footerY = heroY + pagerHeight + gBC;
    const peek = contract.hero_and_peek.peek;
    const peekWidth = actualHeroWidth * peek.width_max_relative_to_main;
    const peekHeight = heroHeight * peek.height_relative_to_main;
    const peekY = heroY + peek.top_offset_dp;
    const peekReveal=Math.min(peekWidth,Math.max(0,(safeWidth-actualHeroWidth)/2-peekGap));
    const indicatorWidth=40;
    const rects = {
      page:{x:0,y:0,w:safeWidth,h:Number(height)},
      environment:{x:0,y:0,w:safeWidth,h:Number(height)},
      background:{x:0,y:0,w:safeWidth,h:Number(height)},
      header:{x:0,y:Number(topInset)+outerBreathing,w:safeWidth,h:48},
      brand:{x:20,y:Number(topInset)+outerBreathing+(48-brandHeight)/2,w:brandWidth,h:brandHeight},
      avatar:{x:safeWidth-20-avatarTouchSize,y:Number(topInset)+outerBreathing+(48-avatarTouchSize)/2,w:avatarTouchSize,h:avatarTouchSize},
      stats:{x:20,y:Number(topInset)+outerBreathing+48+headerGap,w:safeWidth-40,h:55*fontScale},
      recent:{x:20,y:recentY,w:safeWidth-40,h:recentHeight},
      'recent-header':{x:20,y:recentY,w:safeWidth-40,h:recentHeight},
      pager:{x:0,y:heroY,w:safeWidth,h:pagerHeight},
      hero:{x:(safeWidth-actualHeroWidth)/2,y:heroY,w:actualHeroWidth,h:heroHeight},
      'peek-left':{x:(safeWidth-actualHeroWidth)/2-peekWidth-peekGap,y:peekY,w:peekWidth,h:peekHeight},
      'peek-right':{x:(safeWidth+actualHeroWidth)/2+peekGap,y:peekY,w:peekWidth,h:peekHeight},
      'pager-indicator':{x:(safeWidth-indicatorWidth)/2,y:heroY+heroHeight+indicatorGap,w:indicatorWidth,h:indicatorHeight},
      footer:{x:0,y:footerY,w:safeWidth,h:footerHeight},
      'capture-action-group':{x:0,y:footerY,w:safeWidth,h:footerHeight},
      cta:{x:20,y:footerY+footerTopPadding,w:safeWidth-40,h:ctaLineHeight},
      camera:{x:(safeWidth-cameraTouchSize)/2,y:footerY+footerTopPadding+ctaLineHeight+ctaCameraGap,w:cameraTouchSize,h:cameraTouchSize},
      'camera-artwork':{x:(safeWidth-cameraSize)/2,y:footerY+footerTopPadding+ctaLineHeight+ctaCameraGap+(cameraTouchSize-cameraSize)/2,w:cameraSize,h:cameraSize}
    };
    const conflicts = [];
    if (authorityConflict) conflicts.push('SPEC_CONFLICT · 正式 Markdown A.2 与机器合同的剩余空间公式不一致或无法解析');
    if (formulaConflict) conflicts.push('SPEC_CONFLICT · 正式机器合同中的响应式公式无法解析；当前回退仅供诊断，不是合同计算结果');
    if (cameraFormulaConflict) conflicts.push('SPEC_CONFLICT · 正式机器合同相机尺寸公式无法解析');
    if (previewRulesConflict) conflicts.push('PREVIEW_RULES_ERROR · 缺少有效 PREVIEW_ONLY 结构化布局规则');
    if (pagerNeedsScroll) conflicts.push('PREVIEW_LOCAL_SCROLL · FishCatchPager 内容按可读 Hero 比例保留并在 Pager 内部滚动');
    if (pagerBudgetRaw<0) conflicts.push('SAFE_OVERFLOW · 固定 Header/Stats/标题/Footer 已超过 safeContentHeight');
    if (heroHeight<minimumHero-.1) conflicts.push('FAIL_HERO_MINIMUM · Hero 低于最小可读高度');
    if (footerY + footerHeight > height - bottomInset + .5) conflicts.push('SPEC_CONFLICT · Footer 超出底部安全区');
    return {safeWidth,safeHeight,hA,hB,hC,baseAB,baseBC,S,targetSurplus,gapABExtra,gapBCExtra,outerBreathing,gAB,gBC,titleGap,
      heroHeight,targetHeroWidth:heroWidth,actualHeroWidth,desiredHeroHeight,availableHero,pagerHeight,pagerContentHeight,pagerNeedsScroll,
      peekWidth,peekHeight,peekGap,peekReveal,maxGeometryOverlap,indicatorHeight,indicatorGap,ctaLineHeight,rects,conflicts,
      fontViewport,fontSp:headingSp,fontSizes,brandWidth,brandHeight,avatarSize,avatarTouchSize,headerGap,cameraSize,cameraTouchSize,ctaCameraGap,footerTopPadding,footerBottomPadding,contractId:contract.foundation_layout_principles.foundation_id,previewRulesId:previewRules.rules_id};
  }
  function mount(root, view, contract, manifest, build, markdownContract = '', resourceBinding = {}, resourceDiagnostics = [], componentTree = {}, structure = {}, avatarAuthority = '', previewRules = {}, previewMedia = {}) {
    const authorityConflict = hasAuthorityConflict(markdownContract, contract);
    if (!root || !contract?.foundation_layout_principles || !Array.isArray(manifest?.assets) || !structure?.preview_fixture || !avatarAuthority || previewRules?.status!=='PREVIEW_ONLY') {
      throw new Error('NH01 workspace mount dependencies are incomplete');
    }
    const layerRoot=createLayerTree(componentTree,structure);
    const layers=flattenLayerTree(layerRoot);
    if (new Set(layers.map(layer=>layer.id)).size!==layers.length) throw new Error('NH01 layer IDs must be unique');
    const workspace=document.createElement('section');
    const safeText=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    workspace.className='nh01-structured';
    workspace.dataset.testid='nh01-structured-workspace';
    const options=profiles.map(([w,h],i)=>`<option value="${i}" ${i===2?'selected':''}>${i===4?'480 dp 宽度边界 · 高度保持当前值':`${w} × ${h} dp`}</option>`).join('');
    workspace.innerHTML=`<header class="nh01-structured-heading"><div><small>DESIGN WORKSPACE · ${contract.foundation_layout_principles.foundation_id} ${contract.foundation_layout_principles.version}</small><h2>结构化高保真 · 多屏静态与响应式规范</h2><p>布局绑定现行组件与响应式合同；未冻结的 Pager 空间规则标注 PREVIEW_ONLY。Hero、previous、next 三张图均为 GENERATED_ILLUSTRATIVE_ONLY，不代表真实鱼获或记录数量。Android Runtime = NOT_VERIFIED。</p>${authorityConflict?'<p class="nh01-authority-conflict" role="alert">SPEC_CONFLICT · 正式 Markdown A.2 与机器合同的剩余空间公式不一致或无法解析；画布按可读取的机器合同计算并保留冲突标记。</p>':''}</div><code>源码 ${build}</code></header>
      <div class="nh01-structured-controls"><label>视口 <select data-control="profile">${options}</select></label><label>宽 <input data-control="width" type="number" min="280" max="900" value="390"> dp</label><label>高 <input data-control="height" type="number" min="400" max="1400" value="844"> dp</label><label>字体 <input data-control="font" type="range" min="1" max="1.5" step="0.05" value="1"><output data-output="font">1.00×</output></label><label>画布 <input data-control="zoom" type="range" min="0.4" max="1.2" step="0.05" value="0.7"><output data-output="zoom">70%</output></label><details><summary>安全区输入</summary><label>上 <input data-control="top" type="number" min="0" max="80" value="24"> dp</label><label>下 <input data-control="bottom" type="number" min="0" max="80" value="24"> dp</label></details></div>
      <div class="nh01-structured-grid"><nav class="nh01-layer-tree" aria-label="NH01 图层树"><h3>页面图层 <small>${safeText(componentTree.status||'SOURCE TREE')}</small></h3><div data-layer-tree></div></nav><div class="nh01-canvas-area"><div class="nh01-canvas-scroll"><div class="nh01-artboard" data-artboard data-layer="page" data-testid="nh01-canvas-page"></div></div><div class="nh01-comparison" data-comparison></div></div><aside class="nh01-inspector" aria-label="图层属性检查器"><h3>属性与响应式规则</h3><div data-inspector></div></aside></div>
      <details class="nh01-development-basis"><summary>开发依据与验收要求</summary><p>正式响应式合同：${contract.contract_id} ${contract.contract_version} / ${contract.foundation_layout_principles.foundation_id}；组件父子树：${componentTree.document_type} · ${componentTree.status||'REVIEW_REQUIRED'}；静态参考：${manifest.contract_id}。Android Runtime：NOT_VERIFIED。</p><p><a href="${'../../'+view.machine_authority}" target="_blank" rel="noreferrer">机器合同</a> · <a href="${'../../'+view.authority}" target="_blank" rel="noreferrer">正式文字合同</a> · <a href="../../design/manager/nh01_acceptance/data/NH01_Component_Tree_V1.json" target="_blank" rel="noreferrer">NH01 组件父子树</a> · <a href="../../design/pages/home/normal_home/engineering/Visual_Anchor_Asset_Resolution_V1.md" target="_blank" rel="noreferrer">NH01 头像视觉权威</a></p><div data-basis></div><details open><summary>资源加载审计</summary><div data-resource-report></div></details></details>`;
    const staticRoot=root.querySelector('.nh01-static-workspace');
    if (!staticRoot) throw new Error('NH01 static reference workspace container is missing');
    staticRoot.prepend(workspace);
    const get=name=>workspace.querySelector(`[data-control="${name}"]`);
    const treeContainer=workspace.querySelector('[data-layer-tree]');
    const board=workspace.querySelector('[data-artboard]');
    let selected='hero';
    const expanded=new Set(layers.filter(layer=>layer.children.length).map(layer=>layer.id));
    const assetByProfile=manifest.assets||[];
    const bindingResources=resourceBinding.resources||[];
    const backgroundAsset=bindingResources.find(item=>item.path?.endsWith('/Morning_Lake_Master_V1.png'));
    const avatarAsset={asset_id:'normal_home_default_avatar_v2',path:'app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png',sha256:'fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528',status:'APPROVED_NH01_HOME_V2_VISUAL_AUTHORITY'};
    const previewAssets=Array.isArray(previewMedia?.assets)?previewMedia.assets:[];
    const mediaForRole=role=>previewAssets.find(item=>item.role===role)||{id:`preview-placeholder-${role}`,role,label:`${role} preview placeholder`,path:null,sha256:null,asset_status:'PREVIEW_PLACEHOLDER'};
    const chevronAsset=bindingResources.find(item=>item.path?.endsWith('/ic_chevron_right_v1.svg'));
    const brandAsset=resourceBinding.brand_title_binding;
    const cameraAsset=resourceBinding.camera_primary_binding;
    const assetUrl=path=>'../../'+path+'?v='+encodeURIComponent(build);
    const resourceByPath=path=>path?resourceDiagnostics.find(item=>item.url?.includes(path)):null;
    const resourceStatusMarkup=()=>resourceDiagnostics.map(item=>`<tr data-resource-id="${safeText(item.id)}"><td>${safeText(item.label||item.id)}</td><td>${safeText(item.status)}</td><td>${safeText(item.http_status??'—')}</td><td>${safeText(item.sha256||'—')}</td><td>Content-Type ${safeText(item.content_type||'未返回')}<br><code>${safeText(item.url)}</code><br>开始 ${safeText(item.started_at||'未开始')} · 完成 ${safeText(item.completed_at||'进行中')}<br>${safeText(item.failure_reason||'')}</td></tr>`).join('');
    workspace.querySelector('[data-resource-report]').innerHTML=`<table class="nh01-resource-table"><thead><tr><th>资源</th><th>状态</th><th>HTTP</th><th>SHA-256</th><th>URL / 失败原因</th></tr></thead><tbody>${resourceStatusMarkup()}</tbody></table>`;
    const assetStatusFor=id=>({
      page:'STRUCTURE_PREVIEW',environment:'COMPOSE_CONTAINER',background:'FROZEN_BACKGROUND',header:'SOURCE_MAPPED',brand:'FROZEN_BRAND_ARTWORK',
      avatar:'APPROVED_NH01_HOME_V2_FROZEN',stats:'STATIC_SAMPLE_FIXTURE','stat-species':'STATIC_SAMPLE_FIXTURE','stat-catch':'STATIC_SAMPLE_FIXTURE','stat-days':'STATIC_SAMPLE_FIXTURE',
      'recent-header':'SOURCE_MAPPED','recent-title':'REFERENCE_COPY','recent-all':'REFERENCE_COPY','recent-chevron':'FROZEN_ICON',pager:'PREVIEW_ONLY_LAYOUT',
      'peek-left':'GENERATED_ILLUSTRATIVE_ONLY',hero:'GENERATED_ILLUSTRATIVE_ONLY','peek-right':'GENERATED_ILLUSTRATIVE_ONLY','pager-indicator':'PREVIEW_ONLY_INDICATOR',
      'capture-action-group':'SOURCE_MAPPED',cta:'REFERENCE_COPY',camera:'FROZEN_CAMERA'
    })[id]||'NOT_CLASSIFIED';
    function assetRowsFor(id) {
      const rows={
        background:[backgroundAsset],brand:[brandAsset],
        avatar:[avatarAsset],
        'recent-chevron':[chevronAsset],camera:[cameraAsset],
        hero:[mediaForRole('hero')],
        'peek-left':[mediaForRole('neighbor_previous')],
        'peek-right':[mediaForRole('neighbor_next')]
      };
      return rows[id]||[];
    }
    const profileForAsset=profile=>profile>=0&&profile<assetByProfile.length?assetByProfile[profile]:null;
    function treeNodeMarkup(layer,depth=0) {
      const hasChildren=layer.children.length>0, isOpen=expanded.has(layer.id), status=assetStatusFor(layer.id);
      const icon=layer.kind==='IMAGE_LAYER'?'▧':layer.kind==='FRAME'?'▱':layer.kind==='BUTTON'?'◉':'◦';
      return `<li class="nh01-tree-node" data-tree-node="${safeText(layer.id)}"><div class="nh01-tree-row" style="--tree-depth:${depth}"><button type="button" class="nh01-tree-select ${selected===layer.id?'selected':''}" data-select="${safeText(layer.id)}" data-testid="nh01-layer-${safeText(layer.id)}" data-source-node-id="${safeText(layer.sourceNodeId||'NOT_MAPPED')}" data-source-asset-id="${safeText(layer.sourceAssetId||'NOT_MAPPED')}" aria-pressed="${selected===layer.id}"><span class="nh01-tree-icon" aria-hidden="true">${icon}</span><span class="nh01-tree-copy"><span>${safeText(layer.name)}</span><small>${safeText(layer.logicalGroup)} · ${safeText(status)}${layer.sourceAssetId?` · source ${safeText(layer.sourceAssetId)}`:''}</small></span></button>${hasChildren?`<button type="button" class="nh01-tree-toggle" data-toggle="${safeText(layer.id)}" aria-label="${isOpen?'折叠':'展开'} ${safeText(layer.name)}" aria-expanded="${isOpen}">${isOpen?'▾':'▸'}</button>`:''}</div>${hasChildren?`<ul class="nh01-tree-children" data-children-of="${safeText(layer.id)}" ${isOpen?'':'hidden'}>${layer.children.map(child=>treeNodeMarkup(child,depth+1)).join('')}</ul>`:''}</li>`;
    }
    function renderTree() {
      treeContainer.innerHTML=`<ul class="nh01-tree-root">${treeNodeMarkup(layerRoot)}</ul>`;
    }
    function expandParents(id) {
      let layer=findLayer(layerRoot,id);
      while(layer?.parentId){expanded.add(layer.parentId);layer=findLayer(layerRoot,layer.parentId);}
    }
    function formulaForLayer(id,result) {
      const formulas={
        page:'画布 = safeWidthDp × 当前视口高度；不将静态 PNG 当作生产页面背景。',
        background:'Morning Lake master · centered ContentScale.Crop · 无额外 tint / blur。',
        header:'Header 高 48dp；Brand 宽 clamp(safeWidth×155/1080,46,70)，Avatar 36–40dp / touch ≥48dp。',
        stats:`Header–Stats 间距 ${result.headerGap.toFixed(1)}dp；统计行高 55×fontScale dp；三列等分。`,
        'stat-species':'样例值来自 NH01_Structure_V1.preview_fixture；只用于设计参考，不是实时产品数据。',
        'stat-catch':'样例值来自 NH01_Structure_V1.preview_fixture；只用于设计参考，不是实时产品数据。',
        'stat-days':'样例值来自 NH01_Structure_V1.preview_fixture；只用于设计参考，不是实时产品数据。',
        'recent-header':'Stats→Title 使用 stats_to_title；Title→Hero 使用 title_to_card_runtime。',
        pager:`Hero 比例 ${contract.hero_and_peek.hero_ratio}；左右 Peek 对称；peekGap=clamp(8dp,trackWidth×2.5%,12dp)；最大几何重叠 ${result.maxGeometryOverlap}dp；Pager 高度含 ${result.indicatorHeight}dp 示意指示器。PREVIEW_ONLY。`,
        hero:`Hero 目标宽 ${result.targetHeroWidth.toFixed(1)}dp；按 ${contract.hero_and_peek.hero_ratio} 比例受 Pager 可用高度约束；实算 ${result.actualHeroWidth.toFixed(1)}×${result.heroHeight.toFixed(1)}dp。示意媒体加载失败不改变几何。`,
        'peek-left':`Previous 示意卡；宽 ${result.peekWidth.toFixed(1)}dp / 实际露出 ${result.peekReveal.toFixed(1)}dp；与 Hero 实际 gap ${result.peekGap.toFixed(1)}dp。素材只作 GENERATED_ILLUSTRATIVE_ONLY。`,
        'peek-right':`Next 示意卡；宽 ${result.peekWidth.toFixed(1)}dp / 实际露出 ${result.peekReveal.toFixed(1)}dp；与 Hero 实际 gap ${result.peekGap.toFixed(1)}dp。素材只作 GENERATED_ILLUSTRATIVE_ONLY。`,
        'pager-indicator':'仅显示结构化预览的示意 Pager 状态；不表示真实记录数、选中记录或可分页数据。',
        'capture-action-group':`A/B/C 逻辑组 C；CTA 是说明性静态文字，不另分配48dp触控框；Camera 目标 ${result.cameraTouchSize}dp，整组实算 ${result.hC.toFixed(1)}dp。`,
        cta:`静态说明文字；行高按实算 CTA 字号 ${result.fontSizes.cta.toFixed(1)}px × 1.25 = ${result.ctaLineHeight.toFixed(1)}dp，不设独立触控目标。`,
        camera:`Camera 视觉 ${result.cameraSize.toFixed(1)}dp；触控目标 ${result.cameraTouchSize}dp；资产路径与 SHA 已登记。`,
        avatar:'NH01 专用批准头像 V2 位图；保留真实账号照片/冻结回退语义，不使用 NH07 旧头像或共享线稿替代。'
      };
      return formulas[id]||'该子节点无独立冻结公式；以下显示当前 DOM 实测边界。';
    }
    board.addEventListener('click',event=>{
      const node=event.target.closest('[data-layer]');
      if(!node)return;
      selected=node.dataset.layer;
      expandParents(selected);
      render();
    });
    board.addEventListener('keydown',event=>{
      if(event.key!=='Enter'&&event.key!==' ')return;
      const node=event.target.closest('[data-layer]');
      if(!node)return;
      event.preventDefault();selected=node.dataset.layer;expandParents(selected);render();
    });
    treeContainer.addEventListener('click',event=>{
      const toggle=event.target.closest('[data-toggle]');
      if(toggle){const id=toggle.dataset.toggle;if(expanded.has(id))expanded.delete(id);else expanded.add(id);renderTree();return;}
      const button=event.target.closest('[data-select]');
      if(!button)return;
      selected=button.dataset.select;expandParents(selected);render();
      board.querySelector(`[data-layer="${selected}"]`)?.scrollIntoView({block:'nearest',inline:'nearest'});
    });
    function render() {
      const profile=Number(get('profile').value), w=Number(get('width').value), h=Number(get('height').value), font=Number(get('font').value), zoom=Number(get('zoom').value);
      const top=Number(get('top').value), bottom=Number(get('bottom').value);
      const result=calculate(w,h,font,top,bottom,contract,authorityConflict,previewRules);
      workspace.dataset.loadState='READY';
      workspace.dataset.selectedLayer=selected;
      workspace.querySelector('[data-output="font"]').textContent=font.toFixed(2)+'×';
      workspace.querySelector('[data-output="zoom"]').textContent=Math.round(zoom*100)+'%';
      board.style.width=result.safeWidth+'px';board.style.height=h+'px';board.style.transform=`scale(${zoom})`;board.style.transformOrigin='top left';
      workspace.querySelector('.nh01-canvas-scroll').style.minHeight=(h*zoom+24)+'px';
      const fixture=structure.preview_fixture;
      const stats=Array.isArray(fixture.statistics)?fixture.statistics:[];
      const statIds=['stat-species','stat-catch','stat-days'];
      const layer=layerId=>findLayer(layerRoot,layerId);
      const layerAttrs=id=>{
        const meta=layer(id);
        return `data-layer="${safeText(id)}" data-testid="nh01-canvas-${safeText(id)}" data-logical-group="${safeText(meta?.logicalGroup||'UNASSIGNED')}" data-source-node-id="${safeText(meta?.sourceNodeId||'NOT_MAPPED')}" data-mapping-status="${safeText(meta?.mappingStatus||'NOT_MAPPED')}" data-asset-status="${safeText(assetStatusFor(id))}" tabindex="0"`;
      };
      const positioned=(rect,origin={x:0,y:0})=>`left:${rect.x-origin.x}px;top:${rect.y-origin.y}px;width:${rect.w}px;height:${rect.h}px`;
      const header=result.rects.header, brandRect=result.rects.brand, avatarRect=result.rects.avatar, statsRect=result.rects.stats, recentRect=result.rects['recent-header'], pager=result.rects.pager, heroRect=result.rects.hero, footer=result.rects['capture-action-group'];
      const peekLeft=result.rects['peek-left'],peekRight=result.rects['peek-right'];
      const brandSrc=assetUrl(brandAsset.registry_path),backgroundSrc=assetUrl(backgroundAsset.path),chevronSrc=assetUrl(chevronAsset.path),cameraSrc=assetUrl(cameraAsset.registry_path),avatarSrc=assetUrl(avatarAsset.path);
      const mediaSrc=asset=>asset?.path?assetUrl(asset.path):'';
      const cardMedia=(asset,role)=>mediaSrc(asset)?`<img class="nh01-card-media" data-testid="nh01-media-${role}" data-media-role="${safeText(role)}" data-media-status="GENERATED_ILLUSTRATIVE_ONLY" src="${mediaSrc(asset)}" alt="" draggable="false">`:`<div class="nh01-card-media-fallback" data-testid="nh01-media-${role}" data-media-role="${safeText(role)}" data-media-status="PREVIEW_PLACEHOLDER" aria-hidden="true"></div>`;
      const statMarkup=statIds.map((id,index)=>{
        const item=stats[index]||{};
        const width=statsRect.w/statIds.length;
        const rect={x:width*index,y:0,w:width,h:statsRect.h};
        return `<div class="nh01-stat-item" ${layerAttrs(id)} data-testid="nh01-stat-item-${index+1}" style="${positioned(rect)}"><span class="nh01-stat-value" style="font-size:${result.fontSizes.statisticsValue}px">${safeText(item.value??'—')}</span><span class="nh01-stat-label" style="font-size:${result.fontSizes.statisticsLabel}px">${safeText(item.label??'样例未提供')}</span></div>`;
      }).join('');
      const hero=fixture.hero||{};
      board.dataset.previewOnly='true';
      board.dataset.layoutMetrics=JSON.stringify({rulesId:result.previewRulesId,viewport:{width:w,height:h,fontScale:font,topInset:top,bottomInset:bottom},pager:{height:result.pagerHeight,contentHeight:result.pagerContentHeight,scroll:result.pagerNeedsScroll,peekGap:result.peekGap,maxGeometryOverlap:result.maxGeometryOverlap,peekVisibleWidth:result.peekReveal},hero:{width:result.actualHeroWidth,height:result.heroHeight,ratio:result.actualHeroWidth/result.heroHeight},footer:{height:result.hC,cameraTouch:result.cameraTouchSize,cameraArtwork:result.cameraSize}});
      board.innerHTML=`<div class="nh01-environment" ${layerAttrs('environment')} style="${positioned(result.rects.environment)}"><img class="nh01-background" ${layerAttrs('background')} src="${backgroundSrc}" alt="Morning Lake background · frozen source" draggable="false"></div>
        <header class="nh01-header" ${layerAttrs('header')} data-testid="nh01-header" style="${positioned(header)}"><img class="nh01-brand" ${layerAttrs('brand')} src="${brandSrc}" alt="渔见品牌标题 · frozen artwork" style="${positioned(brandRect,{x:header.x,y:header.y})}"><div class="nh01-avatar-touch" ${layerAttrs('avatar')} data-testid="nh01-avatar" aria-label="NH01 approved default-avatar V2 fallback" title="NH01_HOME_AVATAR_V2 · source-approved bitmap" style="${positioned(avatarRect,{x:header.x,y:header.y})}"><img class="nh01-avatar-image" style="width:${result.avatarSize}px;height:${result.avatarSize}px" src="${avatarSrc}" alt="Normal Home V2 默认头像 · 已批准视觉资产" draggable="false"></div></header>
        <section class="nh01-stats" ${layerAttrs('stats')} data-testid="nh01-stats" style="${positioned(statsRect)}">${statMarkup}</section>
        <section class="nh01-recent" ${layerAttrs('recent-header')} data-testid="nh01-recent-header" style="${positioned(recentRect)}"><span class="nh01-recent-title" ${layerAttrs('recent-title')} data-testid="nh01-recent-title" style="font-size:${result.fontSizes.recentHeading}px">最近鱼获</span><span class="nh01-recent-all" ${layerAttrs('recent-all')} data-testid="nh01-recent-all" style="font-size:${result.fontSizes.recentAction}px">全部</span><img class="nh01-recent-chevron" ${layerAttrs('recent-chevron')} src="${chevronSrc}" alt="" aria-hidden="true"></section>
        <section class="nh01-pager ${result.pagerNeedsScroll?'nh01-pager-scrollable':''}" ${layerAttrs('pager')} data-testid="nh01-pager" data-scroll-needed="${result.pagerNeedsScroll}" data-max-geometry-overlap-dp="${result.maxGeometryOverlap}" style="${positioned(pager)}"><div class="nh01-pager-content" style="height:${result.pagerContentHeight}px"><div class="nh01-peek nh01-peek-left" ${layerAttrs('peek-left')} data-testid="nh01-peek-left" data-role="previous" style="${positioned(peekLeft,{x:pager.x,y:pager.y})}" aria-label="Previous generated illustrative card">${cardMedia(mediaForRole('neighbor_previous'),'previous')}</div><article class="nh01-hero" ${layerAttrs('hero')} data-testid="nh01-hero" style="${positioned(heroRect,{x:pager.x,y:pager.y})}">${cardMedia(mediaForRole('hero'),'hero')}<div class="nh01-hero-metadata" data-sample-copy="SOURCE_SAMPLE_TEXT_NOT_LIVE_PRODUCT_DATA"><strong style="font-size:${result.fontSizes.heroSpecies}px">${safeText(hero.species||'鱼种未提供')}</strong><span style="font-size:${result.fontSizes.heroMeasurement}px">${safeText(hero.measurement||'尺寸样例未提供')}</span><span style="font-size:${result.fontSizes.heroMetadata}px">${safeText(hero.context||'时间与位置样例未提供')}</span></div></article><div class="nh01-peek nh01-peek-right" ${layerAttrs('peek-right')} data-testid="nh01-peek-right" data-role="next" style="${positioned(peekRight,{x:pager.x,y:pager.y})}" aria-label="Next generated illustrative card">${cardMedia(mediaForRole('neighbor_next'),'next')}</div><div class="nh01-pager-indicator" ${layerAttrs('pager-indicator')} data-testid="nh01-pager-indicator" data-preview-only="true" role="img" aria-label="Illustrative pager indicator; not a live record count" style="${positioned(result.rects['pager-indicator'],{x:pager.x,y:pager.y})}"><i></i><i class="active"></i><i></i></div></div></section>
        <section class="nh01-capture-action-group" ${layerAttrs('capture-action-group')} data-testid="nh01-capture-action-group" style="${positioned(footer)}"><div class="nh01-cta" ${layerAttrs('cta')} data-testid="nh01-cta" data-interaction="static-label" style="left:${result.rects.cta.x}px;top:${result.rects.cta.y-footer.y}px;width:${result.rects.cta.w}px;height:${result.rects.cta.h}px;font-size:${result.fontSizes.cta}px;line-height:${result.ctaLineHeight}px">记录下一条鱼</div><div class="nh01-camera" ${layerAttrs('camera')} data-testid="nh01-camera" data-preview-only-hit-area="true" aria-label="Camera touch target preview · ${result.cameraTouchSize} dp · no runtime interaction" style="left:${result.rects.camera.x}px;top:${result.rects.camera.y-footer.y}px;width:${result.cameraTouchSize}px;height:${result.cameraTouchSize}px"><img data-testid="nh01-camera-artwork" style="width:${result.cameraSize}px;height:${result.cameraSize}px" src="${cameraSrc}" alt="拍照图案示意 · 可见图案 ${result.cameraSize.toFixed(1)}dp；触控区域 ${result.cameraTouchSize}dp" draggable="false"></div></section>`;
      board.querySelectorAll('.nh01-card-media').forEach(image=>image.addEventListener('error',()=>{image.dataset.mediaStatus='PREVIEW_PLACEHOLDER';image.style.opacity='0';},{once:true}));
      board.classList.toggle('nh01-layer-selected',selected==='page');
      board.querySelectorAll('[data-layer]').forEach(node=>node.classList.toggle('nh01-layer-selected',node.dataset.layer===selected));
      renderTree();
      const selectedLayer=findLayer(layerRoot,selected);
      const selectedNode=selected==='page'?board:board.querySelector(`[data-layer="${selected}"]`);
      if(!selectedLayer||!selectedNode) throw new Error(`图层树节点 ${selected} 没有真实画布 DOM 映射`);
      const measured=measuredLayerBounds(selectedNode,board,zoom);
      const formula=result.rects[selected]||null;
      const asset=profileForAsset(profile);
      const selectedAssets=assetRowsFor(selected);
      const assetRows=selectedAssets.map(item=>{
        const path=item?.path||item?.registry_path||'';
        const diagnostic=resourceByPath(path);
        const fileStatus=item?.asset_status||item?.status||'未登记';
        const hash=item?.sha256||diagnostic?.sha256||'未提供';
        return `<p data-asset-row><strong>${safeText(item?.asset_id||item?.id||item?.component||'未登记')}</strong> · ${safeText(fileStatus)} · 请求 ${safeText(diagnostic?.status||(path?'由画布 img 加载':'未请求'))}<br><code>${safeText(path||'没有独立原图路径')}</code><br>SHA-256 ${safeText(hash)}</p>`;
      }).join('')||'<p>独立位图：不适用。组件由登记的页面结构和正式布局合同呈现。</p>';
      const referenceRow=asset?`<p><strong>冻结静态对照</strong> · ${safeText(asset.status)} · version ${safeText(asset.version||'未提供')}<br><code>${safeText(asset.path)}</code><br>SHA-256 ${safeText(asset.sha256)}</p>`:'';
      const contractMeta=resourceDiagnostics.find(item=>item.id==='machine-contract');
      const manifestMeta=resourceDiagnostics.find(item=>item.id==='frozen-manifest');
      const treeMeta=resourceDiagnostics.find(item=>item.id==='component-tree');
      const formulaText=formula?`${formula.x.toFixed(1)}, ${formula.y.toFixed(1)} · ${formula.w.toFixed(1)} × ${formula.h.toFixed(1)} dp`:'DOM 实测；正式机器合同未提供独立子节点矩形';
      const boundsText=measured?`${measured.x.toFixed(1)}, ${measured.y.toFixed(1)} · ${measured.w.toFixed(1)} × ${measured.h.toFixed(1)} CSS px（相对画布，已除画布缩放）`:'不可测量';
      const metadataNote=selected.startsWith('stat-')||selected==='hero'?'<p class="nh01-sample-disclosure">静态参考样例，不是实时鱼获或账户数据。</p>':'';
      const illustrativeMediaSelected=['hero','peek-left','peek-right'].includes(selected);
      workspace.querySelector('[data-inspector]').innerHTML=`<dl data-testid="nh01-inspector"><dt>选中图层</dt><dd data-testid="nh01-inspector-layer" data-layer-id="${safeText(selected)}">${safeText(selectedLayer.name)}</dd><dt>稳定 ID / 组件 ID</dt><dd>${safeText(selectedLayer.id)} · ${safeText(selectedLayer.componentId||'未映射组件 ID')}</dd><dt>父图层 / 来源映射</dt><dd>${safeText(selectedLayer.parentId||'NH01 Home')} · ${safeText(selectedLayer.sourceNodeId||'未映射到 Figma 节点')} · ${safeText(selectedLayer.mappingStatus)}${selectedLayer.sourceReference?` · implementation reference ${safeText(selectedLayer.sourceReference)}`:''}</dd><dt>逻辑组</dt><dd>${safeText(selectedLayer.logicalGroup)} · ${safeText(assetStatusFor(selected))}</dd><dt>视口 / 安全区 / 字体</dt><dd>${w} × ${h} dp · fontScale ${font.toFixed(2)} · top ${top} / bottom ${bottom} dp · zoom ${Math.round(zoom*100)}%</dd><dt>合同计算边界</dt><dd data-testid="nh01-inspector-formula-bounds">${safeText(formulaText)}</dd><dt>浏览器实测 DOM 边界</dt><dd data-testid="nh01-inspector-measured-bounds">${safeText(boundsText)}</dd><dt>字号 / 相机</dt><dd>${['recent-header','recent-title','hero','cta'].includes(selected)?`${result.fontSizes[ selected==='hero'?'heroSpecies':selected==='cta'?'cta':'recentHeading'].toFixed(1)} CSS px`:'—'} · 相机视觉 ${result.cameraSize.toFixed(1)} / 触控 ${result.cameraTouchSize} dp</dd><dt>资产来源 / SHA-256</dt><dd>${assetRows}${referenceRow}${metadataNote}${illustrativeMediaSelected?'<p class="nh01-sample-disclosure">GENERATED_ILLUSTRATIVE_ONLY · 仅用于展示卡片裁切与布局，不代表真实鱼获或可分页记录。</p>':''}</dd><dt>正式合同 / Manifest SHA</dt><dd>${safeText(contract.contract_id)} · ${safeText(result.contractId)} ${safeText(contract.foundation_layout_principles.version)}<br><code>${safeText(view.machine_authority)}</code> · SHA-256 ${safeText(contractMeta?.sha256||'未验证')}<br><code>${safeText(view.visual_authority_manifest)}</code> · SHA-256 ${safeText(manifestMeta?.sha256||'未验证')}<br><code>design/manager/nh01_acceptance/data/NH01_Component_Tree_V1.json</code> · SHA-256 ${safeText(treeMeta?.sha256||'未验证')}</dd><dt>本图层布局规则</dt><dd>${safeText(formulaForLayer(selected,result))}</dd><dt>布局冲突</dt><dd>${result.conflicts.length?result.conflicts.join('; '):'当前计算未发现约束冲突'} · Android NOT_VERIFIED</dd></dl><h4>A/B/C · PREVIEW_ONLY 剩余空间</h4><code>H_safe=${result.safeHeight.toFixed(1)}<br>hA=${result.hA.toFixed(1)}, hB=${result.hB.toFixed(1)}, hC=${result.hC.toFixed(1)}<br>gAB=${result.baseAB.toFixed(1)}+${result.gapABExtra.toFixed(1)}=${result.gAB.toFixed(1)}<br>gBC=${result.baseBC.toFixed(1)}+${result.gapBCExtra.toFixed(1)}=${result.gBC.toFixed(1)}<br>Outer breathing=${result.outerBreathing.toFixed(1)}dp each<br>Pager budget/content=${result.pagerHeight.toFixed(1)}/${result.pagerContentHeight.toFixed(1)}dp · local scroll ${result.pagerNeedsScroll?'ON':'OFF'}</code><p>Stats→Title ${result.baseAB.toFixed(1)} dp · Title→Hero ${result.titleGap.toFixed(1)} dp · Peek gap ${result.peekGap.toFixed(1)} dp (overlap cap ${result.maxGeometryOverlap.toFixed(1)} dp) · CTA line ${result.ctaLineHeight.toFixed(1)} dp · CTA→Camera ${result.ctaCameraGap} dp · Footer ${result.hC.toFixed(1)} dp · Camera art/touch ${result.cameraSize.toFixed(1)}/${result.cameraTouchSize} dp</p>`;
      workspace.querySelector('[data-comparison]').innerHTML=asset?`<strong>冻结高保真对照 · ${safeText(asset.asset_id)}</strong><span>目标 ${asset.target_viewport_dp.width} × ${asset.target_viewport_dp.height??'高度未冻结'} dp · 原图 ${asset.actual_canvas_px.width} × ${asset.actual_canvas_px.height} px · 原图只读</span><img src="${assetUrl(asset.path)}" alt="${safeText(asset.asset_id)} 冻结参考">`:'<span>无对应冻结稿</span>';
      workspace.querySelector('[data-basis]').textContent=`当前冻结参考 ${asset?.path||'未提供'} · SHA-256 ${asset?.sha256||'未提供'}；父子树来源 ${componentTree.status||'REVIEW_REQUIRED'}；布局边界来自 ${previewRules.rules_id}（PREVIEW_ONLY），三张媒体只作构图示意，非实时记录、非 PNG 像素验收、非 Android Runtime 结果。源码 ${build}。`;
    }
    get('profile').onchange=()=>{const [w,h]=profiles[Number(get('profile').value)];get('width').value=w;if(h!==null)get('height').value=h;render();};
    ['width','height','font','zoom','top','bottom'].forEach(name=>get(name).addEventListener('input',render));
    render();
  }
  window.NH01StructuredWorkspace={mount,calculate,hasAuthorityConflict,allocationShare,createResourceLoader,createLayerTree,flattenLayerTree,findLayer,measuredLayerBounds};
})();
