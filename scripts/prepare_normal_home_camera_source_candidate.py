#!/usr/bin/env python3
"""Prepare a review-only camera derivative; never edits or registers frozen assets."""
import argparse
import hashlib
import json
from math import hypot
from pathlib import Path
from PIL import Image, ImageOps
from verify_normal_home_a4_runtime_evidence import raster_bounds, TARGETS

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/system/core_visual_v1/reference/normal_home_v1.png'
SOURCE_SHA = '6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377'
ROI = (435, 1586, 643, 1794)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_SHA
    source = Image.open(SOURCE).convert('RGBA')
    assert source.size == (1080, 1920)
    candidate = source.crop(ROI)  # Native pixels; no resampling, repainting or color conversion.
    rgba = []
    for y in range(208):
        for x in range(208):
            red, green, blue, _ = candidate.getpixel((x, y))
            radius = hypot(x - 104, y - 104)
            alpha = 255 if radius <= 101 else round(255 * max(0, min(1, (104 - radius) / 3)))
            rgba.append((red, green, blue, alpha) if alpha else (0, 0, 0, 0))
    candidate.putdata(rgba)
    args.output.mkdir(parents=True, exist_ok=True)
    output = args.output / 'normal_home_camera_source_candidate.png'
    candidate.save(output)
    targets = json.loads(TARGETS.read_text())
    background = ImageOps.fit(Image.open(ROOT / 'app/src/main/assets/normal_home_runtime_v1/static/scene_base.png').convert('RGBA'), (1080, 1920))
    background.alpha_composite(candidate, (435, 1587))
    masks = {key: value for key, value in raster_bounds(background, targets).items() if key.startswith('CAMERA')}
    assert all(item['status'] == 'PASS' for item in masks.values())
    preview = Image.new('RGB', (624, 208), (80, 100, 110))
    preview.paste(source.crop(ROI), (0, 0))
    old = Image.open(ROOT / 'app/src/main/assets/normal_home_runtime_v1/camera/camera_button_base.png').convert('RGBA')
    preview.paste(old, (208, 0), old)
    preview.paste(candidate, (416, 0), candidate)
    preview.save(args.output / 'source_registered_candidate_comparison.png')
    report = {
        'status': 'REVIEW_REQUIRED', 'runtime_status': 'NOT_RUN',
        'source_path': str(SOURCE.relative_to(ROOT)), 'source_sha256': SOURCE_SHA,
        'source_roi_exclusive_px': list(ROI), 'candidate_dimensions_px': [208, 208],
        'candidate_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
        'rgb_policy': 'Original source RGB retained at every nontransparent pixel; only outer circular alpha matte added.',
        'alpha_policy': 'center=(104,104), opaque radius<=101; linear 3px outer feather to radius104; outside transparent.',
        'offline_composite_only': {'status': 'STATIC_PASS', 'placement_px': [435, 1587], 'masks': masks},
        'comparison_order': ['Frozen source ROI', 'existing registered raster', 'review candidate'],
        'registration': 'NOT_REGISTERED; NOT_FROZEN; NOT_BOUND_TO_ANDROID',
        'existing_frozen_assets_changed': False, 'pixel_targets_or_tolerances_changed': False,
        'remaining': 'Requires asset governance resolution and real Android rendering review before production binding. Offline composition is not device PASS.'
    }
    (args.output / 'candidate_manifest.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'candidate_status': report['status'], 'offline_mask_check': 'STATIC_PASS', 'runtime': 'NOT_RUN'}))

if __name__ == '__main__':
    main()
