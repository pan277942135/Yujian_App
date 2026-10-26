const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const crypto=require('node:crypto');
const sandbox={window:{},AbortController,TextEncoder,crypto:crypto.webcrypto,setTimeout,clearTimeout};
vm.runInNewContext(fs.readFileSync('design/manager/nh01_structured_workspace.js','utf8'),sandbox);
const calculate=sandbox.window.NH01StructuredWorkspace.calculate;
const createResourceLoader=sandbox.window.NH01StructuredWorkspace.createResourceLoader;
const {createLayerTree,flattenLayerTree,findLayer,measuredLayerBounds}=sandbox.window.NH01StructuredWorkspace;
const staticArchiveContract=JSON.parse(fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json'));
const contract=JSON.parse(fs.readFileSync('design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json'));
const previewRules=JSON.parse(fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_STRUCTURED_PREVIEW_LAYOUT_RULES_V2.json'));
const previewMedia=JSON.parse(fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_STRUCTURED_PREVIEW_MEDIA_V1.json'));
const markdown=fs.readFileSync('design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.md','utf8');
const manifest=JSON.parse(fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json'));
const resourceBinding=JSON.parse(fs.readFileSync('design/system_v3/09_Android_Implementation_Contracts/Normal_Home/Normal_Home_Resource_Binding_V1.json'));
const componentTree=JSON.parse(fs.readFileSync('design/manager/nh01_acceptance/data/NH01_Component_Tree_V1.json'));
const structure=JSON.parse(fs.readFileSync('design/manager/nh01_acceptance/data/NH01_Structure_V1.json'));
function assertFileHash(path, expected, label) {
  const bytes=fs.readFileSync(path);
  assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),expected,`${label} SHA-256 must match its authority`);
}
for(const asset of manifest.assets){
  assertFileHash(asset.path,asset.sha256,asset.asset_id);
}
for(const asset of [
  ...resourceBinding.resources.filter(item=>item.path?.endsWith('/Morning_Lake_Master_V1.png')||item.path?.endsWith('/nh07_default_avatar_1x_64.png')),
  resourceBinding.brand_title_binding,
  resourceBinding.camera_primary_binding,
  {asset_id:'approved Normal Home V2 avatar',path:'app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png',sha256:'fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528'},
  {asset_id:'NH01 original visual reference',path:manifest.original_reference.path,sha256:manifest.original_reference.historical_sha256}
]) assertFileHash(asset.path||asset.registry_path,asset.sha256,asset.asset_id);
for(const asset of previewMedia.assets) assertFileHash(asset.path,asset.sha256,asset.id);
assert.equal(previewRules.status,'PREVIEW_ONLY');
assert.equal(previewMedia.assets.length,3);
assert.ok(previewMedia.assets.every(asset=>asset.asset_status==='GENERATED_ILLUSTRATIVE_ONLY'));

const layerRoot=createLayerTree(componentTree,structure);
const layers=Array.from(flattenLayerTree(layerRoot));
const layerIds=layers.map(layer=>layer.id);
assert.equal(new Set(layerIds).size,layerIds.length,'NH01 tree must have unique stable layer IDs');
assert.equal(layerRoot.id,'page');
assert.deepEqual(Array.from(layerRoot.children,node=>node.id),['environment','header','stats','recent-header','pager','capture-action-group']);
assert.deepEqual(Array.from(findLayer(layerRoot,'environment').children,node=>node.id),['background']);
assert.deepEqual(Array.from(findLayer(layerRoot,'header').children,node=>node.id),['brand','avatar']);
assert.deepEqual(Array.from(findLayer(layerRoot,'stats').children,node=>node.id),['stat-species','stat-catch','stat-days']);
assert.deepEqual(Array.from(findLayer(layerRoot,'recent-header').children,node=>node.id),['recent-title','recent-all','recent-chevron']);
assert.deepEqual(Array.from(findLayer(layerRoot,'pager').children,node=>node.id),['peek-left','hero','peek-right','pager-indicator']);
assert.deepEqual(Array.from(findLayer(layerRoot,'capture-action-group').children,node=>node.id),['cta','camera']);
assert.equal(findLayer(layerRoot,'cta').parentId,'capture-action-group');
assert.equal(findLayer(layerRoot,'cta').sourceNodeId,null,'CTA must not reuse its parent’s Figma node ID as a fake child mapping');
assert.equal(findLayer(layerRoot,'stat-species').mappingStatus,'NOT_IN_MAPPING_SNAPSHOT');
assert.equal(findLayer(layerRoot,'avatar').sourceAssetId,'avatar_default_nh07','the historical Figma asset mapping must remain visible beside the approved V2 active source');
assert.ok(!layerIds.includes('top')&&!layerIds.includes('footer'),'the logical A band and an empty Footer overlay are not real DOM components');
const measured=measuredLayerBounds({getBoundingClientRect:()=>({left:170,top:120,width:40,height:20})},{getBoundingClientRect:()=>({left:100,top:50})},.5);
assert.deepEqual({...measured},{x:140,y:140,w:80,h:40},'DOM measurements must normalize viewport zoom around the artboard origin');
const authorityConflict=sandbox.window.NH01StructuredWorkspace.hasAuthorityConflict(markdown,contract);
assert.equal(authorityConflict,false,'V2 Markdown and machine contract agree on equal S/2 allocation');
assert.equal(contract.contract_id,'NH01_RESPONSIVE_LAYOUT_V2');
assert.equal(contract.logical_allocation.allocation_when_positive_surplus.a_to_b,'gAB_base + S/2');
assert.equal(contract.logical_allocation.allocation_when_positive_surplus.b_to_c,'gBC_base + S/2');
assert.equal(previewRules.status,'PREVIEW_ONLY');
assert.equal(previewRules.numeric_authority,'NONE');
assert.equal(previewRules.no_numeric_overrides,true);
assert.ok(!('hero' in previewRules)&&!('pager' in previewRules)&&!('vertical_allocation' in previewRules),'preview metadata must not define a second numeric rule set');
assert.equal(staticArchiveContract.contract_id,'NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1');
const staleMarkdown=markdown.replace('gAB_base + S/2','gAB_base + 2S/3').replace('gBC_base + S/2','gBC_base + S/3');
assert.equal(sandbox.window.NH01StructuredWorkspace.hasAuthorityConflict(staleMarkdown,contract),true,'stale 2:1 Markdown must remain an explicit authority conflict');
const profiles=[[320,480],[360,640],[390,844],[430,932],[480,480],[480,700],[480,1100],[412,915]];
let observedPositiveSurplus=false;
for(const [width,height] of profiles){
  for(const font of [1,1.3,1.5]){
    const r=calculate(width,height,font,24,24,contract,authorityConflict,previewRules);
    assert.equal(r.safeHeight,height-48);
    assert.ok(Math.abs(r.gapABExtra-r.S/2)<1e-5,'A→B receives exactly S/2');
    assert.ok(Math.abs(r.gapBCExtra-r.S/2)<1e-5,'B→C receives exactly S/2');
    assert.equal(r.outerBreathing,0,'no separate outer-breathing reserve is permitted');
    if(r.S>0)observedPositiveSurplus=true;
    assert.ok(r.targetHeroWidth<=contract.preview_engine.parameters.hero.maximum_width_dp+1e-5);
    assert.ok(r.actualHeroWidth<=r.targetHeroWidth+1e-5 || (width===320&&height===480&&font===1),'Hero must respect target width except exact M01');
    assert.ok(Math.abs(r.actualHeroWidth/r.heroHeight-247/293)<1e-5,'Hero must preserve the shared 247:293 ratio');
    assert.ok(r.cameraSize>=56&&r.cameraSize<=80);
    assert.ok(r.cameraTouchSize>=72);
    assert.equal(r.contractId,'NH01_RESPONSIVE_LAYOUT_V2');
    assert.ok(r.rects['peek-left']&&r.rects['peek-right'],'both Peek slots need measurable geometry');
    assert.ok(Math.abs((r.rects['peek-right'].x-r.rects.hero.x-r.rects.hero.w)-r.peekGap)<1e-5,'right Peek gap comes from V2');
    assert.ok(Math.abs((r.rects.hero.x-r.rects['peek-left'].x-r.rects['peek-left'].w)-r.peekGap)<1e-5,'left Peek gap matches right');
    assert.ok(r.peekGap>=contract.preview_engine.parameters.neighbor.peek_gap.minimum_dp&&r.peekGap<=contract.preview_engine.parameters.neighbor.peek_gap.maximum_dp);
    assert.ok(r.pagerContentHeight>=r.heroHeight+r.indicatorGap+r.indicatorHeight-1e-5);
    assert.ok(r.rects['capture-action-group'].y+r.hC<=height-24+.5,'Footer must stay inside bottom safe inset');
    if(font===1)assert.ok(r.hC<130,'static CTA text must not create a redundant 48dp touch box');
    else assert.ok(r.hC<146,'larger text must fit without a duplicate CTA touch container');
    if(width===320&&height===480&&font===1){
      assert.ok(r.actualHeroWidth>=contract.preview_engine.parameters.hero.short_screen_exception.minimum_width_dp-1e-5,'M01 preserves minimum Hero width');
      assert.ok(r.heroHeight>=contract.preview_engine.parameters.hero.short_screen_exception.minimum_readable_floor_dp-1e-5,'M01 preserves minimum-readable floor');
      assert.equal(r.pagerNeedsScroll,false,'exact M01 must not scroll');
      assert.ok(!r.conflicts.some(item=>item.includes('FAIL_CONSTRAINT')),'exact M01 must satisfy the short-screen constraints');
    }
  }
}
assert.ok(observedPositiveSurplus,'matrix must exercise positive surplus');
const normal=calculate(390,844,1,24,24,contract,authorityConflict,previewRules);
assert.ok(Math.abs(normal.actualHeroWidth-273)<.01);
assert.ok(normal.hC>=90&&normal.hC<=125,'CaptureActionGroup should fit measured CTA line and camera target');
assert.equal(normal.gapABExtra,normal.S/2);
assert.equal(normal.gapBCExtra,normal.S/2);
assert.ok(calculate(390,844,1,24,24,contract,true,previewRules).conflicts.some(x=>x.includes('SPEC_CONFLICT')&&x.includes('Markdown')));
const malformed={...contract,preview_engine:{...contract.preview_engine,parameters:{...contract.preview_engine.parameters,hero:null}}};
assert.ok(calculate(390,844,1,24,24,malformed,false,previewRules).conflicts.some(x=>x.includes('SPEC_CONFLICT')),'missing V2 numeric parameters must fail closed');

const response=(body,{status=200,type='application/json'}={})=>new Response(body,{status,headers:{'content-type':type}});
const digestBytes=async bytes=>crypto.createHash('sha256').update(Buffer.from(bytes)).digest('hex');
async function runLoaderCases(){
  const events=[];
  const goodLoader=createResourceLoader({fetchImpl:async()=>response('{"ok":true}'),digestBytes,onUpdate:event=>events.push(event.state),timeoutMs:100});
  const good=await goodLoader.load([{id:'contract',url:'/contract.json',type:'json',mime:/application\/json/,validate:value=>value.ok}]);
  assert.equal(good.state,'READY');
  assert.equal(good.resources[0].parse_result,'VALID_JSON');
  assert.equal(good.resources[0].status,'READY');
  assert.equal(good.resources[0].http_status,200);
  assert.equal(good.data.contract.ok,true);
  assert.ok(events.includes('LOADING')&&events.includes('READY'));

  const missing=await createResourceLoader({fetchImpl:async()=>response('missing',{status:404,type:'text/plain'}),digestBytes,timeoutMs:100})
    .load([{id:'manifest',url:'/manifest.json',type:'json',mime:/application\/json/}]);
  assert.equal(missing.state,'ERROR');
  assert.equal(missing.resources[0].http_status,404);
  assert.equal(missing.resources[0].failure_kind,'http');

  const invalid=await createResourceLoader({fetchImpl:async()=>response('<html>not JSON</html>',{type:'text/html'}),digestBytes,timeoutMs:100})
    .load([{id:'contract',url:'/contract.json',type:'json',mime:/application\/json/}]);
  assert.equal(invalid.state,'ERROR');
  assert.equal(invalid.resources[0].http_status,200);
  assert.equal(invalid.resources[0].failure_kind,'content_type');
  const invalidJson=await createResourceLoader({fetchImpl:async()=>response('{broken'),digestBytes,timeoutMs:100})
    .load([{id:'contract',url:'/contract.json',type:'json',mime:/application\/json/}]);
  assert.equal(invalidJson.resources[0].failure_kind,'parse');
  assert.equal(invalidJson.resources[0].parse_result,'INVALID');

  const integrity=await createResourceLoader({fetchImpl:async()=>response(Buffer.from('wrong image bytes'),{type:'image/png'}),digestBytes,timeoutMs:100})
    .load([{id:'frozen-image',url:'/frozen.png',type:'bytes',mime:/image\/png/,expectedSha256:'0'.repeat(64)}]);
  assert.equal(integrity.state,'ERROR');
  assert.equal(integrity.resources[0].http_status,200);
  assert.equal(integrity.resources[0].failure_kind,'integrity');
  assert.match(integrity.resources[0].failure_reason,/SHA-256 mismatch/);

  let failOnce=true;
  const retryStates=[];
  const retryLoader=createResourceLoader({fetchImpl:async()=>{if(failOnce){failOnce=false;return response('down',{status:503,type:'text/plain'});}return response('{"ok":true}');},digestBytes,onUpdate:event=>retryStates.push(event.state),timeoutMs:100});
  const first=await retryLoader.load([{id:'manifest',url:'/manifest.json',type:'json',mime:/application\/json/}]);
  assert.equal(first.state,'ERROR');
  const recovered=await retryLoader.load([{id:'manifest',url:'/manifest.json',type:'json',mime:/application\/json/}],{retry:true});
  assert.equal(recovered.state,'READY');
  assert.ok(retryStates.includes('RETRYING'));

  const timeoutLoader=createResourceLoader({fetchImpl:(_url,{signal})=>new Promise((_resolve,reject)=>signal.addEventListener('abort',()=>reject(new Error('aborted')),{once:true})),digestBytes,timeoutMs:15});
  const timed=await timeoutLoader.load([{id:'slow',url:'/slow.json',type:'json'}]);
  assert.equal(timed.state,'ERROR');
  assert.equal(timed.resources[0].failure_kind,'timeout');
  assert.match(timed.resources[0].failure_reason,/15 ms/);

  let release;
  const staleLoader=createResourceLoader({fetchImpl:()=>new Promise(resolve=>{release=resolve;}),digestBytes,timeoutMs:100});
  const stalePromise=staleLoader.load([{id:'old-route',url:'/old.json',type:'json'}]);
  await new Promise(resolve=>setTimeout(resolve,0));
  staleLoader.abort();
  release(response('{"old":true}'));
  const stale=await stalePromise;
  assert.equal(stale.state,'CANCELLED','an aborted route generation cannot report READY');
}

runLoaderCases().then(()=>console.log('NH01_STRUCTURED=PASS source-mapped component tree; 8 viewport cases × 3 font scales; frozen and illustrative asset hashes; Hero ratio; Peek spacing/visibility inputs; bounded gaps; footer hit geometry; HTTP/JSON/timeout/retry/stale-route states')).catch(error=>{console.error(error);process.exitCode=1;});
