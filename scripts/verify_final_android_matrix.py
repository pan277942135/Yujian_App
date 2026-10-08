#!/usr/bin/env python3
"""Verify the final Android API 28 matrix routes accumulated page tests."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
DISPATCH = (ROOT / "scripts/run_android_runtime_gate.sh").read_text(encoding="utf-8")

REQUIRED_GATES = {
    "normal-home-v1",
    "recognition-frozen",
    "empty-home-v2",
    "login-v2",
    "hifi-pages-v1",
    "data-sanitization",
    "fish-guide-v1",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


matrix = WORKFLOW.split("      matrix:\n", 1)[1].split("\n\n    steps:", 1)[0]
gate_names = re.findall(r"^\s+- gate: ([a-z0-9-]+)$", matrix, re.MULTILINE)
require(len(gate_names) == len(set(gate_names)), "Android gate matrix contains duplicate rows")
require(REQUIRED_GATES.issubset(gate_names), f"required gates missing: {sorted(REQUIRED_GATES - set(gate_names))}")
require("max-parallel: 1" in WORKFLOW, "API 28 Harness rows must remain serialized")
require("runs-on: [self-hosted, yujian-android, api28]" in WORKFLOW,
        "authoritative GCP API 28 runner labels changed")
require("Verify exact frozen APK pair" in WORKFLOW and "sha256sum -c SHA256SUMS" in WORKFLOW,
        "runtime matrix must install the exact build-once APK pair")
require("bash scripts/run_android_runtime_gate.sh --gate \"${{ matrix.gate }}\"" in WORKFLOW,
        "matrix must dispatch through the existing Android Runtime Harness")

for gate in REQUIRED_GATES | {"runtime-parity"}:
    script = gate.replace("-", "_") + ".sh"
    require(f'source "$RUNTIME_DIR/gates/{script}"' in DISPATCH,
            f"Harness dispatcher is missing {gate}")

EXPECTED_TESTS = {
    "login-v2": ("scripts/android_runtime/gates/login_v2.sh", "FishRecordDetailRuntimeTest"),
    "hifi-pages-v1": ("scripts/android_runtime/gates/hifi_pages_v1.sh", "HiFiPagesRuntimeTest"),
    "data-sanitization": (
        "scripts/android_runtime/gates/data_sanitization.sh",
        "PresentationSanitizationRuntimeTest",
    ),
    "fish-guide-v1": ("scripts/android_runtime/gates/fish_guide_v1.sh", "FishKnowledgeContractTest"),
}
for gate, (path, test_name) in EXPECTED_TESTS.items():
    source = (ROOT / path).read_text(encoding="utf-8")
    require(test_name in source, f"{test_name} is not included in required {gate} gate")

print("P14 final Android matrix static checks: PASS")
