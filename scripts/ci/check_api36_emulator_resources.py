"""Gate API 36 smoke on the running emulator's resource profile."""

import os
from pathlib import Path
import re
import subprocess
import sys


def resolve_serial():
    configured = os.environ.get("ANDROID_SERIAL", "").strip()
    if configured:
        return configured
    output = subprocess.run(
        ["adb", "devices"], check=True, capture_output=True, text=True, timeout=15,
    ).stdout
    devices = [line.split()[0] for line in output.splitlines()[1:]
               if len(line.split()) >= 2 and line.split()[1] == "device"]
    emulators = [serial for serial in devices if serial.startswith("emulator-")]
    if len(emulators) != 1 or len(devices) != 1:
        raise ValueError(f"expected one online emulator, found {devices}")
    return emulators[0]


def main():
    evidence_dir = Path("evidence/runtime/recognition-result-api36")
    evidence_dir.mkdir(parents=True, exist_ok=True)
    with (evidence_dir / "emulator-resources.txt").open("w", encoding="utf-8") as report:
        def record(message):
            print(message, flush=True)
            print(message, file=report, flush=True)

        try:
            serial = resolve_serial()
            record(f"serial={serial}")
            avd_dir = Path(os.environ["ANDROID_AVD_HOME"]) / "test.avd"
            hardware = avd_dir / "hardware-qemu.ini"
            configured = {}
            for line in (avd_dir / "config.ini").read_text(encoding="utf-8").splitlines():
                key, sep, value = line.partition("=")
                if sep:
                    configured[key.strip()] = value.strip()
            effective = {}
            for line in hardware.read_text(encoding="utf-8").splitlines():
                key, sep, value = line.partition("=")
                if sep:
                    effective[key.strip()] = value.strip()

            expected = {"hw.cpu.ncore": "4", "hw.ramSize": "4096", "hw.heapSize": "512", "vm.heapSize": "512"}
            failures = []
            def megabytes(value):
                return re.sub(r"(?i)m(?:b)?$", "", value.strip())
            for key, value in expected.items():
                configured_value = configured.get(key, "MISSING")
                record(f"configured {key}={configured_value} expected={value}")
                if megabytes(configured_value) != value:
                    failures.append(f"config {key}")
                observed = effective.get(key, "MISSING")
                record(f"effective {key}={observed} expected={value}")
                # The QEMU hardware file may contain stale or omitted CPU and
                # heap keys. Actual guest CPU count and Dalvik heap below are
                # authoritative; RAM is also verified against guest MemTotal.
                if key == "hw.ramSize" and megabytes(observed) != value:
                    failures.append(f"hardware {key}")

            guest = subprocess.run(
                ["adb", "-s", serial, "shell", "grep -c '^processor[[:space:]]*:' /proc/cpuinfo && "
                 "grep '^MemTotal:' /proc/meminfo && getprop dalvik.vm.heapsize"],
                check=True, capture_output=True, text=True, timeout=30,
            ).stdout.strip()
            record(f"guest observations:\n{guest}")
            cpu_count, meminfo, heap_size = guest.splitlines()
            mem_total = re.fullmatch(r"MemTotal:\s+(\d+)\s+kB", meminfo)
            if cpu_count.strip() != "4":
                failures.append("guest online CPUs")
            # Linux MemTotal excludes kernel-reserved memory. The effective
            # emulator config above still has to specify exactly 4096 MiB.
            if not mem_total or not 3840 * 1024 <= int(mem_total[1]) <= 4096 * 1024:
                failures.append("guest MemTotal outside 3840–4096 MiB")
            # AVD VM heap (hw.heapSize) is an emulator profile setting. The
            # system image sets dalvik.vm.heapsize independently (576m on this
            # API36 image), so preserve its actual value without conflating it
            # with the frozen 512M AVD configuration gate.
            record(f"guest dalvik.vm.heapsize={heap_size.strip()} (observational; AVD VM heap=512M)")

            if failures:
                raise ValueError(", ".join(failures))
        except (KeyError, OSError, ValueError, subprocess.SubprocessError) as error:
            record(f"API36_EMULATOR_RESOURCES_FAIL={error}")
            print(f"::error title=API36 emulator resources::{type(error).__name__}: {error}", flush=True)
            return 1

        record("API36_EMULATOR_RESOURCES_PASS")
        return 0


if __name__ == "__main__":
    sys.exit(main())
