#!/usr/bin/env python3
"""Verify acceptance V1 raster identity and child-only Design Manager registration."""
import hashlib,json,struct
from pathlib import Path
R=Path(__file__).resolve().parents[1]
ASSETS={
'hero':('design/system/core_visual_v1/components/assets/YuJianCatchHeroCard_Media_Edge_States_V1_Frozen.png','design/system/core_visual_v1/components/assets/YuJianCatchHeroCard_Media_Edge_States_V1_Frozen.manifest.json','design/system/core_visual_v1/components/YuJianCatchHeroCard_Media_Edge_States_V1.md'),
'generation':('design/pages/fish_record/detail/frozen/generation/FishRecordDetail_Asset_Generation_States_V1_Frozen.png','design/pages/fish_record/detail/frozen/generation/manifest.json','design/pages/fish_record/detail/FishRecordDetail_Asset_Generation_Spec_V1.md'),
'populated':('design/pages/fish_record/detail/frozen/states/FishRecordDetail_State_Populated_Memory_V1_Frozen.png','design/pages/fish_record/detail/frozen/states/populated_media_manifest.json','design/pages/fish_record/detail/FishRecordDetail_Populated_Media_Spec_V1.md'),
'capture':('design/system/core_visual_v1/components/assets/Fish_Memory_Capture_V1_Frozen.png','design/system/core_visual_v1/components/assets/Fish_Memory_Capture_V1_Frozen.manifest.json','design/system/core_visual_v1/components/Fish_Memory_Capture_V1.md')}
def load(p): return json.loads((R/p).read_text(encoding='utf-8'))
def fail(s): raise SystemExit('FAIL: '+s)
exp=load('design/registry/experience_registry_v1.json'); shared=load('design/registry/shared_design_system_v1.json'); f=next(x for x in exp['features'] if x['id']=='fish_record_detail_v2'); roots=[x['id'] for x in f['hifi_views']]
if roots!=['overview','a_side','b_side','asset_generation','editing','page_states']: fail(f'00–05 hierarchy changed: {roots}')
v={x['id']:x for x in f['hifi_views']}; g=v['asset_generation']
if g.get('status')!='FROZEN' or g.get('visual_status')!='FROZEN' or not any(x.get('id')=='asset_generation_states_visual' for x in g.get('children',[])): fail('03 has no Frozen visual child')
if not any(x.get('id')=='populated_memory_media' for x in v['b_side'].get('children',[])): fail('populated media is not a child of 02')
s={x['id']:x for x in shared['items']}
for id in ('yu_jian_catch_hero_card_v1','fish_memory_capture_v1'):
 if id not in s: fail('missing shared system '+id)
for key,(path,mp,spec) in ASSETS.items():
 p=R/path; b=p.read_bytes()
 if not b.startswith(b'\x89PNG\r\n\x1a\n'): fail(path+' is not PNG')
 w,h=struct.unpack('>II',b[16:24]); actual={'path':path,'dimensions_px':{'width':w,'height':h},'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()}; m=load(mp)
 for field,value in actual.items():
  if m.get(field)!=value: fail(f'{key} manifest mismatch: {field}')
 if m.get('status')!='FROZEN' or m.get('authority_spec')!=spec: fail(key+' manifest status/spec mismatch')
 if not (R/spec).is_file(): fail('missing spec '+spec)
 if key in ('hero','capture'):
  item=s['yu_jian_catch_hero_card_v1' if key=='hero' else 'fish_memory_capture_v1']; data=item['visual_authority']
  if any(data.get(k)!=actual[k] for k in actual): fail(key+' shared registry identity mismatch')
 else:
  parent=g if key=='generation' else v['b_side']; child=next(x for x in parent['children'] if x['id']==('asset_generation_states_visual' if key=='generation' else 'populated_memory_media'))
  fields={'path':'visual_authority','dimensions_px':'visual_authority_dimensions','bytes':'visual_authority_bytes','sha256':'visual_authority_sha256'}
  if any(child.get(vv)!=actual[k] for k,vv in fields.items()): fail(key+' page child identity mismatch')
 print(f"PASS {key}: {w}x{h} {len(b)} bytes sha256={actual['sha256']}")
print('PASS FishRecordDetail root menu is 00–05; new visual states are child/shared authorities')
