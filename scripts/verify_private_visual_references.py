#!/usr/bin/env python3
"""Registration and publication exclusion gate; never promotes runtime visual status."""
import hashlib
import json
import subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MANIFEST='design/pages/recognition/web/VISUAL_REFERENCE_MANIFEST_V1.json'
REQUIRED=['user_reference_registered','original_sha_verified','compliant_viewer_opened','components_located','machine_structure_reviewed','versions_distinguished','unlicensed_images_excluded']
def validate_frozen(view):
    if 'VISUAL_FROZEN' not in [view.get('status'),view.get('design_status')]: return
    assert view.get('visual_reference_manifest'), 'VISUAL_FROZEN missing visual_reference_manifest'
    assert (ROOT/view['visual_reference_manifest']).is_file(), 'missing visual reference manifest'
    e=view.get('visual_closure_evidence',{})
    assert len(e.get('source_sha',''))==40, 'missing exact source SHA'
    assert all(e.get('checks',{}).get(k) is True for k in REQUIRED), 'incomplete seven visual checks'
    assert e.get('browser_report') and (ROOT/e['browser_report']).is_file(), 'browser evidence missing'
    report=json.loads((ROOT/e['browser_report']).read_text())
    assert report.get('source_sha')==e['source_sha'] and report.get('result')=='PASS', 'browser SHA/result mismatch'
    assert report.get('viewports') and report.get('reference_sha256'), 'browser report missing exercised viewports/reference SHA'
    assert all(report.get('checks',{}).get(k) is True for k in REQUIRED), 'browser report incomplete seven checks'
def main():
    m=json.loads((ROOT/MANIFEST).read_text());refs=m['references']
    assert [r['sha256'] for r in refs]==['27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2','ea0cf2e5fa84aad64195487cbedaab07b1727e96e87c9b4e661dc3d0fa252c1c']
    assert [(r['width'],r['height']) for r in refs]==[(1536,1024),(1672,941)]
    assert all(r['public_permission']=='NOT_CONFIRMED' and r['repository_included'] is False for r in refs)
    baseline=m.get('pixel_baseline',{})
    assert m.get('pixel_frozen_source')=='FROZEN_PRIVATE_SHA_BOUND', 'private pixel baseline must be explicitly frozen by SHA'
    assert baseline.get('baseline_id')=='YUJIAN_SWOS_WEB_PIXEL_BASELINE_V1' and baseline.get('status')=='FROZEN_PRIVATE_SHA_BOUND', 'pixel baseline identity/status'
    assert baseline.get('reference_id')=='web-three-stage' and baseline.get('sha256')==refs[0]['sha256'], 'pixel baseline must bind the approved Web reference SHA'
    assert baseline.get('dimensions_px')==[1536,1024] and baseline.get('size_bytes')==1804497, 'pixel baseline dimensions/bytes'
    assert baseline.get('source_bytes_reverified_this_execution') is False, 'do not claim original bytes were reverified in this execution'
    assert m.get('public_publication_allowed') is False and m.get('public_repo_commit_allowed') is False and m.get('public_ci_artifact_allowed') is False and m.get('public_pages_upload_allowed') is False, 'private baseline must remain excluded from public destinations'
    paths=subprocess.check_output(['git','ls-files','-z'],cwd=ROOT).decode().split('\0')
    private_names={r['filename'] for r in refs}|{'YuJian_Web_SWOS_Visual_References_Private_Handoff.zip'}
    private_hashes={r['sha256'] for r in refs}
    for name in filter(None,paths):
        assert Path(name).name not in private_names, 'private original/ZIP tracked'
        if Path(name).suffix.lower() in {'.png','.jpg','.jpeg','.webp','.zip'} and (ROOT/name).is_file():
            assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest() not in private_hashes, 'renamed private original tracked'
    registry=json.loads((ROOT/'design/registry/experience_registry_v1.json').read_text())
    for f in registry['features']:
        for v in f.get('hifi_views',[]): validate_frozen(v)
    feature=next(f for f in registry['features'] if f['id']=='recognition_flow_v1_2')
    for id in ['web_hifi_contract_v2','swos_static_visual_v1_1']:
        assert next(v for v in feature['hifi_views'] if v['id']==id)['visual_reference_manifest']==MANIFEST
    assert len(m['mappings'])==5 and all(x['component_ids'] and x['tokens'] and x['geometry'] and x['responsive'] for x in m['mappings'])
    print('PRIVATE_VISUAL_REGISTRATION=PASS references=2 mapped_regions=5 PIXEL_BASELINE=FROZEN_PRIVATE_SHA_BOUND PUBLIC_PERMISSION=NOT_CONFIRMED')
    print('BROWSER_AND_STRUCTURAL_VISUAL_REVIEW=SEPARATE_REQUIRED_EVIDENCE RUNTIME_VISUAL_VERIFIED=NO')
if __name__=='__main__':main()
