#!/usr/bin/env node
import { createServer } from "node:http";
import { createReadStream, existsSync, statSync } from "node:fs";
import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { resolve, extname, sep } from "node:path";
import { createRequire } from "node:module";

const require=createRequire(import.meta.url);
const moduleRoot=process.env.PLAYWRIGHT_MODULE_DIR;
const {chromium}=require(process.env.PLAYWRIGHT_PATH||(moduleRoot?resolve(moduleRoot,"playwright"):"playwright"));
const repoRoot=process.cwd();
const evidencePath="design/pages/home/normal_home/08_multi_screen_static_hifi/evidence/structured_preview_layout_v2";

function parseArgs(argv){
  const args={root:repoRoot,url:null,outputDir:resolve("/tmp","nh01-structured-responsive-v2"),expectedBuild:null};
  for(let i=2;i<argv.length;i+=1){
    if(argv[i]==="--root")args.root=resolve(argv[++i]);
    else if(argv[i]==="--url")args.url=argv[++i];
    else if(argv[i]==="--output-dir")args.outputDir=resolve(argv[++i]);
    else if(argv[i]==="--expected-build")args.expectedBuild=argv[++i];
    else throw new Error(`Unknown argument ${argv[i]}`);
  }
  return args;
}
function assert(value,message){if(!value)throw new Error(message);}
function startServer(root){
  const absolute=resolve(root);
  const mime={".html":"text/html; charset=utf-8",".js":"text/javascript; charset=utf-8",".css":"text/css; charset=utf-8",".json":"application/json; charset=utf-8",".png":"image/png",".svg":"image/svg+xml",".jpg":"image/jpeg",".jpeg":"image/jpeg",".md":"text/markdown; charset=utf-8"};
  const server=createServer((request,response)=>{
    let path;try{path=decodeURIComponent(new URL(request.url,"http://localhost").pathname);}catch(_){response.writeHead(400).end("Bad path");return;}
    let file=resolve(absolute,`.${path}`);
    if(file!==absolute&&!file.startsWith(absolute+sep)){response.writeHead(403).end("Forbidden");return;}
    if(existsSync(file)&&statSync(file).isDirectory())file=resolve(file,"index.html");
    if(!existsSync(file)||!statSync(file).isFile()){response.writeHead(404).end("Not found");return;}
    response.writeHead(200,{"Content-Type":mime[extname(file).toLowerCase()]||"application/octet-stream","Cache-Control":"no-store"});
    if(request.method==="HEAD")response.end();else createReadStream(file).pipe(response);
  });
  return new Promise((resolvePromise,reject)=>{server.once("error",reject);server.listen(0,"127.0.0.1",()=>resolvePromise({server,url:`http://127.0.0.1:${server.address().port}/design/manager/`}));});
}

const viewports=[
  {id:"M01-short",width:320,height:480},
  {id:"M02-standard",width:360,height:640},
  {id:"M03-medium-large",width:390,height:844},
  {id:"M04-large",width:430,height:932},
  {id:"M05-short-width-boundary",width:480,height:480},
  {id:"M05-medium-width-boundary",width:480,height:700},
  {id:"M05-long-width-boundary",width:480,height:1100},
  {id:"I01-interpolation",width:412,height:915}
];
const fontScales=[1,1.3,1.5];

async function configure(page,width,height,fontScale){
  const workspace=page.locator('[data-testid="nh01-structured-workspace"]');
  await workspace.locator('[data-control="width"]').fill(String(width));
  await workspace.locator('[data-control="height"]').fill(String(height));
  await workspace.locator('[data-control="font"]').evaluate((node,value)=>{node.value=String(value);node.dispatchEvent(new Event("input",{bubbles:true}));},fontScale);
  await workspace.locator('[data-control="zoom"]').evaluate(node=>{node.value="1";node.dispatchEvent(new Event("input",{bubbles:true}));});
  await page.waitForFunction(()=>document.querySelector('[data-testid="nh01-canvas-page"]')?.dataset.layoutMetrics,undefined,{timeout:10000});
}

async function measure(page,profile,fontScale){
  return page.evaluate(({profile,fontScale})=>{
    const board=document.querySelector('[data-artboard]');
    const origin=board.getBoundingClientRect();
    const rect=node=>{const r=node.getBoundingClientRect();return{x:+(r.left-origin.left).toFixed(3),y:+(r.top-origin.top).toFixed(3),w:+r.width.toFixed(3),h:+r.height.toFixed(3),right:+(r.right-origin.left).toFixed(3),bottom:+(r.bottom-origin.top).toFixed(3)};};
    const pick=id=>board.querySelector(`[data-layer="${id}"]`);
    const header=rect(board.querySelector('[data-layer="header"]'));
    const stats=rect(board.querySelector('[data-layer="stats"]'));
    const recent=rect(board.querySelector('[data-layer="recent-header"]'));
    const title=rect(board.querySelector('[data-layer="recent-title"]'));
    const pagerNode=board.querySelector('[data-layer="pager"]');
    const pager=rect(pagerNode);
    const hero=rect(pick('hero'));
    const previous=rect(pick('peek-left'));
    const next=rect(pick('peek-right'));
    const indicator=rect(pick('pager-indicator'));
    const footer=rect(board.querySelector('[data-layer="capture-action-group"]'));
    const cta=rect(pick('cta'));
    const camera=rect(pick('camera'));
    const cameraArtwork=rect(document.querySelector('[data-testid="nh01-camera-artwork"]'));
    const visible=(candidate)=>{const left=Math.max(candidate.x,pager.x),right=Math.min(candidate.right,pager.right);return Math.max(0,right-left);};
    const styleOverflow=node=>({scrollWidth:node.scrollWidth,clientWidth:node.clientWidth,scrollHeight:node.scrollHeight,clientHeight:node.clientHeight,clippedX:node.scrollWidth>node.clientWidth+1,clippedY:node.scrollHeight>node.clientHeight+1});
    const textSelectors=['.nh01-stat-value','.nh01-stat-label','.nh01-recent-title','.nh01-recent-all','.nh01-hero-metadata strong','.nh01-hero-metadata span','.nh01-cta'];
    const textLayout=[...board.querySelectorAll(textSelectors.join(','))].map(node=>({role:node.className||node.tagName,text:node.textContent.trim(),...styleOverflow(node)}));
    const safeBottom=profile.height-24;
    const peekLeftVisible=visible(previous),peekRightVisible=visible(next);
    const leftGap=hero.x-previous.right,rightGap=next.x-hero.right;
    const cameraArtInside={left:cameraArtwork.x-camera.x,top:cameraArtwork.y-camera.y,right:camera.right-cameraArtwork.right,bottom:camera.bottom-cameraArtwork.bottom};
    const metrics=JSON.parse(board.dataset.layoutMetrics);
    return {
      id:profile.id,viewport:{width:profile.width,height:profile.height,fontScale,topInset:24,bottomInset:24,zoom:1},previewRulesId:metrics.rulesId,
      bounds:{header,stats,recentCatchHeader:recent,recentTitle:title,pager,hero,neighborPrevious:previous,neighborNext:next,pagerIndicator:indicator,captureActionGroup:footer,cta,cameraTouchTarget:camera,cameraArtwork},
      intervals:{headerToStats:stats.y-header.bottom,statsToTitle:title.y-stats.bottom,titleToHero:hero.y-title.bottom,heroBottomToFooterTop:footer.y-hero.bottom,visibleHeroBottomToFooterTop:footer.y-Math.min(hero.bottom,pager.bottom),pagerBottomToFooterTop:footer.y-pager.bottom,heroBottomToCtaTop:cta.y-hero.bottom,ctaToCameraTouch:camera.y-cta.bottom,ctaToCameraArtwork:cameraArtwork.y-cta.bottom,cameraTouchToSafeBottom:safeBottom-camera.bottom,footerToSafeBottom:safeBottom-footer.bottom},
      pager:{peekGapLeft:leftGap,peekGapRight:rightGap,maxGeometryOverlap:Math.max(0,-leftGap,-rightGap),peekGapRule:metrics.pager.peekGap,maxGeometryOverlapLimit:metrics.pager.maxGeometryOverlap,neighborPreviousVisibleWidth:peekLeftVisible,neighborNextVisibleWidth:peekRightVisible,pagerHeight:pager.h,pagerContentHeight:metrics.pager.contentHeight,indicatorHeight:indicator.h,indicatorGapAfterHero:indicator.y-hero.bottom,localScroll:pagerNode.scrollHeight>pagerNode.clientHeight+1,scrollHeight:pagerNode.scrollHeight,clientHeight:pagerNode.clientHeight,scrollNeeded:pagerNode.dataset.scrollNeeded==="true"},
      heroRatio:hero.w/hero.h,heroTargetWidth:metrics.hero.width,heroAvailableHeight:metrics.pager.contentHeight-metrics.pager.indicatorGapAfterHero-metrics.pager.indicatorHeight,
      cameraArtInsideTouch:cameraArtInside,ctaFontSize:parseFloat(getComputedStyle(pick('cta')).fontSize),ctaLineHeight:parseFloat(getComputedStyle(pick('cta')).lineHeight),textLayout,
      images:[...board.querySelectorAll('.nh01-card-media')].map(image=>({role:image.dataset.mediaRole,status:image.dataset.mediaStatus,loaded:image.complete&&image.naturalWidth>0,width:image.naturalWidth,height:image.naturalHeight})),
      layoutConflicts:[...document.querySelector('[data-inspector]').querySelectorAll('dd')].find(node=>node.previousElementSibling?.textContent==="布局冲突")?.textContent||"",
      responsiveWidth:{document:document.documentElement.scrollWidth,body:document.body.scrollWidth,viewport:window.innerWidth}
    };
  },{profile,fontScale});
}

function validate(result){
  const b=result.bounds,p=result.pager,i=result.intervals;
  const failures=[];
  if(Math.abs(result.heroRatio-247/293)>.001)failures.push(`Hero ratio ${result.heroRatio}`);
  if(b.hero.w>result.heroTargetWidth+.5)failures.push("Hero exceeded target width");
  if(Math.abs(p.peekGapLeft-p.peekGapRight)>.5)failures.push("left/right Peek gap is asymmetric");
  if(Math.abs(p.neighborPreviousVisibleWidth-p.neighborNextVisibleWidth)>.5)failures.push("left/right Peek reveal is asymmetric");
  if(p.neighborPreviousVisibleWidth<1||p.neighborNextVisibleWidth<1)failures.push("both Neighbor cards must have a measurable visible Peek segment");
  if(p.maxGeometryOverlap>p.maxGeometryOverlapLimit+.01)failures.push("Peek geometry exceeds the overlap cap");
  if(p.peekGapLeft<7.9||p.peekGapLeft>12.1)failures.push("actual Peek gap is outside the 8–12dp preview clamp");
  if(p.pagerHeight<p.indicatorHeight)failures.push("Pager indicator is outside the Pager height budget");
  if(i.headerToStats<0||i.statsToTitle<0||i.titleToHero<0||i.pagerBottomToFooterTop<0)failures.push("adjacent layout regions overlap");
  if(i.ctaToCameraTouch<5.9)failures.push("CTA text collides with camera touch target");
  if(i.cameraTouchToSafeBottom<1.9)failures.push("camera target crosses the bottom safe area");
  if(i.footerToSafeBottom<-.51)failures.push("CaptureActionGroup exceeds the bottom safe area");
  if(b.cameraTouchTarget.w<72||b.cameraTouchTarget.h<72)failures.push("camera target is below 72dp");
  if(Object.values(result.cameraArtInsideTouch).some(value=>value<-.5))failures.push("camera artwork is not contained by its touch target");
  if(result.textLayout.some(item=>item.clippedX||item.clippedY))failures.push("text box clips content");
  if(result.responsiveWidth.document>result.responsiveWidth.viewport||result.responsiveWidth.body>result.responsiveWidth.viewport)failures.push("Design Manager page has horizontal viewport overflow");
  if(p.localScroll!==p.scrollNeeded)failures.push(`Pager scroll contract mismatch: data=${p.scrollNeeded}, actual=${p.localScroll}`);
  return failures;
}

async function main(){
  const args=parseArgs(process.argv);
  await mkdir(args.outputDir,{recursive:true});
  const rules=JSON.parse(await readFile(resolve(args.root,"design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_STRUCTURED_PREVIEW_LAYOUT_RULES_V2.json"),"utf8"));
  const media=JSON.parse(await readFile(resolve(args.root,"design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_STRUCTURED_PREVIEW_MEDIA_V1.json"),"utf8"));
  const mediaHashes=media.assets.map(asset=>({id:asset.id,sha256:createHash("sha256").update(require("node:fs").readFileSync(resolve(args.root,asset.path))).digest("hex")}));
  assert(rules.status==="PREVIEW_ONLY"&&media.assets.length===3,"preview rules and 3-item illustrative media manifest are required");
  assert(mediaHashes.every((item,index)=>item.sha256===media.assets[index].sha256),"illustrative media SHA-256 does not match its manifest");
  const {server,url}=args.url?{server:null,url:args.url.endsWith("/")?args.url:`${args.url}/`}:await startServer(args.root);
  const errors=[],badResponses=[],illustrativeMediaFailures=[];
  const browser=await chromium.launch({headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1});
    page.on("pageerror",error=>errors.push(error.message));
    page.on("console",message=>{if(message.type()==="error"&&!message.text().includes("preview_illustrative"))errors.push(message.text());});
    page.on("response",response=>{if(response.status()>=400){const item=`${response.status()} ${response.url()}`;if(response.url().includes("preview_illustrative"))illustrativeMediaFailures.push(item);else badResponses.push(item);}});
    await page.goto(`${url}#page/home_normal_v1/hifi/multi_screen_static_hifi`,{waitUntil:"domcontentloaded",timeout:60000});
    await page.waitForFunction(()=>["READY","ERROR"].includes(document.querySelector("#hifiViewCanvas")?.dataset.nh01LoadState),undefined,{timeout:60000});
    assert(await page.locator("#hifiViewCanvas").getAttribute("data-nh01-load-state")==="READY","existing NH01 Design Manager route did not reach READY");
    const workspace=page.locator('[data-testid="nh01-structured-workspace"]');
    assert(await workspace.count()===1,"existing route must render one structured preview");
    assert(await workspace.locator('[data-testid="nh01-canvas-pager-indicator"]').count()===1,"the preview pager indicator must have an inspectable component boundary");
    const mediaSettled=await page.waitForFunction(()=>[...document.querySelectorAll('[data-media-role]')].length===3&&[...document.querySelectorAll('[data-media-role]')].every(image=>image.complete),undefined,{timeout:20000}).then(()=>true).catch(()=>false);
    await workspace.locator(".nh01-structured-controls details > summary").click();
    await workspace.locator('[data-control="top"]').fill("24");
    await workspace.locator('[data-control="bottom"]').fill("24");
    const buildSha=await page.locator('meta[name="design-manager-build"]').getAttribute("content");
    if(args.expectedBuild)assert(buildSha===args.expectedBuild,`Build SHA mismatch ${buildSha} != ${args.expectedBuild}`);
    const results=[];
    for(const profile of viewports){
      for(const fontScale of fontScales){
        await page.setViewportSize({width:profile.width,height:profile.height});
        await configure(page,profile.width,profile.height,fontScale);
        const result=await measure(page,profile,fontScale);
        const failures=validate(result);
        result.status=failures.length?"FAIL":"PASS";
        result.failures=failures;
        result.screenshot=`${profile.id}-font-${String(fontScale).replace(".","_")}x.png`;
        const board=workspace.locator('[data-artboard]');
        await board.evaluate(node=>node.querySelectorAll(".nh01-layer-selected").forEach(layer=>layer.classList.remove("nh01-layer-selected")));
        await board.screenshot({path:resolve(args.outputDir,result.screenshot),animations:"disabled"});
        const png=await readFile(resolve(args.outputDir,result.screenshot));
        result.screenshotDimensions={width:png.readUInt32BE(16),height:png.readUInt32BE(20),bytes:png.length,sha256:createHash("sha256").update(png).digest("hex")};
        // Element screenshots round fractional artboard edges outward. A
        // one-pixel height difference is the rasterized CSS clip, not a
        // different test viewport; larger differences remain a failure.
        if(result.screenshotDimensions.width!==profile.width||result.screenshotDimensions.height<profile.height||result.screenshotDimensions.height>profile.height+1)failures.push(`screenshot dimensions ${result.screenshotDimensions.width}×${result.screenshotDimensions.height} do not match the ${profile.width}×${profile.height} artboard viewport within 1px edge rounding`);
        result.status=failures.length?"FAIL":"PASS";
        results.push(result);
        if(fontScale===1){
          const legacy={"M01-short":"m01","M02-standard":"m02","M03-medium-large":"m03","M04-large":"m04"}[profile.id];
          if(legacy){
            const frozenReference={
              m01:"NH01_M01_320x480dp_STATIC_HIFI_V1.png",
              m02:"NH01_M02_360x640dp_STATIC_HIFI_V1.png",
              m03:"NH01_M03_390x844dp_STATIC_HIFI_V1.png",
              m04:"NH01_M04_430x932dp_STATIC_HIFI_V1.png"
            }[legacy];
            const frozenReferencePath=`design/pages/home/normal_home/08_multi_screen_static_hifi/${frozenReference}`;
            const frozenReferenceSha=createHash("sha256").update(require("node:fs").readFileSync(resolve(args.root,frozenReferencePath))).digest("hex");
            await copyFile(resolve(args.outputDir,result.screenshot),resolve(args.outputDir,`nh01-${legacy}-render-${profile.width}x${profile.height}.png`));
            await writeFile(resolve(args.outputDir,`nh01-${legacy}-dom-bounds.json`),JSON.stringify({
              build_sha:buildSha,
              preview_rules_id:rules.rules_id,
              viewport:{width:profile.width,height:profile.height},
              frozen_reference:{path:frozenReferencePath,sha256:frozenReferenceSha},
              dom:result.bounds,
              intervals:result.intervals,
              pager:result.pager,
              hero_ratio:result.heroRatio,
              footer_height:result.bounds.captureActionGroup.h,
              status:result.status,
              failures:result.failures
            },null,2)+"\n");
          }
        }
      }
    }
    await writeFile(resolve(args.outputDir,"nh01-m05-width-only-bounds.json"),JSON.stringify({build_sha:buildSha,scope:"PREVIEW_ONLY; M05 Android height remains unspecified",viewport_cases:results.filter(result=>result.viewport.width===480).map(result=>({viewport:result.viewport,status:result.status,bounds:{hero:result.bounds.hero,pager:result.bounds.pager,footer:result.bounds.captureActionGroup},pager:result.pager}))},null,2)+"\n");
    // Prove missing media cannot prevent geometry from rendering or change its bounds.
    const probePage=await browser.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
    await probePage.route("**/assets/preview_illustrative/**",route=>route.abort("failed"));
    await probePage.goto(`${url}#page/home_normal_v1/hifi/multi_screen_static_hifi`,{waitUntil:"domcontentloaded",timeout:60000});
    await probePage.waitForFunction(()=>document.querySelector("#hifiViewCanvas")?.dataset.nh01LoadState==="READY",undefined,{timeout:60000});
    const mediaWorkspace=probePage.locator('[data-testid="nh01-structured-workspace"]');
    await mediaWorkspace.locator(".nh01-structured-controls details > summary").click();
    await mediaWorkspace.locator('[data-control="top"]').fill("24");
    await mediaWorkspace.locator('[data-control="bottom"]').fill("24");
    await configure(probePage,390,844,1);
    await probePage.waitForTimeout(300);
    const geometryAfter=await measure(probePage,{id:"media-failure-probe",width:390,height:844},1);
    const geometryBefore=results.find(item=>item.viewport.width===390&&item.viewport.height===844&&item.viewport.fontScale===1);
    const mediaGeometryStable=JSON.stringify(geometryBefore.bounds)===JSON.stringify(geometryAfter.bounds)&&geometryBefore.heroRatio===geometryAfter.heroRatio;
    const failedMediaImages=geometryAfter.images.filter(item=>!item.loaded).length;
    const mediaProbeState=await probePage.locator("#hifiViewCanvas").getAttribute("data-nh01-load-state");
    assert(mediaProbeState==="READY","the workspace stays mounted when preview media requests fail");
    assert(mediaGeometryStable,"illustrative image failures changed layout geometry");
    await probePage.close();
    const summary={
      task_id:"YUJIAN_NH01_STRUCTURED_HIFI_RESPONSIVE_LAYOUT_REPAIR_V2",
      route:"#page/home_normal_v1/hifi/multi_screen_static_hifi",
      source_sha:buildSha,
      browser:{name:"Chromium",version:browser.version(),device_scale_factor:1},
      preview_rules_id:rules.rules_id,
      status:results.every(result=>result.status==="PASS")&&mediaGeometryStable&&errors.length===0&&badResponses.length===0?"PASS":"FAIL",
      matrix:{viewport_count:viewports.length,font_scales:fontScales,case_count:results.length,pass_count:results.filter(result=>result.status==="PASS").length,fail_count:results.filter(result=>result.status==="FAIL").length},
      generated_illustrative_media:mediaHashes,
      generated_illustrative_images_settled:mediaSettled,
      generated_illustrative_image_http_failures:illustrativeMediaFailures,
      image_failure_probe:{geometry_stable:mediaGeometryStable,failed_images:failedMediaImages,root_state:mediaProbeState},
      results,
      errors,bad_responses:badResponses
    };
    await writeFile(resolve(args.outputDir,"NH01_STRUCTURED_RESPONSIVE_LAYOUT_V2_REPORT.json"),JSON.stringify(summary,null,2)+"\n");
    const lines=["# NH01 Structured Responsive Layout V2 — Chromium Run",`Status: **${summary.status}**`,`Source SHA: \`${buildSha}\``,`Run ID: \`${process.env.GITHUB_RUN_ID||"local"}\``,`Preview rules: \`${rules.rules_id}\``,`Browser: Chromium ${browser.version()} (device scale 1)`,"","| Case | Viewport | Font | Hero (W×H) | Ratio | Peek gap / visible width | Pager scroll | Footer | Result |","| --- | ---: | ---: | ---: | ---: | ---: | :---: | ---: | :---: |"];
    for(const item of results)lines.push(`| ${item.id} | ${item.viewport.width}×${item.viewport.height} | ${item.viewport.fontScale}× | ${item.bounds.hero.w.toFixed(1)}×${item.bounds.hero.h.toFixed(1)} | ${item.heroRatio.toFixed(4)} | ${item.pager.peekGapLeft.toFixed(1)} / ${item.pager.neighborPreviousVisibleWidth.toFixed(1)}dp | ${item.pager.localScroll?"yes":"no"} | ${item.bounds.captureActionGroup.h.toFixed(1)}dp | ${item.status} |`);
    lines.push("",`Illustrative image failure probe: ${mediaGeometryStable?"PASS — layout bounds unchanged":"FAIL"}; ${failedMediaImages}/3 illustrative images failed as injected.`,`Workspace errors: ${errors.length}; HTTP errors: ${badResponses.length}.`,"","Screenshots are Chromium renderings of the Design Manager artboard. Frozen PNG comparisons are not used as a pass/fail threshold.");
    await writeFile(resolve(args.outputDir,"NH01_STRUCTURED_RESPONSIVE_LAYOUT_V2_REPORT.md"),lines.join("\n")+"\n");
    if(summary.status!=="PASS"){
      console.error(JSON.stringify({NH01_STRUCTURED_RESPONSIVE_LAYOUT_V2_FAILURE:{source_sha:buildSha,browser:summary.browser,matrix:summary.matrix,failed_cases:results.filter(result=>result.status!=="PASS").map(result=>({id:result.id,viewport:result.viewport,failures:result.failures,bounds:result.bounds,intervals:result.intervals,pager:result.pager,heroRatio:result.heroRatio,heroTargetWidth:result.heroTargetWidth,layoutConflicts:result.layoutConflicts})),image_failure_probe:summary.image_failure_probe,errors,bad_responses:badResponses}}));
      throw new Error(`Responsive matrix failed; see ${resolve(args.outputDir,"NH01_STRUCTURED_RESPONSIVE_LAYOUT_V2_REPORT.json")}`);
    }
    console.log(`NH01_STRUCTURED_RESPONSIVE_LAYOUT_V2=${summary.status} source=${buildSha} cases=${summary.matrix.pass_count}/${summary.matrix.case_count} chromium=${browser.version()} output=${args.outputDir}`);
  }finally{await browser.close();if(server)server.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
