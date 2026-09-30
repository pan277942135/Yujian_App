#!/usr/bin/env python3
"""Fetch and verify the current production model Release for an Android build.

The classifier is a mutable production Release pointer. Each invocation reads
the current Release metadata, downloads its four linked assets by asset ID,
validates one coherent model contract and writes an exact APK snapshot. Detector
asset handling remains on its existing separate pinned contract.
"""

from __future__ import annotations

import hashlib
import json
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
from urllib.request import Request, urlopen

from production_model_contract import (
    ANDROID_CLASS_MAP_FILE,
    ANDROID_METADATA_FILE,
    ANDROID_MODEL_FILE,
    ANDROID_SNAPSHOT_FILE,
    ANDROID_TENSOR_CONTRACT_FILE,
    RELEASE_REPOSITORY,
    RELEASE_TAG,
    REQUIRED_ASSETS,
    ModelContractError,
    build_model_snapshot,
    snapshot_summary,
)


ROOT = Path(__file__).resolve().parents[1]
MAIN_ASSETS = ROOT / "app" / "src" / "main" / "assets"
TEST_DETECTOR_ASSETS = ROOT / "app" / "src" / "androidTest" / "assets" / "detector"

PRODUCTION_RELEASE_API_URL = (
    f"https://api.github.com/repos/{RELEASE_REPOSITORY}/releases/tags/{RELEASE_TAG}"
)
DETECTOR_DEFAULTS = {
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
class DetectorContract:
    detector_bundle_url: str
    detector_bundle_sha256: str

    @classmethod
    def from_environment(cls) -> "DetectorContract":
        values = {key: os.environ.get(key, value) for key, value in DETECTOR_DEFAULTS.items()}
        return cls(
            values["DETECTOR_BUNDLE_URL"],
            values["DETECTOR_BUNDLE_SHA256"],
        )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fetch_production_release() -> dict:
    request = Request(
        PRODUCTION_RELEASE_API_URL,
        headers={
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "YuJian-Android-Model-Bootstrap/1",
        },
    )
    with urlopen(request, timeout=30) as response:
        release = json.loads(response.read().decode("utf-8"))
    if not isinstance(release, dict):
        raise BootstrapError("production model release API did not return an object")
    return release


def _release_fingerprint(release: dict) -> tuple:
    assets = release.get("assets")
    if not isinstance(assets, list):
        raise BootstrapError("production model release has no asset list")
    indexed = {asset.get("name"): asset for asset in assets if isinstance(asset, dict)}
    missing = [name for name in REQUIRED_ASSETS if name not in indexed]
    if missing:
        raise BootstrapError(f"production model release missing required assets: {missing}")
    return (
        release.get("id"),
        release.get("tag_name"),
        tuple(
            (
                name,
                indexed[name].get("id"),
                indexed[name].get("size"),
                indexed[name].get("digest"),
                indexed[name].get("updated_at"),
                indexed[name].get("state"),
            )
            for name in REQUIRED_ASSETS
        ),
    )


def download_release_asset(asset: dict, destination: Path) -> bytes:
    """Download by Release asset id so mutable tag URLs cannot mix versions."""
    url = asset.get("url")
    if not isinstance(url, str) or not url.startswith(
        f"https://api.github.com/repos/{RELEASE_REPOSITORY}/releases/assets/"
    ):
        raise BootstrapError(f"invalid GitHub API asset URL for {asset.get('name')}")
    request = Request(
        url,
        headers={
            "Accept": "application/octet-stream",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "YuJian-Android-Model-Bootstrap/1",
        },
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.download-{time.time_ns()}")
    try:
        with urlopen(request, timeout=180) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        payload = temporary.read_bytes()
        if not payload:
            raise BootstrapError(f"empty production Release asset: {asset.get('name')}")
        os.replace(temporary, destination)
        return payload
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


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


def _write_asset(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.install-{time.time_ns()}")
    try:
        temporary.write_bytes(payload)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def bootstrap(contract: DetectorContract | None = None, root: Path = ROOT, cache: Path | None = None) -> None:
    global MAIN_ASSETS, TEST_DETECTOR_ASSETS
    original_main_assets = MAIN_ASSETS
    original_test_assets = TEST_DETECTOR_ASSETS
    try:
        MAIN_ASSETS = root / "app" / "src" / "main" / "assets"
        TEST_DETECTOR_ASSETS = root / "app" / "src" / "androidTest" / "assets" / "detector"
        contract = contract or DetectorContract.from_environment()
        cache = cache or Path(os.environ.get("YUJIAN_RECOGNITION_ASSET_CACHE", root / "build" / "recognition-assets-cache"))
        cache.mkdir(parents=True, exist_ok=True)
        MAIN_ASSETS.mkdir(parents=True, exist_ok=True)

        # Resolve the mutable production pointer once, download by the exact
        # Release asset IDs, then confirm the pointer did not move mid-build.
        release = fetch_production_release()
        release_assets = {
            asset.get("name"): asset
            for asset in release.get("assets", [])
            if isinstance(asset, dict) and asset.get("name") in REQUIRED_ASSETS
        }
        _release_fingerprint(release)
        with tempfile.TemporaryDirectory(prefix="yujian-production-model-") as temporary_name:
            temporary = Path(temporary_name)
            downloaded = {
                name: download_release_asset(release_assets[name], temporary / name)
                for name in REQUIRED_ASSETS
            }
            latest_release = fetch_production_release()
            if _release_fingerprint(release) != _release_fingerprint(latest_release):
                raise BootstrapError("production Release assets changed while this build snapshot was downloading")

            snapshot = build_model_snapshot(
                model=downloaded[REQUIRED_ASSETS[0]],
                metadata_bytes=downloaded[REQUIRED_ASSETS[1]],
                class_map_bytes=downloaded[REQUIRED_ASSETS[2]],
                tensor_contract_bytes=downloaded[REQUIRED_ASSETS[3]],
                release=release,
                build_sha=os.environ.get("GITHUB_SHA") or os.environ.get("BUILD_SHA"),
            )
            package_files = {
                ANDROID_MODEL_FILE: downloaded[REQUIRED_ASSETS[0]],
                ANDROID_METADATA_FILE: downloaded[REQUIRED_ASSETS[1]],
                ANDROID_CLASS_MAP_FILE: downloaded[REQUIRED_ASSETS[2]],
                ANDROID_TENSOR_CONTRACT_FILE: downloaded[REQUIRED_ASSETS[3]],
                ANDROID_SNAPSHOT_FILE: json.dumps(
                    snapshot, ensure_ascii=False, indent=2, sort_keys=True
                ).encode("utf-8") + b"\n",
            }
            for filename, payload in package_files.items():
                _write_asset(MAIN_ASSETS / filename, payload)

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
        print("MODEL_RELEASE_SNAPSHOT=" + json.dumps(snapshot_summary(snapshot), ensure_ascii=False, sort_keys=True))
    finally:
        MAIN_ASSETS = original_main_assets
        TEST_DETECTOR_ASSETS = original_test_assets


def main() -> int:
    try:
        bootstrap()
    except (BootstrapError, ModelContractError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
