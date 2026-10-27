import hashlib
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.design import validate_page_resource_binding as binding  # noqa: E402


class PageResourceBindingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / "repo"
        self.root.mkdir()
        self.site = Path(self.temp.name) / "site"
        self.site.mkdir()
        self.owner = "design/pages/home/test_home"
        self.asset_rel = f"{self.owner}/assets/hero.png"
        self.asset_path = self.root / self.asset_rel
        self.asset_path.parent.mkdir(parents=True)
        Image.new("RGB", (16, 24), (25, 80, 120)).save(self.asset_path, format="PNG")
        self.marker = "cp -R design _site/design"
        self.workflow_rel = ".github/workflows/design-manager-pages.yml"
        (self.root / self.workflow_rel).parent.mkdir(parents=True)
        (self.root / self.workflow_rel).write_text(self.marker + "\n", encoding="utf-8")
        self.experience_rel = "design/registry/experience_registry_v1.json"
        exp_path = self.root / self.experience_rel
        exp_path.parent.mkdir(parents=True)
        exp_path.write_text(json.dumps({"features": [{"id": "test_home", "owner_path": self.owner}]}), encoding="utf-8")
        self.manifest_rel = f"{self.owner}/manifest.json"
        self.manifest_path = self.root / self.manifest_rel
        raw = self.asset_path.read_bytes()
        with Image.open(self.asset_path) as image:
            width, height = image.size
        self.designation = "ORIGINAL"
        self.requiredness = "REQUIRED"
        self.manifest_path.write_text(json.dumps({
            "version": "v1",
            "asset": {
                "path": self.asset_rel,
                "mime": "image/png",
                "bytes": len(raw),
                "sha256": hashlib.sha256(raw).hexdigest(),
                "width": width,
                "height": height,
                "lifecycle_status": "FROZEN",
                "designation": "ORIGINAL",
            },
        }), encoding="utf-8")
        self.schema_rel = "design/system_v3/06_Asset_System/Page_Resource_Binding_Schema_V1.json"
        schema_path = self.root / self.schema_rel
        schema_path.parent.mkdir(parents=True)
        shutil.copy2(ROOT / self.schema_rel, schema_path)
        self.private_manifest_rel = "design/pages/recognition/web/VISUAL_REFERENCE_MANIFEST_V1.json"
        private_manifest = self.root / self.private_manifest_rel
        private_manifest.parent.mkdir(parents=True)
        private_manifest.write_text(json.dumps({"references": []}), encoding="utf-8")
        self.manifest = json.loads(self.manifest_path.read_text())
        self.binding_path = self.root / f"{self.owner}/Page_Resource_Binding_V1.json"
        self.binding_path.write_text(json.dumps(self.make_binding()), encoding="utf-8")
        self.page = {"id": "test_home", "owner_path": self.owner}
        self.copy_asset_to_site()

    def tearDown(self):
        self.temp.cleanup()

    def sha(self, path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def source_authority(self, authority_id, path, purpose):
        return {
            "authority_id": authority_id,
            "path": path,
            "sha256": self.sha(self.root / path),
            "purpose": purpose,
        }

    def make_binding(self):
        raw = self.asset_path.read_bytes()
        with Image.open(self.asset_path) as image:
            width, height = image.size
        manifest_sha = hashlib.sha256(raw).hexdigest()
        return {
            "schema_version": "1.0",
            "binding_id": "test_home_binding_v1",
            "page_id": "test_home",
            "page_id_aliases": [],
            "owner_path": self.owner,
            "authority_version": "v1",
            "page_identity_authority": {
                "authority_id": "experience",
                "json_pointer": "/features/0",
                "field_pointer": "/id",
            },
            "authority_version_source": {
                "authority_id": "manifest",
                "json_pointer": "",
                "field_pointer": "/version",
            },
            "source_authorities": [
                self.source_authority("experience", self.experience_rel, "Page identity"),
                self.source_authority("manifest", self.manifest_rel, "Page resource manifest"),
                self.source_authority("workflow", self.workflow_rel, "Pages publication policy"),
            ],
            "publication_policy": {"authority_id": "workflow", "public_scope_marker": self.marker},
            "page_notes": [],
            "asset_bindings": [{
                "page_id": "test_home",
                "asset_id": "test_home_hero",
                "asset_role": "page_visual_authority",
                "source_path": self.asset_rel,
                "source_authorities": [{
                    "authority_id": "manifest",
                    "json_pointer": "/asset",
                    "field_map": {
                        "source_path": "/path",
                        "media_type": "/mime",
                        "bytes": "/bytes",
                        "sha256": "/sha256",
                        "pixel_dimensions.width": "/width",
                        "pixel_dimensions.height": "/height",
                        "lifecycle_status": "/lifecycle_status",
                        "asset_designation": "/designation",
                    },
                }],
                "media_type": "image/png",
                "bytes": len(raw),
                "sha256": manifest_sha,
                "pixel_dimensions": {"width": width, "height": height},
                "coordinate_space": "reference_1080x1920",
                "fit_crop_policy": "Fit",
                "lifecycle_status": "FROZEN",
                "asset_designation": self.designation,
                "publication_permission": {
                    "state": "PUBLIC_BY_EXISTING_PAGES_RULE",
                    "authority_id": "workflow",
                    "scope": "Test fixture explicit Pages rule",
                    "policy_marker": self.marker,
                },
                "requiredness": self.requiredness,
            }],
        }

    def read_binding(self):
        return json.loads(self.binding_path.read_text())

    def write_binding(self, data):
        self.binding_path.write_text(json.dumps(data), encoding="utf-8")

    def copy_asset_to_site(self):
        target = self.site / self.asset_rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if self.asset_path.is_file():
            shutil.copy2(self.asset_path, target)

    def validate(self):
        return binding.validate_binding(self.root, self.binding_path, self.page, self.site)

    def add_site_file(self, relative, content):
        path = self.site / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def test_checked_in_three_page_bindings_validate(self):
        report = binding.validate_all(
            ROOT,
            ("home_normal_v1", "home_empty_v2", "auth_login_v2"),
        )
        self.assertEqual(report["status"], "PASS", report["errors"])
        self.assertEqual({row["page_id"] for row in report["pages"]}, {"home_normal_v1", "home_empty_v2", "auth_login_v2"})

    def test_resolver_returns_real_resources_for_all_three_pages(self):
        cases = [
            ("home_normal_v1", "frozen_page_visual_authority", "2.0", "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"),
            ("home_empty_v2", "page_visual_authority_base", "V2.2", "30f95fc68b65d5a55552ba279753cac1fd979216679217377e43cea8e33cb85b"),
            ("auth_login_v2", "page_visual_authority_v2_1", "V2.1", "c9323f11a9d935f8e98b95276a73930b549b20262eddb5a57968a10835c7486e"),
        ]
        for page_id, role, version, sha in cases:
            with self.subTest(page_id=page_id):
                result = binding.resolve_page_asset(ROOT, page_id, role, version, sha)
                self.assertEqual(result["status"], "RESOLVED", result["reason"])
                resource = result["resources"][0]
                self.assertEqual(resource["sha256"], sha)
                self.assertTrue(resource["source_path"])
                self.assertEqual(resource["verification"]["validation_status"], "PASS")

    def test_resolver_rejects_an_unregistered_source_sha(self):
        result = binding.resolve_page_asset(
            ROOT,
            "home_normal_v1",
            "frozen_page_visual_authority",
            "2.0",
            "0" * 64,
        )
        self.assertEqual(result["status"], "BLOCKED_INTEGRITY")
        self.assertEqual(result["reason"]["code"], "SOURCE_SHA_MISMATCH")

    def test_gate_rejects_a_reported_sha_that_is_not_the_checkout(self):
        report = binding.validate_all(
            ROOT,
            ("home_normal_v1", "home_empty_v2", "auth_login_v2"),
            source_sha="0" * 40,
        )
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("EXACT_SOURCE_SHA_MISMATCH" in error for error in report["errors"]))

    def test_required_file_missing_fails_closed(self):
        self.asset_path.unlink()
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("REQUIRED_RESOURCE_MISSING" in error for error in report["errors"]))

    def test_sha_mismatch_fails(self):
        self.asset_path.write_bytes(self.asset_path.read_bytes() + b"tampered")
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("SHA256_MISMATCH" in error for error in report["errors"]))

    def test_byte_count_mismatch_fails(self):
        data = self.read_binding()
        data["asset_bindings"][0]["bytes"] += 1
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("BYTE_COUNT_MISMATCH" in error or "SOURCE_RECORD_MISMATCH" in error for error in report["errors"]))

    def test_media_type_mismatch_fails(self):
        data = self.read_binding()
        data["asset_bindings"][0]["media_type"] = "image/jpeg"
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("MEDIA_TYPE_MISMATCH" in error or "SOURCE_RECORD_MISMATCH" in error for error in report["errors"]))

    def test_unsafe_source_path_fails(self):
        data = self.read_binding()
        data["asset_bindings"][0]["source_path"] = "../outside.png"
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("RESOURCE_PATH_INVALID" in error or "SCHEMA_INVALID" in error for error in report["errors"]))

    def test_registered_dimensions_mismatch_fails(self):
        data = self.read_binding()
        data["asset_bindings"][0]["pixel_dimensions"]["width"] += 1
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("DIMENSIONS_MISMATCH" in error or "SOURCE_RECORD_MISMATCH" in error for error in report["errors"]))

    def test_invalid_source_authority_fails(self):
        data = self.read_binding()
        data["source_authorities"][1]["sha256"] = "0" * 64
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("SOURCE_AUTHORITY_SHA_MISMATCH" in error for error in report["errors"]))

    def test_replaced_frozen_asset_fails_even_if_binding_is_rewritten(self):
        Image.new("RGB", (16, 24), (220, 10, 10)).save(self.asset_path, format="PNG")
        data = self.read_binding()
        raw = self.asset_path.read_bytes()
        data["asset_bindings"][0]["sha256"] = hashlib.sha256(raw).hexdigest()
        data["asset_bindings"][0]["bytes"] = len(raw)
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("SOURCE_RECORD_MISMATCH" in error for error in report["errors"]))

    def test_illustrative_media_cannot_be_promoted_to_original(self):
        self.designation = "ILLUSTRATIVE_ONLY"
        self.requiredness = "OPTIONAL"
        raw = self.asset_path.read_bytes()
        self.manifest["asset"]["designation"] = "ILLUSTRATIVE_ONLY"
        self.manifest["asset"]["lifecycle_status"] = "GENERATED_ILLUSTRATIVE_ONLY"
        self.manifest_path.write_text(json.dumps(self.manifest), encoding="utf-8")
        data = self.read_binding()
        asset = data["asset_bindings"][0]
        asset["asset_designation"] = "ILLUSTRATIVE_ONLY"
        asset["lifecycle_status"] = "GENERATED_ILLUSTRATIVE_ONLY"
        asset["requiredness"] = "OPTIONAL"
        asset["degradation_policy"] = "Omit preview media only; keep page geometry unchanged."
        self.write_binding(data)
        # Update the pinned authority SHA only for the legitimate fixture setup.
        data = self.read_binding()
        data["source_authorities"][1]["sha256"] = self.sha(self.manifest_path)
        self.write_binding(data)
        data = self.read_binding()
        data["asset_bindings"][0]["asset_designation"] = "ORIGINAL"
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("ILLUSTRATIVE_PROMOTION_BLOCKED" in error for error in report["errors"]))

    def test_private_image_in_published_bundle_fails_by_path_and_bytes(self):
        buffer = io.BytesIO()
        Image.new("RGB", (8, 8), (10, 20, 30)).save(buffer, format="PNG")
        content = buffer.getvalue()
        private_hash = hashlib.sha256(content).hexdigest()
        manifest_path = self.root / self.private_manifest_rel
        manifest_path.write_text(json.dumps({"references": [{
            "filename": "private-fish-photo.png",
            "sha256": private_hash,
            "public_publication_allowed": False,
        }]}), encoding="utf-8")
        private_source = self.root / "app/private/photo.png"
        private_source.parent.mkdir(parents=True)
        private_source.write_bytes(content)
        # The public bundle contains a renamed copy, so the actual _site byte scan must catch it.
        self.add_site_file("media/renamed.png", content)
        data = self.read_binding()
        data["source_authorities"].append(self.source_authority("private_policy", self.private_manifest_rel, "Private image denylist"))
        data["asset_bindings"].append({
            "page_id": "test_home",
            "asset_id": "private_user_photo",
            "asset_role": "runtime_user_fish_photo",
            "source_path": "app/private/photo.png",
            "source_authorities": [{
                "authority_id": "private_policy",
                "json_pointer": "/references/0",
                "field_map": {"sha256": "/sha256"},
            }],
            "media_type": "image/png",
            "bytes": len(content),
            "sha256": private_hash,
            "pixel_dimensions": None,
            "coordinate_space": None,
            "fit_crop_policy": None,
            "lifecycle_status": "RUNTIME_USER_MEDIA_NOT_STATIC",
            "asset_designation": "NOT_APPLICABLE",
            "publication_permission": {
                "state": "PRIVATE_NOT_DISTRIBUTABLE",
                "authority_id": "private_policy",
                "scope": "Private user photo",
            },
            "requiredness": "NOT_VERIFIED",
        })
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("PRIVATE_REFERENCE_BYTES_IN_PAGES" in error for error in report["errors"]))

    def test_missing_public_permission_cannot_publish(self):
        data = self.read_binding()
        data["asset_bindings"][0]["publication_permission"]["state"] = "NOT_CONFIRMED"
        data["asset_bindings"][0]["publication_permission"].pop("policy_marker", None)
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("UNAUTHORIZED_RESOURCE_PATH_IN_PAGES" in error for error in report["errors"]))

    def test_optional_missing_illustration_degrades_without_changing_geometry(self):
        data = self.read_binding()
        asset = data["asset_bindings"][0]
        asset["requiredness"] = "OPTIONAL"
        asset["degradation_policy"] = "Omit preview media only; keep page geometry unchanged."
        self.write_binding(data)
        self.asset_path.unlink()
        (self.site / self.asset_rel).unlink()
        report = self.validate()
        self.assertEqual(report["status"], "PASS", report["errors"])
        result = report["asset_results"][0]
        self.assertEqual(result["resource_status"], "MISSING")
        self.assertEqual(result["degradation"], "Omit preview media only; keep page geometry unchanged.")
        self.assertEqual(data["asset_bindings"][0]["coordinate_space"], "reference_1080x1920")

    def test_missing_permission_field_fails_schema(self):
        data = self.read_binding()
        data["asset_bindings"][0].pop("publication_permission")
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("SCHEMA_INVALID" in error for error in report["errors"]))

    def test_resource_page_id_must_match_owning_binding(self):
        data = self.read_binding()
        data["asset_bindings"][0]["page_id"] = "home_empty_v2"
        self.write_binding(data)
        report = self.validate()
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("RESOURCE_PAGE_ID_MISMATCH" in error for error in report["errors"]))


if __name__ == "__main__":
    unittest.main()
