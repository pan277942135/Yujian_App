#!/usr/bin/env python3
"""Static consistency gate for the dependent Web Hi-Fi V2 design proposal."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'design/pages/recognition/web/contracts'
NAMES = ['Page_Structure', 'Visual_Tokens', 'Component_Geometry', 'Responsive_Rules', 'Page_State_Matrix', 'Authority_References', 'Acceptance_Matrix', 'Asset_Manifest', 'Work_B_Implementation_Mapping']

def load(path):
    return json.loads(path.read_text(encoding='utf-8'))

def check(condition, message):
    if not condition:
        raise AssertionError(message)

def main():
    docs = {name: load(BASE / f'{name}_V2.json') for name in NAMES}
    for name, document in docs.items():
        check(document['contract_id'] == 'YUJIAN_AI_CATCH_RECOGNITION_WEB_HIFI_V1', f'{name}: ID')
        check(document['authority_status'] == 'VISUAL_CONTRACT_DEFINED', f'{name}: authority')
        check(document['adoption_status'].startswith('DRAFT_'), f'{name}: draft dependence')
        check(document['page_id'] == 'ai_catch_recognition_web', f'{name}: page ID')
        check(bool(document['acceptance_criteria']), f'{name}: acceptance criteria')
    structure = docs['Page_Structure']
    check([s['id'] for s in structure['stages']] == ['upload', 'processing', 'result'], 'three stages')
    check(len(structure['five_step_strip']) == 5, 'five visible steps')
    ids = [s['component_id'] for s in structure['stages']] + [c for s in structure['stages'] for c in s['children']] + [s['component_id'] for s in structure['five_step_strip']]
    check(len(ids) == len(set(ids)), 'duplicate component IDs')
    states = docs['Page_State_Matrix']['states']
    check({s['state'] for s in states} == {'HIGH','MEDIUM','LOW','NO_FISH','IMAGE_QUALITY','TECHNICAL_FAILURE'}, '3+2/failure coverage')
    check(all(s['rr09'] == 'never' for s in states if s['state'] != 'HIGH'), 'HIGH-only reveal')
    times = docs['Authority_References']
    keyframes = load(ROOT / times['motion_contract_ref'])
    photo = load(ROOT / times['transition_contract_ref'])
    check((keyframes['processing']['frame_count'],keyframes['rr09']['frame_count'],keyframes['frame_count']) == (8,7,15), 'frame counts')
    check((keyframes['processing']['duration_ms'],keyframes['rr09']['duration_ms'],keyframes['total_nominal_duration_ms']) == (2800,850,3650), 'nominal duration')
    check(times['water_strength'] == [f['ambient_water_strength'] for f in keyframes['keyframes'][:8]], 'water intensities')
    check(times['gold_strength'] == [f['fish_gold_strength'] for f in keyframes['keyframes'][:8]], 'gold intensities')
    mid = next(f for f in keyframes['keyframes'] if f['id'] == 'R0.5')
    check((mid['relative_ms'],mid['global_nominal_ms']) == (100,2900), 'R0.5 time')
    for key, ref in [('processing_foreground',photo['processing_photo_content_rect']),('mid_container',photo['samples']['R0.5']['outer_rect']),('mid_foreground',photo['samples']['R0.5']['foreground_rect']),('result_hero',photo['result_hero_rect']),('result_foreground',photo['result_hero_photo_content_rect'])]:
        check(times['r05_geometry_360dp'][key] == [ref[k] for k in ['x','y','width','height']], f'R0.5 geometry {key}')
    widths = [320,360,390,430,480,768,1024,1280,1440]
    for width in widths:
        matches = [b for b in docs['Responsive_Rules']['breakpoints'] if b['min_width_px'] <= width and (b['max_width_px'] is None or width <= b['max_width_px'])]
        check(len(matches) == 1, f'{width} width coverage')
    for ref in [times[k] for k in ['motion_contract_ref','transition_contract_ref','optical_contract_ref','result_motion_ref','result_media_ref']]:
        check((ROOT / ref).is_file(), f'missing authority {ref}')
    assets = docs['Asset_Manifest']
    check(assets['approved_web_hifi']['sha256'] == '5f07c2f65ea33f14c8517dddbb7f8d6b7369a6ed980cba11cc92014f4e6a75f9' and assets['approved_web_hifi']['publication_permission'] == 'NOT_CONFIRMED' and assets['approved_web_hifi']['status'] == 'CANDIDATE_BYTES_VERIFIED_APPROVAL_NOT_TRACED', 'candidate bytes and publication gate')
    registry = load(ROOT / 'design/registry/experience_registry_v1.json')
    feature = next(f for f in registry['features'] if f['id']=='recognition_flow_v1_2')
    view = next(v for v in feature['hifi_views'] if v['id']=='web_hifi_contract_v2')
    check(view['authority'] == 'design/pages/recognition/web/WEB_HIFI_CONTRACT_V2.md', 'Design Manager authority')
    for link in view['contract_links']:
        check((ROOT / link['path']).is_file(), f'missing menu resource {link["path"]}')
    check(all((ROOT / f'design/pages/recognition/web/{name}').is_file() for name in ['WEB_HIFI_HANDOFF_A_V2.md','WEB_HIFI_GAP_MATRIX_V2.md']), 'handoff/gap')
    print('WEB_HIFI_CONTRACT_V2=PASS contract_files=9 stages=3 steps=5 states=6 frames=15 duration_ms=3650 responsive_widths=9')
    print('PIXEL_AUTHORITY=NOT_CONFIRMED MAIN_ADOPTED=NO RUNTIME_VISUAL_VERIFIED=NO')

if __name__ == '__main__':
    main()
