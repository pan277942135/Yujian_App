#!/usr/bin/env python3
"""Summarize API36 memory evidence and retain samples from failed model attempts."""

import json
import re
import sys
from datetime import datetime
from pathlib import Path

from api36_instrumentation_case import lmk_victim


MODEL_EVENTS = (
    'SLIMSAM_START', 'SLIMSAM_END', 'SLIMSAM_ENCODER_START', 'SLIMSAM_ENCODER_END',
    'SLIMSAM_DECODER_START', 'SLIMSAM_DECODER_END', 'ATTEMPT_DEADLINE_EXCEEDED',
    'SLIMSAM_RESULT_FAILED', 'NATIVE_RUN_RETURNED',
)
TERMINALS = ('SLIMSAM_END', 'ATTEMPT_DEADLINE_EXCEEDED', 'SLIMSAM_RESULT_FAILED', 'NATIVE_RUN_RETURNED')
EVENT = re.compile(r'\b(' + '|'.join(MODEL_EVENTS) + r')\b')
THREADTIME = re.compile(r'^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)\s+(\d+)\s+\d+\s')


def read(path):
    return path.read_text(encoding='utf-8', errors='replace') if path.is_file() else ''


def model_evidence(lines):
    events = {name: [] for name in MODEL_EVENTS}
    attempts = {}
    windows = []

    def close(start, terminals):
        for name in TERMINALS:
            for terminal in terminals.get(name, []):
                end_ms = terminal['uptime_ms']
                if end_ms is None:
                    # Deadline and failure markers can omit uptime. Convert their
                    # threadtime relative to the start event in the same process.
                    begin, end = THREADTIME.match(start['line']), THREADTIME.match(terminal['line'])
                    if begin and end:
                        fmt = '%Y-%m-%d %H:%M:%S.%f'
                        before = datetime.strptime('2000-' + begin[1], fmt)
                        after = datetime.strptime('2000-' + end[1], fmt)
                        if before.month == 12 and after.month == 1:
                            after = after.replace(year=2001)
                        end_ms = start['uptime_ms'] + round((after - before).total_seconds() * 1000)
                if end_ms is not None and end_ms >= start['uptime_ms']:
                    windows.append({'start_uptime_ms': start['uptime_ms'],
                                    'end_uptime_ms': end_ms, 'end_event': name})
                    return

    for line in lines:
        match = EVENT.search(line)
        if not match:
            continue
        name = match[1]
        if name == 'ATTEMPT_DEADLINE_EXCEEDED' and not re.search(r'\bstage=SLIMSAM\b', line):
            continue
        row = {'uptime_ms': number(r'\buptime_ms=(\d+)', line),
               'duration_ms': number(r'\bduration_ms=(\d+)', line), 'line': line}
        events[name].append(row)
        clock = THREADTIME.match(line)
        pid = clock[2] if clock else None
        if name == 'SLIMSAM_START' and row['uptime_ms'] is not None:
            if pid in attempts:
                close(*attempts[pid])
            attempts[pid] = (row, {})
        elif name in TERMINALS and pid in attempts:
            start, terminals = attempts[pid]
            identity = re.search(r'\battempt=(\S+)', line)
            start_identity = re.search(r'\battempt=(\S+)', start['line'])
            if not identity or not start_identity or identity[1] == start_identity[1]:
                # A failed SLIMSAM_END can follow the more specific failure marker.
                if name != 'SLIMSAM_END' or 'status=FAILED' not in line:
                    terminals.setdefault(name, []).append(row)
                else:
                    terminals.setdefault('SLIMSAM_RESULT_FAILED', []).append(row)
    for attempt in attempts.values():
        close(*attempt)
    return events, windows


def number(pattern, text):
    match = re.search(pattern, text, re.MULTILINE)
    return int(match.group(1).replace(',', '')) if match else None


def analyze(root):
    host_text = read(root / 'host-memory-snapshots.txt')
    emulator_text = read(root / 'emulator-memory-snapshots.txt')
    host_blocks = re.split(r'(?=^===== HOST_MEMORY_SNAPSHOT )', host_text, flags=re.MULTILINE)
    emulator_blocks = re.split(r'(?=^===== EMULATOR_MEMORY_SNAPSHOT )', emulator_text, flags=re.MULTILINE)
    host = []
    for block in host_blocks:
        if not block.startswith('===== HOST_MEMORY_SNAPSHOT '):
            continue
        host.append({
            'label': re.search(r'^===== HOST_MEMORY_SNAPSHOT (\S+)', block, re.MULTILINE).group(1),
            'mem_total_kb': number(r'^MemTotal:\s+(\d+)', block),
            'mem_available_kb': number(r'^MemAvailable:\s+(\d+)', block),
            'swap_total_kb': number(r'^SwapTotal:\s+(\d+)', block),
            'swap_free_kb': number(r'^SwapFree:\s+(\d+)', block),
        })
    emulator = []
    for block in emulator_blocks:
        if not block.startswith('===== EMULATOR_MEMORY_SNAPSHOT '):
            continue
        uptime = re.search(r'^([0-9]+(?:\.[0-9]+)?)\s+[0-9]+(?:\.[0-9]+)?\s*$', block, re.MULTILINE)
        emulator.append({
            'label': re.search(r'^===== EMULATOR_MEMORY_SNAPSHOT (\S+)', block, re.MULTILINE).group(1),
            'uptime_ms': round(float(uptime.group(1)) * 1000) if uptime else None,
            'mem_available_kb': number(r'^MemAvailable:\s+(\d+)', block),
            'swap_total_kb': number(r'^SwapTotal:\s+(\d+)', block),
            'swap_free_kb': number(r'^SwapFree:\s+(\d+)', block),
            'pswpin': number(r'^pswpin\s+(\d+)', block),
            'pswpout': number(r'^pswpout\s+(\d+)', block),
            'process_rss_kb': number(r'^VmRSS:\s+(\d+)', block),
            'process_rss_peak_kb': number(r'^VmHWM:\s+(\d+)', block),
            'process_swap_kb': number(r'^VmSwap:\s+(\d+)', block),
            'app_total_pss_kb': number(r'^\s*TOTAL PSS:\s*([\d,]+)', block),
            'native_heap_kb': number(r'^\s*Native Heap:\s*([\d,]+)', block),
        })
    logs = ['logcat-live.txt', 'test-cases/classifier/logcat.txt',
            'test-cases/production-reveal/logcat.txt', 'test-cases/production-auto/logcat.txt', 'logcat-pre-verdict.txt', 'logcat.txt']
    lines = list(dict.fromkeys(line.strip() for name in logs for line in read(root / name).splitlines()))
    # Dumped logs can recover lines absent from the live stream.
    lines.sort(key=lambda line: line[:18] if THREADTIME.match(line) else '')
    slimsam, windows = model_evidence(lines)
    model_window = [row for row in emulator if row['uptime_ms'] is not None and any(
        window['start_uptime_ms'] <= row['uptime_ms'] <= window['end_uptime_ms']
        for window in windows)]
    case_results = []
    for case_file in (root / 'test-cases').glob('*/result.json'):
        try:
            case_results.append(json.loads(read(case_file)))
        except (OSError, ValueError):
            pass
    app_kill = any(case.get('lmk_confirmed') is True for case in case_results)
    background_kills = [line for line in lines
                        if (victim := lmk_victim(line)) and victim != 'com.yujian.ai.uiv2']
    normal_cleanup = any(case.get('normal_process_cleanup') is True for case in case_results)
    min_host_available = min((row['mem_available_kb'] for row in host if row['mem_available_kb'] is not None), default=None)
    min_emulator_available = min((row['mem_available_kb'] for row in emulator if row['mem_available_kb'] is not None), default=None)
    peak_model = max((row['process_rss_kb'] for row in model_window if row['process_rss_kb'] is not None), default=None)
    final_rss = next((row['process_rss_kb'] for row in reversed(emulator) if row['process_rss_kb'] is not None), None)
    # A configured swap device alone is insufficient evidence of pressure.
    swap_used = max((row['swap_total_kb'] - row['swap_free_kb'] for row in emulator
                     if row['swap_total_kb'] is not None and row['swap_free_kb'] is not None), default=0)
    swap_activity = any(len({row[key] for row in emulator if row[key] is not None}) > 1
                        for key in ('pswpin', 'pswpout'))
    emulator_pressure = (
        (min_emulator_available is not None and min_emulator_available < 512 * 1024)
        or swap_used >= 64 * 1024 or swap_activity
        or any((row['process_swap_kb'] or 0) >= 64 * 1024 for row in emulator)
    )
    classifications = [name for name, observed in (
        ('APP_LMK_CONFIRMED', app_kill),
        ('BACKGROUND_LMK_PRESSURE', bool(background_kills)),
        ('EMULATOR_MEMORY_PRESSURE', emulator_pressure),
        ('HOST_MEMORY_PRESSURE_OBSERVED', min_host_available is not None and min_host_available < 512 * 1024),
        ('NORMAL_PROCESS_CLEANUP', normal_cleanup),
    ) if observed] or ['NO_MEMORY_PRESSURE']
    if peak_model is not None and final_rss is not None and peak_model > final_rss * 1.10:
        model_peak = 'TRANSIENT_RSS_PEAK_DURING_SLIMSAM_WINDOW'
    else:
        model_peak = 'NO_TRANSIENT_SLIMSAM_RSS_PEAK_CONFIRMED'
    return {
        'resource_classification': classifications[0],
        'resource_classifications': classifications,
        'background_lmk_events': background_kills,
        'emulator_peak_swap_used_kb': swap_used,
        'emulator_swap_activity_observed': swap_activity,
        'slimsam_memory_windows': windows,
        'model_peak_assessment': model_peak,
        'memory_leak_assessment': 'NOT_DIAGNOSABLE_FROM_ONE_PRODUCTION_ATTEMPT; repeated workload or heap profiling is required',
        'host_memory_min_available_kb': min_host_available,
        'emulator_memory_min_available_kb': min_emulator_available,
        'slimsam_model_events': slimsam,
        'slimsam_memory_window_samples': model_window,
        'app_rss_peak_in_slimsam_window_kb': peak_model,
        'final_app_rss_kb': final_rss,
        'lowmemorykiller_app_kill_observed': app_kill,
        'host_snapshots': host,
        'emulator_snapshots': emulator,
    }


if __name__ == '__main__':
    output, evidence = map(Path, sys.argv[1:])
    output.write_text(json.dumps(analyze(evidence), indent=2) + '\n', encoding='utf-8')
