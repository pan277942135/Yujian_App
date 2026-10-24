const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const sandbox={window:{}};
vm.runInNewContext(fs.readFileSync('design/manager/nh01_structured_workspace.js','utf8'),sandbox);
const calculate=sandbox.window.NH01StructuredWorkspace.calculate;
const contract=JSON.parse(fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json'));
const markdown=fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md','utf8');
const manifest=JSON.parse(fs.readFileSync('design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json'));
const resourceBinding=JSON.parse(fs.readFileSync('design/system_v3/09_Android_Implementation_Contracts/Normal_Home/Normal_Home_Resource_Binding_V1.json'));
const crypto=require('node:crypto');
function assertFileHash(path, expected, label) {
  const bytes=fs.readFileSync(path);
  assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),expected,`${label} SHA-256 must match its authority`);
}
for(const asset of manifest.assets){
  assertFileHash(asset.path,asset.sha256,asset.asset_id);
}
for(const asset of [
  ...resourceBinding.resources.filter(item=>item.path?.endsWith('/Morning_Lake_Master_V1.png')),
  resourceBinding.brand_title_binding,
  resourceBinding.camera_primary_binding
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
    assert.ok(r.cameraSize>=72);
    assert.equal(r.contractId,'NH01_RESPONSIVE_FOUNDATION_V1');
    if(width===320 && height===480 && font===1) assert.ok(r.conflicts.some(x=>x.includes('145dp')));
  }
  const current=calculate(width,height,1,24,24,contract,authorityConflict);
  if(prior) { assert.ok(current.fontSp>=prior.fontSp); assert.ok(current.cameraSize>=prior.cameraSize); assert.ok(current.actualHeroWidth>=prior.actualHeroWidth); }
  prior=current;
}
assert.ok(observedPositiveSurplus,'profile set must exercise a positive surplus allocation');
assert.ok(calculate(390,844,1,24,24,contract,true).conflicts.some(x=>x.includes('SPEC_CONFLICT')&&x.includes('Markdown A.2')));
assert.ok(calculate(390,844,1,24,24,{foundation_layout_principles:{...contract.foundation_layout_principles,allocation_when_surplus_positive:{a_to_b_final:'unparseable',b_to_c_final:'unparseable'}}}).conflicts.some(x=>x.includes('表达式')));
console.log('NH01_STRUCTURED_CALCULATION=PASS 5 profiles × 3 font scales; V1.1 authorities agree; frozen asset hashes verified; 320×480 conflict reported');
