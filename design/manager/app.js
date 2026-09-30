const REGISTRY_URL = "../registry/experience_registry_v1.json";
const DESIGN_MODALITIES = ["behavior", "visual", "motion", "haptic", "sound", "assets"];
const ICONS = {
  FROZEN:"✓", ACTIVE_CLOSURE:"◐", PARTIAL:"◒", MISSING:"×",
  RUNTIME_ONLY:"↗", DESIGN_ONLY:"◇", DEPRECATED:"—", CANDIDATE:"◐",
  SPEC_TO_FREEZE:"◐", TO_DESIGN:"○", SOURCE_PRESENT_AUTHORITY_REVISION_PENDING:"◐"
};
const POLICY_LABELS = {
  INDEPENDENT_HIFI:"独立高保",
  COMBINED_BOARD:"合并规范图",
  AUTHORITY_BOARD:"Authority 规范图",
  OVERVIEW_BOARD:"总览规范图"
};

let registry=null;
let selectedId=null;
let selectedSecondaryId=null;
const navigationCache=new Map();

const el=id=>document.getElementById(id);
const esc=value=>String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const repoHref=path=>path ? "../../"+path : "#";
const isImage=path=>!!path && /\.(png|jpe?g|webp|gif)$/i.test(path);
const currentVersion=feature=>(feature.design_versions||[]).find(v=>v.current)||(feature.design_versions||[])[0]||null;
const statusClass=status => ["FROZEN","ACTIVE_CLOSURE","PARTIAL","MISSING","RUNTIME_ONLY","DESIGN_ONLY","DEPRECATED","CANDIDATE"].includes(status) ? status : (status==="TO_DESIGN" ? "MISSING" : "ACTIVE_CLOSURE");

function statusBadge(status){
  const safe=status||"MISSING";
  return '<span class="badge status-'+esc(statusClass(safe))+'">'+(ICONS[safe]||"•")+' '+esc(safe)+'</span>';
}

function routeHash(featureId, secondaryId=null){
  return "#page/"+encodeURIComponent(featureId)+(secondaryId?"/"+encodeURIComponent(secondaryId):"");
}
function parseRoute(){
  const raw=decodeURIComponent(location.hash.replace(/^#/,""));
  if(raw.startsWith("page/")){
    const parts=raw.slice(5).split("/").filter(Boolean);
    return {featureId:parts[0]||null, secondaryId:parts[1]||null};
  }
  return {featureId:raw||null, secondaryId:null};
}
function setRoute(featureId,secondaryId=null){
  history.replaceState(null,"",routeHash(featureId,secondaryId));
}

async function loadNavigation(feature){
  const path=feature?.secondary_navigation;
  if(!path) return null;
  if(navigationCache.has(path)) return navigationCache.get(path);
  try{
    const response=await fetch(repoHref(path),{cache:"no-store"});
    if(!response.ok) throw new Error("HTTP "+response.status);
    const nav=await response.json();
    navigationCache.set(path,nav);
    return nav;
  }catch(error){
    navigationCache.set(path,{error:error.message,level_2:[]});
    return navigationCache.get(path);
  }
}

function renderSummary(features){
  const counts={};
  features.forEach(f=>counts[f.design_overall]=(counts[f.design_overall]||0)+1);
  el("summary").innerHTML='<span class="summary-chip"><b>'+features.length+'</b> modules</span>'+
    Object.entries(counts).sort().map(([k,v])=>'<span class="summary-chip">'+(ICONS[k]||"•")+' '+esc(k)+' <b>'+v+'</b></span>').join("");
}
function fillStatusFilter(features){
  const values=[...new Set(features.map(f=>f.design_overall))].sort();
  el("statusFilter").innerHTML='<option value="">全部状态</option>'+values.map(v=>'<option value="'+esc(v)+'">'+esc(v)+'</option>').join("");
}
function filteredFeatures(){
  const q=el("searchInput").value.trim().toLowerCase();
  const status=el("statusFilter").value;
  return registry.features.filter(f=>(!q||JSON.stringify(f).toLowerCase().includes(q))&&(!status||f.design_overall===status));
}

function renderList(){
  const features=filteredFeatures();
  el("moduleList").innerHTML=features.map(f=>{
    const active=f.id===selectedId?" active":"";
    const nav=f.secondary_navigation ? navigationCache.get(f.secondary_navigation) : null;
    const children=(active && nav?.level_2?.length)
      ? '<div class="secondary-list">'+nav.level_2.map(item=>{
          const current=item.id===selectedSecondaryId?" active":"";
          return '<button class="secondary-item'+current+'" data-feature="'+esc(f.id)+'" data-secondary="'+esc(item.id)+'">'+
            '<span class="secondary-code">'+esc(item.id)+'</span><span>'+esc(item.label_zh||item.label_en||item.id)+'</span>'+
            '</button>';
        }).join("")+'</div>'
      : "";
    return '<div class="module-group"><button class="module-item'+active+'" data-id="'+esc(f.id)+'">'+
      '<div class="module-name"><span>'+esc(f.display_name||f.id)+'</span>'+statusBadge(f.design_overall)+'</div>'+
      '<div class="module-path">'+esc(f.owner_path)+'</div></button>'+children+'</div>';
  }).join("")||'<div class="preview-empty">没有匹配模块</div>';

  document.querySelectorAll(".module-item").forEach(btn=>btn.addEventListener("click",()=>selectModule(btn.dataset.id,null,true)));
  document.querySelectorAll(".secondary-item").forEach(btn=>btn.addEventListener("click",()=>selectModule(btn.dataset.feature,btn.dataset.secondary,true)));
}

function renderPreview(feature,secondary){
  const version=currentVersion(feature);
  const visual=secondary?.authority||secondary?.source||version?.visual_authority||feature.modalities?.visual?.authority;
  el("visualAuthorityTitle").textContent=secondary ? ((secondary.label_zh||secondary.id)+"｜视觉 / Source") : "当前视觉 Authority";
  if(isImage(visual)){
    el("visualPreview").innerHTML='<a href="'+esc(repoHref(visual))+'" target="_blank" rel="noreferrer">'+
      '<img src="'+esc(repoHref(visual))+'" alt="'+esc(secondary?.label_zh||feature.display_name||feature.id)+' visual authority"></a>';
  }else if(visual){
    el("visualPreview").innerHTML='<div class="preview-empty">当前 Authority 不是直接图片。<br><br>'+
      '<a href="'+esc(repoHref(visual))+'" target="_blank" rel="noreferrer">'+esc(visual)+'</a></div>';
  }else{
    el("visualPreview").innerHTML='<div class="preview-empty">该二级内容尚未生成视觉 Authority。<br><br>状态：'+esc(secondary?.status||"MISSING")+'</div>';
  }
}

function renderModalities(feature){
  el("modalityGrid").innerHTML=DESIGN_MODALITIES.map(name=>{
    const item=feature.modalities?.[name]||{status:"MISSING",authority:null};
    const authority=item.authority?'<a href="'+esc(repoHref(item.authority))+'" target="_blank" rel="noreferrer">'+esc(item.authority)+'</a>':'—';
    const note=item.note?'<div class="authority">'+esc(item.note)+'</div>':'';
    return '<div class="modality-card"><div class="modality-top"><div class="modality-name">'+esc(name)+'</div>'+statusBadge(item.status)+'</div>'+
      '<div class="authority"><b>Authority</b><br>'+authority+'</div>'+note+'</div>';
  }).join("");
}
function renderVersions(feature){
  const versions=feature.design_versions||[];
  if(!versions.length){el("versionTimeline").innerHTML='<div class="preview-empty">尚未建立版本历史。</div>';return;}
  el("versionTimeline").innerHTML=versions.map(v=>{
    const refs=[v.visual_authority,v.spec_authority].filter(Boolean);
    const desc=refs.map(r=>'<a href="'+esc(repoHref(r))+'" target="_blank" rel="noreferrer">'+esc(r)+'</a>').join("<br>");
    return '<div class="version-row"><div><div class="version-id">'+esc(v.version)+'</div>'+(v.current?'<div class="version-current">CURRENT</div>':'')+
      '</div><div>'+statusBadge(v.status)+'</div><div class="version-desc">'+(desc||"No authority path")+(v.note?'<br><br>'+esc(v.note):'')+'</div></div>';
  }).join("");
}

function renderSecondary(feature,nav,secondary){
  if(!nav || !nav.level_2?.length){
    el("secondarySection").classList.add("hidden");
    return;
  }
  el("secondarySection").classList.remove("hidden");
  el("secondaryTitle").textContent=(nav.level_1?.label_zh||feature.display_name||feature.id)+"｜二级菜单";
  el("secondaryPrinciple").textContent=nav.principle||"";
  el("secondaryNav").innerHTML=nav.level_2.map(item=>{
    const active=item.id===secondary?.id?" active":"";
    return '<button class="secondary-tab'+active+'" data-id="'+esc(item.id)+'"><span>'+esc(item.id)+'</span><b>'+esc(item.label_zh||item.label_en||item.id)+'</b></button>';
  }).join("");
  document.querySelectorAll(".secondary-tab").forEach(btn=>btn.addEventListener("click",()=>selectModule(feature.id,btn.dataset.id,true)));

  if(!secondary){ el("secondaryDetail").innerHTML=""; return; }
  const links=[secondary.page,secondary.authority,secondary.source].filter(Boolean);
  el("secondaryDetail").innerHTML=
    '<div class="secondary-detail-head"><div><div class="secondary-detail-code">'+esc(secondary.id)+'</div>'+
    '<h4>'+esc(secondary.label_zh||secondary.label_en||secondary.id)+'</h4>'+
    '<div class="secondary-en">'+esc(secondary.label_en||"")+'</div></div>'+
    '<div class="secondary-badges">'+statusBadge(secondary.status)+'<span class="policy-chip">'+esc(POLICY_LABELS[secondary.output_policy]||secondary.output_policy||"—")+'</span></div></div>'+
    ((secondary.contains||[]).length?'<div class="contains-grid">'+secondary.contains.map(x=>'<div class="contains-item">'+esc(x)+'</div>').join("")+'</div>':'')+
    (links.length?'<div class="secondary-links">'+links.map(p=>'<a href="'+esc(repoHref(p))+'" target="_blank" rel="noreferrer">'+esc(p)+'</a>').join("")+'</div>':'');
}

async function selectModule(id,secondaryId=null,updateRoute=false){
  selectedId=id;
  const feature=registry.features.find(f=>f.id===id);
  if(!feature) return;
  const nav=await loadNavigation(feature);
  const items=nav?.level_2||[];
  let secondary=secondaryId ? items.find(x=>x.id===secondaryId) : null;
  if(!secondary && items.length) secondary=items[0];
  selectedSecondaryId=secondary?.id||null;
  if(updateRoute) setRoute(id,secondaryId||null);
  renderList();

  el("emptyState").classList.add("hidden");
  el("detail").classList.remove("hidden");
  el("pageTitle").textContent=feature.display_name||feature.id;
  el("pageStatus").innerHTML=statusBadge(feature.design_overall);
  el("moduleName").textContent=feature.display_name||feature.id;
  el("overallBadge").innerHTML=statusBadge(feature.design_overall);

  const version=currentVersion(feature);
  el("moduleMeta").innerHTML=[
    ["Feature ID",feature.id],["Owner Path",feature.owner_path],["Current Version",version?.version||"—"],["Version Status",version?.status||"—"]
  ].map(([label,value])=>'<div class="meta-card"><div class="meta-label">'+esc(label)+'</div><div class="meta-value">'+esc(value)+'</div></div>').join("");

  if(feature.note){el("moduleNote").textContent=feature.note;el("moduleNote").classList.remove("hidden");}
  else el("moduleNote").classList.add("hidden");

  renderSecondary(feature,nav,secondary);
  renderPreview(feature,secondary);
  renderModalities(feature);
  renderVersions(feature);
  renderList();
}

async function init(){
  try{
    const response=await fetch(REGISTRY_URL,{cache:"no-store"});
    if(!response.ok) throw new Error("HTTP "+response.status);
    registry=await response.json();
  }catch(error){
    document.body.innerHTML='<div style="padding:40px;font-family:system-ui"><h2>Design Manager 无法读取 Registry</h2><pre>'+esc(error.message)+'</pre></div>';
    return;
  }
  const features=registry.features||[];
  renderSummary(features);
  fillStatusFilter(features);
  renderList();

  const route=parseRoute();
  const initial=features.some(f=>f.id===route.featureId)?route.featureId:features[0]?.id;
  if(initial) await selectModule(initial,route.secondaryId,false);

  el("searchInput").addEventListener("input",renderList);
  el("statusFilter").addEventListener("change",renderList);
  window.addEventListener("hashchange",async()=>{const r=parseRoute();if(r.featureId) await selectModule(r.featureId,r.secondaryId,false);});
}
init();
