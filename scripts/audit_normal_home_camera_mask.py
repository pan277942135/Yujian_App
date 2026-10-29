#!/usr/bin/env python3
"""Explain strict camera mask failures without changing the frozen gate."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('a4', ROOT / 'scripts/verify_normal_home_a4_runtime_evidence.py')
a4 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(a4)


def components(image, roi):
    rgb = image.convert('RGB')
    x0, y0, x1, y1 = roi
    remaining = {(x, y) for y in range(y0, y1) for x in range(x0, x1)
                 if min(rgb.getpixel((x, y))) >= 210
                 and max(rgb.getpixel((x, y))) - min(rgb.getpixel((x, y))) <= 46}
    output = []
    while remaining:
        queue = [remaining.pop()]
        region = []
        while queue:
            x, y = queue.pop()
            region.append((x, y))
            for neighbor in ((x-1, y), (x+1, y), (x, y-1), (x, y+1)):
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    queue.append(neighbor)
        output.append({'pixels': len(region), 'bbox_inclusive_px': [
            min(x for x, y in region), min(y for x, y in region),
            max(x for x, y in region), max(y for x, y in region)]})
    return sorted(output, key=lambda item: (-item['pixels'], item['bbox_inclusive_px']))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screenshot', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    frozen = ROOT / 'design/system/core_visual_v1/reference/normal_home_v1.png'
    targets = json.loads((a4.TARGETS).read_text())
    expected_sha = '6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377'
    assert hashlib.sha256(frozen.read_bytes()).hexdigest() == expected_sha
    source, runtime = Image.open(frozen), Image.open(args.screenshot)
    assert source.size == runtime.size == (1080, 1920), 'Native reference profile required; never resize evidence'
    strict = a4.raster_bounds(runtime, targets)
    roi = (400, 1580, 680, 1785)
    source_components, runtime_components = components(source, roi), components(runtime, roi)
    report = {
        'status': strict['CAMERA_CORE_NEAR_WHITE']['status'],
        'scope': 'OPTICAL_MASK_ONLY; touch/layout bounds remain separate runtime evidence',
        'source_sha256': expected_sha,
        'screenshot_sha256': hashlib.sha256(args.screenshot.read_bytes()).hexdigest(),
        'strict_frozen_masks': strict,
        'diagnostics_only_not_alternative_gate': {
            'connectivity': 4,
            'source_components': source_components,
            'runtime_components': runtime_components,
            'largest_component_edge_delta_px': [runtime_components[0]['bbox_inclusive_px'][i] - source_components[0]['bbox_inclusive_px'][i] for i in range(4)],
            'interpretation': 'The full threshold includes disconnected rim highlights as well as the white core. Largest-component agreement does not override the full frozen mask.'
        },
        'threshold_or_target_changes': False,
        'next_action': 'Inspect failed pixels against the registered camera raster and runtime motion state; retain FAIL until exact-mask parity is demonstrated.'
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'camera_mask_status': report['status'], 'output': str(args.output)}))
    return 0 if report['status'] == 'PASS' else 1

if __name__ == '__main__':
    raise SystemExit(main())
