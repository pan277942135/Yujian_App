#!/usr/bin/env python3
import csv,hashlib,json
from pathlib import Path
from PIL import Image
R=Path(__file__).resolve().parent
REPO=R.parents[1]
rows=list(csv.DictReader((R/'stage_matrix/recognition_21_frame_matrix.csv').open()))
j=json.loads((R/'stage_matrix/recognition_21_frame_matrix.json').read_text())['frames']
ids=[r['Frame ID'] for r in rows]
expected=[f'{n:02d}' for n in range(1,11)]+[f'11.{i}' for i in range(1,4)]+[f'12.{i}' for i in range(1,4)]+[f'13.{i}' for i in range(1,6)]
assert ids==expected and len(j)==21 and len(set(ids))==21
assert len(set(r['Stage ID'] for r in rows))==13
assert [r['Runtime Stage ID'] for r in rows[10:13]]==['ORB_TO_NAME']*3
assert [r['Runtime Stage ID'] for r in rows[13:16]]==['SPECIES_NAME_REVEAL']*3
assert [r['Runtime Stage ID'] for r in rows[16:]]==['RESIDUE_SETTLE']*5
for r in rows:
 assert all(r.values()) or r['Frame ID'] in ('01','13.5')
 assert r['Entry Condition'] and r['Exit Event'] and r['Model Gate'] and r['Fallback']
for i in range(1,7):
 p=R/'visual_boards'/f'G0{i}.png'; assert p.exists() and min(Image.open(p).size)>800
assert (R/'visual_boards/six_group_overview.png').exists()
reg=json.loads((R/'contracts/frozen_parameter_registry.json').read_text())
assert all(x['status'] in ('FROZEN','PROPOSED','PENDING_PRODUCT_REVIEW','RUNTIME_TUNABLE') and x['source'] for x in reg['entries'])
assert {'R06_GEOMETRY','R06_PROFILE','R06_SVG'}=={x['id'] for x in reg['sourceConflicts']}
for x in reg['sourceConflicts']:
 assert hashlib.sha256((REPO/x['path']).read_bytes()).hexdigest()==x['currentMainSha256']
assets=json.loads((R/'provenance/assets_manifest.json').read_text())['assets']
for a in assets:
 if a.get('availability')=='TRACKED_MAIN':assert (REPO/a['path']).exists() and hashlib.sha256((REPO/a['path']).read_bytes()).hexdigest()==a['sha256']
assert len(assets)>40
lines=[]
for p in sorted(R.rglob('*')):
 if p.is_file() and p.name!='SHA256SUMS':lines.append(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(R)}')
(R/'SHA256SUMS').write_text('\n'.join(lines)+'\n')
print('PASS structural: 6 groups, 13 stages, 21 frames, source/registry checks')
print('BLOCKED visual freeze: R06 hash identity, real Mask, S3 morph and KF06–KF10 geometry remain open')
