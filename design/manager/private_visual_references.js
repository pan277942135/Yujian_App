/* Only manifest/contracts are fetched. Selected image bytes never leave browser memory. */
(async () => {
  'use strict';
  const $ = id => document.getElementById(id);
  const base = '../../design/pages/recognition/web/';
  const response = await fetch(base + 'VISUAL_REFERENCE_MANIFEST_V1.json');
  if (!response.ok) throw new Error('Reference manifest unavailable');
  const manifest = await response.json();
  const frames = await (await fetch('../../' + manifest.current_frame_contract)).json();
  const esc = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let active = 'web-three-stage', generation = 0, screenshotGeneration = 0;
  const urls = new Set();
  function release(img) { const u = img.getAttribute('src'); if (u) {URL.revokeObjectURL(u); urls.delete(u);} img.removeAttribute('src'); img.hidden = true; }
  function clear() {generation++; screenshotGeneration++; release($('original')); release($('actual')); $('reference').value=''; $('implementation').value=''; $('region').hidden=true; $('check').textContent='尚未选择原图'; $('check').dataset.state='empty'; $('source-sha').value=''; $('viewport').value=''; provenance();}
  function provenance() { $('comparison-status').textContent='实现截图：NOT_VERIFIED · 用户声明 SHA：'+($('source-sha').value || '未提供')+' · 视口：'+($('viewport').value || '未提供')+' · 需独立绑定当前Web HEAD后才可验收'; }
  function select(id) {
    clear(); active=id;
    document.querySelectorAll('[data-tab]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tab===id)));
    $('local').hidden=id==='current'; $('current').hidden=id!=='current';
    const ref=manifest.references.find(r=>r.id===id);
    $('identity').textContent=ref ? `${ref.filename} · ${ref.width}×${ref.height} · ${ref.size_bytes} bytes · SHA-256 ${ref.sha256}` : '正式权威：SWOS 15 帧 V1.1（8 Processing + 7 RR09）';
    $('version').textContent=ref?.status==='14_FRAME_V0_3_HISTORICAL_VISUAL_REFERENCE' ? '14_FRAME_V0_3_HISTORICAL_VISUAL_REFERENCE · 用户确认的历史光学方向；不能覆盖V1.1新增R0.5。' : '用户确认的视觉参考；公开权限 NOT_CONFIRMED，运行像素一致性 NOT_VERIFIED。';
  }
  document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>select(b.dataset.tab));
  $('clear').onclick=clear;
  $('reference').onchange=async e=>{
    const token=++generation, file=e.target.files[0], ref=manifest.references.find(r=>r.id===active);
    release($('original')); $('region').hidden=true;
    if(!file || !ref)return;
    try {
      const bytes=await file.arrayBuffer();
      const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');
      if(token!==generation)return;
      if(hash!==ref.sha256 || file.size!==ref.size_bytes)throw new Error('SHA或字节数不匹配；拒绝用其他候选图片替代原图');
      const blob=new Blob([bytes],{type:'image/png'}), bitmap=await createImageBitmap(blob);
      const matches=bitmap.width===ref.width&&bitmap.height===ref.height; bitmap.close();
      if(!matches)throw new Error('原图尺寸不匹配');
      if(token!==generation)return;
      const url=URL.createObjectURL(blob);urls.add(url);$('original').src=url;$('original').hidden=false;
      $('check').textContent=`VERIFIED · SHA-256、尺寸和字节数一致 · ${ref.filename} · 仅本地内存`;$('check').dataset.state='verified';
    } catch(error){if(token!==generation)return;$('check').textContent=error.message;$('check').dataset.state='error';}
  };
  $('implementation').onchange=async e=>{
    const n=++screenshotGeneration,file=e.target.files[0];release($('actual'));if(!file)return;
    try {const bitmap=await createImageBitmap(file);bitmap.close();if(n!==screenshotGeneration)return;const u=URL.createObjectURL(file);urls.add(u);$('actual').src=u;$('actual').hidden=false;provenance();}catch{ $('comparison-status').textContent='截图无法解码 · NOT_VERIFIED'; }
  };
  $('source-sha').oninput=provenance;$('viewport').oninput=provenance;
  $('zoom').oninput=()=>{$('reference-stage').style.width=$('zoom').value+'%';$('actual').style.width=$('zoom').value+'%';};
  $('mappings').innerHTML=manifest.mappings.map((m,i)=>`<tr><td><button data-region="${i}">${esc(m.label)}</button></td><td>${esc(m.component_ids.join(', '))}</td><td>${esc(m.tokens.join(', '))}</td><td>${esc(m.geometry.join(', '))}</td><td>${esc(m.responsive.join(', '))}</td></tr>`).join('');
  document.querySelectorAll('[data-region]').forEach(b=>b.onclick=()=>{
    if(active!=='web-three-stage'||$('original').hidden){$('check').textContent='请先在Web三阶段页选择并核验原图，才能定位结构区域';return;}
    const [x,y,w,h]=manifest.mappings[Number(b.dataset.region)].region;
    Object.assign($('region').style,{left:x/1536*100+'%',top:y/1024*100+'%',width:w/1536*100+'%',height:h/1024*100+'%'});$('region').hidden=false;$('reference-stage').scrollIntoView({block:'nearest'});
  });
  $('contracts').innerHTML=['Page_Structure','Visual_Tokens','Component_Geometry','Responsive_Rules','Page_State_Matrix'].map(n=>`<a href="${base}contracts/${n}_V2.json" target="_blank" rel="noreferrer">${n} V2</a>`).join('');
  $('differences').innerHTML=manifest.differences.map(d=>`<li><strong>${esc(d.item)}</strong>：${esc(d.rule)}</li>`).join('');
  $('frames').innerHTML='<table><thead><tr><th>帧</th><th>相对时间ms</th><th>全局时间ms</th></tr></thead><tbody>'+frames.keyframes.map(f=>`<tr><td>${esc(f.id)}</td><td>${esc(f.relative_ms??f.global_nominal_ms)}</td><td>${esc(f.global_nominal_ms)}</td></tr>`).join('')+'</tbody></table>';
  window.addEventListener('pagehide',()=>{generation++;screenshotGeneration++;urls.forEach(u=>URL.revokeObjectURL(u));urls.clear();});
  const initial=new URLSearchParams(location.search).get('reference'); select(manifest.references.some(r=>r.id===initial)?initial:'web-three-stage');
})().catch(error=>{document.getElementById('check').textContent='查看器加载失败：'+error.message;});
