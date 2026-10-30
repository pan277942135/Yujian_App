#!/usr/bin/env python3
"""Compare a live Design Manager root with the byte manifest of its deployed baseline."""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import re
import sys
from pathlib import PurePosixPath
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
from pathlib import Path


SHA1 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


def digest_map(files: dict[str, str]) -> str:
    canonical = json.dumps(files, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def fetch(url: str, timeout: float) -> tuple[int, str | None]:
    request = Request(url, headers={"User-Agent": "Yujian-Design-Manager-Root-Integrity/1.0"})
    try:
        with urlopen(request, timeout=timeout) as response:
            body = response.read()
            return response.status, hashlib.sha256(body).hexdigest()
    except (HTTPError, URLError, TimeoutError, OSError) as exc:
        return 0, f"{type(exc).__name__}: {exc}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="Published Pages root URL, ending with the repository path")
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--composition-report", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--timeout", type=float, default=30)
    args = parser.parse_args()

    report: dict[str, object] = {
        "schema": "YUJIAN_DESIGN_MANAGER_ROOT_INTEGRITY_V1",
        "status": "FAIL",
        "expected_build_sha": args.expected_sha,
        "observed_build_sha": None,
        "expected_file_count": 0,
        "checked_file_count": 0,
        "manifest_sha256": None,
        "production_pages_run_id": None,
        "production_pages_artifact_id": None,
        "production_pages_artifact_digest": None,
        "errors": [],
    }
    try:
        if not SHA1.fullmatch(args.expected_sha):
            raise ValueError("expected SHA must be a full 40-character commit SHA")
        composition = json.loads(args.composition_report.read_text(encoding="utf-8"))
        if composition.get("status") != "PASS" or composition.get("production_pages_source_sha") != args.expected_sha:
            raise ValueError("composition report does not attest a matching successful production Pages baseline")
        files = composition.get("production_root_files_sha256")
        if not isinstance(files, dict) or not files:
            raise ValueError("composition report is missing the complete production root file manifest")
        report["expected_file_count"] = len(files)
        if composition.get("production_root_files") != len(files):
            raise ValueError("production root file count does not match its manifest")
        expected_manifest_digest = composition.get("production_root_manifest_sha256")
        actual_manifest_digest = digest_map(files)
        if expected_manifest_digest != actual_manifest_digest:
            raise ValueError("production root manifest digest does not match the file map")
        for relative, digest in files.items():
            path = PurePosixPath(relative)
            if (not isinstance(relative, str) or path.is_absolute() or ".." in path.parts or "\\" in relative
                    or relative.startswith("previews/pr-234/") or not isinstance(digest, str) or not SHA256.fullmatch(digest)):
                raise ValueError(f"unsafe or malformed production root manifest entry: {relative!r}")
        root_url = args.base_url.rstrip("/") + "/"
        with urlopen(Request(root_url + "design/manager/build.json", headers={"User-Agent": "Yujian-Design-Manager-Root-Integrity/1.0"}), timeout=args.timeout) as response:
            if response.status != 200:
                raise ValueError(f"live build.json is HTTP {response.status}")
            build_json = json.loads(response.read().decode("utf-8"))
        report["observed_build_sha"] = build_json.get("sha")
        if build_json.get("sha") != args.expected_sha:
            raise ValueError(f"live root build SHA changed: {build_json.get('sha')} != {args.expected_sha}")

        def check(relative: str) -> tuple[str, str | None]:
            escaped = quote(relative, safe="/%:@")
            status_code, result = fetch(root_url + escaped, args.timeout)
            if status_code != 200 or result is None:
                return relative, f"HTTP {status_code}: {result}"
            if result != files[relative]:
                return relative, f"SHA-256 mismatch: expected {files[relative]}, observed {result}"
            return relative, None

        errors: list[str] = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, min(args.workers, 24))) as pool:
            for relative, error in pool.map(check, files):
                if error:
                    errors.append(f"{relative}: {error}")
        report.update({
            "expected_file_count": len(files),
            "checked_file_count": len(files) - len(errors),
            "manifest_sha256": actual_manifest_digest,
            "production_pages_run_id": composition.get("production_pages_run_id"),
            "production_pages_artifact_id": composition.get("production_pages_artifact_id"),
            "production_pages_artifact_digest": composition.get("production_pages_artifact_digest"),
            "errors": errors,
            "status": "PASS" if not errors else "FAIL",
        })
    except Exception as exc:  # report and fail closed for malformed evidence or network failures
        report["errors"] = [f"{type(exc).__name__}: {exc}"]

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
