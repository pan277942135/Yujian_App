#!/usr/bin/env python3
"""Validator unit tests only; generated images are never acceptance fixtures."""
import tempfile
import unittest
from pathlib import Path
from PIL import Image
from verify_normal_home_hero_fixtures import audit, check_fixture, inspect_source

class FixturePreflightTest(unittest.TestCase):
    def test_corrupt_jpeg_is_rejected_even_when_hash_matches(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'broken.jpg'
            path.write_bytes(b'not a JPEG')
            observed = inspect_source(path)
            case = check_fixture({'id':'F03','test_case':'portrait_3_4','source_path':'broken.jpg',
                                  'source_sha256':observed['source_sha256']}, root)
            self.assertIn('ORIGINAL_DECODE_FAILED', case['blockers'])
            self.assertEqual('NOT_RUN', case['runtime_status'])

    def test_bbox_outside_oriented_photo_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'test-only.png'
            Image.new('RGB', (30, 40)).save(path)
            case = check_fixture({'id':'F03','test_case':'portrait_3_4','source_path':path.name,
                                  'source_sha256':inspect_source(path)['source_sha256'],
                                  'source_dimensions_px':[30,40], 'fish_bbox_xyxy':[0,0,31,40]}, root)
            self.assertIn('BBOX_INVALID_IN_ORIENTED_SOURCE', case['blockers'])
            self.assertIn('SOURCE_PROVENANCE_PENDING', case['blockers'])

    def test_duplicate_ids_cannot_complete_matrix(self):
        result = audit({'fixtures':[{'id':'F03','test_case':'portrait_3_4'}] * 8})
        self.assertEqual('BLOCKED', result['status'])
        self.assertIn('REQUIRED_F01_F08_MATRIX_INCOMPLETE_OR_DUPLICATED', result['matrix_errors'])

    def test_path_escape_is_rejected(self):
        case = check_fixture({'id':'F01','test_case':'landscape_4_3','source_path':'../outside.jpg'}, Path('/tmp/example'))
        self.assertIn('ORIGINAL_MISSING', case['blockers'])

if __name__ == '__main__':
    unittest.main()
