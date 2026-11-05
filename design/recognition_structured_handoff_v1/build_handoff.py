#!/usr/bin/env python3
"""Build source-backed review boards and semantic matrix; no Android source edits."""
from __future__ import annotations
import csv, hashlib, json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parents[1]
V3=REPO/'design/recognition_flow/hifi_freeze_v1'
KF=V3/'keyframes'
REF=Path('/workspace/scratch/6ba3d44942a1/references/水润识鱼界面演进展示板.png')
ORIG=V3/'provenance/originals'
FONT=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',25)
SMALL=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',18)

FRAMES=[
('01','PHOTO_STABLE','PHOTO_STABLE','Photo first frame presented','photo stable event','large card fixed','single full Core, S1 alpha 1','internal quiet; 1–3 subtle drops','off','none','off','Processing','photo first frame; local ≥120ms','photo identity ready','same attempt'),
('02','ORB_WAKE','ORB_WAKE','PHOTO_STABLE and Orb anchor ready','wake completion + anchor ready','same card','same Core S1→S2, no replacement','caustics/drops/grass wake 0–350ms','R06 alpha 0–450ms','none','off','Processing','local ≥350ms','models may run concurrently','same attempt'),
('03','ORB_RELEASE','ORB_RELEASE','S2 and rendered local release valid','local release event and R06 path ready','same card; perimeter off','S2 Core continuously breathing','active water, grass, 3 drops','five R06 paths extend then local return; 690–950ms C1','none','off','Processing','local ≥600ms','no detector requirement for local event','same attempt'),
('04','PERIMETER_ARRIVAL','PERIMETER_FORM','release reaches photo edge anchor','perimeter rendered; detector + photo transform ready','same card','Core remains','internal S2','photo perimeter only; no fish contour','none','off','Processing','local ≥125ms','Detector target and geometry','same attempt'),
('05','PATH_TO_FISH','FISH_SEEK','perimeter ready and Detector target valid','seek complete; Classifier + SlimSAM terminal + branch decision','same card, source transform fixed','Core remains','small dispersed drops','1–2 broken gold filaments perimeter→real fish-tail anchor','no full contour','off','Processing','local ≥125ms','Classifier and SlimSAM join','same attempt'),
('06','CONTOUR_A','CONTOUR_A','FULL branch and TRUSTED_MASK','local A completed','same card','Core remains','drops near path','perimeter decays as specified','true Mask path partial A','off','Processing','local ≥250ms','same trusted Mask identity','same attempt'),
('07','CONTOUR_B','CONTOUR_B','A complete and same trusted Mask','local B completed','same card','Core remains','varied droplets','perimeter→fish energy','same true path extends B, blue/gold','off','Processing','local ≥250ms','same trusted Mask','same attempt'),
('08','CONTOUR_C','CONTOUR_C','B complete and same trusted Mask','real contour closed','same card','Core remains','water droplets retained','perimeter falls away','true contour C closed','off','Processing','local ≥250ms','contour valid and closed','same attempt'),
('09','FISH_GOLD_PEAK','FISH_GOLD','closed trusted contour','Core/Soft/Halo event complete','same card','Core remains','internal active','perimeter zero by handoff','three-layer gold on real contour only','off','Processing','local ≥850ms; 1550ms nominal peak','Classifier ready; full branch','same attempt'),
('10','ORB_RETURN','ORB_RETURN','FULL gold complete or ORB_ONLY branch selected','Orb energy receive event + result geometry ready','same card','same Core absorbs feedback','drops/grass respond','reverse actual path; ORB_ONLY bypasses fake fish','FULL glow settles; ORB_ONLY none','glyph resource preparing','Processing','local ≥380ms','successful Classifier and SlimSAM terminal','same attempt'),
('11.1','ORB_TO_NAME','ORB_CORE_HOLD','Orb return received and real glyph available','core hold substate complete','same card','full round core, beginning of S3 proposal','rich water, grass, filament preparation','external energy closes','none','not readable','Processing','ORB_TO_NAME envelope ≥170ms; sub-allocation PROPOSED','Classifier name and glyph valid','same attempt'),
('11.2','ORB_TO_NAME','ORB_HORIZONTAL_STRETCH','11.1 complete','horizontal stretch substate complete','same card','same Core water elongates, not stretched bitmap','grass/caustics deform with water','R06 participates in shape','none','not readable','Processing','sub-allocation PROPOSED','same glyph and name','same attempt'),
('11.3','ORB_TO_NAME','LIQUID_BRUSH_TRANSITION','11.2 complete','liquid stroke transition complete','same card','Core liquid brush, no second Orb','water/drops remain continuous','filaments become strokes','none','not readable','Processing','sub-allocation PROPOSED','same glyph and name','same attempt'),
('12.1','SPECIES_NAME_REVEAL','STROKE_AGGREGATION','11.3 brush ready','initial strokes aggregate','same card','Core identity transfers to brush','cyan water + dark lake ink','filaments become partial strokes','none','not fully readable','Processing','NAME_REVEAL envelope ≥130ms; sub-allocation PROPOSED','Classifier name bound','same attempt'),
('12.2','SPECIES_NAME_REVEAL','GLYPH_EMERGENCE','12.1 complete and real glyph path available','partial glyph recognisable','same card','Orb structure in letter strokes','cyan + ink + sparse gold','path to actual glyph','none','partially readable true name','Processing','sub-allocation PROPOSED','real glyph path required','same attempt'),
('12.3','SPECIES_NAME_REVEAL','SPECIES_NAME_FINAL','12.2 complete','glyph reveal complete','same card','morph stabilises','water highlights settle','residual light','none','full Classifier name','Processing','sub-allocation PROPOSED','Classifier name validated','same attempt'),
('13.1','RESIDUE_SETTLE','RESULT_NAME_HOLD','name reveal complete','hold and residual handoff ready','large card unchanged','Orb-to-ink complete','residual lake light','glow holds','none','same full name','Processing title','RESIDUE_SETTLE envelope ≥170ms; sub-allocation PROPOSED','result geometry pending/ready','same attempt'),
('13.2','RESIDUE_SETTLE','PHOTO_REPOSITION','13.1 hold complete','same photo reaches result target','card scales/translates without source switch','no new Core','glow contracts','perimeter softens','none','full name persistent','Processing title','sub-allocation PROPOSED','result geometry ready','same attempt'),
('13.3','RESIDUE_SETTLE','RESULT_HANDOFF','photo target geometry reached','result title and initial content committed','result card target; same source','no new Core','glow transfers','no Processing perimeter','none','full name persistent','Result title; initial info','sub-allocation PROPOSED','result state committed once','same attempt'),
('13.4','RESIDUE_SETTLE','RESULT_INFO_ARRIVAL','13.3 committed','info/action entrances complete','same result card','none','ambient low','residual fades','none','full name persistent','fields and actions arrive','sub-allocation PROPOSED','field data or empty-state values','same attempt'),
('13.5','RESIDUE_SETTLE','RESULT_SCENE_STABLE','info/action entrance complete','stable Result scene','same final card','none','ambient stable','Processing light gone','none','full name persistent','interactive Result','sub-allocation PROPOSED','successful result model output','same attempt')]

DELTAS=[
 'first stable photo frame defines shared visual origin',
 'same Core wakes internally; caustics, grass, drops and R06 alpha rise',
 'same five R06 paths extend locally while photo perimeter remains dark',
 'energy arrives at photo edge; perimeter begins without fish illumination',
 '1–2 broken gold filaments leave perimeter toward real fish-tail anchor',
 'FULL only: first portion of same trusted fish contour appears',
 'same contour extends with alternating cyan and gold',
 'same actual outline closes, no rectangle substitution',
 'Core/Soft/Halo gold peaks along that contour, not whole photo',
 'FULL path reverses or ORB_ONLY joins; same Core receives energy',
 'same Core remains round and holds before horizontal motion',
 'internal water, grass and light stretch laterally inside same Core',
 'stretched water turns into liquid brush while full name remains unreadable',
 'liquid brush gathers into partial strokes, still not fully legible',
 'actual Classifier glyph becomes partly legible from prior strokes',
 'same glyph completes and stabilizes as true species name',
 'name and large card hold while residual glow remains',
 'same photo scales and translates toward Result geometry; title still Processing',
 'title switches to Result and first result content commits',
 'existing result fields/actions enter progressively, same components',
 'fields/actions become interactive and Processing glow disappears']

def make_matrix():
 out=ROOT/'stage_matrix';out.mkdir(parents=True,exist_ok=True)
 rows=[]
 for i,(fid,stage,name,entry,exit_,photo,orb,water,wl,fish,glyph,result,clock,gate,session) in enumerate(FRAMES):
  group=fid.split('.')[0]
  assets=['canonical photo preview; real original pending','AI Orb immutable Core / runtime WebP','R01B masks','R06 path/profile']
  if 4<=int(group)<=10:assets+=['WaterLight geometry','same-attempt SlimSAM Mask at runtime, not V3 polygon']
  if int(group)>=11:assets+=['Classifier name → live Glyph path; S3 morph pending']
  components=(['Photo','AIOrbCore','R01B','R06'] if i<=2 else ['Photo','WaterLight','AIOrbCore'])
  if 5<=i<=9:components+=['FishMask','FishContour','FishGold']
  if 10<=i<=15:components+=['OrbMorph','SpeciesNameReveal','GlyphRenderer']
  if i>=16:components+=['ResultHandoff','ResultScreen']
  row={'Stage ID':group,'Frame ID':fid,'Stage Name':name,'Runtime Stage ID':stage,'Previous Frame':FRAMES[i-1][0] if i else '', 'Next Frame':FRAMES[i+1][0] if i+1<len(FRAMES) else '', 'Entry Condition':entry,'Exit Event':exit_,'Visual Delta from Previous':DELTAS[i],'Visual Components':components,'Real Input':'current attempt photo + Detector target + Classifier name + terminal SlimSAM state as required; never fixed fish/name','Photo State':photo,'Orb State':orb,'Water State':water,'WaterLight':wl,'Fish Focus':fish,'Glyph':glyph,'Result':result,'SceneClock':clock,'Model Gate':gate,'Session':session+'; attemptId/imageId/photoContentVersion match','Manual Access':'diagnostic stage can be selected; no fabricated real content','Real Data Rendering':'only if current-attempt '+gate+'; otherwise show waiting/fallback','Auto Advance':'minimum visibility AND '+exit_+' AND required model/geometry gates','Assets':assets,'Parameter Status':'FROZEN rules; subframe timing PROPOSED' if '.' in fid else 'FROZEN rules, visual geometry PENDING_PRODUCT_REVIEW','Fallback':'ORB_ONLY skips 06–09 if Mask FAILED/UNTRUSTED; no fake contour' if int(group) in (5,6,7,8,9,10) else 'hold/wait or fail within same attempt; never substitute fixed glyph'}
  rows.append(row)
 with (out/'recognition_21_frame_matrix.csv').open('w',newline='') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
 (out/'recognition_21_frame_matrix.json').write_text(json.dumps({'authority':'design semantics projected onto 13 existing runtime enum stages; 11/12/13 subframes are not extra clocks','frames':rows},ensure_ascii=False,indent=2)+'\n')

def board(name,entries,note,width=1800):
 # Source images are evidence/reference, not newly approved frames.
 h=880;im=Image.new('RGB',(width,h),(234,247,248));d=ImageDraw.Draw(im)
 d.text((35,22),name,font=FONT,fill=(13,69,88));d.text((35,65),note,font=SMALL,fill=(122,71,42))
 x=35;gap=15;cell=(width-70-gap*(len(entries)-1))//len(entries)
 for label,path in entries:
  src=(path if isinstance(path,Image.Image) else Image.open(path)).convert('RGB');src.thumbnail((cell-12,675),Image.Resampling.LANCZOS)
  d.rounded_rectangle((x,108,x+cell,825),radius=17,fill=(252,255,254),outline=(145,194,201),width=2)
  im.paste(src,(x+(cell-src.width)//2,145+(675-src.height)//2))
  d.text((x+12,112),label,font=SMALL,fill=(12,81,105));x+=cell+gap
 out=ROOT/'visual_boards';out.mkdir(parents=True,exist_ok=True);im.save(out/(name.split(' ')[0]+'.png'))

def make_boards():
 names={p.stem.split('_')[0]:p for p in KF.glob('*.png') if p.stem[:2].isdigit()}
 full={p.stem:p for p in KF.glob('*.png')}
 def f(id):return next(p for stem,p in full.items() if stem.startswith(id.replace('.','_')+'_'))
 board('G01 PHOTO_STABLE',[('Historical 01',next(ORIG.glob('01 PHOTO*.png'))),('Unified-photo candidate 01',f('01'))], 'CANDIDATE 941×1672: photo x98 y150 w745 h1023; Orb center(470,1452), visible D224 (30.1% card width)',1400)
 board('G02 ORB_WAKE_RELEASE',[('02 S1 to S2',f('02')),('03 local R06',f('03')),('Frozen Orb state board',ORIG/'AI Orb三态视觉规范板.png')], 'CORE SAME / 5 R06 PATHS / 03 perimeter OFF / V3 candidate effects unapproved')
 board('G03 PERIMETER_FISH_SEEK',[('03 local release',f('03')),('04 photo perimeter',f('04')),('05 seek to fish',f('05'))], 'V3 REFERENCE ONLY: R06 / Perimeter / Seek are separate; real fish-tail anchor required')
 board('G04 FISH_FOCUS_GOLD_RETURN',[(x,f(x)) for x in ['06','07','08','09','10']], 'REFERENCE ONLY: V3 polygon NOT TRUSTED; FULL uses real Mask; ORB_ONLY skips 06–09',1900)
 glyph_labels=['11.1 Core hold','11.2 Same-core stretch','11.3 Liquid brush','12.1 STROKE GAP','12.2 Partial glyph','12.3 Live-name target']
 board('G05 ORB_TO_SPECIES_GLYPH',[(label,f(x)) for label,x in zip(glyph_labels,['11_1','11_2','11_3','12_1','12_2','12_3'])], 'REFERENCE ONLY: V3 11.3 / 12.1 repeat; transition unresolved; runtime Classifier glyph, never bitmap',2100)
 ref=Image.open(REF)
 board('G06 RESULT_HANDOFF',[(f'KF{i:02d}',ref.crop((round((i-6)*ref.width/5),0,round((i-5)*ref.width/5),ref.height))) for i in range(6,11)], 'KF06–KF10 composition ONLY: old fisher differs from confirmed blue-cap photo; NOT final frames',1900)
 tiles=Image.new('RGB',(1500,1620),(235,247,249))
 for i in range(6):
  t=Image.open(ROOT/'visual_boards'/f'G0{i+1}.png');t.thumbnail((730,520),Image.Resampling.LANCZOS)
  tiles.paste(t,((i%2)*750+10,(i//2)*540+10))
 tiles.save(ROOT/'visual_boards'/'six_group_overview.png')

def manifest():
 files=[]
 for prefix,p in [('V3 candidate',KF),('historical',ORIG),('Orb source',REPO/'design/system/components/ai_orb_core'),('R01B',REPO/'app/src/main/assets/orb_r01b_v1_1')]:
  choices=list(p.glob('*.png')) if prefix in ('V3 candidate','historical') else [q for q in p.rglob('*') if q.is_file() and (('R06_' in q.name) if prefix=='Orb source' else True)]
  for q in sorted(choices):
   files.append({'role':prefix,'path':str(q.relative_to(REPO)),'availability':'LOCAL_V3_PACKAGE_NOT_YET_TRACKED_IN_MAIN' if prefix in ('V3 candidate','historical') else 'TRACKED_MAIN','sha256':hashlib.sha256(q.read_bytes()).hexdigest(),'policy':'REFERENCE_ONLY' if prefix in ('V3 candidate','historical') else 'SOURCE_REUSE_NO_MUTATION'})
 files.append({'role':'KF06–KF10 composition','path':'Library reference / 水润识鱼界面演进展示板.png','sha256':hashlib.sha256(REF.read_bytes()).hexdigest(),'policy':'REFERENCE_ONLY'})
 out=ROOT/'provenance';out.mkdir(parents=True,exist_ok=True)
 (out/'assets_manifest.json').write_text(json.dumps({'status':'SOURCE_BACKED_WITH_EXPLICIT_VISUAL_GAPS','photoSourceQuality':'PENDING_SOURCE_ASSET','assets':files},ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':
 make_matrix();make_boards();manifest()
