#!/usr/bin/env python3
"""Bootstrap the pinned production DET_FISH v0.3 and Classifier v0.2 Android assets.

The classifier runtime continues to use its existing mobile-model-v0.2 production
Release. The model-assets repository ZIP is downloaded separately as provenance
evidence and must contain the exact same TFLite bytes and semantic class mapping.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
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
from det_fish_v03_contract import (
    ANDROID_CLASSIFIER_PARITY_FILE,
    ANDROID_DETECTOR_MODEL_FILE,
    ANDROID_DETECTOR_METADATA_FILE,
    ANDROID_DETECTOR_PIPELINE_FILE,
    ANDROID_DETECTOR_SNAPSHOT_FILE,
    ANDROID_MODEL_PACKAGE_MANIFEST_FILE,
    CLASSIFIER_BUNDLE_ASSET_ID,
    DETECTOR_ASSET_ID,
    DetectorContractError,
    build_android_model_package_manifest,
    cached_release_asset,
    detector_snapshot_summary,
    fetch_model_assets_release,
    install_detector_zip,
    verify_classifier_bundle_parity,
)

ROOT = Path(__file__).resolve().parents[1]
MAIN_ASSETS = ROOT / "app" / "src" / "main" / "assets"
PRODUCTION_RELEASE_API_URL = (
    f"https://api.github.com/repos/{RELEASE_REPOSITORY}/releases/tags/{RELEASE_TAG}"
)


class BootstrapError(RuntimeError):
    pass


def _github_api_authorization() -> dict[str, str]:
    token = os.environ.get("YUJIAN_GITHUB_TOKEN", "").strip()
    return {"Authorization": f"Bearer {token}"} if token else {}


def fetch_production_release() -> dict:
    request = Request(
        PRODUCTION_RELEASE_API_URL,
        headers={
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "YuJian-Android-Model-Bootstrap/2",
            **_github_api_authorization(),
        },
    )
    with urlopen(request, timeout=30) as response:
        release = json.loads(response.read().decode("utf-8"))
    if not isinstance(release, dict):
        raise BootstrapError("production classifier Release API did not return an object")
    return release


def _release_fingerprint(release: dict) -> tuple:
    assets = release.get("assets")
    if not isinstance(assets, list):
        raise BootstrapError("production classifier Release has no asset list")
    indexed = {asset.get("name"): asset for asset in assets if isinstance(asset, dict)}
    missing = [name for name in REQUIRED_ASSETS if name not in indexed]
    if missing:
        raise BootstrapError(f"production classifier Release missing required assets: {missing}")
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


def download_production_release_asset(asset: dict, destination: Path) -> bytes:
    """Download the classifier's current production Release asset by asset ID."""
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
            "User-Agent": "YuJian-Android-Model-Bootstrap/2",
            **_github_api_authorization(),
        },
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.download-{time.time_ns()}")
    try:
        with urlopen(request, timeout=180) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        payload = temporary.read_bytes()
        if not payload:
            raise BootstrapError(f"empty production classifier Release asset: {asset.get('name')}")
        os.replace(temporary, destination)
        return payload
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


def _write_asset(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.install-{time.time_ns()}")
    try:
        temporary.write_bytes(payload)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _model_assets_fingerprint(release: dict) -> tuple:
    return (
        release.get("id"),
        release.get("tag_name"),
        tuple(
            sorted(
                (
                    item.get("id"),
                    item.get("name"),
                    item.get("size"),
                    item.get("digest"),
                )
                for item in release.get("assets", [])
                if isinstance(item, dict)
            )
        ),
    )


def run_verifier(script: str) -> None:
    result = subprocess.run([sys.executable, str(ROOT / "scripts" / script)], cwd=ROOT)
    if result.returncode:
        raise BootstrapError(f"{script} failed with exit code {result.returncode}")


def bootstrap(root: Path = ROOT, cache: Path | None = None) -> None:
    global MAIN_ASSETS
    original_main_assets = MAIN_ASSETS
    try:
        MAIN_ASSETS = root / "app" / "src" / "main" / "assets"
        cache = cache or Path(
            os.environ.get("YUJIAN_RECOGNITION_ASSET_CACHE", root / "build" / "recognition-assets-cache")
        )
        cache.mkdir(parents=True, exist_ok=True)
        MAIN_ASSETS.mkdir(parents=True, exist_ok=True)

        # Remove only the obsolete v0.1 runtime binary/cache. Historical fixtures
        # and historical documentation remain untouched and are not production inputs.
        (MAIN_ASSETS / "fish_detector_yolox_nano_v0_1.onnx").unlink(missing_ok=True)
        (cache / "detector_bundle.zip").unlink(missing_ok=True)

        # Classifier: preserve the already released mobile-model-v0.2 production
        # runtime assets and snapshot. Resolve the release once and verify it did
        # not change while its four immutable assets were being downloaded.
        classifier_release = fetch_production_release()
        release_assets = {
            asset.get("name"): asset
            for asset in classifier_release.get("assets", [])
            if isinstance(asset, dict) and asset.get("name") in REQUIRED_ASSETS
        }
        classifier_fingerprint = _release_fingerprint(classifier_release)
        with tempfile.TemporaryDirectory(prefix="yujian-production-classifier-") as temporary_name:
            temporary = Path(temporary_name)
            downloaded = {
                name: download_production_release_asset(release_assets[name], temporary / name)
                for name in REQUIRED_ASSETS
            }
            latest_classifier_release = fetch_production_release()
            if classifier_fingerprint != _release_fingerprint(latest_classifier_release):
                raise BootstrapError("classifier production Release assets changed while this build snapshot was downloading")

            classifier_snapshot = build_model_snapshot(
                model=downloaded[REQUIRED_ASSETS[0]],
                metadata_bytes=downloaded[REQUIRED_ASSETS[1]],
                class_map_bytes=downloaded[REQUIRED_ASSETS[2]],
                tensor_contract_bytes=downloaded[REQUIRED_ASSETS[3]],
                release=classifier_release,
                build_sha=os.environ.get("BUILD_SHA") or os.environ.get("GITHUB_SHA"),
            )
            classifier_package_files = {
                ANDROID_MODEL_FILE: downloaded[REQUIRED_ASSETS[0]],
                ANDROID_METADATA_FILE: downloaded[REQUIRED_ASSETS[1]],
                ANDROID_CLASS_MAP_FILE: downloaded[REQUIRED_ASSETS[2]],
                ANDROID_TENSOR_CONTRACT_FILE: downloaded[REQUIRED_ASSETS[3]],
                ANDROID_SNAPSHOT_FILE: json.dumps(
                    classifier_snapshot, ensure_ascii=False, indent=2, sort_keys=True
                ).encode("utf-8") + b"\n",
            }
            for filename, payload in classifier_package_files.items():
                _write_asset(MAIN_ASSETS / filename, payload)

        # Model Assets Release: fixed Release ID + fixed Asset IDs + fixed ZIP
        # SHA-256. The repository is public, so this download deliberately does
        # not use the app-repository-scoped GITHUB_TOKEN.
        model_assets_release = fetch_model_assets_release()
        detector_zip_path, detector_zip_payload = cached_release_asset(
            model_assets_release, DETECTOR_ASSET_ID, cache
        )
        classifier_zip_path, classifier_zip_payload = cached_release_asset(
            model_assets_release, CLASSIFIER_BUNDLE_ASSET_ID, cache
        )
        latest_model_assets_release = fetch_model_assets_release()
        if _model_assets_fingerprint(model_assets_release) != _model_assets_fingerprint(latest_model_assets_release):
            raise BootstrapError("pinned model-assets Release changed while this build was downloading")

        build_sha = os.environ.get("BUILD_SHA") or os.environ.get("GITHUB_SHA")
        detector_snapshot = install_detector_zip(
            detector_zip_payload, MAIN_ASSETS, model_assets_release, build_sha
        )
        classifier_parity = verify_classifier_bundle_parity(
            classifier_zip_payload, classifier_snapshot, model_assets_release
        )
        _write_asset(
            MAIN_ASSETS / ANDROID_CLASSIFIER_PARITY_FILE,
            json.dumps(classifier_parity, ensure_ascii=False, indent=2, sort_keys=True).encode("utf-8") + b"\n",
        )
        model_package_manifest = build_android_model_package_manifest(
            detector_snapshot, classifier_snapshot, classifier_parity, build_sha
        )
        _write_asset(
            MAIN_ASSETS / ANDROID_MODEL_PACKAGE_MANIFEST_FILE,
            json.dumps(model_package_manifest, ensure_ascii=False, indent=2, sort_keys=True).encode("utf-8") + b"\n",
        )

        # These are hard gates: production classifier release + source ZIP parity,
        # detector ZIP/ONNX graph + APK-ready model snapshots.
        run_verifier("verify_production_model.py")
        run_verifier("verify_production_detector.py")
        print("BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_PASS")
        print("CLASSIFIER_MODEL_SNAPSHOT=" + json.dumps(snapshot_summary(classifier_snapshot), ensure_ascii=False, sort_keys=True))
        print("DETECTOR_MODEL_SNAPSHOT=" + json.dumps(detector_snapshot_summary(detector_snapshot), ensure_ascii=False, sort_keys=True))
        print("CLASSIFIER_BUNDLE_PARITY=" + json.dumps(classifier_parity, ensure_ascii=False, sort_keys=True))
        print("ANDROID_MODEL_PACKAGE_MANIFEST=" + json.dumps(model_package_manifest, ensure_ascii=False, sort_keys=True))
        print("DETECTOR_ZIP_CACHE=" + str(detector_zip_path))
        print("CLASSIFIER_ZIP_CACHE=" + str(classifier_zip_path))
    finally:
        MAIN_ASSETS = original_main_assets


def main() -> int:
    try:
        bootstrap()
    except (
        BootstrapError,
        ModelContractError,
        DetectorContractError,
        OSError,
        ValueError,
        KeyError,
    ) as error:
        print(f"BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
