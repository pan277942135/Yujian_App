#!/usr/bin/env node

import { createServer } from "node:http";
import { createReadStream, existsSync, statSync } from "node:fs";
import { readFile, writeFile, rename } from "node:fs/promises";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { resolve, extname, sep } from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const moduleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (moduleRoot ? resolve(moduleRoot, "playwright") : "playwright");
const { chromium } = require(playwrightPath);
let activeBrowser = null;
let activeServer = null;

function parseArgs(argv) {
  const result = { root: process.cwd(), url: null, outputDir: "/tmp/design-manager-browser-evidence", expectedBuild: null, swosPhoto: null };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--root") result.root = resolve(argv[++i]);
    else if (arg === "--url") result.url = argv[++i];
    else if (arg === "--output-dir") result.outputDir = resolve(argv[++i]);
    else if (arg === "--expected-build") result.expectedBuild = argv[++i];
    else if (arg === "--swos-photo") result.swosPhoto = resolve(argv[++i]);
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return result;
}

function fail(message) { throw new Error(message); }
function assert(condition, message) { if (!condition) fail(message); }

function startStaticServer(root) {
  const absoluteRoot = resolve(root);
  const mime = {
    ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8",
    ".png": "image/png", ".svg": "image/svg+xml", ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg", ".webp": "image/webp", ".md": "text/markdown; charset=utf-8"
  };
  const server = createServer((request, response) => {
    let pathname;
    try { pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname); }
    catch (_) { response.writeHead(400).end("Bad path"); return; }
    let file = resolve(absoluteRoot, `.${pathname}`);
    if (file !== absoluteRoot && !file.startsWith(absoluteRoot + sep)) {
      response.writeHead(403).end("Forbidden"); return;
    }
    if (existsSync(file) && statSync(file).isDirectory()) file = resolve(file, "index.html");
    if (!existsSync(file) || !statSync(file).isFile()) {
      response.writeHead(404).end("Not found"); return;
    }
    response.writeHead(200, { "Content-Type": mime[extname(file).toLowerCase()] || "application/octet-stream", "Cache-Control": "no-store" });
    if (request.method === "HEAD") response.end();
    else createReadStream(file).pipe(response);
  });
  return new Promise((resolvePromise, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      resolvePromise({ server, url: `http://127.0.0.1:${address.port}/design/manager/` });
    });
  });
}

async function waitForManager(page) {
  await page.locator("#pageList .module-item").first().waitFor({ state: "visible", timeout: 30000 });
}

async function waitForManagerShell(page) {
  // Historical routes can intentionally hide the current title while keeping
  // the shared navigation shell mounted. Use the shell as the readiness signal
  // when opening arbitrary registered routes.
  await page.locator("#pageList .module-item").first().waitFor({ state: "visible", timeout: 30000 });
}

async function waitForPrivateReference(page) {
  const selector = 'iframe[title="原始高保真私有查看与合同对照"]';
  if (await page.locator(selector).count()) {
    await page.frameLocator(selector).locator("#identity").filter({hasText: /SHA-256|正式权威/}).waitFor({state: "visible", timeout: 30000});
  }
}

async function waitForHifiView(page) {
  await page.locator("#hifiViewTitle").waitFor({ state: "visible", timeout: 30000 });
}

async function waitForLoadedImage(page, selector, label) {
  await page.locator(selector).first().waitFor({ state: "attached", timeout: 30000 });
  try {
    await page.waitForFunction((imageSelector) => {
      const image = document.querySelector(imageSelector);
      return !!image && image.complete && image.naturalWidth > 0;
    }, selector, { timeout: 30000 });
  } catch (error) {
    throw new Error(`${label} original image did not finish loading: ${error.message}`);
  }
  return page.locator(selector).first().evaluate(node => ({
    width: node.naturalWidth,
    height: node.naturalHeight,
    complete: node.complete,
    src: node.currentSrc
  }));
}

async function main() {
  const args = parseArgs(process.argv);
  const { mkdir } = await import("node:fs/promises");
  await mkdir(args.outputDir, { recursive: true });
  let server = null;
  let baseUrl = args.url;
  if (!baseUrl) {
    const started = await startStaticServer(args.root);
    server = started.server;
    activeServer = server;
    baseUrl = started.url;
  } else if (!baseUrl.endsWith("/design/manager/")) {
    baseUrl = new URL("design/manager/", baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`).href;
  }

  const errors = [];
  const failedRequests = [];
  const badResponses = [];
  const requests = [];
  const browser = await chromium.launch({ headless: true });
  activeBrowser = browser;
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  page.on("pageerror", error => errors.push(error.message));
  page.on("requestfailed", request => failedRequests.push(`${request.url()} · ${request.failure()?.errorText || "request failed"}`));
  page.on("request", request => requests.push({ url: request.url(), method: request.method() }));
  page.on("response", response => {
    if (response.status() >= 400 && response.url().startsWith(new URL(baseUrl).origin)) {
      badResponses.push(`${response.status()} ${response.url()}`);
    }
  });

  const overviewHash = "#page/recognition_flow_v1_2/hifi/main_journey_overview";
  const p01Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01";
  const p02Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p02";
  const p03Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p03";
  const resourcesHash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources";
  const fullFlowModelsHash = "#page/recognition_flow_v1_2/hifi/recognition_full_flow_models";
  const nh01MultiScreenHash = "#page/home_normal_v1/hifi/multi_screen_static_hifi";
  const open = async hash => {
    await page.goto(baseUrl + hash, { waitUntil: "domcontentloaded", timeout: 45000 });
    await waitForManagerShell(page);
    await page.waitForFunction(expected =>
      location.hash === expected && Boolean(document.querySelector("#hifiViewTitle")?.textContent?.trim()),
      hash,
      { timeout: 30000 }
    );
    await waitForPrivateReference(page);
  };

  await open(nh01MultiScreenHash);
  await waitForManager(page);
  await waitForHifiView(page);
  await page.waitForFunction(()=>["READY","ERROR"].includes(document.querySelector("#hifiViewCanvas")?.dataset.nh01LoadState),null,{timeout:30000});
  const nh01LoadSnapshot=await page.evaluate(()=>({state:document.querySelector("#hifiViewCanvas")?.dataset.nh01LoadState,text:document.querySelector("#hifiViewCanvas")?.innerText?.slice(0,12000),resources:[...document.querySelectorAll("#hifiViewCanvas [data-resource-id]")].map(node=>({id:node.dataset.resourceId,status:node.cells?.[1]?.innerText,http:node.cells?.[2]?.innerText,detail:node.innerText})),scripts:[...document.scripts].filter(node=>node.dataset.resourceId?.startsWith("nh01-")).map(node=>({id:node.dataset.resourceId,src:node.src,loaded:node.dataset.loaded||null})),styles:[...document.querySelectorAll('link[data-resource-id="nh01-workspace-css"]')].map(node=>({href:node.href,applied:!!node.sheet}))}));
  if(nh01LoadSnapshot.state!=="READY")fail(`NH01 structured resource state did not reach READY: ${JSON.stringify({...nh01LoadSnapshot,errors,failedRequests,badResponses})}`);
  await page.locator('[data-testid="nh01-multiscreen-workspace"]').waitFor({ state: "visible", timeout: 30000 });
  const buildMarker = await page.locator('meta[name="design-manager-build"]').getAttribute("content");
  if (args.expectedBuild) assert(buildMarker === args.expectedBuild, `NH01 build marker mismatch: ${buildMarker} != ${args.expectedBuild}`);
  assert((await page.locator("#hifiViewTitle").innerText()).includes("结构化高保真"), "NH01 structured hi-fi workspace route did not resolve");
  assert((await page.locator("#hifiViewStatus").innerText()).includes("静态高保已冻结"), "NH01 static hi-fi frozen status is missing");
  assert(await page.locator(".nh01-static-asset").count() === 5, "NH01 route must show exactly five recovered static assets");
  assert(await page.locator('[data-testid="nh01-structured-workspace"]').count() === 1, "NH01 route must have exactly one integrated structured workspace");
  assert((await page.locator(".nh01-structured-heading").innerText()).includes("V1.1"), "NH01 workspace must bind the current responsive foundation version");
  assert((await page.locator(".nh01-structured-heading").innerText()).includes("多屏静态与响应式规范"), "NH01 integrated workspace title is missing");
  const nh01ModuleUrls = await page.evaluate(() => {
    const js=document.querySelector('script[data-resource-id="nh01-workspace-js"]');
    const css=document.querySelector('link[data-resource-id="nh01-workspace-css"]');
    return {jsBuild:new URL(js.src).searchParams.get("v"),cssBuild:new URL(css.href).searchParams.get("v"),cssApplied:!!css.sheet};
  });
  assert(nh01ModuleUrls.jsBuild===buildMarker&&nh01ModuleUrls.cssBuild===buildMarker&&nh01ModuleUrls.cssApplied,`NH01 JS/CSS must load with the exact build marker: ${JSON.stringify(nh01ModuleUrls)}`);
  const workspace = page.locator('[data-testid="nh01-structured-workspace"]');
  await workspace.locator(".nh01-development-basis > summary").click();
  assert(await workspace.locator(".nh01-development-basis").evaluate(node=>node.open),"NH01 resource audit must be reachable and opened before its visible rows are verified");
  const resourceRows=workspace.locator(".nh01-resource-table tbody tr[data-resource-id]");
  assert(await resourceRows.count()===21,`NH01 needs nine contract/module resources and twelve verified frozen/used images; got ${await resourceRows.count()}`);
  const resourceEvidence=await resourceRows.evaluateAll(rows=>rows.map(row=>{
    const cells=[...row.cells];
    const text=cells.map(cell=>cell.innerText).join("\n");
    const sha=text.match(/SHA-256\s+([0-9a-f]{64})/i)?.[1]||"";
    return {id:row.dataset.resourceId,status:cells[1]?.innerText,http:cells[2]?.innerText,parse:cells[3]?.innerText||"",sha,url:row.querySelector("code")?.innerText||""};
  }));
  assert(resourceEvidence.every(item=>item.status==="READY"&&item.http==="200"&&/^[0-9a-f]{64}$/i.test(item.sha)),`NH01 resource request evidence is incomplete: ${JSON.stringify(resourceEvidence)}`);
  const layerTree = workspace.locator("[data-layer-tree]");
  const nestedTreeEvidence=await workspace.evaluate(()=>{
    const board=document.querySelector('[data-artboard]');
    const tree=[...document.querySelectorAll('[data-tree-node]')].map(node=>({id:node.dataset.treeNode,parent:node.parentElement.closest('[data-tree-node]')?.dataset.treeNode??null}));
    const dom=[board,...board.querySelectorAll('[data-layer]')].map(node=>({id:node.dataset.layer,parent:node===board?null:node.parentElement.closest('[data-layer]')?.dataset.layer??null,rect:(()=>{const r=node.getBoundingClientRect(),b=board.getBoundingClientRect();return{x:+(r.left-b.left).toFixed(3),y:+(r.top-b.top).toFixed(3),w:+r.width.toFixed(3),h:+r.height.toFixed(3)}})(),tag:node.tagName,assetStatus:node.dataset.assetStatus||'ROOT'}));
    const domById=new Map(dom.map(item=>[item.id,item]));
    return {tree,dom,unmapped:tree.filter(node=>!domById.has(node.id)),parentMismatches:tree.filter(node=>node.id!=='page'&&domById.get(node.id)?.parent!==node.parent),htmlParentIds:tree.map(node=>node.id)};
  });
  assert(nestedTreeEvidence.unmapped.length===0,`every registered tree node must have a real canvas element: ${JSON.stringify(nestedTreeEvidence.unmapped)}`);
  assert(nestedTreeEvidence.parentMismatches.length===0,`tree hierarchy must match nearest real DOM parents: ${JSON.stringify(nestedTreeEvidence.parentMismatches)}`);
  assert(nestedTreeEvidence.tree.some(node=>node.id==='header')&&nestedTreeEvidence.tree.some(node=>node.id==='background'),"the tree must expose the true nested page structure");
  assert(!nestedTreeEvidence.tree.some(node=>['top','footer'].includes(node.id)),"logical bands and empty overlays must not appear as fake DOM layers");
  assert(await layerTree.locator('[data-select="avatar"]').getAttribute('data-source-asset-id')==='avatar_default_nh07',"the legacy Figma avatar binding must remain explicitly recorded beside the approved V2 asset");
  assert((await layerTree.locator('[data-tree-node="avatar"]').innerText()).includes('APPROVED_NH01_HOME_V2_FROZEN'),"avatar layer must disclose its current approved NH01 V2 source");
  await layerTree.locator('[data-toggle="stats"]').click();
  assert(await layerTree.locator('[data-children-of="stats"]').isHidden(),"parent layers must collapse their child rows");
  await layerTree.locator('[data-toggle="stats"]').click();
  await layerTree.locator('[data-select="recent-header"]').click();
  assert(await workspace.locator('[data-testid="nh01-canvas-recent-header"].nh01-layer-selected').count() === 1, "layer tree selection must select the matching canvas layer");
  assert((await workspace.locator('[data-testid="nh01-inspector-layer"]').getAttribute('data-layer-id'))==='recent-header',"inspector must display the selected registered layer id");
  await workspace.locator('[data-testid="nh01-canvas-hero"]').click();
  assert(await layerTree.locator('[data-select="hero"]').getAttribute("aria-pressed") === "true", "canvas selection must synchronize the layer tree");
  assert((await workspace.locator("[data-inspector]").innerText()).includes("Hero"), "inspector must synchronize with the selected canvas layer");
  await layerTree.locator('[data-select="peek-left"]').click();
  assert(await workspace.locator('[data-testid="nh01-canvas-peek-left"].nh01-layer-selected').count()===1,"Peek tree selection must highlight its canvas layer");
  assert((await workspace.locator("[data-inspector]").innerText()).includes("BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS"),"Peek inspector must preserve the missing-approved-photo blocker");
  assert((await workspace.locator('[data-testid="nh01-canvas-peek-left"]').innerText()).includes("BLOCKED"),"Peek must render a truthful placeholder instead of a fabricated photo");
  assert(await workspace.locator('[data-testid="nh01-canvas-hero"] [data-testid="nh01-hero-media-blocked"]').isVisible(),"Hero must retain the explicit blocked-media state");
  assert((await workspace.locator('[data-testid="nh01-canvas-avatar"] img').getAttribute('src')).includes('normal_home_default_avatar_v2.png'),"the approved Home-specific fallback avatar must be used instead of the historical shared binding");
  const heroMetadataLayout=await workspace.locator('.nh01-hero-metadata').evaluate(node=>({position:getComputedStyle(node).position,bottom:getComputedStyle(node).bottom,box:node.getBoundingClientRect().toJSON(),hero:node.parentElement.getBoundingClientRect().toJSON()}));
  assert(heroMetadataLayout.position==="absolute"&&heroMetadataLayout.bottom==="0px",`Hero metadata must anchor to its true card bottom: ${JSON.stringify(heroMetadataLayout)}`);
  const backgroundObjectPosition=await workspace.locator('[data-testid="nh01-canvas-background"]').evaluate(node=>getComputedStyle(node).objectPosition);
  assert(backgroundObjectPosition==="50% 0%",`NH01 Morning Lake background must follow registered TopCenter/Crop framing: ${backgroundObjectPosition}`);
  const presetChecks=[["0",320,480],["1",360,640],["2",390,844],["3",430,932],["4",480,null]];
  for(const [index,width,height] of presetChecks){
    const retainedHeight=await workspace.locator('[data-control="height"]').inputValue();
    await workspace.locator('[data-control="profile"]').selectOption(index);
    assert(await workspace.locator('[data-control="width"]').inputValue()===String(width),`preset ${index} failed to set ${width} dp width`);
    if(height!==null)assert(await workspace.locator('[data-control="height"]').inputValue()===String(height),`preset ${index} failed to set ${width}×${height} dp`);
    else assert(await workspace.locator('[data-control="height"]').inputValue()===retainedHeight,`480dp boundary must leave the freely selected height ${retainedHeight} unchanged`);
    const text=await workspace.locator("[data-inspector]").innerText();
    assert(text.includes(`${width} × ${height===null?retainedHeight:height} dp`),`preset ${index} did not recalculate inspector geometry`);
    if(index==="4")assert(text.includes("M05 仅冻结 480dp 宽度"),"M05 example height must remain explicitly unfrozen");
  }
  await workspace.locator('[data-control="width"]').fill("390");
  await workspace.locator('[data-control="height"]').fill("844");
  for(const fontScale of ["1","1.3","1.5"]){
    await workspace.locator('[data-control="font"]').evaluate((node,value)=>{node.value=value;node.dispatchEvent(new Event("input",{bubbles:true}));},fontScale);
    assert((await workspace.locator("[data-inspector]").innerText()).includes(`fontScale ${fontScale}`),`fontScale ${fontScale} did not recalculate inspector values`);
  }
  await workspace.locator('[data-control="zoom"]').evaluate(node=>{node.value="0.65";node.dispatchEvent(new Event("input",{bubbles:true}));});
  await workspace.locator(".nh01-structured-controls details > summary").click();
  await workspace.locator('[data-control="top"]').fill("30");
  await workspace.locator('[data-control="bottom"]').fill("40");
  await workspace.locator('[data-control="bottom"]').fill("24");
  await workspace.locator('[data-control="top"]').fill("24");
  await workspace.locator('[data-control="font"]').evaluate((node,value)=>{node.value=value;node.dispatchEvent(new Event("input",{bubbles:true}));},"1.3");
  assert((await workspace.locator("[data-inspector]").innerText()).includes("390 × 844 dp · fontScale 1.3"), "free viewport and font-scale inputs must recalculate inspector data");
  const currentContract = await page.evaluate(async () => {
    const response = await fetch("../registry/experience_registry_v1.json");
    const registry = await response.json();
    const view = registry.features.find(feature => feature.id === "home_normal_v1").hifi_views.find(item => item.id === "multi_screen_static_hifi");
    const contract = await (await fetch("../../" + view.machine_authority)).json();
    return { view, contract };
  });
  const profileComputations = await page.evaluate(contract => [
    [360,640],[390,844],[430,932],[480,844],[320,480]
  ].map(([w,h]) => {
    const result = window.NH01StructuredWorkspace.calculate(w,h,1,24,24,contract);
    return {w,h,S:result.S,gapA:result.gAB-result.baseAB,gapB:result.gBC-result.baseBC,conflicts:result.conflicts};
  }), currentContract.contract);
  for (const result of profileComputations) {
    assert(Math.abs(result.gapA-result.gapB) < 0.001, `V1.1 equal surplus split failed at ${result.w}×${result.h}: ${JSON.stringify(result)}`);
    if (result.w === 320) assert(result.conflicts.some(item => item.startsWith("SPEC_CONFLICT")), "320×480 constraint conflict must remain explicit");
  }
  const nh01ExpectedAssets = [
    ["NH01-M01-320x480dp-STATIC-HIFI-V1", 1024, 1536, "a3808f2fb93d"],
    ["NH01-M02-360x640dp-STATIC-HIFI-V1", 941, 1672, "8a6721552cec"],
    ["NH01-M03-390x844dp-STATIC-HIFI-V1", 853, 1844, "6a5e2e9fd18d"],
    ["NH01-M04-430x932dp-STATIC-HIFI-V1", 853, 1844, "07846d3089b7"],
    ["NH01-M05-480dp-WIDTH-STATIC-HIFI-V1", 1024, 1536, "ee2ed666107b"]
  ];
  for (const [assetId, width, height, shaPrefix] of nh01ExpectedAssets) {
    const card = page.locator(`.nh01-static-asset[data-nh01-asset-card="${assetId}"]`);
    assert(await card.isVisible(), `${assetId} gallery card is missing`);
    const loaded = await waitForLoadedImage(page, `.nh01-static-asset[data-nh01-asset-card="${assetId}"] .hifi-original img`, assetId);
    assert(loaded.width === width && loaded.height === height, `${assetId} source PNG dimensions changed: ${JSON.stringify(loaded)}`);
    assert((await card.innerText()).includes(shaPrefix), `${assetId} SHA is not visible in its card`);
  }
  await workspace.locator('[data-control="font"]').evaluate(node=>{node.value="1";node.dispatchEvent(new Event("input",{bubbles:true}));});
  await workspace.locator('[data-control="zoom"]').evaluate(node=>{node.value="1";node.dispatchEvent(new Event("input",{bubbles:true}));});
  for(const selector of ['[data-testid="nh01-canvas-background"]','[data-testid="nh01-canvas-brand"]','[data-testid="nh01-canvas-avatar"] img','[data-testid="nh01-canvas-camera"] img']){
    await waitForLoadedImage(page,selector,`NH01 render source ${selector}`);
  }
  await workspace.locator('[data-artboard]').evaluate(node=>node.querySelectorAll('.nh01-layer-selected').forEach(layer=>layer.classList.remove('nh01-layer-selected')));
  const artboard=workspace.locator('[data-artboard]');
  const originalArtboardStyle=await artboard.getAttribute('style');
  let renderedCanvas;
  try {
    await artboard.evaluate(node=>{
      node.style.setProperty('position','fixed','important');
      node.style.setProperty('left','0px','important');
      node.style.setProperty('top','0px','important');
      node.style.setProperty('margin','0px','important');
      node.style.setProperty('transform','none','important');
      node.style.setProperty('z-index','2147483647','important');
    });
    renderedCanvas=await page.screenshot({scale:"css",clip:{x:0,y:0,width:390,height:844}});
  } finally {
    await artboard.evaluate((node,style)=>{if(style===null)node.removeAttribute('style');else node.setAttribute('style',style);},originalArtboardStyle);
  }
  const canvasSize={width:renderedCanvas.readUInt32BE(16),height:renderedCanvas.readUInt32BE(20)};
  assert(canvasSize.width===390&&canvasSize.height===844,`NH01 comparison render must capture exactly 390×844 CSS px, got ${JSON.stringify(canvasSize)}`);
  const renderedCanvasPath=resolve(args.outputDir,"nh01-m03-render-390x844.png");
  await writeFile(renderedCanvasPath,renderedCanvas);
  const domEvidence=await workspace.evaluate(()=>{
    const board=document.querySelector('[data-artboard]');
    const origin=board.getBoundingClientRect();
    const tree=[...document.querySelectorAll('[data-tree-node]')].map(node=>({id:node.dataset.treeNode,parent:node.parentElement.closest('[data-tree-node]')?.dataset.treeNode??null,name:node.querySelector('[data-select] .nh01-tree-copy>span')?.textContent?.trim()||null,sourceNodeId:node.querySelector('[data-select]')?.dataset.sourceNodeId||null,sourceAssetId:node.querySelector('[data-select]')?.dataset.sourceAssetId||null,status:node.querySelector('[data-select] .nh01-tree-copy small')?.textContent?.trim()||null}));
    const dom=[board,...board.querySelectorAll('[data-layer]')].map(node=>{const r=node.getBoundingClientRect();return{id:node.dataset.layer,parent:node===board?null:node.parentElement.closest('[data-layer]')?.dataset.layer??null,rect:{x:+(r.left-origin.left).toFixed(3),y:+(r.top-origin.top).toFixed(3),w:+r.width.toFixed(3),h:+r.height.toFixed(3)},tag:node.tagName,assetStatus:node.dataset.assetStatus||'ROOT',mappingStatus:node.dataset.mappingStatus||'ROOT'};});
    const imageNodes=[...board.querySelectorAll('img')].map(image=>({alt:image.alt,src:image.currentSrc,naturalWidth:image.naturalWidth,naturalHeight:image.naturalHeight,complete:image.complete,objectFit:getComputedStyle(image).objectFit}));
    const domById=new Map(dom.map(item=>[item.id,item]));
    return {viewport:{width:390,height:844,fontScale:1,zoom:1},tree,dom,imageNodes,unmapped:tree.filter(node=>!domById.has(node.id)),parentMismatches:tree.filter(node=>node.id!=='page'&&domById.get(node.id)?.parent!==node.parent)};
  });
  assert(domEvidence.unmapped.length===0&&domEvidence.parentMismatches.length===0,`NH01 DOM measurement report must reflect all real source-parent relationships: ${JSON.stringify({unmapped:domEvidence.unmapped,parentMismatches:domEvidence.parentMismatches})}`);
  await writeFile(resolve(args.outputDir,"nh01-m03-dom-bounds.json"),JSON.stringify({build_sha:buildMarker,load_state:nh01LoadSnapshot.state,canvas_png:{path:renderedCanvasPath,...canvasSize},frozen_reference:{asset_id:"NH01-M03-390x844dp-STATIC-HIFI-V1",path:"design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M03_390x844dp_STATIC_HIFI_V1.png",sha256:"6a5e2e9fd18d2b06f9452b6a6c95802e41cfc0450b7ec77cf0d1500c6b3932ff"},...domEvidence},null,2)+"\n");
  const domFormulaComparison=await page.evaluate(({contract,measuredLayers})=>{
    const calculated=window.NH01StructuredWorkspace.calculate(390,844,1,24,24,contract,false);
    const actual=new Map(measuredLayers.map(layer=>[layer.id,layer.rect]));
    const ids=['page','environment','background','header','brand','avatar','stats','recent-header','pager','hero','peek-left','peek-right','capture-action-group','cta','camera'];
    return ids.map(id=>{const expected=calculated.rects[id]||{x:0,y:0,w:390,h:844},seen=actual.get(id);return{id,expected:{x:expected.x,y:expected.y,w:expected.w,h:expected.h},actual:seen,delta:seen?{x:+(seen.x-expected.x).toFixed(3),y:+(seen.y-expected.y).toFixed(3),w:+(seen.w-expected.w).toFixed(3),h:+(seen.h-expected.h).toFixed(3)}:null};});
  },{contract:currentContract.contract,measuredLayers:domEvidence.dom});
  assert(domFormulaComparison.every(row=>row.actual&&row.delta&&Object.values(row.delta).every(delta=>Math.abs(delta)<0.1)),`the rendered DOM must use the formal 390×844 contract rectangles: ${JSON.stringify(domFormulaComparison)}`);
  const profileGeometryComparisons=[{profile:"M03",viewport:{width:390,height:844,fontScale:1,topInset:24,bottomInset:24},layers:domFormulaComparison}];
  const additionalFrozenProfiles=[
    {index:"0",prefix:"m01",assetId:"NH01-M01-320x480dp-STATIC-HIFI-V1",path:"design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M01_320x480dp_STATIC_HIFI_V1.png",width:320,height:480},
    {index:"1",prefix:"m02",assetId:"NH01-M02-360x640dp-STATIC-HIFI-V1",path:"design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M02_360x640dp_STATIC_HIFI_V1.png",width:360,height:640},
    {index:"3",prefix:"m04",assetId:"NH01-M04-430x932dp-STATIC-HIFI-V1",path:"design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M04_430x932dp_STATIC_HIFI_V1.png",width:430,height:932}
  ];
  for(const profile of additionalFrozenProfiles){
    await workspace.locator('[data-control="profile"]').selectOption(profile.index);
    await workspace.locator('[data-control="font"]').evaluate(node=>{node.value="1";node.dispatchEvent(new Event("input",{bubbles:true}));});
    await workspace.locator('[data-control="zoom"]').evaluate(node=>{node.value="1";node.dispatchEvent(new Event("input",{bubbles:true}));});
    const canvas=workspace.locator('[data-artboard]');
    await canvas.evaluate(node=>{node.classList.remove('nh01-layer-selected');node.querySelectorAll('.nh01-layer-selected').forEach(layer=>layer.classList.remove('nh01-layer-selected'));});
    const originalCanvasStyle=await canvas.getAttribute("style");
    let imageBytes;
    try {
      await canvas.evaluate(node=>{
        node.style.setProperty("position","fixed","important");
        node.style.setProperty("left","0px","important");
        node.style.setProperty("top","0px","important");
        node.style.setProperty("margin","0px","important");
        node.style.setProperty("transform","none","important");
        node.style.setProperty("z-index","2147483647","important");
      });
      imageBytes=await page.screenshot({scale:"css",clip:{x:0,y:0,width:profile.width,height:profile.height}});
    } finally {
      await canvas.evaluate((node,style)=>{if(style===null)node.removeAttribute("style");else node.setAttribute("style",style);},originalCanvasStyle);
    }
    const dimensions={width:imageBytes.readUInt32BE(16),height:imageBytes.readUInt32BE(20)};
    assert(dimensions.width===profile.width&&dimensions.height===profile.height,`${profile.assetId} must capture its free viewport size: ${JSON.stringify(dimensions)}`);
    const imagePath=resolve(args.outputDir,`nh01-${profile.prefix}-render-${profile.width}x${profile.height}.png`);
    await writeFile(imagePath,imageBytes);
    const measured=await workspace.evaluate(()=>{
      const board=document.querySelector('[data-artboard]'),origin=board.getBoundingClientRect();
      const tree=[...document.querySelectorAll('[data-tree-node]')].map(node=>({id:node.dataset.treeNode,parent:node.parentElement.closest('[data-tree-node]')?.dataset.treeNode??null,name:node.querySelector('[data-select] .nh01-tree-copy>span')?.textContent?.trim()||null,sourceNodeId:node.querySelector('[data-select]')?.dataset.sourceNodeId||null,sourceAssetId:node.querySelector('[data-select]')?.dataset.sourceAssetId||null,status:node.querySelector('[data-select] .nh01-tree-copy small')?.textContent?.trim()||null}));
      const dom=[board,...board.querySelectorAll('[data-layer]')].map(node=>{const r=node.getBoundingClientRect();return{id:node.dataset.layer,parent:node===board?null:node.parentElement.closest('[data-layer]')?.dataset.layer??null,rect:{x:+(r.left-origin.left).toFixed(3),y:+(r.top-origin.top).toFixed(3),w:+r.width.toFixed(3),h:+r.height.toFixed(3)},tag:node.tagName,assetStatus:node.dataset.assetStatus||'ROOT',mappingStatus:node.dataset.mappingStatus||'ROOT'};});
      const imageNodes=[...board.querySelectorAll('img')].map(image=>({alt:image.alt,src:image.currentSrc,naturalWidth:image.naturalWidth,naturalHeight:image.naturalHeight,complete:image.complete,objectFit:getComputedStyle(image).objectFit}));
      const domById=new Map(dom.map(item=>[item.id,item]));
      return {viewport:{width:Number(document.querySelector('[data-control="width"]').value),height:Number(document.querySelector('[data-control="height"]').value),fontScale:Number(document.querySelector('[data-control="font"]').value),zoom:Number(document.querySelector('[data-control="zoom"]').value)},tree,dom,imageNodes,unmapped:tree.filter(node=>!domById.has(node.id)),parentMismatches:tree.filter(node=>node.id!=='page'&&domById.get(node.id)?.parent!==node.parent)};
    });
    assert(measured.unmapped.length===0&&measured.parentMismatches.length===0,`${profile.assetId} tree/DOM mapping must remain intact`);
    const profileGeometry=await page.evaluate(({contract,measured,profile})=>{
      const calculated=window.NH01StructuredWorkspace.calculate(profile.width,profile.height,1,24,24,contract,false);
      const actual=new Map(measured.dom.map(layer=>[layer.id,layer.rect]));
      const ids=['page','environment','background','header','brand','avatar','stats','recent-header','pager','hero','peek-left','peek-right','capture-action-group','cta','camera'];
      return ids.map(id=>{const expected=calculated.rects[id]||{x:0,y:0,w:profile.width,h:profile.height},seen=actual.get(id);return{id,expected:{x:expected.x,y:expected.y,w:expected.w,h:expected.h},actual:seen,delta:seen?{x:+(seen.x-expected.x).toFixed(3),y:+(seen.y-expected.y).toFixed(3),w:+(seen.w-expected.w).toFixed(3),h:+(seen.h-expected.h).toFixed(3)}:null};});
    },{contract:currentContract.contract,measured,profile});
    assert(profileGeometry.every(row=>row.actual&&row.delta&&Object.values(row.delta).every(delta=>Math.abs(delta)<0.1)),`${profile.assetId} measured bounds must equal the formal responsive contract: ${JSON.stringify(profileGeometry)}`);
    profileGeometryComparisons.push({profile:profile.prefix.toUpperCase(),viewport:{width:profile.width,height:profile.height,fontScale:1,topInset:24,bottomInset:24},layers:profileGeometry});
    const referenceResource=resourceEvidence.find(resource=>resource.id===profile.assetId);
    assert(referenceResource?.sha,`${profile.assetId} frozen asset SHA evidence is required`);
    await writeFile(resolve(args.outputDir,`nh01-${profile.prefix}-dom-bounds.json`),JSON.stringify({build_sha:buildMarker,load_state:nh01LoadSnapshot.state,canvas_png:{path:imagePath,...dimensions},frozen_reference:{asset_id:profile.assetId,path:profile.path,sha256:referenceResource.sha},...measured,formal_contract_geometry:profileGeometry},null,2)+"\n");
  }
  await workspace.locator('[data-control="profile"]').selectOption("4");
  const widthBoundaryEvidence=await page.evaluate(contract=>{
    const width=Number(document.querySelector('[data-control="width"]').value),height=Number(document.querySelector('[data-control="height"]').value),font=Number(document.querySelector('[data-control="font"]').value),zoom=Number(document.querySelector('[data-control="zoom"]').value);
    const board=document.querySelector('[data-artboard]'),origin=board.getBoundingClientRect();
    const layers=[board,...board.querySelectorAll('[data-layer]')].map(node=>{const r=node.getBoundingClientRect();return{id:node.dataset.layer,rect:{x:+(r.left-origin.left).toFixed(3),y:+(r.top-origin.top).toFixed(3),w:+r.width.toFixed(3),h:+r.height.toFixed(3)}};});
    const result=window.NH01StructuredWorkspace.calculate(width,height,font,24,24,contract);
    return {viewport:{width,height,fontScale:font,zoom,height_authority:"UNSPECIFIED_BY_M05"},contract_status:result.conflicts,dom_layers:layers};
  },currentContract.contract);
  assert(widthBoundaryEvidence.viewport.width===480&&widthBoundaryEvidence.viewport.height===932,`M05 must preserve the current free height rather than freeze it: ${JSON.stringify(widthBoundaryEvidence.viewport)}`);
  await writeFile(resolve(args.outputDir,"nh01-m05-width-only-bounds.json"),JSON.stringify({build_sha:buildMarker,frozen_reference:{asset_id:"NH01-M05-480dp-WIDTH-STATIC-HIFI-V1",path:"design/pages/home/normal_home/08_multi_screen_static_hifi/NH01_M05_480dp_WIDTH_STATIC_HIFI_V1.png",height_authority:"UNSPECIFIED"},...widthBoundaryEvidence},null,2)+"\n");
  await workspace.locator('[data-control="profile"]').selectOption("2");
  await workspace.locator('[data-control="font"]').evaluate(node=>{node.value="1";node.dispatchEvent(new Event("input",{bubbles:true}));});
  await workspace.locator('[data-control="zoom"]').evaluate(node=>{node.value="0.7";node.dispatchEvent(new Event("input",{bubbles:true}));});
  const nh01ContractText = await page.locator("#hifiViewCanvas").innerText();
  for (const phrase of ["480dp width; height unspecified", "24±2 dp", "fontScale", "六组相邻区域验收", "24 次 API 28 原生执行", "NOT_VERIFIED"]) {
    assert(nh01ContractText.includes(phrase), `NH01 responsive contract detail is missing: ${phrase}`);
  }
  assert((await page.locator("#hifiViewContractDetails").innerText()).includes("NOT_VERIFIED"), "NH01 Design Manager metadata must retain NOT_VERIFIED runtime status");
  await page.screenshot({ path: resolve(args.outputDir, "nh01-multiscreen-desktop.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  const nh01Narrow = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    galleryColumns: getComputedStyle(document.querySelector(".nh01-static-gallery")).gridTemplateColumns,
    assetCount: document.querySelectorAll(".nh01-static-asset").length
  }));
  assert(nh01Narrow.document <= nh01Narrow.viewport && nh01Narrow.body <= nh01Narrow.viewport,
    `NH01 narrow workspace has horizontal overflow: ${JSON.stringify(nh01Narrow)}`);
  assert(nh01Narrow.assetCount === 5, "NH01 narrow workspace lost one or more assets");
  await page.screenshot({ path: resolve(args.outputDir, "nh01-multiscreen-390.png"), fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  assert(page.url().endsWith(nh01MultiScreenHash), "NH01 route must remain active before refresh verification");
  await page.reload({ waitUntil: "networkidle" });
  await page.locator('[data-testid="nh01-multiscreen-workspace"]').waitFor({ state: "visible", timeout: 30000 });
  await page.waitForFunction(()=>document.querySelector("#hifiViewCanvas")?.dataset.nh01LoadState==="READY",null,{timeout:30000});
  assert(page.url().endsWith(nh01MultiScreenHash), "NH01 deep link did not survive refresh");
  await open("#page/home_normal_v1");
  await waitForManager(page);
  const nh01Menu = page.locator('#pageList .page-subitem[data-page-id="home_normal_v1"][data-hifi-id="multi_screen_static_hifi"]');
  assert(await nh01Menu.isVisible(), "NH01 multi-screen entry is not visible under 有数据首页 navigation");
  await nh01Menu.click();
  await page.waitForFunction(expected => location.hash === expected, nh01MultiScreenHash);
  await waitForHifiView(page);
  await page.waitForFunction(()=>document.querySelector("#hifiViewCanvas")?.dataset.nh01LoadState==="READY",null,{timeout:30000});
  assert(page.url().endsWith(nh01MultiScreenHash), "NH01 menu item did not open its registered deep route");
  await page.goBack();
  await page.waitForFunction(() => location.hash === "#page/home_normal_v1", null, { timeout: 15000 });
  await page.goForward();
  await page.waitForFunction(expected => location.hash === expected, nh01MultiScreenHash, { timeout: 15000 });
  await page.waitForFunction(()=>document.querySelector("#hifiViewCanvas")?.dataset.nh01LoadState==="READY",null,{timeout:30000});
  await page.locator(".nh01-static-asset").first().locator(".hifi-image-open").click();
  assert(await page.locator(".hifi-image-lightbox[open]").isVisible(), "NH01 source image did not open in the existing lightbox");
  await page.locator(".hifi-image-lightbox .hifi-lightbox-close").click();
  await page.waitForFunction(() => !document.querySelector(".hifi-image-lightbox")?.open, null, { timeout: 10000 });

  const faultEvidence=[];
  const waitForNH01State=async(targetPage,state)=>targetPage.waitForFunction(expected=>document.querySelector("#hifiViewCanvas")?.dataset.nh01LoadState===expected,state,{timeout:30000});
  const openFaultPage=async(targetPage)=>{
    await targetPage.goto(baseUrl+nh01MultiScreenHash,{waitUntil:"domcontentloaded",timeout:45000});
    await targetPage.locator("#pageList .module-item").first().waitFor({state:"visible",timeout:30000});
    await targetPage.locator("#hifiViewTitle").waitFor({state:"visible",timeout:30000});
  };
  const faultPage=await browser.newPage({viewport:{width:1440,height:1000}});
  let injectManifest404=true;
  await faultPage.route("**/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json*",async route=>{
    if(injectManifest404){injectManifest404=false;await route.fulfill({status:404,contentType:"text/plain",body:"synthetic manifest 404"});}
    else await route.continue();
  });
  await openFaultPage(faultPage);
  await waitForNH01State(faultPage,"ERROR");
  const manifestFailure=await faultPage.locator('#hifiViewCanvas [data-resource-id="frozen-manifest"]').innerText();
  assert(manifestFailure.includes("404")&&manifestFailure.includes("NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json"),`Manifest 404 must identify the actual URL/status: ${manifestFailure}`);
  await faultPage.screenshot({path:resolve(args.outputDir,"nh01-manifest-404-error.png"),fullPage:false});
  faultEvidence.push({case:"manifest-404",state:"ERROR",resource:manifestFailure});
  await faultPage.locator('#hifiViewCanvas [data-action="retry"]').click();
  await waitForNH01State(faultPage,"READY");
  faultEvidence.push({case:"manifest-retry",state:"READY"});
  await faultPage.unroute("**/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json*");
  await faultPage.close();

  const contractFailurePage=await browser.newPage({viewport:{width:1440,height:1000}});
  let injectContract404=true;
  await contractFailurePage.route("**/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json*",async route=>{
    if(injectContract404){injectContract404=false;await route.fulfill({status:404,contentType:"text/plain",body:"synthetic contract 404"});}
    else await route.continue();
  });
  await openFaultPage(contractFailurePage);
  await waitForNH01State(contractFailurePage,"ERROR");
  const contractFailure=await contractFailurePage.locator('#hifiViewCanvas [data-resource-id="machine-contract"]').innerText();
  assert(contractFailure.includes("404")&&contractFailure.includes("NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json"),`Machine contract 404 must report URL/status: ${contractFailure}`);
  await contractFailurePage.locator('#hifiViewCanvas [data-action="retry"]').click();
  await waitForNH01State(contractFailurePage,"READY");
  faultEvidence.push({case:"machine-contract-404-and-retry",state:"ERROR→READY",resource:contractFailure});
  await contractFailurePage.unroute("**/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json*");
  await contractFailurePage.close();

  const invalidJsonPage=await browser.newPage({viewport:{width:1440,height:1000}});
  let injectInvalidJson=true;
  await invalidJsonPage.route("**/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json*",async route=>{
    if(injectInvalidJson){injectInvalidJson=false;await route.fulfill({status:200,contentType:"application/json",body:"{invalid json"});}
    else await route.continue();
  });
  await openFaultPage(invalidJsonPage);
  await waitForNH01State(invalidJsonPage,"ERROR");
  const invalidJson=await invalidJsonPage.locator('#hifiViewCanvas [data-resource-id="machine-contract"]').innerText();
  assert(invalidJson.includes("200")&&invalidJson.includes("INVALID")&&invalidJson.includes("JSON parse failed"),`Invalid JSON must expose HTTP 200 and parse failure: ${invalidJson}`);
  await invalidJsonPage.locator('#hifiViewCanvas [data-action="retry"]').click();
  await waitForNH01State(invalidJsonPage,"READY");
  faultEvidence.push({case:"invalid-json-200-and-retry",state:"ERROR→READY",resource:invalidJson});
  await invalidJsonPage.close();

  const moduleFailurePage=await browser.newPage({viewport:{width:1440,height:1000}});
  let allowModuleRecovery=false;
  await moduleFailurePage.route("**/nh01_structured_workspace.js*",async route=>{
    if(!allowModuleRecovery)await route.fulfill({status:404,contentType:"text/plain",body:"synthetic module 404"});
    else await route.continue();
  });
  await openFaultPage(moduleFailurePage);
  await waitForNH01State(moduleFailurePage,"ERROR");
  const moduleFailure=await moduleFailurePage.locator('#hifiViewCanvas [data-resource-id="workspace-js"]').innerText();
  assert(moduleFailure.includes("404")&&moduleFailure.includes("nh01_structured_workspace.js"),`Failed module script must report exact URL/status: ${moduleFailure}`);
  await moduleFailurePage.screenshot({path:resolve(args.outputDir,"nh01-module-404-error.png"),fullPage:false});
  allowModuleRecovery=true;
  await moduleFailurePage.locator('#hifiViewCanvas [data-action="retry"]').click();
  await waitForNH01State(moduleFailurePage,"READY");
  faultEvidence.push({case:"workspace-js-404-and-reload-retry",state:"ERROR→READY",resource:moduleFailure});
  await moduleFailurePage.close();

  const styleFailurePage=await browser.newPage({viewport:{width:1440,height:1000}});
  let allowStyleRecovery=false;
  await styleFailurePage.route("**/nh01_structured_workspace.css*",async route=>{
    if(!allowStyleRecovery)await route.fulfill({status:404,contentType:"text/plain",body:"synthetic style 404"});
    else await route.continue();
  });
  await openFaultPage(styleFailurePage);
  await waitForNH01State(styleFailurePage,"ERROR");
  const styleFailure=await styleFailurePage.locator('#hifiViewCanvas [data-resource-id="workspace-css"]').innerText();
  assert(styleFailure.includes("404")&&styleFailure.includes("nh01_structured_workspace.css"),`Failed module CSS must report exact URL/status: ${styleFailure}`);
  allowStyleRecovery=true;
  await styleFailurePage.locator('#hifiViewCanvas [data-action="retry"]').click();
  await waitForNH01State(styleFailurePage,"READY");
  faultEvidence.push({case:"workspace-css-404-and-retry",state:"ERROR→READY",resource:styleFailure});
  await styleFailurePage.close();

  const timeoutPage=await browser.newPage({viewport:{width:1440,height:1000}});
  await timeoutPage.addInitScript(()=>{window.__NH01_TEST_TIMEOUT_MS__=1000;});
  let delayManifest=true;
  await timeoutPage.route("**/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json*",async route=>{
    if(delayManifest){delayManifest=false;await new Promise(resolve=>setTimeout(resolve,2500));try{await route.continue();}catch{}}
    else await route.continue();
  });
  await openFaultPage(timeoutPage);
  await waitForNH01State(timeoutPage,"ERROR");
  const timeoutFailure=await timeoutPage.locator('#hifiViewCanvas [data-resource-id="frozen-manifest"]').innerText();
  assert(timeoutFailure.includes("timeout")&&timeoutFailure.includes("1000 ms"),`Timed out dependency must be identified with timeout duration: ${timeoutFailure}`);
  await timeoutPage.screenshot({path:resolve(args.outputDir,"nh01-resource-timeout-error.png"),fullPage:false});
  await timeoutPage.locator('#hifiViewCanvas [data-action="retry"]').click();
  await waitForNH01State(timeoutPage,"READY");
  faultEvidence.push({case:"manifest-timeout-and-retry",state:"ERROR→READY",resource:timeoutFailure});
  await timeoutPage.close();

  const stalePage=await browser.newPage({viewport:{width:1440,height:1000}});
  let notifyContractStarted;
  const contractStarted=new Promise(resolve=>{notifyContractStarted=resolve;});
  let delayFirstContract=true;
  await stalePage.route("**/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.json*",async route=>{
    if(delayFirstContract){delayFirstContract=false;notifyContractStarted();await new Promise(resolve=>setTimeout(resolve,1000));try{await route.continue();}catch{}}
    else await route.continue();
  });
  await openFaultPage(stalePage);
  await contractStarted;
  await stalePage.evaluate(()=>{location.hash="#page/home_normal_v1";});
  await stalePage.waitForFunction(()=>document.querySelector("#hifiViewPanel")?.classList.contains("hidden"),null,{timeout:10000});
  await stalePage.waitForTimeout(1200);
  assert(await stalePage.locator("#hifiViewPanel .nh01-static-load-error").count()===0,"old route response must not render a failure over the current route");
  await stalePage.evaluate(hash=>{location.hash=hash;},nh01MultiScreenHash);
  await waitForNH01State(stalePage,"READY");
  faultEvidence.push({case:"route-change-cancels-stale-request",state:"CANCELLED; new route READY"});
  await stalePage.close();

  await open(p01Hash);
  await waitForManager(page);
  await waitForHifiView(page);
  if (args.expectedBuild) assert(buildMarker === args.expectedBuild, `live/build marker mismatch: ${buildMarker} != ${args.expectedBuild}`);
  const image = page.locator(".hifi-asset-figure img").first();
  const guideImageInfo = await waitForLoadedImage(page, ".hifi-asset-figure img", "P01");
  guideImageInfo.objectFit = await image.evaluate(node => getComputedStyle(node).objectFit);
  assert(guideImageInfo.complete && guideImageInfo.width === 941 && guideImageInfo.height === 1671,
    `P01 must load original 941×1671 pixels: ${JSON.stringify(guideImageInfo)}`);
  assert(guideImageInfo.objectFit === "contain", `P01 image fit must contain: ${guideImageInfo.objectFit}`);
  assert((await page.locator("#hifiViewContractDetails").innerText()).includes("dc731cca5470"), "P01 metadata must expose its registered SHA");
  const guideOriginalHref = await page.locator(".hifi-image-actions a").first().getAttribute("href");
  assert(guideOriginalHref?.includes("P01_First_Use_Camera_Guide_Frozen.png"), "P01 must retain a direct original PNG link");

  const resourcesMenuItem = page.locator('#pageList .page-subsubitem[data-hifi-child-id="resources"]');
  assert(await resourcesMenuItem.isVisible(), "07 guide must show its Resources submenu");
  await resourcesMenuItem.click();
  await page.waitForFunction(expected => location.hash === expected, resourcesHash);
  await waitForHifiView(page);
  assert((await page.locator("#hifiViewTitle").innerText()).trim() === "资源", "Resources submenu must open its registered route");
  assert((await page.locator("#hifiViewSummary").innerText()).includes("不映射为 Android 运行时背景"), "Resources route must state its Android runtime boundary");
  await page.locator('[data-testid="first-use-guide-workspace"]').waitFor({state: "visible", timeout: 30000});
  await page.locator('[data-guide-tab="structure"]').waitFor({state: "visible", timeout: 30000});
  assert(await page.locator(".guide-workspace-tabs [data-guide-tab]").count() === 3, "Resources route must expose structure, responsive and assets workspaces");

  await page.locator('[data-guide-tab="structure"]').click();
  await page.locator('[data-testid="guide-structure-workspace"]').waitFor({state: "visible", timeout: 30000});
  await page.locator(".guide-layer-row[data-guide-layer='guide.hero']").waitFor({state: "visible"});
  assert(await page.locator(".guide-layer-row").count() === 25, "P01 structured page must expose its complete stable layer tree");
  await page.locator(".guide-layer-row[data-guide-layer='guide.error.too_small']").click();
  assert((await page.locator('[data-testid="guide-layer-inspector"]').innerText()).includes("guide.error.too_small"), "Layer selection must update the property inspector");
  assert((await page.locator('[data-testid="guide-layer-inspector"]').innerText()).includes("Native 像素边界"), "Inspector must expose exact measured pixel geometry");
  await page.locator('[data-guide-compare]').evaluate(node => { node.value = "0"; node.dispatchEvent(new Event("input", {bubbles:true})); });
  assert((await page.locator("[data-guide-compare-value]").innerText()) === "0%", "Frozen/geometry comparison control must update overlay visibility");
  await page.screenshot({ path: resolve(args.outputDir, "guide-structure-1440.png"), fullPage: true });

  await page.locator('[data-guide-tab="resources"]').click();
  await page.locator('[data-testid="guide-resources-workspace"]').waitFor({state: "visible", timeout: 30000});
  assert(await page.locator(".guide-resource-card").count() === 3, "Resources workspace must show the three unchanged Frozen source PNGs");
  const resourceExpected = [
    ["p01", 941, 1671, "P01_First_Use_Camera_Guide_Frozen.png", "dc731cca5470"],
    ["p02", 941, 1671, "P02_First_Use_Camera_Guide_Frozen.png", "fb092235fd04"],
    ["p03", 941, 1672, "P03_First_Use_Camera_Guide_Frozen.png", "1a3008483ac5"]
  ];
  for (const [id, width, height, filename, shaPrefix] of resourceExpected) {
    const selector = `.guide-frozen-resource[data-resource-id="${id}"]`;
    const card = page.locator(selector);
    await card.scrollIntoViewIfNeeded();
    const loaded = await waitForLoadedImage(page, `${selector} img`, `${id.toUpperCase()} resource`);
    assert(loaded.width === width && loaded.height === height,
      `${id.toUpperCase()} resource dimensions changed: ${JSON.stringify(loaded)}`);
    assert((await card.innerText()).includes(shaPrefix), `${id.toUpperCase()} resource SHA is not shown`);
    const download = card.locator("a[download]");
    assert(await download.getAttribute("download") === filename, `${id.toUpperCase()} original download filename changed`);
    assert((await download.getAttribute("href"))?.includes(filename), `${id.toUpperCase()} download does not point to its original PNG`);
  }
  const manifestLinks = page.locator(".guide-workspace-authorities a");
  const manifestHrefs = await manifestLinks.evaluateAll(nodes => nodes.map(node => node.getAttribute("href")));
  for (const path of ["manifest.json", "frontend_assets_manifest.json", "structured_pages.json", "geometry_measurements.json", "responsive_contract.json", "PREDEV_READINESS_MATRIX.json", "Freeze_Decision.md"]) {
    assert(manifestHrefs.some(href => href?.includes(`first_use_camera_guide/${path}`)), `Resources workspace must link ${path}`);
  }
  assert(await page.locator(".guide-asset-slot").count() === 12, "Resources workspace must display all 12 page/role photo slots");
  assert(await page.locator(".guide-asset-slot[data-authority='CANDIDATE_NEEDS_APPROVAL']").count() === 12, "All generated images must remain candidate assets");
  assert(await page.locator(".guide-asset-slot[data-asset-id$='_correct_hero']").filter({hasText: "ASSET_SOURCE_BLOCKED"}).count() === 3, "Three original Hero source slots must remain explicitly blocked");
  assert(await page.locator(".guide-reference-only").count() === 9, "Nine archived composite crops must remain REFERENCE_ONLY");
  assert(await page.locator(".guide-asset-slot[data-asset-id^='p02_']").count() === 4, "P02 must have four vertical-orientation candidate bindings");
  const candidatePaths = await page.locator(".guide-asset-slot").evaluateAll(nodes => nodes.map(node => node.dataset.sourcePath));
  assert(new Set(candidatePaths).size === 8, `12 page/role bindings must reuse exactly eight original candidate files: ${new Set(candidatePaths).size}`);
  for (const asset of await page.locator(".guide-asset-slot").all()) {
    await asset.scrollIntoViewIfNeeded();
    const preview = asset.locator(".guide-candidate-preview img");
    const dimensions = await preview.evaluate(image => new Promise(resolve => {
      if (image.complete && image.naturalWidth > 0) resolve({width: image.naturalWidth, height: image.naturalHeight});
      else { image.addEventListener("load", () => resolve({width: image.naturalWidth, height: image.naturalHeight}), {once:true}); image.addEventListener("error", () => resolve({width:0,height:0}), {once:true}); }
    }));
    assert(dimensions.width > 0 && dimensions.height > 0, `Independent candidate image did not load: ${await asset.getAttribute("data-asset-id")}`);
    const cardText = await asset.innerText();
    assert(cardText.includes("CANDIDATE_NEEDS_APPROVAL") && cardText.includes("approved_for_frontend=false") && cardText.includes("approved_for_runtime=false"), "Candidate status and production boundary must be visible");
    assert(await asset.locator("a[download]").count() === 1, "Each independent candidate needs a direct original-file download link");
  }
  await page.locator(".guide-workspace-tabs").scrollIntoViewIfNeeded();
  await page.screenshot({ path: resolve(args.outputDir, "guide-assets-1440.png"), fullPage: false });

  const responsiveTab = page.locator('[data-guide-tab="responsive"]');
  await responsiveTab.scrollIntoViewIfNeeded();
  await responsiveTab.click({timeout: 10000});
  await page.waitForFunction(() => new URLSearchParams(location.search).get("guide_workspace") === "responsive", null, {timeout: 10000});
  const workspaceState = await page.locator(".guide-workspace").getAttribute("data-workspace");
  assert(workspaceState === "responsive", `Responsive tab click did not render the workspace: state=${workspaceState}; errors=${errors.join(" | ")}`);
  await page.locator('[data-testid="guide-responsive-workspace"]').waitFor({state: "visible", timeout: 10000});
  const responsivePageTitles = {p01:"竖屏拍摄示意",p02:"竖拍鱼类拍摄示意",p03:"横屏拍摄示意"};
  const responsiveMatrix = [];
  const responsiveProfiles = [[320,480],[360,640],[390,844],[412,915],[430,932],[480,960],[640,360],[844,390],[915,412]];
  for (const pageId of ["p01", "p02", "p03"]) {
    await page.locator(`[data-guide-page="${pageId}"]`).click();
    assert((await page.locator(".guide-sim-title").innerText()).includes(responsivePageTitles[pageId]), `${pageId} responsive page copy failed to update`);
    for (const [width, height] of responsiveProfiles) {
      for (const fontScale of ["1", "1.3"]) {
        await page.locator("[data-testid='guide-simulator-viewport']").evaluate((viewport, profile) => {
          const [width,height,fontScale]=profile;
          viewport.dataset.guideViewportWidth=String(width);
          viewport.dataset.guideViewportHeight=String(height);
          viewport.style.width=`${width}px`;
          viewport.style.height=`${height}px`;
          viewport.style.setProperty("--guide-viewport-h",`${height}px`);
          viewport.style.setProperty("--guide-base-font",`${14*fontScale}px`);
          viewport.style.setProperty("--guide-small-font",`${12*fontScale}px`);
          viewport.style.setProperty("--guide-title-font",`${22*fontScale}px`);
        },[width,height,Number(fontScale)]);
        const loadedHero = await waitForLoadedImage(page, ".guide-sim-hero img", `${pageId} hero ${width}x${height} font ${fontScale}`);
        const result = await page.locator("[data-testid='guide-simulator-viewport']").evaluate(async viewport => {
          const content = viewport.querySelector(".guide-sim-page");
          const selectors = [".guide-sim-header", ".guide-sim-hero", ".guide-sim-intro", ".guide-sim-examples", ".guide-sim-completion", ".guide-sim-pagination", ".guide-sim-actions"];
          const boxes = selectors.map(selector => { const rect = content.querySelector(selector).getBoundingClientRect(); return {selector,top:rect.top,bottom:rect.bottom}; });
          const textNodes = [...content.querySelectorAll(".guide-sim-text,.guide-sim-example span,.guide-sim-masthead span")];
          const textOverflow = textNodes.filter(node => node.scrollWidth > node.clientWidth + 1).map(node => node.textContent);
          const targets = [...content.querySelectorAll(".guide-sim-back,.guide-sim-actions button")].map(node => { const rect = node.getBoundingClientRect(); return {width:rect.width,height:rect.height}; });
          viewport.scrollTop = viewport.scrollHeight;
          await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
          const primary = content.querySelector(".guide-sim-primary").getBoundingClientRect();
          const outer = viewport.getBoundingClientRect();
          const sourceOrientations = [...content.querySelectorAll("img[data-fish-orientation]")].map(node => node.dataset.fishOrientation);
          return {
            viewportWidth:viewport.getBoundingClientRect().width,viewportHeight:viewport.getBoundingClientRect().height,
            selectedWidth:Number(viewport.dataset.guideViewportWidth),selectedHeight:Number(viewport.dataset.guideViewportHeight),
            viewportClientWidth:viewport.clientWidth,viewportClientHeight:viewport.clientHeight,
            viewportScrollWidth:viewport.scrollWidth,contentWidth:content.clientWidth,contentScrollWidth:content.scrollWidth,
            scrollHeight:viewport.scrollHeight,scrollTop:viewport.scrollTop,
            horizontalOverflow:textOverflow,targets,boxes,primaryReachable:primary.top>=outer.top-1 && primary.bottom<=outer.bottom+1,
            orientation:sourceOrientations,gridColumns:getComputedStyle(content.querySelector(".guide-sim-examples")).gridTemplateColumns,
            gridColumnCount:getComputedStyle(content.querySelector(".guide-sim-examples")).gridTemplateColumns.split(/\s+/).filter(Boolean).length
          };
        });
        assert(result.viewportWidth === width && result.viewportHeight === height && result.selectedWidth === width && result.selectedHeight === height, `Simulator must preserve selected outer viewport for ${pageId} ${width}x${height}: ${JSON.stringify(result)}`);
        assert(result.viewportScrollWidth <= result.viewportWidth && result.contentScrollWidth <= result.contentWidth,
          `Responsive horizontal overflow at ${pageId} ${width}x${height} font ${fontScale}: ${JSON.stringify(result)}`);
        assert(result.horizontalOverflow.length === 0, `Responsive text clipped at ${pageId} ${width}x${height} font ${fontScale}: ${JSON.stringify(result.horizontalOverflow)}`);
        assert(result.targets.every(target => target.width >= 48 && target.height >= 48), `A responsive action target is below 48dp at ${pageId} ${width}x${height}: ${JSON.stringify(result.targets)}`);
        assert(result.boxes.every((box,index) => index === 0 || box.top >= result.boxes[index-1].bottom-1), `Responsive content groups overlap at ${pageId} ${width}x${height}: ${JSON.stringify(result.boxes)}`);
        assert(result.primaryReachable, `Primary action is not scroll-reachable at ${pageId} ${width}x${height} font ${fontScale}: ${JSON.stringify(result)}`);
        assert(result.orientation.length === 4 && result.orientation.every(value => value === (pageId === "p02" ? "vertical" : "horizontal")), `Fish orientation binding mismatch for ${pageId}: ${JSON.stringify(result.orientation)}`);
        assert(result.gridColumnCount === (width >= 384 ? 3 : 2), `Example grid breakpoint mismatch at ${pageId} ${width}x${height}: ${JSON.stringify(result.gridColumns)}`);
        assert(loadedHero.width > 0 && loadedHero.height > 0, `Candidate hero is missing at ${pageId} ${width}x${height}`);
        responsiveMatrix.push({pageId,width,height,fontScale,...result});
        if (pageId === "p03" && width === 640 && fontScale === "1.3") {
          await page.screenshot({ path: resolve(args.outputDir, "guide-responsive-p03-640x360-font13.png"), fullPage: false });
        }
      }
    }
  }
  assert(responsiveMatrix.length === 54, `Expected 54 page × viewport × font-scale cases, got ${responsiveMatrix.length}`);
  await page.locator('[data-guide-page="p01"]').click();
  await page.locator('[data-guide-preset="390x844"]').click();
  assert(await page.locator("[data-testid='guide-simulator-viewport']").getAttribute("data-guide-viewport-width") === "390", "Responsive preset control must update simulator width");
  await page.locator("[data-guide-font-scale]").selectOption("1.3");
  assert((await page.locator("[data-testid='guide-simulator-viewport']").evaluate(node => getComputedStyle(node).getPropertyValue("--guide-base-font").trim())) === "18.2px", "Font scale control must update the responsive preview typography");
  await page.locator("[data-guide-font-scale]").selectOption("1");
  const topInset = page.locator('[data-guide-inset="top"]');
  await topInset.fill("32");
  await topInset.press("Tab");
  await page.waitForFunction(() => getComputedStyle(document.querySelector("[data-testid='guide-simulator-viewport']")).getPropertyValue("--guide-inset-top").trim() === "32px", null, {timeout: 5000});
  const topInsetCss = await page.locator("[data-testid='guide-simulator-viewport']").evaluate(node => getComputedStyle(node).getPropertyValue("--guide-inset-top").trim());
  assert(topInsetCss === "32px", `Insets controls must update the simulator safe-area padding: ${topInsetCss}`);
  await page.screenshot({ path: resolve(args.outputDir, "guide-responsive-p01-390x844.png"), fullPage: true });
  assert(new URL(page.url()).searchParams.get("guide_workspace") === "responsive", "Workspace tab choice must survive a refreshable query deep link");
  assert(new URL(page.url()).searchParams.get("guide_page") === "p01", "Selected guide page must be stored in the workspace URL");
  await page.reload({waitUntil:"networkidle"});
  await page.locator('[data-testid="guide-responsive-workspace"]').waitFor({state:"visible",timeout:30000});
  assert(await page.locator(".guide-sim-title").innerText().then(text => text.includes(responsivePageTitles.p01)), "Responsive workspace/page selection did not survive refresh");
  await page.setViewportSize({ width: 390, height: 844 });
  const resourceMobileWidth = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
  assert(resourceMobileWidth.document <= 390 && resourceMobileWidth.body <= 390,
    `Guide workspace overflows the mobile viewport: ${JSON.stringify(resourceMobileWidth)}`);
  await page.screenshot({ path: resolve(args.outputDir, "guide-responsive-mobile-390.png"), fullPage: true });

  const viewportResults = [];
  for (const [width, height, screenshotName] of [
    [1440, 1000, "desktop-1440.png"], [768, 1024, "tablet-768.png"],
    [390, 844, "mobile-390.png"], [360, 800, null], [320, 720, null]
  ]) {
    await page.setViewportSize({ width, height });
    const result = await page.evaluate(() => ({
      viewport: window.innerWidth,
      document: document.documentElement.scrollWidth,
      body: document.body.scrollWidth,
      imageBox: document.querySelector(".hifi-asset-figure")?.getBoundingClientRect().width || 0,
      title: document.querySelector("#hifiViewTitle")?.textContent?.trim() || ""
    }));
    assert(result.document <= width && result.body <= width,
      `Horizontal overflow at ${width}px: ${JSON.stringify(result)}`);
    assert(result.imageBox <= width, `Hi-Fi figure exceeds ${width}px viewport: ${JSON.stringify(result)}`);
    viewportResults.push(result);
    if (screenshotName) await page.screenshot({ path: resolve(args.outputDir, screenshotName), fullPage: true });
  }

  await page.setViewportSize({ width: 1440, height: 1000 });
  await open(p01Hash);
  await page.locator(".hifi-image-open").click();
  const dialog = page.locator("dialog.hifi-image-lightbox");
  await dialog.waitFor({ state: "visible" });
  await dialog.locator('[data-zoom="in"]').click();
  assert((await dialog.locator(".hifi-zoom-level").innerText()) === "125%", "zoom-in control must change the displayed scale");
  await dialog.locator('[data-zoom="fit"]').click();
  assert((await dialog.locator(".hifi-zoom-level").innerText()) === "100%", "Fit Window must restore the fitted scale");
  await page.screenshot({ path: resolve(args.outputDir, "lightbox-1440.png"), fullPage: false });
  await page.keyboard.press("Escape");
  await dialog.waitFor({ state: "hidden" });
  assert(await page.locator(".hifi-image-open").evaluate(node => document.activeElement === node), "closing the lightbox must restore focus");

  for (const [hash, width, height, id] of [[p02Hash, 941, 1671, "P02"], [p03Hash, 941, 1672, "P03"]]) {
    await open(hash);
    const loaded = await waitForLoadedImage(page, ".hifi-asset-figure img", id);
    assert(loaded.complete && loaded.width === width && loaded.height === height, `${id} original dimensions are wrong: ${JSON.stringify(loaded)}`);
    assert(await page.locator("#hifiViewContractDetails").innerText().then(text => text.includes(id)), `${id} Page/State metadata missing`);
  }

  await open(overviewHash);
  await waitForManager(page);
  await waitForHifiView(page);
  const overviewImageInfo = await waitForLoadedImage(page, ".hifi-asset-figure img", "overview PNG");
  assert(overviewImageInfo.complete && overviewImageInfo.width === 5120 && overviewImageInfo.height === 2880,
    `Overview must show the complete approved PNG: ${JSON.stringify(overviewImageInfo)}`);
  assert((await page.locator(".hifi-overview-intro").innerText()).includes("不是任何单页"), "overview must not be labeled a single-page authority");
  assert((await page.locator("#hifiViewContractDetails").innerText()).includes("2cf0ea161360"), "overview metadata must display its approved PNG SHA");
  await page.screenshot({ path: resolve(args.outputDir, "overview-1440.png"), fullPage: true });
  const firstJourneyLink = page.locator(".hifi-overview-link").first();
  const overviewUrl = page.url();
  const targetHash = await firstJourneyLink.getAttribute("href");
  await firstJourneyLink.click();
  await page.waitForFunction(expected => location.hash === expected, targetHash);
  assert(page.url().includes(targetHash), "overview journey link did not open its registered route");
  await page.goBack();
  await page.waitForFunction(expected => location.hash === expected, overviewHash);
  assert(page.url().endsWith(overviewHash), `Back did not restore the overview route from ${overviewUrl}`);
  await page.goForward();
  await page.waitForFunction(expected => location.hash === expected, targetHash);

  await open(fullFlowModelsHash);
  await waitForManager(page);
  await waitForHifiView(page);
  assert(page.url().endsWith(fullFlowModelsHash), "full-flow/model direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("识别全流程-和模型"), "full-flow/model menu title is missing");
  const fullFlowImage = await waitForLoadedImage(page, ".recognition-full-flow-models .hifi-asset-figure img", "recognition full-flow diagram");
  assert(fullFlowImage.complete && fullFlowImage.width === 2200 && fullFlowImage.height === 1500,
    `full-flow SVG must load at 2200×1500: ${JSON.stringify(fullFlowImage)}`);
  assert(await page.locator(".recognition-full-flow-models .authority-index-card").count() >= 30,
    "full-flow/model page must render the stages, model roles, branches and acceptance cards");
  assert((await page.locator("#hifiViewContractDetails").innerText()).includes("Recognition_Full_Flow_Models_V1.md"),
    "full-flow/model page must expose its canonical specification link");
  await page.screenshot({ path: resolve(args.outputDir, "recognition-full-flow-models-1440.png"), fullPage: true });

  await open("#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/new_states_review");
  await waitForHifiView(page);
  const reviewImage = await waitForLoadedImage(page, ".hifi-asset-figure img", "review source");
  assert(reviewImage.complete && reviewImage.width === 2800 && reviewImage.height === 1600,
    `review image must load the real 2800×1600 source: ${JSON.stringify(reviewImage)}`);
  assert((await page.locator("#hifiViewStatus").innerText()).includes("部分完成"), "review image must remain PARTIAL, not Frozen");

  await open(p01Hash);
  await page.reload({ waitUntil: "networkidle" });
  assert(page.url().endsWith(p01Hash), "refresh must preserve the direct Page/State Hash");
  await waitForManager(page);
  await open(resourcesHash);
  await page.reload({ waitUntil: "networkidle" });
  assert(page.url().endsWith(resourcesHash), "refresh must preserve the direct Resources Hash");
  assert(await page.locator(".guide-resource-card").count() === 3, "direct Resources Hash must render all source PNGs after refresh");

  const missingCases = [
    ["capture_portrait", "Recognition_Capture_Ready_Portrait_Horizontal_Fish_Frozen.png"],
    ["capture_landscape", "Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png"],
    ["camera_permission_visual", "湖畔相机权限提示界面.png"]
  ];
  for (const [childId, absentName] of missingCases) {
    const hash = `#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/${childId}`;
    await open(hash);
    assert(await page.locator(".hifi-missing-authority").isVisible(), `${childId} missing-state explanation is not visible`);
    assert(await page.locator("#hifiViewCanvas img").count() === 0, `${childId} must not show a substitute image`);
    const missingLinkCount = await page.locator(`#hifiViewCanvas a[href*="${encodeURIComponent(absentName)}"], #hifiViewContractDetails a[href*="${encodeURIComponent(absentName)}"]`).count();
    assert(missingLinkCount === 0, `${childId} exposes an invalid link to an absent authority file`);
  }

  await open("#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/master_board_v1");
  assert(await page.locator(".hifi-master-board-gate").isVisible(), "Master Board missing PNG/SVG gate is not visible");
  assert((await page.locator(".hifi-state-table tbody tr").count()) > 0, "Master Board state JSON should render as a table");
  assert(await page.locator("#hifiViewCanvas img").count() === 0, "Overview must not be used as a Master Board image substitute");

  const swosHash = "#page/recognition_flow_v1_2/hifi/swos_optical_contract";
  await open(swosHash);
  await waitForManager(page);
  assert(page.url().endsWith(swosHash), "SWOS Optical Contract direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("SWOS 光学合同"), "SWOS contract menu title is missing");
  assert((await page.locator("#hifiViewAuthorities").innerText()).includes("SWOS_Optical_Contract_V1.md"), "SWOS contract authority link is missing");
  await page.screenshot({ path: resolve(args.outputDir, "swos-optical-contract-1440.png"), fullPage: true });
  await page.reload({ waitUntil: "networkidle" });
  assert(page.url().endsWith(swosHash), "refresh must preserve the SWOS contract Hash route");

  const swosStaticHash = "#page/recognition_flow_v1_2/hifi/swos_static_visual_v1_1";
  await open(swosStaticHash);
  await waitForManager(page);
  assert(page.url().endsWith(swosStaticHash), "SWOS static workspace direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("SWOS"), "SWOS static menu title is missing");
  const swosWorkspace = page.locator(".swos-workspace");
  await swosWorkspace.waitFor({ state: "visible" });
  assert(await page.locator(".swos-frame-card").count() === 15, "static workspace must show exactly 15 clickable frames");
  assert(await page.locator(".swos-track-row").count() === 2, "static workspace must keep Processing and RR09 on separate rows");
  assert(await page.locator(".swos-track-row").nth(0).locator(".swos-frame-card").count() === 8, "Processing storyboard row must contain 8 frames");
  assert(await page.locator(".swos-track-row").nth(1).locator(".swos-frame-card").count() === 7, "RR09 storyboard row must contain 7 frames");
  const r0Frame = page.locator('.swos-frame-card[data-frame-id="R0"] .swos-phone');
  assert(await r0Frame.locator(".swos-photo-shell").count() === 0, "R0 must not paint a full-screen blurred photo over the entering Result UI");
  assert(await r0Frame.locator(".swos-result-authority").count() === 1, "R0 must begin with the Frozen Result page underneath the shared photo");
  assert(await page.locator("[data-geometry-table] tbody tr").count() === 4, "P7/R0/R0.5/R1 geometry comparison must contain 4 rows");
  assert(await page.locator("[data-geometry-table]").getAttribute("data-contract-frame-count") === "15", "geometry comparison must bind the 15-frame contract");
  const resultSource = await waitForLoadedImage(page, ".swos-result-authority", "Frozen Result HIGH structure");
  assert(resultSource.width === 941 && resultSource.height === 1672, `static Result preview must use the existing Frozen HIGH authority: ${JSON.stringify(resultSource)}`);
  await page.screenshot({ path: resolve(args.outputDir, "swos-static-visual-1440.png"), fullPage: true });

  const sourceRequestCount = requests.length;
  let uploadedPhotoSha = null;
  if (args.swosPhoto) {
    const photoBytes = await readFile(args.swosPhoto);
    uploadedPhotoSha = createHash("sha256").update(photoBytes).digest("hex");
    await page.locator(".swos-photo-input").setInputFiles(args.swosPhoto);
    await page.waitForFunction(sha => document.querySelector(".swos-photo-meta")?.textContent?.includes(sha), uploadedPhotoSha, { timeout: 30000 });
    assert(await page.locator(".swos-photo-foreground").count() >= 15, "local photo must be composed into the static storyboard frames");
    const r0Source = await r0Frame.locator(".swos-photo-foreground").getAttribute("src");
    const r0BlurSource = await r0Frame.locator(".swos-r0-underlay img").getAttribute("src");
    assert(r0Source && r0Source === r0BlurSource, "R0 foreground and emerging Hero underlay must share the same local photo URL");
    assert(await r0Frame.locator(".swos-photo-shell").count() === 0, "R0 local photo must leave the Result page visible outside the Processing photo rect");
    assert(!(await page.evaluate(() => Object.keys(localStorage).length)), "SWOS must not persist the local photo in localStorage");
    assert(requests.slice(sourceRequestCount).every(request => request.method === "GET" && !request.url.includes(args.swosPhoto.split(/[\\/]/).pop())), "local photo selection must not trigger a POST or request the local filename");
  }

  const r05 = page.locator('.swos-frame-card[data-frame-id="R0.5"]');
  await r05.click();
  const swosDialog = page.locator(".swos-zoom-dialog");
  await swosDialog.waitFor({ state: "visible" });
  assert((await swosDialog.locator("[data-zoom-caption]").innerText()).includes("2900ms"), "R0.5 inspector must show its 2900ms global sample time");
  assert(await swosDialog.locator('.swos-phone[data-frame="R0.5"]').count() === 1, "R0.5 zoom must show its mid-flight photo frame");
  await swosDialog.locator('[data-mode="original"]').click();
  assert(await swosDialog.locator(".swos-overlay-svg").count() === 0, "original comparison must hide optical overlays");
  await swosDialog.locator('[data-mode="composite"]').click();
  await page.screenshot({ path: resolve(args.outputDir, "swos-r05-zoom-1440.png"), fullPage: false });
  await page.keyboard.press("Escape");
  await swosDialog.waitFor({ state: "hidden" });

  const rr09RelatedRoute = '#page/recognition_result_v1/hifi/motion_continuation';
  await page.locator('.hifi-related-routes a[href="' + rr09RelatedRoute + '"]').click();
  await page.waitForFunction(expected => location.hash === expected, rr09RelatedRoute);
  assert(await page.locator('.hifi-related-routes a[href="' + swosStaticHash + '"]').count() === 1, "RR09 must expose the reverse link to SWOS static inspection");
  await page.locator('.hifi-related-routes a[href="' + swosStaticHash + '"]').click();
  await page.waitForFunction(expected => location.hash === expected, swosStaticHash);
  const swosWebMotionRouteHash = "#page/recognition_flow_v1_2/hifi/swos_web_hifi_motion_contract_v1_0";
  assert(await page.locator('.hifi-related-routes a[href="' + swosWebMotionRouteHash + '"]').count() === 1, "SWOS static review must link to the Web motion technical contract");
  await waitForPrivateReference(page);
  await page.setViewportSize({ width: 390, height: 844 });
  const swosMobileWidth = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
  assert(swosMobileWidth.document <= 390 && swosMobileWidth.body <= 390, `SWOS workspace overflows the mobile viewport: ${JSON.stringify(swosMobileWidth)}`);
  await page.screenshot({ path: resolve(args.outputDir, "swos-static-visual-390.png"), fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });

  const webHifiHash = "#page/recognition_flow_v1_2/hifi/web_hifi_contract_v2";
  await open(webHifiHash);
  await waitForHifiView(page);
  assert(page.url().endsWith(webHifiHash), "Web Hi-Fi V2 direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("Web AI渔获识别"), "Web Hi-Fi V2 menu title is missing");
  assert((await page.locator("#hifiViewAuthorities").innerText()).includes("WEB_HIFI_CONTRACT_V2.md"), "Web Hi-Fi V2 authority link missing");
  assert((await page.locator("#hifiViewCanvas").innerText()).includes("像素基准已冻结（私有源）"), "Web private pixel baseline status missing");
  assert((await page.locator("#hifiViewCanvas").innerText()).includes("27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2"), "Web private pixel baseline SHA missing");
  assert(await page.locator('#hifiViewContractDetails a[href*="VISUAL_BASELINE_FREEZE_V1.md"]').count() === 1, "Web visual baseline freeze record link missing");
  assert(await page.locator('#hifiViewContractDetails a[href*="Responsive_Rules_V2.json"]').count() === 1, "Web responsive contract link missing");
  await page.screenshot({ path: resolve(args.outputDir, "web-hifi-v2-1440.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  const webMobileWidth = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
  assert(webMobileWidth.document <= 390 && webMobileWidth.body <= 390, `Web contract workspace overflows mobile: ${JSON.stringify(webMobileWidth)}`);
  await page.setViewportSize({ width: 1440, height: 1000 });

  const swosWebMotionHash = "#page/recognition_flow_v1_2/hifi/swos_web_hifi_motion_contract_v1_0";
  await open(swosWebMotionHash);
  await waitForHifiView(page);
  assert(page.url().endsWith(swosWebMotionHash), "SWOS Web motion technical contract direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("SWOS Web 高保真动效技术合同 V1.0"), "SWOS Web motion contract title is missing");
  assert((await page.locator("#hifiViewAuthorities").innerText()).includes("SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md"), "SWOS Web motion contract authority link is missing");
  assert(await page.locator('#hifiViewContractDetails .hifi-contract-links a[href*="SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.json"]').count() === 1, "SWOS Web motion machine contract link is missing from the Contracts section");
  assert(await page.locator('.hifi-related-routes a[href="' + swosStaticHash + '"]').count() === 1, "SWOS Web motion contract must link to the static 15-frame workspace");
  assert((await page.locator("#hifiViewCanvas").innerText()).includes("SAM"), "SWOS Web motion contract must state existing SAM experiment reuse");
  await page.screenshot({ path: resolve(args.outputDir, "swos-web-motion-contract-1440.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  const swosMotionMobileWidth = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
  assert(swosMotionMobileWidth.document <= 390 && swosMotionMobileWidth.body <= 390, "SWOS Web motion contract overflows mobile: " + JSON.stringify(swosMotionMobileWidth));
  await page.screenshot({ path: resolve(args.outputDir, "swos-web-motion-contract-390.png"), fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });

  const continuityHash = "#page/recognition_flow_v1_2/hifi/processing_result_continuity";
  await open(continuityHash);
  assert(page.url().endsWith(continuityHash), "Processing → Result continuity direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("光学接续"), "continuity menu title is missing");
  assert((await page.locator("#hifiViewAuthorities").innerText()).includes("Processing_Result_Optical_Continuity_Contract_V1.md"), "continuity authority link is missing");
  await page.screenshot({ path: resolve(args.outputDir, "processing-result-continuity-1440.png"), fullPage: true });

  const rr09V3Hash = "#page/recognition_result_v1/hifi/motion_continuation";
  await open(rr09V3Hash);
  assert(page.url().endsWith(rr09V3Hash), "RR09 V3 direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("V3"), "current RR09 menu must identify V3");
  assert((await page.locator("#hifiViewAuthorities").innerText()).includes("Recognition_Result_Motion_Spec_V3.md"), "current RR09 V3 authority link is missing");
  assert(await page.locator("#hifiViewCanvas iframe").count() === 0, "current RR09 V3 must not render the legacy V2 playable prototype");
  await page.screenshot({ path: resolve(args.outputDir, "rr09-v3-authority-1440.png"), fullPage: true });

  const rr09V2HistoryHash = "#page/recognition_result_v1/hifi/motion_continuation_v2_history";
  await open(rr09V2HistoryHash);
  assert(page.url().endsWith(rr09V2HistoryHash), "RR09 V2 history direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("V2 历史基线"), "RR09 V2 history label is missing");
  assert((await page.locator("#hifiViewStatus").innerText()).includes("已废弃"), "RR09 V2 history must remain DEPRECATED");
  assert((await page.locator("#hifiViewCanvas").innerText()).includes("历史对照"), "RR09 V2 page must explain that it is historical only");
  const historicalMotionFrame = page.locator("#hifiViewCanvas iframe");
  assert(await historicalMotionFrame.count() === 1, "RR09 V2 historical preview must remain separately reachable");
  // The historical motion page is intentionally embedded. Wait for its document
  // to finish loading before navigating to the next Hash route; otherwise Chromium
  // reports the expected iframe teardown as net::ERR_ABORTED and the global
  // failed-request gate misclassifies a normal route transition as a product error.
  await page.waitForFunction(() => {
    const frame = document.querySelector("#hifiViewCanvas iframe");
    if (!frame) return false;
    try {
      const frameDocument = frame.contentDocument;
      return Boolean(frameDocument && frameDocument.readyState === "complete" && frameDocument.querySelector("main.workspace"));
    } catch (_) {
      return false;
    }
  }, null, { timeout: 30000 });
  await page.screenshot({ path: resolve(args.outputDir, "rr09-v2-history-1440.png"), fullPage: true });


  const fishtraceSitesHash = "#page/recognition_flow_v1_2/hifi/fishtrace_sites_web_hifi_workspace";
  await open(fishtraceSitesHash);
  assert(page.url().endsWith(fishtraceSitesHash), "FishTrace Sites high-fidelity workspace direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("FishTrace Sites"), "FishTrace Sites prototype title is missing");
  const fishtraceFrameSelector = 'iframe[title="FishTrace Sites Web 高保真交互体验 V1.1"]';
  await page.locator(fishtraceFrameSelector).waitFor({ state: "visible", timeout: 30000 });
  await page.waitForFunction(selector => {
    const frame = document.querySelector(selector);
    try {
      return Boolean(frame && frame.contentDocument && frame.contentDocument.readyState === "complete" &&
        frame.contentDocument.querySelector('[data-testid="fishtrace-sites-web-workspace"]'));
    } catch (_) { return false; }
  }, fishtraceFrameSelector, { timeout: 30000 });
  const fishtraceFrame = page.frame({ url: /fishtrace_sites_web_workspace.html/ });
  assert(Boolean(fishtraceFrame), "FishTrace Sites prototype iframe document is not reachable");
  await fishtraceFrame.locator('[data-testid="fishtrace-sites-web-workspace"]').waitFor({ state: "visible", timeout: 30000 });

  const waitForFreshFishtraceFrame = async targetPage => {
    await targetPage.locator(fishtraceFrameSelector).waitFor({state:"visible",timeout:30000});
    await targetPage.locator(fishtraceFrameSelector).scrollIntoViewIfNeeded();
    await targetPage.waitForFunction(selector => {
      const frame = document.querySelector(selector);
      try {
        return Boolean(frame && frame.contentDocument && frame.contentDocument.readyState === "complete" &&
          frame.contentDocument.querySelector('[data-testid="fishtrace-sites-web-workspace"]'));
      } catch (_) { return false; }
    },fishtraceFrameSelector,{timeout:30000});
    const targetFrame = targetPage.frame({url:/fishtrace_sites_web_workspace\.html/});
    assert(Boolean(targetFrame),"FishTrace workspace iframe did not finish loading in the browser context");
    await targetFrame.locator('[data-testid="fishtrace-sites-web-workspace"]').waitFor({state:"visible",timeout:30000});
    return targetFrame;
  };
  assert((await fishtraceFrame.locator(".prototype-warning").innerText()).includes("不调用真实模型"), "FishTrace Sites prototype demo-only disclaimer is missing");
  assert((await fishtraceFrame.locator(".product-device").getAttribute("data-device")) === "desktop", "FishTrace Sites prototype must start in desktop preview");
  await fishtraceFrame.locator('[data-action="start"]').click();
  assert(await fishtraceFrame.locator('[data-scene="source-choice"]').isVisible(), "Entry CTA must show source choice first");
  assert(await fishtraceFrame.locator('.source-options .source-card[data-layer-id="camera-card"]').isVisible(), "Camera source option is missing");
  assert(await fishtraceFrame.locator('.source-options .source-card[data-layer-id="gallery-card"]').isVisible(), "Gallery source option is missing");
  const localPhotoRequestStart = requests.length;
  const galleryChooserPromise = page.waitForEvent("filechooser");
  await fishtraceFrame.locator('[data-action="gallery"]').first().click();
  const galleryChooser = await galleryChooserPromise;
  await galleryChooser.setFiles(resolve(args.root, "design/system/fish_media_display_v1/assets/real_catch_source.jpg"));
  await fishtraceFrame.waitForFunction(() => document.querySelector("#photo-source-label")?.textContent === "本机选择", null, { timeout: 30000 });
  await fishtraceFrame.waitForFunction(() => {
    const image = document.querySelector("#photo-foreground-preview");
    return Boolean(image && image.complete && image.naturalWidth > 0 && image.currentSrc.startsWith("blob:"));
  }, null, { timeout: 30000 });
  const localPhotoRequests = requests.slice(localPhotoRequestStart).filter(item =>
    item.method === "POST" || item.url.includes("/api/product/catch-recognition/infer")
  );
  assert(localPhotoRequests.length === 0, "FishTrace Sites design prototype must not upload local photos or call the inference API");
  assert(await fishtraceFrame.locator('[data-scene="preview"]').isVisible(), "Gallery selection must converge to the shared photo preview");
  await fishtraceFrame.locator('.layer-node[data-layer-id="photo-foreground"]').click();
  assert(await fishtraceFrame.locator('[data-inspector-view="properties"]').isVisible(), "Selecting a photo layer must open its property inspector");
  assert((await fishtraceFrame.locator("#selected-layer-label").innerText()).includes("完整原图"), "Selected layer label must identify the full foreground photo");
  await fishtraceFrame.locator('.state-tab[data-state="result"]').click();
  assert(await fishtraceFrame.locator('[data-scene="result"]').isVisible(), "Result state must be inspectable");
  assert((await fishtraceFrame.locator('[data-scene="result"] .demo-chip').innerText()).includes("样例数据"), "Result must label the sample data explicitly");
  assert((await fishtraceFrame.locator('[data-scene="result"] .result-disclaimer').innerText()).includes("视觉样例"), "Result must not imply real model inference");
  const desktopResultColumns = await fishtraceFrame.locator('[data-scene="result"] .result-layout').evaluate(node => getComputedStyle(node).gridTemplateColumns);
  assert(desktopResultColumns.includes(" "), "Desktop result must render photo-left/result-right columns");
  await page.screenshot({ path: resolve(args.outputDir, "fishtrace-sites-web-hifi-desktop.png"), fullPage: true });
  await fishtraceFrame.locator('button[data-device="phone"]').click();
  assert((await fishtraceFrame.locator(".product-device").getAttribute("data-device")) === "phone", "Phone viewport toggle did not activate");
  const phoneResultColumns = await fishtraceFrame.locator('[data-scene="result"] .result-layout').evaluate(node => getComputedStyle(node).gridTemplateColumns);
  assert(!phoneResultColumns.includes(" "), "Phone result must stack photo above result");
  await page.screenshot({ path: resolve(args.outputDir, "fishtrace-sites-web-hifi-phone.png"), fullPage: true });
  await fishtraceFrame.locator('.state-tab[data-state="processing"]').click();
  await fishtraceFrame.locator('[data-frame="P4"]').click();
  assert((await fishtraceFrame.locator("#processing-status-copy").innerText()).includes("P4"), "Processing timeline frame selection did not update status");
  assert((await fishtraceFrame.locator("#timeline-inspector .timeline-note").innerText()).includes("NONE"), "Prototype must state that the fixture has no trusted Mask");
  await page.screenshot({ path: resolve(args.outputDir, "fishtrace-sites-web-hifi-processing.png"), fullPage: true });
  await fishtraceFrame.locator('button[data-device="desktop"]').click();
  await fishtraceFrame.locator('.state-tab[data-state="reveal"]').click();
  assert(await fishtraceFrame.locator('[data-scene="reveal"]').isVisible(), "RR09 reveal state is missing");
  await fishtraceFrame.locator('[data-rr09-frame="R4"]').click();
  assert((await fishtraceFrame.locator("#rr09-status-copy").innerText()).includes("R4"), "RR09 R4 frame selection did not update status");
  assert((await fishtraceFrame.locator(".rr09-timeline-inspector .timeline-note").innerText()).includes("NONE"), "RR09 reveal must disclose missing trusted Mask");
  await fishtraceFrame.waitForFunction(() => {
    const node = document.querySelector("#reveal-species-title");
    return Boolean(node && Number(getComputedStyle(node).opacity) > 0.7);
  }, null, { timeout: 3000 });
  const revealTitleOpacity = await fishtraceFrame.locator("#reveal-species-title").evaluate(node => getComputedStyle(node).opacity);
  assert(Number(revealTitleOpacity) > 0.7, "RR09 R4 should reveal the Chinese title fallback");
  await page.screenshot({ path: resolve(args.outputDir, "fishtrace-sites-web-hifi-reveal.png"), fullPage: true });
  await page.emulateMedia({ reducedMotion: "no-preference" });
  assert(!(await fishtraceFrame.locator("body").evaluate(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches)), "Full-motion acceptance must run with reduced motion disabled");
  await fishtraceFrame.locator('.state-tab[data-state="preview"]').click();
  await fishtraceFrame.locator('[data-scene="preview"] [data-action="recognize"]').click();
  await fishtraceFrame.locator('[data-scene="result"]').waitFor({ state: 'visible', timeout: 5000 });
  const transitionPhotoSource = await fishtraceFrame.locator('#photo-transition-image').evaluate(node => node.currentSrc);
  const revealPhotoSource = await fishtraceFrame.locator('#photo-foreground-reveal').evaluate(node => node.currentSrc);
  assert(transitionPhotoSource && transitionPhotoSource === revealPhotoSource, "RR09 photo transition must reuse the same source photo");
  const transitionRendered = await fishtraceFrame.locator('#photo-transition-overlay').evaluate(node => node.dataset.transitionRendered === 'true');
  assert(transitionRendered, "The full playback must execute the photo-geometry transition renderer");
  assert(await fishtraceFrame.locator('#photo-transition-overlay').isHidden(), "Photo transition overlay must be removed after R1");
  await page.screenshot({ path: resolve(args.outputDir, "fishtrace-sites-web-hifi-continuous-result.png"), fullPage: true });
  await fishtraceFrame.locator('.state-tab[data-state="error"]').click();
  assert(await fishtraceFrame.locator('[data-scene="error"]').isVisible(), "Error recovery state is missing");
  assert(await fishtraceFrame.locator(".recovery-actions button").count() === 2, "Error recovery must expose retry and source replacement actions");
  await page.screenshot({ path: resolve(args.outputDir, "fishtrace-sites-web-hifi-error.png"), fullPage: true });
  await fishtraceFrame.locator('[data-action="reset"]').click();
  assert(await fishtraceFrame.locator('[data-scene="entry"]').isVisible(), "Prototype reset must return to entry");
  await fishtraceFrame.waitForFunction(() => [...document.querySelectorAll(".photo-foreground")].every(image => image.complete && image.naturalWidth > 0), null, { timeout: 30000 });


  const waterTab = fishtraceFrame.locator('[data-water-view="canvas-water"]');
  const waterContract = JSON.parse(await readFile(resolve(args.root,"design/pages/recognition/web/candidates/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json"),"utf8"));
  await fishtraceFrame.locator("#photo-transition-overlay").evaluate(node => {node.hidden=false; node.style.pointerEvents="none"; node.style.width="0px"; node.style.height="0px"; node.dataset.transitionRendered="true";});
  await waterTab.click();
  assert(await fishtraceFrame.locator("#photo-transition-overlay").isHidden(), "Opening Canvas Water Lab must clear any stale photo-transition overlay");
  const waterLab = fishtraceFrame.locator('[data-testid="canvas-water-lab"]');
  await waterLab.waitFor({state:"visible",timeout:10000});
  await fishtraceFrame.waitForFunction(() => {
    const lab = document.querySelector('[data-testid="canvas-water-lab"]');
    const image = document.querySelector("#water-source-image");
    const canvas = document.querySelector("#water-overlay");
    return lab?.dataset.rendererStatus === "READY" && image?.dataset.assetVerified === "true" &&
      canvas?.dataset.rendererStatus === "READY" && canvas.width > 1 && canvas.height > 1;
  }, null, {timeout:30000});
  const causticModelState=await fishtraceFrame.locator("#water-overlay").evaluate(canvas=>({model:canvas.dataset.causticGeometryModel,patchCount:Number(canvas.dataset.causticPatchCount),directionBins:Number(canvas.dataset.causticDirectionBins),nearHorizontalFraction:Number(canvas.dataset.causticNearHorizontalFraction)}));
  assert(causticModelState.model==="a_line_organic_face_patches_v1"&&causticModelState.patchCount===18,"V5 must render a deterministic local organic-patch field; observed "+JSON.stringify(causticModelState));
  assert(causticModelState.directionBins>=5&&causticModelState.nearHorizontalFraction<=.45,"V5 patch directions must not collapse into parallel horizontal bands; observed "+JSON.stringify(causticModelState));
  const waterAsset = await fishtraceFrame.locator("#water-source-image").evaluate(image => ({
    naturalWidth:image.naturalWidth,naturalHeight:image.naturalHeight,
    sha256:image.dataset.sha256,src:image.currentSrc,complete:image.complete
  }));
  assert(waterAsset.naturalWidth === 1152 && waterAsset.naturalHeight === 1536, "Canvas Water Lab must load the verified 1152×1536 fixed photo");
  assert(waterAsset.sha256 === "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef", "Canvas Water Lab fixed photo SHA-256 mismatch");
  const waterStageStructure = await fishtraceFrame.locator("#water-stage").evaluate(node => ({
    children:[...node.children].map(child => child.tagName.toLowerCase()),
    text:node.textContent.trim(),
    objectFit:getComputedStyle(document.querySelector("#water-source-image")).objectFit
  }));
  assert(waterStageStructure.children.join(",") === "img,canvas" && waterStageStructure.text === "", "Photo stage must contain only the ordinary image and transparent Canvas with no in-photo labels/HUD");
  assert(waterStageStructure.objectFit === "contain", "Fixed photo must use object-fit:contain");
  const waterNetworkStart = requests.length;
  const waterScreenshots = [];
  const waterCanvasState = async () => fishtraceFrame.locator("#water-overlay").evaluate(canvas => {
    const context = canvas.getContext("2d");
    const pixels = context.getImageData(0,0,canvas.width,canvas.height).data;
    let visible = 0;
    let outside = 0;
    let outsideActiveZone = 0;
    const rect = JSON.parse(canvas.dataset.contentRect);
    const dpr = Number(canvas.dataset.dpr || 1);
    const zones=JSON.parse(canvas.dataset.waterRegionZones);
    const activeMask=document.createElement("canvas");activeMask.width=canvas.width;activeMask.height=canvas.height;
    const maskContext=activeMask.getContext("2d");maskContext.setTransform(dpr,0,0,dpr,0,0);
    const activePath=new Path2D();
    for(const polygons of [zones.A,zones.B,zones.C])for(const polygon of polygons){
      polygon.points.forEach((point,index)=>{
        const x=rect.x+point[0]*rect.width,y=rect.y+point[1]*rect.height;
        if(index===0)activePath.moveTo(x,y);else activePath.lineTo(x,y);
      });
      activePath.closePath();
    }
    maskContext.fill(activePath,"nonzero");
    const mask=maskContext.getImageData(0,0,canvas.width,canvas.height).data;
    for (let pixel=0;pixel<pixels.length;pixel+=4) {
      if (pixels[pixel+3] === 0) continue;
      visible += 1;
      const index = pixel/4;
      const x = (index % canvas.width)/dpr;
      const y = Math.floor(index/canvas.width)/dpr;
      if (x < rect.x-1 || y < rect.y-1 || x > rect.x+rect.width+1 || y > rect.y+rect.height+1) outside += 1;
      if(mask[pixel+3]===0)outsideActiveZone++;
    }
    return {visible,outside,outside_active_zone_alpha_pixels:outsideActiveZone,width:canvas.width,height:canvas.height,dpr,rect,time:canvas.dataset.currentTime};
  });
  const waterStageShot = async name => {
    await fishtraceFrame.locator("#water-stage").screenshot({path:resolve(args.outputDir,name)});
    waterScreenshots.push(name);
  };
  const regionDebugToggle=fishtraceFrame.locator("#water-region-debug");
  assert(!(await regionDebugToggle.isChecked()), "Water-region debug outlines must be off in normal playback by default");
  await regionDebugToggle.check();
  await fishtraceFrame.locator('[data-water-frame="P3"]').click();
  const debugOverlayState=await fishtraceFrame.locator("#water-overlay").evaluate(canvas=>({enabled:canvas.dataset.debugRegion,version:canvas.dataset.waterRegionVersion,polygons:JSON.parse(canvas.dataset.waterRegionPolygons),zones:JSON.parse(canvas.dataset.waterRegionZones),validation_status:document.querySelector('[data-testid="canvas-water-lab"]')?.dataset.waterRegionValidation}));
  assert(debugOverlayState.enabled==="true"&&debugOverlayState.polygons.length===2&&debugOverlayState.zones.A.length===2&&debugOverlayState.zones.B.length===1&&debugOverlayState.zones.C.length===1, "The development-only normalized A/B/C region overlay must expose two A polygons, one B polygon and one C polygon");
  await waterStageShot("fishtrace-canvas-water-region-debug.png");
  await regionDebugToggle.uncheck();
  assert((await fishtraceFrame.locator("#water-overlay").getAttribute("data-debug-region"))==="false", "The normal photo preview must have no water-region debug contour");

  const measureComposite=async label=>fishtraceFrame.locator("#water-stage").evaluate((stage,label)=>{
    const image=document.querySelector("#water-source-image");
    const overlay=document.querySelector("#water-overlay");
    const rect=JSON.parse(overlay.dataset.contentRect);
    const zones=JSON.parse(overlay.dataset.waterRegionZones);
    const source=JSON.parse(overlay.dataset.sourceDimensions);
    const width=Math.max(1,Math.round(stage.clientWidth));
    const height=Math.max(1,Math.round(stage.clientHeight));
    const reference=document.createElement("canvas");reference.width=width;reference.height=height;
    const composite=document.createElement("canvas");composite.width=width;composite.height=height;
    const aMaskCanvas=document.createElement("canvas");aMaskCanvas.width=width;aMaskCanvas.height=height;
    const activeMaskCanvas=document.createElement("canvas");activeMaskCanvas.width=width;activeMaskCanvas.height=height;
    const zoneMasks={A:aMaskCanvas,B:document.createElement("canvas"),C:document.createElement("canvas")};
    for(const canvas of [zoneMasks.B,zoneMasks.C]){canvas.width=width;canvas.height=height;}
    const refContext=reference.getContext("2d"),compositeContext=composite.getContext("2d");
    for(const ctx of [refContext,compositeContext])ctx.drawImage(image,rect.x,rect.y,rect.width,rect.height);
    compositeContext.globalCompositeOperation="screen";
    compositeContext.drawImage(overlay,0,0,width,height);
    compositeContext.globalCompositeOperation="source-over";
    const makePath=polygons=>{
      const path=new Path2D();
      for(const polygon of polygons){
        polygon.points.forEach((point,index)=>{
          const x=rect.x+point[0]*rect.width;
          const y=rect.y+point[1]*rect.height;
          if(index===0)path.moveTo(x,y);else path.lineTo(x,y);
        });
        path.closePath();
      }
      return path;
    };
    const paths={A:makePath(zones.A),B:makePath(zones.B),C:makePath(zones.C)};
    const activePath=new Path2D();for(const name of ["A","B","C"])activePath.addPath(paths[name]);
    for(const name of ["A","B","C"]){
      const context=zoneMasks[name].getContext("2d");context.fill(paths[name],"nonzero");
    }
    const activeContext=activeMaskCanvas.getContext("2d");activeContext.fill(activePath,"nonzero");
    const ref=refContext.getImageData(0,0,width,height).data;
    const out=compositeContext.getImageData(0,0,width,height).data;
    const aMask=zoneMasks.A.getContext("2d").getImageData(0,0,width,height).data;
    const activeMask=activeContext.getImageData(0,0,width,height).data;
    const zonePixelData=Object.fromEntries(["A","B","C"].map(name=>[name,zoneMasks[name].getContext("2d").getImageData(0,0,width,height).data]));
    const diffMapPixels=compositeContext.createImageData(width,height);
    let roiPixels=0,activePixels=0,forbiddenPixels=0,channelDiffSum=0,waterPixelsDiff2=0,waterPixelsDiff6=0;
    let forbiddenLeakPixels2=0,forbiddenLeakPixels4=0,forbiddenDiffSum=0,maxDiff=0,changedPixels=0;
    let diffPixels=0;
    const zoneStats=Object.fromEntries(["A","B","C"].map(name=>[name,{pixels:0,diff_sum:0,changed_pixels:0}]));
    const frameData=new Uint8ClampedArray(out);
    for(let i=0;i<out.length;i+=4){
      const x=(i/4)%width,y=Math.floor((i/4)/width);
      if(x<rect.x||y<rect.y||x>=rect.x+rect.width||y>=rect.y+rect.height)continue;
      const dr=Math.abs(out[i]-ref[i]),dg=Math.abs(out[i+1]-ref[i+1]),db=Math.abs(out[i+2]-ref[i+2]);
      const peak=Math.max(dr,dg,db);maxDiff=Math.max(maxDiff,peak);
      if(activeMask[i+3]>16)activePixels++;
      if(aMask[i+3]>16){
        roiPixels++;channelDiffSum+=(dr+dg+db)/3;
        if(peak>=2)waterPixelsDiff2++;
        if(peak>=6)waterPixelsDiff6++;
        if(peak>0)diffPixels++;
      }
      if(activeMask[i+3]<=16){
        forbiddenPixels++;forbiddenDiffSum+=(dr+dg+db)/3;
        if(peak>2)forbiddenLeakPixels2++;
        if(peak>4)forbiddenLeakPixels4++;
        if(peak>4){diffMapPixels.data[i]=255;diffMapPixels.data[i+1]=20;diffMapPixels.data[i+2]=35;diffMapPixels.data[i+3]=255;}
      }
      for(const name of ["A","B","C"]){
        const mask=zonePixelData[name];
        if(mask[i+3]>16){zoneStats[name].pixels++;zoneStats[name].diff_sum+=(dr+dg+db)/3;if(peak>0)zoneStats[name].changed_pixels++;}
      }
      if(activeMask[i+3]>16&&peak>=2){
        diffMapPixels.data[i]=Math.min(255,dr*4+14);
        diffMapPixels.data[i+1]=Math.min(255,dg*4+82);
        diffMapPixels.data[i+2]=Math.min(255,db*4+44);
        diffMapPixels.data[i+3]=255;
      }
    }
    const diffMapCanvas=document.createElement("canvas");diffMapCanvas.width=width;diffMapCanvas.height=height;
    diffMapCanvas.getContext("2d").putImageData(diffMapPixels,0,0);
    const frames=window.__fishtraceWaterCompositeFrames||(window.__fishtraceWaterCompositeFrames={});
    frames[label]={data:frameData,mask:new Uint8ClampedArray(aMask),width,height};
    let p2p3=null;
    if(label==="P3"&&frames.P2){
      let sum=0,changed=0,p2Sum=0,p3Sum=0;
      const referencePixels=window.__fishtraceWaterReferencePixels;
      for(let i=0;i<out.length;i+=4){
        if(aMask[i+3]<=16)continue;
        const delta=Math.max(Math.abs(frames.P2.data[i]-out[i]),Math.abs(frames.P2.data[i+1]-out[i+1]),Math.abs(frames.P2.data[i+2]-out[i+2]));
        sum+=delta;if(delta>=3)changed++;
        if(referencePixels){
          p2Sum+=Math.max(Math.abs(frames.P2.data[i]-referencePixels[i]),Math.abs(frames.P2.data[i+1]-referencePixels[i+1]),Math.abs(frames.P2.data[i+2]-referencePixels[i+2]));
          p3Sum+=Math.max(Math.abs(out[i]-referencePixels[i]),Math.abs(out[i+1]-referencePixels[i+1]),Math.abs(out[i+2]-referencePixels[i+2]));
        }
      }
      let structureChanged=0;
      if(referencePixels&&p2Sum>0&&p3Sum>0){
        for(let i=0;i<out.length;i+=4){
          if(aMask[i+3]<=16)continue;
          const p2=Math.max(Math.abs(frames.P2.data[i]-referencePixels[i]),Math.abs(frames.P2.data[i+1]-referencePixels[i+1]),Math.abs(frames.P2.data[i+2]-referencePixels[i+2]))*roiPixels/p2Sum;
          const p3=Math.max(Math.abs(out[i]-referencePixels[i]),Math.abs(out[i+1]-referencePixels[i+1]),Math.abs(out[i+2]-referencePixels[i+2]))*roiPixels/p3Sum;
          if(Math.abs(p2-p3)>=.15)structureChanged++;
        }
      }
      p2p3={mean_max_channel_diff:sum/roiPixels,changed_pixel_ratio_gte_3:changed/roiPixels,normalized_structure_pixel_ratio_abs_delta_gte_0_15:structureChanged/roiPixels,p2_normalized_mean_channel_delta:p2Sum/roiPixels,p3_normalized_mean_channel_delta:p3Sum/roiPixels};
    }
    if(label==="P0")window.__fishtraceWaterReferencePixels=new Uint8ClampedArray(ref);
    const zonesOut=Object.fromEntries(["A","B","C"].map(name=>[name,{pixels:zoneStats[name].pixels,mean_rgb_abs_diff_255:zoneStats[name].pixels?zoneStats[name].diff_sum/zoneStats[name].pixels/255:0,changed_pixel_ratio:zoneStats[name].pixels?zoneStats[name].changed_pixels/zoneStats[name].pixels:0}]));
    return {
      frame:label,display_pixels:{width,height},water_roi_pixels:roiPixels,primary_region_A_pixels:roiPixels,active_zone_pixels:activePixels,forbidden_zone_pixels:forbiddenPixels,
      water_roi_mean_rgb_abs_diff_255:roiPixels?channelDiffSum/roiPixels/255:0,
      water_roi_mean_rgb_abs_diff_rgb_code_value:roiPixels?channelDiffSum/roiPixels:0,
      water_roi_pixel_ratio_max_channel_diff_gte_2:roiPixels?waterPixelsDiff2/roiPixels:0,
      water_roi_pixel_ratio_max_channel_diff_gte_6:roiPixels?waterPixelsDiff6/roiPixels:0,
      water_roi_coverage_ratio:roiPixels?diffPixels/roiPixels:0,
      forbidden_zone_mean_rgb_abs_diff_255:forbiddenPixels?forbiddenDiffSum/forbiddenPixels/255:0,
      forbidden_zone_pixel_ratio_peak_diff_gt_4:forbiddenPixels?forbiddenLeakPixels4/forbiddenPixels:0,
      forbidden_zone_pixels_peak_diff_gt_4:forbiddenLeakPixels4,
      non_water_roi_mean_rgb_abs_diff_255:forbiddenPixels?forbiddenDiffSum/forbiddenPixels/255:0,
      non_water_pixels_max_channel_diff_gt_2:forbiddenLeakPixels2,
      max_channel_diff:Math.round(maxDiff),p2_to_p3_spatial_change:p2p3,zones:zonesOut,
      difference_map_png:label==="P3"?diffMapCanvas.toDataURL("image/png"):null
    };
  },label);

  const allWaterFrames=waterContract.timeline.frames.map(frame=>[frame.id,frame.ms,Math.round(frame.layers.caustic*100)]);
  const waterFrameJumpEvidence=[];
  const compositeMetrics={};
  const waterFrameStates={};
  for(const [id,ms,intensity] of allWaterFrames){
    await fishtraceFrame.locator('[data-water-frame="'+id+'"]').click();
    const shownTime=await fishtraceFrame.locator("#water-current-time").innerText();
    const shownIntensity=Number((await fishtraceFrame.locator("#water-debug-intensity").innerText()).replace("%",""));
    assert(shownTime===ms+" ms"&&shownIntensity===intensity,id+" must jump to its exact time and frozen baseline intensity");
    const actualLayerStrengths=JSON.parse(await fishtraceFrame.locator("#water-overlay").getAttribute("data-layer-strengths"));
    const expectedLayerStrengths=waterContract.timeline.frames.find(frame=>frame.id===id).layers;
    for(const channel of ["ambient","caustic","highlight"])assert(Math.abs(actualLayerStrengths[channel]-expectedLayerStrengths[channel])<1e-9,id+" "+channel+" multiplier must match the frozen A-line curve");
    const state=await waterCanvasState();
    const metrics=await measureComposite(id);
    assert(state.outside_active_zone_alpha_pixels===0,id+" Canvas alpha must remain inside the union of A/B/C; got "+state.outside_active_zone_alpha_pixels);
    assert(metrics.non_water_pixels_max_channel_diff_gt_2===0,id+" overlay must not alter any Forbidden Zone pixels; got "+metrics.non_water_pixels_max_channel_diff_gt_2);
    if(id==="P0"||id==="P7")assert(state.visible===0&&metrics.water_roi_mean_rgb_abs_diff_255===0&&metrics.max_channel_diff===0,id+" must be the unchanged original with zero Canvas alpha");
    else assert(state.visible>0&&state.outside===0,id+" must render only inside the photo content");
    await waterStageShot("fishtrace-canvas-water-"+id+".png");
    compositeMetrics[id]=metrics;
    waterFrameStates[id]=state;
    waterFrameJumpEvidence.push({id,ms,intensity_percent:shownIntensity,layer_strengths:actualLayerStrengths,canvas_visible_pixels:state.visible,canvas_alpha_pixels_outside_active_zones:state.outside_active_zone_alpha_pixels,water_roi_mean_rgb_abs_diff_255:metrics.water_roi_mean_rgb_abs_diff_255});
  }
  const continuityCheckTimes=[0,50,300,350,400,900,950,1000,1150,1200,1250,2000,2050,2100,2550,2600,2650,2750,2800];
  const continuityLabels=[];
  for(const timeMs of continuityCheckTimes){
    const label="C"+timeMs;
    await fishtraceFrame.locator("#water-timeline").evaluate((input,value)=>{
      input.value=String(value);
      input.dispatchEvent(new Event("input",{bubbles:true}));
    },timeMs);
    assert((await fishtraceFrame.locator("#water-current-time").innerText())===timeMs+" ms","Continuity sample seek must show exact "+timeMs+"ms");
    await measureComposite(label);
    continuityLabels.push({time_ms:timeMs,label});
  }
  const continuityPairs=[];
  for(let index=1;index<continuityLabels.length;index+=1){
    const before=continuityLabels[index-1],after=continuityLabels[index];
    if(after.time_ms-before.time_ms===50)continuityPairs.push({from:before.label,to:after.label,from_ms:before.time_ms,to_ms:after.time_ms});
  }
  const continuityResults=await fishtraceFrame.evaluate(pairs=>{
    const frames=window.__fishtraceWaterCompositeFrames;
    return pairs.map(pair=>{
      const before=frames[pair.from],after=frames[pair.to];
      let sum=0,count=0,max=0,changed=0;
      for(let i=0;i<before.data.length;i+=4){
        if(before.mask[i+3]<=16)continue;
        const dr=Math.abs(before.data[i]-after.data[i]);
        const dg=Math.abs(before.data[i+1]-after.data[i+1]);
        const db=Math.abs(before.data[i+2]-after.data[i+2]);
        const peak=Math.max(dr,dg,db);
        sum+=(dr+dg+db)/3;count++;max=Math.max(max,peak);
        if(peak>=3)changed++;
      }
      return {from_ms:pair.from_ms,to_ms:pair.to_ms,water_roi_mean_rgb_abs_delta_255:count?sum/count:0,changed_pixel_ratio_gte_3:count?changed/count:0,max_channel_delta:Math.round(max)};
    });
  },continuityPairs);
  const maxContinuityDelta=Math.max(...continuityResults.map(result=>result.water_roi_mean_rgb_abs_delta_255));
  assert(continuityResults.length>=12&&maxContinuityDelta<=6.5,"Canvas composite must change continuously around fixed timing nodes; 50ms ROI mean delta exceeded 6.5/255: "+JSON.stringify(continuityResults));
  await fishtraceFrame.locator('[data-water-frame="P0"]').click();
  const continuityEvidence={sample_times_ms:continuityCheckTimes,pairs:continuityResults,maximum_50ms_mean_rgb_abs_delta_255:maxContinuityDelta,threshold_mean_rgb_abs_delta_255:6.5};

  const p0ScreenshotSha=createHash("sha256").update(await readFile(resolve(args.outputDir,"fishtrace-canvas-water-P0.png"))).digest("hex");
  const p7ScreenshotSha=createHash("sha256").update(await readFile(resolve(args.outputDir,"fishtrace-canvas-water-P7.png"))).digest("hex");
  assert(p0ScreenshotSha===p7ScreenshotSha,"P0 and P7 actual browser stage screenshots must be byte-identical when the water layer has fully exited");
  const waterComparisonPng=await fishtraceFrame.evaluate(()=>{
    const frames=window.__fishtraceWaterCompositeFrames;
    const labels=["P0 · 原图","P2 · 950ms","P3 · 1200ms","P5 · 2050ms"];
    const samples=[frames.P0,frames.P2,frames.P3,frames.P5];
    const width=samples[0].width,height=samples[0].height;
    const montage=document.createElement("canvas");montage.width=width*4;montage.height=height+30;
    const context=montage.getContext("2d");context.fillStyle="#fff";context.fillRect(0,0,montage.width,montage.height);
    for(let index=0;index<samples.length;index++){
      const tile=document.createElement("canvas");tile.width=width;tile.height=height;
      tile.getContext("2d").putImageData(new ImageData(samples[index].data,width,height),0,0);
      context.drawImage(tile,index*width,30);
      context.fillStyle="#254b45";context.font="600 14px sans-serif";context.fillText(labels[index],index*width+8,20);
    }
    return montage.toDataURL("image/png");
  });
  await writeFile(resolve(args.outputDir,"fishtrace-canvas-water-comparison-original-P2-P3-P5.png"),Buffer.from(waterComparisonPng.split(",")[1],"base64"));
  const p0Water=waterFrameStates.P0,p2Water=waterFrameStates.P2,p3Water=waterFrameStates.P3,p5Water=waterFrameStates.P5,p7Water=waterFrameStates.P7;
  const p2Intensity=waterContract.timeline.frames.find(frame=>frame.id==="P2").layers.caustic*100;
  const p3Intensity=waterContract.timeline.frames.find(frame=>frame.id==="P3").layers.caustic*100;
  const roiMean255=frame=>compositeMetrics[frame].water_roi_mean_rgb_abs_diff_255*255;
  const regionARanges=waterContract.acceptance.region_a_mean_rgb_abs_diff_255_target;
  for(const [frame,[min,max]] of Object.entries(regionARanges))assert(roiMean255(frame)>=min&&roiMean255(frame)<=max,frame+" Region A mean RGB difference must be "+min+"–"+max+" /255; observed "+roiMean255(frame).toFixed(3));
  assert(compositeMetrics.P3.water_roi_pixel_ratio_max_channel_diff_gte_6>=0.10,"At least 10% of the P3 Region A ROI must change by 6/255 or more");
  for(const [frame,metrics] of Object.entries(compositeMetrics)){
    assert(metrics.forbidden_zone_mean_rgb_abs_diff_255<waterContract.acceptance.forbidden_zone_mean_rgb_abs_diff_255_max/255,frame+" Forbidden Zone mean RGB difference must stay below the contract threshold");
    assert(metrics.forbidden_zone_pixel_ratio_peak_diff_gt_4<waterContract.acceptance.forbidden_zone_pixels_with_peak_diff_gt_4_fraction_max,frame+" Forbidden Zone pixels above 4/255 must stay below the contract threshold");
  }
  assert(compositeMetrics.P3.water_roi_mean_rgb_abs_diff_255>compositeMetrics.P2.water_roi_mean_rgb_abs_diff_255,"P3 must have a stronger composited water field than P2");
  assert(compositeMetrics.P3.p2_to_p3_spatial_change?.changed_pixel_ratio_gte_3>0.10,"P2 to P3 must change the spatial light field, not only its reported intensity");
  assert(compositeMetrics.P3.p2_to_p3_spatial_change?.normalized_structure_pixel_ratio_abs_delta_gte_0_15>0.10,"P2 to P3 normalized light maps must differ spatially after removing global intensity scaling");
  assert(compositeMetrics.P5.water_roi_mean_rgb_abs_diff_255<compositeMetrics.P3.water_roi_mean_rgb_abs_diff_255&&compositeMetrics.P5.water_roi_mean_rgb_abs_diff_255>0,"P5 water light must remain visible but weaker than P3");
  assert(compositeMetrics.P6.water_roi_mean_rgb_abs_diff_255<compositeMetrics.P5.water_roi_mean_rgb_abs_diff_255,"P6 must further reduce the water light toward exit");
  const captureP2CanvasHash=async()=>{
    await fishtraceFrame.locator('[data-water-frame="P2"]').click();
    return fishtraceFrame.locator("#water-overlay").evaluate(async node=>{
      const canvas=node,context=canvas.getContext("2d");
      const pixels=context.getImageData(0,0,canvas.width,canvas.height).data;
      const digest=await crypto.subtle.digest("SHA-256",pixels);
      const sha256=Array.from(new Uint8Array(digest),byte=>byte.toString(16).padStart(2,"0")).join("");
      let visiblePixels=0;
      for(let i=3;i<pixels.length;i+=4)if(pixels[i])visiblePixels++;
      return {time_ms:Number(canvas.dataset.currentTime),width:canvas.width,height:canvas.height,sha256,visible_pixels:visiblePixels};
    });
  };
  const p2Repeat=await captureP2CanvasHash();
  assert(p2Repeat.time_ms===950&&p2Repeat.visible_pixels>0,"The first determinism sample must be a visible, exact P2 frame");
  const p2RepeatAgain=await captureP2CanvasHash();
  assert(p2RepeatAgain.time_ms===950&&p2Repeat.sha256===p2RepeatAgain.sha256&&p2Repeat.width===p2RepeatAgain.width&&p2Repeat.height===p2RepeatAgain.height,"Repeated P2 seeks must reproduce the same deterministic Canvas pixels and backing dimensions");
  const p3FullMotionPixels=await fishtraceFrame.locator("#water-overlay").evaluate(node=>{
    const button=document.querySelector('[data-water-frame="P3"]');button.click();return node.toDataURL("image/png");
  });
  const p3ShownIntensity=Number((await fishtraceFrame.locator("#water-debug-intensity").innerText()).replace("%",""));
  assert(p3ShownIntensity===p3Intensity,"P3 intensity readout must reflect the A-line caustic channel value from the candidate contract");
  const p3DiffMapData=compositeMetrics.P3.difference_map_png.split(",")[1];
  await writeFile(resolve(args.outputDir,"fishtrace-canvas-water-diff-map-P3.png"),Buffer.from(p3DiffMapData,"base64"));
  await page.setViewportSize({width:390,height:844});
  await fishtraceFrame.locator('[data-water-frame="P3"]').click();
  await waterStageShot("fishtrace-canvas-water-mobile-390.png");
  await page.setViewportSize({width:430,height:932});
  await fishtraceFrame.locator('[data-water-frame="P3"]').click();
  await waterStageShot("fishtrace-canvas-water-mobile-430.png");
  await page.setViewportSize({width:1440,height:1000});

  await fishtraceFrame.locator('[data-water-frame="P0"]').click();
  await fishtraceFrame.locator("#water-play").click();
  await fishtraceFrame.waitForFunction(() => Number(document.querySelector("#water-current-time")?.textContent.match(/\d+/)?.[0]) > 0, null, {timeout:2000});
  await fishtraceFrame.locator("#water-pause").click();
  const pausedWaterTime = await fishtraceFrame.locator("#water-current-time").innerText();
  await fishtraceFrame.waitForTimeout(180);
  assert((await fishtraceFrame.locator("#water-current-time").innerText()) === pausedWaterTime, "Pause must freeze the displayed time and Canvas scene");
  await fishtraceFrame.locator("#water-timeline").evaluate(input => {
    input.value = "1375";
    input.dispatchEvent(new Event("input",{bubbles:true}));
  });
  assert((await fishtraceFrame.locator("#water-current-time").innerText()) === "1375 ms", "Scrubbing must pause and render an arbitrary time immediately");
  await fishtraceFrame.locator('[data-water-frame="P3"]').click();
  const beforeWaterOff = await waterCanvasState();
  await fishtraceFrame.locator("#water-enabled").uncheck();
  const afterWaterOff = await waterCanvasState();
  assert(afterWaterOff.visible === 0 && afterWaterOff.time === beforeWaterOff.time, "Water toggle must clear only the water layer and preserve timeline time");
  await fishtraceFrame.locator("#water-enabled").check();
  assert((await waterCanvasState()).visible > 0, "Water toggle must restore the current deterministic frame");
  await fishtraceFrame.locator("#water-strength").evaluate(input => {
    input.value = "125";
    input.dispatchEvent(new Event("input",{bubbles:true}));
  });
  assert((await fishtraceFrame.locator("#water-strength-value").innerText()) === "125%", "Development strength slider must update the preview only");
  await fishtraceFrame.locator("#water-reset").click();
  assert((await fishtraceFrame.locator("#water-current-time").innerText()) === "0 ms", "Reset must return to P0");
  assert((await fishtraceFrame.locator("#water-strength").inputValue()) === "100" && await fishtraceFrame.locator("#water-enabled").isChecked(), "Reset must restore frozen intensity baseline and enable the water channel");
  assert((await waterCanvasState()).visible === 0, "Reset must restore the clear original image");

  const waterViewportChecks = [];
  for (const viewport of [{width:1440,height:1000},{width:390,height:844},{width:430,height:932}]) {
    await page.setViewportSize(viewport);
    await page.waitForTimeout(120);
    await fishtraceFrame.locator('[data-water-frame="P3"]').click();
    const geometry = await fishtraceFrame.locator("#water-overlay").evaluate(canvas => {
      const stage = document.querySelector("#water-stage").getBoundingClientRect();
      const image = document.querySelector("#water-source-image").getBoundingClientRect();
      const layer = canvas.getBoundingClientRect();
      const dpr = Number(canvas.dataset.dpr || 1);
      const mapped = JSON.parse(canvas.dataset.contentRect);
      const pixels = canvas.getContext("2d").getImageData(0,0,canvas.width,canvas.height).data;
      let visible = 0, outside = 0;
      for (let pixel=0;pixel<pixels.length;pixel+=4) {
        if (pixels[pixel+3] <= 2) continue;
        visible += 1;
        const index=pixel/4, x=(index%canvas.width)/dpr, y=Math.floor(index/canvas.width)/dpr;
        if (x < mapped.x-1 || y < mapped.y-1 || x > mapped.x+mapped.width+1 || y > mapped.y+mapped.height+1) outside += 1;
      }
      return {
        stage:{x:stage.x,y:stage.y,width:stage.width,height:stage.height},
        image:{x:image.x,y:image.y,width:image.width,height:image.height},
        canvas:{x:layer.x,y:layer.y,width:layer.width,height:layer.height},
        backing:{width:canvas.width,height:canvas.height},dpr,content:mapped,visible,outside
      };
    });
    assert(geometry.visible > 0 && geometry.outside === 0, "Water must remain visible and clipped within the fixed photo at " + viewport.width + "×" + viewport.height);
    for (const edge of ["x","y","width","height"]) {
      assert(Math.abs(geometry.stage[edge]-geometry.image[edge]) < 1.1 && Math.abs(geometry.stage[edge]-geometry.canvas[edge]) < 1.1, "Photo and Canvas geometry must align at " + viewport.width + "×" + viewport.height);
    }
    assert(Math.abs(geometry.backing.width-geometry.stage.width*geometry.dpr) <= 1.5 && Math.abs(geometry.backing.height-geometry.stage.height*geometry.dpr) <= 1.5, "Canvas backing store must match CSS dimensions at devicePixelRatio");
    waterViewportChecks.push({viewport:viewport.width+"×"+viewport.height,geometry:geometry});
  }
  const hiDpiContext = await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:2});
  const hiDpiPage = await hiDpiContext.newPage();
  const hiDpiPageErrors = [];
  const hiDpiRequests = [];
  hiDpiPage.on("pageerror",error => hiDpiPageErrors.push(error.message));
  hiDpiPage.on("request",request => hiDpiRequests.push({url:request.url(),method:request.method()}));
  await hiDpiPage.goto(new URL(fishtraceSitesHash,baseUrl).href,{waitUntil:"domcontentloaded"});
  const hiDpiFrame = await waitForFreshFishtraceFrame(hiDpiPage);
  await hiDpiFrame.locator('[data-water-view="canvas-water"]').click();
  await hiDpiFrame.waitForFunction(() => document.querySelector('[data-testid="canvas-water-lab"]')?.dataset.rendererStatus === "READY",null,{timeout:30000});
  await hiDpiFrame.locator('[data-water-frame="P3"]').click();
  const hiDpiGeometry = await hiDpiFrame.locator("#water-overlay").evaluate(canvas => {
    const stage = document.querySelector("#water-stage").getBoundingClientRect();
    const image = document.querySelector("#water-source-image").getBoundingClientRect();
    const layer = canvas.getBoundingClientRect();
    const dpr = Number(canvas.dataset.dpr || 1);
    const mapped = JSON.parse(canvas.dataset.contentRect);
    const pixels = canvas.getContext("2d").getImageData(0,0,canvas.width,canvas.height).data;
    let visible = 0, outside = 0;
    for (let pixel=0;pixel<pixels.length;pixel+=4) {
      if (pixels[pixel+3] <= 2) continue;
      visible += 1;
      const index=pixel/4, x=(index%canvas.width)/dpr, y=Math.floor(index/canvas.width)/dpr;
      if (x < mapped.x-1 || y < mapped.y-1 || x > mapped.x+mapped.width+1 || y > mapped.y+mapped.height+1) outside += 1;
    }
    return {
      stage:{x:stage.x,y:stage.y,width:stage.width,height:stage.height},
      image:{x:image.x,y:image.y,width:image.width,height:image.height},
      canvas:{x:layer.x,y:layer.y,width:layer.width,height:layer.height},
      backing:{width:canvas.width,height:canvas.height},dpr,content:mapped,visible,outside
    };
  });
  assert(hiDpiGeometry.dpr === 2, "Canvas must use the actual devicePixelRatio on a 2× browser surface");
  for (const edge of ["x","y","width","height"]) {
    assert(Math.abs(hiDpiGeometry.stage[edge]-hiDpiGeometry.image[edge]) < 1.1 && Math.abs(hiDpiGeometry.stage[edge]-hiDpiGeometry.canvas[edge]) < 1.1, "2× DPR photo and Canvas geometry must align");
  }
  assert(Math.abs(hiDpiGeometry.backing.width-hiDpiGeometry.stage.width*2) <= 1.5 && Math.abs(hiDpiGeometry.backing.height-hiDpiGeometry.stage.height*2) <= 1.5, "Canvas backing store must be 2× CSS dimensions on a high-DPI surface");
  assert(hiDpiGeometry.visible > 0 && hiDpiGeometry.outside === 0, "2× DPR water caustics must stay visible and clipped to the photo");
  const forbiddenHiDpiTraffic = hiDpiRequests.filter(request =>
    request.method !== "GET" && request.method !== "HEAD" ||
    /inference|detector|classifier|upload/i.test(request.url)
  );
  assert(forbiddenHiDpiTraffic.length === 0 && hiDpiPageErrors.length === 0, "High-DPI preview must not upload/infer or create uncaught page errors");
  await hiDpiContext.close();

  await page.setViewportSize({width:1440,height:1000});
  await fishtraceFrame.locator("#water-reset").click();
  const waterTraffic = requests.slice(waterNetworkStart);
  const forbiddenWaterTraffic = waterTraffic.filter(request =>
    request.method !== "GET" && request.method !== "HEAD" ||
    /inference|detector|classifier|upload/i.test(request.url)
  );
  assert(forbiddenWaterTraffic.length === 0, "Canvas Water Lab must make no photo upload or real inference requests");
  await page.emulateMedia({reducedMotion:"reduce"});
  await fishtraceFrame.locator('[data-water-frame="P3"]').click();
  const reducedMotionScale = await fishtraceFrame.locator("#water-overlay").getAttribute("data-motion-scale");
  const p3ReducedMotionPixels = await fishtraceFrame.locator("#water-overlay").evaluate(canvas => canvas.toDataURL("image/png"));
  assert(reducedMotionScale === "0.12" && p3ReducedMotionPixels !== p3FullMotionPixels, "Reduced-motion must reduce dynamic movement while retaining static frame seeking");
  await page.emulateMedia({reducedMotion:"no-preference"});
  await fishtraceFrame.locator("#water-reset").click();
  const playbackSamples=await fishtraceFrame.evaluate(()=>new Promise(resolve=>{
    const samples=[];
    let previous=null;
    const sample=now=>{
      const canvas=document.querySelector("#water-overlay");
      const current=Number(canvas?.dataset.currentTime||0);
      if(previous!==null)samples.push({frame_interval_ms:now-previous,draw_ms:Number(canvas?.dataset.renderDurationMs||0),timeline_ms:current});
      previous=now;
      if(current>=2800&&Number(canvas?.dataset.performanceSampleCount||0)>0)resolve(samples);
      else requestAnimationFrame(sample);
    };
    requestAnimationFrame(sample);
    document.querySelector("#water-play").click();
  }));
  const intervalValues=playbackSamples.map(sample=>sample.frame_interval_ms).filter(Number.isFinite).sort((a,b)=>a-b);
  const drawValues=playbackSamples.map(sample=>sample.draw_ms).filter(Number.isFinite).sort((a,b)=>a-b);
  const percentile=(values,p)=>values.length?values[Math.min(values.length-1,Math.ceil(values.length*p)-1)]:0;
  const performanceProfile={
    build_sha:buildMarker,route:fishtraceSitesHash,environment:{browser:"Chromium headless via Playwright",viewport_css_px:"1440×1000",device_scale_factor:1,device_model:"GitHub Actions virtual Linux runner; not a physical mobile device"},
    playback_frame_count:playbackSamples.length,
    frame_interval_ms:{average:intervalValues.reduce((sum,value)=>sum+value,0)/Math.max(1,intervalValues.length),median:percentile(intervalValues,.5),p95:percentile(intervalValues,.95),dropped_over_33_3_ms_ratio:intervalValues.filter(value=>value>33.3).length/Math.max(1,intervalValues.length)},
    canvas_draw_ms:{average:drawValues.reduce((sum,value)=>sum+value,0)/Math.max(1,drawValues.length),median:percentile(drawValues,.5),p95:percentile(drawValues,.95)},
    complete_playback_observed:Number((await fishtraceFrame.locator("#water-current-time").innerText()).match(/\d+/)?.[0])===2800,
    instrumentation:"rAF interval and CanvasWaterRenderer measured draw duration sampled in the actual 2800ms playback"
  };
  process.stdout.write("FISHTRACE_CANVAS_WATER_PERFORMANCE="+JSON.stringify(performanceProfile)+"\n");
  assert(performanceProfile.complete_playback_observed&&performanceProfile.playback_frame_count>=100,"Performance profile must include a complete 2800ms playback with at least 100 sampled frames; observed "+JSON.stringify(performanceProfile));
  assert(performanceProfile.frame_interval_ms.median<=16.7&&performanceProfile.frame_interval_ms.p95<=33.3,"Canvas playback frame interval must meet median 16.7ms and P95 33.3ms targets; observed "+JSON.stringify(performanceProfile.frame_interval_ms));
  const waterCanvasEvidence = {
    fixed_asset:waterAsset,
    p0:p0Water,p2:p2Water,p3:p3Water,p5:p5Water,p7:p7Water,
    all_keyframe_metrics:compositeMetrics,caustic_model:causticModelState,continuity:continuityEvidence,
    caustic_channel_percent:{P2:p2Intensity,P3:p3Intensity},
    composited_visual_metrics:compositeMetrics,
    source_normalized_water_field:{version:debugOverlayState.version,coordinate_system:"source_image_normalized_top_left_0_1",zone_polygon_counts:{A:debugOverlayState.zones.A.length,B:debugOverlayState.zones.B.length,C:debugOverlayState.zones.C.length},validation_status:debugOverlayState.validation_status||"user-specified geometry; browser boundary review in this run",debug_screenshot:"fishtrace-canvas-water-region-debug.png"},
    exact_frame_jumps:waterFrameJumpEvidence,
    deterministic_p2_repeat:true,
    p2_repeat_pixels:{time_ms:p2Repeat.time_ms,width:p2Repeat.width,height:p2Repeat.height,visible_pixels:p2Repeat.visible_pixels,sha256:p2Repeat.sha256,repeated_sha256:p2RepeatAgain.sha256},
    stale_transition_overlay_cleared:true,
    pause_time:pausedWaterTime,
    scrubbed_time_ms:1375,
    reset_time_ms:0,
    responsive_geometry:waterViewportChecks,
    reduced_motion_scale:reducedMotionScale,
    reduced_motion_changes_frame:p3ReducedMotionPixels !== p3FullMotionPixels,
    device_pixel_ratio_2:hiDpiGeometry,
    dpr_2_photo_upload_or_inference_requests:forbiddenHiDpiTraffic.length,
    dpr_2_page_errors:hiDpiPageErrors,
    performance_profile:performanceProfile,
    requests:waterTraffic.map(request=>({method:request.method,url:request.url})),
    photo_upload_or_inference_requests:forbiddenWaterTraffic.length,
    screenshots:waterScreenshots
  };


  const failedContext = await browser.newContext({viewport:{width:1440,height:1000},deviceScaleFactor:1});
  const failedPage = await failedContext.newPage();
  const failedPageErrors = [];
  failedPage.on("pageerror",error => failedPageErrors.push(error.message));
  const blockedContract = "**/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json*";
  await failedPage.route(blockedContract,route => route.abort());
  await failedPage.goto(new URL(fishtraceSitesHash,baseUrl).href,{waitUntil:"domcontentloaded"});
  const failedFrame = await waitForFreshFishtraceFrame(failedPage);
  await failedFrame.locator('[data-water-view="canvas-water"]').click();
  await failedFrame.waitForFunction(() => document.querySelector('[data-testid="canvas-water-lab"]')?.dataset.rendererStatus === "ERROR",null,{timeout:15000});
  assert(await failedFrame.locator("#water-load-error").isVisible() && await failedFrame.locator("#water-retry").isVisible(), "A failed candidate contract load must show a clear error and retry entry");
  await failedPage.unroute(blockedContract);
  await failedFrame.locator("#water-retry").click();
  await failedFrame.waitForFunction(() => document.querySelector('[data-testid="canvas-water-lab"]')?.dataset.rendererStatus === "READY",null,{timeout:30000});
  assert(failedPageErrors.length === 0, "Canvas load failure/retry must not create uncaught browser errors");
  await failedContext.close();
  const waterFailureEvidence = {
    injected_candidate_contract_failure:true,
    explicit_error_and_retry_visible:true,
    retry_loaded_verified_asset:true,
    uncaught_errors:failedPageErrors
  };

  const videoContext = await browser.newContext({
    viewport:{width:1440,height:1000},
    deviceScaleFactor:1,
    reducedMotion:"no-preference",
    recordVideo:{dir:args.outputDir,size:{width:1440,height:1000}}
  });
  const videoRecordingStartEpochMs=Date.now();
  const videoPage = await videoContext.newPage();
  const videoErrors = [];
  const videoRequests = [];
  videoPage.on("console",message => {if(message.type()==="error") videoErrors.push(message.text());});
  videoPage.on("pageerror",error => videoErrors.push(error.message));
  videoPage.on("request",request => videoRequests.push({url:request.url(),method:request.method()}));
  await videoPage.goto(new URL(fishtraceSitesHash,baseUrl).href,{waitUntil:"domcontentloaded"});
  const videoFrame = await waitForFreshFishtraceFrame(videoPage);
  await videoFrame.locator('[data-water-view="canvas-water"]').click();
  await videoFrame.waitForFunction(() => document.querySelector('[data-testid="canvas-water-lab"]')?.dataset.rendererStatus === "READY",null,{timeout:30000});
  await videoFrame.locator("#water-source-image").scrollIntoViewIfNeeded();
  await videoFrame.locator("#water-stage").scrollIntoViewIfNeeded();
  await videoPage.locator(fishtraceFrameSelector).scrollIntoViewIfNeeded();
  const videoPhotoPreflight=await videoPage.evaluate(selector=>{
    const iframe=document.querySelector(selector);
    const doc=iframe?.contentDocument;
    const stage=doc?.querySelector("#water-stage");
    const canvas=doc?.querySelector("#water-overlay");
    if(!iframe||!doc||!stage||!canvas)return {available:false};
    const frameBox=iframe.getBoundingClientRect(),stageBox=stage.getBoundingClientRect(),content=JSON.parse(canvas.dataset.contentRect);
    const photo={x:stageBox.x+content.x,y:stageBox.y+content.y,width:content.width,height:content.height};
    const frameClient={x:frameBox.x+iframe.clientLeft,y:frameBox.y+iframe.clientTop};
    const onOuter={x:frameClient.x+photo.x,y:frameClient.y+photo.y,width:photo.width,height:photo.height};
    const inner={width:doc.documentElement.clientWidth,height:doc.documentElement.clientHeight};
    const outer={width:window.innerWidth,height:window.innerHeight};
    const insideInner=photo.x>=0&&photo.y>=0&&photo.x+photo.width<=inner.width&&photo.y+photo.height<=inner.height;
    const insideOuter=onOuter.x>=0&&onOuter.y>=0&&onOuter.x+onOuter.width<=outer.width&&onOuter.y+onOuter.height<=outer.height;
    return {available:true,viewport:{outer,inner},frame:{x:frameBox.x,y:frameBox.y,width:frameBox.width,height:frameBox.height},stage:{x:stageBox.x,y:stageBox.y,width:stageBox.width,height:stageBox.height},photo_content_rect_in_frame:photo,photo_content_rect_in_recording_viewport:onOuter,fully_visible_in_frame:insideInner,fully_visible_in_recording_viewport:insideOuter,scroll:{outer_x:window.scrollX,outer_y:window.scrollY,inner_x:doc.defaultView.scrollX,inner_y:doc.defaultView.scrollY},renderer:canvas.dataset.rendererStatus,debug:canvas.dataset.debugRegion};
  },fishtraceFrameSelector);
  assert(videoPhotoPreflight.available&&videoPhotoPreflight.fully_visible_in_frame&&videoPhotoPreflight.fully_visible_in_recording_viewport,"Full fixed photo must fit inside the actual iframe and recording viewport before WebM playback; geometry="+JSON.stringify(videoPhotoPreflight));
  await videoPage.screenshot({path:resolve(args.outputDir,"fishtrace-canvas-water-video-preflight.png")});
  assert(await videoFrame.locator("#water-stage").isVisible(), "The recorded page must visibly be in the Canvas Water Lab stage");
  const videoStartState=await videoFrame.locator("#water-overlay").evaluate(canvas=>({time:Number(canvas.dataset.currentTime),renderer:canvas.dataset.rendererStatus,alphaPixels:(()=>{const data=canvas.getContext("2d").getImageData(0,0,canvas.width,canvas.height).data;let n=0;for(let i=3;i<data.length;i+=4)if(data[i])n++;return n;})(),debug:canvas.dataset.debugRegion}));
  assert(videoStartState.time===0&&videoStartState.renderer==="READY"&&videoStartState.alphaPixels===0&&videoStartState.debug==="false", "Full playback recording must start at clean P0 on the READY Canvas Water Lab without the debug outline");
  const waterPlaybackStartedAt=new Date().toISOString();
  const waterPlaybackStartEpochMs=Date.now();
  await videoFrame.locator("#water-play").click();
  const waterPlaybackStarted = Date.now();
  await videoFrame.waitForFunction(() => Number(document.querySelector("#water-current-time")?.textContent.match(/\d+/)?.[0]) === 2800,null,{timeout:8000});
  const waterPlaybackWallMs = Date.now()-waterPlaybackStarted;
  const waterPlaybackCompletedAt=new Date().toISOString();
  assert(waterPlaybackWallMs >= 2400 && waterPlaybackWallMs < 8000, "Full timeline playback must run through the 2800ms duration without stalling");
  assert(videoErrors.length === 0, "Canvas recording browser reported errors: " + videoErrors.join("; "));
  assert(videoRequests.every(request => request.method === "GET" || request.method === "HEAD") &&
    !videoRequests.some(request => /inference|detector|classifier|upload/i.test(request.url)), "Recorded Canvas playback must not request upload or inference endpoints");
  await videoPage.waitForTimeout(180);
  const playbackVideo = await videoPage.video();
  await videoContext.close();
  const playbackVideoPath = resolve(args.outputDir,"fishtrace-canvas-water-full-2800ms.webm");
  await rename(await playbackVideo.path(),playbackVideoPath);
  const videoProbe=JSON.parse(execFileSync("ffprobe",["-v","error","-show_entries","format=duration:stream=width,height","-of","json",playbackVideoPath],{encoding:"utf8"}));
  const videoStream=videoProbe.streams?.find(stream=>stream.width&&stream.height);
  assert(videoStream&&videoStream.width===1440&&videoStream.height===1000,"Recorded WebM must retain the actual 1440x1000 viewport; metadata="+JSON.stringify(videoProbe));
  const playbackStartOffsetMs=Math.max(0,waterPlaybackStartEpochMs-videoRecordingStartEpochMs);
  assert(Number(videoProbe.format.duration)>=playbackStartOffsetMs/1000+2.8,"WebM must contain the full 2800ms playback interval; probe="+JSON.stringify(videoProbe.format));
  const videoFrameDirectory=resolve(args.outputDir,"fishtrace-canvas-water-video-frames");
  await mkdir(videoFrameDirectory,{recursive:true});
  const extractedVideoFrames=[];
  for(const frame of allWaterFrames){
    const [frameId,frameMs]=frame;
    const outputPath=resolve(videoFrameDirectory,frameId+"-"+frameMs+"ms.png");
    const seekSeconds=(playbackStartOffsetMs+frameMs)/1000;
    execFileSync("ffmpeg",["-hide_banner","-loglevel","error","-y","-ss",seekSeconds.toFixed(3),"-i",playbackVideoPath,"-frames:v","1",outputPath],{stdio:"pipe"});
    const frameBytes=await readFile(outputPath);
    assert(frameBytes.length>24&&frameBytes.toString("ascii",1,4)==="PNG","WebM frame extraction failed at "+frameId+"; offset="+seekSeconds.toFixed(3));
    extractedVideoFrames.push({id:frameId,target_ms:frameMs,video_timestamp_s:Number(seekSeconds.toFixed(3)),path:"fishtrace-canvas-water-video-frames/"+frameId+"-"+frameMs+"ms.png",sha256:createHash("sha256").update(frameBytes).digest("hex"),dimensions:{width:frameBytes.readUInt32BE(16),height:frameBytes.readUInt32BE(20)},photo_fully_visible_in_recording_viewport:videoPhotoPreflight.fully_visible_in_recording_viewport});
  }
  assert(extractedVideoFrames.length===8&&extractedVideoFrames.every(frame=>frame.dimensions.width===1440&&frame.dimensions.height===1000&&frame.photo_fully_visible_in_recording_viewport),"Every extracted WebM sample must be a full-size viewport frame from a preflight-verified full-photo recording");
  waterCanvasEvidence.full_playback = {
    video:"fishtrace-canvas-water-full-2800ms.webm",
    frame_duration_ms:2800,
    playback_started_at_utc:waterPlaybackStartedAt,
    playback_completed_at_utc:waterPlaybackCompletedAt,
    observed_playback_wall_ms:waterPlaybackWallMs,
    start_state:videoStartState,
    photo_visibility_preflight:videoPhotoPreflight,
    recording_start_epoch_ms:videoRecordingStartEpochMs,
    playback_start_offset_ms:playbackStartOffsetMs,
    container_duration_s:Number(videoProbe.format.duration),
    extracted_frames:extractedVideoFrames,
    final_time_ms:2800,
    errors:videoErrors,
    photo_upload_or_inference_requests:videoRequests.filter(request => /inference|detector|classifier|upload/i.test(request.url)).length
  };

  await open("#page/recognition_flow_v1_2/hifi/invalid-release-route");
  assert(await page.locator(".invalid-manager-route").isVisible(), "invalid Hash must show an explicit error state");
  assert(await page.locator("#hifiViewCanvas img").count() === 0, "invalid Hash must not fall back to an unrelated Frozen image");

  assert(badResponses.length === 0, `HTTP failures observed: ${badResponses.join("; ")}`);
  assert(failedRequests.length === 0, `Failed requests observed: ${failedRequests.join("; ")}`);
  assert(errors.length === 0, `Browser errors observed: ${errors.join("; ")}`);

  const sourcePaths=[
    "design/manager/fishtrace_canvas_water_lab.js",
    "design/manager/fishtrace_canvas_water_lab.css",
    "design/manager/app.js",
    "design/manager/fishtrace_sites_web_workspace.html",
    "design/manager/fishtrace_sites_web_workspace.css",
    "design/manager/fishtrace_sites_web_workspace.js",
    "design/pages/recognition/web/candidates/FISHTRACE_SWOS_CANVAS_WATER_LAB_V1_CANDIDATE.json",
    "design/system/fish_media_display_v1/assets/real_catch_source.jpg"
  ];
  const sourceManifestFiles=[];
  for(const path of sourcePaths){
    const bytes=await readFile(resolve(args.root,path));
    sourceManifestFiles.push({path,sha256:createHash("sha256").update(bytes).digest("hex"),bytes:bytes.length});
  }
  const sourceManifest={
    schema:"FISHTRACE_CANVAS_WATER_LAB_SOURCE_MANIFEST_V3",
    task_id:waterContract.task_id,
    candidate_source_sha:buildMarker,
    route:fishtraceSitesHash,
    contract_id:waterContract.contract_id,
    contract_version:waterContract.contract_version,
    fixture:{path:"design/system/fish_media_display_v1/assets/real_catch_source.jpg",sha256:"6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef",decoded_dimensions:{width:waterAsset.naturalWidth,height:waterAsset.naturalHeight},exif_orientation:"none"},
    water_field:{version:debugOverlayState.version,coordinate_system:"source_image_normalized_top_left_0_1",trust:"USER_SPECIFIED_FIXED_DEMO_GEOMETRY_NOT_AI_MASK_NOT_PRODUCTION_ASSET",validation_status:debugOverlayState.validation_status,zones:debugOverlayState.zones},
    files:sourceManifestFiles
  };
  const visualMetricsReport={
    schema:"FISHTRACE_CANVAS_WATER_LAB_VISUAL_METRICS_V3",
    task_id:waterContract.task_id,
    candidate_source_sha:buildMarker,
    composition_method:"Chromium Canvas2D: HTML source image composited with the visible overlay using screen blend at measured stage CSS dimensions; normalized A/B/C zones are mapped through the same object-fit:contain content rectangle and Forbidden Zone is the photo complement.",
    thresholds:{primary_roi:waterContract.acceptance.primary_roi,region_a_mean_rgb_abs_diff_255_target:waterContract.acceptance.region_a_mean_rgb_abs_diff_255_target,forbidden_zone_mean_rgb_abs_diff_255_max:waterContract.acceptance.forbidden_zone_mean_rgb_abs_diff_255_max,forbidden_zone_pixels_with_peak_diff_gt_4_fraction_max:waterContract.acceptance.forbidden_zone_pixels_with_peak_diff_gt_4_fraction_max,p0_p7_canvas_alpha_max:waterContract.acceptance.p0_p7_canvas_alpha_max,p0_p7_composite_rgb_diff_max:waterContract.acceptance.p0_p7_composite_rgb_diff_max,full_timeline_unchanged:true},
    source_asset:sourceManifest.fixture,
    region_version:debugOverlayState.version,
    zone_polygon_counts:{A:debugOverlayState.zones.A.length,B:debugOverlayState.zones.B.length,C:debugOverlayState.zones.C.length},
    frames:compositeMetrics,
    screenshots:waterScreenshots,
    comparison_montage:"fishtrace-canvas-water-comparison-original-P2-P3-P5.png",
    debug_overlay_screenshot:"fishtrace-canvas-water-region-debug.png",
    difference_map:"fishtrace-canvas-water-diff-map-P3.png"
  };
  await writeFile(resolve(args.outputDir,"source-manifest.json"),JSON.stringify(sourceManifest,null,2)+"\n");
  await writeFile(resolve(args.outputDir,"visual-metrics.json"),JSON.stringify(visualMetricsReport,null,2)+"\n");
  await writeFile(resolve(args.outputDir,"performance-profile.json"),JSON.stringify(performanceProfile,null,2)+"\n");

  const summary = {
    result: "PASS",
    base_url: baseUrl,
    build_sha: buildMarker,
    nh01: {
      state: "READY",
      module_builds: nh01ModuleUrls,
      resources: resourceEvidence,
      canvas_comparison_input: {screenshot:renderedCanvasPath,dom_bounds:"nh01-m03-dom-bounds.json",dimensions:canvasSize},
      nested_tree: {nodes:domEvidence.tree.length,parent_matches:domEvidence.parentMismatches.length,unmapped:domEvidence.unmapped.length},
      frozen_reference_renders: [{profile:"M01",width:320,height:480},{profile:"M02",width:360,height:640},{profile:"M03",width:390,height:844},{profile:"M04",width:430,height:932}],
      width_only_boundary: widthBoundaryEvidence,
      formal_390x844_dom_geometry_delta: domFormulaComparison,
      formal_geometry_comparison_by_profile: profileGeometryComparisons,
      hero_metadata_layout: heroMetadataLayout,
      background_object_position: backgroundObjectPosition,
      formula_profile_results: profileComputations,
      injected_failure_recovery: faultEvidence,
      layer_selection: ["recent tree→canvas", "hero canvas→tree/inspector", "peek-left tree→canvas/inspector blocked state"],
      viewport_checks: [[320,480],[360,640],[390,844],[430,932],[480,"width-only; retained height"]],
      font_scales: [1,1.3,1.5]
    },
    viewports: viewportResults,
    p01: guideImageInfo,
    overview: overviewImageInfo,
    review: reviewImage,
    fishtrace_sites: {
      route: fishtraceSitesHash,
      iframe_title: "FishTrace Sites Web 高保真交互体验 V1.1",
      states_verified: ["entry", "source-choice", "preview", "processing", "reveal", "result", "error"],
      local_photo_preview: true,
      local_photo_upload_requests: localPhotoRequests.length,
      desktop_result_columns: desktopResultColumns,
      phone_result_columns: phoneResultColumns,
      mask_trust_state: "NONE",
      rr09_reveal: {frame: "R4", title_opacity: Number(revealTitleOpacity), fallback: "CHINESE_TITLE_FADE_ONLY"},
      canvas_water_lab: Object.assign(waterCanvasEvidence,{failure_recovery:waterFailureEvidence})
    },
    checks: ["A-line Canvas contract, normalized A/B/C/D region geometry and three-channel curve load", "FishTrace Sites Hi-Fi route and iframe load", "entry CTA → source choice before camera/gallery", "camera and gallery source cards visible", "local photo preview uses blob URL without POST/inference API", "structured photo layer selection opens properties", "desktop result photo-left/result-right", "phone result photo-top/result-below", "P0-P7 timeline selection and explicit NONE Mask trust", "Canvas Water Lab clears stale prototype photo-transition layer on entry", "Canvas Water Lab P0-P7 exact timestamp and baseline intensity", "Canvas contract failure message and retry recovery", "RR09 R0-R5 timeline and Chinese-title fallback at R4", "single-source Processing→RR09 photo geometry transition and final result state", "error recovery retry/source replacement", "prototype reset and DEMO_DATA_ONLY boundary", "Canvas Water Lab fixed asset SHA-256 and decoded dimensions", "P0/P7 exact transparent Canvas evidence", "A-line Region A P1–P6 pixel ranges, Forbidden Zone measurements and deterministic in-photo caustics", "play/pause/reset/scrub/P0-P7/strength/water toggle controls", "desktop/390×844/430×932 Canvas-photo alignment and DPR geometry",
    "Canvas 2× DPR backing-store and clipping geometry", "reduced-motion behavior", "complete 2800ms playback WebM", "no photo upload or inference network request", "NH01 structured workspace READY", "21 source and frozen/used resources with HTTP/SHA evidence", "source layer tree matches real nested DOM parentage", "layer tree/canvas/inspector bidirectional selection", "M01-M04 CSS canvas PNGs and DOM bounds; M05 width-only geometry without assumed height", "formal 390×844 contract rectangles equal measured DOM geometry", "five dp presets", "free viewport/font 1.0/1.3/1.5/zoom/safe area", "Manifest/contract 404, invalid JSON, JS/CSS 404, timeout and recovery", "stale route cancellation", "direct Hash", "Resources submenu click and direct route", "original PNG previews/download links and manifest", "refresh", "back/forward", "modal zoom/fit/Esc/focus restore", "SWOS and continuity authority routes", "15-frame static workspace and exact 8+7 count", "R0.5 2900ms zoom and original/composite toggle", "P7/R0/R0.5/R1 geometry table", "RR09 ↔ SWOS routes", "Web Hi-Fi V2 authority/resources and 390px route", "local-only photo selection without persistent/network upload", "390px SWOS responsive preview", "RR09 V3 current / V2 historical route separation", "missing authority no fake UI", "invalid route guard", "no HTTP errors", "no console/page errors"],
    swos_local_photo_sha256: uploadedPhotoSha,
    swos_local_photo_uploaded: Boolean(args.swosPhoto),
    screenshots: ["fishtrace-sites-web-hifi-desktop.png", "fishtrace-sites-web-hifi-phone.png", "fishtrace-sites-web-hifi-processing.png", "fishtrace-sites-web-hifi-reveal.png", "fishtrace-sites-web-hifi-error.png", "fishtrace-sites-web-hifi-continuous-result.png", "fishtrace-canvas-water-P0.png", "fishtrace-canvas-water-P1.png", "fishtrace-canvas-water-P2.png", "fishtrace-canvas-water-P3.png", "fishtrace-canvas-water-P4.png", "fishtrace-canvas-water-P5.png", "fishtrace-canvas-water-P6.png", "fishtrace-canvas-water-P7.png", "fishtrace-canvas-water-region-debug.png", "fishtrace-canvas-water-diff-map-P3.png", "fishtrace-canvas-water-comparison-original-P2-P3-P5.png", "fishtrace-canvas-water-mobile-390.png", "fishtrace-canvas-water-mobile-430.png", "fishtrace-canvas-water-full-2800ms.webm", "source-manifest.json", "visual-metrics.json", "performance-profile.json", "nh01-m01-render-320x480.png", "nh01-m02-render-360x640.png", "nh01-m03-render-390x844.png", "nh01-m04-render-430x932.png", "nh01-m01-dom-bounds.json", "nh01-m02-dom-bounds.json", "nh01-m03-dom-bounds.json", "nh01-m04-dom-bounds.json", "nh01-m05-width-only-bounds.json", "desktop-1440.png", "tablet-768.png", "mobile-390.png", "lightbox-1440.png", "overview-1440.png", "guide-resources-1440.png", "guide-resources-390.png", "swos-optical-contract-1440.png", "swos-static-visual-1440.png", "swos-static-visual-390.png", "swos-r05-zoom-1440.png", "swos-web-motion-contract-1440.png", "swos-web-motion-contract-390.png", "web-hifi-v2-1440.png", "processing-result-continuity-1440.png", "rr09-v3-authority-1440.png", "rr09-v2-history-1440.png"],
    errors,
    bad_responses: badResponses,
    failed_requests: failedRequests
  };
  await writeFile(resolve(args.outputDir, "browser-verification.json"), JSON.stringify(summary, null, 2) + "\n");
  process.stdout.write(`PASS: Design Manager browser verification | ${viewportResults.map(x => x.viewport).join(",")}px | ${summary.checks.join(" | ")}\n`);

  await browser.close();
  activeBrowser = null;
  if (server) await new Promise(resolvePromise => server.close(resolvePromise));
  activeServer = null;
}

main().catch(async error => {
  if (activeBrowser) await activeBrowser.close().catch(() => {});
  if (activeServer) await new Promise(resolvePromise => activeServer.close(resolvePromise));
  process.stderr.write(`FAIL: ${error.stack || error.message}\n`);
  process.exitCode = 1;
});

