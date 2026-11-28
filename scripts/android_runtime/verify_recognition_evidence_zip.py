#!/usr/bin/env python3
"""Independent verifier for a schema-v2 YuJian recognition evidence ZIP."""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path
from typing import Any
from zipfile import BadZipFile, ZipFile

SCHEMA_VERSION = 2
RUNTIME_CONTRACT_VERSION = "yujian-runtime-contract-v1"
DIAGNOSTIC_BUILD_VERSION = "stage2-foundation-v1"
REQUIRED_ENTRIES = {
    "manifest.json", "session/session.json", "session/session_identity.json",
    "session/session_summary.json", "session/stage_timeline.json", "attempt.json",
    "frames/frame_index.json", "frames/pixelcopy_manifest.jsonl",
    "logs/recognition_events.jsonl", "logs/stage_timeline.jsonl",
    "logs/model_inference.jsonl", "logs/mask_runtime.jsonl", "logs/waterlight_paths.jsonl",
    "logs/result_handoff.jsonl", "logs/gpu_trace.jsonl", "logs/gl_trace.jsonl",
    "logs/surface_trace.jsonl", "reports/model_summary.json", "reports/mask_summary.json",
    "reports/geometry_summary.json", "reports/path_summary.json",
    "reports/result_evidence_status.json", "crash_report.json", "recovery_report.json",
    "device.json", "build.json", "source.json", "resource_manifest.json",
}
TERMINAL_STATES = {
    "RESULT_STABLE", "RECOGNITION_FAILED", "CANCELLED", "INTERRUPTED", "CRASHED",
    "PROCESS_DIED", "SURFACE_LOST", "UNKNOWN_TERMINATION",
}


class EvidenceZipError(ValueError):
    pass


def _fail(reason: str) -> None:
    raise EvidenceZipError(reason)


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _json(data: bytes, label: str) -> Any:
    try:
        return json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        _fail(f"INVALID_JSON:{label}:{exc}")


def _jsonl(data: bytes, label: str) -> list[dict[str, Any]]:
    rows = []
    for line_number, line in enumerate(data.splitlines(), 1):
        if not line.strip():
            continue
        value = _json(line, f"{label}:{line_number}")
        if not isinstance(value, dict):
            _fail(f"JSONL_ROW_NOT_OBJECT:{label}:{line_number}")
        rows.append(value)
    return rows


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    return a if pa <= pb and pa <= pc else b if pb <= pc else c


def _validate_png(data: bytes, label: str) -> tuple[int, int]:
    signature = b"\x89PNG\r\n\x1a\n"
    if not data.startswith(signature):
        _fail(f"PNG_SIGNATURE_INVALID:{label}")
    offset = len(signature)
    chunks: list[tuple[bytes, bytes]] = []
    seen_ihdr = seen_iend = False
    while offset < len(data):
        if offset + 12 > len(data):
            _fail(f"PNG_TRUNCATED_CHUNK:{label}")
        length = struct.unpack_from(">I", data, offset)[0]
        chunk_type = data[offset + 4:offset + 8]
        end = offset + 12 + length
        if end > len(data):
            _fail(f"PNG_TRUNCATED_CHUNK:{label}")
        chunk_data = data[offset + 8:offset + 8 + length]
        expected_crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        if zlib.crc32(chunk_type + chunk_data) & 0xFFFFFFFF != expected_crc:
            _fail(f"PNG_CRC_MISMATCH:{label}:{chunk_type.decode('ascii', 'replace')}")
        if chunk_type == b"IHDR":
            if seen_ihdr or offset != len(signature) or length != 13:
                _fail(f"PNG_IHDR_INVALID:{label}")
            seen_ihdr = True
        if chunk_type == b"IEND":
            if length != 0:
                _fail(f"PNG_IEND_INVALID:{label}")
            seen_iend = True
            if end != len(data):
                _fail(f"PNG_TRAILING_DATA:{label}")
        chunks.append((chunk_type, chunk_data))
        offset = end
        if seen_iend:
            break
    if not seen_ihdr or not seen_iend:
        _fail(f"PNG_REQUIRED_CHUNK_MISSING:{label}")

    ihdr = next(payload for kind, payload in chunks if kind == b"IHDR")
    width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(">IIBBBBB", ihdr)
    if width <= 0 or height <= 0 or width * height > 100_000_000:
        _fail(f"PNG_DIMENSIONS_INVALID:{label}")
    channels_by_type = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}
    channels = channels_by_type.get(color_type)
    valid_depths = {0: {1, 2, 4, 8, 16}, 2: {8, 16}, 3: {1, 2, 4, 8}, 4: {8, 16}, 6: {8, 16}}
    if channels is None or bit_depth not in valid_depths[color_type] or compression != 0 or filtering != 0 or interlace not in (0, 1):
        _fail(f"PNG_HEADER_UNSUPPORTED:{label}")
    try:
        compressed = b"".join(payload for kind, payload in chunks if kind == b"IDAT")
        decoded = zlib.decompress(compressed)
    except zlib.error as exc:
        _fail(f"PNG_DEFLATE_INVALID:{label}:{exc}")

    passes = [(0, 0, 1, 1)] if interlace == 0 else [
        (0, 0, 8, 8), (4, 0, 8, 8), (0, 4, 4, 8), (2, 0, 4, 4),
        (0, 2, 2, 4), (1, 0, 2, 2), (0, 1, 1, 2),
    ]
    bpp = max(1, (channels * bit_depth + 7) // 8)
    cursor = 0
    for start_x, start_y, step_x, step_y in passes:
        pass_width = max(0, (width - start_x + step_x - 1) // step_x)
        pass_height = max(0, (height - start_y + step_y - 1) // step_y)
        if not pass_width or not pass_height:
            continue
        row_bytes = (pass_width * channels * bit_depth + 7) // 8
        previous = bytearray(row_bytes)
        for _ in range(pass_height):
            if cursor + 1 + row_bytes > len(decoded):
                _fail(f"PNG_PIXEL_DATA_TRUNCATED:{label}")
            filter_type = decoded[cursor]
            cursor += 1
            scan = bytearray(decoded[cursor:cursor + row_bytes])
            cursor += row_bytes
            if filter_type > 4:
                _fail(f"PNG_FILTER_INVALID:{label}")
            for index in range(row_bytes):
                left = scan[index - bpp] if index >= bpp else 0
                above = previous[index]
                upper_left = previous[index - bpp] if index >= bpp else 0
                if filter_type == 1:
                    scan[index] = (scan[index] + left) & 0xFF
                elif filter_type == 2:
                    scan[index] = (scan[index] + above) & 0xFF
                elif filter_type == 3:
                    scan[index] = (scan[index] + ((left + above) >> 1)) & 0xFF
                elif filter_type == 4:
                    scan[index] = (scan[index] + _paeth(left, above, upper_left)) & 0xFF
            previous = scan
    if cursor != len(decoded):
        _fail(f"PNG_PIXEL_DATA_TRAILING_BYTES:{label}")
    return width, height


def verify_archive(path: Path) -> dict[str, Any]:
    try:
        zip_file = ZipFile(path)
    except (OSError, BadZipFile) as exc:
        _fail(f"ZIP_OPEN_FAILED:{exc}")
    with zip_file as archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        if len(names) != len(set(names)):
            _fail("ZIP_DUPLICATE_ENTRY")
        missing = REQUIRED_ENTRIES - set(names)
        if missing:
            _fail("ZIP_REQUIRED_ENTRY_MISSING:" + ",".join(sorted(missing)))
        contents = {name: archive.read(name) for name in names}
        manifest = _json(contents["manifest.json"], "manifest.json")
        if not isinstance(manifest, dict):
            _fail("MANIFEST_NOT_OBJECT")
        if manifest.get("evidenceSchemaVersion") != SCHEMA_VERSION or manifest.get("schemaVersion") != SCHEMA_VERSION:
            _fail("EVIDENCE_SCHEMA_VERSION_MISMATCH")
        if manifest.get("runtimeContractVersion") != RUNTIME_CONTRACT_VERSION:
            _fail("RUNTIME_CONTRACT_VERSION_MISMATCH")
        if manifest.get("diagnosticBuildVersion") != DIAGNOSTIC_BUILD_VERSION:
            _fail("DIAGNOSTIC_BUILD_VERSION_MISMATCH")
        if manifest.get("visualSpecSha256") != "NOT_FROZEN" and len(str(manifest.get("visualSpecSha256", ""))) != 64:
            _fail("VISUAL_SPEC_PROVENANCE_INVALID")
        file_hashes = manifest.get("fileSha256")
        expected_paths = set(names) - {"manifest.json"}
        if not isinstance(file_hashes, dict) or set(file_hashes) != expected_paths:
            _fail("MANIFEST_FILE_SET_MISMATCH")
        for name in expected_paths:
            if file_hashes[name] != _sha256(contents[name]):
                _fail(f"SHA256_MISMATCH:{name}")

        parsed_json: dict[str, Any] = {}
        event_rows: list[dict[str, Any]] = []
        for name, data in contents.items():
            if name.endswith(".json"):
                parsed_json[name] = _json(data, name)
            elif name.endswith(".jsonl") or ".jsonl.part_" in name:
                rows = _jsonl(data, name)
                if name.startswith("logs/"):
                    event_rows.extend(row for row in rows if "evidenceSequence" in row)

        sequences = [row["evidenceSequence"] for row in event_rows]
        if any(not isinstance(sequence, int) or sequence <= 0 for sequence in sequences):
            _fail("EVENT_SEQUENCE_INVALID")
        if len(sequences) != len(set(sequences)):
            _fail("EVENT_SEQUENCE_DUPLICATE")

        identity = parsed_json.get("session/session_identity.json", {})
        session_hash = identity.get("sessionIdHash")
        attempt_hash = identity.get("attemptIdHash")
        photo_hash = identity.get("imageIdHash")
        expected_scene_key = f"{session_hash}:{attempt_hash}:{photo_hash}"
        if not all(isinstance(value, str) and len(value) == 64 for value in (session_hash, attempt_hash, photo_hash)):
            _fail("SESSION_IDENTITY_HASH_INVALID")
        attempt_metadata = parsed_json.get("attempt.json", {})
        if attempt_metadata.get("sceneKey") != expected_scene_key:
            _fail("ATTEMPT_SCENE_KEY_MISMATCH")
        event_by_sequence = {row["evidenceSequence"]: row for row in event_rows}
        for row in event_rows:
            if row.get("sceneKey") != expected_scene_key:
                _fail("EVENT_SCENE_KEY_MISMATCH")
            if row.get("sessionIdHash") != session_hash or row.get("attemptIdHash") != attempt_hash or row.get("photoIdentityHash") != photo_hash:
                _fail("EVENT_IDENTITY_MISMATCH")
            if row.get("identityMatch") is False:
                _fail("RUNTIME_EVENT_REPORTED_CROSS_ATTEMPT_IDENTITY")
        export_sequence = manifest.get("zipExportRequestEvidenceSequence")
        if manifest.get("zipExportRequestStatus") != "PERSISTED":
            _fail("ZIP_EXPORT_REQUEST_STATUS_INVALID")
        if not isinstance(export_sequence, int) or export_sequence not in event_by_sequence:
            _fail("ZIP_EXPORT_REQUEST_EVENT_MISSING")
        export_event = event_by_sequence[export_sequence]
        if export_event.get("eventType") != "EVIDENCE_ZIP_EXPORT_REQUESTED" or export_event.get("sourceDomain") != "SYSTEM":
            _fail("ZIP_EXPORT_REQUEST_EVENT_MISMATCH")
        if export_event.get("sceneKey") != expected_scene_key:
            _fail("ZIP_EXPORT_SCENE_IDENTITY_MISMATCH")
        runtime_sequences = [
            row.get("runtimeEventSequence") for row in sorted(event_rows, key=lambda item: item["evidenceSequence"])
            if str(row.get("eventType", "")).startswith("SCENE_STAGE_") and isinstance(row.get("runtimeEventSequence"), int)
        ]
        if any(later < earlier for earlier, later in zip(runtime_sequences, runtime_sequences[1:])):
            _fail("RUNTIME_EVENT_SEQUENCE_REGRESSION")

        pixel_rows = _jsonl(contents["frames/pixelcopy_manifest.jsonl"], "frames/pixelcopy_manifest.jsonl")
        for row in pixel_rows:
            if row.get("sceneKey") != expected_scene_key:
                _fail("PIXELCOPY_SCENE_KEY_MISMATCH")
            if row.get("sessionIdHash") != session_hash or row.get("attemptIdHash") != attempt_hash or row.get("imageIdHash") != photo_hash:
                _fail("PIXELCOPY_IDENTITY_MISMATCH")
            if row.get("evidenceBindingStatus") == "BOUND":
                request_sequence = row.get("requestEvidenceSequence")
                completion_sequence = row.get("completionEvidenceSequence")
                if request_sequence not in event_by_sequence or completion_sequence not in event_by_sequence:
                    _fail("PIXELCOPY_EVENT_REFERENCE_MISSING")
                request_event = event_by_sequence[request_sequence]
                completion_event = event_by_sequence[completion_sequence]
                if request_event.get("captureId") != row.get("captureId") or request_event.get("eventType") != "PIXELCOPY_REQUESTED":
                    _fail("PIXELCOPY_REQUEST_BINDING_MISMATCH")
                if request_sequence >= completion_sequence:
                    _fail("PIXELCOPY_EVENT_SEQUENCE_ORDER_INVALID")
                if completion_event.get("captureId") != row.get("captureId"):
                    _fail("PIXELCOPY_COMPLETION_BINDING_MISMATCH")
                expected_completion_type = "PIXELCOPY_SAVED" if row.get("pixelCopyStatus") == "SUCCESS" else row.get("pixelCopyStatus")
                if completion_event.get("eventType") != expected_completion_type:
                    _fail("PIXELCOPY_COMPLETION_TYPE_MISMATCH")
                if row.get("sourceDomain") != "AUTO" or request_event.get("sourceDomain") != "AUTO":
                    _fail("AUTO_PIXELCOPY_SOURCE_DOMAIN_MISMATCH")
            else:
                _fail("PIXELCOPY_EVENT_BINDING_MISSING")
            relative_path = row.get("relativePath")
            if row.get("pixelCopyStatus") == "SUCCESS":
                if relative_path not in contents or row.get("sha256") != _sha256(contents[relative_path]):
                    _fail("PIXELCOPY_FILE_HASH_MISMATCH")

        png_dimensions = {}
        for name, data in contents.items():
            if name.lower().endswith(".png"):
                png_dimensions[name] = _validate_png(data, name)

        attempt = parsed_json["attempt.json"]
        terminal_state = attempt.get("terminalState")
        if terminal_state not in TERMINAL_STATES:
            _fail("ATTEMPT_TERMINAL_STATE_INVALID")
        recovery = parsed_json["recovery_report.json"]
        if recovery.get("recoveryStatus") == "RECOVERED":
            recovered_state = recovery.get("terminalState")
            if recovered_state not in {"CRASHED", "PROCESS_DIED", "INTERRUPTED", "SURFACE_LOST", "UNKNOWN_TERMINATION"}:
                _fail("RECOVERY_TERMINAL_STATE_INVALID")
            if not recovery.get("classificationBasis") or not recovery.get("reason"):
                _fail("RECOVERY_CLASSIFICATION_EVIDENCE_MISSING")
            basis = recovery["classificationBasis"]
            if recovered_state == "CRASHED":
                if basis == "UNCAUGHT_EXCEPTION_SIDECAR_PRESENT":
                    if recovery.get("crashSidecarPresent") is not True:
                        _fail("CRASH_CLASSIFICATION_WITHOUT_SIDECAR_EVIDENCE")
                    if parsed_json.get("crash_report.json", {}).get("crashStatus") != "CRASHED":
                        _fail("CRASH_REPORT_MISSING")
                elif basis == "PERSISTED_CRASH_TERMINAL_MARKER":
                    if recovery.get("previousTerminalState") != "CRASHED" or attempt_metadata.get("terminalState") != "CRASHED":
                        _fail("CRASH_CLASSIFICATION_WITHOUT_PERSISTED_TERMINAL_MARKER")
                else:
                    _fail("CRASH_CLASSIFICATION_WITHOUT_CRASH_BASIS")
            elif recovered_state == "PROCESS_DIED":
                previous = recovery.get("previousProcessInstanceId")
                current = recovery.get("currentProcessInstanceId")
                if basis != "PERSISTED_PROCESS_INSTANCE_CHANGED" or not previous or not current or previous == current:
                    _fail("PROCESS_DIED_CLASSIFICATION_WITHOUT_PROCESS_ID_EVIDENCE")
            elif recovered_state == "INTERRUPTED":
                previous = recovery.get("previousProcessInstanceId")
                current = recovery.get("currentProcessInstanceId")
                if basis != "SAME_PROCESS_INSTANCE_WITHOUT_TERMINAL_MARKER" or not previous or previous != current:
                    _fail("INTERRUPTED_CLASSIFICATION_WITHOUT_SAME_PROCESS_EVIDENCE")
            if recovery.get("resultEvidenceStatus") == "AVAILABLE":
                _fail("RECOVERY_MUST_NOT_INVENT_RESULT_EVIDENCE")

        result_status = parsed_json["reports/result_evidence_status.json"]
        has_stable_pixelcopy = any(
            row.get("captureType") == "RESULT_SCENE_STABLE" and row.get("pixelCopyStatus") == "SUCCESS"
            for row in pixel_rows
        )
        if result_status.get("status") == "AVAILABLE" and not has_stable_pixelcopy:
            _fail("RESULT_EVIDENCE_AVAILABLE_WITHOUT_PIXELCOPY")
        if terminal_state in {"CRASHED", "PROCESS_DIED", "INTERRUPTED", "CANCELLED", "RECOGNITION_FAILED"} and result_status.get("status") == "AVAILABLE":
            _fail("NON_SUCCESS_ATTEMPT_HAS_RESULT_EVIDENCE")
        if manifest.get("attemptTerminalState") != terminal_state:
            _fail("MANIFEST_ATTEMPT_TERMINAL_MISMATCH")
        if manifest.get("sceneKey") != expected_scene_key:
            _fail("MANIFEST_SCENE_KEY_MISMATCH")

        return {
            "status": "PASS",
            "archive": path.name,
            "archiveSha256": _sha256(path.read_bytes()),
            "archiveBytes": path.stat().st_size,
            "evidenceSchemaVersion": manifest["evidenceSchemaVersion"],
            "runtimeContractVersion": manifest["runtimeContractVersion"],
            "gitSha": manifest.get("gitSha", "NOT_RECORDED"),
            "attemptTerminalState": terminal_state,
            "eventCount": len(event_rows),
            "eventSequenceMin": min(sequences) if sequences else None,
            "eventSequenceMax": max(sequences) if sequences else None,
            "pixelCopyRows": len(pixel_rows),
            "decodedPngCount": len(png_dimensions),
            "recoveryStatus": recovery.get("recoveryStatus", "NOT_RECOVERED"),
        }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    try:
        report = verify_archive(args.archive)
    except EvidenceZipError as exc:
        print(json.dumps({"status": "FAIL", "reason": str(exc)}, indent=2), file=sys.stderr)
        return 1
    encoded = json.dumps(report, indent=2, sort_keys=True)
    if args.report:
        args.report.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
