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
  const result = { root: process.cwd(), url: null, expectedBuild: null, expectedManagerBuild: null, expectedDeploymentSha: null, deploymentManifestPath: null, privateMedia: false, output: '/tmp/nh01-responsive-preview-report.json', previewPath: 'previews/pr-176/design/manager/nh01_acceptance/gemini-native-preview.html' };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--root') result.root = resolve(argv[++i]);
    else if (arg === '--url') result.url = argv[++i];
    else if (arg === '--expected-build') result.expectedBuild = argv[++i];
    else if (arg === '--expected-manager-build') result.expectedManagerBuild = argv[++i];
    else if (arg === '--expected-deployment-sha') result.expectedDeploymentSha = argv[++i];
    else if (arg === '--deployment-manifest-path') result.deploymentManifestPath = argv[++i].replace(/^\/+/, '');
    else if (arg === '--private-media') result.privateMedia = true;
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
async function collectPrivatePhotoGeometry(page) {
  return page.evaluate(() => {
    const cards=[['left','#leftCatchCard'],['hero','#mainCatchCard'],['right','#rightCatchCard']].map(([role,selector])=>{
      const card=document.querySelector(selector);const image=card.querySelector('img.catch-photo-fit[data-private-photo-id]');
      if(!image||card.hidden)return {role,hidden:card.hidden,asset_id:image?.dataset.privatePhotoId||null};
      const c=card.getBoundingClientRect(),r=image.getBoundingClientRect();
      const ratio=image.naturalWidth/image.naturalHeight,boxRatio=r.width/r.height;
      const width=boxRatio>ratio?r.height*ratio:r.width,height=boxRatio>ratio?r.height:r.width/ratio;
      const left=r.left+(r.width-width)/2,top=r.top+(r.height-height)/2;
      return {role,hidden:false,asset_id:image.dataset.privatePhotoId,fixture_id:image.dataset.privateFixtureId,sha256:image.dataset.sha256,object_fit:getComputedStyle(image).objectFit,natural_px:[image.naturalWidth,image.naturalHeight],card_bounds:[c.left,c.top,c.right,c.bottom],rendered_full_photo_bounds:[left,top,left+width,top+height],full_photo_within_card:left>=c.left-1&&top>=c.top-1&&left+width<=c.right+1&&top+height<=c.bottom+1,rendered_aspect_ratio:width/height,source_aspect_ratio:ratio};
    });
    return {cards,viewport:[window.NH01GeminiPreviewLayout?.logical_viewport_dp?.[0],window.NH01GeminiPreviewLayout?.logical_viewport_dp?.[1]],font_scale:window.NH01GeminiPreviewLayout?.system_font_scale};
  });
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
  const previewAddress = new URL(args.previewPath, origin);
  if (args.privateMedia) previewAddress.searchParams.set('privateMedia','1');
  const previewUrl = previewAddress.href;
  const managerUrl = new URL('design/manager/#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01', origin).href;
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH } : {})
  });
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
      visibleNeighborCount:[...document.querySelectorAll('.neighbor-card')].filter(node=>!node.hidden).length,
      heroPlaceholder:!!document.querySelector('#heroPlaceholder')&&!document.querySelector('#heroPlaceholder').hidden&&document.querySelector('#heroPlaceholder').dataset.status==='BLOCKED_ASSET_SOURCE',
      heroPhotoRendered:[...document.querySelectorAll('#mainCatchCard img[data-private-photo-id]')].some(image=>image.classList.contains('catch-photo-fit')&&image.complete&&image.naturalWidth>0),
      neighborPhotoCount:[...document.querySelectorAll('.neighbor-card:not([hidden]) img.catch-photo-fit[data-private-photo-id]')].filter(image=>image.complete&&image.naturalWidth>0).length,
      privatePhotoIdentities:[...document.querySelectorAll('.neighbor-card:not([hidden]) img.catch-photo-fit[data-private-photo-id],#mainCatchCard img.catch-photo-fit[data-private-photo-id]')].map(image=>({id:image.dataset.privatePhotoId,fixtureId:image.dataset.privateFixtureId,sha256:image.dataset.sha256,fit:getComputedStyle(image).objectFit,naturalWidth:image.naturalWidth,naturalHeight:image.naturalHeight})),
      privatePhotoReferences:[...document.querySelectorAll('img')].map(image=>image.currentSrc||image.src||'').filter(src=>src.includes('sample_recent_catch.jpg')||src.includes('/home_normal/fish_record/')).length,
      layerTree:document.querySelectorAll('#layerTree button').length,
      propertyInspector:!!document.querySelector('[aria-label="所选图层属性检查器"]'),
      ruleRows:document.querySelectorAll('#responsiveRuleList .rule-row').length,
      referenceRendered:document.querySelector('#anchorImage')?.naturalWidth>0
    }));
    assert(visibleStructure.brand && visibleStructure.stats === 3 && visibleStructure.sectionTitle && visibleStructure.hero && visibleStructure.footer, 'Brand, three statistics, Recent Catch, Hero and Footer hierarchy must render');
    assert(visibleStructure.middle&&visibleStructure.footerOutsideMiddle&&visibleStructure.layerTree>0&&visibleStructure.propertyInspector,'Footer/Middle composition or existing layer/property workspace is missing');
    assert(visibleStructure.privatePhotoReferences===0, 'An unapproved catch-photo source is referenced by a rendered image');
    if(args.privateMedia){
      const localAudit=await page.evaluate(()=>window.NH01PrivatePhotoAudit);
      assert(localAudit?.status==='PASS'&&localAudit.records?.length===3,'Private local photo binding did not pass SHA, size and type checks: '+JSON.stringify(localAudit));
      assert(!visibleStructure.heroPlaceholder&&visibleStructure.heroPhotoRendered&&visibleStructure.visibleNeighborCount===2&&visibleStructure.neighborPhotoCount===2,'Local preview must show the Hero plus two distinct Peek originals');
      assert(new Set(visibleStructure.privatePhotoIdentities.map(item=>item.id)).size===3&&new Set(visibleStructure.privatePhotoIdentities.map(item=>item.sha256)).size===3&&new Set(visibleStructure.privatePhotoIdentities.map(item=>item.fixtureId)).size===3,'Three visible cards do not have distinct asset, source hash and fixture identities');
      assert(visibleStructure.privatePhotoIdentities.every(item=>item.fit==='contain'&&item.naturalWidth>0&&item.naturalHeight>0),'A private photo was stretched, cropped in the foreground, or failed to load');
      report.checks.private_media=localAudit;
    }else{
      assert(visibleStructure.hiddenNeighbors&&visibleStructure.heroPlaceholder&&!visibleStructure.heroPhotoRendered,'Public preview must keep private photos unavailable and preserve the labeled Hero placeholder');
      report.checks.private_media={status:'NOT_REQUESTED',public_redistribution:'NOT_CONFIRMED'};
    }
    assert(visibleStructure.ruleRows >= 8 && visibleStructure.referenceRendered, 'Interactive rule viewer and frozen-reference viewer must render');
    const assets = await page.locator('img[data-asset-id]').evaluateAll(images => images.map(image => ({asset:image.dataset.assetId,src:image.currentSrc,complete:image.complete,naturalWidth:image.naturalWidth,naturalHeight:image.naturalHeight})));
    assert(assets.every(image => image.complete && image.naturalWidth > 0), 'Registered preview assets did not all load: ' + JSON.stringify(assets.filter(image=>!image.complete||image.naturalWidth<=0)));
    report.checks.contract = {id:contract.id,version:contract.version,path:contract.path,sha256:contract.sha256,status:contract.status,source_sha:contract.source_sha,deployment_sha:contract.deployment_sha,anchor_count:anchors.length,anchors,visible_structure:visibleStructure,registered_assets:assets};
    report.checks.fixed_viewport_frames=frameContracts;
    if(args.privateMedia){
      const initialTriplet=await page.evaluate(()=>({selected:window.NH01SelectedPrivatePhoto,left:document.querySelector('#leftCatchCard').querySelector('.catch-photo-fit')?.dataset.privatePhotoId||null,hero:document.querySelector('#mainCatchCard .catch-photo-fit')?.dataset.privatePhotoId||null,right:document.querySelector('#rightCatchCard').querySelector('.catch-photo-fit')?.dataset.privatePhotoId||null}));
      assert(initialTriplet.selected?.index===1&&initialTriplet.left==='peek_left_photo'&&initialTriplet.hero==='hero_photo'&&initialTriplet.right==='peek_right_photo','Initial three-card mapping does not match the assigned Hero / Peek identities: '+JSON.stringify(initialTriplet));
      const publicPage=await browser.newPage({viewport:{width:1600,height:1400},deviceScaleFactor:1});
      const publicAddress=new URL(args.previewPath,origin);
      const publicResponse=await publicPage.goto(publicAddress.href,{waitUntil:'domcontentloaded',timeout:60000});
      assert(publicResponse?.status()===200,'Public placeholder comparison route HTTP status is not 200');
      await publicPage.waitForFunction(()=>window.NH01GeminiPreviewReady===true&&window.NH01GeminiPreviewLayoutReady===true,undefined,{timeout:60000});
      const publicState=await publicPage.evaluate(()=>({heroPlaceholder:!document.querySelector('#heroPlaceholder').hidden,hiddenNeighbors:[...document.querySelectorAll('.neighbor-card')].every(node=>node.hidden),photoElements:[...document.querySelectorAll('.catch-photo-fit')].filter(image=>image.complete&&image.naturalWidth>0).length}));
      assert(publicState.heroPlaceholder&&publicState.hiddenNeighbors&&publicState.photoElements===0,'Public comparison route exposed private photos instead of the placeholder state');
      const publicShot=join(evidenceDir,'public-placeholder-390x844.png');
      await publicPage.locator('#device').screenshot({path:publicShot,animations:'disabled'});report.screenshots.push(publicShot);
      await publicPage.close();
      const tripletShot=join(evidenceDir,'three-card-initial-390x844.png');
      await page.locator('#device').screenshot({path:tripletShot,animations:'disabled'});report.screenshots.push(tripletShot);
      await page.locator('#leftCatchCard').click();
      await page.waitForFunction(()=>window.NH01SelectedPrivatePhoto?.index===0&&window.NH01GeminiPreviewLayoutReady,undefined,{timeout:10000});
      const firstTriplet=await page.evaluate(()=>({selected:window.NH01SelectedPrivatePhoto,leftHidden:document.querySelector('#leftCatchCard').hidden,hero:document.querySelector('#mainCatchCard .catch-photo-fit')?.dataset.privatePhotoId||null,right:document.querySelector('#rightCatchCard .catch-photo-fit')?.dataset.privatePhotoId||null}));
      assert(firstTriplet.selected.asset_id==='peek_left_photo'&&firstTriplet.leftHidden&&firstTriplet.hero==='peek_left_photo'&&firstTriplet.right==='hero_photo','Selecting the left Peek did not update the finite carousel: '+JSON.stringify(firstTriplet));
      const firstShot=join(evidenceDir,'three-card-first-record.png');await page.locator('#device').screenshot({path:firstShot,animations:'disabled'});report.screenshots.push(firstShot);
      await page.locator('#rightCatchCard').click();await page.waitForFunction(()=>window.NH01SelectedPrivatePhoto?.index===1&&window.NH01GeminiPreviewLayoutReady,undefined,{timeout:10000});
      await page.locator('#rightCatchCard').click();await page.waitForFunction(()=>window.NH01SelectedPrivatePhoto?.index===2&&window.NH01GeminiPreviewLayoutReady,undefined,{timeout:10000});
      const lastTriplet=await page.evaluate(()=>({selected:window.NH01SelectedPrivatePhoto,left:document.querySelector('#leftCatchCard .catch-photo-fit')?.dataset.privatePhotoId||null,rightHidden:document.querySelector('#rightCatchCard').hidden}));
      assert(lastTriplet.selected.asset_id==='peek_right_photo'&&lastTriplet.left==='hero_photo'&&lastTriplet.rightHidden,'Selecting the right Peek did not stop at the finite last record: '+JSON.stringify(lastTriplet));
      await page.locator('#rightCatchCard').evaluate(node=>node.dispatchEvent(new MouseEvent('click',{bubbles:true})));
      assert(await page.evaluate(()=>window.NH01SelectedPrivatePhoto.index===2),'Carousel wrapped past the final real record.');
      const lastShot=join(evidenceDir,'three-card-last-record.png');await page.locator('#device').screenshot({path:lastShot,animations:'disabled'});report.screenshots.push(lastShot);
      await page.locator('#leftCatchCard').click();await page.waitForFunction(()=>window.NH01SelectedPrivatePhoto?.index===1&&window.NH01GeminiPreviewLayoutReady,undefined,{timeout:10000});
      await page.locator('#rightCatchCard').focus();await page.keyboard.press('Enter');await page.waitForFunction(()=>window.NH01SelectedPrivatePhoto?.index===2&&window.NH01GeminiPreviewLayoutReady,undefined,{timeout:10000});
      await page.locator('#leftCatchCard').focus();await page.keyboard.press('Enter');await page.waitForFunction(()=>window.NH01SelectedPrivatePhoto?.index===1&&window.NH01GeminiPreviewLayoutReady,undefined,{timeout:10000});
      report.checks.three_card_switch={status:'PASS',initial:initialTriplet,first:firstTriplet,last:lastTriplet,finite_end_no_wrap:true,mouse_click:true,keyboard_enter:true,center_restored:await page.evaluate(()=>window.NH01SelectedPrivatePhoto.asset_id)};
      report.checks.private_photo_viewports=[];
    }
    const previewAudit=await page.evaluate(()=>window.NH01GeminiPreviewAudit);
    const mediaBlockers=(previewAudit?.assets||[]).filter(asset=>asset.status==='BLOCKED_PUBLIC_REDISTRIBUTION');
    report.checks.nh01_preview_v2={status:mediaBlockers.length?'BLOCKED':'PASS',media_blockers:mediaBlockers.map(asset=>({id:asset.id,status:asset.status,reason:asset.reason||null})),rule:'Public image distribution remains blocked until permission is confirmed; local private photo verification is reported independently.'};
    report.nh01_preview_v2_status=mediaBlockers.length?'BLOCKED':'PASS';
    if(mediaBlockers.length)report.nh01_preview_v2_blockers=report.checks.nh01_preview_v2.media_blockers;
    if(mediaBlockers.length){
      assert(visibleStructure.heroPlaceholder===!visibleStructure.heroPhotoRendered, 'Blocked Hero media must use a placeholder, never an unapproved photo');
      assert(visibleStructure.hiddenNeighbors=== (visibleStructure.neighborPhotoCount===0), 'Neighbor visibility does not match the independently approved photo count');
    }else{
      assert(!visibleStructure.heroPlaceholder && visibleStructure.heroPhotoRendered, 'G-NH01-RESPONSIVE-PREVIEW-V2 cannot pass until an approved Hero photo is actually rendered');
      assert(visibleStructure.visibleNeighborCount===2 && visibleStructure.neighborPhotoCount===2, 'G-NH01-RESPONSIVE-PREVIEW-V2 cannot pass until both independently approved neighbor photos are actually rendered');
    }
    if(args.deploymentManifestPath){
      const deploymentUrl=new URL(args.deploymentManifestPath,origin).href;
      const deploymentResponse=await page.request.get(deploymentUrl,{timeout:30000});
      assert(deploymentResponse.status()===200,'Preview deployment manifest HTTP status is not 200');
      const deploymentManifest=await deploymentResponse.json();
      assert(deploymentManifest.contract_id===contract.id&&deploymentManifest.contract_version===contract.version,'Deployment manifest contract identity/version mismatch');
      if(deploymentManifest.release_mode==='PREVIEW_ONLY'){
        assert(contract.status==='PROPOSED_FOR_CANONICAL_MAIN_REVIEW','PREVIEW_ONLY deployment cannot claim a frozen contract');
        assert(deploymentManifest.release_gate_effect==='NONE'&&deploymentManifest.acceptance_status==='NOT_CLAIMED','PREVIEW_ONLY deployment must not claim NH01 preview or freeze-gate PASS');
        assert(deploymentManifest.media_policy?.non_public_asset_files_copied===false,'PREVIEW_ONLY must not copy non-public media');
        if(report.nh01_preview_v2_status==='BLOCKED')assert(deploymentManifest.media_policy?.hero==='PLACEHOLDER_ONLY'&&deploymentManifest.media_policy?.neighbors==='HIDDEN_UNTIL_PUBLIC_REDISTRIBUTION_AUTHORIZED','Blocked media policy is missing from the deployment manifest');
      }else if(deploymentManifest.release_mode==='FROZEN_ACCEPTANCE'){
        assert(contract.status==='SPEC_FROZEN'&&report.nh01_preview_v2_status==='PASS','FROZEN_ACCEPTANCE requires SPEC_FROZEN and NH01 Responsive Preview V2 PASS');
        assert(deploymentManifest.release_gate_effect==='G-NH01-RESPONSIVE-PREVIEW-V2_PASS_REQUIRED','Frozen release manifest must record the NH01 Responsive Preview V2 gate');
      }else{
        throw new Error('Unknown NH01 preview release mode: '+deploymentManifest.release_mode);
      }
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
      if(profile.width===320&&profile.height===480)assert(!layout.needs_scroll, '320x480dp at fontScale 1.0 must not introduce unexpected Middle scrolling');
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
      if(args.privateMedia){
        const geometry=await collectPrivatePhotoGeometry(page);
        assert(geometry.cards.length===3&&geometry.cards.every(card=>!card.hidden&&card.object_fit==='contain'&&card.full_photo_within_card&&numericDelta(card.rendered_aspect_ratio,card.source_aspect_ratio)<0.01),`${profile.id} cropped, stretched, failed or hid a local original: ${JSON.stringify(geometry)}`);
        report.checks.private_photo_viewports.push({profile_id:profile.id,viewport_dp:geometry.viewport,font_scale:geometry.font_scale,cards:geometry.cards.map(({role,asset_id,fixture_id,sha256,object_fit,natural_px,full_photo_within_card})=>({role,asset_id,fixture_id,sha256,object_fit,natural_px,full_photo_within_card}))});
      }
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

    if(args.privateMedia){
      const widthBoundary=[];
      for(const width of [479,480,481]){
        const layout=await setViewport(page,width,844,1);
        assert(layout.measurements.horizontal_overflow_dp<=0.5,`Width-only boundary probe ${width}dp has horizontal overflow`);
        assert(layout.measurements.hero_overlap_footer<=0.5,`Width-only boundary probe ${width}dp overlaps Hero and Footer`);
        const geometry=await collectPrivatePhotoGeometry(page);
        assert(geometry.cards.length===3&&geometry.cards.every(card=>!card.hidden&&card.object_fit==='contain'&&card.full_photo_within_card),`Width-only boundary probe ${width}dp cropped or hid an original: ${JSON.stringify(geometry)}`);
        widthBoundary.push({width_dp:width,height_context_dp:844,height_context_is_probe_only:true,no_M05_height_inferred:true,card_width_dp:layout.card_width_dp,card_height_dp:layout.card_height_dp,title_to_card_dp:layout.measurements.title_to_card,full_photo_fit:true});
        if(width===480){const path=join(evidenceDir,'width-boundary-480dp-probe-only.png');await page.locator('#device').screenshot({path,animations:'disabled'});report.screenshots.push(path);}
      }
      assert(widthBoundary[0].card_width_dp<=widthBoundary[1].card_width_dp+0.5&&widthBoundary[1].card_width_dp<=widthBoundary[2].card_width_dp+0.5,'Hero width is discontinuous across the 480dp width boundary');
      report.checks.width_boundary_480dp={status:'PASS',context:'Synthetic free-resize probe at 479/480/481dp width with 844dp test harness context only; does not infer a height for the M05 width-only static reference.',cases:widthBoundary};
    }

    const font130=await setViewport(page,320,480,1.3);
    report.checks.font_1_3=font130;
    const fontScreenshot=join(evidenceDir,'font-1_3-320x480.png');
    await page.locator('#device').screenshot({path:fontScreenshot,animations:'disabled'});
    report.screenshots.push(fontScreenshot);
    assert(font130.text_lines.species>=1&&font130.text_lines.species<=2&&font130.text_lines.measurement<=2&&font130.text_lines.metadata<=2, `Font scale 1.3 text wrapping exceeded the contract line limits: ${JSON.stringify(font130.text_lines)}`);
    assert(font130.measurements.horizontal_overflow_dp<=0.5, 'Font scale 1.3 caused horizontal overflow');
    const font130Long=await setViewport(page,430,932,1.3);
    assert(font130Long.text_lines.species>=1&&font130Long.text_lines.species<=2&&font130Long.text_lines.measurement<=2&&font130Long.text_lines.metadata<=2, `Long-screen font scale 1.3 text wrapping exceeded the contract line limits: ${JSON.stringify(font130Long.text_lines)}`);
    assert(font130Long.measurements.horizontal_overflow_dp<=0.5, 'Long-screen font scale 1.3 caused horizontal overflow');
    const fontLongScreenshot=join(evidenceDir,'font-1_3-430x932.png');
    await page.locator('#device').screenshot({path:fontLongScreenshot,animations:'disabled'});report.screenshots.push(fontLongScreenshot);
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
    report.checks.interactive_controls={baseline,adaptive_restored:returned.variant,free_viewport:freeWidth.logical_viewport_dp,font_scale_1_3:{short_screen:{font_scale:font130.system_font_scale,text_lines:font130.text_lines,card_size:[font130.card_width_dp,font130.card_height_dp]},wide_long_screen:{font_scale:font130Long.system_font_scale,text_lines:font130Long.text_lines,card_size:[font130Long.card_width_dp,font130Long.card_height_dp]}},safe_inset_adjustment:insetProbe,continuity_probe_safe_insets:defaultInsets,m05_viewer:m05ViewerText};

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
    const mismatchContract=JSON.parse(JSON.stringify(originalContract));
    mismatchContract.contract_version='9.9';
    await mismatchPage.route('**/NH01_RESPONSIVE_LAYOUT_V2.json',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(mismatchContract)}));
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
    assert(/content.max_width_dp/.test(missingFieldFailure),'Missing required contract field did not fail closed: '+missingFieldFailure);
    assert(await missingFieldPage.evaluate(()=>window.NH01GeminiPreviewLayoutReady!==true),'Missing required field used a stale/fallback layout');
    await missingFieldPage.close();
    report.checks.fail_closed={missing_contract:missingFailure,version_mismatch:versionFailure,missing_required_field:missingFieldFailure,no_fallback:true};

    if(args.privateMedia){
      const missingPhotoPage=await browser.newPage({viewport:{width:1600,height:1400}});
      await missingPhotoPage.route('**/local_media/*',route=>route.abort());
      const missingPhotoAddress=new URL(args.previewPath,origin);missingPhotoAddress.searchParams.set('privateMedia','1');
      const missingPhotoResponse=await missingPhotoPage.goto(missingPhotoAddress.href,{waitUntil:'domcontentloaded',timeout:60000});
      assert(missingPhotoResponse?.status()===200,'Image-failure fallback test route HTTP status is not 200');
      await missingPhotoPage.waitForFunction(()=>window.NH01PrivatePhotoAudit?.status==='FAIL'&&window.NH01GeminiPreviewLayoutReady===true,undefined,{timeout:60000});
      const imageFallback=await missingPhotoPage.evaluate(()=>({status:window.NH01PrivatePhotoAudit.status,placeholder:!document.querySelector('#heroPlaceholder').hidden,neighborsHidden:[...document.querySelectorAll('.neighbor-card')].every(card=>card.hidden),photoCount:[...document.querySelectorAll('.catch-photo-fit[data-private-photo-id]')].filter(image=>image.complete&&image.naturalWidth>0).length,message:document.querySelector('#privatePhotoStatus')?.textContent||''}));
      assert(imageFallback.placeholder&&imageFallback.neighborsHidden&&imageFallback.photoCount===0&&/回退/.test(imageFallback.message),'Image-load failure did not fail closed to the labeled public placeholder state: '+JSON.stringify(imageFallback));
      report.checks.image_load_failure_fallback={status:'PASS',...imageFallback};
      await missingPhotoPage.close();
    }

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
  process.stdout.write(JSON.stringify({status:report.status,nh01_preview_v2_status:report.nh01_preview_v2_status,nh01_preview_v2_blockers:report.nh01_preview_v2_blockers||[],manager:report.checks.manager,preview_url:report.preview_url,contract:report.checks.contract&&{id:report.checks.contract.id,version:report.checks.contract.version,sha256:report.checks.contract.sha256,source_sha:report.checks.contract.source_sha,deployment_sha:report.checks.contract.deployment_sha,anchors:report.checks.contract.anchors.map(anchor=>({id:anchor.id,status:anchor.status,sha256:anchor.actual_sha256,canvas_px:anchor.actual_canvas_px,width_only_static:anchor.width_only_static}))},fixed_viewports:report.checks.fixed_viewports?.map(item=>({profile:item.profile_id,viewport:item.requested_viewport_dp,card:[item.measured.card_width_dp,item.measured.card_height_dp],title_to_card:item.measured.measurements.title_to_card,scroll:item.measured.needs_scroll})),probes:report.checks.continuity_probes,fail_closed:report.checks.fail_closed,screenshots:report.screenshots,evidence:args.output},null,2)+'\n');
  if(report.status!=='PASS')throw new Error(report.failure||'NH01 responsive browser verification failed');
}
main().catch(error=>{console.error(error.stack||error);process.exitCode=1;});
