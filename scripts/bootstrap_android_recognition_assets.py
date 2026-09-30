#!/usr/bin/env python3
"""Prepare and verify every recognition asset required by an Android build.

The classifier production channel is the mutable GitHub Release tag
`pan277942135/Yujian@mobile-model-v0.2`.  Every build resolves the current
published asset digests and model metadata from that Release, verifies the
bytes it downloads, and writes a provenance manifest that is packaged into the
APK.  This intentionally avoids pinning a newly published production SHA in
source code.

Environment overrides remain available for explicit test fixtures/mirrors.
The detector release is a separate immutable contract.
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


ROOT = Path(__file__).resolve().parents[1]
MAIN_ASSETS = ROOT / "app" / "src" / "main" / "assets"
TEST_ASSETS = ROOT / "app" / "src" / "androidTest" / "assets"
TEST_DETECTOR_ASSETS = TEST_ASSETS / "detector"

MOBILE_MODEL_REPOSITORY = "pan277942135/Yujian"
MOBILE_MODEL_RELEASE_TAG = "mobile-model-v0.2"
MOBILE_MODEL_RELEASE_API = (
    f"https://api.github.com/repos/{MOBILE_MODEL_REPOSITORY}/releases/tags/{MOBILE_MODEL_RELEASE_TAG}"
)
MODEL_TFLITE_ASSET = "fish_classifier_v0_2.tflite"
MODEL_METADATA_ASSET = "fish_classifier_v0_2.metadata.json"
MODEL_TENSOR_CONTRACT_ASSET = "tensor_contract.json"
MODEL_CLASS_MAP_ASSET = "class_map.json"
MODEL_CLASS_MAP_RUNTIME_ASSET = "model_class_map.json"
MODEL_PARITY_REPORT_ASSET = "parity_report.json"
MODEL_RELEASE_MANIFEST_ASSET = "model_release_manifest.json"
MODEL_PARITY_TEST_ASSET = "model_release_parity_report.json"

PRODUCTION_DEFAULTS = {
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
    model_version: str
    dataset_id: str
    release_repository: str
    release_tag: str
    release_name: str
    tflite_url: str
    tflite_sha256: str
    tflite_bytes: int
    tensor_contract_url: str
    tensor_contract_sha256: str
    class_map_url: str
    class_map_sha256: str
    class_count: int
    parity_report_url: str
    parity_report_sha256: str
    detector_bundle_url: str
    detector_bundle_sha256: str

    @classmethod
    def from_environment(cls) -> "Contract":
        model_override_keys = (
            "MODEL_TFLITE_URL",
            "MODEL_TFLITE_SHA256",
            "MODEL_TENSOR_CONTRACT_URL",
            "MODEL_TENSOR_CONTRACT_SHA256",
        )
        if any(os.environ.get(key) for key in model_override_keys):
            missing = [key for key in model_override_keys if not os.environ.get(key)]
            if missing:
                raise BootstrapError(
                    "model fixture override is incomplete; missing " + ", ".join(missing)
                )
            model = {
                "model_version": os.environ.get("MODEL_VERSION", "environment-model"),
                "dataset_id": os.environ.get("MODEL_DATASET_ID", ""),
                "release_repository": os.environ.get("MODEL_RELEASE_REPOSITORY", "environment"),
                "release_tag": os.environ.get("MODEL_RELEASE_TAG", "environment"),
                "release_name": os.environ.get("MODEL_RELEASE_NAME", "environment override"),
                "tflite_url": os.environ["MODEL_TFLITE_URL"],
                "tflite_sha256": os.environ["MODEL_TFLITE_SHA256"].lower(),
                "tflite_bytes": int(os.environ.get("MODEL_TFLITE_BYTES", "0") or "0"),
                "tensor_contract_url": os.environ["MODEL_TENSOR_CONTRACT_URL"],
                "tensor_contract_sha256": os.environ["MODEL_TENSOR_CONTRACT_SHA256"].lower(),
                "class_map_url": os.environ.get("MODEL_CLASS_MAP_URL", ""),
                "class_map_sha256": os.environ.get("MODEL_CLASS_MAP_SHA256", "").lower(),
                "class_count": int(os.environ.get("MODEL_CLASS_COUNT", "0") or "0"),
                "parity_report_url": os.environ.get("MODEL_PARITY_REPORT_URL", ""),
                "parity_report_sha256": os.environ.get("MODEL_PARITY_REPORT_SHA256", "").lower(),
            }
        else:
            model = resolve_mobile_model_release()

        return cls(
            **model,
            detector_bundle_url=os.environ.get(
                "DETECTOR_BUNDLE_URL",
                PRODUCTION_DEFAULTS["DETECTOR_BUNDLE_URL"],
            ),
            detector_bundle_sha256=os.environ.get(
                "DETECTOR_BUNDLE_SHA256",
                PRODUCTION_DEFAULTS["DETECTOR_BUNDLE_SHA256"],
            ).lower(),
        )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def verify_file_hash(path: Path, expected: str, label: str) -> None:
    if not path.is_file():
        raise BootstrapError(f"{label}: missing file {path}")
    actual = sha256(path)
    if actual != expected:
        raise BootstrapError(f"{label}: SHA-256 mismatch; expected {expected}, got {actual}")


def _request(url: str) -> urllib.request.Request:
    headers = {
        "User-Agent": "YuJian-recognition-bootstrap/2",
        "Accept": "application/vnd.github+json",
    }
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = f"Bearer {token}"
    return urllib.request.Request(url, headers=headers)


def _read_bytes_url(url: str, label: str) -> bytes:
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(_request(url), timeout=120) as response:
                payload = response.read()
            if not payload:
                raise BootstrapError(f"{label}: downloaded payload is zero bytes")
            return payload
        except Exception as error:
            last_error = error
            if attempt < 2:
                time.sleep(2 ** attempt)
    raise BootstrapError(f"{label}: download failed from {url}: {last_error}")


def _read_json_url(url: str, label: str) -> dict:
    payload = _read_bytes_url(url, label)
    try:
        value = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BootstrapError(f"{label}: invalid JSON: {error}") from error
    if not isinstance(value, dict):
        raise BootstrapError(f"{label}: expected JSON object")
    return value


def _asset_digest(asset: dict, label: str) -> str:
    digest = str(asset.get("digest") or "")
    if not digest.startswith("sha256:"):
        raise BootstrapError(f"{label}: GitHub Release asset is missing a sha256 digest")
    value = digest.split(":", 1)[1].lower()
    if len(value) != 64:
        raise BootstrapError(f"{label}: invalid sha256 digest {digest}")
    return value


def resolve_mobile_model_release() -> dict:
    release = _read_json_url(MOBILE_MODEL_RELEASE_API, "mobile model release")
    if release.get("tag_name") != MOBILE_MODEL_RELEASE_TAG:
        raise BootstrapError(
            f"mobile model release tag mismatch: {release.get('tag_name')} != {MOBILE_MODEL_RELEASE_TAG}"
        )
    if release.get("draft") or release.get("prerelease"):
        raise BootstrapError("mobile model production release must not be draft/prerelease")

    assets = {str(asset.get("name")): asset for asset in release.get("assets") or []}
    required = (
        MODEL_TFLITE_ASSET,
        MODEL_METADATA_ASSET,
        MODEL_TENSOR_CONTRACT_ASSET,
        MODEL_CLASS_MAP_ASSET,
        MODEL_PARITY_REPORT_ASSET,
    )
    missing = [name for name in required if name not in assets]
    if missing:
        raise BootstrapError("mobile model release is missing assets: " + ", ".join(missing))

    tflite_asset = assets[MODEL_TFLITE_ASSET]
    metadata_asset = assets[MODEL_METADATA_ASSET]
    tensor_asset = assets[MODEL_TENSOR_CONTRACT_ASSET]
    class_map_asset = assets[MODEL_CLASS_MAP_ASSET]
    parity_asset = assets[MODEL_PARITY_REPORT_ASSET]

    tflite_sha = _asset_digest(tflite_asset, "classifier")
    metadata_sha = _asset_digest(metadata_asset, "classifier metadata")
    tensor_sha = _asset_digest(tensor_asset, "tensor contract")
    class_map_sha = _asset_digest(class_map_asset, "class map")
    parity_sha = _asset_digest(parity_asset, "parity report")

    metadata_bytes = _read_bytes_url(
        str(metadata_asset.get("browser_download_url") or ""),
        "classifier metadata",
    )
    actual_metadata_sha = sha256_bytes(metadata_bytes)
    if actual_metadata_sha != metadata_sha:
        raise BootstrapError(
            f"classifier metadata: SHA-256 mismatch; expected {metadata_sha}, got {actual_metadata_sha}"
        )
    try:
        metadata = json.loads(metadata_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BootstrapError(f"classifier metadata: invalid JSON: {error}") from error

    model_version = str(metadata.get("model_id") or "").strip()
    if not model_version:
        raise BootstrapError("classifier metadata: model_id is required")
    if str(metadata.get("published_filename") or "") != MODEL_TFLITE_ASSET:
        raise BootstrapError("classifier metadata: published_filename mismatch")
    if str(metadata.get("sha256") or "").lower() != tflite_sha:
        raise BootstrapError("classifier metadata: sha256 does not match Release asset digest")
    class_map_bytes = _read_bytes_url(
        str(class_map_asset.get("browser_download_url") or ""),
        "class map",
    )
    actual_class_map_sha = sha256_bytes(class_map_bytes)
    if actual_class_map_sha != class_map_sha:
        raise BootstrapError(
            f"class map: SHA-256 mismatch; expected {class_map_sha}, got {actual_class_map_sha}"
        )
    try:
        class_map = json.loads(class_map_bytes.decode("utf-8"))
        classes = class_map["classes"]
    except (UnicodeDecodeError, json.JSONDecodeError, KeyError, TypeError) as error:
        raise BootstrapError(f"class map: invalid JSON: {error}") from error
    if not isinstance(classes, list) or not classes:
        raise BootstrapError("class map: classes must be a non-empty list")
    ordered = sorted(classes, key=lambda item: int(item.get("class_index", -1)))
    indexes = [int(item.get("class_index", -1)) for item in ordered]
    if indexes != list(range(len(ordered))):
        raise BootstrapError(f"class map: class_index must be contiguous from zero, got {indexes}")
    class_keys = [str(item.get("species_key") or "").strip() for item in ordered]
    if any(not key for key in class_keys) or len(set(class_keys)) != len(class_keys):
        raise BootstrapError("class map: species_key values must be non-empty and unique")

    class_names = metadata.get("class_names")
    if class_names is not None:
        if not isinstance(class_names, list) or [str(value) for value in class_names] != class_keys:
            raise BootstrapError("classifier metadata: class_names do not match class_map order")
    metadata_count = metadata.get("num_classes")
    if metadata_count is not None and int(metadata_count) != len(ordered):
        raise BootstrapError("classifier metadata: num_classes does not match class_map")

    return {
        "model_version": model_version,
        "dataset_id": str(metadata.get("dataset_id") or ""),
        "release_repository": MOBILE_MODEL_REPOSITORY,
        "release_tag": MOBILE_MODEL_RELEASE_TAG,
        "release_name": str(release.get("name") or MOBILE_MODEL_RELEASE_TAG),
        "tflite_url": str(tflite_asset.get("browser_download_url") or ""),
        "tflite_sha256": tflite_sha,
        "tflite_bytes": int(tflite_asset.get("size") or 0),
        "tensor_contract_url": str(tensor_asset.get("browser_download_url") or ""),
        "tensor_contract_sha256": tensor_sha,
        "class_map_url": str(class_map_asset.get("browser_download_url") or ""),
        "class_map_sha256": class_map_sha,
        "class_count": len(ordered),
        "parity_report_url": str(parity_asset.get("browser_download_url") or ""),
        "parity_report_sha256": parity_sha,
    }


def _download(url: str, destination: Path, label: str) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.download-{os.getpid()}-{time.time_ns()}")
    try:
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                with urllib.request.urlopen(_request(url), timeout=120) as response, temporary.open("wb") as output:
                    shutil.copyfileobj(response, output)
                if temporary.stat().st_size == 0:
                    raise BootstrapError(f"{label}: downloaded file is zero bytes")
                os.replace(temporary, destination)
                return
            except Exception as error:
                last_error = error
                temporary.unlink(missing_ok=True)
                if attempt < 2:
                    time.sleep(2 ** attempt)
        raise BootstrapError(f"{label}: download failed from {url}: {last_error}")
    finally:
        temporary.unlink(missing_ok=True)


def materialize_verified_file(url: str, expected_sha: str, destination: Path, cache: Path, label: str) -> None:
    """Reuse only a hash-verified destination/cache file; otherwise redownload."""
    if not url or not expected_sha:
        raise BootstrapError(f"{label}: URL and SHA-256 are required")
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


def write_model_release_manifest(contract: Contract) -> None:
    model = MAIN_ASSETS / "fish_classifier.tflite"
    tensor = MAIN_ASSETS / "model_tensor_contract.json"
    class_map = MAIN_ASSETS / MODEL_CLASS_MAP_RUNTIME_ASSET
    if contract.tflite_bytes > 0 and model.stat().st_size != contract.tflite_bytes:
        raise BootstrapError(
            f"classifier: size mismatch; expected {contract.tflite_bytes}, got {model.stat().st_size}"
        )
    verify_file_hash(model, contract.tflite_sha256, "classifier")
    verify_file_hash(tensor, contract.tensor_contract_sha256, "tensor contract")
    verify_file_hash(class_map, contract.class_map_sha256, "class map")

    manifest = {
        "schema_version": "YUJIAN_ANDROID_MODEL_RELEASE_v1",
        "release_repository": contract.release_repository,
        "release_tag": contract.release_tag,
        "release_name": contract.release_name,
        "model_version": contract.model_version,
        "dataset_id": contract.dataset_id,
        "model_asset": MODEL_TFLITE_ASSET,
        "model_sha256": contract.tflite_sha256,
        "model_bytes": model.stat().st_size,
        "tensor_contract_asset": MODEL_TENSOR_CONTRACT_ASSET,
        "tensor_contract_sha256": contract.tensor_contract_sha256,
        "class_map_asset": MODEL_CLASS_MAP_ASSET,
        "class_map_sha256": contract.class_map_sha256,
        "class_count": contract.class_count,
        "parity_report_asset": MODEL_PARITY_REPORT_ASSET,
        "parity_report_sha256": contract.parity_report_sha256,
    }
    destination = MAIN_ASSETS / MODEL_RELEASE_MANIFEST_ASSET
    destination.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        "classifier release: "
        f"{contract.model_version} dataset={contract.dataset_id or '-'} "
        f"sha256={contract.tflite_sha256}"
    )


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
    expected_stems = {Path(name).stem for name in golden}
    if not all(case in expected_stems for case in REQUIRED_GOLDEN_CASES):
        raise BootstrapError(f"detector bundle: golden/ is missing one or more required cases; found {golden}")
    return members


def _safe_extract_member(archive: zipfile.ZipFile, member: str, destination: Path) -> None:
    target = destination.resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(f".{target.name}.install-{time.time_ns()}")
    with archive.open(member) as source, temporary.open("wb") as output:
        shutil.copyfileobj(source, output)
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
    global MAIN_ASSETS, TEST_ASSETS, TEST_DETECTOR_ASSETS
    original_main_assets = MAIN_ASSETS
    original_test_assets = TEST_ASSETS
    original_test_detector_assets = TEST_DETECTOR_ASSETS
    try:
        MAIN_ASSETS = root / "app" / "src" / "main" / "assets"
        TEST_ASSETS = root / "app" / "src" / "androidTest" / "assets"
        TEST_DETECTOR_ASSETS = TEST_ASSETS / "detector"
        contract = contract or Contract.from_environment()
        cache = cache or Path(
            os.environ.get(
                "YUJIAN_RECOGNITION_ASSET_CACHE",
                root / "build" / "recognition-assets-cache",
            )
        )
        cache.mkdir(parents=True, exist_ok=True)
        MAIN_ASSETS.mkdir(parents=True, exist_ok=True)
        TEST_ASSETS.mkdir(parents=True, exist_ok=True)

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
        materialize_verified_file(
            contract.class_map_url,
            contract.class_map_sha256,
            MAIN_ASSETS / MODEL_CLASS_MAP_RUNTIME_ASSET,
            cache,
            "class map",
        )
        if contract.parity_report_url and contract.parity_report_sha256:
            materialize_verified_file(
                contract.parity_report_url,
                contract.parity_report_sha256,
                TEST_ASSETS / MODEL_PARITY_TEST_ASSET,
                cache,
                "model parity report",
            )
        write_model_release_manifest(contract)

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

        run_verifier("verify_production_model.py")
        run_verifier("verify_production_detector.py")
        print("BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_PASS")
    finally:
        MAIN_ASSETS = original_main_assets
        TEST_ASSETS = original_test_assets
        TEST_DETECTOR_ASSETS = original_test_detector_assets


def main() -> int:
    try:
        bootstrap()
    except (BootstrapError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"BOOTSTRAP_ANDROID_RECOGNITION_ASSETS_FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
