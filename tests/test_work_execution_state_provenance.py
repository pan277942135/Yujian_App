import unittest

from scripts.validate_work_execution_state import resolve_contract, validate_source_sha


class WorkExecutionStateProvenanceTests(unittest.TestCase):
    def test_committed_checkpoint_uses_direct_product_parent(self):
        self.assertIsNone(
            validate_source_sha(
                "a" * 40,
                "b" * 40,
                ["a" * 40],
                True,
            )
        )

    def test_committed_checkpoint_rejects_head_self_reference(self):
        self.assertEqual(
            validate_source_sha(
                "a" * 40,
                "a" * 40,
                ["p" * 40],
                True,
            ),
            "source_sha cannot equal a committed checkpoint HEAD (self-reference)",
        )

    def test_uncommitted_local_preflight_may_use_current_head(self):
        self.assertIsNone(
            validate_source_sha(
                "a" * 40,
                "a" * 40,
                [],
                False,
            )
        )

    def test_contract_id_alias_resolves_to_feature_contract(self):
        contracts = {
            "feature_contracts": {
                "home_normal_v1": {"contract_id": "FC-home_normal_v1", "status": "FROZEN"}
            }
        }
        self.assertEqual(
            resolve_contract(contracts, "FC-home_normal_v1")["status"],
            "FROZEN",
        )

    def test_duplicate_contract_id_alias_is_rejected_as_ambiguous(self):
        contracts = {
            "feature_contracts": {
                "home_normal_v1": {"contract_id": "FC-duplicate"}
            },
            "shared_contracts": {
                "home_normal_shared": {"contract_id": "FC-duplicate"}
            },
        }
        with self.assertRaisesRegex(ValueError, "ambiguous"):
            resolve_contract(contracts, "FC-duplicate")


    def test_multiple_state_only_checkpoints_preserve_source_sha(self):
        self.assertIsNone(
            validate_source_sha(
                "a" * 40, "c" * 40, ["b" * 40], True,
                changed_paths=["WORK_EXECUTION_STATE.json", "PROJECT_MASTER_STATE.yaml"],
                source_is_ancestor=True,
            )
        )

    def test_product_changes_invalidate_checkpoint_source(self):
        err = validate_source_sha(
            "a" * 40, "c" * 40, ["b" * 40], True,
            changed_paths=["WORK_EXECUTION_STATE.json", "app/src/main/MainActivity.kt"],
            source_is_ancestor=True,
        )
        self.assertIn("revalidation", err)

    def test_design_contract_changes_invalidate_checkpoint_source(self):
        err = validate_source_sha(
            "a" * 40, "b" * 40, ["a" * 40], True,
            changed_paths=["design/system_v3/08_Page_Contracts/example.json"],
            source_is_ancestor=True,
        )
        self.assertIn("revalidation", err)

    def test_nonancestor_source_sha_is_rejected(self):
        err = validate_source_sha(
            "a" * 40, "c" * 40, ["b" * 40], True,
            changed_paths=["WORK_EXECUTION_STATE.json"],
            source_is_ancestor=False,
        )
        self.assertIn("ancestor", err)


if __name__ == "__main__":
    unittest.main()
