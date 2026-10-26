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
  function normalizeFormula(value) { return String(value || '').replace(/\s+/g,'').toLowerCase(); }
  function allocationShare(formula, baseName) {
    const normalized=normalizeFormula(formula), prefix=String(baseName).toLowerCase()+'+';
    if(!normalized.startsWith(prefix))return null;
    const match=normalized.slice(prefix.length).match(/^(?:(\d+(?:\.\d+)?)\*)?s(?:\/(\d+(?:\.\d+)?))?$/i);
    return match?Number(match[1]||1)/Number(match[2]||1):null;
  }
  function hasAuthorityConflict(markdownContract, contract) {
    const allocation=contract?.logical_allocation?.allocation_when_positive_surplus||{};
    const md=String(markdownContract||'').replaceAll(String.fromCharCode(96),'').replace(/\s+/g,'').toLowerCase();
    const explainsEqualAllocation=md.includes('equalportions');
    const contradictsMachineRule=md.includes('2s/3')||md.includes('s/3')||md.includes('2:1')||md.includes('unequalportions');
    return allocation.a_to_b!=='gAB_base + S/2'||allocation.b_to_c!=='gBC_base + S/2'||
      !explainsEqualAllocation||contradictsMachineRule;
  }
  function curveValue(points,x,field,xField='safe_height_dp') {
    if(!Array.isArray(points)||!points.length)return 0;
    if(x<=Number(points[0][xField]))return Number(points[0][field]);
    const last=points[points.length-1]; if(x>=Number(last[xField]))return Number(last[field]);
    for(let i=1;i<points.length;i+=1){const left=points[i-1],right=points[i];if(x<=Number(right[xField])){
      const span=Number(right[xField])-Number(left[xField]),t=span>0?(x-Number(left[xField]))/span:0;
      return Number(left[field])+(Number(right[field])-Number(left[field]))*t;
    }}
    return Number(last[field]);
  }
  function calculate(width,height,fontScale,topInset,bottomInset,contract,authorityConflict=false,previewRules={}) {
    const p=contract?.preview_engine?.parameters, allocation=contract?.logical_allocation, conflicts=[];
    if(!p?.content||!p.hero||!p.neighbor||!p.camera||!p.spacing||!allocation)return {
      safeWidth:Number(width)||0,safeHeight:Math.max(0,(Number(height)||0)-(Number(topInset)||0)-(Number(bottomInset)||0)),hA:0,hB:0,hC:0,
      baseAB:0,baseBC:0,S:0,gapABExtra:0,gapBCExtra:0,outerBreathing:0,gAB:0,gBC:0,titleGap:0,
      heroHeight:0,targetHeroWidth:0,actualHeroWidth:0,desiredHeroHeight:0,availableHero:0,pagerHeight:0,pagerContentHeight:0,pagerNeedsScroll:false,
      peekWidth:0,peekHeight:0,peekGap:0,peekReveal:0,maxGeometryOverlap:0,indicatorHeight:0,indicatorGap:0,ctaLineHeight:0,rects:{},
      conflicts:['SPEC_CONFLICT · V2 responsive machine contract is missing required preview_engine.parameters or logical_allocation fields'],
      contractId:contract?.contract_id||'MISSING',contractVersion:contract?.contract_version||'MISSING',previewRulesId:previewRules?.rules_id||'MISSING'};
    const viewportWidth=Math.max(0,Number(width)||0), viewportHeight=Math.max(0,Number(height)||0);
    const safeWidth=viewportWidth,safeHeight=Math.max(0,viewportHeight-Number(topInset||0)-Number(bottomInset||0));
    const content=p.content,scaleRule=p.viewport_scale,spacing=p.spacing,hero=p.hero,neighbor=p.neighbor,camera=p.camera;
    const contentWidth=Math.min(safeWidth,Number(content.max_width_dp||560)),trackX=(safeWidth-contentWidth)/2;
    const fs=Number(fontScale)||1,pr=spacing.font_pressure||{base_font_scale:1,range:.5},sh=spacing.short_height_pressure||{reference_height_dp:620,range_dp:220};
    const pressure=clamp((fs-Number(pr.base_font_scale))/Number(pr.range),0,1);
    const shortPressure=clamp((Number(sh.reference_height_dp)-safeHeight)/Number(sh.range_dp),0,1);
    const fontViewport=clamp(Math.min(Math.min(contentWidth,Number(scaleRule.width_reference_dp))/Number(scaleRule.width_reference_dp),safeHeight/Number(scaleRule.height_reference_dp)),Number(scaleRule.minimum),Number(scaleRule.maximum));
    const roles=contract.typography?.roles||[],role=id=>roles.find(item=>item.id===id)||{};
    const roleSize=(id,fallback,min,max)=>{const item=role(id);return clamp(safeHeight*Number(item.base_ratio??fallback),Number(item.min_sp??min),Number(item.max_sp??max))*fontViewport*fs;};
    const fontSizes={
      statisticsValue:roleSize('statistics-number',.03,17,21),statisticsLabel:roleSize('statistics-label',.019,11,13),
      recentHeading:roleSize('recent-catch-heading',.027,16,19),recentAction:roleSize('all-entry',.021,13,15),
      heroSpecies:roleSize('species-name',.036,20,26),heroMeasurement:roleSize('measurements',.023,13,16),
      heroMetadata:roleSize('time-location',.019,11,13),cta:roleSize('record-next-catch-cta',.021,12,14)
    };
    const exactM01=viewportWidth===Number(hero.short_screen_exception?.exact_viewport_width_dp)&&viewportHeight===Number(hero.short_screen_exception?.exact_viewport_height_dp)&&fs===Number(hero.short_screen_exception?.exact_font_scale);
    const compact=hero.short_screen_exception?.compact_layout||{};
    const topPadding=exactM01?Number(compact.top_padding_dp??0):Number(content.top_padding_dp||0);
    const headerHeight=Number(content.header_min_height_dp||48);
    const headerGap=exactM01?Number(compact.header_to_stats_gap_dp??spacing.brand_to_stats.minimum_gap_dp):
      Math.max(Number(spacing.brand_to_stats.minimum_gap_dp),clamp(safeHeight*Number(spacing.brand_to_stats.height_fraction),Number(spacing.brand_to_stats.base_minimum_dp),Number(spacing.brand_to_stats.base_maximum_dp))-pressure*Number(spacing.brand_to_stats.font_pressure_penalty_dp)-shortPressure*Number(spacing.brand_to_stats.short_height_pressure_penalty_dp));
    const statsMeasuredHeight=fontSizes.statisticsValue*Number(role('statistics-number').line_height_ratio||1.14)+fontSizes.statisticsLabel*Number(role('statistics-label').line_height_ratio||1.2)+Number(content.stats_vertical_padding_dp||0)*2;
    const statsHeight=exactM01?Number(compact.stats_height_dp??content.stats_min_height_dp):Math.max(Number(content.stats_min_height_dp||48),statsMeasuredHeight);
    const st=spacing.stats_to_title,tc=spacing.title_to_card,cf=spacing.card_to_footer;
    const baseAB=exactM01?Number(compact.stats_to_title_gap_dp??st.minimum_gap_dp):Math.max(Number(st.minimum_gap_dp),curveValue(st.base_curve,safeHeight,'value_dp')-Number(st.font_pressure_penalty_dp)*pressure);
    const baseBC=exactM01?Number(compact.card_to_footer_gap_dp??cf.minimum_gap_dp):Math.max(Number(cf.minimum_gap_dp),clamp(safeHeight*Number(cf.height_fraction),Number(cf.base_minimum_dp),Number(cf.base_maximum_dp))-pressure*Number(cf.font_pressure_penalty_dp)-shortPressure*Number(cf.short_height_pressure_penalty_dp));
    const titleGap=exactM01?Number(compact.title_to_card_gap_dp??tc.minimum_gap_dp):Math.max(Number(tc.minimum_gap_dp),curveValue(tc.base_curve,safeHeight,'value_dp')-Number(tc.font_pressure_penalty_dp)*pressure);
    const recentHeight=Math.max(fontSizes.recentHeading*Number(role('recent-catch-heading').line_height_ratio||1.2),fontSizes.recentAction*Number(role('all-entry').line_height_ratio||1.2));
    const brandWidth=clamp(safeWidth*Number(p.brand_title.width_ratio_numerator)/Number(p.brand_title.width_ratio_denominator),Number(p.brand_title.min_width_dp),Number(p.brand_title.max_width_dp));
    const brandHeight=brandWidth*Number(p.brand_title.height_ratio_numerator)/Number(p.brand_title.width_ratio_numerator);
    const avatarSize=clamp(safeWidth*Number(p.avatar.width_ratio),Number(p.avatar.min_visual_dp),Number(p.avatar.max_visual_dp));
    const avatarTouchSize=Math.max(Number(p.avatar.minimum_touch_dp),avatarSize);
    const cameraSize=Math.max(Number(camera.minimum_visual_dp),Math.min(contentWidth*Number(camera.width_fraction_of_capped_safe_width),safeHeight*Number(camera.height_fraction_of_safe_height),Number(camera.maximum_visual_dp)));
    const cameraTouchSize=Math.max(cameraSize,Number(camera.minimum_touch_dp));
    const footerCurve=spacing.footer.transition_curve||[],footerThreshold=Number(spacing.footer.large_bottom_inset_threshold_dp||40);
    const ctaCameraGap=exactM01?Number(compact.cta_camera_gap_dp??curveValue(footerCurve,safeHeight,'cta_camera_gap_dp')):curveValue(footerCurve,safeHeight,'cta_camera_gap_dp');
    const footerTopPadding=exactM01?Number(compact.footer_top_padding_dp??0):curveValue(footerCurve,safeHeight,'top_padding_dp');
    const bottomField=Number(bottomInset)>=footerThreshold?'bottom_padding_large_inset_dp':'bottom_padding_small_inset_dp';
    const footerBottomPadding=exactM01?Number(compact.footer_bottom_padding_dp??0):curveValue(footerCurve,safeHeight,bottomField);
    const ctaLineHeight=resultLineHeight(fontSizes.cta,role('record-next-catch-cta').line_height_ratio,1.25);
    const footerHeight=footerTopPadding+ctaLineHeight+ctaCameraGap+cameraTouchSize+footerBottomPadding;
    const rw=Number(hero.aspect_ratio_width),rh=Number(hero.aspect_ratio_height);
    const targetHeroWidth=Math.min(contentWidth*Number(hero.target_width_fraction),Number(hero.maximum_width_dp));
    const desiredHeroHeight=Math.min(targetHeroWidth*rh/rw,Number(hero.maximum_height_dp));
    const indicatorHeight=Number(neighbor.indicator_height_dp),indicatorGap=Number(neighbor.indicator_gap_after_hero_dp),indicatorWidth=Number(neighbor.indicator_width_dp||40);
    const indicatorStack=indicatorHeight+indicatorGap;
    const minM01HeroHeight=Math.max(Number(hero.short_screen_exception?.minimum_readable_floor_dp||0),Number(hero.short_screen_exception?.minimum_width_dp||0)*rh/rw);
    let heroHeight=desiredHeroHeight;
    if(exactM01){
      const budget=safeHeight-(topPadding+headerHeight+headerGap+statsHeight+baseAB+recentHeight+titleGap+baseBC+footerHeight+indicatorStack);
      if(budget>=minM01HeroHeight)heroHeight=Math.min(desiredHeroHeight,budget);
      else{heroHeight=minM01HeroHeight;conflicts.push('FAIL_CONSTRAINT · M01 NO_SCROLL cannot fit minimum Hero width, 247:293 ratio, readable content, Footer and safe insets together');}
    }
    const actualHeroWidth=heroHeight*rw/rh;
    const peekGap=clamp(contentWidth*Number(neighbor.peek_gap.track_width_fraction),Number(neighbor.peek_gap.minimum_dp),Number(neighbor.peek_gap.maximum_dp));
    const peekWidth=actualHeroWidth*Number(neighbor.maximum_width_fraction_of_hero),peekHeight=heroHeight*Number(neighbor.height_fraction_of_hero);
    const maxGeometryOverlap=Number(neighbor.maximum_overlap_dp),pagerContentHeight=heroHeight+indicatorStack;
    const targetTotal=topPadding+headerHeight+headerGap+statsHeight+baseAB+recentHeight+titleGap+pagerContentHeight+baseBC+footerHeight;
    const targetSurplus=safeHeight-targetTotal,S=Math.max(0,targetSurplus),gapABExtra=S/2,gapBCExtra=S/2,outerBreathing=0;
    const gAB=baseAB+gapABExtra,gBC=baseBC+gapBCExtra;
    let pagerHeight;
    if(targetSurplus>=-0.001||exactM01)pagerHeight=pagerContentHeight;
    else pagerHeight=Math.max(0,safeHeight-(topPadding+headerHeight+headerGap+statsHeight+baseAB+recentHeight+titleGap+baseBC+footerHeight));
    const pagerNeedsScroll=pagerContentHeight>pagerHeight+.1&&!exactM01;
    const hA=topPadding+headerHeight+headerGap+statsHeight,hB=recentHeight+titleGap+pagerContentHeight,hC=footerHeight;
    const recentY=Number(topInset)+hA+gAB,heroY=recentY+recentHeight+titleGap,footerY=heroY+pagerHeight+gBC;
    const sectionPad=clamp(safeWidth*Number(content.section_horizontal_padding.ratio),Number(content.section_horizontal_padding.min_dp),Number(content.section_horizontal_padding.max_dp));
    const topPad=clamp(safeWidth*Number(content.top_horizontal_padding.ratio),Number(content.top_horizontal_padding.min_dp),Number(content.top_horizontal_padding.max_dp));
    const rects={
      page:{x:0,y:0,w:viewportWidth,h:viewportHeight},environment:{x:0,y:0,w:viewportWidth,h:viewportHeight},background:{x:0,y:0,w:viewportWidth,h:viewportHeight},
      header:{x:trackX,y:Number(topInset)+topPadding,w:contentWidth,h:headerHeight},
      brand:{x:trackX+topPad,y:Number(topInset)+topPadding+(headerHeight-brandHeight)/2,w:brandWidth,h:brandHeight},
      avatar:{x:trackX+contentWidth-topPad-avatarTouchSize,y:Number(topInset)+topPadding+(headerHeight-avatarTouchSize)/2,w:avatarTouchSize,h:avatarTouchSize},
      stats:{x:trackX+sectionPad,y:Number(topInset)+topPadding+headerHeight+headerGap,w:Math.max(0,contentWidth-2*sectionPad),h:statsHeight},
      recent:{x:trackX+sectionPad,y:recentY,w:Math.max(0,contentWidth-2*sectionPad),h:recentHeight},
      'recent-header':{x:trackX+sectionPad,y:recentY,w:Math.max(0,contentWidth-2*sectionPad),h:recentHeight},
      pager:{x:trackX,y:heroY,w:contentWidth,h:pagerHeight},
      hero:{x:trackX+(contentWidth-actualHeroWidth)/2,y:heroY,w:actualHeroWidth,h:heroHeight},
      'peek-left':{x:trackX+(contentWidth-actualHeroWidth)/2-peekWidth-peekGap,y:heroY+Number(neighbor.top_offset_dp),w:peekWidth,h:peekHeight},
      'peek-right':{x:trackX+(contentWidth+actualHeroWidth)/2+peekGap,y:heroY+Number(neighbor.top_offset_dp),w:peekWidth,h:peekHeight},
      'pager-indicator':{x:trackX+(contentWidth-indicatorWidth)/2,y:heroY+heroHeight+indicatorGap,w:indicatorWidth,h:indicatorHeight},
      footer:{x:trackX,y:footerY,w:contentWidth,h:footerHeight},'capture-action-group':{x:trackX,y:footerY,w:contentWidth,h:footerHeight},
      cta:{x:trackX+sectionPad,y:footerY+footerTopPadding,w:Math.max(0,contentWidth-2*sectionPad),h:ctaLineHeight},
      camera:{x:trackX+(contentWidth-cameraTouchSize)/2,y:footerY+footerTopPadding+ctaLineHeight+ctaCameraGap,w:cameraTouchSize,h:cameraTouchSize},
      'camera-artwork':{x:trackX+(contentWidth-cameraSize)/2,y:footerY+footerTopPadding+ctaLineHeight+ctaCameraGap+(cameraTouchSize-cameraSize)/2,w:cameraSize,h:cameraSize}
    };
    if(authorityConflict)conflicts.push('SPEC_CONFLICT · V2 Markdown 与机器合同的唯一剩余空间公式不一致');
    if(allocation.allocation_when_positive_surplus?.a_to_b!=='gAB_base + S/2'||allocation.allocation_when_positive_surplus?.b_to_c!=='gBC_base + S/2')conflicts.push('SPEC_CONFLICT · V2 contract must assign positive surplus equally as S/2 + S/2');
    if(previewRules?.status!=='PREVIEW_ONLY'||previewRules?.responsive_authority!=='design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json'||previewRules?.numeric_authority!=='NONE'||previewRules?.no_numeric_overrides!==true||['hero','pager','vertical_allocation','capture_action_group'].some(key=>Object.prototype.hasOwnProperty.call(previewRules,key)))conflicts.push('PREVIEW_RULES_ERROR · preview metadata must not define duplicate responsive numeric rules');
    if(exactM01&&actualHeroWidth<Number(hero.short_screen_exception.minimum_width_dp)-.1)conflicts.push('FAIL_CONSTRAINT · M01 Hero width is below the 145dp minimum');
    if(exactM01&&pagerNeedsScroll)conflicts.push('FAIL_CONSTRAINT · exact M01 requires NO_SCROLL');
    if(pagerNeedsScroll)conflicts.push('PREVIEW_LOCAL_SCROLL · FishCatchPager retains the target Hero ratio while only its viewport scrolls');
    if(!exactM01&&targetSurplus<0&&pagerHeight<=0)conflicts.push('SAFE_OVERFLOW · no space remains for the Pager viewport after required fixed content');
    if(rects['capture-action-group'].y+hC>viewportHeight-Number(bottomInset)+.5)conflicts.push('SAFE_OVERFLOW · Footer exceeds the bottom safe area');
    if(rects['capture-action-group'].y<rects.pager.y+rects.pager.h-.5&&pagerNeedsScroll)conflicts.push('SAFE_OVERFLOW · Footer overlaps the Pager viewport');
    const gridRows=[Number(topInset)+topPadding,headerHeight,headerGap,statsHeight,gAB,recentHeight,titleGap,pagerHeight,gBC,footerHeight,Number(bottomInset)];
    return {safeWidth,safeHeight,contentWidth,trackX,topPadding,headerHeight,hA,hB,hC,baseAB,baseBC,S,targetSurplus,gapABExtra,gapBCExtra,outerBreathing,gAB,gBC,titleGap,
      heroHeight,targetHeroWidth,actualHeroWidth,desiredHeroHeight,availableHero:Math.max(0,safeHeight-(hA+baseAB+recentHeight+titleGap+baseBC+footerHeight+indicatorStack)),pagerHeight,pagerContentHeight,pagerNeedsScroll,
      peekWidth,peekHeight,peekGap,peekReveal:Math.min(peekWidth,Math.max(0,(contentWidth-actualHeroWidth)/2-peekGap)),maxGeometryOverlap,indicatorHeight,indicatorGap,ctaLineHeight,rects,gridRows,conflicts,
      fontViewport,fontSp:fontSizes.recentHeading,fontSizes,brandWidth,brandHeight,avatarSize,avatarTouchSize,headerGap,cameraSize,cameraTouchSize,ctaCameraGap,footerTopPadding,footerBottomPadding,
      contractId:contract.contract_id,contractVersion:contract.contract_version,contractStatus:contract.status,previewRulesId:previewRules?.rules_id||'MISSING',layoutMode:exactM01?'M01_NO_SCROLL_COMPACT':(targetSurplus>=0?'FLOW_FIT':'PAGER_LOCAL_SCROLL')};
  }
  function resultLineHeight(size,ratio,fallback) {
    const value=Number(ratio);return Number(size)*(Number.isFinite(value)&&value>0?value:fallback);
  }
  function mount(root, view, contract, manifest, build, markdownContract = '', resourceBinding = {}, resourceDiagnostics = [], componentTree = {}, structure = {}, avatarAuthority = '', previewRules = {}, previewMedia = {}, responsiveContract = null, responsiveMarkdown = '') {
    const layoutContract = responsiveContract;
    const authorityConflict = hasAuthorityConflict(responsiveMarkdown, layoutContract);
    if (!root || !contract?.foundation_layout_principles || !layoutContract?.preview_engine?.parameters || !layoutContract?.logical_allocation || !Array.isArray(manifest?.assets) || !structure?.preview_fixture || !avatarAuthority || previewRules?.status!=='PREVIEW_ONLY' || previewRules?.numeric_authority!=='NONE') {
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
    workspace.innerHTML=`<header class="nh01-structured-heading"><div><small>DESIGN WORKSPACE · 静态归档 ${contract.foundation_layout_principles.version} · Responsive V${layoutContract.contract_version} ${layoutContract.status}</small><h2>结构化高保真 · 多屏静态与响应式规范</h2><p>布局数值由唯一响应式机器合同驱动；独立 PREVIEW_ONLY 文件仅保留展示元数据。Hero、previous、next 三张图均为 GENERATED_ILLUSTRATIVE_ONLY，不代表真实鱼获或记录数量。Android Runtime = NOT_VERIFIED。</p>${authorityConflict?'<p class="nh01-authority-conflict" role="alert">SPEC_CONFLICT · 响应式 V2 Markdown 与机器合同的剩余空间公式不一致；画布保留冲突标记。</p>':''}</div><code>源码 ${build}</code></header>
      <div class="nh01-structured-controls"><label>视口 <select data-control="profile">${options}</select></label><label>宽 <input data-control="width" type="number" min="280" max="900" value="390"> dp</label><label>高 <input data-control="height" type="number" min="400" max="1400" value="844"> dp</label><label>字体 <input data-control="font" type="range" min="1" max="1.5" step="0.05" value="1"><output data-output="font">1.00×</output></label><label>画布 <input data-control="zoom" type="range" min="0.4" max="1.2" step="0.05" value="0.7"><output data-output="zoom">70%</output></label><details><summary>安全区输入</summary><label>上 <input data-control="top" type="number" min="0" max="80" value="24"> dp</label><label>下 <input data-control="bottom" type="number" min="0" max="80" value="24"> dp</label></details></div>
      <div class="nh01-structured-grid"><nav class="nh01-layer-tree" aria-label="NH01 图层树"><h3>页面图层 <small>${safeText(componentTree.status||'SOURCE TREE')}</small></h3><div data-layer-tree></div></nav><div class="nh01-canvas-area"><div class="nh01-canvas-scroll"><div class="nh01-artboard" data-artboard data-layer="page" data-testid="nh01-canvas-page"></div></div><div class="nh01-comparison" data-comparison></div></div><aside class="nh01-inspector" aria-label="图层属性检查器"><h3>属性与响应式规则</h3><div data-inspector></div></aside></div>
      <details class="nh01-development-basis"><summary>开发依据与验收要求</summary><p>冻结静态归档合同：${contract.contract_id} ${contract.contract_version}；唯一响应式机器合同：${layoutContract.contract_id} V${layoutContract.contract_version}（${layoutContract.status}）；组件父子树：${componentTree.document_type} · ${componentTree.status||'REVIEW_REQUIRED'}；静态参考：${manifest.contract_id}。Android Runtime：NOT_VERIFIED。</p><p><a href="${'../../'+view.machine_authority}" target="_blank" rel="noreferrer">机器合同</a> · <a href="${'../../'+view.authority}" target="_blank" rel="noreferrer">正式文字合同</a> · <a href="../../design/manager/nh01_acceptance/data/NH01_Component_Tree_V1.json" target="_blank" rel="noreferrer">NH01 组件父子树</a> · <a href="../../design/pages/home/normal_home/engineering/Visual_Anchor_Asset_Resolution_V1.md" target="_blank" rel="noreferrer">NH01 头像视觉权威</a></p><div data-basis></div><details open><summary>资源加载审计</summary><div data-resource-report></div></details></details>`;
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
        stats:`Header–Stats 间距 ${result.headerGap.toFixed(1)}dp；Stats 实际高度 ${result.rects.stats.h.toFixed(1)}dp，由字号、行高和合同约束计算；三列等分。`,
        'stat-species':'样例值来自 NH01_Structure_V1.preview_fixture；只用于设计参考，不是实时产品数据。',
        'stat-catch':'样例值来自 NH01_Structure_V1.preview_fixture；只用于设计参考，不是实时产品数据。',
        'stat-days':'样例值来自 NH01_Structure_V1.preview_fixture；只用于设计参考，不是实时产品数据。',
        'recent-header':'Stats→Title 使用 stats_to_title；Title→Hero 使用 title_to_card_runtime。',
        pager:`Hero 比例 ${layoutContract.preview_engine.parameters.hero.aspect_ratio_width}:${layoutContract.preview_engine.parameters.hero.aspect_ratio_height}；左右 Peek 对称；peekGap 使用唯一 V2 合同参数；最大几何重叠 ${result.maxGeometryOverlap}dp；Pager 高度含 ${result.indicatorHeight}dp 示意指示器。`,
        hero:`Hero 目标宽 ${result.targetHeroWidth.toFixed(1)}dp；按 ${layoutContract.preview_engine.parameters.hero.aspect_ratio_width}:${layoutContract.preview_engine.parameters.hero.aspect_ratio_height} 比例由唯一 V2 合同计算；实算 ${result.actualHeroWidth.toFixed(1)}×${result.heroHeight.toFixed(1)}dp。示意媒体加载失败不改变几何。`,
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
      const result=calculate(w,h,font,top,bottom,layoutContract,authorityConflict,previewRules);
      workspace.dataset.loadState='READY';
      workspace.dataset.selectedLayer=selected;
      workspace.querySelector('[data-output="font"]').textContent=font.toFixed(2)+'×';
      workspace.querySelector('[data-output="zoom"]').textContent=Math.round(zoom*100)+'%';
      board.style.width=result.safeWidth+'px';board.style.height=h+'px';board.style.display='grid';board.style.gridTemplateColumns='minmax(0,1fr)';board.style.gridTemplateRows=result.gridRows.map(value=>Math.max(0,value)+'px').join(' ');board.style.rowGap='0';board.style.columnGap='0';board.style.position='relative';board.style.transform=`scale(${zoom})`;board.style.transformOrigin='top left';
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
      const flowPositioned=(rect,row)=>`grid-row:${row};grid-column:1;position:relative;left:auto;top:auto;width:${rect.w}px;height:${rect.h}px;margin-left:${rect.x}px;min-width:0;min-height:0;`;
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
      const responsiveContractMeta=resourceDiagnostics.find(item=>item.id==='responsive-contract');
      board.dataset.layoutMetrics=JSON.stringify({rulesId:result.previewRulesId,contractId:result.contractId,contractVersion:result.contractVersion,contractStatus:result.contractStatus,contractSha256:responsiveContractMeta?.sha256||null,allocation:{S:result.S,gABExtra:result.gapABExtra,gBCExtra:result.gapBCExtra,outerBreathing:result.outerBreathing,gAB:result.gAB,gBC:result.gBC},viewport:{width:w,height:h,fontScale:font,topInset:top,bottomInset:bottom},pager:{height:result.pagerHeight,contentHeight:result.pagerContentHeight,scroll:result.pagerNeedsScroll,peekGap:result.peekGap,maxGeometryOverlap:result.maxGeometryOverlap,peekVisibleWidth:result.peekReveal,indicatorGapAfterHero:result.indicatorGap,indicatorHeight:result.indicatorHeight},hero:{width:result.actualHeroWidth,height:result.heroHeight,targetWidth:result.targetHeroWidth,ratio:result.actualHeroWidth/result.heroHeight},footer:{height:result.hC,cameraTouch:result.cameraTouchSize,cameraArtwork:result.cameraSize}});
      board.innerHTML=`<div class="nh01-environment" ${layerAttrs('environment')} style="${positioned(result.rects.environment)}"><img class="nh01-background" ${layerAttrs('background')} src="${backgroundSrc}" alt="Morning Lake background · frozen source" draggable="false"></div>
        <header class="nh01-header" ${layerAttrs('header')} data-testid="nh01-header" style="${flowPositioned(header,2)}"><img class="nh01-brand" ${layerAttrs('brand')} src="${brandSrc}" alt="渔见品牌标题 · frozen artwork" style="${positioned(brandRect,{x:header.x,y:header.y})}"><div class="nh01-avatar-touch" ${layerAttrs('avatar')} data-testid="nh01-avatar" aria-label="NH01 approved default-avatar V2 fallback" title="NH01_HOME_AVATAR_V2 · source-approved bitmap" style="${positioned(avatarRect,{x:header.x,y:header.y})}"><img class="nh01-avatar-image" style="width:${result.avatarSize}px;height:${result.avatarSize}px" src="${avatarSrc}" alt="Normal Home V2 默认头像 · 已批准视觉资产" draggable="false"></div></header>
        <section class="nh01-stats" ${layerAttrs('stats')} data-testid="nh01-stats" style="${flowPositioned(statsRect,4)}">${statMarkup}</section>
        <section class="nh01-recent" ${layerAttrs('recent-header')} data-testid="nh01-recent-header" style="${flowPositioned(recentRect,6)}"><span class="nh01-recent-title" ${layerAttrs('recent-title')} data-testid="nh01-recent-title" style="font-size:${result.fontSizes.recentHeading}px">最近鱼获</span><span class="nh01-recent-all" ${layerAttrs('recent-all')} data-testid="nh01-recent-all" style="font-size:${result.fontSizes.recentAction}px">全部</span><img class="nh01-recent-chevron" ${layerAttrs('recent-chevron')} src="${chevronSrc}" alt="" aria-hidden="true"></section>
        <section class="nh01-pager ${result.pagerNeedsScroll?'nh01-pager-scrollable':''}" ${layerAttrs('pager')} data-testid="nh01-pager" data-scroll-needed="${result.pagerNeedsScroll}" data-max-geometry-overlap-dp="${result.maxGeometryOverlap}" style="${flowPositioned(pager,8)};overflow-x:hidden;overflow-y:${result.pagerNeedsScroll?'auto':'hidden'};"><div class="nh01-pager-content" style="height:${result.pagerContentHeight}px"><div class="nh01-peek nh01-peek-left" ${layerAttrs('peek-left')} data-testid="nh01-peek-left" data-role="previous" style="${positioned(peekLeft,{x:pager.x,y:pager.y})}" aria-label="Previous generated illustrative card">${cardMedia(mediaForRole('neighbor_previous'),'previous')}</div><article class="nh01-hero" ${layerAttrs('hero')} data-testid="nh01-hero" style="${positioned(heroRect,{x:pager.x,y:pager.y})}">${cardMedia(mediaForRole('hero'),'hero')}<div class="nh01-hero-metadata" data-sample-copy="SOURCE_SAMPLE_TEXT_NOT_LIVE_PRODUCT_DATA"><strong style="font-size:${result.fontSizes.heroSpecies}px">${safeText(hero.species||'鱼种未提供')}</strong><span style="font-size:${result.fontSizes.heroMeasurement}px">${safeText(hero.measurement||'尺寸样例未提供')}</span><span style="font-size:${result.fontSizes.heroMetadata}px">${safeText(hero.context||'时间与位置样例未提供')}</span></div></article><div class="nh01-peek nh01-peek-right" ${layerAttrs('peek-right')} data-testid="nh01-peek-right" data-role="next" style="${positioned(peekRight,{x:pager.x,y:pager.y})}" aria-label="Next generated illustrative card">${cardMedia(mediaForRole('neighbor_next'),'next')}</div><div class="nh01-pager-indicator" ${layerAttrs('pager-indicator')} data-testid="nh01-pager-indicator" data-preview-only="true" role="img" aria-label="Illustrative pager indicator; not a live record count" style="${positioned(result.rects['pager-indicator'],{x:pager.x,y:pager.y})}"><i></i><i class="active"></i><i></i></div></div></section>
        <section class="nh01-capture-action-group" ${layerAttrs('capture-action-group')} data-testid="nh01-capture-action-group" style="${flowPositioned(footer,10)}"><div class="nh01-cta" ${layerAttrs('cta')} data-testid="nh01-cta" data-interaction="static-label" style="left:${result.rects.cta.x-footer.x}px;top:${result.rects.cta.y-footer.y}px;width:${result.rects.cta.w}px;height:${result.rects.cta.h}px;font-size:${result.fontSizes.cta}px;line-height:${result.ctaLineHeight}px">记录下一条鱼</div><div class="nh01-camera" ${layerAttrs('camera')} data-testid="nh01-camera" data-preview-only-hit-area="true" aria-label="Camera touch target preview · ${result.cameraTouchSize} dp · no runtime interaction" style="left:${result.rects.camera.x-footer.x}px;top:${result.rects.camera.y-footer.y}px;width:${result.cameraTouchSize}px;height:${result.cameraTouchSize}px"><img data-testid="nh01-camera-artwork" style="width:${result.cameraSize}px;height:${result.cameraSize}px" src="${cameraSrc}" alt="拍照图案示意 · 可见图案 ${result.cameraSize.toFixed(1)}dp；触控区域 ${result.cameraTouchSize}dp" draggable="false"></div></section>`;
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
      const archiveContractMeta=resourceDiagnostics.find(item=>item.id==='machine-contract');
       const contractMeta=resourceDiagnostics.find(item=>item.id==='responsive-contract');
      const manifestMeta=resourceDiagnostics.find(item=>item.id==='frozen-manifest');
      const treeMeta=resourceDiagnostics.find(item=>item.id==='component-tree');
      const formulaText=formula?`${formula.x.toFixed(1)}, ${formula.y.toFixed(1)} · ${formula.w.toFixed(1)} × ${formula.h.toFixed(1)} dp`:'DOM 实测；正式机器合同未提供独立子节点矩形';
      const boundsText=measured?`${measured.x.toFixed(1)}, ${measured.y.toFixed(1)} · ${measured.w.toFixed(1)} × ${measured.h.toFixed(1)} CSS px（相对画布，已除画布缩放）`:'不可测量';
      const metadataNote=selected.startsWith('stat-')||selected==='hero'?'<p class="nh01-sample-disclosure">静态参考样例，不是实时鱼获或账户数据。</p>':'';
      const illustrativeMediaSelected=['hero','peek-left','peek-right'].includes(selected);
      workspace.querySelector('[data-inspector]').innerHTML=`<dl data-testid="nh01-inspector"><dt>选中图层</dt><dd data-testid="nh01-inspector-layer" data-layer-id="${safeText(selected)}">${safeText(selectedLayer.name)}</dd><dt>稳定 ID / 组件 ID</dt><dd>${safeText(selectedLayer.id)} · ${safeText(selectedLayer.componentId||'未映射组件 ID')}</dd><dt>父图层 / 来源映射</dt><dd>${safeText(selectedLayer.parentId||'NH01 Home')} · ${safeText(selectedLayer.sourceNodeId||'未映射到 Figma 节点')} · ${safeText(selectedLayer.mappingStatus)}${selectedLayer.sourceReference?` · implementation reference ${safeText(selectedLayer.sourceReference)}`:''}</dd><dt>逻辑组</dt><dd>${safeText(selectedLayer.logicalGroup)} · ${safeText(assetStatusFor(selected))}</dd><dt>视口 / 安全区 / 字体</dt><dd>${w} × ${h} dp · fontScale ${font.toFixed(2)} · top ${top} / bottom ${bottom} dp · zoom ${Math.round(zoom*100)}%</dd><dt>合同计算边界</dt><dd data-testid="nh01-inspector-formula-bounds">${safeText(formulaText)}</dd><dt>浏览器实测 DOM 边界</dt><dd data-testid="nh01-inspector-measured-bounds">${safeText(boundsText)}</dd><dt>字号 / 相机</dt><dd>${['recent-header','recent-title','hero','cta'].includes(selected)?`${result.fontSizes[ selected==='hero'?'heroSpecies':selected==='cta'?'cta':'recentHeading'].toFixed(1)} CSS px`:'—'} · 相机视觉 ${result.cameraSize.toFixed(1)} / 触控 ${result.cameraTouchSize} dp</dd><dt>资产来源 / SHA-256</dt><dd>${assetRows}${referenceRow}${metadataNote}${illustrativeMediaSelected?'<p class="nh01-sample-disclosure">GENERATED_ILLUSTRATIVE_ONLY · 仅用于展示卡片裁切与布局，不代表真实鱼获或可分页记录。</p>':''}</dd><dt>正式合同 / Manifest SHA</dt><dd>${safeText(result.contractId)} · V${safeText(result.contractVersion)} · ${safeText(result.contractStatus)}<br><code>${safeText(view.responsive_layout_authority||'design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json')}</code> · SHA-256 ${safeText(contractMeta?.sha256||'未验证')}<br><strong>冻结静态归档合同</strong> · <code>${safeText(view.machine_authority)}</code> · SHA-256 ${safeText(archiveContractMeta?.sha256||'未验证')}<br><code>${safeText(view.visual_authority_manifest)}</code> · SHA-256 ${safeText(manifestMeta?.sha256||'未验证')}<br><code>design/manager/nh01_acceptance/data/NH01_Component_Tree_V1.json</code> · SHA-256 ${safeText(treeMeta?.sha256||'未验证')}</dd><dt>本图层布局规则</dt><dd>${safeText(formulaForLayer(selected,result))}</dd><dt>布局冲突</dt><dd>${result.conflicts.length?result.conflicts.join('; '):'当前计算未发现约束冲突'} · Android NOT_VERIFIED</dd></dl><h4>A/B/C · PREVIEW_ONLY 剩余空间</h4><code>H_safe=${result.safeHeight.toFixed(1)}<br>hA=${result.hA.toFixed(1)}, hB=${result.hB.toFixed(1)}, hC=${result.hC.toFixed(1)}<br>gAB=${result.baseAB.toFixed(1)}+${result.gapABExtra.toFixed(1)}=${result.gAB.toFixed(1)}<br>gBC=${result.baseBC.toFixed(1)}+${result.gapBCExtra.toFixed(1)}=${result.gBC.toFixed(1)}<br>新增剩余空间 S=${result.S.toFixed(1)}dp · A→B 新增 ${result.gapABExtra.toFixed(1)}dp · B→C 新增 ${result.gapBCExtra.toFixed(1)}dp · 外侧留白=${result.outerBreathing.toFixed(1)}dp<br>Pager 可视/内容=${result.pagerHeight.toFixed(1)}/${result.pagerContentHeight.toFixed(1)}dp · ${result.layoutMode} · local scroll ${result.pagerNeedsScroll?'ON':'OFF'}</code><p>Stats→Title ${result.baseAB.toFixed(1)} dp · Title→Hero ${result.titleGap.toFixed(1)} dp · Peek gap ${result.peekGap.toFixed(1)} dp (overlap cap ${result.maxGeometryOverlap.toFixed(1)} dp) · CTA line ${result.ctaLineHeight.toFixed(1)} dp · CTA→Camera ${result.ctaCameraGap} dp · Footer ${result.hC.toFixed(1)} dp · Camera art/touch ${result.cameraSize.toFixed(1)}/${result.cameraTouchSize} dp</p>`;
      workspace.querySelector('[data-comparison]').innerHTML=asset?`<strong>冻结高保真对照 · ${safeText(asset.asset_id)}</strong><span>目标 ${asset.target_viewport_dp.width} × ${asset.target_viewport_dp.height??'高度未冻结'} dp · 原图 ${asset.actual_canvas_px.width} × ${asset.actual_canvas_px.height} px · 原图只读</span><img src="${assetUrl(asset.path)}" alt="${safeText(asset.asset_id)} 冻结参考">`:'<span>无对应冻结稿</span>';
      workspace.querySelector('[data-basis]').textContent=`当前冻结参考 ${asset?.path||'未提供'} · SHA-256 ${asset?.sha256||'未提供'}；父子树来源 ${componentTree.status||'REVIEW_REQUIRED'}；布局数值唯一来自 ${layoutContract.contract_id} V${layoutContract.contract_version}（${layoutContract.status}），PREVIEW_ONLY 文件仅提供展示元数据；三张媒体只作构图示意，非实时记录、非 PNG 像素验收、非 Android Runtime 结果。源码 ${build}。`;
    }
    get('profile').onchange=()=>{const [w,h]=profiles[Number(get('profile').value)];get('width').value=w;if(h!==null)get('height').value=h;render();};
    ['width','height','font','zoom','top','bottom'].forEach(name=>get(name).addEventListener('input',render));
    render();
  }
  window.NH01StructuredWorkspace={mount,calculate,hasAuthorityConflict,allocationShare,createResourceLoader,createLayerTree,flattenLayerTree,findLayer,measuredLayerBounds};
})();
