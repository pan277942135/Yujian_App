"""Classify a hosted emulator step that produced no runtime result JSON.

This keeps workflow cancellation distinct from a boot failure. GitHub does not
expose the initiator or reason for every cancelled step, so the cancellation
case intentionally records only what the workflow can prove.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class EmulatorOutcome:
    classification: str
    failure_phase: str
    failure_reason: str


def classify_emulator_outcome(available: bool, step_outcome: str) -> EmulatorOutcome:
    """Return truthful fallback metadata when the runtime result is absent."""
    outcome = step_outcome.strip().lower()

    if not available:
        return EmulatorOutcome(
            classification="BLOCKED_INFRA",
            failure_phase="EMULATOR_RUNNER",
            failure_reason="STANDARD_INTEL_MACOS_EMULATOR_RUNNER_UNAVAILABLE",
        )

    if outcome == "cancelled":
        return EmulatorOutcome(
            classification="CANCELLED",
            failure_phase="WORKFLOW_CANCELLATION",
            failure_reason="ANDROID_EMULATOR_STEP_CANCELLED_REASON_UNAVAILABLE",
        )

    if outcome == "failure":
        return EmulatorOutcome(
            classification="BLOCKED_INFRA",
            failure_phase="EMULATOR_RUNNER",
            failure_reason="ANDROID_EMULATOR_STEP_FAILED_BEFORE_RUNTIME_RESULT",
        )

    if outcome == "success":
        return EmulatorOutcome(
            classification="FAIL_EVIDENCE",
            failure_phase="EVIDENCE",
            failure_reason="ANDROID_EMULATOR_STEP_SUCCEEDED_WITHOUT_RUNTIME_RESULT",
        )

    return EmulatorOutcome(
        classification="BLOCKED_INFRA",
        failure_phase="EMULATOR_RUNNER",
        failure_reason=f"ANDROID_EMULATOR_STEP_OUTCOME_{outcome.upper() or 'UNKNOWN'}",
    )
