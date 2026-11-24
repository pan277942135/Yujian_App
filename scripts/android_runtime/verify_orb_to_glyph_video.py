#!/usr/bin/env python3
"""Validate an actual Android screen recording and its attempt/KF index metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import subprocess
import sys
from pathlib import Path

REQUIRED_KFS = tuple(f"KF{i:02d}" for i in range(14, 21))


class VideoValidationError(ValueError):
    pass


def _run(command: list[str]) -> str:
    result = subprocess.run(command, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        raise VideoValidationError(result.stderr.strip() or f"COMMAND_FAILED:{command[0]}")
    return result.stdout


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _fps(value: str | None) -> float:
    if not value or value == "0/0":
        return 0.0
    numerator, separator, denominator = value.partition("/")
    try:
        result = float(numerator) / float(denominator) if separator else float(numerator)
    except (ValueError, ZeroDivisionError):
        return 0.0
    return result if math.isfinite(result) and result > 0 else 0.0


def _probe(video: Path) -> dict:
    output = _run([
        "ffprobe", "-v", "error", "-select_streams", "v:0", "-count_frames",
        "-show_entries",
        "stream=width,height,codec_name,avg_frame_rate,r_frame_rate,nb_read_frames,duration:format=duration:frame=best_effort_timestamp_time",
        "-show_frames", "-of", "json", str(video),
    ])
    try:
        data = json.loads(output)
        stream = data["streams"][0]
    except (json.JSONDecodeError, KeyError, IndexError, TypeError) as error:
        raise VideoValidationError("FFPROBE_VIDEO_STREAM_MISSING") from error
    frames = data.get("frames", [])
    pts = []
    for frame in frames:
        try:
            value = float(frame["best_effort_timestamp_time"])
        except (KeyError, TypeError, ValueError):
            continue
        if math.isfinite(value):
            pts.append(value)
    duration = _fps("0")
    try:
        duration = float(stream.get("duration") or data.get("format", {}).get("duration") or 0)
    except (TypeError, ValueError):
        duration = 0.0
    if not math.isfinite(duration) or duration <= 0:
        duration = max(pts) - min(pts) if len(pts) >= 2 else 0.0
    try:
        frame_count = int(stream.get("nb_read_frames") or len(pts))
    except (TypeError, ValueError):
        frame_count = len(pts)
    fps = _fps(stream.get("avg_frame_rate")) or _fps(stream.get("r_frame_rate"))
    return {
        "width": int(stream.get("width") or 0),
        "height": int(stream.get("height") or 0),
        "codec": str(stream.get("codec_name") or ""),
        "fps": fps,
        "durationSeconds": duration,
        "frameCount": frame_count,
        "ptsSeconds": pts,
    }


def _validate_kf_index(raw: object, duration_ms: int) -> dict[str, int]:
    if not isinstance(raw, dict):
        raise VideoValidationError("KF_INDEX_MUST_BE_OBJECT")
    result = {}
    previous = -1
    for key in REQUIRED_KFS:
        value = raw.get(key)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise VideoValidationError(f"KF_TIMESTAMP_MISSING_OR_INVALID:{key}")
        value = int(value)
        if value < 0 or value > duration_ms:
            raise VideoValidationError(f"KF_TIMESTAMP_OUTSIDE_VIDEO:{key}")
        if value <= previous:
            raise VideoValidationError(f"KF_SEQUENCE_NOT_STRICTLY_INCREASING:{key}")
        result[key] = value
        previous = value
    return result


def inspect_video(video: Path, attempt_id: str, snapshot_id: str, kf_index: object) -> dict:
    if not video.is_file() or video.stat().st_size <= 0:
        raise VideoValidationError("VIDEO_MISSING_OR_EMPTY")
    if not attempt_id.strip() or not snapshot_id.strip():
        raise VideoValidationError("ATTEMPT_OR_SNAPSHOT_ID_MISSING")
    probe = _probe(video)
    if probe["width"] <= 0 or probe["height"] <= 0 or not probe["codec"]:
        raise VideoValidationError("VIDEO_STREAM_METADATA_INVALID")
    if probe["frameCount"] <= 0 or probe["durationSeconds"] <= 0 or probe["fps"] <= 0:
        raise VideoValidationError("VIDEO_HAS_NO_DECODED_FRAMES_OR_TIMING")
    _run(["ffmpeg", "-v", "error", "-i", str(video), "-map", "0:v:0", "-f", "null", "-"])
    duration_ms = round(probe["durationSeconds"] * 1000)
    kf_timestamps = _validate_kf_index(kf_index, duration_ms)
    pts_ms = [round(value * 1000) for value in probe["ptsSeconds"]]
    expected_frames = round(probe["durationSeconds"] * probe["fps"])
    dropped_estimate = max(0, expected_frames - probe["frameCount"])
    return {
        "schemaVersion": 1,
        "source": "ANDROID_DEVICE_SCREENRECORD",
        "attemptId": attempt_id,
        "snapshotId": snapshot_id,
        "videoFile": video.name,
        "videoSha256": _sha256(video),
        "videoSizeBytes": video.stat().st_size,
        "width": probe["width"],
        "height": probe["height"],
        "codec": probe["codec"],
        "fps": probe["fps"],
        "frameCount": probe["frameCount"],
        "durationMs": duration_ms,
        "ptsMs": pts_ms,
        "droppedFramesEstimated": dropped_estimate,
        "droppedFrameMetric": "ESTIMATE_FROM_DURATION_FPS_MINUS_DECODED_FRAMES",
        "kfTimestampsMs": kf_timestamps,
        "decodeStatus": "PASS",
    }


def verify_metadata(video: Path, metadata: dict, attempt_id: str | None = None, snapshot_id: str | None = None) -> None:
    if metadata.get("source") != "ANDROID_DEVICE_SCREENRECORD":
        raise VideoValidationError("VIDEO_SOURCE_NOT_DEVICE_SCREENRECORD")
    if attempt_id is not None and metadata.get("attemptId") != attempt_id:
        raise VideoValidationError("ATTEMPT_ID_MISMATCH")
    if snapshot_id is not None and metadata.get("snapshotId") != snapshot_id:
        raise VideoValidationError("SNAPSHOT_ID_MISMATCH")
    if metadata.get("videoSha256") != _sha256(video) or metadata.get("videoSizeBytes") != video.stat().st_size:
        raise VideoValidationError("VIDEO_HASH_OR_SIZE_MISMATCH")
    _validate_kf_index(metadata.get("kfTimestampsMs"), int(metadata.get("durationMs", 0)))
    if metadata.get("decodeStatus") != "PASS" or int(metadata.get("frameCount", 0)) <= 0:
        raise VideoValidationError("VIDEO_DECODE_NOT_PASSED")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--video", required=True, type=Path)
    parser.add_argument("--attempt-id", required=True)
    parser.add_argument("--snapshot-id", required=True)
    parser.add_argument("--kf-index-json", required=True, type=Path)
    parser.add_argument("--write-metadata", type=Path)
    parser.add_argument("--metadata", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.metadata:
            metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
            verify_metadata(args.video, metadata, args.attempt_id, args.snapshot_id)
        else:
            kf_index = json.loads(args.kf_index_json.read_text(encoding="utf-8"))
            metadata = inspect_video(args.video, args.attempt_id, args.snapshot_id, kf_index)
            if args.write_metadata:
                args.write_metadata.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({key: metadata[key] for key in (
            "videoSha256", "videoSizeBytes", "attemptId", "snapshotId", "frameCount", "durationMs", "decodeStatus",
        )}, ensure_ascii=False))
        return 0
    except (OSError, json.JSONDecodeError, VideoValidationError, KeyError, TypeError, ValueError) as error:
        print(f"VIDEO_VALIDATION_FAIL={error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
