#!/usr/bin/env node
import { createServer } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve, extname, sep, dirname, join } from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const moduleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (moduleRoot ? resolve(moduleRoot, 'playwright') : 'playwright');
const { chromium } = require(playwrightPath);
function assert(condition, message) { if (!condition) throw new Error(message); }
function parseArgs(argv) {
  const result = { root: process.cwd(), url: null, expectedBuild: null, expectedManagerBuild: null, expectedDeploymentSha: null, deploymentManifestPath: null, output: '/tmp/nh01-responsive-preview-report.json', previewPath: 'previews/pr-176/design/manager/nh01_acceptance/gemini-native-preview.html' };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--root') result.root = resolve(argv[++i]);
    else if (arg === '--url') result.url = argv[++i];
    else if (arg === '--expected-build') result.expectedBuild = argv[++i];
    else if (arg === '--expected-manager-build') result.expectedManagerBuild = argv[++i];
    else if (arg === '--expected-deployment-sha') result.expectedDeploymentSha = argv[++i];
    else if (arg === '--deployment-manifest-path') result.deploymentManifestPath = argv[++i].replace(/^\/+/, '');
    else if (arg === '--output') result.output = resolve(argv[++i]);
    else if (arg === '--preview-path') result.previewPath = argv[++i].replace(/^\/+/, '');
    else throw new Error('Unknown argument: ' + arg);
  }
  return result;
}
function startStaticServer(root) {
  const absoluteRoot = resolve(root);
  const mime = { '.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.css':'text/css; charset=utf-8', '.json':'application/json; charset=utf-8', '.png':'image/png', '.svg':'image/svg+xml', '.jpg':'image/jpeg', '.jpeg':'image/jpeg', '.webp':'image/webp', '.md':'text/markdown; charset=utf-8' };
  const server = createServer((request, response) => {
    let pathname;
    try { pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname); }
    catch { response.writeHead(400).end('Bad path'); return; }
    let file = resolve(absoluteRoot, '.' + pathname);
    if (file !== absoluteRoot && !file.startsWith(absoluteRoot + sep)) { response.writeHead(403).end('Forbidden'); return; }
    if (existsSync(file) && statSync(file).isDirectory()) file = resolve(file, 'index.html');
    if (!existsSync(file) || !statSync(file).isFile()) { response.writeHead(404).end('Not found'); return; }
    response.writeHead(200, { 'Content-Type': mime[extname(file).toLowerCase()] || 'application/octet-stream', 'Cache-Control':'no-store' });
    if (request.method === 'HEAD') response.end(); else createReadStream(file).pipe(response);
  });
  return new Promise((resolvePromise, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolvePromise({ server, origin: 'http://127.0.0.1:' + server.address().port + '/' }));
  });
}
async function waitReady(page, width, height, fontScale = null) {
  await page.waitForFunction(({width,height,fontScale}) => {
    const layout = window.NH01GeminiPreviewLayout;
    return window.NH01GeminiPreviewLayoutReady === true && layout?.logical_viewport_dp?.[0] === width && layout?.logical_viewport_dp?.[1] === height && (fontScale === null || layout?.system_font_scale === fontScale);
  }, {width,height,fontScale}, { timeout: 30000 });
  return page.evaluate(() => window.NH01GeminiPreviewLayout);
}
async function setViewport(page, width, height, fontScale = 1) {
  await page.locator('#layoutVariant').selectOption('ADAPTIVE_V2');
  await page.locator('#customWidth').fill(String(width));
  await page.locator('#customHeight').fill(String(height));
  await page.locator('#fontScale').evaluate((element, value) => {
    element.value = String(value); element.dispatchEvent(new Event('input', {bubbles:true})); element.dispatchEvent(new Event('change', {bubbles:true}));
  }, fontScale);
  await page.locator('#customHeight').press('Tab');
  return waitReady(page, width, height, fontScale);
}
async function setSafeInsets(page, insets) {
  await page.evaluate(values => {
    const fields={top:document.querySelector('#safeTop'),bottom:document.querySelector('#safeBottom'),start:document.querySelector('#safeStart'),end:document.querySelector('#safeEnd')};
    for(const [key,value] of Object.entries(values))fields[key].value=String(value);
    fields.bottom.dispatchEvent(new Event('change',{bubbles:true}));
  },insets);
  await page.waitForFunction(values=>window.NH01GeminiPreviewLayoutReady&&Object.entries(values).every(([key,value])=>window.NH01GeminiPreviewLayout.safe_insets_dp[key]===value),insets,{timeout:10000});
}
function numericDelta(a, b) { return Math.abs(Number(a) - Number(b)); }
async function main() {
  const args = parseArgs(process.argv);
  assert(args.expectedBuild, '--expected-build is required');
  await mkdir(dirname(args.output), { recursive:true });
  const evidenceDir = join(dirname(args.output), 'nh01-responsive-preview');
  await mkdir(evidenceDir, { recursive:true });
  let server = null;
  let origin = args.url;
  if (!origin) { const started = await startStaticServer(args.root); server = started.server; origin = started.origin; }
  if (!origin.endsWith('/')) origin += '/';
  const previewUrl = new URL(args.previewPath, origin).href;
  const managerUrl = new URL('design/manager/#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01', origin).href;
  const browser = await chromium.launch({ headless:true });
  const errors = [];
  const failedRequests = [];
  const badResponses = [];
  const sameOrigin = new URL(origin).origin;
  const report = { status:'RUNNING', expected_source_sha:args.expectedBuild, manager_url:managerUrl, preview_url:previewUrl, checks:{}, browser_errors:errors, failed_requests:failedRequests, bad_responses:badResponses, screenshots:[], evidence_dir:evidenceDir };
  try {
    const page = await browser.newPage({ viewport:{width:1600,height:1400}, deviceScaleFactor:1 });
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
    page.on('pageerror', error => errors.push(error.message));
    page.on('requestfailed', request => { if (request.url().startsWith(sameOrigin)) failedRequests.push(request.url() + ' · ' + (request.failure()?.errorText || 'failed')); });
    page.on('response', response => { if (response.status() >= 400 && response.url().startsWith(sameOrigin)) badResponses.push(response.status() + ' ' + response.url()); });

    const mainResponse = await page.goto(managerUrl, {waitUntil:'networkidle', timeout:60000});
    assert(mainResponse?.status() === 200, 'Design Manager main page HTTP status is not 200');
    await page.locator('#hifiViewTitle').waitFor({state:'visible',timeout:30000});
    await page.locator('#pageList .module-item').first().waitFor({state:'visible',timeout:30000});
    const managerBuild = await page.locator('meta[name="design-manager-build"]').getAttribute('content');
    const expectedManagerBuild=args.expectedManagerBuild||args.expectedBuild;
    assert(managerBuild === expectedManagerBuild, 'Design Manager artifact SHA mismatch: ' + managerBuild);
    report.checks.manager = {http_status:mainResponse.status(),build_sha:managerBuild,title:await page.title()};

    const previewResponse = await page.goto(previewUrl, {waitUntil:'domcontentloaded',timeout:60000});
    assert(previewResponse?.status() === 200, 'NH01 responsive preview HTTP status is not 200');
    await page.waitForFunction(() => window.NH01GeminiPreviewReady === true || window.NH01ResponsiveContractFailure, undefined, {timeout:60000});
    assert(!await page.locator('#nh01GeminiPreview').evaluate(node => node.classList.contains('contract-failed')), 'Preview failed closed while loading the V2 contract');
    await waitReady(page, 390, 844, 1);
    const contract = await page.evaluate(() => window.NH01ResponsiveContract);
    assert(contract?.id === 'NH01_RESPONSIVE_LAYOUT_V2', 'Preview did not load the NH01 V2 machine contract');
    assert(contract.version === '2.0', 'Responsive contract version mismatch: ' + contract.version);
    assert(contract.source_sha === args.expectedBuild, 'Preview source SHA mismatch: ' + contract.source_sha);
    if(args.expectedDeploymentSha)assert(contract.deployment_sha===args.expectedDeploymentSha,'Preview deployment SHA mismatch: '+contract.deployment_sha);
    assert(/NH01_RESPONSIVE_LAYOUT_V2\.json$/.test(contract.path), 'Unexpected responsive contract path: ' + contract.path);
    const contractJson = await page.evaluate(async path => { const response=await fetch(path,{cache:'no-store'}); if(!response.ok)throw new Error('contract HTTP '+response.status); return response.json(); }, contract.path);
    assert(contractJson.contract_id === contract.id && contractJson.contract_version === contract.version, 'Loaded contract content does not match the page contract label');
    const profileInfo=await page.evaluate(async path=>{
      const root=new URL(document.querySelector('meta[name="nh01-preview-root"]').content,location.href);
      const url=new URL(path,root);
      const response=await fetch(url,{cache:'no-store'});
      if(!response.ok)throw new Error(`Viewport profile registry HTTP ${response.status}`);
      const bytes=await response.arrayBuffer();
      const hash=await crypto.subtle.digest('SHA-256',bytes);
      const sha256=[...new Uint8Array(hash)].map(value=>value.toString(16).padStart(2,'0')).join('');
      return {path:url.pathname,sha256,registry:JSON.parse(new TextDecoder().decode(bytes))};
    },contractJson.authority.viewport_profiles);
    assert(/Viewport_Profiles_V1\.json$/.test(profileInfo.path),'Responsive contract viewport profile reference is unexpected');
    const continuityIds=contractJson.acceptance?.continuity_probe_ids||[];
    const requiredProbeIds=['RESPONSIVE-CONTINUITY-375x720','RESPONSIVE-HEIGHT-BOUNDARY-499-500','RESPONSIVE-HEIGHT-BOUNDARY-699-700','RESPONSIVE-HEIGHT-BOUNDARY-839-840','RESPONSIVE-FOOTER-BOUNDARY-459-460'];
    assert(JSON.stringify(continuityIds)===JSON.stringify(requiredProbeIds),'Contract continuity_probe_ids do not match the required acceptance probes');
    const probePairs=continuityIds.map(probeId=>{
      const definition=profileInfo.registry.interpolation_probes?.find(item=>item.probe_id===probeId);
      assert(definition,`Viewport profile registry is missing continuity probe ${probeId}`);
      const heights=definition.height_values_dp||[definition.height_dp];
      const fontScales=definition.font_scales||[definition.font_scale??1];
      assert(Number.isFinite(definition.width_dp)&&heights.length>0&&heights.every(Number.isFinite),'Continuity probe definition is incomplete: '+probeId);
      return {id:probeId,width:definition.width_dp,heights,fontScales,usesBoundaryInsets:Array.isArray(definition.height_values_dp)};
    });
    report.checks.viewport_profile_registry={path:profileInfo.path,sha256:profileInfo.sha256,continuity_probe_ids:continuityIds};
    assert(await page.locator('#layoutVariant').inputValue() === 'ADAPTIVE_V2', 'Adaptive V2 is not the default view');
    assert((await page.locator('.responsive-frame').count()) === 5, 'Expected five fixed viewport browser previews');
    await page.waitForFunction(() => Array.from(document.querySelectorAll('.responsive-frame')).every(frame => frame.contentWindow?.NH01GeminiPreviewLayoutReady === true), undefined, {timeout:90000});
    const frameContracts=await page.locator('.responsive-frame').evaluateAll(frames=>frames.map(frame=>({viewport:frame.dataset.viewport,contract_sha256:frame.contentWindow?.NH01GeminiPreviewLayout?.contract_sha256,variant:frame.contentWindow?.NH01GeminiPreviewLayout?.variant})));
    assert(frameContracts.length===5&&frameContracts.every(frame=>frame.contract_sha256===contract.sha256&&frame.variant==='ADAPTIVE_V2'),'The five fixed viewport previews do not share the same contract-driven Adaptive V2 policy');
    await page.waitForFunction(() => window.NH01FrozenAnchorAudit?.status === 'PASS', undefined, {timeout:90000});
    const anchors = await page.evaluate(() => window.NH01FrozenAnchorAudit.assets);
    assert(anchors.length === 6 && anchors.every(anchor => anchor.status === 'PASS'), 'All six frozen reference assets must pass SHA and canvas checks');
    const m05 = anchors.find(anchor => anchor.id.includes('M05'));
    assert(m05?.width_only_static === true && m05.logical_viewport_dp.height === null, 'M05 must remain width-only/static with no invented height');
    const visibleStructure = await page.evaluate(() => ({
      brand:!!document.querySelector('#assetBrand')?.naturalWidth,
      stats:document.querySelectorAll('#statsBar .stat').length,
      sectionTitle:document.querySelector('.section-title')?.textContent.trim(),
      hero:!!document.querySelector('#mainCatchCard'),
      middle:!!document.querySelector('#middleSection'),
      footer:!!document.querySelector('#footerSection'),
      footerOutsideMiddle:!!document.querySelector('#middleSection')&&!document.querySelector('#middleSection').contains(document.querySelector('#footerSection')),
      hiddenNeighbors:[...document.querySelectorAll('.neighbor-card')].every(node=>node.hidden),
      layerTree:document.querySelectorAll('#layerTree button').length,
      propertyInspector:!!document.querySelector('[aria-label="所选图层属性检查器"]'),
      ruleRows:document.querySelectorAll('#responsiveRuleList .rule-row').length,
      referenceRendered:document.querySelector('#anchorImage')?.naturalWidth>0
    }));
    assert(visibleStructure.brand && visibleStructure.stats === 3 && visibleStructure.sectionTitle && visibleStructure.hero && visibleStructure.footer, 'Brand, three statistics, Recent Catch, Hero and Footer hierarchy must render');
    assert(visibleStructure.middle&&visibleStructure.footerOutsideMiddle&&visibleStructure.layerTree>0&&visibleStructure.propertyInspector,'Footer/Middle composition or existing layer/property workspace is missing');
    assert(visibleStructure.hiddenNeighbors, 'No missing neighbor catch may be cloned or rendered');
    assert(visibleStructure.ruleRows >= 8 && visibleStructure.referenceRendered, 'Interactive rule viewer and frozen-reference viewer must render');
    const assets = await page.locator('img[data-asset-id]').evaluateAll(images => images.map(image => ({asset:image.dataset.assetId,src:image.currentSrc,complete:image.complete,naturalWidth:image.naturalWidth,naturalHeight:image.naturalHeight})));
    assert(assets.every(image => image.complete && image.naturalWidth > 0), 'Registered preview assets did not all load: ' + JSON.stringify(assets.filter(image=>!image.complete||image.naturalWidth<=0)));
    report.checks.contract = {id:contract.id,version:contract.version,path:contract.path,sha256:contract.sha256,status:contract.status,source_sha:contract.source_sha,deployment_sha:contract.deployment_sha,anchor_count:anchors.length,anchors,visible_structure:visibleStructure,registered_assets:assets};
    report.checks.fixed_viewport_frames=frameContracts;
    const previewAudit=await page.evaluate(()=>window.NH01GeminiPreviewAudit);
    const mediaBlockers=(previewAudit?.assets||[]).filter(asset=>asset.status==='BLOCKED_ASSET_SOURCE'||asset.status==='BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS');
    report.checks.p3_06={status:mediaBlockers.length?'BLOCKED':'PASS',media_blockers:mediaBlockers.map(asset=>({id:asset.id,status:asset.status,reason:asset.reason||null})),rule:'P3-06 cannot claim image-level acceptance while Hero photo provenance or independent neighbor records are blocked.'};
    report.p3_06_status=mediaBlockers.length?'BLOCKED':'PASS';
    if(mediaBlockers.length)report.p3_06_blockers=report.checks.p3_06.media_blockers;
    if(args.deploymentManifestPath){
      const deploymentUrl=new URL(args.deploymentManifestPath,origin).href;
      const deploymentResponse=await page.request.get(deploymentUrl,{timeout:30000});
      assert(deploymentResponse.status()===200,'Preview deployment manifest HTTP status is not 200');
      const deploymentManifest=await deploymentResponse.json();
      assert(deploymentManifest.contract_id===contract.id&&deploymentManifest.contract_version===contract.version,'Deployment manifest contract identity/version mismatch');
      assert(deploymentManifest.contract_sha256===contract.sha256,'Deployment manifest contract SHA mismatch');
      assert(deploymentManifest.preview_source_sha===contract.source_sha,'Deployment manifest source SHA mismatch');
      assert(deploymentManifest.pages_deployment_sha===contract.deployment_sha,'Deployment manifest Pages SHA mismatch');
      const relativeProfilePath=profileInfo.path.slice(profileInfo.path.indexOf('/design/')+1);
      assert(deploymentManifest.viewport_profiles_path===relativeProfilePath,'Deployment manifest viewport profile path mismatch');
      assert(deploymentManifest.viewport_profiles_sha256===profileInfo.sha256,'Deployment manifest viewport profile SHA mismatch');
      report.checks.deployment_manifest={url:deploymentUrl,http_status:deploymentResponse.status(),...deploymentManifest};
    }

    const fixed=contractJson.preview_profiles.filter(profile=>profile.preview_enabled).map(profile=>({id:profile.profile_id,width:profile.width_dp,height:profile.height_dp}));
    assert(fixed.length===5&&JSON.stringify(fixed.map(profile=>`${profile.width}x${profile.height}`))===JSON.stringify(['320x480','360x640','390x844','430x932','600x960']),'Contract preview_profiles do not define the five required fixed viewports');
    const registeredProfiles=new Map(profileInfo.registry.profiles.map(profile=>[profile.profile_id,profile]));
    for(const profile of fixed){
      const registered=registeredProfiles.get(profile.id);
      assert(registered&&registered.preview_enabled&&registered.width_dp===profile.width&&registered.height_dp===profile.height,`Contract fixed profile ${profile.id} drifted from the global viewport registry`);
    }
    const fixedResults=[];
    for (const profile of fixed) {
      const layout = await setViewport(page,profile.width,profile.height,1);
      const filename=`fixed-${profile.width}x${profile.height}.png`;
      await page.locator('#device').screenshot({path:join(evidenceDir,filename),animations:'disabled'});
      report.screenshots.push(join(evidenceDir,filename));
      fixedResults.push({profile_id:profile.id,requested_viewport_dp:[profile.width,profile.height],measured:layout});
      report.checks.fixed_viewports=fixedResults;
      assert(layout.variant === 'ADAPTIVE_V2' && layout.contract_sha256 === contract.sha256, `${profile.id} did not use the loaded V2 contract`);
      assert(layout.content_width_dp <= 560 + 0.01, `${profile.id} exceeded the contract max content width`);
      assert(layout.measurements.horizontal_overflow_dp <= 0.5, `${profile.id} has horizontal overflow`);
      assert(layout.measurements.hero_overlap_footer <= 0.5, `${profile.id} Hero overlaps Footer`);
      assert(layout.actual_bounds_dp.footer.top+0.5>=layout.actual_bounds_dp.middle.bottom,`${profile.id} Footer enters the Middle scroll viewport`);
      if(layout.needs_scroll)assert(layout.scroll_metrics.middle_scroll_height_dp>layout.scroll_metrics.middle_client_height_dp+0.5,`${profile.id} contract-requested Middle scroll has no scrollable overflow`);
      assert(layout.measurements.camera_bottom_safe >= -0.5 && layout.measurements.camera_touch_bottom_safe >= -0.5, `${profile.id} camera artwork or touch area intrudes into bottom safe inset`);
      assert(layout.measurements.cta_camera_visual > 0 && layout.measurements.cta_camera_touch >= -0.5, `${profile.id} CTA and camera artwork/touch target are not a clear action group`);
      assert(numericDelta(layout.measurements.cta_camera_touch,layout.footer.cta_camera_gap_dp)<=0.5, `${profile.id} CTA-to-camera touch spacing does not match the responsive contract`);
      assert(layout.camera_touch_size_dp >= layout.responsive_parameters.camera.minimum_touch_dp, `${profile.id} camera touch target is below its contract minimum`);
      assert(layout.hero_aspect_ratio && numericDelta(layout.card_width_dp/layout.card_height_dp,layout.responsive_parameters.hero.aspect_ratio_width/layout.responsive_parameters.hero.aspect_ratio_height) < 0.015, `${profile.id} Hero aspect ratio is outside contract tolerance`);
      const target=contractJson.spacing.title_to_card_anchor_targets.find(item=>item.profile_id===profile.id);
      if(target) assert(numericDelta(layout.measurements.title_to_card,target.target_dp)<=target.tolerance_dp,`${profile.id} title-to-card gap ${layout.measurements.title_to_card.toFixed(2)}dp outside ${target.target_dp}±${target.tolerance_dp}dp`);
    }
    assert(fixedResults.at(-1).measured.content_width_dp === 560, '600dp wide view did not center at the contract width cap');
    for(let index=1;index<fixedResults.length;index++){
      const previous=fixedResults[index-1].measured;
      const current=fixedResults[index].measured;
      assert(current.brand_width_dp+0.5>=previous.brand_width_dp,`${fixedResults[index].profile_id} brand artwork shrank at a larger viewport`);
      assert(current.card_width_dp+0.5>=previous.card_width_dp&&current.card_height_dp+0.5>=previous.card_height_dp,`${fixedResults[index].profile_id} Hero dimensions shrank at a larger viewport`);
      for(const role of ['stat_value','stat_label','section_title','section_action','cta','species','measurement','metadata'])assert(current.typography_base_sp[role]+0.01>=previous.typography_base_sp[role],`${fixedResults[index].profile_id} ${role} type shrank at a larger viewport`);
    }
    const typeSizes=fixedResults[2].measured.typography_base_sp;
    assert(typeSizes.stat_value>typeSizes.stat_label&&typeSizes.species>typeSizes.measurement, 'Key text hierarchy changed at the 390x844dp reference viewport');
    assert(Math.abs(fixedResults.at(-1).measured.content_left_dp-20)<=0.5,'600dp wide content is not centered around its 560dp width cap');
    report.checks.fixed_viewports=fixedResults;

    const font130=await setViewport(page,320,480,1.3);
    report.checks.font_1_3=font130;
    const fontScreenshot=join(evidenceDir,'font-1_3-320x480.png');
    await page.locator('#device').screenshot({path:fontScreenshot,animations:'disabled'});
    report.screenshots.push(fontScreenshot);
    assert(font130.text_lines.species>=1&&font130.text_lines.species<=2&&font130.text_lines.measurement<=2&&font130.text_lines.metadata<=2, `Font scale 1.3 text wrapping exceeded the contract line limits: ${JSON.stringify(font130.text_lines)}`);
    assert(font130.measurements.horizontal_overflow_dp<=0.5, 'Font scale 1.3 caused horizontal overflow');
    await page.locator('#layoutVariant').selectOption('V3_BASELINE');
    await page.waitForFunction(()=>window.NH01GeminiPreviewLayoutReady&&window.NH01GeminiPreviewLayout.variant==='V3_BASELINE',undefined,{timeout:30000});
    const baseline=await page.evaluate(()=>({variant:window.NH01GeminiPreviewLayout.variant,card_size:[window.NH01GeminiPreviewLayout.card_width_dp,window.NH01GeminiPreviewLayout.card_height_dp]}));
    await page.locator('#layoutVariant').selectOption('ADAPTIVE_V2');
    const returned=await waitReady(page,320,480,1.3);
    const m05Option=await page.locator('#anchorSelect option').evaluateAll(options=>{
      const option=options.find(item=>item.textContent.includes('M05'));
      return option?{value:option.value,label:option.textContent}:null;
    });
    assert(m05Option,'M05 width-only static reference option is missing');
    await page.locator('#anchorSelect').selectOption(m05Option.value);
    const m05ViewerText=await page.locator('#anchorMeta').innerText();
    assert(/高度未指定/.test(m05ViewerText)&&/静态参考/.test(m05ViewerText),'M05 viewer must disclose its static width-only exception');
    await page.locator('#viewportPreset').selectOption('390x844');
    await waitReady(page,390,844,1.3);
    const freeWidth=await setViewport(page,375,720,1);
    assert(freeWidth.logical_viewport_dp[0]===375&&freeWidth.logical_viewport_dp[1]===720,'Free viewport controls failed');
    const insetProbe=await page.evaluate(async()=>{
      const fields={top:document.querySelector('#safeTop'),bottom:document.querySelector('#safeBottom'),start:document.querySelector('#safeStart'),end:document.querySelector('#safeEnd')};
      fields.top.value='30';fields.bottom.value='32';fields.start.value='8';fields.end.value='8';fields.bottom.dispatchEvent(new Event('change',{bubbles:true}));
      await new Promise(resolve=>setTimeout(resolve,100));
      return window.NH01GeminiPreviewLayout.safe_insets_dp;
    });
    await page.waitForFunction(()=>window.NH01GeminiPreviewLayoutReady&&window.NH01GeminiPreviewLayout.safe_insets_dp.top===30&&window.NH01GeminiPreviewLayout.safe_insets_dp.bottom===32,undefined,{timeout:10000});
    const defaultInsets=contractJson.preview_engine.coordinate_space.default_safe_insets_dp;
    await page.evaluate(insets=>{
      const fields={top:document.querySelector('#safeTop'),bottom:document.querySelector('#safeBottom'),start:document.querySelector('#safeStart'),end:document.querySelector('#safeEnd')};
      for(const [key,value] of Object.entries(insets))fields[key].value=String(value);
      fields.bottom.dispatchEvent(new Event('change',{bubbles:true}));
    },defaultInsets);
    await page.waitForFunction(insets=>window.NH01GeminiPreviewLayoutReady&&Object.entries(insets).every(([key,value])=>window.NH01GeminiPreviewLayout.safe_insets_dp[key]===value),defaultInsets,{timeout:10000});
    report.checks.interactive_controls={baseline,adaptive_restored:returned.variant,free_viewport:freeWidth.logical_viewport_dp,font_scale_1_3:{font_scale:font130.system_font_scale,text_lines:font130.text_lines,card_size:[font130.card_width_dp,font130.card_height_dp]},safe_inset_adjustment:insetProbe,continuity_probe_safe_insets:defaultInsets,m05_viewer:m05ViewerText};

    const probes=[];
    for(const probe of probePairs){
      const probeInsets=probe.usesBoundaryInsets?{top:0,bottom:0,start:0,end:0}:defaultInsets;
      await setSafeInsets(page,probeInsets);
      for(const scale of probe.fontScales){
        const rows=[];
        for(const height of probe.heights){
          const layout=await setViewport(page,probe.width,height,scale);
          assert(layout.variant==='ADAPTIVE_V2'&&layout.contract_sha256===contract.sha256,`${probe.id} left contract-driven Adaptive V2`);
          assert(layout.measurements.horizontal_overflow_dp<=0.5,`${probe.id} has horizontal overflow`);
          assert(layout.measurements.hero_overlap_footer<=0.5,`${probe.id} Hero/Footer overlap`);
          assert(layout.actual_bounds_dp.footer.top+0.5>=layout.actual_bounds_dp.middle.bottom,`${probe.id} Footer enters the Middle scroll viewport`);
          if(layout.needs_scroll)assert(layout.scroll_metrics.middle_scroll_height_dp>layout.scroll_metrics.middle_client_height_dp+0.5,`${probe.id} contract-requested Middle scroll has no scrollable overflow`);
          assert(layout.measurements.camera_bottom_safe>=-0.5&&layout.measurements.camera_touch_bottom_safe>=-0.5,`${probe.id} camera or touch area is outside bottom safe area`);
          assert(layout.measurements.cta_camera_visual>0&&layout.measurements.cta_camera_touch>=-0.5,`${probe.id} CTA/camera visual or touch bounds collide`);
          assert(numericDelta(layout.measurements.cta_camera_touch,layout.footer.cta_camera_gap_dp)<=0.5,`${probe.id} CTA-to-camera touch spacing left the contract value`);
          assert(layout.text_lines.species<=2&&layout.text_lines.measurement<=2&&layout.text_lines.metadata<=2,`${probe.id} text exceeded two line allowance`);
          rows.push(layout);
        }
        if(rows.length===2){
          const jump=Math.max(numericDelta(rows[0].card_width_dp,rows[1].card_width_dp),numericDelta(rows[0].card_height_dp,rows[1].card_height_dp),numericDelta(rows[0].measurements.title_to_card,rows[1].measurements.title_to_card),numericDelta(rows[0].measurements.cta_camera_visual,rows[1].measurements.cta_camera_visual));
          assert(jump<=2.5,`${probe.id} adjacent-height layout jump ${jump.toFixed(2)}dp exceeds continuity tolerance`);
          assert(rows[0].logical_viewport_dp[1]+1===rows[1].logical_viewport_dp[1],`${probe.id} probes are not adjacent`);
          probes.push({probe_id:probe.id,width_dp:probe.width,font_scale:scale,safe_insets_dp:probeInsets,views:rows.map(layout=>({height_dp:layout.logical_viewport_dp[1],safe_height_dp:layout.safe_height_dp,card_size_dp:[layout.card_width_dp,layout.card_height_dp],title_to_card_dp:layout.measurements.title_to_card,cta_camera_gap_dp:layout.footer.cta_camera_gap_dp,cta_camera_touch_dp:layout.measurements.cta_camera_touch,camera_bottom_safe_dp:layout.measurements.camera_bottom_safe,needs_scroll:layout.needs_scroll,footer_y_dp:layout.actual_bounds_dp.footer.top})) ,maximum_component_delta_dp:jump});
        }else probes.push({probe_id:probe.id,width_dp:probe.width,font_scale:scale,safe_insets_dp:probeInsets,views:rows.map(layout=>({height_dp:layout.logical_viewport_dp[1],safe_height_dp:layout.safe_height_dp,card_size_dp:[layout.card_width_dp,layout.card_height_dp],title_to_card_dp:layout.measurements.title_to_card,cta_camera_gap_dp:layout.measurements.cta_camera_touch,camera_bottom_safe_dp:layout.measurements.camera_bottom_safe,needs_scroll:layout.needs_scroll}))});
      }
    }
    await setSafeInsets(page,defaultInsets);
    report.checks.fixed_viewports=fixedResults;
    report.checks.continuity_probes=probes;
    report.checks.font_1_3=font130;
    await page.locator('#layerTree button[data-target="#mainCatchCard"]').click();
    await page.locator('#cameraButton').click();
    await page.waitForFunction(()=>document.querySelector('.preview-toast')?.dataset.visible==='true',undefined,{timeout:10000});
    report.checks.layer_selection=await page.evaluate(()=>window.NH01SelectedLayer);
    report.checks.camera_button_toast=await page.locator('.preview-toast').innerText();
    await page.screenshot({path:join(evidenceDir,'acceptance-workspace.png'),fullPage:true,animations:'disabled'});
    report.screenshots.push(join(evidenceDir,'acceptance-workspace.png'));

    const missingPage=await browser.newPage({viewport:{width:1500,height:1200}});
    await missingPage.route('**/NH01_RESPONSIVE_LAYOUT_V2.json',route=>route.abort());
    await missingPage.goto(previewUrl,{waitUntil:'domcontentloaded',timeout:60000});
    await missingPage.locator('#contractFailure').waitFor({state:'visible',timeout:30000});
    const missingFailure=await missingPage.locator('#contractFailure').innerText();
    assert(/已停止/.test(missingFailure)&&/未回退/.test(missingFailure),'Missing contract did not fail closed');
    assert(await missingPage.evaluate(()=>window.NH01GeminiPreviewLayoutReady!==true),'Missing contract used stale/fallback layout');
    await missingPage.close();

    const mismatchPage=await browser.newPage({viewport:{width:1500,height:1200}});
    const originalContract=await page.evaluate(async path=>(await (await fetch(path,{cache:'no-store'})).json()),contract.path);
    originalContract.contract_version='9.9';
    await mismatchPage.route('**/NH01_RESPONSIVE_LAYOUT_V2.json',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(originalContract)}));
    await mismatchPage.goto(previewUrl,{waitUntil:'domcontentloaded',timeout:60000});
    await mismatchPage.locator('#contractFailure').waitFor({state:'visible',timeout:30000});
    const versionFailure=await mismatchPage.locator('#contractFailure').innerText();
    assert(/version mismatch/.test(versionFailure),'Version mismatch did not fail closed');
    await mismatchPage.close();
    const missingFieldPage=await browser.newPage({viewport:{width:1500,height:1200}});
    const missingFieldContract={...originalContract,preview_engine:{...originalContract.preview_engine,parameters:{...originalContract.preview_engine.parameters,content:{...originalContract.preview_engine.parameters.content}}}};
    delete missingFieldContract.preview_engine.parameters.content.max_width_dp;
    await missingFieldPage.route('**/NH01_RESPONSIVE_LAYOUT_V2.json',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(missingFieldContract)}));
    await missingFieldPage.goto(previewUrl,{waitUntil:'domcontentloaded',timeout:60000});
    await missingFieldPage.locator('#contractFailure').waitFor({state:'visible',timeout:30000});
    const missingFieldFailure=await missingFieldPage.locator('#contractFailure').innerText();
    assert(/content.max_width_dp/.test(missingFieldFailure),'Missing required contract field did not fail closed');
    assert(await missingFieldPage.evaluate(()=>window.NH01GeminiPreviewLayoutReady!==true),'Missing required field used a stale/fallback layout');
    await missingFieldPage.close();
    report.checks.fail_closed={missing_contract:missingFailure,version_mismatch:versionFailure,missing_required_field:missingFieldFailure,no_fallback:true};

    assert(failedRequests.length===0,'Same-origin requests failed: '+failedRequests.join('; '));
    assert(badResponses.length===0,'Same-origin responses returned errors: '+badResponses.join('; '));
    assert(errors.length===0,'Browser errors: '+errors.join('; '));
    report.status='PASS';
  } catch (error) {
    report.status='FAIL';
    report.failure=error.stack||String(error);
  } finally {
    await browser.close();
    if(server) await new Promise(resolvePromise=>server.close(resolvePromise));
  }
  await writeFile(args.output,JSON.stringify(report,null,2)+'\n');
  process.stdout.write(JSON.stringify({status:report.status,p3_06_status:report.p3_06_status,p3_06_blockers:report.p3_06_blockers||[],manager:report.checks.manager,preview_url:report.preview_url,contract:report.checks.contract&&{id:report.checks.contract.id,version:report.checks.contract.version,sha256:report.checks.contract.sha256,source_sha:report.checks.contract.source_sha,deployment_sha:report.checks.contract.deployment_sha,anchors:report.checks.contract.anchors.map(anchor=>({id:anchor.id,status:anchor.status,sha256:anchor.actual_sha256,canvas_px:anchor.actual_canvas_px,width_only_static:anchor.width_only_static}))},fixed_viewports:report.checks.fixed_viewports?.map(item=>({profile:item.profile_id,viewport:item.requested_viewport_dp,card:[item.measured.card_width_dp,item.measured.card_height_dp],title_to_card:item.measured.measurements.title_to_card,scroll:item.measured.needs_scroll})),probes:report.checks.continuity_probes,fail_closed:report.checks.fail_closed,screenshots:report.screenshots,evidence:args.output},null,2)+'\n');
  if(report.status!=='PASS')throw new Error(report.failure||'NH01 responsive browser verification failed');
}
main().catch(error=>{console.error(error.stack||error);process.exitCode=1;});
