(() => {
  "use strict";
  const WIDTH = 1080, HEIGHT = 1920;
  const FROZEN_SHA = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377";
  const FROZEN_PATH = "design/system/core_visual_v1/reference/normal_home_v1.png";
  const ROI = [
    {id:"header",label:"Header",box:[88,104,992,208],note:"Avatar authority remains a separate review item."},
    {id:"statistics",label:"Statistics",box:[150,304,930,420],note:"Compare geometry separately from font rasterization."},
    {id:"recent_header",label:"Recent Catch Header",box:[108,494,972,564],note:"Text and chevron authority must be checked separately."},
    {id:"hero",label:"Hero Card",box:[170,596,910,1476],note:"Dynamic photo ROI; never treat unrelated image pixels as parity evidence."},
    {id:"cta",label:"CTA glyph",box:[433,1527,647,1559],note:"Use the source mask and quantitative contract for formal acceptance."},
    {id:"camera",label:"Camera core",box:[450,1590,629,1774],note:"Visual ink is distinct from the semantic touch target."}
  ];
  let selectedFile=null, selectedImage=null, objectUrl=null, latestReport=null;
  const $=id=>document.getElementById(id);
  const setStatus=(state,message)=>{const node=$("localCompareStatus");node.dataset.state=state;node.textContent=message;};
  const hex=bytes=>Array.from(new Uint8Array(bytes)).map(v=>v.toString(16).padStart(2,"0")).join("");
  const sha256=async file=>hex(await crypto.subtle.digest("SHA-256",await file.arrayBuffer()));

  function canvasForImage(image,fit){
    const canvas=document.createElement("canvas");canvas.width=WIDTH;canvas.height=HEIGHT;
    const ctx=canvas.getContext("2d",{willReadFrequently:true});ctx.fillStyle="#e8edf0";ctx.fillRect(0,0,WIDTH,HEIGHT);
    if(fit==="strict"){if(image.naturalWidth!==WIDTH||image.naturalHeight!==HEIGHT)return null;ctx.drawImage(image,0,0,WIDTH,HEIGHT);}
    else{const scale=Math.min(WIDTH/image.naturalWidth,HEIGHT/image.naturalHeight);const w=image.naturalWidth*scale,h=image.naturalHeight*scale;ctx.drawImage(image,(WIDTH-w)/2,(HEIGHT-h)/2,w,h);}
    return canvas;
  }
  function makeTargetCanvas(){
    const source=$("localFrozenTarget"),canvas=document.createElement("canvas");canvas.width=WIDTH;canvas.height=HEIGHT;
    canvas.getContext("2d",{willReadFrequently:true}).drawImage(source,0,0,WIDTH,HEIGHT);return canvas;
  }
  function metrics(aCanvas,bCanvas,box){
    const a=aCanvas.getContext("2d",{willReadFrequently:true}).getImageData(box[0],box[1],box[2]-box[0],box[3]-box[1]).data;
    const b=bCanvas.getContext("2d",{willReadFrequently:true}).getImageData(box[0],box[1],box[2]-box[0],box[3]-box[1]).data;
    let total=0,changed=0;
    for(let i=0;i<a.length;i+=4){const d=Math.max(Math.abs(a[i]-b[i]),Math.abs(a[i+1]-b[i+1]),Math.abs(a[i+2]-b[i+2]));total+=Math.abs(a[i]-b[i])+Math.abs(a[i+1]-b[i+1])+Math.abs(a[i+2]-b[i+2]);if(d>8)changed++;}
    const pixels=a.length/4;return{pixel_count:pixels,mean_absolute_difference_rgb:Number((total/(pixels*3)).toFixed(4)),changed_pixel_fraction_over_8:Number((changed/pixels).toFixed(6))};
  }
  function buildHeatmap(target,candidate){
    const a=target.getContext("2d",{willReadFrequently:true}).getImageData(0,0,WIDTH,HEIGHT),b=candidate.getContext("2d",{willReadFrequently:true}).getImageData(0,0,WIDTH,HEIGHT);
    const out=document.createElement("canvas");out.width=WIDTH;out.height=HEIGHT;const ctx=out.getContext("2d"),data=ctx.createImageData(WIDTH,HEIGHT);let total=0,changed=0;
    for(let i=0;i<a.data.length;i+=4){const r=Math.abs(a.data[i]-b.data[i]),g=Math.abs(a.data[i+1]-b.data[i+1]),bl=Math.abs(a.data[i+2]-b.data[i+2]),d=Math.max(r,g,bl);total+=r+g+bl;if(d>8)changed++;data.data[i]=d;data.data[i+1]=Math.max(0,90-Math.round(d*.35));data.data[i+2]=18;data.data[i+3]=255;}
    ctx.putImageData(data,0,0);return{canvas:out,summary:{pixel_count:WIDTH*HEIGHT,mean_absolute_difference_rgb:Number((total/(WIDTH*HEIGHT*3)).toFixed(4)),changed_pixel_fraction_over_8:Number((changed/(WIDTH*HEIGHT)).toFixed(6))}};
  }
  function drawOverlay(target,candidate){const canvas=document.createElement("canvas");canvas.width=WIDTH;canvas.height=HEIGHT;const ctx=canvas.getContext("2d");ctx.drawImage(target,0,0);ctx.globalAlpha=.5;ctx.drawImage(candidate,0,0);ctx.globalAlpha=1;return canvas;}
  function canvasToVisible(source,id){const target=$(id);target.width=WIDTH;target.height=HEIGHT;const ctx=target.getContext("2d");ctx.clearRect(0,0,WIDTH,HEIGHT);ctx.drawImage(source,0,0);}
  function renderRois(target,candidate,classification){
    const wrap=$("localCompareRoiTable");wrap.replaceChildren();const table=document.createElement("table");table.className="local-compare-table";table.innerHTML="<thead><tr><th>区域</th><th>MAE RGB</th><th>差异像素比例 (&gt;8)</th><th>状态</th></tr></thead>";const body=document.createElement("tbody");
    for(const item of ROI){const result=metrics(target,candidate,item.box),row=document.createElement("tr");[item.label,String(result.mean_absolute_difference_rgb),(result.changed_pixel_fraction_over_8*100).toFixed(2)+"%",classification].forEach(value=>{const cell=document.createElement("td");cell.textContent=value;row.append(cell);});body.append(row);}table.append(body);wrap.append(table);
  }
  function downloadReport(){
    if(!latestReport)return;const blob=new Blob([JSON.stringify(latestReport,null,2)+"\n"],{type:"application/json"}),url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download="NH01_Local_Screenshot_Compare_DIAGNOSTIC_ONLY.json";a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  }
  async function compare(){
    if(!selectedFile||!selectedImage){setStatus("BLOCKED_NO_SCREENSHOT","请先选择 Android 截图。");return;}
    const fit=$("localCompareFit").value,isExact=selectedImage.naturalWidth===WIDTH&&selectedImage.naturalHeight===HEIGHT;
    if(fit==="strict"&&!isExact){latestReport=null;$("downloadLocalCompareReport").disabled=true;setStatus("BLOCKED_DIMENSION_MISMATCH","严格模式要求 "+WIDTH+"×"+HEIGHT+"；当前截图 "+selectedImage.naturalWidth+"×"+selectedImage.naturalHeight+"。可切换等比例居中适配查看诊断，但不得用于验收。");$("localCompareSummary").replaceChildren();$("localCompareRoiTable").replaceChildren();return;}
    const targetImage=$("localFrozenTarget");if(!targetImage.complete||targetImage.naturalWidth!==WIDTH||targetImage.naturalHeight!==HEIGHT){setStatus("BLOCKED_TARGET_SOURCE","Frozen 目标图未成功加载或尺寸不符。");return;}
    const target=makeTargetCanvas(),candidate=canvasForImage(selectedImage,fit);if(!candidate){setStatus("BLOCKED_DIMENSION_MISMATCH","截图尺寸不符合严格对比要求。");return;}
    const overlay=drawOverlay(target,candidate),heat=buildHeatmap(target,candidate);canvasToVisible(overlay,"localOverlayCanvas");canvasToVisible(heat.canvas,"localHeatmapCanvas");
    $("localScreenshotPreview").src=objectUrl;$("localScreenshotPreview").alt="Local Android screenshot "+selectedImage.naturalWidth+"×"+selectedImage.naturalHeight;$("localScreenshotMetadata").dataset.loaded="true";
    const localHash=await sha256(selectedFile),normalized=fit!=="strict"||!isExact,classification=normalized?"LOCAL_DIAGNOSTIC_ONLY_NORMALIZED":"LOCAL_DIAGNOSTIC_ONLY";
    latestReport={schema_version:1,classification,acceptance_gate_effect:"NONE",privacy:{upload:"NONE",storage:"BROWSER_MEMORY_ONLY",note:"The selected screenshot is not sent to a server and is not committed."},source_sha:document.querySelector('meta[name="nh01-source-sha"]')?.content||null,target:{path:FROZEN_PATH,sha256:FROZEN_SHA,dimensions:[WIDTH,HEIGHT],role:"Frozen reference; only authoritative for a design state that has not been explicitly superseded."},candidate:{filename:selectedFile.name,sha256:localHash,original_dimensions:[selectedImage.naturalWidth,selectedImage.naturalHeight],compared_dimensions:[WIDTH,HEIGHT],fit_mode:fit},summary:{...heat.summary,classification},rois:ROI.map(item=>({id:item.id,label:item.label,bbox_xyxy:item.box,...metrics(target,candidate,item.box),status:classification,note:item.note})),note:"Local visual diagnostic only. It cannot close exact-SHA Android runtime, responsive, visual-parity, physical-device, or release gates.",generated_at_utc:new Date().toISOString()};
    $("localCompareSummary").replaceChildren();
    [["Classification",classification],["Screenshot SHA-256",localHash],["Mean RGB absolute error",heat.summary.mean_absolute_difference_rgb],["Changed pixels > 8",(heat.summary.changed_pixel_fraction_over_8*100).toFixed(2)+"%"]].forEach(pair=>{const card=document.createElement("div"),small=document.createElement("small"),strong=document.createElement("strong");small.textContent=pair[0];strong.textContent=String(pair[1]);card.append(small,strong);$("localCompareSummary").append(card);});
    $("downloadLocalCompareReport").disabled=false;setStatus(classification,"已生成本地差异诊断。结果仅供人工定位，不会写入 GitHub、不会上传截图，也不会改变任何验收 Gate。"+(normalized?" 当前图片经过等比例居中适配，像素数值不可用于正式验收。":""));
  }
  function clear(){
    selectedFile=null;selectedImage=null;latestReport=null;if(objectUrl)URL.revokeObjectURL(objectUrl);objectUrl=null;$("localAndroidScreenshot").value="";$("localScreenshotPreview").removeAttribute("src");$("localScreenshotMetadata").dataset.loaded="false";
    ["localOverlayCanvas","localHeatmapCanvas"].forEach(id=>{const canvas=$(id);canvas.getContext("2d").clearRect(0,0,canvas.width,canvas.height);});$("localCompareSummary").replaceChildren();$("localCompareRoiTable").replaceChildren();$("downloadLocalCompareReport").disabled=true;setStatus("NOT_RUN","尚未选择截图。");
  }
  function bind(){
    $("localAndroidScreenshot").addEventListener("change",event=>{
      const file=event.target.files&&event.target.files[0];if(!file){clear();return;}if(!/^image\/(png|jpeg|webp)$/.test(file.type)){setStatus("BLOCKED_FILE_TYPE","仅支持 PNG、JPEG 或 WebP 图片。");return;}
      selectedFile=file;if(objectUrl)URL.revokeObjectURL(objectUrl);objectUrl=URL.createObjectURL(file);const image=new Image();
      image.onload=()=>{selectedImage=image;$("localScreenshotPreview").src=objectUrl;$("localScreenshotMetadata").dataset.loaded="true";setStatus("READY","已在浏览器本地读取 "+file.name+" · "+image.naturalWidth+"×"+image.naturalHeight+"。尚未计算差异。");};
      image.onerror=()=>setStatus("BLOCKED_IMAGE_DECODE","浏览器无法解码所选图片。");image.src=objectUrl;
    });
    $("runLocalCompare").addEventListener("click",()=>compare().catch(error=>setStatus("BLOCKED_COMPARE_ERROR","本地比较失败："+error.message)));
    $("clearLocalCompare").addEventListener("click",clear);$("downloadLocalCompareReport").addEventListener("click",downloadReport);
    $("localCompareFit").addEventListener("change",()=>{if(selectedFile)setStatus("READY","比较模式已改变，请重新生成诊断。");});
    window.NH01LocalCompare={clear,compare,getReport:()=>latestReport};setStatus("NOT_RUN","尚未选择截图。");
  }
  document.addEventListener("DOMContentLoaded",bind);
})();
