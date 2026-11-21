#!/usr/bin/env python3
"""Guard the Android source-module graph and the code/resource ownership split."""
from __future__ import annotations

import hashlib
import re
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
BASELINE_TEST_SOURCE_COUNT = 144
MOVED_RESOURCES = {
    "app/src/main/res/drawable/image_error_v12.xml": (
        "recognition-ui/src/main/res/drawable/image_error_v12.xml",
        "fb86a7df2aef5c48e31289e9b30f5a5bfe254feddd66fb50423834f174ff0fd6",
    ),
    "app/src/main/res/drawable-nodpi/ai_orb_camera_activated_v1.webp": (
        "recognition-ui/src/main/res/drawable-nodpi/ai_orb_camera_activated_v1.webp",
        "f8114d5d6d1b2211ce3527e849b07ed9434d8d102f88b247bac28aa2eaea870a",
    ),
    "app/src/main/res/drawable-nodpi/capture_button_main_v1.png": (
        "recognition-ui/src/main/res/drawable-nodpi/capture_button_main_v1.png",
        "501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327",
    ),
    "app/src/main/res/drawable-nodpi/processing_water_core_v1.webp": (
        "recognition-ui/src/main/res/drawable-nodpi/processing_water_core_v1.webp",
        "daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09",
    ),
}
REQUIRED_OWNERS = {
    "application-core/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt": "app/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt",
    "recognition-ui/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt": "app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt",
    "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt": "app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt",
    "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionIssueScreen.kt": "recognition-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionIssueScreen.kt",
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

current_test_paths = {
    path.as_posix() for module in MODULES
    for source_set in ("src/test", "src/androidTest")
    for path in (ROOT / module / source_set).rglob("*.kt")
}
require(len(current_test_paths) >= BASELINE_TEST_SOURCE_COUNT,
        f"Kotlin test sources were lost during module migration: baseline={BASELINE_TEST_SOURCE_COUNT}, current={len(current_test_paths)}")

for owner, retired in REQUIRED_OWNERS.items():
    require((ROOT / owner).is_file(), f"required production source is missing: {owner}")
    require(not (ROOT / retired).exists(), f"moved production source remains in App: {retired}")

require(not (ROOT / "app/src/main/java/com/yujian/ai/ui/screens/RecognitionIssueScreen.kt").exists(),
        "RecognitionIssueScreen must remain owned by :recognition-result-ui")
app_asset_contract_test = ROOT / "app/src/test/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassRuntimeContractTest.kt"
module_asset_contract_test = ROOT / "recognition-ui/src/test/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassRuntimeContractTest.kt"
require(app_asset_contract_test.is_file() and not module_asset_contract_test.exists(),
        "LakeGlass app asset contract test must stay in :app because it verifies App-owned assets")

for original, (destination, expected_sha256) in MOVED_RESOURCES.items():
    current = ROOT / destination
    require(current.is_file(), f"moved resource is missing: {destination}")
    require(not (ROOT / original).exists(), f"resource still exists in the App module: {original}")
    require(hashlib.sha256(current.read_bytes()).hexdigest() == expected_sha256,
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
    visible_modules = {consumer}
    pending_modules = list(graph[consumer])
    while pending_modules:
        dependency = pending_modules.pop()
        if dependency in visible_modules:
            continue
        visible_modules.add(dependency)
        pending_modules.extend(graph[dependency])

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
            owner_path = fqns.get(imported)
            if owner_path is not None:
                owner_module = owner_path.relative_to(ROOT).parts[0]
                require(owner_module in visible_modules,
                        f"cross-module import without Gradle dependency {imported}: :{consumer} -> :{owner_module}")

print(f"PASS: module graph is acyclic; {len(MODULES)} module boundaries, {len(MOVED_RESOURCES)} unchanged resources, {len(fqns)} production types verified")
