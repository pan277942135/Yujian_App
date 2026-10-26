import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const moduleDir=process.env.PLAYWRIGHT_MODULE_DIR;
if(!moduleDir)throw new Error("Set PLAYWRIGHT_MODULE_DIR to a Playwright node_modules directory");
const {chromium}=await import(pathToFileURL(path.join(moduleDir,"playwright/index.mjs")).href);
const baseUrl=process.argv.find(v=>v.startsWith("--base-url="))?.slice("--base-url=".length)||"http://127.0.0.1:8765";
const expectedSha=process.argv.find(v=>v.startsWith("--expected-source-sha="))?.slice("--expected-source-sha=".length)||null;
const outIndex=process.argv.indexOf("--output-dir");
const outputDir=process.argv.find(v=>v.startsWith("--output-dir="))?.slice("--output-dir=".length)||(outIndex>=0?process.argv[outIndex+1]:null)||".tmp/nh01-local-compare";
fs.mkdirSync(outputDir,{recursive:true});
const browser=await chromium.launch({headless:true}),failures=[];
try{
 const page=await browser.newPage({viewport:{width:1280,height:900},deviceScaleFactor:1});
 page.on("pageerror",e=>failures.push(e.message));page.on("console",m=>{if(m.type()==="error")failures.push(m.text());});
 const response=await page.goto(baseUrl+"/design/manager/nh01_acceptance/?mode=compare",{waitUntil:"networkidle"});
 if(!response||!response.ok())throw new Error("NH01 Viewer did not load for local comparison");
 await page.waitForFunction(()=>window.NH01ViewerModelReady===true,{timeout:10000});
 await page.waitForFunction(()=>typeof window.NH01LocalCompare==="object"&&typeof window.NH01LocalCompare.compare==="function",{timeout:5000});
 const sourceSha=await page.locator('meta[name="nh01-source-sha"]').getAttribute("content");
 if(expectedSha&&sourceSha!==expectedSha)throw new Error("Viewer source SHA mismatch: "+sourceSha+" != "+expectedSha);
 for(const id of ["localAndroidScreenshot","localCompareFit","runLocalCompare","clearLocalCompare","downloadLocalCompareReport","localOverlayCanvas","localHeatmapCanvas"])if(await page.locator("#"+id).count()!==1)throw new Error("Missing local comparison control: "+id);
 async function makePng(width,height,color){const base64=await page.evaluate(({width,height,color})=>{const c=document.createElement("canvas");c.width=width;c.height=height;const x=c.getContext("2d");x.fillStyle=color;x.fillRect(0,0,width,height);return c.toDataURL("image/png").split(",")[1];},{width,height,color});return Buffer.from(base64,"base64");}
 const exactBuffer=await makePng(1080,1920,"#647a8b");
 await page.locator("#localAndroidScreenshot").setInputFiles({name:"synthetic-api28-test.png",mimeType:"image/png",buffer:exactBuffer});
 await page.waitForFunction(()=>document.querySelector("#localScreenshotMetadata")?.dataset.loaded==="true",{timeout:5000});
 await page.locator("#runLocalCompare").click();
 await page.waitForFunction(()=>document.querySelector("#localCompareStatus")?.dataset.state==="LOCAL_DIAGNOSTIC_ONLY",{timeout:10000});
 const exactReport=await page.evaluate(()=>window.NH01LocalCompare.getReport());
 if(exactReport.acceptance_gate_effect!=="NONE"||exactReport.candidate.original_dimensions.join("x")!=="1080x1920")throw new Error("Exact-size local diagnostic report provenance invalid");
 if(exactReport.privacy.upload!=="NONE")throw new Error("Local screenshot privacy boundary missing");
 const mismatchBuffer=await makePng(720,1280,"#9aaabb");
 await page.locator("#localAndroidScreenshot").setInputFiles({name:"synthetic-small-test.png",mimeType:"image/png",buffer:mismatchBuffer});
 await page.waitForFunction(()=>document.querySelector("#localScreenshotMetadata")?.dataset.loaded==="true",{timeout:5000});
 await page.locator("#runLocalCompare").click();
 await page.waitForFunction(()=>document.querySelector("#localCompareStatus")?.dataset.state==="BLOCKED_DIMENSION_MISMATCH",{timeout:5000});
 await page.locator("#localCompareFit").selectOption("contain");await page.locator("#runLocalCompare").click();
 await page.waitForFunction(()=>document.querySelector("#localCompareStatus")?.dataset.state==="LOCAL_DIAGNOSTIC_ONLY_NORMALIZED",{timeout:10000});
 const normalized=await page.evaluate(()=>window.NH01LocalCompare.getReport());
 if(normalized.classification!=="LOCAL_DIAGNOSTIC_ONLY_NORMALIZED"||normalized.acceptance_gate_effect!=="NONE")throw new Error("Normalized comparison must remain diagnostic-only");
 if(failures.length)throw new Error("Browser errors: "+failures.join(" | "));
 const report={status:"PASS",gate:"LOCAL_COMPARE_TOOL_SMOKE_ONLY",acceptance_gate_effect:"NONE",source_sha:sourceSha,checks:["exact 1080x1920 local compare","dimension mismatch fails closed in strict mode","contain normalization is visibly diagnostic-only","local image privacy boundary","report carries candidate SHA and dimensions"],synthetic_inputs_only:true,real_android_runtime:"NOT_TESTED_BY_THIS_TOOL",checked_at_utc:new Date().toISOString()};
 fs.writeFileSync(path.join(outputDir,"NH01_Local_Compare_Tool_Smoke_Report.json"),JSON.stringify(report,null,2)+"\n");
 console.log("NH01_LOCAL_COMPARE_TOOL=PASS acceptance_gate_effect=NONE");
}finally{await browser.close();}
