import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("validate_m1_scope.py")
SPEC = importlib.util.spec_from_file_location("validate_m1_scope", MODULE_PATH)
scope = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(scope)


class ScopeProtectionTest(unittest.TestCase):
    def test_frozen_design_rasters_are_protected(self):
        for path in (
            "design/pages/recognition/example/frozen/guide.png",
            "design/system/core_visual_v1/components/assets/Primary_CTA_Frozen.svg",
        ):
            with self.subTest(path=path):
                self.assertTrue(scope.is_protected(path, []))

    def test_runtime_webp_is_not_misclassified_as_frozen_png_or_svg(self):
        self.assertFalse(
            scope.is_protected(
                "app/src/main/res/drawable-nodpi/recognition_guide.webp", []
            )
        )

    def test_control_plane_and_model_snapshots_are_protected(self):
        patterns = [
            "CONTROL_PLANE_REGISTRY.yaml",
            "harness/**",
            "app/src/main/assets/models/**",
        ]
        for path in (
            "CONTROL_PLANE_REGISTRY.yaml",
            "harness/FEATURE_MATRIX.yaml",
            "app/src/main/assets/models/detector.tflite",
        ):
            with self.subTest(path=path):
                self.assertTrue(scope.is_protected(path, patterns))


if __name__ == "__main__":
    unittest.main()
