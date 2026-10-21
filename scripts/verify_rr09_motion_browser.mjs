#!/usr/bin/env node
/**
 * RR09 Motion V2 playable browser regression + exact photo integrity evidence.
 * No claim is made that browser playback constitutes Android runtime acceptance.
 */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { resolve, join } from "node:path";
import { createRequire } from "node:module";
const args=process.argv.slice(2);
const option=(key,fallback)=>{const idx=args.indexOf(key);return idx>=0?args[idx+1]:fallback;};
const root=resolve(option("--root",process.cwd()));
const out=resolve(option("--output-dir","/tmp/rr09-motion-evidence"));
mkdirSync(out,{recursive:true});
const moduleDir=process.env.PLAYWRIGHT_MODULE_DIR;
if(!moduleDir) throw Error("PLAYWRIGHT_MODULE_DIR must point to installed Playwright node_modules");
const require=createRequire(import.meta.url),{chromium}=require(join(moduleDir,"playwright"));
const assertPhoto=()=> {
 const p=join(root,"design/system/fish_media_display_v1/assets/real_catch_source.jpg");
 if(!existsSync(p)) throw Error("Canonical real-catch source photo missing: "+p);
 const bytes=readFileSync(p),sha=createHash("sha256").update(bytes).digest("hex");
 assert.equal(sha,"6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef","canonical photo SHA drift");
 assert.equal(bytes.length,292222,"canonical real-catch bytes drift");
 return {path:"design/system/fish_media_display_v1/assets/real_catch_source.jpg",sha256:sha,bytes:bytes.length};
};
const photo=assertPhoto();
const port=8769,base="http://127.0.0.1:"+port+"/design/manager/rr09_motion/";
const server=spawn("python3",["-m","http.server",String(port),"--bind","127.0.0.1","--directory",root],{stdio:["ignore","pipe","pipe"]});
let serverErr="";server.stderr.on("data",b=>serverErr+=String(b));
async function waitServer() {
 for(let i=0;i<70;i++){
   try{const r=await fetch(base);if(r.ok)return;}catch{}
   await new Promise(done=>setTimeout(done,200));
 }
 throw Error("RR09 HTTP server unavailable: "+serverErr.slice(-1000));
}
let browser;
const results={contract:"recognition_result_motion_continuation_v2",revision:process.env.GITHUB_SHA||"LOCAL_UNBOUND",photo,runner:"PLAYWRIGHT_CHROMIUM",runtime_classification:"BROWSER_ONLY_NOT_ANDROID",screenshots:[],functional_checks:[],visual_review:"REVIEW_REQUIRED",verdict:"NOT_RUN"};
const check=(id,predicate,detail="")=>{assert.ok(predicate,id+": "+detail);results.functional_checks.push({id,status:"PASS",detail});};
const shaFile=p=>createHash("sha256").update(readFileSync(p)).digest("hex");
try {
 await waitServer();
 browser=await chromium.launch({headless:true,args:["--disable-dev-shm-usage"]});
 const context=await browser.newContext({viewport:{width:1380,height:1010},deviceScaleFactor:1,recordVideo:{dir:join(out,"video"),size:{width:1380,height:1010}}});
 const page=await context.newPage();
 const jsErrors=[];page.on("pageerror",e=>jsErrors.push(String(e)));
 await page.goto(base,{waitUntil:"networkidle"});
 await page.waitForFunction(()=>window.__RR09?.state()?.ready===true,{timeout:20000});
 const state=()=>page.evaluate(()=>window.__RR09.state());
 const mode=async(id,value)=>page.evaluate(([i,v])=>window.__RR09.setMode(i,v),[id,value]);
 const seek=async t=>{await page.evaluate(t=>window.__RR09.seek(t),t);await page.waitForTimeout(80);};
 const photoReady=await state();
 check("CANONICAL_REAL_PHOTO_SHA_AND_DECODE",photoReady.photoTrusted===true,"photo sha="+photo.sha256);
 check("MANUAL_CONTOUR_PROVENANCE_DISCLOSED",photoReady.contourProvenance==="MANUAL_REFERENCE_CONTOUR");
 check("AUTOPLAY_ON_NEW_SESSION",photoReady.autoplayStarted===true);
 const positions=[];
 for(const [k,t] of [["K01",0],["K02",120],["K03",290],["K04",465],["K05",610],["K06",850]]){
  await seek(t);
  const snap=await page.evaluate(()=>{
    const b=document.querySelector("#photoBox").getBoundingClientRect();
    const root=document.querySelector("#stage").getBoundingClientRect();
    const s=window.__RR09.state();
    return {t:s.t,phase:s.phase,particleCount:s.particleCount,nameReadable:s.nameReadable,photoTrusted:s.photoTrusted,
       photoRect:{left:b.left-root.left,top:b.top-root.top,width:b.width,height:b.height},
       titleTop:document.querySelector("#speciesArea").getBoundingClientRect().top-root.top,
       cnOpacity:Number(document.querySelector("#cnSpecies").style.opacity),
       laOpacity:Number(document.querySelector("#laSpecies").style.opacity)
    };
  });
  positions.push(snap);
  const filename=k+"_"+String(t).padStart(3,"0")+"ms.png";
  await page.screenshot({path:join(out,filename),animations:"disabled",fullPage:true});
  results.screenshots.push({filename,sha256:shaFile(join(out,filename)),...snap});
 }
 check("EXPLICIT_PHOTO_HANDOFF_INTERMEDIATE_FRAME",
    positions[1].photoRect.width<positions[0].photoRect.width &&
    positions[1].photoRect.width>positions[5].photoRect.width &&
    positions[1].photoRect.top<positions[0].photoRect.top,
    "Same verified image element changes box continuously 0→120→200ms");
 check("K03_GOLD_PARTICLES_PRESENT",positions[2].particleCount>=12 && positions[2].particleCount<=18);
 check("K04_TITLE_NOT_FULLY_READABLE",positions[3].cnOpacity===0 && positions[3].nameReadable==="false");
 check("K05_TITLE_REVEAL_AND_SCIENTIFIC_NAME",positions[4].cnOpacity>0 && positions[4].laOpacity>0);
 check("CHINESE_TITLE_BELOW_PHOTO",positions[5].titleTop>positions[5].photoRect.top+positions[5].photoRect.height);
 check("K06_AFTERGLOW_FINITE",positions[5].particleCount===0 && positions[5].cnOpacity===1 && positions[5].laOpacity===1);
 await seek(680);
 check("K05_FULL_READABILITY_BY_680MS",(await state()).nameReadable==="true");
 await mode("lowQuality",true);await seek(290);
 check("LOW_PERFORMANCE_MAX_SIX",(await state()).particleCount<=6);
 await mode("lowQuality",false);await mode("reduceMotion",true);await seek(290);
 check("REDUCED_MOTION_NO_PARTICLES_AND_READABLE",(await state()).particleCount===0 && (await state()).nameReadable==="true");
 await mode("reduceMotion",false);await mode("invalidGeometry",true);await seek(290);
 check("UNTRUSTED_GEOMETRY_NO_FAKE_GOLD",(await state()).particleCount===0 && (await state()).fallback==="true");
 await mode("invalidGeometry",false);await mode("recognitionState","RESULT_MEDIUM");await seek(290);
 check("MEDIUM_DOES_NOT_CELEBRATE",(await state()).particleCount===0 && (await state()).fallback==="true");
 await mode("recognitionState","RESULT_HIGH");await seek(290);
 await page.locator("#saveBtn").click();
 check("CTA_USABLE_DURING_MOTION",(await page.locator("#toast").textContent()).includes("保存按钮可操作"));
 await seek(850);await page.evaluate(()=>window.dispatchEvent(new Event("hashchange")));
 check("NO_AUTOMATIC_REPLAY_ON_HASH_CHANGE",(await state()).t===850 && (await state()).autoplayStarted===true);
 check("NO_JS_PAGE_ERRORS",jsErrors.length===0,jsErrors.join(" | "));
 // Exact responsive entry at 320px width, stage zoom preserves CTA without horizontal overflow.
 await page.setViewportSize({width:320,height:720});await seek(850);
 const narrow=await page.evaluate(()=>{
   const b=document.querySelector("#stage").getBoundingClientRect();
   const a=document.querySelector("#resultActions").getBoundingClientRect();
   return {windowWidth:innerWidth,bodyWidth:document.body.scrollWidth,stage:{left:b.left,right:b.right,bottom:b.bottom},actions:{left:a.left,right:a.right,bottom:a.bottom}};
 });
 check("MOBILE_320_STAGE_AND_CTA_VISIBLE_HORIZONTALLY",
  narrow.bodyWidth<=narrow.windowWidth+2 && narrow.actions.right<=narrow.windowWidth+2 && narrow.actions.left>=-2,
  JSON.stringify(narrow));
 results.mobile320=narrow;
 // Review-only recording: a user-triggered replay; production replay policy stays one-shot.
 await page.setViewportSize({width:1380,height:1010});
 await page.locator("#restartBtn").click();
 await page.waitForTimeout(1070);
 check("MANUAL_REVIEW_PLAYBACK_FINISHES",(await state()).t===850);
 await context.close();
 const videoDir=join(out,"video");
 results.videos=existsSync(videoDir)?readdirSync(videoDir).filter(x=>x.endsWith(".webm")).map(filename=>({filename:"video/"+filename,sha256:shaFile(join(videoDir,filename))})):[];
 results.verdict="BROWSER_FUNCTIONAL_PASS_VISUAL_REVIEW_REQUIRED";
 console.log("PASS: RR09 browser functional and canonical photo integrity | frames="+results.screenshots.length+" | evidence="+out);
} catch(error) {
 results.verdict="FAIL";
 results.error=String(error.stack||error);
 console.error(results.error);
 process.exitCode=1;
} finally {
 writeFileSync(join(out,"RR09_Browser_Review_Report.json"),JSON.stringify(results,null,2)+"\n");
 await browser?.close().catch(()=>{});
 server.kill("SIGTERM");
}
