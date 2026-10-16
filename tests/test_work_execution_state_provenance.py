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


if __name__ == "__main__":
    unittest.main()
