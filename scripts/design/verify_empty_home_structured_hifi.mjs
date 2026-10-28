#!/usr/bin/env node

import { createServer } from "node:http";
import { createReadStream, existsSync, statSync } from "node:fs";
import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve, extname, sep } from "node:path";
import { createRequire } from "node:module";
import { createHash } from "node:crypto";

const require = createRequire(import.meta.url);
const playwrightPath = process.env.PLAYWRIGHT_PATH || (process.env.PLAYWRIGHT_MODULE_DIR ? resolve(process.env.PLAYWRIGHT_MODULE_DIR, "playwright") : "playwright");
const { chromium } = require(playwrightPath);
const ROUTE = "#page/home_empty_v2/hifi/structured_workspace";
const MIME = {".html":"text/html; charset=utf-8",".js":"text/javascript; charset=utf-8",".css":"text/css; charset=utf-8",".json":"application/json; charset=utf-8",".png":"image/png",".svg":"image/svg+xml",".jpg":"image/jpeg",".jpeg":"image/jpeg",".webp":"image/webp",".md":"text/markdown; charset=utf-8"};

function parseArgs(argv) {
  const args = { root: process.cwd(), url: null, outputDir: "/tmp/empty-home-structured-hifi-evidence", expectedSha: null };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--root") args.root = resolve(argv[++i]);
    else if (argv[i] === "--url") args.url = argv[++i];
    else if (argv[i] === "--output-dir") args.outputDir = resolve(argv[++i]);
    else if (argv[i] === "--expected-sha") args.expectedSha = argv[++i];
    else throw new Error(`Unknown argument ${argv[i]}`);
  }
  return args;
}

function assert(condition, message) { if (!condition) throw new Error(message); }
async function sha256File(path) { return createHash("sha256").update(await readFile(path)).digest("hex"); }
async function pngDimensions(path) {
  const bytes = await readFile(path);
  assert(bytes.length >= 24 && bytes.subarray(1, 4).toString("ascii") === "PNG", `EVIDENCE_SCREENSHOT_NOT_PNG: ${path}`);
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}
async function screenshotRootViewport(page, path, width, height, timeout = 45000) {
  const locator = page.locator("#eh-EmptyHomeRoot");
  await locator.waitFor({ state: "visible", timeout });
  await locator.scrollIntoViewIfNeeded({ timeout });
  const bounds = await page.evaluate(() => {
    const rect = document.querySelector("#eh-EmptyHomeRoot")?.getBoundingClientRect();
    return rect ? { x: rect.left, y: rect.top, width: rect.width, height: rect.height } : null;
  });
  assert(bounds && Math.abs(bounds.width - width) < 0.01 && Math.abs(bounds.height - height) < 0.01,
    `MATRIX_ROOT_BOUNDS_MISMATCH: requested=${width}x${height} actual=${JSON.stringify(bounds)}`);
  await page.screenshot({ path, clip: { x: bounds.x, y: bounds.y, width, height }, scale: "device", animations: "disabled", timeout });
  return { size: await pngDimensions(path), bounds_css_px: { x: bounds.x, y: bounds.y, width, height } };
}

function startServer(root) {
  const absoluteRoot = resolve(root);
  const server = createServer((request, response) => {
    let pathname;
    try { pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname); }
    catch { response.writeHead(400).end("Bad path"); return; }
    let file = resolve(absoluteRoot, `.${pathname}`);
    if (file !== absoluteRoot && !file.startsWith(absoluteRoot + sep)) { response.writeHead(403).end("Forbidden"); return; }
    if (existsSync(file) && statSync(file).isDirectory()) file = resolve(file, "index.html");
    if (!existsSync(file) || !statSync(file).isFile()) { response.writeHead(404).end("Not found"); return; }
    response.writeHead(200,{"Content-Type":MIME[extname(file).toLowerCase()]||"application/octet-stream","Cache-Control":"no-store"});
    if (request.method === "HEAD") response.end(); else createReadStream(file).pipe(response);
  });
  return new Promise((ok,reject)=>{server.once("error",reject);server.listen(0,"127.0.0.1",()=>ok({server,url:`http://127.0.0.1:${server.address().port}`}));});
}

async function waitReady(page, expectedSha) {
  await page.locator("#pageList .module-item").first().waitFor({state:"visible",timeout:30000});
  const diagnostic=async()=>page.evaluate(()=>{
    const adapter=window.PageWorkspaceAdapters?.active;
    const host=adapter?.root||document.querySelector("#hifiViewCanvas");
    const workspace=host?.querySelector("[data-page-workspace-root]");
    const board=workspace?.querySelector("[data-workspace-artboard]");
    const rendered=board?.querySelector("#eh-EmptyHomeRoot");
    const loadError=host?.querySelector(".page-ws-load-error");
    const workspaceError=host?.querySelector("[data-workspace-error]");
    const inspector=workspace?.querySelector("[data-workspace-inspector]");
    const images=[...(board?.querySelectorAll("img")||[])].map(image=>({id:image.id||null,complete:image.complete,
      natural_width:image.naturalWidth,natural_height:image.naturalHeight,src:image.currentSrc||image.src||null}));
    return {
      active_adapter_page_id:adapter?.pageId||null,active_adapter_source_sha:adapter?.sourceSha||null,
      active_root_connected:Boolean(adapter?.root?.isConnected),workspace_count:document.querySelectorAll("[data-page-workspace-root]").length,
      workspace:{source_sha:workspace?.dataset.sourceSha||null,kind:workspace?.dataset.workspaceKind||null,
        view_state:workspace?.dataset.viewState||null,visible:Boolean(workspace?.getClientRects().length)},
      board_connected:Boolean(board?.isConnected),board_viewport:board?.dataset.viewport||null,
      has_layout_metrics:Boolean(board?.dataset.layoutMetrics),rendered_source_sha:rendered?.dataset.sourceSha||null,
      render_ready:rendered?.dataset.renderReady||null,selected_node_id:inspector?.dataset.selectedNodeId||null,
      images,load_error:loadError?.innerText||null,
      workspace_error:workspaceError&&!workspaceError.hidden?workspaceError.innerText:null,
      host_text:host?.innerText?.slice(0,1200)||null,host_html:host?.innerHTML?.slice(0,1800)||null
    };
  });
  try {
    await page.waitForFunction(({sha})=>{
      const adapter=window.PageWorkspaceAdapters?.active;
      const host=adapter?.root||document.querySelector("#hifiViewCanvas");
      const workspace=host?.querySelector("[data-page-workspace-root]");
      const board=workspace?.querySelector("[data-workspace-artboard]");
      const rendered=board?.querySelector("#eh-EmptyHomeRoot");
      const loadError=host?.querySelector(".page-ws-load-error");
      const workspaceError=host?.querySelector("[data-workspace-error]");
      if(loadError||(workspaceError&&!workspaceError.hidden)) return true;
      const inspector=workspace?.querySelector("[data-workspace-inspector]");
      return adapter?.pageId==="home_empty_v2" && adapter.sourceSha===sha && adapter.root?.isConnected &&
        workspace?.dataset.sourceSha===sha && workspace.dataset.workspaceKind==="structured-dom-candidate" &&
        workspace.dataset.viewState==="EMPTY_DEFAULT" && workspace.getClientRects().length>0 && board?.dataset.layoutMetrics &&
        rendered?.dataset.sourceSha===sha && rendered.dataset.renderReady==="true" && inspector?.dataset.selectedNodeId &&
        [...board.querySelectorAll("img")].every(image=>image.complete&&image.naturalWidth>0&&image.naturalHeight>0);
    },{sha:expectedSha},{timeout:60000});
  } catch (error) {
    throw new Error(`EMPTY_HOME_RUNTIME_STABILITY_TIMEOUT: ${error.message}; diagnostics=${JSON.stringify(await diagnostic())}`);
  }
  const state=await diagnostic();
  if(state.load_error||state.workspace_error) throw new Error(`EMPTY_HOME_WORKSPACE_RENDER_FAILED: ${state.load_error||state.workspace_error}; diagnostics=${JSON.stringify(state)}`);
  assert(state.active_adapter_page_id==="home_empty_v2"&&state.workspace_count===1&&state.workspace.source_sha===expectedSha&&
    state.workspace.view_state==="EMPTY_DEFAULT"&&state.rendered_source_sha===expectedSha&&state.render_ready==="true"&&
    state.has_layout_metrics&&state.selected_node_id&&state.images.length>0&&state.images.every(image=>image.complete&&image.natural_width>0&&image.natural_height>0),
  `EMPTY_HOME_RUNTIME_NOT_STABLE: diagnostics=${JSON.stringify(state)}`);
}

async function main() {
  const args = parseArgs(process.argv);
  await mkdir(args.outputDir,{recursive:true});
  const expectedSha = args.expectedSha || (await import("node:child_process")).execFileSync("git",["rev-parse","HEAD"],{encoding:"utf8"}).trim();
  const runId = process.env.GITHUB_RUN_ID || "LOCAL_UNTRACKED";
  let server = null;
  let activeMatrixRow = null;
  let base = args.url;
  if (!base) { const started = await startServer(args.root); server = started.server; base = started.url; }
  const managerUrl = base.endsWith("/design/manager/") ? base : new URL("design/manager/",base.endsWith("/")?base:`${base}/`).href;
  const browser = await chromium.launch({headless:true});
  const page = await browser.newPage({viewport:{width:1600,height:1350},deviceScaleFactor:3});
  const consoleErrors=[];
  const pageErrors=[];
  const failedRequests=[];
  page.on("console",message=>{if(message.type()==="error")consoleErrors.push(message.text());});
  page.on("pageerror",error=>pageErrors.push(error.message));
  page.on("requestfailed",request=>failedRequests.push({url:request.url(),failure:request.failure()?.errorText||"failed"}));
  const baseEvidence={schema:"YUJIAN_PAGE_EVIDENCE_MANIFEST_V1",page_id:"home_empty_v2",source_sha:expectedSha,candidate_source_sha:expectedSha,
    contract_id:null,contract_version:null,contract_sha256:null,profile_id:null,profile_sha256:null,resource_binding_sha256:null,
    site_build_sha:expectedSha,deploy_sha:process.env.DEPLOY_SHA||"NOT_RUN",github_run_id:process.env.GITHUB_RUN_ID||null,route:ROUTE,
    native_runtime_status:"NOT_VERIFIED",visual_review_status:"USER_VISUAL_ACCEPTANCE_PENDING",user_acceptance_status:"USER_VISUAL_ACCEPTANCE_PENDING",
    page_profile_status:"DRAFT_SCHEMA_VALID_ONLY",mapping_sha256:null,w2_binding_sha256:null,resource_statuses:[],authority_gaps:[],matrix:[],negative_cases:[],
    differences:[],
    geometry_result:"NOT_RUN",resource_result:"NOT_RUN",responsive_result:"NOT_RUN",dom_report:null,screenshot_reference:null,screenshot_rendered:null,
    overlay_reference:null,difference_reference:null,browser_url:null,
    browser_screenshot_pixel_scale:3,browser_screenshot_scale_disclosure:"Chromium DPR 3 screenshot pixels; DOM geometry remains CSS px and is not Native density."};
  try {
    baseEvidence.github_run_id=process.env.GITHUB_RUN_ID||null;
    baseEvidence.site_build_sha=expectedSha;
    baseEvidence.candidate_url=args.url?`${managerUrl}${ROUTE}`:null;
    await page.goto(`${managerUrl}${ROUTE}`,{waitUntil:"domcontentloaded",timeout:45000});
    await waitReady(page,expectedSha);
    const preflight=await page.evaluate(async expectedSha=>{
      const need=(condition,message)=>{if(!condition)throw new Error(message);};
      const adapter=window.PageWorkspaceAdapters.active,adapterRoot=adapter?.root;
      const root=adapterRoot?.querySelector("[data-page-workspace-root]"),board=adapterRoot?.querySelector("[data-workspace-artboard]"),page=board?.querySelector("#eh-EmptyHomeRoot");
      const rootCount=document.querySelectorAll("[data-page-workspace-root]").length;
      need(adapter&&root&&board&&page,`ACTIVE_WORKSPACE_DOM_MISSING: adapter=${Boolean(adapter)} adapter_root=${adapterRoot?.id||adapterRoot?.className||"missing"} root_count=${rootCount} adapter_root_html=${adapterRoot?.innerHTML?.slice(0,1200)||""}`);
      need(rootCount===1,`WORKSPACE_ROOT_AMBIGUOUS: count=${rootCount}`);
      need(adapter.sourceSha===root.dataset.sourceSha&&root.dataset.sourceSha===expectedSha,`ACTIVE_WORKSPACE_SHA_MISMATCH: adapter=${adapter.sourceSha} root=${root.dataset.sourceSha} expected=${expectedSha}`);
      const tree=await window.PageWorkspaceAdapters.loadComponentTree();
      const authority=await window.PageWorkspaceAdapters.loadAuthority(root.dataset.sourceSha);
      const viewportAuthorityId=authority.envelope.viewport_inputs?.viewport_authority_ref_id;
      const profileBindsViewport=authority.profile.authority_bindings?.some(row=>row.authority_ref_id===viewportAuthorityId);
      need(profileBindsViewport,`PAGE_PROFILE_DOES_NOT_BIND_VIEWPORT_AUTHORITY: ${viewportAuthorityId}`);
      const viewportAuthority=authority.envelope.authority_refs?.find(row=>row.authority_ref_id===viewportAuthorityId);
      need(viewportAuthority?.path&&viewportAuthority?.sha256,"PAGE_VIEWPORT_AUTHORITY_REF_MISSING");
      const responsive=await window.PageWorkspaceAdapters.active.loadAuthorityDocument(viewportAuthority.path,viewportAuthority.sha256);
      need(Array.isArray(responsive.responsive_profiles_to_verify)&&responsive.responsive_profiles_to_verify.length>0,"PAGE_VIEWPORT_PROFILE_MISSING");
      need(Array.isArray(responsive.font_scale_profiles)&&responsive.font_scale_profiles.length>0,"PAGE_FONT_SCALE_PROFILE_MISSING");
      const liveBoard=adapterRoot.querySelector("[data-workspace-artboard]");
      need(liveBoard===board&&board.isConnected,`ACTIVE_WORKSPACE_ARTBOARD_CHANGED: connected=${Boolean(board.isConnected)} active_board=${Boolean(liveBoard)} root_count=${document.querySelectorAll("[data-page-workspace-root]").length}`);
      const rows=window.PageWorkspaceAdapters.measureElements();
      const resources=window.PageWorkspaceAdapters.active.getResourceDiagnostics();
      const imageLoads=[...board.querySelectorAll("img")].map(image=>({id:image.id||null,complete:image.complete,natural_width:image.naturalWidth,natural_height:image.naturalHeight}));
      const brokenImages=imageLoads.filter(image=>!image.complete||image.natural_width<=0||image.natural_height<=0);
      return {pageId:root.dataset.pageId,sourceSha:root.dataset.sourceSha,profileStatus:page.dataset.profileStatus,workspaceKind:root.dataset.workspaceKind,
        contractId:authority.envelope.contract_id,contractVersion:authority.envelope.contract_version,contractSha:authority.envelope_sha256,
        profileId:authority.profile.profile_id,profileSha:authority.profile_sha256,
        viewportAuthority:{id:viewportAuthority.authority_ref_id,path:viewportAuthority.path,sha256:viewportAuthority.sha256},
        formalViewports:responsive.responsive_profiles_to_verify.map(row=>({width:row.width_dp,height:row.height_dp})),fontScales:responsive.font_scale_profiles,
        mappingSha:root.querySelector("#eh-EmptyHomeRoot")?.dataset.mappingSha256,w2BindingSha:board.dataset.w2BindingSha256,
        treeNodeCount:tree.nodes.length,measuredCount:rows.length,treeNodes:tree.nodes.map(node=>({id:node.nodeId,parent:node.parentId,visible:node.visible,measurable:node.measurable,renderedDomId:node.renderedDomId})),
        measurements:rows,resources,imageLoads,brokenImages,layoutMetrics:JSON.parse(board.dataset.layoutMetrics),rippleCount:Number(board.dataset.rippleCount),
        authorityGaps:Number(board.querySelector("#eh-EmptyHomeRoot").dataset.authorityGaps),referenceSha:board.dataset.visualReferenceSha};
    },expectedSha);
    assert(preflight.pageId==="home_empty_v2"&&preflight.workspaceKind==="structured-dom-candidate","the page must be rendered by the shared structured DOM adapter");
    assert(preflight.treeNodeCount>=20,`expected full component tree, got ${preflight.treeNodeCount}`);
    assert(preflight.measuredCount===preflight.treeNodes.filter(node=>node.visible&&node.measurable).length,"every visible measurable tree node must map to actual DOM");
    assert(preflight.resources.length>=9&&preflight.resources.every(resource=>resource.status!=="MISSING"&&resource.status!=="BLOCKED_INTEGRITY"),"all used bound resources must resolve and pass integrity/publishing checks");
    assert(preflight.resources.every(resource=>resource.resource_ref?.publication_permission?.state==="PUBLIC_BY_EXISTING_PAGES_RULE"),"every published preview resource must have an explicit W2 permission");
    assert(preflight.brokenImages.length===0,`browser image load failure: ${JSON.stringify(preflight.brokenImages)}`);
    assert(preflight.rippleCount===1,"layer authority requires exactly one ripple");
    baseEvidence.mapping_sha256=preflight.mappingSha;
    baseEvidence.w2_binding_sha256=preflight.w2BindingSha;
    baseEvidence.resource_binding_sha256=preflight.w2BindingSha;
    baseEvidence.contract_id=preflight.contractId;
    baseEvidence.contract_version=preflight.contractVersion;
    baseEvidence.contract_sha256=preflight.contractSha;
    baseEvidence.profile_id=preflight.profileId;
    baseEvidence.profile_sha256=preflight.profileSha;
    baseEvidence.matrix_authority=preflight.viewportAuthority;
    baseEvidence.formal_matrix_definition={viewports:preflight.formalViewports,font_scales:preflight.fontScales,
      source_path:preflight.viewportAuthority.path,source_sha256:preflight.viewportAuthority.sha256};
    const formalCases=preflight.formalViewports.flatMap(({width,height})=>preflight.fontScales.map(fontScale=>({width,height,fontScale})));
    baseEvidence.matrix=formalCases.map(({width,height,fontScale})=>({
      case_id:`EMPTY_HOME_${width}x${height}_FONT_${fontScale}`,
      page_id:"home_empty_v2",
      viewport:{width,height},
      font_scale:fontScale,
      source_sha:expectedSha,
      run_id:runId,
      matrix_source_path:preflight.viewportAuthority.path,
      matrix_source_sha256:preflight.viewportAuthority.sha256,
      result:"NOT_RUN"
    }));
    baseEvidence.matrix_summary={expected:formalCases.length,executed:0,pass:0,fail:0,not_run:formalCases.length,
      source_path:preflight.viewportAuthority.path,source_sha256:preflight.viewportAuthority.sha256};
    baseEvidence.resource_statuses=preflight.resources;
    baseEvidence.browser_url=args.url?`${managerUrl}${ROUTE}`:"LOCAL_SITE_BUILD_ONLY_NOT_DEPLOYED";
    baseEvidence.authority_gaps=AUTHORITY_GAPS_FOR_REPORT();
    assert(preflight.authorityGaps===baseEvidence.authority_gaps.length,"authority gap report count differs from the page renderer");
    baseEvidence.differences=AUTHORITY_DIFFERENCE_REPORT();
    baseEvidence.preflight={tree_node_count:preflight.treeNodeCount,measured_count:preflight.measuredCount,reference_sha256:preflight.referenceSha,profile_status:preflight.profileStatus,
      profile_id:preflight.profileId,profile_sha256:preflight.profileSha,workspace_kind:preflight.workspaceKind};
    baseEvidence.component_bounds_report="EmptyHome_Component_Bounds_Report_V1.json";
    const componentBounds={schema:"YUJIAN_EMPTY_HOME_COMPONENT_BOUNDS_V1",page_id:"home_empty_v2",source_sha:expectedSha,
      mapping_sha256:preflight.mappingSha,w2_binding_sha256:preflight.w2BindingSha,coordinate_units:{reference:"reference_canvas_px",browser:"css_px",native:"NOT_VERIFIED"},
      baseline:preflight.measurements,viewports:[]};
    const screenshotRefs=[];

    const treeButton=page.locator('[data-workspace-tree-node][data-node-id="FishingLine"]');
    await treeButton.click();
    assert(await page.locator('[data-workspace-inspector]').getAttribute("data-selected-node-id")==="FishingLine","tree selection must update the actual Inspector");
    assert(await page.locator("#eh-FishingLine[data-workspace-selected]").count()===1,"tree selection must highlight the real DOM/SVG component");
    await page.locator("#eh-HeroCopy").click({position:{x:12,y:12}});
    assert(await page.locator('[data-workspace-inspector]').getAttribute("data-selected-node-id")==="HeroCopy","canvas click must locate the corresponding tree layer");

    const updateMatrixSummary=()=>{
      const pass=baseEvidence.matrix.filter(row=>row.result==="PASS").length;
      const fail=baseEvidence.matrix.filter(row=>row.result==="FAIL").length;
      const executed=pass+fail;
      baseEvidence.matrix_summary={...baseEvidence.matrix_summary,expected:formalCases.length,executed,pass,fail,
        not_run:formalCases.length-executed};
    };
    for (const {width,height,fontScale} of formalCases) {
      activeMatrixRow=baseEvidence.matrix.find(row=>row.case_id===`EMPTY_HOME_${width}x${height}_FONT_${fontScale}`);
      activeMatrixRow.result="IN_PROGRESS";
      activeMatrixRow.started_at=new Date().toISOString();
      updateMatrixSummary();
      await page.setViewportSize({width,height});
      await page.waitForFunction(({w,h})=>window.innerWidth===w&&window.innerHeight===h,{w:width,h:height});
      const preset=page.locator("[data-empty-viewport-preset]");
      await preset.selectOption(`${width}x${height}@${fontScale}`);
      await page.waitForFunction(({w,h,f})=>{
        const root=document.querySelector("[data-page-workspace-root]"),board=root?.querySelector("[data-workspace-artboard]");
        return root?.dataset.viewState==="EMPTY_DEFAULT"&&Number(root.dataset.fontScale)===f&&Number(board?.dataset.viewport?.split("x")[0])===w&&Number(board?.dataset.viewport?.split("x")[1])===h;
      },{w:width,h:height,f:fontScale},{timeout:15000});
      const caseEvidence=await page.evaluate(()=>{
        const board=document.querySelector("[data-workspace-artboard]"),root=board.querySelector("#eh-EmptyHomeRoot");
        const rows=window.PageWorkspaceAdapters.measureElements();
        const byId=Object.fromEntries(rows.map(row=>[row.node_id,row]));
        const metrics=JSON.parse(board.dataset.layoutMetrics);
        const width=metrics.viewport.width,height=metrics.viewport.height;
        const uiIds=["Header","HeroCopy","CapturePrompt","AccountEntry","PrimaryCaptureButton","AlbumAction"];
        const ui=Object.fromEntries(uiIds.map(id=>[id,byId[id]?.bounds||null]));
        const outsideViewport=Object.entries(ui).filter(([,bounds])=>bounds&&(
          bounds.x < -0.5||bounds.y < -0.5||bounds.right > width+0.5||bounds.bottom > height+0.5)).map(([id])=>id);
        const textIds=["Brand","Subtitle","AccountEntry","CapturePrompt","AlbumAction"];
        const textOverflow=textIds.filter(id=>{
          const node=document.querySelector(`#eh-${CSS.escape(id)}`);
          return node&&(node.scrollWidth>node.clientWidth+1||node.scrollHeight>node.clientHeight+1);
        });
        const fishingIds=["FishingRod","FishingLine","BobberBody","WaterContact","Ripple","BobberReflection"];
        const fishingOutside=fishingIds.filter(id=>{
          const bounds=byId[id]?.bounds;
          return bounds&&(bounds.x<-.5||bounds.y<-.5||bounds.right>width+.5||bounds.bottom>height+.5);
        });
        const line=byId.FishingLine?.bounds,rod=byId.FishingRod?.bounds,bobber=byId.BobberBody?.bounds;
        const contact=byId.WaterContact?.bounds,reflection=byId.BobberReflection?.bounds,ripple=byId.Ripple?.bounds;
        const lineEndContactGap=line&&contact?Math.hypot(contact.x-line.right,contact.y-line.bottom):null;
        const bobberContact={horizontal_inside:contact&&bobber?contact.x>=bobber.x-.5&&contact.x<=bobber.right+.5:false,
          vertical_gap:contact&&bobber?Math.abs(contact.y-bobber.bottom):null};
        const reflectionGap=contact&&reflection?Math.abs(reflection.y-contact.y):null;
        const rippleContainsContact=contact&&ripple?contact.x>=ripple.x&&contact.x<=ripple.right&&contact.y>=ripple.y&&contact.y<=ripple.bottom:false;
        const layerOrder=[byId.FishingLine?.layer.z_index,byId.BobberReflection?.layer.z_index,byId.Ripple?.layer.z_index,bobber?getComputedStyle(document.querySelector("#eh-BobberBody")).zIndex:null].map(Number);
        const overflow={outside_viewport:outsideViewport,text_overflow:textOverflow,scene_crop_expected:true};
        const boardBounds=board.getBoundingClientRect();
        return {viewport:metrics.viewport,font_scale:metrics.font_scale,scene_transform:metrics.scene_transform,profile_width_class:board.dataset.profileWidthClass,
          root_size:{width:root.getBoundingClientRect().width,height:root.getBoundingClientRect().height},overflow,ui_bounds:ui,
          fishing:{rod:rod,line,bobber,contact,ripple,reflection,composition_outside_viewport:fishingOutside,line_end_contact_gap:lineEndContactGap,
            bobber_contact_alignment:bobberContact,reflection_contact_gap:reflectionGap,ripple_contains_contact:rippleContainsContact,layer_order:layerOrder},
          hero:byId.HeroCopy?.bounds,prompt:byId.CapturePrompt?.bounds,camera:byId.PrimaryCaptureButton?.bounds,album:byId.AlbumAction?.bounds,
          deviations:Object.fromEntries(Object.entries(byId).map(([key,row])=>[key,row.deviation])),inspectorNode:document.querySelector("[data-workspace-inspector]").dataset.selectedNodeId,
          promptTypography:byId.CapturePrompt?.typography,sourceSha:root.dataset.sourceSha,unit:byId.FishingRod?.bounds.unit,
          node_bounds:rows, cameraTouch:{width:byId.PrimaryCaptureButton?.bounds.width,height:byId.PrimaryCaptureButton?.bounds.height},boardOrigin:{x:boardBounds.x,y:boardBounds.y}};
      });
      Object.assign(activeMatrixRow,caseEvidence);
      assert(caseEvidence.overflow.outside_viewport.length===0&&caseEvidence.overflow.text_overflow.length===0,`responsive UI overflow at ${width}x${height} @${fontScale}: ${JSON.stringify(caseEvidence.overflow)}`);
      assert(caseEvidence.unit==="css_px","DOM measurements must be reported as CSS px");
      assert(caseEvidence.inspectorNode==="HeroCopy","viewport changes must retain a measurable current selection");
      assert(caseEvidence.fishing.rod&&caseEvidence.fishing.line&&caseEvidence.fishing.bobber&&caseEvidence.fishing.contact&&caseEvidence.fishing.ripple,"fishing composition must map to separate measured DOM nodes");
      assert(caseEvidence.fishing.composition_outside_viewport.length===0,`fishing composition clipped at ${width}x${height}: ${JSON.stringify(caseEvidence.fishing.composition_outside_viewport)}`);
      assert(Math.abs(caseEvidence.fishing.line.x-(caseEvidence.fishing.rod.x+337*caseEvidence.scene_transform.scale))<1.5,"fishing line must use the same scene transform as the rod");
      assert(Math.abs(caseEvidence.fishing.line.y-(caseEvidence.fishing.rod.y+4*caseEvidence.scene_transform.scale))<1.5,"fishing line start must align with the registered rod-tip point");
      assert(caseEvidence.fishing.line_end_contact_gap<=5*caseEvidence.scene_transform.scale,`fishing line endpoint misses water-contact anchor at ${width}x${height}`);
      assert(caseEvidence.fishing.bobber_contact_alignment.horizontal_inside&&caseEvidence.fishing.bobber_contact_alignment.vertical_gap<=1.5,`bobber does not meet water contact at ${width}x${height}`);
      assert(caseEvidence.fishing.reflection_contact_gap<=2*caseEvidence.scene_transform.scale,`bobber reflection is detached from water contact at ${width}x${height}`);
      assert(caseEvidence.fishing.ripple_contains_contact,`water ripple does not contain contact point at ${width}x${height}`);
      assert(caseEvidence.fishing.layer_order[0]<caseEvidence.fishing.layer_order[1]&&caseEvidence.fishing.layer_order[1]<caseEvidence.fishing.layer_order[2]&&caseEvidence.fishing.layer_order[2]<caseEvidence.fishing.layer_order[3],`fishing layer occlusion order is invalid: ${caseEvidence.fishing.layer_order}`);
      assert(caseEvidence.cameraTouch.width>0&&caseEvidence.cameraTouch.height>0,"camera button actual DOM bounds must be measured");
      const shot=resolve(args.outputDir,`EmptyHome_${width}x${height}_font-${fontScale}.png`);
      const capture=await screenshotRootViewport(page,shot,width,height);
      const shotSize=capture.size;
      assert(shotSize.width===width*3&&shotSize.height===height*3,`MATRIX_SCREENSHOT_PIXEL_SCALE_MISMATCH: ${width}x${height}: ${JSON.stringify({shotSize,captureBounds:capture.bounds_css_px})}`);
      Object.assign(activeMatrixRow,caseEvidence,{
        case_id:activeMatrixRow.case_id,page_id:"home_empty_v2",source_sha:expectedSha,run_id:runId,
        matrix_source_path:preflight.viewportAuthority.path,matrix_source_sha256:preflight.viewportAuthority.sha256,
        state_id:"EMPTY_DEFAULT",
        browser_url:args.url?page.url():"LOCAL_SITE_BUILD_ONLY_NOT_DEPLOYED",
        browser_viewport_css_px:await page.evaluate(()=>({width:window.innerWidth,height:window.innerHeight,device_scale_factor:window.devicePixelRatio})),
        viewport_width:width,viewport_height:height,font_scale:fontScale,
        candidate_source_sha:expectedSha,site_build_sha:expectedSha,
        dom_report:"EmptyHome_Component_Bounds_Report_V1.json",
        screenshot_rendered:{path:shot.split("/").at(-1),sha256:await sha256File(shot),pixel_width:shotSize.width,pixel_height:shotSize.height,device_scale_factor:3,capture_bounds_css_px:capture.bounds_css_px},
        screenshot_reference:{path:"design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png",sha256:preflight.referenceSha},
        geometry_result:"PASS_DOM_MEASURED_VISUAL_REVIEW_PENDING",
        resource_result:"PASS_W2_BOUND_RESOURCES_VERIFIED",
        responsive_result:"PASS_NO_DECLARED_UI_OVERFLOW",
        visual_review_status:"USER_VISUAL_ACCEPTANCE_PENDING",
        native_runtime_status:"NOT_VERIFIED",
        user_acceptance_status:"USER_VISUAL_ACCEPTANCE_PENDING",
        result:"PASS",completed_at:new Date().toISOString()
      });
      screenshotRefs.push(activeMatrixRow.screenshot_rendered);
      componentBounds.viewports.push({viewport:activeMatrixRow.viewport,font_scale:activeMatrixRow.font_scale,node_bounds:activeMatrixRow.node_bounds});
      updateMatrixSummary();
      activeMatrixRow=null;
    }
    assert(baseEvidence.matrix_summary.expected===baseEvidence.matrix_summary.pass&&baseEvidence.matrix_summary.fail===0&&baseEvidence.matrix_summary.not_run===0,
      `FORMAL_MATRIX_CASE_COUNT_MISMATCH: ${JSON.stringify(baseEvidence.matrix_summary)}`);

    await page.locator("[data-empty-viewport-preset]").selectOption("360x640@1");
    await page.waitForFunction(()=>document.querySelector("[data-workspace-artboard]")?.dataset.viewport==="360x640");
    await page.locator("[data-empty-compare-mode]").selectOption("structured");
    const originalFrozen=resolve(args.root,"design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png");
    const frozenCopy=resolve(args.outputDir,"EmptyHome_Frozen_V2_2_Original.png");
    await copyFile(originalFrozen,frozenCopy);
    assert(await sha256File(frozenCopy)===preflight.referenceSha,"frozen V2.2 evidence copy SHA differs from the W2-verified reference");
    const compareStructured=resolve(args.outputDir,"EmptyHome_Structured_1080x1920.png");
    const compareCapture=await screenshotRootViewport(page,compareStructured,360,640,45000);
    const compareSize=compareCapture.size;
    assert(compareSize.width===1080&&compareSize.height===1920,`REFERENCE_COMPARISON_PIXEL_SIZE_MISMATCH: ${JSON.stringify(compareSize)}`);
    for (const mode of ["frozen","overlay","difference"]) {
      await page.locator("[data-empty-compare-mode]").selectOption(mode);
      const comparePath=resolve(args.outputDir,`EmptyHome_Compare_${mode}_1080x1920.png`);
      const capture=await screenshotRootViewport(page,comparePath,360,640,45000);
      const size=capture.size;
      assert(size.width===1080&&size.height===1920,`REFERENCE_COMPARISON_PIXEL_SIZE_MISMATCH: ${mode}: ${JSON.stringify(size)}`);
    }
    await page.locator("[data-empty-compare-mode]").selectOption("structured");
    const comparisonFiles=[];
    for(const mode of ["structured","frozen","overlay","difference"]){
      const path=resolve(args.outputDir,mode==="structured"?"EmptyHome_Structured_1080x1920.png":`EmptyHome_Compare_${mode}_1080x1920.png`);
      comparisonFiles.push({mode,path:path.split("/").at(-1),sha256:await sha256File(path)});
    }
    baseEvidence.frozen_visual={path:frozenCopy.split("/").at(-1),sha256:await sha256File(frozenCopy),authority_sha256:preflight.referenceSha};
    baseEvidence.reference_comparison={logical_viewport_css_px:{width:360,height:640},browser_device_scale_factor:3,capture_bounds_css_px:compareCapture.bounds_css_px,screenshot_pixels:compareSize,
      native_density_claim:false,source_ratio_preserved:true};
    baseEvidence.comparison_images=comparisonFiles;
    baseEvidence.screenshot_references=screenshotRefs;

    const diagnostic=[];
    for (const [width,height] of [[320,480],[360,640],[390,844],[430,932]]) {
      await page.locator("[data-workspace-width]").fill(String(width));
      await page.locator("[data-workspace-height]").fill(String(height));
      await page.locator("[data-workspace-apply-viewport]").click();
      await page.waitForFunction(({w,h})=>document.querySelector("[data-workspace-artboard]")?.dataset.viewport===`${w}x${h}`,{w:width,h:height});
      diagnostic.push(await page.evaluate(()=>JSON.parse(document.querySelector("[data-workspace-artboard]").dataset.layoutMetrics)));
    }
    baseEvidence.diagnostic_matrix=diagnostic.map(row=>({...row,case_status:"DIAGNOSTIC_ONLY_NOT_FORMAL_SUPPORT"}));

    const negative=async(label, mutate) => {
      const url=`${managerUrl}?negative=${encodeURIComponent(label)}${ROUTE}`;
      await mutate(page.route.bind(page));
      await page.goto(url,{waitUntil:"domcontentloaded",timeout:45000});
      await page.locator("#pageList .module-item").first().waitFor({state:"visible",timeout:30000});
      await page.locator("[data-page-workspace-root]").waitFor({state:"visible",timeout:30000});
      await page.waitForFunction(()=>document.querySelector("[data-page-workspace-root]")?.dataset.viewState==="ERROR",null,{timeout:45000});
      const message=await page.locator("[data-workspace-error]").innerText();
      assert(message.length>0,`${label}: no explicit fail-closed reason was reported`);
      baseEvidence.negative_cases.push({case:label,result:"EXPECTED_FAIL_CLOSED",error:message});
      await page.unrouteAll({behavior:"ignoreErrors"});
    };
    const routeForAsset=(pattern,handler)=>async route=>{
      await route("**/*",async requestRoute=>{
        if(new URL(requestRoute.request().url()).pathname.endsWith(pattern)) return handler(requestRoute);
        return requestRoute.continue();
      });
    };
    await negative("required_resource_missing",routeForAsset("/design/pages/home/empty_home/shared/assets/fishing/rod_master.png",route=>route.fulfill({status:404,contentType:"text/plain",body:"missing"})));
    await negative("component_dom_mapping_missing",async route=>{
      const mapping=JSON.parse(await readFile(resolve(args.root,"design/manager/empty_home_v22_component_mapping.json"),"utf8"));
      const visit=node=>{if(node.nodeId==="FishingLine")node.renderedDomId="eh-MissingFishingLine";(node.children||[]).forEach(visit);};
      visit(mapping.component_tree);
      const payload=Buffer.from(`${JSON.stringify(mapping)}\n`);
      const modifiedSha=createHash("sha256").update(payload).digest("hex");
      await route("**/*",async requestRoute=>{
        const pathname=new URL(requestRoute.request().url()).pathname;
        if(pathname.endsWith("/design/manager/empty_home_v22_component_mapping.json")){
          const response=await requestRoute.fetch();
          await requestRoute.fulfill({response,body:payload});
          return;
        }
        if(pathname.endsWith("/design/manager/empty_home_v22_workspace.js")){
          const response=await requestRoute.fetch();
          const script=(await response.text()).replace(/const MAPPING_SHA256 = "[a-f0-9]{64}";/,`const MAPPING_SHA256 = "${modifiedSha}";`);
          await requestRoute.fulfill({response,body:script});
          return;
        }
        await requestRoute.continue();
      });
    });
    const capturePath="/design/system/components/primary_capture_button/assets/capture_button_main_v1.png";
    await negative("resource_sha_mismatch",routeForAsset(capturePath,async route=>{
      const response=await route.fetch(); const bytes=await response.body(); bytes[bytes.length-1]^=1;
      await route.fulfill({response,body:bytes,headers:{...response.headers(),"content-type":"image/png"}});
    }));
    await negative("frozen_resource_replaced",routeForAsset("/design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png",async route=>{
      const response=await route.fetch(); const bytes=await response.body(); bytes[bytes.length-1]^=1;
      await route.fulfill({response,body:bytes,headers:{...response.headers(),"content-type":"image/png"}});
    }));
    await negative("resource_dimensions_mismatch",routeForAsset("/design/pages/home/empty_home/Page_Resource_Binding_V1.json",async route=>{
      const response=await route.fetch(); const body=await response.json();
      const asset=body.asset_bindings.find(row=>row.asset_id==="home_empty_v2_capture_button_v1_1");
      asset.pixel_dimensions.width+=1;
      const source=asset.source_authorities.find(row=>row.authority_id==="capturebutton");
      delete source.field_map["pixel_dimensions.width"];
      await route.fulfill({response,contentType:"application/json",body:JSON.stringify(body)});
    }));
    await negative("resource_source_mismatch",routeForAsset("/design/pages/home/empty_home/Page_Resource_Binding_V1.json",async route=>{
      const response=await route.fetch(); const body=await response.json();
      const asset=body.asset_bindings.find(row=>row.asset_id==="home_empty_v2_rod_master");
      asset.source_path="design/pages/home/normal_home/shared/assets/fishing/rod_master.png";
      await route.fulfill({response,contentType:"application/json",body:JSON.stringify(body)});
    }));
    await negative("publication_permission_missing",routeForAsset("/design/pages/home/empty_home/Page_Resource_Binding_V1.json",async route=>{
      const response=await route.fetch(); const body=await response.json();
      const asset=body.asset_bindings.find(row=>row.asset_id==="home_empty_v2_capture_button_v1_1");
      asset.publication_permission.state="NOT_CONFIRMED";
      await route.fulfill({response,contentType:"application/json",body:JSON.stringify(body)});
    }));
    await negative("source_authority_sha_mismatch",routeForAsset("/design/pages/home/empty_home/shared/contracts/runtime_manifest.json",async route=>{
      const response=await route.fetch(); const bytes=await response.body(); bytes[bytes.length-2]^=1;
      await route.fulfill({response,body:bytes,headers:{...response.headers(),"content-type":"application/json"}});
    }));

    const boundsReportPath=resolve(args.outputDir,"EmptyHome_Component_Bounds_Report_V1.json");
    await writeFile(boundsReportPath,JSON.stringify(componentBounds,null,2)+"\n");
    baseEvidence.component_bounds_report_sha256=await sha256File(boundsReportPath);
    baseEvidence.dom_report=baseEvidence.component_bounds_report;
    baseEvidence.dom_report_sha256=baseEvidence.component_bounds_report_sha256;
    baseEvidence.screenshot_reference=baseEvidence.frozen_visual;
    baseEvidence.screenshot_rendered=comparisonFiles.find(row=>row.mode==="structured");
    baseEvidence.overlay_reference=comparisonFiles.find(row=>row.mode==="overlay");
    baseEvidence.difference_reference=comparisonFiles.find(row=>row.mode==="difference");
    baseEvidence.geometry_result="PASS_DOM_MEASURED_VISUAL_REVIEW_PENDING";
    baseEvidence.resource_result="PASS_W2_BOUND_RESOURCES_VERIFIED";
    baseEvidence.responsive_result="PASS_PAGE_PROFILE_MATRIX_EXECUTED";
    assert(consoleErrors.length===0,`Chromium console errors: ${consoleErrors.join(" | ")}`);
    assert(pageErrors.length===0,`Chromium page errors: ${pageErrors.join(" | ")}`);
    assert(failedRequests.length===0,`Chromium failed requests: ${JSON.stringify(failedRequests)}`);
    baseEvidence.console_errors=consoleErrors;
    baseEvidence.page_errors=pageErrors;
    baseEvidence.failed_requests=failedRequests;
    baseEvidence.status="PASS_STRUCTURAL_MATRIX_VISUAL_REVIEW_PENDING";
    baseEvidence.completed_at=new Date().toISOString();
    await writeFile(resolve(args.outputDir,"EmptyHome_Structured_HiFi_Evidence_V1.json"),JSON.stringify(baseEvidence,null,2)+"\n");
    console.log(JSON.stringify({status:baseEvidence.status,source_sha:expectedSha,matrix_cases:baseEvidence.matrix.length,negative_cases:baseEvidence.negative_cases.length,output_dir:args.outputDir},null,2));
  } catch(error) {
    baseEvidence.status="FAIL";
    baseEvidence.failure=error?.stack||String(error);
    if(activeMatrixRow){
      activeMatrixRow.result="FAIL";
      activeMatrixRow.failure=error?.message||String(error);
      activeMatrixRow.completed_at=new Date().toISOString();
      const pass=baseEvidence.matrix.filter(row=>row.result==="PASS").length;
      const fail=baseEvidence.matrix.filter(row=>row.result==="FAIL").length;
      const expected=baseEvidence.matrix_summary?.expected??baseEvidence.matrix.length;
      baseEvidence.matrix_summary={...baseEvidence.matrix_summary,expected,executed:pass+fail,pass,fail,not_run:expected-pass-fail};
    }
    baseEvidence.console_errors=consoleErrors;
    baseEvidence.page_errors=pageErrors;
    baseEvidence.failed_requests=failedRequests;
    baseEvidence.completed_at=new Date().toISOString();
    await writeFile(resolve(args.outputDir,"EmptyHome_Structured_HiFi_Evidence_V1.json"),JSON.stringify(baseEvidence,null,2)+"\n");
    throw error;
  } finally {
    await browser.close();
    if(server) await new Promise(resolvePromise=>server.close(resolvePromise));
  }
}

function AUTHORITY_GAPS_FOR_REPORT(){return [
  "No approved standalone brand wordmark is bound; the current DOM text remains a candidate representation.",
  "Approved account and album icons are not bound; the candidate omits those icons.",
  "Frozen reference includes Android system chrome; Chromium preview does not simulate Native status/navigation bars.",
  "Sun particle coordinates have no frozen static authority; the motion layer is NOT_RUN in this static candidate.",
  "Cloud position and alpha follow the existing Android Renderer implementation reference and need visual review.",
  "Dynamic Sun Beam is NOT_RUN because the scene master already contains ambient sun glow; no static beam is overlaid.",
  "Header horizontal inset uses shared AdaptiveLayoutContract values because the Empty Home visual contract has no page-specific inset; review required."
];}

function AUTHORITY_DIFFERENCE_REPORT(){return [
  {page_id:"home_empty_v2",component_id:"Brand",category:"AUTHORITY_GAP",authority:"design/pages/home/empty_home/shared/contracts/asset_manifest.json",expected:"Approved standalone brand asset or confirmed DOM-text rule",actual:"DOM text candidate; no W2-bound logo asset",numeric_deviation:null,root_cause:"No page-owned approved brand source is registered.",correction_status:"REVIEW_REQUIRED",approval_required:true},
  {page_id:"home_empty_v2",component_id:"AccountEntry / AlbumAction",category:"EVIDENCE_MISSING",authority:"design/pages/home/empty_home/shared/contracts/asset_manifest.json",expected:"Approved account and album icons if required by V2.2",actual:"Icons omitted; real DOM buttons remain",numeric_deviation:null,root_cause:"No approved icon bindings are present.",correction_status:"REVIEW_REQUIRED",approval_required:true},
  {page_id:"home_empty_v2",component_id:"EnvironmentScene",category:"AUTHORITY_GAP",authority:"Design Manager browser preview",expected:"Frozen visual including Android system chrome",actual:"Page content rendered without Android status/navigation bars",numeric_deviation:null,root_cause:"Browser CSS viewport does not provide Native system bars or safe drawing insets.",correction_status:"NOT_SIMULATED",approval_required:true},
  {page_id:"home_empty_v2",component_id:"SunParticles",category:"AUTHORITY_GAP",authority:"design/pages/home/empty_home/shared/contracts/motion_contract.json",expected:"Motion-generated particle positions only in an authorized motion state",actual:"Static candidate does not render particles",numeric_deviation:null,root_cause:"No frozen static particle coordinates exist.",correction_status:"NOT_RUN",approval_required:true},
  {page_id:"home_empty_v2",component_id:"CloudAtmosphere",category:"AUTHORITY_GAP",authority:"app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt",expected:"Page-specific frozen cloud bounds and alpha",actual:"Existing Android renderer reference used in candidate",numeric_deviation:null,root_cause:"No independent page anchor for static cloud placement is registered.",correction_status:"REVIEW_REQUIRED",approval_required:true},
  {page_id:"home_empty_v2",component_id:"SunBeam",category:"AUTHORITY_GAP",authority:"design/pages/home/empty_home/shared/contracts/motion_contract.json",expected:"Beam only in the corresponding authorized running motion state",actual:"Hidden and NOT_RUN in static candidate",numeric_deviation:null,root_cause:"Scene base already contains ambient sun glow; static overlay would duplicate it.",correction_status:"NOT_RUN",approval_required:true},
  {page_id:"home_empty_v2",component_id:"Header",category:"AUTHORITY_GAP",authority:"app/src/main/java/com/yujian/ai/ui/adaptive/AdaptiveLayoutContract.kt",expected:"Page-owned horizontal inset value",actual:"Shared adaptive inset used as candidate value",numeric_deviation:null,root_cause:"Empty Home visual contract does not set a horizontal inset.",correction_status:"REVIEW_REQUIRED",approval_required:true}
];}

main().catch(error=>{console.error(error?.stack||String(error));process.exitCode=1;});
