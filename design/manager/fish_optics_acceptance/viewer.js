const $ = (selector) => document.querySelector(selector);
const fileInput = $('#evidence-input');
const filesByPath = new Map();
const fileUrls = new Map();
let activeProfile = 'M07';
let runtimeReports = [];
let demoReport = null;
let issueRows = [];

const gates = [
  'API36_RUNTIME_GATE','GLES2_SHADER_GATE','AGSL_SHADER_GATE','REAL_MASK_GATE',
  'REAL_TIME_13_STAGE_GATE','AUTOMATIC_HANDOFF_GATE','PROCESSING_RESULT_GATE',
  'DETERMINISTIC_13_STAGE_CAPTURE_GATE','PERFORMANCE_GATE','PERIMETER_VISUAL_GATE',
  'FISH_CONTOUR_VISUAL_GATE','GOLD_GLINT_GATE','VISUAL_EVIDENCE_GATE','PHYSICAL_DEVICE_GATE',
];
const gateLabels = {
  API36_RUNTIME_GATE:'API 36 原生运行', GLES2_SHADER_GATE:'GLES2 外围 Shader',
  AGSL_SHADER_GATE:'AGSL Orb 折射', REAL_MASK_GATE:'生产 SlimSAM Mask',
  REAL_TIME_13_STAGE_GATE:'实时 13 阶段', AUTOMATIC_HANDOFF_GATE:'自动 Result 交接',
  PROCESSING_RESULT_GATE:'Processing → Result', DETERMINISTIC_13_STAGE_CAPTURE_GATE:'13 阶段关键帧',
  PERFORMANCE_GATE:'帧耗时采样', PERIMETER_VISUAL_GATE:'照片外围光效',
  FISH_CONTOUR_VISUAL_GATE:'鱼体轮廓贴合', GOLD_GLINT_GATE:'日光金流动',
  VISUAL_EVIDENCE_GATE:'原生截图与录屏', PHYSICAL_DEVICE_GATE:'Huawei CMS-AL20 真机',
};
const stageNames = [
  '照片稳定','光球苏醒','光球释放水光','照片外围激活','水光寻鱼','鱼体轮廓初显','轮廓继续形成',
  '轮廓闭合','日光金稳定','能量返回光球','光球凝字','鱼种名称揭晓','水光余辉收束',
];
const roiOrigins = {
  full:[50,50,1], perimeter:[50,48,1.5], tl:[12,14,2.2], tr:[88,14,2.2],
  bl:[12,86,2.2], br:[88,86,2.2], bottom:[50,88,1.8], fish:[50,52,2],
};

function basename(path){ return path.split('/').pop().toLowerCase(); }
function findPath(endsWith, profile = null){
  const candidates = [...filesByPath.keys()].filter(path => path.toLowerCase().endsWith(endsWith.toLowerCase()));
  if(profile){
    const matching = candidates.find(path => path.toUpperCase().includes(profile.toUpperCase()));
    if(matching) return matching;
  }
  return candidates[0] ?? null;
}
function jsonAt(endsWith, profile = null){
  const path = findPath(endsWith, profile);
  if(!path) return null;
  return filesByPath.get(path).json ?? null;
}
function objectUrl(path){
  if(!path) return '';
  if(!fileUrls.has(path)) fileUrls.set(path, URL.createObjectURL(filesByPath.get(path).file));
  return fileUrls.get(path);
}
function setImage(img, path){
  const empty = img.parentElement.querySelector('.empty-state');
  if(path){ img.src = objectUrl(path); img.dataset.path = path; img.hidden = false; if(empty) empty.hidden = true; }
  else { img.removeAttribute('src'); img.dataset.path = ''; img.hidden = true; if(empty) empty.hidden = false; }
}
function allImages(){
  return [...filesByPath.keys()].filter(path => /\.(png|jpe?g|webp)$/i.test(path));
}
function isAndroidScreenshot(path){
  const p=path.toLowerCase();
  return p.includes('screenshot') || p.includes('shared-stage-reveal/') || /\d{2}_[a-z_]+_p\d{3}\.png$/.test(p) || p.includes('recognition_');
}
function isDemoScreenshot(path){ return /demo-m07-|demo.*\.png$/i.test(path); }
function renderImageSelectors(){
  const android = $('#android-image'), demo = $('#demo-image');
  const androidPaths=allImages().filter(isAndroidScreenshot);
  const demoPaths=allImages().filter(isDemoScreenshot);
  const options=(paths, preferred) => paths.map(path=>`<option value="${escapeHtml(path)}">${escapeHtml(path.split('/').slice(-2).join('/'))}</option>`).join('');
  android.innerHTML=options(androidPaths);
  demo.innerHTML=options(demoPaths);
  const proc=androidPaths.find(p=>p.toLowerCase().endsWith('recognition_processing_photo.png'));
  const result=androidPaths.find(p=>p.toLowerCase().endsWith('recognition_result_automatic_handoff.png'));
  const reference=demoPaths.find(p=>p.toLowerCase().includes('demo-m07'));
  if(result) android.value=result; else if(proc) android.value=proc;
  if(reference) demo.value=reference;
  refreshCompare();
}
function screenPhotoBounds(profile, kind){
  if(kind==='demo'){
    const m=demoReport?.measured, b=m?.photoContent?.boundsCssPx;
    const dpr=m?.viewport?.devicePixelRatio ?? demoReport?.configuredDeviceScaleFactor;
    const width=demoReport?.screenshot?.widthPx ?? (demoReport?.viewportCssPx?.width*dpr);
    const height=demoReport?.screenshot?.heightPx ?? (demoReport?.viewportCssPx?.height*dpr);
    if(b&&dpr&&width&&height)return {left:b.left*dpr/width,top:b.top*dpr/height,right:b.right*dpr/width,bottom:b.bottom*dpr/height};
    return null;
  }
  const g=runtimeReports.find(entry=>entry.profile===profile)?.geometry;
  return g?.processing?.photo_bounds_screen_normalized ?? g?.photo?.bounds_screen_normalized ?? null;
}
function roiTransform(path,profile,kind){
  const selected=$('#roi-select').value, photo=screenPhotoBounds(profile,kind);
  if(selected==='full')return {origin:'50% 50%',scale:1};
  if(!photo)return {origin:'50% 50%',scale:1};
  const w=photo.right-photo.left,h=photo.bottom-photo.top;
  const points={
    perimeter:[photo.left+w*.5,photo.top+h*.5,1.12],
    tl:[photo.left+w*.12,photo.top+h*.12,2.1],tr:[photo.left+w*.88,photo.top+h*.12,2.1],
    bl:[photo.left+w*.12,photo.top+h*.88,2.1],br:[photo.left+w*.88,photo.top+h*.88,2.1],
    bottom:[photo.left+w*.5,photo.top+h*.9,1.8],
  };
  if(selected==='fish'){
    const g=runtimeReports.find(entry=>entry.profile===profile)?.geometry;
    const b=kind==='android'?g?.fish_contour?.bounds_screen_normalized:null;
    if(b){
      const bw=Math.max(.001,b.right-b.left),bh=Math.max(.001,b.bottom-b.top);
      return {origin:`${(b.left+bw/2)*100}% ${(b.top+bh/2)*100}%`,scale:Math.min(4,Math.max(1,.78/Math.max(bw,bh)))};
    }
    return {origin:'50% 50%',scale:1};
  }
  const [x,y,scale]=points[selected]??[.5,.5,1];
  return {origin:`${x*100}% ${y*100}%`,scale};
}
function applyRoi(profile=activeProfile){
  for(const [image,path,kind] of [
    [$('#android-preview'),$('#android-image').value,'android'],[$('#demo-preview'),$('#demo-image').value,'demo'],
    [$('#overlay-android'),$('#android-image').value,'android'],[$('#overlay-demo'),$('#demo-image').value,'demo'],
  ]){
    const t=roiTransform(path,profile,kind);image.style.transformOrigin=t.origin;image.style.transform=`scale(${t.scale})`;
  }
}
function refreshCompare(){
  const androidPath=$('#android-image').value || null;
  const demoPath=$('#demo-image').value || null;
  setImage($('#android-preview'),androidPath);
  setImage($('#demo-preview'),demoPath);
  setImage($('#overlay-android'),androidPath);
  setImage($('#overlay-demo'),demoPath);
  $('#overlay-demo').style.opacity=Number($('#opacity').value)/100;
  $('#opacity-label').value=`${$('#opacity').value}%`;
  applyRoi(activeProfile);
  const dimensions=[];
  for(const [label,path] of [['Android',androidPath],['Demo',demoPath]]){
    if(!path) continue;
    const image=new Image(); image.src=objectUrl(path);
    image.onload=()=>{
      const side=$('#compare-size');
      side.textContent=`${label} ${image.naturalWidth}×${image.naturalHeight}px`;
      if(androidPath && demoPath) createHeatmap(androidPath,demoPath);
    };
    dimensions.push(path);
  }
  if(!androidPath || !demoPath) clearHeatmap();
}
function clearHeatmap(message='当前没有可比较的同源照片像素'){
  const canvas=$('#heatmap'), ctx=canvas.getContext('2d');
  canvas.width=1; canvas.height=1; ctx.clearRect(0,0,1,1); $('#heatmap-empty').hidden=false;
  $('#heatmap-empty').textContent=message;$('#diff-summary').textContent=message;
}
function createHeatmap(androidPath,demoPath,profile=activeProfile){
  const androidGeometry=runtimeReports.find(entry=>entry.profile===profile)?.geometry;
  const androidPhotoSha=androidGeometry?.photo_identity?.sha256;
  const demoPhotoSha=demoReport?.photo_identity?.sha256 ?? demoReport?.measured?.photoContent?.sha256;
  const sameSource=Boolean(androidPhotoSha&&demoPhotoSha&&androidPhotoSha.toLowerCase()===demoPhotoSha.toLowerCase());
  const androidCrop=androidGeometry?.processing?.photo_crop_px;
  const demoCrop=demoReport?.measured?.photoContent?.screenshotBoundsPx;
  if(!sameSource||!androidCrop||!demoCrop){clearHeatmap('未提供已核验的同一原始照片裁切；不计算整图像素差');return;}
  const a=new Image(),d=new Image(); let loaded=0;
  const ready=()=>{ if(++loaded<2)return; try{
    const width=Math.min(1200,Math.max(1,Math.round(androidCrop.width))),height=Math.min(1600,Math.max(1,Math.round(androidCrop.height)));
    const input=document.createElement('canvas'); input.width=width;input.height=height;
    const ictx=input.getContext('2d',{willReadFrequently:true}); ictx.drawImage(d,demoCrop.x,demoCrop.y,demoCrop.width,demoCrop.height,0,0,width,height);
    const ref=ictx.getImageData(0,0,width,height).data;
    const target=document.createElement('canvas');target.width=width;target.height=height;
    const tctx=target.getContext('2d',{willReadFrequently:true});tctx.drawImage(a,androidCrop.x,androidCrop.y,androidCrop.width,androidCrop.height,0,0,width,height);
    const src=tctx.getImageData(0,0,width,height),out=tctx.createImageData(width,height);
    let total=0,maximum=0,count=0;
    for(let i=0;i<src.data.length;i+=4){
      const delta=(Math.abs(src.data[i]-ref[i])+Math.abs(src.data[i+1]-ref[i+1])+Math.abs(src.data[i+2]-ref[i+2]))/3;
      total+=delta;maximum=Math.max(maximum,delta);if(delta>20)count++;
      const hot=Math.min(255,delta*2.5);
      out.data[i]=Math.min(255,hot*1.05);out.data[i+1]=Math.max(0,130-hot*.4);out.data[i+2]=Math.max(0,255-hot);out.data[i+3]=Math.min(225,70+hot*.58);
    }
    const canvas=$('#heatmap');canvas.width=width;canvas.height=height;canvas.getContext('2d').putImageData(out,0,0);
    $('#heatmap-empty').hidden=true;
    $('#diff-summary').textContent=`同源照片裁切 RGB mean ${ (total/(width*height)).toFixed(1) } · max ${maximum.toFixed(0)} · pixels Δ>20 ${((count/(width*height))*100).toFixed(1)}%`;
  }catch(error){ $('#diff-summary').textContent=`无法计算：${error.message}`; }};
  a.onload=ready;d.onload=ready;a.onerror=ready;d.onerror=ready;a.src=objectUrl(androidPath);d.src=objectUrl(demoPath);
}
function renderStages(profile){
  const grid=$('#stage-grid');grid.innerHTML='';
  const paths=allImages().filter(p=>/\d{2}_[a-z_]+_p(000|050|100)\.png$/i.test(p)&&p.toUpperCase().includes(profile.toUpperCase()));
  for(let i=0;i<13;i++){
    const number=String(i+1).padStart(2,'0');
    const card=document.createElement('article');card.className='stage-card';
    card.innerHTML=`<header><span><span class="stage-number">${number} / 13</span><br><span class="stage-title">${stageNames[i]}</span></span><span class="stage-subtitle">${['P0','P1','P2','P3','P4','P5','P6','P7','R0','R1','R2','R3','R4'][i] ?? 'R5'}</span></header><div class="stage-frames"></div>`;
    const row=card.querySelector('.stage-frames');
    const match=paths.filter(path=>basename(path).startsWith(`${number}_`));
    for(const progress of ['000','050','100']){
      const path=match.find(p=>basename(p).endsWith(`_p${progress}.png`));
      if(!path){const ph=document.createElement('div');ph.className='stage-placeholder';ph.textContent=`P${Number(progress)/100} · 未采集`;row.append(ph);continue;}
      const button=document.createElement('button');button.className='stage-frame';
      button.title=`${stageNames[i]} · ${Number(progress)/100}% · deterministic projection`;
      button.innerHTML=`<img src="${objectUrl(path)}" alt="${escapeHtml(stageNames[i])} ${progress}"><span>${Number(progress)/100}%</span>`;
      button.onclick=()=>{ const select=$('#android-image'); if(![...select.options].some(o=>o.value===path)){select.add(new Option(path,path));}select.value=path;refreshCompare();window.scrollTo({top:$('.compare-panel').offsetTop,behavior:'smooth'}); };
      row.append(button);
    }
    grid.append(card);
  }
}
function renderVideo(profile){
  const paths=[...filesByPath.keys()].filter(p=>/fish_optics_[^/]*processing_result\.mp4$/i.test(p)&&p.toUpperCase().includes(profile.toUpperCase()));
  const path=paths.find(p=>p.toUpperCase().includes(profile.toUpperCase()))??paths[0]??null;
  const video=$('#runtime-video');
  if(path){video.src=objectUrl(path);$('#video-source').textContent=path.split('/').slice(-2).join('/');}
  else{video.removeAttribute('src');$('#video-source').textContent='未载入真实 screenrecord';}
}
function renderGates(profile){
  const result=runtimeReports.find(entry=>entry.profile===profile)?.data;
  const values=result?.gates??{};
  $('#gate-grid').innerHTML=gates.map(name=>{
    const value=values[name]??(name==='PHYSICAL_DEVICE_GATE'?'NOT_RUN':'NOT_PROVEN');
    const kind=/PASS|MEASURED|CAPTURED/.test(value)?'pass':/FAIL|BLOCKED/.test(value)?'fail':/NOT_RUN/.test(value)?'not-run':'review';
    return `<div class="gate-card ${kind}"><span class="name">${gateLabels[name]}</span><span class="value">${escapeHtml(value)}</span></div>`;
  }).join('');
  $('#overall-status').textContent=result?.classification??'等待运行证据';
  $('#overall-status').style.color=result?.classification==='EXACT_HEAD_RUNTIME_CAPTURED'?'var(--good)':'var(--gold)';
  $('#physical-status').textContent=`Huawei CMS-AL20 · ${values.PHYSICAL_DEVICE_GATE??'NOT_RUN'}`;
  if(result){
    const profileInfo=result.device_profile??{};
    $('#source-head').textContent=`SOURCE · ${(result.source_sha??'').slice(0,12)} · ${profileInfo.profile??profile}`;
    $('#run-link').textContent=`API ${result.api_level} · ${profileInfo.logical_width_dp}×${profileInfo.logical_height_dp}dp · ${profileInfo.physical_width_px}×${profileInfo.physical_height_px}px · ${profileInfo.density_dpi}dpi`;
    $('#runtime-data').textContent=JSON.stringify({device_profile:result.device_profile,mask_provider:result.mask_provider,runtime_shader_evidence:result.runtime_shader_evidence,performance_data:result.performance_data?.profiles??result.performance_data,gates:result.gates},null,2);
  }
}
function renderGeometry(profile){
  const report=runtimeReports.find(entry=>entry.profile===profile)?.geometry??demoReport?.measured??demoReport;
  $('#geometry-data').textContent=report?JSON.stringify(report,null,2):'等待 FISH_OPTICS_GEOMETRY_REPORT_V1.json 或 demo-geometry.json';
}
function renderIssues(){
  const body=$('#issue-rows');
  if(!issueRows.length){body.innerHTML='<tr><td colspan="5" class="empty-row">载入 FISH_OPTICS_VISUAL_ISSUE_REGISTER.csv</td></tr>';$('#issue-count').textContent='0 项';return;}
  $('#issue-count').textContent=`${issueRows.length} 项`;
  body.innerHTML=issueRows.map(row=>`<tr><td><code>${escapeHtml(row['Failure ID']??row.id??'')}</code><br>${escapeHtml(row.Status??row.status??'')}</td><td>${escapeHtml(row.Category??row.category??'')}</td><td>${escapeHtml(row.Actual??row.actual??'')}<br><span class="muted">${escapeHtml(row['Root Cause']??row.root_cause??'')}</span></td><td>${escapeHtml(row.Fix??row.fix??'')}<br><span class="muted">${escapeHtml(row.Retest??row.retest??'')}</span></td><td>${escapeHtml(row.Evidence??row.evidence??'')}</td></tr>`).join('');
}
function escapeHtml(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function parseCsv(text){
  const rows=[];let row=[],cell='',quoted=false;
  for(let i=0;i<text.length;i++){const c=text[i];if(c==='"'&&quoted&&text[i+1]==='"'){cell+='"';i++;}else if(c==='"'){quoted=!quoted;}else if(c===','&&!quoted){row.push(cell);cell='';}else if((c==='\n'||c==='\r')&&!quoted){if(c==='\r'&&text[i+1]==='\n')i++;row.push(cell);cell='';if(row.some(v=>v!==''))rows.push(row);row=[];}else cell+=c;}
  if(cell||row.length){row.push(cell);rows.push(row);}const head=rows.shift()??[];return rows.map(values=>Object.fromEntries(head.map((h,i)=>[h,values[i]??''])));
}
async function ingest(files){
  for(const file of files){
    const relative=file.webkitRelativePath||file.name;
    const key=relative.replaceAll('\\','/');
    const entry={file,json:null};
    if(/\.json$/i.test(key)){try{entry.json=JSON.parse(await file.text());}catch{}}
    if(/\.csv$/i.test(key)&&key.toLowerCase().includes('issue_register'))issueRows=parseCsv(await file.text());
    filesByPath.set(key,entry);
  }
  runtimeReports=[];demoReport=null;
  for(const [path,entry] of filesByPath){
    const p=path.toLowerCase();
    if(p.endsWith('/runtime-result.json')||p==='runtime-result.json'){
      const profile=entry.json?.device_profile?.profile??path.match(/(M04|M07|M10|HUAWEI_CMS_AL20)/i)?.[1]??'M07';
      const productionPath=findPath('RR09_Production_Qualification_Report.json',profile);
      const geometryPath=findPath('FISH_OPTICS_GEOMETRY_REPORT_V1.json',profile);
      runtimeReports.push({profile:profile.toUpperCase(),data:entry.json,production:filesByPath.get(productionPath)?.json,geometry:filesByPath.get(geometryPath)?.json});
    }
    if(p.endsWith('/demo-geometry.json')||p==='demo-geometry.json')demoReport=entry.json;
  }
  const profileSelect=$('#profile-select');
  const profiles=[...new Set(runtimeReports.map(r=>r.profile))];
  if(profiles.length){profileSelect.innerHTML=profiles.map(p=>`<option value="${escapeHtml(p)}">${escapeHtml(p)}</option>`).join('');if(profiles.includes(activeProfile))profileSelect.value=activeProfile;else{activeProfile=profiles[0];profileSelect.value=activeProfile;}}
  $('#loaded-files').textContent=`已载入 ${filesByPath.size} 个文件 · ${runtimeReports.length} 个 Android Profile 报告 · ${demoReport?'Demo 几何已载入':'Demo 几何未载入'}`;
  renderImageSelectors();renderProfile();renderIssues();
}
function renderProfile(){
  activeProfile=$('#profile-select').value||activeProfile;
  renderGates(activeProfile);renderStages(activeProfile);renderVideo(activeProfile);renderGeometry(activeProfile);applyRoi(activeProfile);refreshCompare();
}

fileInput.addEventListener('change',event=>ingest([...event.target.files]));
$('#android-image').addEventListener('change',refreshCompare);
$('#demo-image').addEventListener('change',refreshCompare);
$('#roi-select').addEventListener('change',applyRoi);
$('#opacity').addEventListener('input',refreshCompare);
$('#profile-select').addEventListener('change',renderProfile);
clearHeatmap();
fetch('data/acceptance.json').then(r=>r.ok?r.json():null).then(data=>{
  if(!data)return;
  if(data.demo_commit)$('#source-head').textContent=`DEMO · ${data.demo_commit.slice(0,12)}`;
  if(data.physical_device_gate)$('#physical-status').textContent=`Huawei CMS-AL20 · ${data.physical_device_gate}`;
}).catch(()=>{});
