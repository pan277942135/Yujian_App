(() => {
"use strict";
const D=Object.fromEntries(["stage","photoState","photoSha","runtimeResult","fishPhoto","photoBox","resultHead","fishOverlay","edgeField","haloOuter","haloInner","haloCore","haloComet","processingCaption","resultBody","resultMeta","resultActions","storyPlaceholder","speciesArea","cnSpecies","laSpecies","formationGlyphs","titleSweep","effects","frameCode","frameTime","timeline","playBtn","pauseBtn","restartBtn","steps","reduceMotion","lowQuality","invalidGeometry","recognitionState","continueBtn","saveBtn","toast"].map(id=>[id,document.getElementById(id)]));
const TOTAL=850, TARGET_SHA="6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
const FRAME=[["K01_FISH_FOCUS","鱼体高亮锁定",0],["K02_PHOTO_CONTINUITY","照片自然接续",1],["K03_GOLD_TRANSFER","金光向下转移",201],["K04_NAME_FORMATION","名称凝结中",381],["K05_NAME_REVEAL","鱼种名称揭晓",551],["K06_AFTERGLOW_FADE","余辉完全消散",681]];
const S={t:0,playing:false,ready:false,photoTrusted:false,contourValid:false,fixture:null,started:false,completed:false,origin:0,raf:0,reduced:false,low:false,invalid:false,classification:"RESULT_HIGH",particleCount:0};
const ctx=D.effects.getContext("2d");
const lerp=(a,b,p)=>a+(b-a)*p,clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const progress=(t,start,end)=>clamp((t-start)/(end-start));
const ease=p=>1-Math.pow(1-clamp(p),3);
const phases=t=>t===0?0:t<=200?1:t<=380?2:t<=550?3:t<=680?4:5;
function phase(){return FRAME[phases(S.t)][0]}
function fitPhone(){
  D.stage.style.width="360px";D.stage.style.height="780px";
  const usable=D.stage.parentElement.clientWidth;
  const scale=Math.min(1,Math.max(.1,(usable-4)/360));
  D.stage.style.zoom=String(scale);
}
function fieldRectAt(t) {
  const p=ease(progress(t,0,200));
  return {x:lerp(0,18,p),y:lerp(125,115,p),w:lerp(360,324,p),h:lerp(490,401,p)};
}
function contourAnchor(box){
  const fixture=S.fixture;
  if(!fixture || !S.contourValid) return null;
  const [x,y]=fixture.manual_contour.gold_transfer_origin_px;
  const [w,h]=[fixture.photo.width,fixture.photo.height];
  const sc=Math.min(box.w/w,box.h/h),padX=(box.w-w*sc)/2,padY=(box.h-h*sc)/2;
  return {x:box.x+padX+x*sc,y:box.y+padY+y*sc,scale:sc};
}
function cubicPoint(p,a,b,c,d){
 const q=1-p;return {x:q*q*q*a.x+3*q*q*p*b.x+3*q*p*p*c.x+p*p*p*d.x,y:q*q*q*a.y+3*q*q*p*b.y+3*q*p*p*c.y+p*p*p*d.y};
}
function pathPoints(box){
 const a=contourAnchor(box);if(!a)return null;
 const d={x:89,y:556};const b={x:a.x+26,y:a.y+68},c={x:56,y:d.y-62};
 return {a,b,c,d};
}
function blurredDot(x,y,r,alpha){
 if(r<=0 || alpha<=0)return;
 const grad=ctx.createRadialGradient(x,y,0,x,y,r);
 grad.addColorStop(0,"rgba(255,245,213,"+alpha+")");
 grad.addColorStop(.23,"rgba(255,215,143,"+(alpha*.60)+")");
 grad.addColorStop(1,"rgba(233,180,89,0)");
 ctx.fillStyle=grad;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();
}
function renderEffects(box){
 ctx.setTransform(2,0,0,2,0,0);ctx.clearRect(0,0,360,780);
 S.particleCount=0;
 const full=S.photoTrusted && S.contourValid && !S.invalid && !S.reduced && S.classification==="RESULT_HIGH";
 if(!full || S.t<200 || S.t>=850) {D.stage.dataset.particles="0";return;}
 const path=pathPoints(box);if(!path)return;
 const t=S.t;
 const head=progress(t,200,570),fade=1-progress(t,630,850);
 const intensity=Math.max(0,fade);
 const pos=cubicPoint(ease(head),path.a,path.b,path.c,path.d);
 const tailStart=Math.max(0,head-.40);
 ctx.save();ctx.globalCompositeOperation="screen";
 if(head>0 && head<1){
    for(let i=0;i<18;i++){
      const u=lerp(tailStart,head,i/17),p=cubicPoint(ease(u),path.a,path.b,path.c,path.d);
      blurredDot(p.x,p.y,lerp(11,27,i/17),.025+intensity*.09*(i/17));
    }
    blurredDot(pos.x,pos.y,33,intensity*.52);
    blurredDot(pos.x,pos.y,11,intensity*.66);
    blurredDot(pos.x,pos.y,3,intensity*.96);
 }
 const max=S.low?6:18;
 for(let i=0;i<max;i++){
   const stagger=i/max;
   const advance=clamp(head+(stagger-.5)*.35);
   const p=cubicPoint(ease(advance),path.a,path.b,path.c,path.d);
   const wobble=Math.sin((i+1)*14.17+head*8)*lerp(9,2,head);
   const spread=Math.cos(i*4.81+head*5)*lerp(16,4,head);
   const o=Math.max(.09,Math.sin(Math.PI*progress(t,200,850)))*(.34+.6*(i%5)/5)*intensity;
   blurredDot(p.x+wobble,p.y+spread,lerp(3,7,(i%4)/4),o*.55);
   ctx.fillStyle="rgba(255,234,183,"+o.toFixed(3)+")";
   ctx.beginPath();ctx.arc(p.x+wobble,p.y+spread,lerp(.6,1.45,(i%3)/3),0,Math.PI*2);ctx.fill();
 }
 S.particleCount=max;
 ctx.restore();
 D.stage.dataset.particles=String(max);
}
function applyText(t,isFallback){
 if(isFallback){
   D.cnSpecies.style.opacity="1";D.cnSpecies.style.filter="blur(0px)";
   D.laSpecies.style.opacity="1";D.formationGlyphs.style.opacity="0";D.titleSweep.style.opacity="0";
   D.stage.dataset.nameReadable="true";return;
 }
 const form=progress(t,380,550),reveal=progress(t,550,680);
 D.formationGlyphs.style.opacity=String((1-reveal)*Math.sin(Math.PI*form)*.85);
 D.formationGlyphs.style.transform="translateX("+lerp(0,22,form).toFixed(1)+"px)";
 D.cnSpecies.style.opacity=String(reveal);
 D.cnSpecies.style.filter="blur("+lerp(5,0,ease(reveal)).toFixed(2)+"px)";
 D.laSpecies.style.opacity=String(progress(t,580,680));
 D.titleSweep.style.opacity=String(Math.sin(Math.PI*reveal)*.75);
 D.titleSweep.style.transform="translateX("+lerp(-55,210,reveal).toFixed(1)+"px)";
 D.stage.dataset.nameReadable=reveal>=1?"true":"false";
}
function render(ms){
 const t=clamp(Number(ms)||0,0,TOTAL);S.t=t;
 const state=S.classification,high=state==="RESULT_HIGH";
 const fallback=S.reduced || !S.photoTrusted || !S.contourValid || S.invalid || !high;
 const p=S.reduced?1:ease(progress(t,0,200)),box=fieldRectAt(S.reduced?200:t);
 Object.assign(D.photoBox.style,{left:box.x+"px",top:box.y+"px",width:box.w+"px",height:box.h+"px",borderRadius:lerp(0,18,p)+"px"});
 D.resultHead.style.opacity=String(p);
 D.processingCaption.style.opacity=String(S.reduced?0:1-progress(t,0,200));
 D.edgeField.style.opacity=String(fallback?0:1-progress(t,0,220));
 const outline=fallback?0:1-progress(t,50,400);
 D.haloOuter.style.opacity=String(outline*.55);
 D.haloInner.style.opacity=String(outline*.73);
 D.haloCore.style.opacity=String(outline*.94);
 D.haloComet.style.opacity=String(outline*.88);
 D.haloComet.style.strokeDashoffset=String(-1200*progress(t,0,370));
 const body=S.reduced?1:progress(t,100,215);
 D.resultBody.style.opacity=String(body);
 D.resultActions.style.opacity=String(S.reduced?1:progress(t,80,200));
 D.resultMeta.style.opacity=String(S.reduced?1:progress(t,180,380));
 D.storyPlaceholder.style.opacity=String(S.reduced?1:progress(t,200,430));
 const chip=D.resultBody.querySelector(".result-chip"); chip.style.opacity=String(S.reduced?1:progress(t,130,300));
 D.cnSpecies.textContent=high?"翘嘴鲌":(state==="RESULT_MEDIUM"?"鱼种待确认":state==="RESULT_LOW"?"无法确认鱼种":"未检测到鱼");
 D.laSpecies.textContent=high?"Culter alburnus":"";
 applyText(t,fallback);
 renderEffects(box);
 const index=phases(t);
 D.frameCode.textContent=FRAME[index][0]+" · "+FRAME[index][1];
 D.frameTime.textContent=String(Math.round(t)).padStart(3,"0")+" / 850 ms";
 D.timeline.value=String(Math.round(t)); D.stage.dataset.phase=phase();
 D.stage.dataset.reduceMotion=String(S.reduced);D.stage.dataset.fallback=String(fallback);
 D.stage.dataset.ctaInteractive=String(D.resultActions.style.pointerEvents!=="none");
 D.steps.querySelectorAll("[data-seek]").forEach((b,i)=>b.classList.toggle("active",i===index));
}
function stop(){cancelAnimationFrame(S.raf);S.playing=false;D.playBtn.textContent="▶ 播放";}
function tick(now){
 if(!S.playing)return;
 render(Math.min(TOTAL,now-S.origin));
 if(S.t>=TOTAL){stop();S.completed=true;D.stage.dataset.ended="true";return;}
 S.raf=requestAnimationFrame(tick);
}
function play({manual=false}={}){
 if(!S.ready)return;
 if(!manual && S.started)return;
 if(!manual)S.started=true;
 if(S.t>=TOTAL)render(0);
 stop();S.playing=true;S.origin=performance.now()-S.t;
 D.stage.dataset.ended="false";
 D.playBtn.textContent="▶ 播放中"; S.raf=requestAnimationFrame(tick);
}
function toast(text){D.toast.textContent=text;D.toast.style.opacity="1";clearTimeout(toast.timer);toast.timer=setTimeout(()=>D.toast.style.opacity="0",1800);}
function applyMode(){
 S.reduced=D.reduceMotion.checked;S.low=D.lowQuality.checked;S.invalid=D.invalidGeometry.checked;S.classification=D.recognitionState.value;
 if(S.reduced)stop();
 render(S.t);
}
function setReady(valid){
 S.ready=true;fitPhone();render(0);
 D.runtimeResult.textContent=valid?"照片 HASH 验证成功 · 浏览器演示可运行（非 Android）":"照片验收失败 · 仅允许运行无光源回退";
 if(valid)play();
}
async function load(){
 try{
 const data=await fetch("./fixture.json",{cache:"no-store"}); if(!data.ok)throw Error("fixture HTTP "+data.status);
 S.fixture=await data.json();
 if(S.fixture.photo.sha256!==TARGET_SHA || S.fixture.photo.width!==1152 || S.fixture.photo.height!==1536 || S.fixture.manual_contour.is_production_mask!==false)throw Error("Fixture authority mismatch");
 const contour=S.fixture.manual_contour.path_d;
 for(const id of ["haloOuter","haloInner","haloCore","haloComet"])D[id].setAttribute("d",contour);
 S.contourValid=S.fixture.manual_contour.source==="MANUAL_REFERENCE_CONTOUR" && contour.length>500;
 const response=await fetch("../../system/fish_media_display_v1/assets/real_catch_source.jpg",{cache:"no-store"});
 if(!response.ok)throw Error("photo HTTP "+response.status);
 const buf=await response.arrayBuffer(),bytes=new Uint8Array(buf);
 const digest=Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",bytes))).map(x=>x.toString(16).padStart(2,"0")).join("");
 if(digest!==TARGET_SHA || bytes.length!==S.fixture.photo.bytes)throw Error("Photo SHA-256 / bytes mismatch");
 const url=URL.createObjectURL(new Blob([buf],{type:"image/jpeg"}));
 D.fishPhoto.src=url;await D.fishPhoto.decode();
 if(D.fishPhoto.naturalWidth!==1152 || D.fishPhoto.naturalHeight!==1536)throw Error("Photo dimensions mismatch");
 S.photoTrusted=true;
 D.photoState.textContent="PHOTO VERIFIED · 1152×1536";D.photoState.className="source-state ok";
 D.photoSha.textContent=digest;setReady(true);
 }catch(e){
 console.error("RR09 photo validation:",e);
 D.photoState.textContent="PHOTO UNVERIFIED · "+String(e.message);D.photoState.className="source-state fail";
 D.photoSha.textContent="HASH UNVERIFIED; no invented light source";
 S.photoTrusted=false;setReady(false);
 }
}
D.playBtn.addEventListener("click",()=>play({manual:true}));
D.restartBtn.addEventListener("click",()=>{stop();render(0);play({manual:true})});
D.pauseBtn.addEventListener("click",stop);
D.timeline.addEventListener("input",e=>{stop();render(e.target.value)});
D.steps.querySelectorAll("button").forEach(b=>b.addEventListener("click",()=>{stop();render(b.dataset.seek)}));
["reduceMotion","lowQuality","invalidGeometry","recognitionState"].forEach(id=>D[id].addEventListener("change",applyMode));
D.continueBtn.addEventListener("click",()=>toast("CTA 可操作 · 评审模式不修改业务数据"));
D.saveBtn.addEventListener("click",()=>toast("保存按钮可操作 · 演示模式不写入记录"));
window.addEventListener("resize",()=>{fitPhone();render(S.t)});
const media=window.matchMedia("(prefers-reduced-motion: reduce)");
if(media.matches){D.reduceMotion.checked=true;S.reduced=true;}
window.__RR09={seek:t=>{stop();render(t);},play:()=>play({manual:true}),pause:stop,state:()=>({
 t:S.t,phase:phase(),ready:S.ready,photoTrusted:S.photoTrusted,contourProvenance:S.fixture?.manual_contour.source||null,
 particleCount:S.particleCount,classification:S.classification,fallback:D.stage.dataset.fallback,
 nameReadable:D.stage.dataset.nameReadable,completed:S.completed,autoplayStarted:S.started
}),getFrames:()=>FRAME.map(x=>x[0]),setMode:(id,value)=>{if(!D[id])throw Error("unknown mode");if(D[id].type==="checkbox")D[id].checked=Boolean(value);else D[id].value=value;applyMode();}};
load();
})();
