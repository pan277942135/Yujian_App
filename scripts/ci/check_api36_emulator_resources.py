"""Gate API 36 smoke on the running emulator's resource profile."""

import os
from pathlib import Path
import re
import subprocess
import sys


def main():
    evidence_dir = Path("evidence/runtime/recognition-result-api36")
    evidence_dir.mkdir(parents=True, exist_ok=True)
    with (evidence_dir / "emulator-resources.txt").open("w", encoding="utf-8") as report:
        def record(message):
            print(message, flush=True)
            print(message, file=report, flush=True)

        try:
            serial = os.environ["ANDROID_SERIAL"]
            record(f"serial={serial}")
            hardware = Path(os.environ["ANDROID_AVD_HOME"]) / "test.avd" / "hardware-qemu.ini"
            effective = {}
            for line in hardware.read_text(encoding="utf-8").splitlines():
                key, sep, value = line.partition("=")
                if sep:
                    effective[key.strip()] = value.strip()

            expected = {"hw.cpu.ncore": "4", "hw.ramSize": "4096", "vm.heapSize": "512"}
            failures = []
            for key, value in expected.items():
                observed = effective.get(key, "MISSING")
                record(f"effective {key}={observed} expected={value}")
                if observed != value:
                    failures.append(key)

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
            if heap_size.strip().lower() != "512m":
                failures.append("guest dalvik.vm.heapsize")

            if failures:
                raise ValueError(", ".join(failures))
        except (KeyError, OSError, ValueError, subprocess.SubprocessError) as error:
            record(f"API36_EMULATOR_RESOURCES_FAIL={error}")
            return 1

        record("API36_EMULATOR_RESOURCES_PASS")
        return 0


if __name__ == "__main__":
    sys.exit(main())
