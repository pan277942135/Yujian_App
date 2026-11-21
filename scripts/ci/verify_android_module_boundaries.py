#!/usr/bin/env python3
"""Guard the Android source-module graph and the code/resource ownership split."""
from __future__ import annotations

import hashlib
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULES = {
    "recognition-domain": set(),
    "recognition-runtime": {"recognition-domain"},
    "waterlight-renderer": {"recognition-domain", "recognition-runtime"},
    "application-core": {"recognition-domain"},
    "recognition-ui": {"application-core", "recognition-domain", "recognition-runtime", "waterlight-renderer"},
    "recognition-result-ui": {"application-core", "recognition-domain", "recognition-runtime", "recognition-ui"},
    "app": {"application-core", "recognition-domain", "recognition-runtime", "waterlight-renderer", "recognition-ui", "recognition-result-ui"},
}
MOVED_RESOURCES = {
    "app/src/main/res/drawable/image_error_v12.xml": "recognition-ui/src/main/res/drawable/image_error_v12.xml",
    "app/src/main/res/drawable-nodpi/ai_orb_camera_activated_v1.webp": "recognition-ui/src/main/res/drawable-nodpi/ai_orb_camera_activated_v1.webp",
    "app/src/main/res/drawable-nodpi/capture_button_main_v1.png": "recognition-ui/src/main/res/drawable-nodpi/capture_button_main_v1.png",
    "app/src/main/res/drawable-nodpi/processing_water_core_v1.webp": "recognition-ui/src/main/res/drawable-nodpi/processing_water_core_v1.webp",
}
REQUIRED_OWNERS = {
    "application-core/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt": "app/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt",
    "recognition-ui/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt": "app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt",
    "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt": "app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt",
    "recognition-ui/src/main/java/com/yujian/ai/ui/screens/BgContentSurface.kt": "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/BgContentSurface.kt",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
for module in MODULES:
    require(f'":{module}"' in settings, f"settings.gradle.kts does not include :{module}")

graph: dict[str, set[str]] = {}
for module, expected in MODULES.items():
    gradle_file = ROOT / module / "build.gradle.kts"
    require(gradle_file.is_file(), f"missing Gradle module script: {gradle_file.relative_to(ROOT)}")
    source = gradle_file.read_text(encoding="utf-8")
    actual = set(re.findall(r'project\(\s*":([A-Za-z0-9_-]+)"\s*\)', source))
    require(actual == expected, f":{module} project dependencies drifted: expected {sorted(expected)}, got {sorted(actual)}")
    graph[module] = actual

visiting: set[str] = set()
visited: set[str] = set()


def visit(module: str) -> None:
    if module in visiting:
        fail(f"module dependency cycle contains :{module}")
    if module in visited:
        return
    visiting.add(module)
    for dependency in graph[module]:
        visit(dependency)
    visiting.remove(module)
    visited.add(module)


for name in graph:
    visit(name)

baseline_test_paths: set[str] = set()
for revision in ("HEAD", "HEAD^"):
    result = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", revision], cwd=ROOT,
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, check=False,
    )
    if result.returncode == 0:
        baseline_test_paths.update(
            path for path in result.stdout.splitlines()
            if path.endswith(".kt") and ("/src/test/" in path or "/src/androidTest/" in path)
        )
current_test_paths = {
    path.as_posix() for module in MODULES
    for source_set in ("src/test", "src/androidTest")
    for path in (ROOT / module / source_set).rglob("*.kt")
}
require(len(current_test_paths) >= len(baseline_test_paths),
        f"Kotlin test sources were lost during module migration: baseline={len(baseline_test_paths)}, current={len(current_test_paths)}")

for owner, retired in REQUIRED_OWNERS.items():
    require((ROOT / owner).is_file(), f"required production source is missing: {owner}")
    require(not (ROOT / retired).exists(), f"moved production source remains in App: {retired}")

for original, destination in MOVED_RESOURCES.items():
    current = ROOT / destination
    require(current.is_file(), f"moved resource is missing: {destination}")
    require(not (ROOT / original).exists(), f"resource still exists in the App module: {original}")
    previous_bytes = None
    for revision in ("HEAD", "HEAD^"):
        result = subprocess.run(
            ["git", "show", f"{revision}:{original}"], cwd=ROOT,
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False,
        )
        if result.returncode == 0:
            previous_bytes = result.stdout
            break
    require(previous_bytes is not None, f"cannot find original resource bytes for {original} in HEAD or HEAD^")
    require(hashlib.sha256(previous_bytes).digest() == hashlib.sha256(current.read_bytes()).digest(),
            f"resource bytes changed while moving {original}")

for module in ("application-core", "recognition-ui", "recognition-result-ui"):
    source_root = ROOT / module / "src/main/java"
    for path in source_root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        if module == "application-core":
            require("androidx.compose" not in text, f"Compose dependency leaked into :application-core: {path.relative_to(ROOT)}")
            require("com.yujian.ai.R" not in text, f"App R reference leaked into :application-core: {path.relative_to(ROOT)}")
        if module in ("recognition-ui", "recognition-result-ui"):
            require("import com.yujian.ai.R" not in text, f"App R reference leaked into :{module}: {path.relative_to(ROOT)}")
            require("import com.yujian.ai.BuildConfig" not in text, f"App BuildConfig reference leaked into :{module}: {path.relative_to(ROOT)}")

declaration = re.compile(
    r"^(?:(?:public|internal|private|protected|expect|actual)\s+)*(?:(?:data|value|sealed|enum|annotation|fun)\s+)*(?:class|object|interface|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
fqns: dict[str, Path] = {}
internal_fqns: dict[str, str] = {}
module_sources: dict[str, list[Path]] = {}
internal_declaration = re.compile(
    r"^internal\s+(?:(?:data|value|sealed|enum|annotation|fun)\s+)*(?:class|object|interface|fun|val|var|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
for module in MODULES:
    source_root = ROOT / module / "src/main/java"
    if not source_root.exists():
        continue
    module_sources[module] = []
    for path in source_root.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        module_sources[module].append(path)
        package_match = re.search(r"^package\s+([A-Za-z0-9_.]+)", source, re.MULTILINE)
        if not package_match:
            continue
        package = package_match.group(1)
        for name in declaration.findall(source):
            fqn = f"{package}.{name}"
            if fqn in fqns:
                fail(f"duplicate production type {fqn}: {fqns[fqn].relative_to(ROOT)} and {path.relative_to(ROOT)}")
            fqns[fqn] = path
        for name in internal_declaration.findall(source):
            internal_fqns[f"{package}.{name}"] = module

for consumer in MODULES:
    sources = [
        path
        for source_set in ("src/main/java", "src/test/java", "src/androidTest/java", "src/debug/java")
        for path in (ROOT / consumer / source_set).rglob("*.kt")
    ]
    for path in sources:
        source = path.read_text(encoding="utf-8")
        for imported in re.findall(r"^import\s+([A-Za-z0-9_.]+)\s*$", source, re.MULTILINE):
            owner = internal_fqns.get(imported)
            require(owner is None or owner == consumer,
                    f"cross-module internal API import {imported}: :{consumer} -> :{owner}")

print(f"PASS: module graph is acyclic; {len(MODULES)} module boundaries, {len(MOVED_RESOURCES)} unchanged resources, {len(fqns)} production types verified")
