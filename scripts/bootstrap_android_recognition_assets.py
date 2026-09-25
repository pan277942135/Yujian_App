#!/usr/bin/env python3
"""Prepare and verify every recognition asset required by an Android build.

This is the one canonical bootstrap used by CI and local/Work QA builds.  The
release URLs and SHA-256 values below are copied from the existing production
workflow and are deliberately not configurable by source changes.  Environment
variables are supported for mirrors and test fixtures, but the default values
remain the production contract.
"""

from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN_ASSETS = ROOT / "app" / "src" / "main" / "assets"
TEST_DETECTOR_ASSETS = ROOT / "app" / "src" / "androidTest" / "assets" / "detector"

PRODUCTION_DEFAULTS = {
    "MODEL_TFLITE_URL": "https://github.com/pan277942135/Yujian/releases/download/mobile-model-v0.2/fish_classifier_v0_2.tflite",
    "MODEL_TFLITE_SHA256": "b77ea78e7f8554078ea3a79051039af1ace04f0ac4e2604da57d1dd8f0b010e7",
    "MODEL_TENSOR_CONTRACT_URL": "https://github.com/pan277942135/Yujian/releases/download/mobile-model-v0.2/tensor_contract.json",
    "MODEL_TENSOR_CONTRACT_SHA256": "f9a477f4f9ecd23b0162ee7f06c0f6965f005a52f17755e11f7b9283e104b1d8",
    "DETECTOR_BUNDLE_URL": "https://github.com/pan277942135/Yujian/releases/download/detector-model-v0.1/det_fish_v0_1_android_bundle.zip",
    "DETECTOR_BUNDLE_SHA256": "246ddacaf89ca7ecc9c64a47d3e12c5ee9088d92c763a38626778505ae8c15ee",
}

REQUIRED_DETECTOR_MEMBERS = (
    "fish_detector_yolox_nano_v0_1.onnx",
    "detector_metadata.json",
    "recognition_pipeline_v1.json",
    "golden_cases.json",
)
REQUIRED_GOLDEN_CASES = ("ready", "no_fish", "incomplete_fish", "fish_too_small", "multiple_fish")


class BootstrapError(RuntimeError):
    pass


@dataclass(frozen=True)
class Contract:
    tflite_url: str
    tflite_sha256: str
    tensor_contract_url: str
    tensor_contract_sha256: str
    detector_bundle_url: str
    detector_bundle_sha256: str

    @classmethod
    def from_environment(cls) -> "Contract":
        values = {key: os.environ.get(key, value) for key, value in PRODUCTION_DEFAULTS.items()}
        return cls(
            values["MODEL_TFLITE_URL"],
            values["MODEL_TFLITE_SHA256"],
            values["MODEL_TENSOR_CONTRACT_URL"],
            values["MODEL_TENSOR_CONTRACT_SHA256"],
            values["DETECTOR_BUNDLE_URL"],
            values["DETECTOR_BUNDLE_SHA256"],
        )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_file_hash(path: Path, expected: str, label: str) -> None:
    if not path.is_file():
        raise BootstrapError(f"{label}: missing file {path}")
    actual = sha256(path)
    if actual != expected:
        raise BootstrapError(f"{label}: SHA-256 mismatch; expected {expected}, got {actual}")


def _download(url: str, destination: Path, label: str) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.download-{os.getpid()}-{time.time_ns()}")
    try:
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "YuJian-recognition-bootstrap/1"})
                with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                    shutil.copyfileobj(response, output)
                if temporary.stat().st_size == 0:
                    raise BootstrapError(f"{label}: downloaded file is zero bytes")
                os.replace(temporary, destination)
                return
            except Exception as error:  # retry network and transient file errors
                last_error = error
                temporary.unlink(missing_ok=True)
                if attempt < 2:
                    time.sleep(2 ** attempt)
        raise BootstrapError(f"{label}: download failed from {url}: {last_error}")
    finally:
        temporary.unlink(missing_ok=True)


def materialize_verified_file(url: str, expected_sha: str, destination: Path, cache: Path, label: str) -> None:
    """Reuse only a hash-verified destination/cache file; otherwise redownload."""
    if destination.is_file():
        try:
            verify_file_hash(destination, expected_sha, label)
            print(f"{label}: verified existing packaged source")
            return
        except BootstrapError:
            pass

    cached = cache / destination.name
    if cached.is_file():
        try:
            verify_file_hash(cached, expected_sha, label)
        except BootstrapError:
            cached.unlink(missing_ok=True)

    if not cached.is_file():
        _download(url, cached, label)
    verify_file_hash(cached, expected_sha, label)

    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.install-{os.getpid()}-{time.time_ns()}")
    shutil.copyfile(cached, temporary)
    os.replace(temporary, destination)
    verify_file_hash(destination, expected_sha, label)
    print(f"{label}: downloaded and verified")


def _member_candidates(archive: zipfile.ZipFile, basename: str) -> list[str]:
    return [name for name in archive.namelist() if name == basename or name.endswith(f"/{basename}")]


def find_required_detector_members(archive: zipfile.ZipFile) -> dict[str, str]:
    members: dict[str, str] = {}
    for basename in REQUIRED_DETECTOR_MEMBERS:
        candidates = _member_candidates(archive, basename)
        if len(candidates) != 1:
            raise BootstrapError(
                f"detector bundle: expected exactly one member named {basename}, found {candidates}"
            )
        members[basename] = candidates[0]

    golden = [name for name in archive.namelist() if "/golden/" in f"/{name}" and not name.endswith("/")]
    if not golden:
        raise BootstrapError("detector bundle: missing golden/ fixtures")
    expected_golden = {f"golden/{case}.jpg" for case in REQUIRED_GOLDEN_CASES}
    expected_golden |= {f"golden/{case}.jpeg" for case in REQUIRED_GOLDEN_CASES}
    expected_golden |= {f"golden/{case}.png" for case in REQUIRED_GOLDEN_CASES}
    expected_stems = {Path(name).stem for name in golden}
    if not all(case in expected_stems for case in REQUIRED_GOLDEN_CASES):
        raise BootstrapError(f"detector bundle: golden/ is missing one or more required cases; found {golden}")
    return members


def _safe_extract_member(archive: zipfile.ZipFile, member: str, destination: Path) -> None:
    target = destination.resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    with archive.open(member) as source, target.with_name(f".{target.name}.install-{time.time_ns()}").open("wb") as output:
        shutil.copyfileobj(source, output)
    temporary = next(target.parent.glob(f".{target.name}.install-*"))
    os.replace(temporary, target)


def install_detector_bundle(bundle: Path) -> None:
    if not bundle.is_file():
        raise BootstrapError(f"detector bundle: missing archive {bundle}")
    with zipfile.ZipFile(bundle) as archive:
        members = find_required_detector_members(archive)
        with tempfile.TemporaryDirectory(prefix="yujian-detector-") as temporary_name:
            temporary = Path(temporary_name)
            main = temporary / "main"
            tests = temporary / "tests"
            for basename in REQUIRED_DETECTOR_MEMBERS[:3]:
                _safe_extract_member(archive, members[basename], main / basename)

            golden_manifest = members["golden_cases.json"]
            _safe_extract_member(archive, golden_manifest, tests / "golden_cases.json")
            golden_target = tests / "golden"
            golden_target.mkdir(parents=True, exist_ok=True)
            for name in archive.namelist():
                if "/golden/" not in f"/{name}" or name.endswith("/"):
                    continue
                relative_name = name.split("/golden/", 1)[1] if "/golden/" in name else name.split("golden/", 1)[1]
                relative = Path(relative_name)
                if relative.is_absolute() or ".." in relative.parts:
                    raise BootstrapError(f"detector bundle: unsafe golden member {name}")
                _safe_extract_member(archive, name, golden_target / relative)

            for basename in REQUIRED_DETECTOR_MEMBERS[:3]:
                destination = MAIN_ASSETS / basename
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(main / basename, destination)
            TEST_DETECTOR_ASSETS.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(tests / "golden_cases.json", TEST_DETECTOR_ASSETS / "golden_cases.json")
            golden_destination = TEST_DETECTOR_ASSETS / "golden"
            if golden_destination.exists():
                shutil.rmtree(golden_destination)
            shutil.copytree(golden_target, golden_destination)

    for basename in REQUIRED_DETECTOR_MEMBERS[:3]:
        if not (MAIN_ASSETS / basename).is_file():
            raise BootstrapError(f"detector bundle: failed to install {basename}")
    if not (TEST_DETECTOR_ASSETS / "golden_cases.json").is_file() or not (TEST_DETECTOR_ASSETS / "golden").is_dir():
        raise BootstrapError("detector bundle: failed to install golden fixtures")
    print("detector bundle: members installed and verified")


def run_verifier(script: str) -> None:
    result = subprocess.run([sys.executable, str(ROOT / "scripts" / script)], cwd=ROOT)
    if result.returncode:
        raise BootstrapError(f"{script} failed with exit code {result.returncode}")


def bootstrap(contract: Contract | None = None, root: Path = ROOT, cache: Path | None = None) -> None:
    global MAIN_ASSETS, TEST_DETECTOR_ASSETS
    original_main_assets = MAIN_ASSETS
    original_test_assets = TEST_DETECTOR_ASSETS
    try:
        MAIN_ASSETS = root / "app" / "src" / "main" / "assets"
        TEST_DETECTOR_ASSETS = root / "app" / "src" / "androidTest" / "assets" / "detector"
        contract = contract or Contract.from_environment()
        cache = cache or Path(os.environ.get("YUJIAN_RECOGNITION_ASSET_CACHE", root / "build" / "recognition-assets-cache"))
        cache.mkdir(parents=True, exist_ok=True)
        MAIN_ASSETS.mkdir(parents=True, exist_ok=True)

        materialize_verified_file(
            contract.tflite_url,
            contract.tflite_sha256,
            MAIN_ASSETS / "fish_classifier.tflite",
            cache,
            "classifier",
        )
        materialize_verified_file(
            contract.tensor_contract_url,
            contract.tensor_contract_sha256,
            MAIN_ASSETS / "model_tensor_contract.json",
            cache,
            "tensor contract",
        )

        bundle_cache = cache / "detector_bundle.zip"
        if bundle_cache.is_file():
            try:
                verify_file_hash(bundle_cache, contract.detector_bundle_sha256, "detector bundle")
            except BootstrapError:
                bundle_cache.unlink(missing_ok=True)
        if not bundle_cache.is_file():
            _download(contract.detector_bundle_url, bundle_cache, "detector bundle")
        verify_file_hash(bundle_cache, contract.detector_bundle_sha256, "detector bundle")
        install_detector_bundle(bundle_cache)

        # These are hard gates, not informational checks.
        run_verifier("verify_production_model.py")
        run_verifier("verify_production_detector.py")
        print("BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_PASS")
    finally:
        MAIN_ASSETS = original_main_assets
        TEST_DETECTOR_ASSETS = original_test_assets


def main() -> int:
    try:
        bootstrap()
    except (BootstrapError, OSError, zipfile.BadZipFile) as error:
        print(f"BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
