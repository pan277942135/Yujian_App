// Browser regression: synthetic PNG stays in memory; private originals never enter CI.
import {createServer} from 'node:http';
import {readFile,stat} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
const require=createRequire(import.meta.url);
const {chromium}=require(resolve(process.env.PLAYWRIGHT_MODULE_DIR,'playwright'));
const root=process.cwd();
const server=createServer(async(req,res)=>{try{let p=resolve(root,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));if(!p.startsWith(root+'/'))throw Error();if((await stat(p)).isDirectory())p+='/index.html';res.setHeader('Content-Type',({'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json'})[extname(p)]||'application/octet-stream');res.end(await readFile(p));}catch{res.writeHead(404).end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
let browser;
try{
 browser=await chromium.launch({headless:true}); const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const url=`http://127.0.0.1:${server.address().port}`;
 const boundaryReports=[];
 const requests=[];page.on('request',r=>requests.push({method:r.method(),url:r.url()}));
 // Reproduce the admitted baseline CSS in Chromium before evaluating the repair.
 await page.route('**/private_visual_references.css',async route=>{
  const response=await route.fetch();const css=await response.text();
  await route.fulfill({response,body:css.split('/* Intrinsic control sizing:')[0]});
 });
 await page.setViewportSize({width:320,height:900});
 await page.goto(url+'/design/manager/private_visual_references.html');
 await page.locator('#identity').filter({hasText:'1536×1024'}).waitFor();
 const baseline=await page.evaluate(()=>({viewport:innerWidth,documentWidth:document.documentElement.scrollWidth,elements:[...document.querySelectorAll('body *')].map(e=>({tag:e.tagName,id:e.id,className:e.className,left:e.getBoundingClientRect().left,right:e.getBoundingClientRect().right,width:e.getBoundingClientRect().width,minWidth:getComputedStyle(e).minWidth})).filter(e=>e.right>innerWidth+.5||e.left<-.5)}));
 console.log('BASELINE_OVERFLOW_BOUNDS='+JSON.stringify(baseline));
 await page.unroute('**/private_visual_references.css');
 await page.reload();
 await page.locator('#identity').filter({hasText:'1536×1024'}).waitFor();
 const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1cAAAAASUVORK5CYII=','base64');
 await page.locator('#reference').setInputFiles({name:'wrong.png',mimeType:'image/png',buffer:png});
 await page.waitForFunction(()=>document.getElementById('check').dataset.state==='error');
 if(await page.locator('#original').isVisible())throw Error('Wrong SHA displayed');
 await page.locator('[data-tab="swos-history"]').click();
 await page.locator('#version').filter({hasText:'14_FRAME_V0_3_HISTORICAL_VISUAL_REFERENCE'}).waitFor();
 await page.locator('[data-tab="current"]').click();
 if(await page.locator('#frames tbody tr').count()!==15)throw Error('15-frame table missing');
 await page.locator('#frames').filter({hasText:'R0.5'}).waitFor();
 await page.locator('[data-tab="web-three-stage"]').click();
 // Optional actual private inputs: local-only, never copied into artifacts or screenshots.
 if(process.env.PRIVATE_WEB_REFERENCE && process.env.PRIVATE_SWOS_REFERENCE){
  await page.locator('#reference').setInputFiles(process.env.PRIVATE_WEB_REFERENCE);
  await page.waitForFunction(()=>document.getElementById('check').dataset.state==='verified');
  for(let i=0;i<5;i++){await page.locator(`[data-region="${i}"]`).click();if(!await page.locator('#region').isVisible())throw Error('Mapping region invisible');}
  await page.locator('#implementation').setInputFiles(process.env.PRIVATE_WEB_REFERENCE);
  await page.locator('#actual').waitFor({state:'visible'});
  await page.locator('#comparison-status').filter({hasText:'NOT_VERIFIED'}).waitFor();
  await page.locator('[data-tab="swos-history"]').click();
  await page.locator('#reference').setInputFiles(process.env.PRIVATE_SWOS_REFERENCE);
  await page.waitForFunction(()=>document.getElementById('check').dataset.state==='verified');
  await page.locator('#clear').click();if(await page.locator('#original').isVisible())throw Error('Clear failed');
 }
 for(const width of [320,390,768,1440]){
  await page.setViewportSize({width,height:900});
  const bounds=await page.evaluate(()=>({viewport:innerWidth,documentWidth:document.documentElement.scrollWidth,controls:[...document.querySelectorAll('.controls > label,.controls input')].map(e=>({tag:e.tagName,id:e.id,left:e.getBoundingClientRect().left,right:e.getBoundingClientRect().right,width:e.getBoundingClientRect().width}))}));
  boundaryReports.push(bounds);console.log('FIXED_CONTROL_BOUNDS='+JSON.stringify(bounds));
  if(bounds.documentWidth>width)throw Error('Body overflow '+width);
 }
 for(const id of ['web_hifi_contract_v2','swos_static_visual_v1_1']){
  await page.goto(url+'/design/manager/#page/recognition_flow_v1_2/hifi/'+id);
  await page.locator('iframe[title="原始高保真私有查看与合同对照"]').waitFor();
 }
 if(errors.length)throw Error(errors.join('\n'));
 if(requests.some(r=>r.method!=='GET'||(!r.url.startsWith(url)&&!r.url.startsWith('blob:'))))throw Error('Unexpected network or upload');
 console.log(JSON.stringify({gate:'PRIVATE_VISUAL_BROWSER',result:'PASS',source_sha:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),private_originals_exercised:!!process.env.PRIVATE_WEB_REFERENCE,checks:['wrong_sha_rejected','version_switch','15_frames_R05','responsive_320_390_768_1440','both_manager_routes','no_upload_or_external_request'],baseline,boundaryReports,screenshots:'NONE_PRIVATE_BYTES_EXCLUDED'}));
}finally{await browser?.close();server.close();}
