#!/usr/bin/env python3
"""Preflight F01-F08 originals. SOURCE_READY never means Android runtime PASS."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
REGISTER = ROOT / 'design/pages/home/normal_home/engineering/hero_real_photo_fixture_register_v1.json'


def inspect_source(path):
    result = {'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    try:
        with Image.open(path) as image:
            image.load()  # Header-only dimensions do not prove decodability.
            result.update(encoded_dimensions_px=list(image.size),
                          oriented_dimensions_px=list(ImageOps.exif_transpose(image).size),
                          exif_orientation=image.getexif().get(274, 1), decode_status='PASS')
    except (OSError, ValueError) as error:
        result.update(decode_status='FAIL', decode_error=str(error))
    return result


def check_fixture(item, root):
    errors = []
    result = {'fixture_id': item['id'], 'case': item['test_case'], 'status': 'NOT_RUN'}
    relative = item.get('source_path')
    path = (root / relative).resolve() if relative else None
    if path is None or not path.is_relative_to(root.resolve()) or not path.is_file():
        errors.append('ORIGINAL_MISSING')
    else:
        observed = inspect_source(path)
        result['observed_source'] = observed
        if observed['decode_status'] != 'PASS':
            errors.append('ORIGINAL_DECODE_FAILED')
        if observed['source_sha256'] != item.get('source_sha256'):
            errors.append('SOURCE_SHA_MISMATCH')
        if observed.get('oriented_dimensions_px') != item.get('source_dimensions_px'):
            errors.append('ORIENTED_DIMENSIONS_MISMATCH')
        dims = observed.get('oriented_dimensions_px')
        if dims:
            ratio = dims[0] / dims[1]
            expected = {'F01': 4/3, 'F02': 16/9, 'F03': 3/4, 'F04': 9/16}.get(item['id'])
            # Exact integer cross-products; no invented aspect-ratio tolerance.
            if expected is not None and ratio != expected:
                errors.append('CASE_ASPECT_RATIO_MISMATCH')
            if item['id'] == 'F05' and ratio >= 0.5:
                errors.append('CASE_NOT_EXTREME_VERTICAL')
            bbox = item.get('fish_bbox_xyxy')
            if bbox is not None:
                if not (isinstance(bbox, list) and len(bbox) == 4 and all(isinstance(v, (int, float)) for v in bbox)
                        and 0 <= bbox[0] < bbox[2] <= dims[0] and 0 <= bbox[1] < bbox[3] <= dims[1]):
                    errors.append('BBOX_INVALID_IN_ORIENTED_SOURCE')
    if item.get('photo_provenance') not in ('VERIFIED', 'APPROVED') or not item.get('source_evidence'):
        errors.append('SOURCE_PROVENANCE_PENDING')
    if item.get('source_usage_rights') not in ('VERIFIED', 'APPROVED') or not item.get('usage_evidence'):
        errors.append('SOURCE_PERMISSION_PENDING')
    if item['id'] == 'F08':
        if item.get('bbox_ground_truth') != 'ABSENT':
            errors.append('F08_MUST_EXPLICITLY_EXERCISE_ABSENT_BBOX')
    elif item.get('bbox_ground_truth') not in ('VERIFIED', 'APPROVED') or not item.get('fish_region_inspection'):
        errors.append('FISH_REGION_INSPECTION_PENDING')
    if item['id'] == 'F07' and not item.get('derived_from_source'):
        errors.append('TECHNICAL_BORDER_DERIVATION_PENDING')
    result['blockers'] = errors
    result['source_status'] = 'SOURCE_READY' if not errors else 'FIXTURE_PROVENANCE_PENDING'
    result['runtime_status'] = 'NOT_RUN'
    return result


def audit(register, root=ROOT):
    cases = [check_fixture(item, root) for item in register['fixtures']]
    required = {f'F{i:02d}' for i in range(1, 9)}
    ids = [item['fixture_id'] for item in cases]
    matrix_errors = []
    if set(ids) != required or len(ids) != 8:
        matrix_errors.append('REQUIRED_F01_F08_MATRIX_INCOMPLETE_OR_DUPLICATED')
    originals = {}
    for case in cases:
        if case['fixture_id'] in ('F07', 'F08'):
            continue  # Contract explicitly permits derivative / absent-bbox reuse.
        digest = case.get('observed_source', {}).get('source_sha256')
        if digest:
            if digest in originals:
                case['blockers'].append('DUPLICATE_ORIGINAL_WITH_' + originals[digest])
                case['source_status'] = 'FIXTURE_PROVENANCE_PENDING'
            originals[digest] = case['fixture_id']
    candidates = []
    for folder in ('app/src/main/assets/home_normal/fish_record', 'app/src/androidTest/assets'):
        for path in sorted((root / folder).glob('*.jpg')):
            candidates.append({'path': str(path.relative_to(root)), **inspect_source(path)})
    return {'status': 'SOURCE_READY' if not matrix_errors and all(not c['blockers'] for c in cases) else 'BLOCKED',
            'runtime_status': 'NOT_RUN', 'matrix_errors': matrix_errors, 'cases': cases,
            'repository_candidates': candidates,
            'limitations': 'Byte-distinct does not establish independent photo provenance. Manual provenance and fish-region inspection remain mandatory. SOURCE_READY is preflight only; HOME+DETAIL device captures at the exact APK SHA are still required.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--register', type=Path, default=REGISTER)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = audit(json.loads(args.register.read_text()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'fixture_preflight': report['status'], 'runtime': 'NOT_RUN'}))
    return 0 if report['status'] == 'SOURCE_READY' else 2

if __name__ == '__main__':
    raise SystemExit(main())
