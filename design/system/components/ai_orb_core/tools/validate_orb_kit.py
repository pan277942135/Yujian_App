#!/usr/bin/env python3
"""Local validator for YuJian Orb S1/S2 resource candidate. No Android build."""
from pathlib import Path
import json,hashlib,math,xml.etree.ElementTree as ET
from PIL import Image
ROOT=Path(__file__).resolve().parent.parent
PASS=[];FAIL=[];WARN=[]
def check(cond,msg): (PASS if cond else FAIL).append(msg)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
manifest=json.loads((ROOT/'manifest_v1_2.json').read_text())
expected={"透明湖水玻璃球.png":"2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee","236493.png":"adefad98ec7cfe32df1316a605ac7f3ded8a5a7b33e107f2380ecbf49f2f7c35","翡翠星光中的流光水晶球.png":"910d15313b39ee2afa277356c46290e2cb7739b80a60e9ab4efaa14a29d74122"}
for name,token in expected.items():
 p=ROOT/'assets/source'/name;check(p.exists() and sha(p)==token,'original unchanged: '+name)
for f in ROOT.rglob('*'):
 if f.suffix.lower()=='.json':
  try:json.loads(f.read_text())
  except Exception as e:FAIL.append(f'JSON parse {f}: {e}')
 if f.suffix.lower()=='.svg':
  try:
   x=ET.parse(f); ps=x.findall('.//{http://www.w3.org/2000/svg}path');check(len(ps)>0,'SVG vector paths: '+f.name)
   for p in ps:check(p.attrib.get('d','').count('C')>=1,'SVG cubic path: '+p.attrib.get('id','unnamed'))
  except Exception as e:FAIL.append(f'SVG parsing {f}: {e}')
 if f.suffix.lower() in ('.png','.webp'):
  try:
   img=Image.open(f);img.verify();check(img.width>0 and img.height>0,'image dimensions: '+f.name)
  except Exception as e:FAIL.append(f'Invalid raster {f}: {e}')
check(len(manifest['assets'])>=30,'manifest asset quantity')
seen_ids=set();hashes={}
for a in manifest['assets']:
 ident=a['id'];check(ident not in seen_ids,'ID unique '+ident);seen_ids.add(ident)
 if a.get('path') and a['path'].startswith(('assets/','boards/','audits/','profiles/','tools/')):
  f=ROOT/a['path'];check(f.exists(),'manifest path '+a['path'])
  if f.exists() and a.get('sha256'):check(sha(f)==a['sha256'],'manifest hash '+a['path'])
  if a.get('sha256'):hashes.setdefault(a['sha256'],[]).append(a['path'])
 check(a.get('status') not in ['DEPRECATED_DO_NOT_INTEGRATE','MISSING_SOURCE'] or not a.get('runtime_ready',False),'banned not ready '+ident)
for hh,locs in hashes.items():check(len(locs)==1,'duplicate content files '+str(locs))
geo=json.loads((ROOT/'assets/paths/R06_orb_filament_path_geometry.json').read_text());check(len(geo['paths'])==5,'exact five path geometries')
ids={g['id'] for g in geo['paths']};check(ids=={'G01','G02','A01','A02','C01'},'five unique path IDs')
for p in geo['paths']:
 check(len(p['segments'])>=2,'2 cubic segments '+p['id'])
 samples=[tuple(p['start'])];segment_start=samples[0]
 for seg in p['segments']:
  c1,c2,e=seg['c1'],seg['c2'],seg['end']
  for j in range(1,129):
   u=j/128;v=1-u
   samples.append((v**3*segment_start[0]+3*v*v*u*c1[0]+3*v*u*u*c2[0]+u**3*e[0],v**3*segment_start[1]+3*v*v*u*c1[1]+3*v*u*u*c2[1]+u**3*e[1]))
  segment_start=tuple(e)
 radius=max(math.hypot(x,y) for x,y in samples)
 expected_r=radius+p['width_dp_min_max'][1]/2/80+(.004 if p['type']=='gold' else .007)
 check(expected_r<=.6751,f'14dp max at 80dp including stroke+warp: {p["id"]} effectiveR={expected_r:.5f}')
 check(p['arc_length_D']>0,f'path arc length positive {p["id"]}')
for name in ('R04_S1_idle_visual.png','R04_S2_active_visual.png','R04_S1_S2_comparison.png','R06_S2_motion_frames.png','R07_68dp_80dp_background_matrix.png'):
 check((ROOT/'boards'/name).exists(),'mandatory visual board '+name)
check(not any('R01_grass_' in str(f) for f in (ROOT/'assets/paths').iterdir() if f.is_file()),'old R01 plant paths not integrated')
check(not any('glyph' in str(f).lower() or 's3_' in str(f).lower() for f in (ROOT/'boards').iterdir() if f.is_file()),'S3 visual excluded')
WARN.append('Main runtime WebP raw bytes not in package: alpha and real runtime comparison remain unverified.')
WARN.append('Visual boards are source-derived static design samples, not Android run-time acceptance.')
report={'task_id':manifest['task_id'],'status':'PASS_WITH_SOURCE_LIMITATIONS' if not FAIL else 'FAIL','passed_checks':len(PASS),'failed_checks':len(FAIL),'warnings':WARN,'failed':FAIL,'sample_passed':PASS[:18]}
(ROOT/'audits/ORB_AUTOMATED_VALIDATION_REPORT.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
(ROOT/'audits/ORB_AUTOMATED_VALIDATION_REPORT.md').write_text(f'# Orb kit automated validation\n\nStatus **{report["status"]}** · passed {len(PASS)}, failed {len(FAIL)}.\n\n## Warnings\n'+''.join('- '+s+'\n' for s in WARN)+'\n## Failures\n'+''.join('- '+s+'\n' for s in FAIL))
print(json.dumps(report,ensure_ascii=False,indent=2));raise SystemExit(1 if FAIL else 0)
