"""Adapter for schema-only draft profiles that are not accepted page pilots."""

from __future__ import annotations


def validate(envelope: dict, profile: dict, root) -> dict:
    """Mark adapter execution as not applicable without implying page acceptance."""
    if envelope.get("status") != "DRAFT" or profile.get("status") != "DRAFT":
        return {
            "status": "FAIL",
            "formal_acceptance": "NOT_VERIFIED",
            "errors": ["schema_only_draft adapter only accepts DRAFT envelope/profile pairs"],
        }
    return {
        "status": "NOT_APPLICABLE",
        "formal_acceptance": "NOT_VERIFIED",
        "checks": ["profile_schema_only", "no_page_acceptance_claim"],
    }
