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
const BROWSER_REFERENCE_SAFE_INSETS_DP = {top:36,bottom:10};
const CANDIDATE_PROMPT_LINE_HEIGHT_SP = 24;

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
async function readJson(root, path) { return JSON.parse(await readFile(resolve(root, path), "utf8")); }
async function buildAuthorityAudit(root, preflight) {
  const paths={authorityManifest:"design/pages/home/empty_home/shared/contracts/authority_manifest.json",
    assetManifest:"design/pages/home/empty_home/shared/contracts/asset_manifest.json",
    registry:"design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json",
    iconInventory:"design/system/icon_library_v0_1/icon_inventory_v0_1.json",
    frozenIcons:"design/system/icon_library_v0_1/frozen_assets_v1.json",
    pageBinding:"design/pages/home/empty_home/Page_Resource_Binding_V1.json",
    typography:"design/system_v3/04_Design_Tokens/Typography/Typography_Tokens_V3_FINAL.json",
    typographySystem:"design/system_v3/04_Design_Tokens/Typography/Typography_System_V3_FINAL.md",
    featureSpec:"design/pages/home/empty_home/spec/Empty_Home_Feature_Spec_V2.md",
    experienceRegistry:"design/registry/experience_registry_v1.json",
    brandManifest:"design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary_manifest.json",
    brandAsset:"design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png"};
  const [authority,manifest,registry,icons,frozenIcons,binding,typography,brandManifest,experience]=await Promise.all([
    readJson(root,paths.authorityManifest),readJson(root,paths.assetManifest),readJson(root,paths.registry),readJson(root,paths.iconInventory),
    readJson(root,paths.frozenIcons),
    readJson(root,paths.pageBinding),readJson(root,paths.typography),readJson(root,paths.brandManifest),readJson(root,paths.experienceRegistry)]);
  const registryRows=registry.entries||registry.assets||[];
  const lifecycleTargets={
    SceneBase:"design/pages/home/empty_home/shared/assets/background/scene_base_master.png",
    CloudAtmosphere:"design/pages/home/empty_home/shared/assets/atmosphere/cloud_master.png",
    FishingRod:"design/pages/home/empty_home/shared/assets/fishing/rod_master.png",
    BobberBody:"design/pages/home/empty_home/shared/assets/fishing/bobber_master.png",
    Ripple:"design/pages/home/empty_home/shared/assets/fishing/ripple_master_mask.png"};
  const lifecycle=Object.entries(lifecycleTargets).map(([componentId,path])=>{
    const pageAsset=(manifest.assets||[]).find(row=>row.path===path);
    const registryAsset=registryRows.find(row=>row.path===path);
    return {component_id:componentId,path,asset_id:pageAsset?.asset_id||registryAsset?.asset_id||null,
      sha256:pageAsset?.sha256||registryAsset?.sha256||null,registry_lifecycle:registryAsset?.status||registryAsset?.lifecycle_status||"UNRESOLVED",
      page_manifest_status:pageAsset?.status||pageAsset?.lifecycle_status||"NOT_DECLARED",
      visual_authority_status:authority.visual_revision_status,
      conflict:authority.visual_revision_status==="APPROVED_FROZEN"&&["REVIEW","UNRESOLVED"].includes(registryAsset?.status||registryAsset?.lifecycle_status)};
  });
  const iconIds={GALLERY:"design/system/icon_library_v0_1/assets/ic_gallery_v1.svg",CHEVRON_RIGHT:"design/system/icon_library_v0_1/assets/ic_chevron_right_v1.svg"};
  const iconEntries=icons.entries||[];
  const frozenIconEntries=frozenIcons.assets||[];
  const iconAudit={};
  for(const [id,path] of Object.entries(iconIds)){
    const entry=iconEntries.find(row=>row.id===id)||null;
    const frozenEntry=frozenIconEntries.find(row=>row.id===id)||null;
    const registryAsset=registryRows.find(row=>row.path===path);
    const bindingAsset=(binding.asset_bindings||[]).find(row=>row.source_path===path||row.asset_id===registryAsset?.asset_id);
    const actualSha=await sha256File(resolve(root,path));
    iconAudit[id]={path,sha256:actualSha,inventory_state:entry?.asset_state||"NOT_LISTED",inventory_sha256:entry?.asset_sha256||null,
      frozen_status:frozenEntry?.status||"NOT_LISTED",frozen_sha256:frozenEntry?.sha256||null,
      registry_lifecycle:registryAsset?.status||registryAsset?.lifecycle_status||"UNRESOLVED",registry_sha256:registryAsset?.sha256||null,
      w2_binding_status:bindingAsset?"BOUND":"NOT_BOUND",binding_sha256:bindingAsset?.sha256||null,
      publication_permission:bindingAsset?.publication_permission?.state||"NOT_CONFIRMED",
      source_hashes_match:actualSha===entry?.asset_sha256&&actualSha===frozenEntry?.sha256&&actualSha===registryAsset?.sha256&&actualSha===bindingAsset?.sha256,
      lifecycle_conflict:entry?.asset_state==="FROZEN_DESIGN"&&frozenEntry?.status==="FROZEN"&&registryAsset?.status==="REVIEW",
      render_status:bindingAsset?"W2_BOUND_CANDIDATE":"BLOCKED_AUTHORITY_GAP"};
  }
  const promptToken=(typography.tokens||[]).find(row=>row.id==="type.body_small")||null;
  const albumToken=(typography.tokens||[]).find(row=>row.id==="type.caption")||null;
  const buttonToken=(typography.tokens||[]).find(row=>row.id==="type.button")||null;
  const normalHome=(experience.features||[]).find(row=>row.id==="home_normal_v1")||null;
  const emptyHome=(experience.features||[]).find(row=>row.id==="home_empty_v2")||null;
  const brandAssetSha=await sha256File(resolve(root,brandManifest.derived_path));
  return {visual_authority:{path:paths.authorityManifest,sha256:await sha256File(resolve(root,paths.authorityManifest)),status:authority.visual_revision_status,
      design_version:authority.design_version,visual_revision:authority.visual_revision},
    resource_lifecycle_conflicts:lifecycle,
    icons:{authority_paths:{inventory:paths.iconInventory,frozen_assets:paths.frozenIcons,asset_registry:paths.registry},items:iconAudit,
      status:Object.values(iconAudit).some(row=>row.w2_binding_status!=="BOUND"||!row.source_hashes_match)?"BLOCKED_W2_AUTHORITY_RECONCILIATION":
        Object.values(iconAudit).some(row=>row.lifecycle_conflict)?"LIFECYCLE_CONFLICT_REVIEW_PRESERVED":"RESOLVED"},
    brand:{status:"BLOCKED_AUTHORITY_GAP",typography_rule:"FROZEN_ARTWORK_ASSET",typography_authority:paths.typographySystem,
      standalone_asset:{path:brandManifest.derived_path,sha256:brandAssetSha,status:brandManifest.status,approval_status:brandManifest.approval_status,
        usage:brandManifest.usage,source_path:brandManifest.source_path,source_sha256:brandManifest.source_sha256},
      empty_home_authorized:brandManifest.usage?.includes("home_empty_v2")||false,
      required_resource:"Independent approved Empty Home wordmark asset/binding or explicit approved placement scope",
      approver:"Product Owner / Brand Design Authority (no named approver is recorded in the repository)"},
    typography:{authority_path:paths.typography,sha256:await sha256File(resolve(root,paths.typography)),prompt_token:promptToken,album_token:albumToken,button_token:buttonToken,
      prompt_line_height_override_sp:CANDIDATE_PROMPT_LINE_HEIGHT_SP,
      browser_font_mapping:"system-ui, sans-serif; browser-only representative",android_font_status:"NOT_VERIFIED",
      subtitle_status:"AUTHORITY_GAP: no explicit Empty Home page typography token or override"},
    content_state_boundary:{result:"NOT_APPLICABLE",state:"CONTENT_PRESENT",authority_path:paths.featureSpec,
      authority_sha256:await sha256File(resolve(root,paths.featureSpec)),reason:"Empty Home Feature Spec selects EMPTY when fishRecords.isEmpty(); normal Home owns the non-empty state. This W5 adapter renders EMPTY only.",
      empty_page_registry_entry:emptyHome?.id||null,normal_page_registry_entry:normalHome?.id||null,executed:false},
    system_bars:{browser_status_bar:"NOT_SIMULATED",browser_safe_insets:{...BROWSER_REFERENCE_SAFE_INSETS_DP,status:"BROWSER_REFERENCE_SCREENSHOT_ESTIMATE_NOT_NATIVE"},
      frozen_reference_status_bar:"PRESENT_IN_RASTER; boundary has no page-owned machine-readable authority",header_measurement:"Recorded per matrix case in CSS px",native_runtime_status:"NOT_VERIFIED"},
    measured_dom_preflight:{resource_count:preflight.resources?.length||0,resources:preflight.resources||[],w2_binding_sha256:preflight.w2BindingSha||null}};
}
async function resolveBehaviorState(root) {
  const envelopePath=resolve(root,"design/system_v3/07_Responsive_System/page_contract_examples/home_empty_v2.envelope.json");
  const envelope=JSON.parse(await readFile(envelopePath,"utf8"));
  const refs=envelope.state_scope?.included_state_refs||[];
  assert(envelope.page?.page_id==="home_empty_v2"&&refs.length===1,"W1 state scope must identify exactly one Empty Home behavior state authority");
  const stateRef=refs[0];
  const authority=(envelope.authority_refs||[]).find(row=>row.authority_ref_id===stateRef.authority_ref_id);
  assert(authority?.path&&authority?.sha256,"behavior-state authority must have an explicit path and SHA-256");
  const bytes=await readFile(resolve(root,authority.path));
  const digest=createHash("sha256").update(bytes).digest("hex");
  assert(digest===authority.sha256,"behavior-state authority SHA-256 differs from the W1 Envelope");
  const declaredLocator=stateRef.locator;
  assert(typeof declaredLocator==="string"&&declaredLocator.startsWith("heading:"),"behavior-state locator must reference a named authority heading");
  const lines=bytes.toString("utf8").split(/\r?\n/);
  const sectionFor=heading=>{
    const matches=[];
    lines.forEach((line,index)=>{
      const match=line.match(/^(#{1,6})[ \t]+(.+?)[ \t]*#*[ \t]*$/);
      if(match&&match[2].trim()===heading) matches.push({index,level:match[1].length});
    });
    if(matches.length!==1) return null;
    const {index,level}=matches[0];
    let end=lines.findIndex((line,lineIndex)=>lineIndex>index&&new RegExp(`^#{1,${level}}[ \t]+`).test(line));
    if(end<0) end=lines.length;
    return lines.slice(index+1,end).join("\n");
  };
  const heading=declaredLocator.slice("heading:".length).trim();
  let resolvedLocator=declaredLocator;
  let resolution="EXACT_LOCATOR";
  let section=sectionFor(heading);
  if(!section&&envelope.page.page_id==="home_empty_v2"&&stateRef.authority_ref_id==="empty-home-behavior"&&
    authority.path==="design/pages/home/empty_home/spec/Empty_Home_Feature_Spec_V2.md"&&declaredLocator==="heading:empty state"){
    const candidate=sectionFor("Scope");
    const selected=[...(candidate||"").matchAll(/\bselects[ \t]+`?([A-Z][A-Z0-9_]*)`?/g)].map(match=>match[1]);
    if(candidate&&/`?fishRecords\.isEmpty\(\)`?\s+selects\s+`?EMPTY`?/.test(candidate)&&selected.length===1&&selected[0]==="EMPTY"){
      section=candidate;
      resolvedLocator="heading:Scope";
      resolution="PAGE_SPECIFIC_EXPLICIT_STATE_MAPPING";
    }
  }
  assert(section,`behavior-state authority heading not found or not eligible for explicit page mapping: ${declaredLocator}`);
  const stateIds=[...section.matchAll(/\bselects[ \t]+`?([A-Z][A-Z0-9_]*)`?/g)].map(match=>match[1]);
  const unique=[...new Set(stateIds)];
  assert(unique.length===1,"behavior-state authority must define exactly one explicit selected state in its locator section");
  return {authority_ref_id:authority.authority_ref_id,path:authority.path,sha256:digest,
    declared_locator:declaredLocator,locator:resolvedLocator,resolution,behavior_state_id:unique[0]};
}
async function pngDimensions(path) {
  const bytes = await readFile(path);
  assert(bytes.length >= 24 && bytes.subarray(1, 4).toString("ascii") === "PNG", `EVIDENCE_SCREENSHOT_NOT_PNG: ${path}`);
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}
async function screenshotRootViewport(page, path, width, height, promptTextPath, timeout = 45000) {
  const locator = page.locator("#eh-EmptyHomeRoot");
  await locator.waitFor({ state: "visible", timeout });
  const isolation = await page.evaluate(({ width, height }) => {
    const root = document.querySelector("#eh-EmptyHomeRoot");
    const rectJSON = rect => ({ x: rect.left, y: rect.top, right: rect.right, bottom: rect.bottom, width: rect.width, height: rect.height });
    if (!root) return { error: "ROOT_MISSING" };
    const workspaceRect = rectJSON(root.getBoundingClientRect());
    const clipAncestors = [];
    for (let parent = root.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
      const style = getComputedStyle(parent);
      const clipX = ["hidden", "clip", "auto", "scroll"].includes(style.overflowX);
      const clipY = ["hidden", "clip", "auto", "scroll"].includes(style.overflowY);
      if (clipX || clipY) {
        const r = parent.getBoundingClientRect();
        const left = r.left + parent.clientLeft, top = r.top + parent.clientTop;
        const right = left + parent.clientWidth, bottom = top + parent.clientHeight;
        clipAncestors.push({ selector: parent.id ? `#${parent.id}` : parent.className?.toString?.().slice(0, 100) || parent.tagName,
          overflow_x: style.overflowX, overflow_y: style.overflowY,
          clips_root: (clipX && (workspaceRect.x < left - .5 || workspaceRect.right > right + .5)) ||
            (clipY && (workspaceRect.y < top - .5 || workspaceRect.bottom > bottom + .5)) });
      }
    }
    const required = ["eh-EnvironmentScene", "eh-CaptureActionGroup", "eh-PrimaryCaptureButton", "eh-AlbumAction"];
    const visible = id => {
      const node = root.querySelector(`#${CSS.escape(id)}`);
      if (!node) return false;
      const r = node.getBoundingClientRect(), style = getComputedStyle(node);
      return r.width > 0 && r.height > 0 && style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) > 0;
    };
    const missing = required.filter(id => !visible(id));
    const targetRect = root.getBoundingClientRect();
    if (Math.abs(targetRect.width - width) >= .01 || Math.abs(targetRect.height - height) >= .01) {
      return { error: "MATRIX_ROOT_BOUNDS_MISMATCH", workspaceRect, clipAncestors, missing };
    }
    const parent = root.parentNode;
    const marker = document.createComment("empty-home-capture-root-placeholder");
    parent.insertBefore(marker, root);
    const oldStyle = root.getAttribute("style"), hadCapture = root.hasAttribute("data-visual-capture");
    const oldCapture = root.getAttribute("data-visual-capture");
    const clean = root.querySelectorAll("[data-workspace-selected]");
    const overlay = document.createElement("div");
    overlay.className = "page-ws-artboard eh-v22-capture-isolation";
    overlay.dataset.emptyHomeCaptureIsolation = "true";
    Object.assign(overlay.style, { position: "fixed", left: "0", top: "0", width: `${width}px`, height: `${height}px`,
      overflow: "visible", transform: "none", zoom: "1", zIndex: "2147483647", background: "transparent", pointerEvents: "none" });
    root.dataset.visualCapture = "true";
    root.style.position = "absolute"; root.style.left = "0"; root.style.top = "0"; root.style.right = "auto"; root.style.bottom = "auto";
    overlay.appendChild(root); document.body.appendChild(overlay);
    window.__restoreEmptyHomeCaptureRoot = () => {
      marker.parentNode?.insertBefore(root, marker);
      marker.remove(); overlay.remove();
      if (oldStyle === null) root.removeAttribute("style"); else root.setAttribute("style", oldStyle);
      if (!hadCapture) root.removeAttribute("data-visual-capture"); else root.setAttribute("data-visual-capture", oldCapture);
      delete window.__restoreEmptyHomeCaptureRoot;
    };
    return { workspaceRect, clipAncestors, required_visible: required.filter(visible), missing, isolation_used: true,
      outside_workspace_chrome_included: false, selected_nodes_suppressed_for_visual_capture: clean.length };
  }, { width, height });
  assert(!isolation.error, `${isolation.error || "MATRIX_ROOT_ISOLATION_FAILED"}: ${JSON.stringify(isolation)}`);
  assert(isolation.missing.length === 0, `MATRIX_CAPTURE_CONTENT_MISSING: ${JSON.stringify(isolation.missing)}`);
  try {
    const isolatedBounds = await page.evaluate(() => {
      const root = document.querySelector("body > .eh-v22-capture-isolation #eh-EmptyHomeRoot");
      const rect = root?.getBoundingClientRect();
      const viewport = { width: innerWidth, height: innerHeight };
      return rect ? { x: rect.left, y: rect.top, right: rect.right, bottom: rect.bottom, width: rect.width, height: rect.height,
        viewport, fully_visible: rect.left >= 0 && rect.top >= 0 && rect.right <= viewport.width && rect.bottom <= viewport.height,
        prompt_text_visible: Boolean(root.querySelector("#eh-CapturePromptText")?.getClientRects().length),
        capture_visible: Boolean(root.querySelector("#eh-PrimaryCaptureButton")?.getClientRects().length),
        album_visible: Boolean(root.querySelector("#eh-AlbumAction")?.getClientRects().length) } : null;
    });
    assert(isolatedBounds?.fully_visible && isolatedBounds.prompt_text_visible && isolatedBounds.capture_visible && isolatedBounds.album_visible,
      `MATRIX_CAPTURE_ROOT_NOT_FULLY_VISIBLE: ${JSON.stringify(isolatedBounds)}`);
    await locator.screenshot({ path, scale: "device", animations: "disabled", timeout });
    const promptTextBounds = await page.evaluate(() => {
      const node = document.querySelector("#eh-CapturePromptText");
      if (!node) return null;
      const range = document.createRange(); range.selectNodeContents(node);
      const r = range.getBoundingClientRect(); const style = getComputedStyle(node.parentElement);
      return { x: r.x, y: r.y, width: r.width, height: r.height, font_size_css_px: style.fontSize,
        line_height_css_px: style.lineHeight, font_family_browser_mapping: style.fontFamily, text: node.textContent };
    });
    assert(promptTextBounds?.width > 0 && promptTextBounds?.height > 0, "CAPTURE_PROMPT_TEXT_RANGE_NOT_MEASURABLE");
    if (promptTextPath) await page.locator("#eh-CapturePromptText").screenshot({ path: promptTextPath, scale: "device", animations: "disabled", timeout });
    return { size: await pngDimensions(path), bounds_css_px: { x: isolatedBounds.x, y: isolatedBounds.y, width, height },
      visible_content: { scene: true, prompt_text: true, camera_button: true, album_entry: true }, prompt_text_bounds_css_px: promptTextBounds,
      workspace_visibility_before_isolation: { root_bounds_css_px: isolation.workspaceRect, clipping_ancestors: isolation.clipAncestors,
        clipped_in_workspace: isolation.clipAncestors.some(row => row.clips_root) },
      capture_isolation_used: true, outside_workspace_chrome_included: false,
      selected_overlay_suppressed: isolation.selected_nodes_suppressed_for_visual_capture };
  } finally {
    await page.evaluate(() => window.__restoreEmptyHomeCaptureRoot?.());
  }
}
function assertScreenshotCaptureEnvelope(size, width, height, label) {
  const minimum = { width: width * 3, height: height * 3 };
  const maximum = { width: (width + 1) * 3, height: (height + 1) * 3 };
  assert(size.width >= minimum.width && size.width <= maximum.width && size.height >= minimum.height && size.height <= maximum.height,
    `MATRIX_SCREENSHOT_CAPTURE_BOUNDS_INVALID: ${label}: ${JSON.stringify({ size, minimum, maximum })}`);
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
  const consoleErrorEvents=[];
  const pageErrors=[];
  const failedRequests=[];
  const httpErrors=[];
  let activeNegativeCase=null;
  const expectedMissingResourcePath="/design/pages/home/empty_home/shared/assets/fishing/rod_master.png";
  const expectedMissingResourceConsoleText="Failed to load resource: the server responded with a status of 404 (Not Found)";
  page.on("console",message=>{
    if(message.type()==="error")consoleErrorEvents.push({text:message.text(),negative_case:activeNegativeCase});
  });
  page.on("pageerror",error=>pageErrors.push(error.message));
  page.on("requestfailed",request=>failedRequests.push({url:request.url(),failure:request.failure()?.errorText||"failed"}));
  page.on("response",response=>{
    if(response.status()>=400)httpErrors.push({url:response.url(),status:response.status(),negative_case:activeNegativeCase});
  });
  const baseEvidence={schema:"YUJIAN_PAGE_EVIDENCE_MANIFEST_V1",page_id:"home_empty_v2",source_sha:expectedSha,candidate_source_sha:expectedSha,
    contract_id:null,contract_version:null,contract_sha256:null,profile_id:null,profile_sha256:null,resource_binding_sha256:null,
    site_build_sha:expectedSha,deploy_sha:process.env.DEPLOY_SHA||"NOT_RUN",github_run_id:process.env.GITHUB_RUN_ID||null,route:ROUTE,
    native_runtime_status:"NOT_VERIFIED",visual_review_status:"USER_VISUAL_ACCEPTANCE_PENDING",user_acceptance_status:"USER_VISUAL_ACCEPTANCE_PENDING",
    page_profile_status:"DRAFT_SCHEMA_VALID_ONLY",mapping_sha256:null,w2_binding_sha256:null,resource_statuses:[],authority_gaps:[],matrix:[],negative_cases:[],
    differences:[],behavior_state_mapping:null,
    geometry_result:"NOT_RUN",resource_result:"NOT_RUN",responsive_result:"NOT_RUN",dom_report:null,screenshot_reference:null,screenshot_rendered:null,
    overlay_reference:null,difference_reference:null,browser_url:null,
    browser_screenshot_pixel_scale:3,browser_screenshot_scale_disclosure:"Chromium DPR 3 element screenshot; up to 1 CSS px at the capture edge may be added by device-pixel rounding and the actual PNG size is recorded. DOM geometry remains CSS px and is not Native density."};
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
      return {pageId:root.dataset.pageId,sourceSha:root.dataset.sourceSha,profileStatus:page.dataset.profileStatus,workspaceKind:root.dataset.workspaceKind,rendererStateId:root.dataset.viewState,
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
    const formalCases=preflight.formalViewports.flatMap(({width,height})=>preflight.fontScales.map(fontScale=>({width,height,fontScale})))
      .map(row=>({...row,matrix_classification:"FORMAL_AUTHORITY_CASE"}));
    const supplementalCases=preflight.formalViewports.flatMap(({width,height})=>
      preflight.fontScales.includes(1.5)?[]:[{width,height,fontScale:1.5,matrix_classification:"SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY"}]);
    const matrixCases=[...formalCases,...supplementalCases];
    baseEvidence.supplemental_matrix_definition={font_scale:1.5,viewports:preflight.formalViewports,
      status:"SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",source_path:preflight.viewportAuthority.path,source_sha256:preflight.viewportAuthority.sha256};
    const behaviorState=await resolveBehaviorState(args.root);
    assert(behaviorState.behavior_state_id==="EMPTY","the declared W5 behavior state must resolve to the EMPTY state selected by the approved behavior authority");
    assert(typeof preflight.rendererStateId==="string"&&preflight.rendererStateId.length>0,"the actual structured renderer must report its current renderer state");
    baseEvidence.behavior_state_mapping={...behaviorState,renderer_state_id:preflight.rendererStateId};
        baseEvidence.matrix=[...formalCases,...supplementalCases].map(({width,height,fontScale,matrix_classification})=>({
// Exact-SHA matrix asserts the live Inspector values equal selected DOM bounds.
      case_id:`EMPTY_HOME_${width}x${height}_FONT_${fontScale}`,
      page_id:"home_empty_v2",
      viewport:{width,height},
      font_scale:fontScale,
      state_id:behaviorState.behavior_state_id,
      renderer_state_id:preflight.rendererStateId,
      matrix_classification,
      source_sha:expectedSha,
      run_id:runId,
      matrix_source_path:preflight.viewportAuthority.path,
      matrix_source_sha256:preflight.viewportAuthority.sha256,
      result:"NOT_RUN"
    }));
    baseEvidence.matrix_summary={expected:formalCases.length,executed:0,pass:0,fail:0,not_run:formalCases.length,
      formal_expected:formalCases.length,formal_executed:0,formal_pass:0,formal_fail:0,formal_not_run:formalCases.length,
      supplemental_expected:supplementalCases.length,supplemental_executed:0,supplemental_pass:0,supplemental_fail:0,supplemental_not_run:supplementalCases.length,
      total_expected:matrixCases.length,total_executed:0,total_pass:0,total_fail:0,total_not_run:matrixCases.length,
      source_path:preflight.viewportAuthority.path,source_sha256:preflight.viewportAuthority.sha256};
    baseEvidence.resource_statuses=preflight.resources;
    baseEvidence.authority_audit=await buildAuthorityAudit(args.root,preflight);
    baseEvidence.state_boundary_coverage=baseEvidence.authority_audit.content_state_boundary;
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

    const workspaceBrowserViewport=await page.evaluate(()=>({width:innerWidth,height:innerHeight,device_scale_factor:devicePixelRatio}));
    assert(workspaceBrowserViewport.width>=1440&&workspaceBrowserViewport.height>=1200,
      `WORKSPACE_BROWSER_VIEWPORT_TOO_SMALL_FOR_CAPTURE: ${JSON.stringify(workspaceBrowserViewport)}`);
    baseEvidence.workspace_capture_browser_viewport=workspaceBrowserViewport;
    const updateMatrixSummary=()=>{
      const group=classification=>baseEvidence.matrix.filter(row=>row.matrix_classification===classification);
      const summarize=rows=>{const pass=rows.filter(row=>row.result==="PASS").length;const fail=rows.filter(row=>row.result==="FAIL").length;
        return {expected:rows.length,executed:pass+fail,pass,fail,not_run:rows.length-pass-fail};};
      const formal=summarize(group("FORMAL_AUTHORITY_CASE"));
      const supplemental=summarize(group("SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY"));
      const total=summarize(baseEvidence.matrix);
      baseEvidence.matrix_summary={expected:formal.expected,executed:formal.executed,pass:formal.pass,fail:formal.fail,not_run:formal.not_run,
        formal_expected:formal.expected,formal_executed:formal.executed,formal_pass:formal.pass,formal_fail:formal.fail,formal_not_run:formal.not_run,
        supplemental_expected:supplemental.expected,supplemental_executed:supplemental.executed,supplemental_pass:supplemental.pass,supplemental_fail:supplemental.fail,supplemental_not_run:supplemental.not_run,
        total_expected:total.expected,total_executed:total.executed,total_pass:total.pass,total_fail:total.fail,total_not_run:total.not_run,
        source_path:preflight.viewportAuthority.path,source_sha256:preflight.viewportAuthority.sha256};
    };
    const promptTextScreenshotHashes={};
    const renderedScreenshotHashes={};
    for (const {width,height,fontScale,matrix_classification} of matrixCases) {
      activeMatrixRow=baseEvidence.matrix.find(row=>row.case_id===`EMPTY_HOME_${width}x${height}_FONT_${fontScale}`);
      activeMatrixRow.result="IN_PROGRESS";
      activeMatrixRow.started_at=new Date().toISOString();
      updateMatrixSummary();
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
        const uiIds=["Header","HeroCopy","CapturePrompt","AccountEntry","ChevronRight","PrimaryCaptureButton","AlbumAction","GalleryIcon"];
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
        const lineSvg=document.querySelector("#eh-FishingLine");
        const linePath=lineSvg?.querySelector("path");
        const lineSvgRect=lineSvg?.getBoundingClientRect(),lineViewBox=lineSvg?.viewBox?.baseVal,boardRect=board.getBoundingClientRect();
        const pointToBoard=(point)=>({x:lineSvgRect.left+point.x/lineViewBox.width*lineSvgRect.width-boardRect.left,
          y:lineSvgRect.top+point.y/lineViewBox.height*lineSvgRect.height-boardRect.top});
        const lineLength=linePath?.getTotalLength()||0;
        const lineStart=linePath&&lineLength?pointToBoard(linePath.getPointAtLength(0)):null;
        const lineEnd=linePath&&lineLength?pointToBoard(linePath.getPointAtLength(lineLength)):null;
        const contactRect=document.querySelector("#eh-WaterContact")?.getBoundingClientRect();
        const contactPoint=contactRect?{x:contactRect.left-boardRect.left,y:contactRect.top-boardRect.top}:null;
        const lineEndContactGap=lineEnd&&contactPoint?Math.hypot(lineEnd.x-contactPoint.x,lineEnd.y-contactPoint.y):null;
        const bobberContact={horizontal_inside:contact&&bobber?contact.x>=bobber.x-.5&&contact.x<=bobber.right+.5:false,
          vertical_gap:contact&&bobber?Math.abs(contact.y-bobber.bottom):null};
        const reflectionGap=contact&&reflection?Math.abs(reflection.y-contact.y):null;
        const rippleContainsContact=contact&&ripple?contact.x>=ripple.x&&contact.x<=ripple.right&&contact.y>=ripple.y&&contact.y<=ripple.bottom:false;
        const layerOrder=[byId.FishingLine?.layer.z_index,byId.BobberReflection?.layer.z_index,byId.Ripple?.layer.z_index,bobber?getComputedStyle(document.querySelector("#eh-BobberBody")).zIndex:null].map(Number);
        const overflow={outside_viewport:outsideViewport,text_overflow:textOverflow,scene_crop_expected:true};
        const boardBounds=board.getBoundingClientRect();
        const actualRect=selector=>{const r=document.querySelector(selector)?.getBoundingClientRect();return r?{x:r.left-boardRect.left,y:r.top-boardRect.top,width:r.width,height:r.height,right:r.right-boardRect.left,bottom:r.bottom-boardRect.top,unit:"css_px"}:null;};
        const loginLabel=actualRect("#eh-AccountEntryLabel"),chevron=actualRect("#eh-ChevronRight"),albumLabel=actualRect("#eh-AlbumActionLabel"),gallery=actualRect("#eh-GalleryIcon");
        const promptNode=document.querySelector("#eh-CapturePrompt"),promptStyle=promptNode?getComputedStyle(promptNode):null;
        const albumNode=document.querySelector("#eh-AlbumAction"),albumStyle=albumNode?getComputedStyle(albumNode):null;
        const promptParent=promptNode?.parentElement||null,promptParentRect=promptParent?.getBoundingClientRect();
        const promptText=document.querySelector("#eh-CapturePromptText"),promptRange=promptText?document.createRange():null;
        if(promptRange&&promptText)promptRange.selectNodeContents(promptText);
        const promptTextRect=promptRange?.getBoundingClientRect();
        const promptSizing={parent_id:promptParent?.id||null,parent_bounds_css_px:actualRect("#eh-CaptureActionGroup"),
          available_width_css_px:promptParent?.clientWidth??null,actual_bounds_css_px:actualRect("#eh-CapturePrompt"),
          width_rule:"100% of CaptureActionGroup available width",min_width_css:promptStyle?.minWidth||null,max_width_css:promptStyle?.maxWidth||null,
          font_size_css_px:promptStyle?parseFloat(promptStyle.fontSize):null,line_height_css_px:promptStyle?(promptStyle.lineHeight==="normal"?null:parseFloat(promptStyle.lineHeight)):null,
          line_height_authority_token:promptNode?.dataset.typographyToken||null,font_scale:metrics.font_scale,
          height_rule:"ceil(page contract 24sp line-height × fontScale)",
          expected_height_css_px:promptStyle&&promptStyle.lineHeight!=="normal"?Math.ceil(parseFloat(promptStyle.lineHeight)):null,
          text_bounds_css_px:promptTextRect?{x:promptTextRect.left-boardRect.left,y:promptTextRect.top-boardRect.top,width:promptTextRect.width,height:promptTextRect.height}:null,
          white_space:promptStyle?.whiteSpace||null,word_break:promptStyle?.wordBreak||null,overflow_wrap:promptStyle?.overflowWrap||null,
          wrapping_rule:"single-line nowrap",vertical_rule:"Y = camera Y - max(frozen prompt offset, actual prompt height + minimum contract clear gap)",
          authority_status:"CANDIDATE_RENDER_RULE; typography token is shared, exact prompt geometry remains subject to visual review"};
        const iconResource=node=>byId[node]?{asset_id:byId[node].resource_ref?.asset_id||document.querySelector(`#eh-${CSS.escape(node)}`)?.dataset.assetId||null,
          sha256:byId[node].resource_sha256||null,lifecycle:byId[node].resource_lifecycle,publication_permission:byId[node].publication_permission}:null;
        const inspector=document.querySelector("[data-workspace-inspector]");
        const inspectorData=window.PageWorkspaceAdapters.getInspector();
        const inspectorField=label=>inspector?.querySelector(`[data-inspector-field="${CSS.escape(label)}"] dd`)?.textContent||null;
        const inspectorEvidence={node_id:inspectorData.node_id,bounds:inspectorData.bounds,typography:inspectorData.typography,
          rendered_bounds:inspectorField("实际 DOM 边界"),rendered_typography:inspectorField("字体 / 行高")};
        return {viewport:metrics.viewport,font_scale:metrics.font_scale,safe_area_preview:metrics.safe_insets_preview||null,scene_transform:metrics.scene_transform,profile_width_class:board.dataset.profileWidthClass,
          root_size:{width:root.getBoundingClientRect().width,height:root.getBoundingClientRect().height},overflow,ui_bounds:ui,
          fishing:{rod:rod,line,bobber,contact,ripple,reflection,line_path_start:{x:lineStart?.x,y:lineStart?.y},line_path_end:{x:lineEnd?.x,y:lineEnd?.y},composition_outside_viewport:fishingOutside,line_end_contact_gap:lineEndContactGap,
            bobber_contact_alignment:bobberContact,reflection_contact_gap:reflectionGap,ripple_contains_contact:rippleContainsContact,layer_order:layerOrder},
          icons:{chevron:byId.ChevronRight?.bounds||null,chevron_label:loginLabel,chevron_gap_css_px:loginLabel&&chevron?chevron.x-loginLabel.right:null,
            gallery:byId.GalleryIcon?.bounds||null,gallery_label:albumLabel,gallery_gap_css_px:albumLabel&&gallery?albumLabel.x-gallery.right:null,
            chevron_resource:iconResource("ChevronRight"),gallery_resource:iconResource("GalleryIcon")},
          hero:byId.HeroCopy?.bounds,prompt:byId.CapturePrompt?.bounds,prompt_sizing:promptSizing,camera:byId.PrimaryCaptureButton?.bounds,album:byId.AlbumAction?.bounds,
          albumTypography:{font_size_css_px:albumStyle?parseFloat(albumStyle.fontSize):null,line_height_css_px:albumStyle?parseFloat(albumStyle.lineHeight):null,font_weight:albumStyle?.fontWeight||null},
          deviations:Object.fromEntries(Object.entries(byId).map(([key,row])=>[key,row.deviation])),inspectorNode:inspector?.dataset.selectedNodeId,inspector:inspectorEvidence,
          promptTypography:byId.CapturePrompt?.typography,sourceSha:root.dataset.sourceSha,unit:byId.FishingRod?.bounds.unit,
          node_bounds:rows, cameraTouch:{width:byId.PrimaryCaptureButton?.bounds.width,height:byId.PrimaryCaptureButton?.bounds.height},
          cameraVisualAsset:(()=>{const r=document.querySelector("#eh-PrimaryCaptureButton img")?.getBoundingClientRect();return r?{x:r.x,y:r.y,width:r.width,height:r.height,ratio:r.width/(byId.PrimaryCaptureButton?.bounds.width||1)}:null})(),
          promptTypographyAuthority:{token:document.querySelector("#eh-CapturePrompt")?.dataset.typographyToken||null,
            authority:document.querySelector("#eh-CapturePrompt")?.dataset.typographyAuthority||null,
            native_font_status:root.dataset.nativeFontStatus||"NOT_VERIFIED"},
          brand_status:root.dataset.brandStatus||"NOT_REPORTED",boardOrigin:{x:boardBounds.x,y:boardBounds.y}};
      });
      Object.assign(activeMatrixRow,caseEvidence);
      assert(caseEvidence.overflow.outside_viewport.length===0&&caseEvidence.overflow.text_overflow.length===0,`responsive UI overflow at ${width}x${height} @${fontScale}: ${JSON.stringify(caseEvidence.overflow)}`);
      assert(caseEvidence.unit==="css_px","DOM measurements must be reported as CSS px");
      assert(caseEvidence.inspectorNode==="HeroCopy"&&caseEvidence.inspector.node_id===caseEvidence.inspectorNode,
        "viewport changes must retain the HeroCopy selection in both the tree and live Inspector adapter");
      const inspectorMeasured=caseEvidence.node_bounds.find(row=>row.node_id===caseEvidence.inspector.node_id)?.bounds;
      const inspectorKeys=["x","y","width","height"];
      assert(inspectorMeasured&&inspectorKeys.every(key=>Math.abs(caseEvidence.inspector.bounds?.[key]-inspectorMeasured[key])<0.05),
        `Inspector API bounds must equal the selected element DOM bounds at ${width}x${height} @${fontScale}`);
      const inspectorBoundsLabel=inspectorMeasured&&`${inspectorMeasured.x.toFixed(1)}, ${inspectorMeasured.y.toFixed(1)} · ${inspectorMeasured.width.toFixed(1)} × ${inspectorMeasured.height.toFixed(1)} CSS px`;
      assert(caseEvidence.inspector.rendered_bounds===inspectorBoundsLabel&&
        caseEvidence.inspector.rendered_typography===`${caseEvidence.inspector.typography.font_size} / ${caseEvidence.inspector.typography.line_height}`,
        `Rendered Inspector values must match its API and actual DOM measurement at ${width}x${height} @${fontScale}`);
      assert(caseEvidence.fishing.rod&&caseEvidence.fishing.line&&caseEvidence.fishing.bobber&&caseEvidence.fishing.contact&&caseEvidence.fishing.ripple,"fishing composition must map to separate measured DOM nodes");
      assert(caseEvidence.fishing.composition_outside_viewport.length===0,`fishing composition clipped at ${width}x${height}: ${JSON.stringify(caseEvidence.fishing.composition_outside_viewport)}`);
      assert(caseEvidence.fishing.line_path_start&&Math.abs(caseEvidence.fishing.line_path_start.x-(caseEvidence.fishing.rod.x+337*caseEvidence.scene_transform.scale))<1.5,"measured SVG path start must align with the registered rod-tip point");
      assert(Math.abs(caseEvidence.fishing.line_path_start.y-(caseEvidence.fishing.rod.y+4*caseEvidence.scene_transform.scale))<1.5,"measured SVG path start must align with the registered rod-tip point");
      assert(caseEvidence.fishing.line_end_contact_gap<=5*caseEvidence.scene_transform.scale,`fishing line endpoint misses water-contact anchor at ${width}x${height}`);
      assert(caseEvidence.fishing.bobber_contact_alignment.horizontal_inside&&caseEvidence.fishing.bobber_contact_alignment.vertical_gap<=1.5,`bobber does not meet water contact at ${width}x${height}`);
      assert(caseEvidence.fishing.reflection_contact_gap<=2*caseEvidence.scene_transform.scale,`bobber reflection is detached from water contact at ${width}x${height}`);
      assert(caseEvidence.fishing.ripple_contains_contact,`water ripple does not contain contact point at ${width}x${height}`);
      assert(caseEvidence.fishing.layer_order[0]<caseEvidence.fishing.layer_order[1]&&caseEvidence.fishing.layer_order[1]<caseEvidence.fishing.layer_order[2]&&caseEvidence.fishing.layer_order[2]<caseEvidence.fishing.layer_order[3],`fishing layer occlusion order is invalid: ${caseEvidence.fishing.layer_order}`);
      assert(caseEvidence.cameraTouch.width>0&&caseEvidence.cameraTouch.height>0,"camera button actual DOM bounds must be measured");
      assert(Math.abs(caseEvidence.cameraVisualAsset.ratio-(208/220))<0.002,"frozen 208px camera artwork must be uniformly fitted in the 220px target");
      const promptFontSize=parseFloat(baseEvidence.authority_audit.typography.prompt_token.size);
      assert(caseEvidence.prompt_sizing.parent_id==="eh-CaptureActionGroup"&&caseEvidence.prompt_sizing.actual_bounds_css_px&&
        Math.abs(caseEvidence.prompt_sizing.actual_bounds_css_px.width-caseEvidence.prompt_sizing.available_width_css_px)<0.05,
        "CapturePrompt must use its real CTA parent width as the available width");
      assert(caseEvidence.prompt_sizing.line_height_authority_token==="type.body_small"&&Number.isFinite(promptFontSize)&&
        Math.abs(caseEvidence.prompt_sizing.font_size_css_px-promptFontSize*fontScale)<0.05&&
        Math.abs(caseEvidence.prompt_sizing.line_height_css_px-CANDIDATE_PROMPT_LINE_HEIGHT_SP*fontScale)<0.05&&
        caseEvidence.prompt_sizing.actual_bounds_css_px.height===Math.ceil(CANDIDATE_PROMPT_LINE_HEIGHT_SP*fontScale),
        "CapturePrompt must use type.body_small size with the page-owned 24sp line-height × fontScale");
      assert(Math.abs(caseEvidence.ui_bounds.AlbumAction.height-48)<0.05&&
        Math.abs(caseEvidence.albumTypography.font_size_css_px-parseFloat(baseEvidence.authority_audit.typography.album_token.size)*fontScale)<0.05&&
        Math.abs(caseEvidence.albumTypography.line_height_css_px-parseFloat(baseEvidence.authority_audit.typography.album_token.lineHeight)*fontScale)<0.05,
        "AlbumAction must use type.caption while preserving its 48px minimum touch height");
      assert(caseEvidence.prompt_sizing.white_space==="nowrap"&&caseEvidence.prompt_sizing.text_bounds_css_px?.width>0&&
        caseEvidence.prompt_sizing.text_bounds_css_px.width<=caseEvidence.prompt_sizing.available_width_css_px,
        "CapturePrompt must be a measured single-line label that fits its actual available width");
      assert(caseEvidence.icons.chevron&&Math.abs(caseEvidence.icons.chevron.width-14)<.01&&Math.abs(caseEvidence.icons.chevron.height-14)<.01,
        "Text Action V1 requires a separately measured 14dp chevron icon");
      assert(Math.abs(caseEvidence.icons.chevron_gap_css_px-4)<.05,"Text Action V1 chevron gap must be 4dp");
      assert(caseEvidence.icons.gallery&&Math.abs(caseEvidence.icons.gallery.width-14)<.01&&Math.abs(caseEvidence.icons.gallery.height-14)<.01,
        "GALLERY icon must render as a real uniformly sized SVG DOM node");
      assert(Math.abs(caseEvidence.icons.gallery_gap_css_px-8)<.05,"GALLERY icon and album text must retain the measured reference gap");
      assert(caseEvidence.icons.chevron_resource?.asset_id==="system_icon_library_v0_1_assets_ic_chevron_right_v1_svg"&&
        caseEvidence.icons.chevron_resource.sha256==="3d8c0bf43500c40b636a08f50d6fedf4d5f0ae2fd984a7e6c16cd03f4130d4a9"&&
        caseEvidence.icons.chevron_resource.lifecycle==="REVIEW"&&caseEvidence.icons.chevron_resource.publication_permission==="PUBLIC_BY_EXISTING_PAGES_RULE",
        "CHEVRON_RIGHT W2 source, lifecycle conflict, and permission must be visible in DOM evidence");
      assert(caseEvidence.icons.gallery_resource?.asset_id==="system_icon_library_v0_1_assets_ic_gallery_v1_svg"&&
        caseEvidence.icons.gallery_resource.sha256==="5798abbdaad45305fe9ed5a578868247925d2d95644c8ea2559f446c1f069fda"&&
        caseEvidence.icons.gallery_resource.lifecycle==="REVIEW"&&caseEvidence.icons.gallery_resource.publication_permission==="PUBLIC_BY_EXISTING_PAGES_RULE",
        "GALLERY W2 source, lifecycle conflict, and permission must be visible in DOM evidence");
      const shot=resolve(args.outputDir,`EmptyHome_${width}x${height}_font-${fontScale}.png`);
      const promptTextShot=resolve(args.outputDir,`EmptyHomePrompt_${width}x${height}_font-${fontScale}.png`);
      const capture=await screenshotRootViewport(page,shot,width,height,promptTextShot);
      const shotSize=capture.size;
      assertScreenshotCaptureEnvelope(shotSize,width,height,`${width}x${height}@${fontScale}`);
      const promptTextSha=await sha256File(promptTextShot);
      const renderedSha=await sha256File(shot);
      promptTextScreenshotHashes[`${width}x${height}`]??={};
      promptTextScreenshotHashes[`${width}x${height}`][String(fontScale)]=promptTextSha;
      renderedScreenshotHashes[`${width}x${height}`]??={};
      renderedScreenshotHashes[`${width}x${height}`][String(fontScale)]=renderedSha;
      Object.assign(activeMatrixRow,caseEvidence,{
        case_id:activeMatrixRow.case_id,page_id:"home_empty_v2",source_sha:expectedSha,run_id:runId,
        matrix_source_path:preflight.viewportAuthority.path,matrix_source_sha256:preflight.viewportAuthority.sha256,
        state_id:behaviorState.behavior_state_id,
        renderer_state_id:preflight.rendererStateId,
        browser_url:args.url?page.url():"LOCAL_SITE_BUILD_ONLY_NOT_DEPLOYED",
        browser_viewport_css_px:workspaceBrowserViewport,
        viewport_width:width,viewport_height:height,font_scale:fontScale,
        candidate_source_sha:expectedSha,site_build_sha:expectedSha,
        dom_report:"EmptyHome_Component_Bounds_Report_V1.json",
        screenshot_rendered:{path:shot.split("/").at(-1),sha256:renderedSha,pixel_width:shotSize.width,pixel_height:shotSize.height,device_scale_factor:3,capture_bounds_css_px:capture.bounds_css_px,
          visible_content:capture.visible_content,capture_isolation_used:capture.capture_isolation_used,outside_workspace_chrome_included:capture.outside_workspace_chrome_included,
          workspace_visibility_before_isolation:capture.workspace_visibility_before_isolation,selected_overlay_suppressed:capture.selected_overlay_suppressed},
        prompt_text_measurement:{...capture.prompt_text_bounds_css_px,screenshot_path:promptTextShot.split("/").at(-1),screenshot_sha256:promptTextSha},
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
    assert(baseEvidence.matrix_summary.formal_expected===baseEvidence.matrix_summary.formal_pass&&baseEvidence.matrix_summary.formal_fail===0&&baseEvidence.matrix_summary.formal_not_run===0&&
      baseEvidence.matrix_summary.supplemental_expected===baseEvidence.matrix_summary.supplemental_pass&&baseEvidence.matrix_summary.supplemental_fail===0&&baseEvidence.matrix_summary.supplemental_not_run===0,
      `FORMAL_MATRIX_CASE_COUNT_MISMATCH: ${JSON.stringify(baseEvidence.matrix_summary)}`);
    for (const [viewport,hashes] of Object.entries(promptTextScreenshotHashes)) {
      assert(Object.keys(hashes).length===3&&new Set(Object.values(hashes)).size===3,
        `FONT_SCALE_SCREENSHOT_HASH_NOT_DISTINCT: viewport=${viewport} hashes=${JSON.stringify(hashes)}`);
    }
    for (const [viewport,hashes] of Object.entries(renderedScreenshotHashes)) {
      assert(Object.keys(hashes).length===3&&new Set(Object.values(hashes)).size===3,
        `FONT_SCALE_RENDERED_PAGE_HASH_NOT_DISTINCT: viewport=${viewport} hashes=${JSON.stringify(hashes)}`);
    }
    baseEvidence.prompt_text_screenshot_hashes=promptTextScreenshotHashes;
    baseEvidence.rendered_screenshot_hashes_by_font_scale=renderedScreenshotHashes;
    baseEvidence.geometry_reconciliation=baseEvidence.matrix.map(row=>({case_id:row.case_id,page_id:"home_empty_v2",source_sha:expectedSha,
      camera:{component_id:"PrimaryCaptureButton",touch_bounds_css_px:row.camera,artwork_dom_bounds_css_px:row.cameraVisualAsset,
        expected_artwork_to_touch_ratio:208/220,actual_artwork_to_touch_ratio:row.cameraVisualAsset?.ratio??null,result:"PASS"},
      bobber:{component_id:"BobberBody",bounds_css_px:row.fishing.bobber,contact_bounds_css_px:row.fishing.contact,
        expected_anchor_ref:"anchor_contract.json#/anchors/bobber/water_contact_reference_px",actual_vertical_gap_css_px:row.fishing.bobber_contact_alignment.vertical_gap,
        result:row.fishing.bobber_contact_alignment.horizontal_inside&&row.fishing.bobber_contact_alignment.vertical_gap<=1.5?"PASS":"FAIL"},
      fishing_line:{component_id:"FishingLine",bounds_css_px:row.fishing.line,path_start_css_px:row.fishing.line_path_start,path_end_css_px:row.fishing.line_path_end,
        water_contact_gap_css_px:row.fishing.line_end_contact_gap,result:row.fishing.line_end_contact_gap<=5*row.scene_transform.scale?"PASS":"FAIL"},
      prompt:{component_id:"CapturePrompt",bounds_css_px:row.prompt_sizing.actual_bounds_css_px,parent_bounds_css_px:row.prompt_sizing.parent_bounds_css_px,
        text_bounds_css_px:row.prompt_sizing.text_bounds_css_px,font_scale:row.prompt_sizing.font_scale,line_height_css_px:row.prompt_sizing.line_height_css_px,
        width_rule:row.prompt_sizing.width_rule,wrap_rule:row.prompt_sizing.wrapping_rule,result:"PASS"} }));
    const cameraDifference=baseEvidence.differences.find(row=>row.component_id==="PrimaryCaptureButton");
    if(cameraDifference){cameraDifference.actual="Exact SHA Chromium evidence records the 208/220 centered artwork ratio in each 12-case matrix row.";
      cameraDifference.numeric_deviation=baseEvidence.geometry_reconciliation.reduce((max,row)=>Math.max(max,Math.abs(row.camera.actual_artwork_to_touch_ratio-row.camera.expected_artwork_to_touch_ratio)),0);
      cameraDifference.correction_status="IMPLEMENTED_AND_VERIFIED_IN_CURRENT_MATRIX";}

    await page.locator("[data-empty-viewport-preset]").selectOption("360x640@1");
    await page.waitForFunction(()=>document.querySelector("[data-workspace-artboard]")?.dataset.viewport==="360x640");
    await page.locator("[data-empty-compare-mode]").selectOption("structured");
    const originalFrozen=resolve(args.root,"design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png");
    const frozenCopy=resolve(args.outputDir,"EmptyHome_Frozen_V2_2_Original.png");
    await copyFile(originalFrozen,frozenCopy);
    assert(await sha256File(frozenCopy)===preflight.referenceSha,"frozen V2.2 evidence copy SHA differs from the W2-verified reference");
    const compareStructured=resolve(args.outputDir,"EmptyHome_Structured_1080x1920.png");
    const compareCapture=await screenshotRootViewport(page,compareStructured,360,640,null,45000);
    const compareSize=compareCapture.size;
    assertScreenshotCaptureEnvelope(compareSize,360,640,"reference-comparison structured");
    for (const mode of ["frozen","overlay","difference"]) {
      await page.locator("[data-empty-compare-mode]").selectOption(mode);
      const comparePath=resolve(args.outputDir,`EmptyHome_Compare_${mode}_1080x1920.png`);
      const capture=await screenshotRootViewport(page,comparePath,360,640,null,45000);
      const size=capture.size;
      assertScreenshotCaptureEnvelope(size,360,640,`reference-comparison ${mode}`);
    }
    await page.locator("[data-empty-compare-mode]").selectOption("structured");
    const comparisonFiles=[];
    for(const mode of ["structured","frozen","overlay","difference"]){
      const path=resolve(args.outputDir,mode==="structured"?"EmptyHome_Structured_1080x1920.png":`EmptyHome_Compare_${mode}_1080x1920.png`);
      comparisonFiles.push({mode,path:path.split("/").at(-1),sha256:await sha256File(path)});
    }
    baseEvidence.frozen_visual={path:frozenCopy.split("/").at(-1),sha256:await sha256File(frozenCopy),authority_sha256:preflight.referenceSha,
      pixel_dimensions:await pngDimensions(frozenCopy),original_bytes_unchanged:true};
    const comparisonCase=baseEvidence.matrix.find(row=>row.viewport?.width===360&&row.viewport?.height===640&&row.font_scale===1) ||
      baseEvidence.matrix.find(row=>row.viewport?.width===360&&row.viewport?.height===780&&row.font_scale===1);
    baseEvidence.reference_comparison={logical_viewport_css_px:{width:360,height:640},frozen_reference_pixel_size:{width:941,height:1672},
      browser_device_scale_factor:3,capture_bounds_css_px:compareCapture.bounds_css_px,screenshot_pixels:compareSize,
      comparison_region:"isolated page-root viewport at 360x640 CSS px with preserved aspect ratio; reference bytes are unmodified",
      page_content_region_alignment:{browser_root_bounds_css_px:{x:0,y:0,width:360,height:640},reference_page_content_bounds_px:"NOT_MACHINE_READABLE",
        status:"FULL_CANVAS_DIAGNOSTIC_ONLY",reason:"V2.2 frozen raster includes Android system chrome but has no machine-readable chrome/content boundary. Browser uses a separately disclosed 36/10 CSS px safe-area estimate; no reference crop or Native inset claim is inferred."},
      native_density_claim:false,source_ratio_preserved:true,
      system_bar_comparison:{browser_status_bar:"NOT_SIMULATED",browser_safe_insets:comparisonCase?.safe_area_preview||{...BROWSER_REFERENCE_SAFE_INSETS_DP,status:"BROWSER_REFERENCE_SCREENSHOT_ESTIMATE_NOT_NATIVE"},
        frozen_reference_status_bar:"PIXELS_PRESENT_IN_FROZEN_RASTER; boundary has no page-owned machine-readable authority",reference_status_bar_bounds:"NOT_VERIFIED",
        browser_safe_area_preview:comparisonCase?.safe_area_preview||null,header_actual_dom_bounds_css_px:comparisonCase?.ui_bounds?.Header||null,
        header_top_from_assumed_safe_inset_css_px:comparisonCase?.ui_bounds?.Header?comparisonCase.ui_bounds.Header.y-(comparisonCase.safe_area_preview?.top||0):null,
        reference_header_bounds:"NOT_MACHINE_READABLE",
        status:"AUTHORITY_GAP; system chrome difference is reported separately and is not treated as Native validation"}};
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
      activeNegativeCase=label;
      await page.goto(url,{waitUntil:"domcontentloaded",timeout:45000});
      await page.locator("#pageList .module-item").first().waitFor({state:"visible",timeout:30000});
      await page.waitForFunction(()=>{
        const canvas=document.querySelector("#hifiViewCanvas");
        return Boolean(canvas?.getClientRects().length>0);
      },null,{timeout:30000});
      try {
        await page.waitForFunction(()=>{
          const canvas=document.querySelector("#hifiViewCanvas");
          const workspace=canvas?.querySelector("[data-page-workspace-root]");
          const workspaceError=canvas?.querySelector("[data-workspace-error]");
          const loadError=canvas?.querySelector(".page-ws-load-error");
          const workspaceFailed=workspace?.dataset.viewState==="ERROR"&&workspaceError&&!workspaceError.hidden;
          const loadFailed=loadError&&loadError.getClientRects().length>0&&loadError.innerText.trim().length>0;
          return Boolean(workspaceFailed||loadFailed);
        },null,{timeout:45000});
      } catch(error) {
        const diagnostic=await page.evaluate(()=>{
          const adapter=window.PageWorkspaceAdapters?.active;
          const canvas=document.querySelector("#hifiViewCanvas");
          const workspace=canvas?.querySelector("[data-page-workspace-root]");
          const errorNode=canvas?.querySelector("[data-workspace-error]");
          const loadError=canvas?.querySelector(".page-ws-load-error");
          return {page_id:adapter?.pageId||null,source_sha:adapter?.sourceSha||null,
            active_root_connected:Boolean(adapter?.root?.isConnected),workspace_count:document.querySelectorAll("[data-page-workspace-root]").length,
            view_state:workspace?.dataset.viewState||null,error_visible:Boolean(errorNode&&!errorNode.hidden),
            error_text:errorNode?.innerText||null,load_error_visible:Boolean(loadError?.getClientRects().length),
            load_error_text:loadError?.innerText||null,canvas_text:canvas?.innerText?.slice(0,1200)||null};
        });
        throw new Error(`${label}: expected active workspace fail-closed state was not observed: ${error.message}; diagnostics=${JSON.stringify(diagnostic)}`);
      }
      const failure=await page.evaluate(()=>{
        const canvas=document.querySelector("#hifiViewCanvas");
        const workspace=canvas?.querySelector("[data-page-workspace-root]");
        const workspaceError=canvas?.querySelector("[data-workspace-error]");
        const loadError=canvas?.querySelector(".page-ws-load-error");
        return workspace?.dataset.viewState==="ERROR"&&workspaceError&&!workspaceError.hidden
          ? {scope:"structured_workspace",message:workspaceError.innerText}
          : {scope:"page_loader",message:loadError?.innerText||""};
      });
      const message=failure.message;
      assert(message.length>0,`${label}: no explicit fail-closed reason was reported`);
      baseEvidence.negative_cases.push({case:label,result:"EXPECTED_FAIL_CLOSED",error_scope:failure.scope,error:message});
      activeNegativeCase=null;
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
    const expectedMissingResource404s=httpErrors.filter(event=>event.negative_case==="required_resource_missing"&&event.status===404&&
      new URL(event.url).pathname.endsWith(expectedMissingResourcePath));
    assert(expectedMissingResource404s.length===1,`required_resource_missing must return one targeted 404: ${JSON.stringify(httpErrors)}`);
    const unexpectedHttpErrors=httpErrors.filter(event=>!expectedMissingResource404s.includes(event));
    assert(unexpectedHttpErrors.length===0,`Unexpected HTTP error responses: ${JSON.stringify(unexpectedHttpErrors)}`);
    const expectedNegativeConsoleErrors=consoleErrorEvents.filter(event=>event.text===expectedMissingResourceConsoleText);
    assert(expectedNegativeConsoleErrors.length<=1,`Repeated resource 404 console events: ${JSON.stringify(expectedNegativeConsoleErrors)}`);
    const unexpectedConsoleErrors=consoleErrorEvents.filter(event=>!expectedNegativeConsoleErrors.includes(event));
    assert(unexpectedConsoleErrors.length===0,`Chromium console errors: ${unexpectedConsoleErrors.map(event=>event.text).join(" | ")}`);
    assert(pageErrors.length===0,`Chromium page errors: ${pageErrors.join(" | ")}`);
    assert(failedRequests.length===0,`Chromium failed requests: ${JSON.stringify(failedRequests)}`);
    baseEvidence.console_errors=unexpectedConsoleErrors.map(event=>event.text);
    baseEvidence.expected_negative_console_errors=expectedNegativeConsoleErrors;
    baseEvidence.http_errors=httpErrors;
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
      const formalRows=baseEvidence.matrix.filter(row=>row.matrix_classification==="FORMAL_AUTHORITY_CASE");
      const supplementalRows=baseEvidence.matrix.filter(row=>row.matrix_classification==="SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY");
      const summarize=rows=>{const p=rows.filter(row=>row.result==="PASS").length;const f=rows.filter(row=>row.result==="FAIL").length;
        return {expected:rows.length,executed:p+f,pass:p,fail:f,not_run:rows.length-p-f};};
      const formal=summarize(formalRows),supplemental=summarize(supplementalRows),total=summarize(baseEvidence.matrix);
      baseEvidence.matrix_summary={expected:formal.expected,executed:formal.executed,pass:formal.pass,fail:formal.fail,not_run:formal.not_run,
        formal_expected:formal.expected,formal_executed:formal.executed,formal_pass:formal.pass,formal_fail:formal.fail,formal_not_run:formal.not_run,
        supplemental_expected:supplemental.expected,supplemental_executed:supplemental.executed,supplemental_pass:supplemental.pass,supplemental_fail:supplemental.fail,supplemental_not_run:supplemental.not_run,
        total_expected:total.expected,total_executed:total.executed,total_pass:total.pass,total_fail:total.fail,total_not_run:total.not_run,
        source_path:baseEvidence.matrix_authority?.path||null,source_sha256:baseEvidence.matrix_authority?.sha256||null};
    }
    baseEvidence.console_errors=consoleErrorEvents.filter(event=>event.text!==expectedMissingResourceConsoleText).map(event=>event.text);
    baseEvidence.expected_negative_console_errors=consoleErrorEvents.filter(event=>event.text===expectedMissingResourceConsoleText);
    baseEvidence.http_errors=httpErrors;
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
  "Brand wordmark is FROZEN_ARTWORK_ASSET. The existing standalone brand artwork is scoped to normal_home; Empty Home has no approved independent wordmark binding, so the candidate suppresses dynamic text and remains BLOCKED_AUTHORITY_GAP.",
  "GALLERY and CHEVRON_RIGHT are W2-bound to their frozen V1 sources; Asset Registry lifecycle remains REVIEW and is preserved in the candidate evidence.",
  "Frozen reference includes Android system chrome; Chromium preview does not simulate Native status/navigation bars.",
  "Sun particle coordinates have no frozen static authority; the motion layer is NOT_RUN in this static candidate.",
  "Cloud position and alpha follow the existing Android Renderer implementation reference and need visual review.",
  "Dynamic Sun Beam is NOT_RUN because the scene master already contains ambient sun glow; no static beam is overlaid.",
  "Header horizontal inset uses shared AdaptiveLayoutContract values because the Empty Home visual contract has no page-specific inset; review required."
];}

function AUTHORITY_DIFFERENCE_REPORT(){return [
  {page_id:"home_empty_v2",component_id:"Brand",category:"AUTHORITY_GAP",authority:"design/system_v3/04_Design_Tokens/Typography/Typography_System_V3_FINAL.md",expected:"FROZEN_ARTWORK_ASSET authorized for home_empty_v2",actual:"Standalone brand asset usage is normal_home only; DOM text is suppressed",numeric_deviation:null,root_cause:"No Empty Home approved independent wordmark binding exists.",correction_status:"BLOCKED_AUTHORITY_GAP",approval_required:true},
  {page_id:"home_empty_v2",component_id:"ChevronRight / GalleryIcon",category:"AUTHORITY_GAP",authority:"design/system/icon_library_v0_1/frozen_assets_v1.json + Asset_Registry_V3_FINAL.json + Empty Home W2 Binding",expected:"Exact frozen icon bytes resolved through W2; lifecycle and permission remain explicit",actual:"W2 verifies both icon hashes; icon catalog says FROZEN while Asset Registry lifecycle remains REVIEW",numeric_deviation:null,root_cause:"Asset Registry lifecycle has not been reconciled to the frozen icon catalog; current candidate renders the verified SVGs without promoting their lifecycle.",correction_status:"W2_BOUND_LIFECYCLE_REVIEW_PRESERVED",approval_required:true},
  {page_id:"home_empty_v2",component_id:"PrimaryCaptureButton",category:"ASSET_MISMATCH",authority:"design/system/components/primary_capture_button/manifest.json + anchor_contract.json",expected:"208×208 frozen artwork centered uniformly in 220×220 hit target",actual:"Renderer uses centered uniform 208/220 fit; exact SHA geometry evidence is attached after the current matrix executes.",numeric_deviation:null,root_cause:"Previous renderer's generic image rule overrode the fitted camera asset dimensions.",correction_status:"IMPLEMENTED_AWAITING_EXACT_SHA_BROWSER_RUN",approval_required:false},
  {page_id:"home_empty_v2",component_id:"EnvironmentScene",category:"AUTHORITY_GAP",authority:"Design Manager browser preview",expected:"Frozen visual including Android system chrome",actual:"Page content rendered without Android status/navigation bars",numeric_deviation:null,root_cause:"Browser CSS viewport does not provide Native system bars or safe drawing insets.",correction_status:"NOT_SIMULATED",approval_required:true},
  {page_id:"home_empty_v2",component_id:"SunParticles",category:"AUTHORITY_GAP",authority:"design/pages/home/empty_home/shared/contracts/motion_contract.json",expected:"Motion-generated particle positions only in an authorized motion state",actual:"Static candidate does not render particles",numeric_deviation:null,root_cause:"No frozen static particle coordinates exist.",correction_status:"NOT_RUN",approval_required:true},
  {page_id:"home_empty_v2",component_id:"CloudAtmosphere",category:"AUTHORITY_GAP",authority:"app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt",expected:"Page-specific frozen cloud bounds and alpha",actual:"Existing Android renderer reference used in candidate",numeric_deviation:null,root_cause:"No independent page anchor for static cloud placement is registered.",correction_status:"REVIEW_REQUIRED",approval_required:true},
  {page_id:"home_empty_v2",component_id:"SunBeam",category:"AUTHORITY_GAP",authority:"design/pages/home/empty_home/shared/contracts/motion_contract.json",expected:"Beam only in the corresponding authorized running motion state",actual:"Hidden and NOT_RUN in static candidate",numeric_deviation:null,root_cause:"Scene base already contains ambient sun glow; static overlay would duplicate it.",correction_status:"NOT_RUN",approval_required:true},
  {page_id:"home_empty_v2",component_id:"Header",category:"AUTHORITY_GAP",authority:"app/src/main/java/com/yujian/ai/ui/adaptive/AdaptiveLayoutContract.kt",expected:"Page-owned horizontal inset value",actual:"Shared adaptive inset used as candidate value",numeric_deviation:null,root_cause:"Empty Home visual contract does not set a horizontal inset.",correction_status:"REVIEW_REQUIRED",approval_required:true}
];}

main().catch(error=>{console.error(error?.stack||String(error));process.exitCode=1;});
