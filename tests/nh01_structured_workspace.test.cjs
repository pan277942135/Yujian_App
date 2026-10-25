const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const crypto=require('node:crypto');
const sandbox={window:{},AbortController,TextEncoder,crypto:crypto.webcrypto,setTimeout,clearTimeout};
vm.runInNewContext(fs.readFileSync('design/manager/nh01_structured_workspace.js','utf8'),sandbox);
const calculate=sandbox.window.NH01StructuredWorkspace.calculate;
const createResourceLoader=sandbox.window.NH01StructuredWorkspace.createResourceLoader;
const contract=JSON.parse(fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json'));
const markdown=fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md','utf8');
const manifest=JSON.parse(fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json'));
const resourceBinding=JSON.parse(fs.readFileSync('design/system_v3/09_Android_Implementation_Contracts/Normal_Home/Normal_Home_Resource_Binding_V1.json'));
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
  {asset_id:'NH01 original visual reference',path:manifest.original_reference.path,sha256:manifest.original_reference.historical_sha256}
]) assertFileHash(asset.path||asset.registry_path,asset.sha256,asset.asset_id);
const authorityConflict=sandbox.window.NH01StructuredWorkspace.hasAuthorityConflict(markdown,contract);
assert.equal(authorityConflict,false,'current NH01 V1.1 Markdown and machine contract agree on equal split');
const staleMarkdown=markdown.replace('gAB = gAB_base + S/2','gAB = gAB_base + 2S/3').replace('gBC = gBC_base + S/2','gBC = gBC_base + S/3');
assert.equal(sandbox.window.NH01StructuredWorkspace.hasAuthorityConflict(staleMarkdown,contract),true,'a stale Markdown 2:1 rule must remain an explicit authority conflict');
const profiles=[[320,480],[360,640],[390,844],[430,932],[480,844]];
let prior=null;
let observedPositiveSurplus=false;
for(const [width,height] of profiles){
  for(const font of [1,1.3,1.5]){
    const r=calculate(width,height,font,24,24,contract,authorityConflict);
    assert.equal(r.safeHeight,height-48);
    assert.ok(Math.abs((r.gAB-r.baseAB)-(r.gBC-r.baseBC))<1e-5);
    assert.ok(Math.abs(r.S-Math.max(0,r.safeHeight-(r.hA+r.hB+r.hC+r.baseAB+r.baseBC)))<1e-5);
    if(r.S>0){observedPositiveSurplus=true;assert.ok(Math.abs((r.gAB-r.baseAB)-r.S/2)<1e-5);}
    assert.ok(r.actualHeroWidth<=337.2+1e-5);
    assert.ok(r.cameraSize>=56&&r.cameraSize<=80);
    assert.ok(r.cameraTouchSize>=72);
    assert.equal(r.contractId,'NH01_RESPONSIVE_FOUNDATION_V1');
    assert.ok(r.rects['peek-left']&&r.rects['peek-right'],'both real-neighbor slots need measurable geometry');
    if(width===320 && height===480 && font===1) assert.ok(r.conflicts.some(x=>x.includes('145dp')));
  }
  const current=calculate(width,height,1,24,24,contract,authorityConflict);
  if(prior) { assert.ok(current.fontSp>=prior.fontSp); assert.ok(current.cameraSize>=prior.cameraSize); assert.ok(current.actualHeroWidth>=prior.actualHeroWidth); }
  prior=current;
}
assert.ok(observedPositiveSurplus,'profile set must exercise a positive surplus allocation');
assert.ok(calculate(390,844,1,24,24,contract,true).conflicts.some(x=>x.includes('SPEC_CONFLICT')&&x.includes('Markdown A.2')));
assert.ok(calculate(390,844,1,24,24,{...contract,foundation_layout_principles:{...contract.foundation_layout_principles,allocation_when_surplus_positive:{a_to_b_final:'unparseable',b_to_c_final:'unparseable'}}}).conflicts.some(x=>x.includes('表达式')));

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

runLoaderCases().then(()=>console.log('NH01_STRUCTURED=PASS 5 profiles × 3 font scales; frozen asset hashes verified; contract formulas, Peek bounds, HTTP/JSON/timeout/retry/stale-route states verified')).catch(error=>{console.error(error);process.exitCode=1;});
