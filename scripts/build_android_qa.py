#!/usr/bin/env python3
"""Canonical fail-fast QA build for the integrated Android application."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import bootstrap_android_recognition_assets as bootstrap  # noqa: E402
from production_model_contract import (  # noqa: E402
    ANDROID_CLASS_MAP_FILE,
    ANDROID_METADATA_FILE,
    ANDROID_MODEL_FILE,
    ANDROID_SNAPSHOT_FILE,
    ANDROID_TENSOR_CONTRACT_FILE,
    snapshot_summary,
    verify_model_snapshot,
)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build_environment() -> dict[str, str]:
    environment = dict(os.environ)
    # Keep Windows/Work builds independent from an unwritable or stale global
    # Android/Gradle user directory. CI may still provide its own locations.
    environment.setdefault("GRADLE_USER_HOME", str(ROOT / "build" / "gradle-user-home"))
    environment.setdefault("ANDROID_USER_HOME", str(ROOT / "build" / "android-user-home"))
    environment.pop("ANDROID_SDK_HOME", None)
    environment.setdefault("ANDROID_HOME", environment.get("ANDROID_SDK_ROOT", ""))
    return environment


def run(command: list[str]) -> None:
    print("QA_BUILD_RUN:", " ".join(command))
    subprocess.run(command, cwd=ROOT, check=True, env=build_environment())


def gradle_command() -> str:
    configured = os.environ.get("YUJIAN_GRADLE_COMMAND")
    if configured:
        return configured
    return "gradle.bat" if os.name == "nt" and shutil.which("gradle.bat") else "gradle"


def gradle_task(command: str, task: str) -> list[str]:
    return [
        command,
        "-Duser.home=" + str(ROOT / "build" / "gradle-user"),
        task,
        "--stacktrace",
        "--no-daemon",
        "--max-workers=2",
        "-Pkotlin.compiler.execution.strategy=in-process",
    ]


def read_apk_entry(apk: Path, name: str) -> bytes:
    with zipfile.ZipFile(apk) as archive:
        try:
            return archive.read(name)
        except KeyError as error:
            raise RuntimeError(f"APK_ASSET_MISSING: {name}") from error


def verify_packaged_apk(apk: Path) -> dict[str, str]:
    if not apk.is_file() or apk.stat().st_size == 0:
        raise RuntimeError(f"APK_ASSET_MISSING: {apk}")
    entries = {
        "classifier": f"assets/{ANDROID_MODEL_FILE}",
        "model_metadata": f"assets/{ANDROID_METADATA_FILE}",
        "class_map": f"assets/{ANDROID_CLASS_MAP_FILE}",
        "tensor_contract": f"assets/{ANDROID_TENSOR_CONTRACT_FILE}",
        "model_snapshot": f"assets/{ANDROID_SNAPSHOT_FILE}",
        "detector": "assets/fish_detector_yolox_nano_v0_1.onnx",
        "detector_metadata": "assets/detector_metadata.json",
        "recognition_pipeline": "assets/recognition_pipeline_v1.json",
    }
    payloads = {key: read_apk_entry(apk, path) for key, path in entries.items()}
    model_snapshot = json.loads(payloads["model_snapshot"])
    model_trace = verify_model_snapshot(
        model=payloads["classifier"],
        metadata_bytes=payloads["model_metadata"],
        class_map_bytes=payloads["class_map"],
        tensor_contract_bytes=payloads["tensor_contract"],
        snapshot=model_snapshot,
    )

    metadata = json.loads(payloads["detector_metadata"])
    detector_sha = sha256_bytes(payloads["detector"])
    if metadata.get("onnx_sha256") != detector_sha:
        raise RuntimeError("APK detector metadata does not match packaged ONNX SHA")
    if int(metadata.get("onnx_bytes") or 0) != len(payloads["detector"]):
        raise RuntimeError("APK detector metadata does not match packaged ONNX size")
    json.loads(payloads["recognition_pipeline"])
    return {
        "model": snapshot_summary(model_trace),
        "model_snapshot_sha256": sha256_bytes(payloads["model_snapshot"]),
        "detector_sha256": detector_sha,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "app" / "build" / "outputs" / "apk" / "debug" / f"YuJian_Integrated_Main_QA_{date.today():%Y%m%d}.apk")
    parser.add_argument("--skip-unit-tests", action="store_true", help="Only for local iteration; CI and delivery must omit this flag.")
    args = parser.parse_args()

    try:
        bootstrap.bootstrap()
        run([sys.executable, str(ROOT / "scripts" / "verify_production_model.py")])
        run([sys.executable, str(ROOT / "scripts" / "verify_production_detector.py")])
        gradle = gradle_command()
        if not args.skip_unit_tests:
            run(gradle_task(gradle, "test"))
        run(gradle_task(gradle, ":app:assembleDebug"))
        run(gradle_task(gradle, ":app:assembleDebugAndroidTest"))

        source_apk = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
        verification = verify_packaged_apk(source_apk)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_apk, args.output)
        output_sha = hashlib.sha256(args.output.read_bytes()).hexdigest()
        print("QA_BUILD_PASS")
        print(f"source_apk={source_apk.relative_to(ROOT)}")
        print(f"output_apk={args.output}")
        print(f"output_size={args.output.stat().st_size}")
        print(f"output_sha256={output_sha}")
        print(f"packaged={verification}")
    except (subprocess.CalledProcessError, RuntimeError, OSError, bootstrap.BootstrapError) as error:
        print(f"QA_BUILD_FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
