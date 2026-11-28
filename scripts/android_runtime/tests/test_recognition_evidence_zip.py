from __future__ import annotations

import hashlib
import json
import struct
import sys
import unittest
import zlib
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_recognition_evidence_zip import EvidenceZipError, REQUIRED_ENTRIES, verify_archive


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def _one_pixel_png() -> bytes:
    return b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)) + \
        _chunk(b"IDAT", zlib.compress(b"\x00\x11\x22\x33\xff")) + _chunk(b"IEND", b"")


def _package(
    root: Path,
    *,
    invalid_png: bool = False,
    terminal: str = "CRASHED",
    missing_recovery_basis: bool = False,
    duplicate_event_sequence: bool = False,
    capture_status: str | None = None,
    identity_mismatch: bool = False,
    crash_sidecar: bool = True,
) -> Path:
    identity = {"sessionIdHash": "s" * 64, "attemptIdHash": "a" * 64, "imageIdHash": "p" * 64}
    scene_key = f"{identity['sessionIdHash']}:{identity['attemptIdHash']}:{identity['imageIdHash']}"
    png = b"not-png" if invalid_png else _one_pixel_png()
    capture_status = capture_status or ("SUCCESS" if terminal == "RESULT_STABLE" else "PIXELCOPY_TIMEOUT")
    completion_event = "PIXELCOPY_SAVED" if capture_status == "SUCCESS" else capture_status
    is_recovered = terminal in {"CRASHED", "PROCESS_DIED", "INTERRUPTED"}
    recovery_basis = {
        "CRASHED": "UNCAUGHT_EXCEPTION_SIDECAR_PRESENT" if crash_sidecar else "PERSISTED_CRASH_TERMINAL_MARKER",
        "PROCESS_DIED": "PERSISTED_PROCESS_INSTANCE_CHANGED",
        "INTERRUPTED": "SAME_PROCESS_INSTANCE_WITHOUT_TERMINAL_MARKER",
    }.get(terminal)
    previous_process = "proc-a" if terminal in {"PROCESS_DIED", "INTERRUPTED"} else None
    current_process = "proc-b" if terminal == "PROCESS_DIED" else previous_process
    result_status = "AVAILABLE" if terminal == "RESULT_STABLE" and capture_status == "SUCCESS" else "NOT_REACHED"
    files = {
        name: (b"{}" if name.endswith(".json") else b"")
        for name in REQUIRED_ENTRIES if name != "manifest.json"
    }
    files.update({
        "session/session_identity.json": json.dumps(identity).encode(),
        "attempt.json": json.dumps({"terminalState": terminal, "resultEvidenceStatus": result_status, "sceneKey": scene_key}).encode(),
        "reports/result_evidence_status.json": json.dumps({"status": result_status}).encode(),
        "recovery_report.json": json.dumps({
            "recoveryStatus": "RECOVERED" if is_recovered else "NOT_RECOVERED", "terminalState": terminal,
            "classificationBasis": recovery_basis,
            "reason": "RECOVERY_CLASSIFIED_FROM_PERSISTED_TERMINAL_AND_PROCESS_METADATA",
            "previousTerminalState": "CRASHED" if terminal == "CRASHED" else "RUNNING",
            "previousProcessInstanceId": previous_process,
            "currentProcessInstanceId": current_process,
            "crashSidecarPresent": terminal == "CRASHED" and crash_sidecar,
            "resultEvidenceStatus": "NOT_REACHED",
        }).encode(),
        "crash_report.json": json.dumps({"crashStatus": "CRASHED" if terminal == "CRASHED" and crash_sidecar else "NOT_CAPTURED"}).encode(),
        "frames/stage_entry/test.png": png,
    })
    events = [
        {"schemaVersion": 2, "evidenceSequence": 1, "eventType": "PIXELCOPY_REQUESTED", "captureId": "c1",
         "sceneKey": scene_key, **identity, "photoIdentityHash": identity["imageIdHash"], "identityMatch": True,
         "sourceDomain": "AUTO"},
        {"schemaVersion": 2, "evidenceSequence": 2, "eventType": completion_event, "captureId": "c1",
         "sceneKey": scene_key, **identity, "photoIdentityHash": identity["imageIdHash"], "identityMatch": True,
         "sourceDomain": "AUTO"},
        {"schemaVersion": 2, "evidenceSequence": 3, "eventType": "EVIDENCE_ZIP_EXPORT_REQUESTED",
         "sceneKey": scene_key, **identity, "photoIdentityHash": identity["imageIdHash"], "identityMatch": True,
         "sourceDomain": "SYSTEM"},
    ]
    if duplicate_event_sequence:
        events[1]["evidenceSequence"] = 1
        files["frames/pixelcopy_manifest.jsonl"] = (json.dumps({
            "captureId": "c1", "sceneKey": scene_key,
            "sessionIdHash": identity["sessionIdHash"], "attemptIdHash": identity["attemptIdHash"],
            "imageIdHash": identity["imageIdHash"], "pixelCopyStatus": capture_status,
            "sourceDomain": "AUTO",
            "evidenceBindingStatus": "BOUND", "requestEvidenceSequence": 1, "completionEvidenceSequence": 1,
        }) + "\n").encode()
    if identity_mismatch:
        events[1]["identityMatch"] = False
    files["logs/recognition_events.jsonl"] = b"\n".join(json.dumps(row).encode() for row in events) + b"\n"
    files["frames/pixelcopy_manifest.jsonl"] = (json.dumps({
        "captureId": "c1", "sceneKey": scene_key,
        "sessionIdHash": identity["sessionIdHash"], "attemptIdHash": identity["attemptIdHash"],
        "imageIdHash": identity["imageIdHash"], "pixelCopyStatus": capture_status,
        "sourceDomain": "AUTO",
        "evidenceBindingStatus": "BOUND", "requestEvidenceSequence": 1, "completionEvidenceSequence": 2,
    }) + "\n").encode()
    if capture_status == "SUCCESS":
        files["frames/pixelcopy_manifest.jsonl"] = (json.dumps({
            "captureId": "c1", "sceneKey": scene_key,
            "sessionIdHash": identity["sessionIdHash"], "attemptIdHash": identity["attemptIdHash"],
            "imageIdHash": identity["imageIdHash"], "pixelCopyStatus": "SUCCESS",
            "sourceDomain": "AUTO",
            "captureType": "RESULT_SCENE_STABLE", "evidenceBindingStatus": "BOUND",
            "requestEvidenceSequence": 1, "completionEvidenceSequence": 2,
            "relativePath": "frames/stage_entry/test.png", "sha256": hashlib.sha256(png).hexdigest(),
        }) + "\n").encode()
    files["resource_manifest.json"] = b'{"aggregateSha256":"NOT_RECORDED"}'
    if missing_recovery_basis:
        recovery = json.loads(files["recovery_report.json"])
        recovery.pop("classificationBasis")
        files["recovery_report.json"] = json.dumps(recovery).encode()
    hashes = {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}
    manifest = {
        "schemaVersion": 2, "evidenceSchemaVersion": 2,
        "runtimeContractVersion": "yujian-runtime-contract-v1",
        "diagnosticBuildVersion": "stage2-foundation-v1", "visualSpecSha256": "NOT_FROZEN",
        "gitSha": "0" * 40, "sourceSha": "0" * 40,
        "attemptTerminalState": terminal, "fileSha256": hashes,
        "sceneKey": scene_key, "zipExportRequestEvidenceSequence": 3, "zipExportRequestStatus": "PERSISTED",
    }
    archive = root / "recognition-evidence.zip"
    with ZipFile(archive, "w") as zip_file:
        for name, data in files.items():
            zip_file.writestr(name, data)
        zip_file.writestr("manifest.json", json.dumps(manifest).encode())
    return archive


class RecognitionEvidenceZipVerifierTest(unittest.TestCase):
    def test_full_archive_hash_schema_event_identity_recovery_and_png_decode(self) -> None:
        with TemporaryDirectory() as tmp:
            report = verify_archive(_package(Path(tmp)))
        self.assertEqual("PASS", report["status"])
        self.assertEqual(3, report["eventCount"])
        self.assertEqual(1, report["decodedPngCount"])
        self.assertEqual("RECOVERED", report["recoveryStatus"])

    def test_fault_outcomes_keep_expected_terminal_state_and_result_handoff_state(self) -> None:
        cases = (
            ("RESULT_STABLE", "SUCCESS", "AVAILABLE", "NOT_RECOVERED"),
            ("RECOGNITION_FAILED", "PIXELCOPY_ERROR", "NOT_REACHED", "NOT_RECOVERED"),
            ("CANCELLED", "PIXELCOPY_TIMEOUT", "NOT_REACHED", "NOT_RECOVERED"),
            ("CRASHED", "PIXELCOPY_TIMEOUT", "NOT_REACHED", "RECOVERED"),
            ("PROCESS_DIED", "PIXELCOPY_TIMEOUT", "NOT_REACHED", "RECOVERED"),
            ("INTERRUPTED", "PIXELCOPY_TIMEOUT", "NOT_REACHED", "RECOVERED"),
        )
        with TemporaryDirectory() as tmp:
            for terminal, capture, result, recovery in cases:
                archive = _package(Path(tmp), terminal=terminal, capture_status=capture)
                report = verify_archive(archive)
                self.assertEqual(terminal, report["attemptTerminalState"])
                self.assertEqual(recovery, report["recoveryStatus"])
                with ZipFile(archive) as zip_file:
                    self.assertEqual(result, json.loads(zip_file.read("reports/result_evidence_status.json"))["status"])

    def test_rejects_png_that_has_no_decodable_chunks(self) -> None:
        with TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(EvidenceZipError, "PNG_SIGNATURE_INVALID"):
                verify_archive(_package(Path(tmp), invalid_png=True))

    def test_rejects_recovery_without_classification_basis(self) -> None:
        with TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(EvidenceZipError, "RECOVERY_CLASSIFICATION_EVIDENCE_MISSING"):
                verify_archive(_package(Path(tmp), missing_recovery_basis=True))

    def test_accepts_crash_recovery_from_persisted_crash_terminal_marker(self) -> None:
        with TemporaryDirectory() as tmp:
            report = verify_archive(_package(Path(tmp), crash_sidecar=False))
        self.assertEqual("CRASHED", report["attemptTerminalState"])
        self.assertEqual("RECOVERED", report["recoveryStatus"])

    def test_rejects_schema_mismatch(self) -> None:
        with TemporaryDirectory() as tmp:
            archive = _package(Path(tmp))
            with self.assertRaisesRegex(EvidenceZipError, "SCHEMA_VERSION_MISMATCH"):
                # Replace the manifest schema while retaining the old package hashes.
                with ZipFile(archive) as old:
                    entries = {name: old.read(name) for name in old.namelist() if name != "manifest.json"}
                    manifest = json.loads(old.read("manifest.json"))
                manifest["evidenceSchemaVersion"] = 1
                with ZipFile(archive, "w") as new:
                    for name, data in entries.items():
                        new.writestr(name, data)
                    new.writestr("manifest.json", json.dumps(manifest).encode())
                verify_archive(archive)

    def test_rejects_zip_without_persisted_export_event_reference(self) -> None:
        with TemporaryDirectory() as tmp:
            archive = _package(Path(tmp))
            with ZipFile(archive) as old:
                entries = {name: old.read(name) for name in old.namelist() if name != "manifest.json"}
                manifest = json.loads(old.read("manifest.json"))
            manifest["zipExportRequestEvidenceSequence"] = 999
            with ZipFile(archive, "w") as new:
                for name, data in entries.items():
                    new.writestr(name, data)
                new.writestr("manifest.json", json.dumps(manifest).encode())
            with self.assertRaisesRegex(EvidenceZipError, "ZIP_EXPORT_REQUEST_EVENT_MISSING"):
                verify_archive(archive)

    def test_rejects_duplicate_global_event_sequence(self) -> None:
        with TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(EvidenceZipError, "EVENT_SEQUENCE_DUPLICATE"):
                verify_archive(_package(Path(tmp), duplicate_event_sequence=True))

    def test_rejects_cross_attempt_runtime_event_even_when_file_hashes_match(self) -> None:
        with TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(EvidenceZipError, "RUNTIME_EVENT_REPORTED_CROSS_ATTEMPT_IDENTITY"):
                verify_archive(_package(Path(tmp), identity_mismatch=True))


if __name__ == "__main__":
    unittest.main()
