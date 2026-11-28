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
    "recognition-result-components": {"application-core", "recognition-domain", "recognition-runtime", "recognition-ui"},
    "recognition-result-ui": {"application-core", "recognition-domain", "recognition-runtime", "recognition-ui", "recognition-result-components"},
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
    "recognition-result-components/src/main/java/com/yujian/ai/ui/screens/RecognitionResultComponents.kt": "app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultComponents.kt",
    "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt": "app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt",
    "recognition-result-components/src/main/java/com/yujian/ai/ui/screens/RecognitionIssueScreen.kt": "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionIssueScreen.kt",
    "recognition-result-components/src/main/java/com/yujian/ai/ui/screens/RecognitionLocationPicker.kt": "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionLocationPicker.kt",
    "recognition-result-components/src/main/java/com/yujian/ai/ui/screens/RecognitionSpeciesSelectorScreen.kt": "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionSpeciesSelectorScreen.kt",
    "recognition-result-components/src/main/java/com/yujian/ai/ui/screens/RecognitionResultHandoffMilestoneEffects.kt": "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/RecognitionResultHandoffMilestoneEffects.kt",
    "recognition-ui/src/main/java/com/yujian/ai/ui/screens/BgContentSurface.kt": "recognition-result-ui/src/main/java/com/yujian/ai/ui/screens/BgContentSurface.kt",
}


violations: list[str] = []


def fail(message: str) -> None:
    violations.append(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


settings_path = ROOT / "settings.gradle.kts"
require(settings_path.is_file(), "settings.gradle.kts is missing")
settings = settings_path.read_text(encoding="utf-8") if settings_path.is_file() else ""
for module in MODULES:
    require(f'":{module}"' in settings, f"settings.gradle.kts does not include :{module}")

graph: dict[str, set[str]] = {}
for module, expected in MODULES.items():
    gradle_file = ROOT / module / "build.gradle.kts"
    require(gradle_file.is_file(), f"missing Gradle module script: {gradle_file.relative_to(ROOT)}")
    if not gradle_file.is_file():
        continue
    source = gradle_file.read_text(encoding="utf-8")
    actual = set(re.findall(r'project\(\s*":([A-Za-z0-9_-]+)"\s*\)', source))
    require(actual == expected, f":{module} project dependencies drifted: expected {sorted(expected)}, got {sorted(actual)}")
    graph[module] = actual

visiting: set[str] = set()
visited: set[str] = set()


def visit(module: str) -> None:
    if module in visiting:
        fail(f"module dependency cycle contains :{module}")
        return
    if module not in graph:
        fail(f"module dependency graph references unknown module :{module}")
        return
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
        "result auxiliary screens and milestone effects must remain owned by :recognition-result-components")
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

for module in ("application-core", "recognition-ui", "recognition-result-components", "recognition-result-ui"):
    source_root = ROOT / module / "src/main/java"
    for path in source_root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        if module == "application-core":
            require("androidx.compose" not in text, f"Compose dependency leaked into :application-core: {path.relative_to(ROOT)}")
            require("com.yujian.ai.R" not in text, f"App R reference leaked into :application-core: {path.relative_to(ROOT)}")
        if module in ("recognition-ui", "recognition-result-components", "recognition-result-ui"):
            require("import com.yujian.ai.R" not in text, f"App R reference leaked into :{module}: {path.relative_to(ROOT)}")
            require("import com.yujian.ai.BuildConfig" not in text, f"App BuildConfig reference leaked into :{module}: {path.relative_to(ROOT)}")

declaration = re.compile(
    r"^(?:(?:public|internal|private|protected|expect|actual)\s+)*(?:(?:data|value|sealed|enum|annotation|fun)\s+)*(?:class|object|interface|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
fqns: dict[str, Path] = {}
internal_fqns: dict[str, str] = {}
module_sources: dict[str, list[Path]] = {}
internal_type_declaration = re.compile(
    r"^\s*internal\s+(?:(?:data|value|sealed|enum|annotation)\s+)*(?:class|object|interface|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
internal_callable_declaration = re.compile(
    r"^\s*internal\s+(?:(?:suspend|inline|operator|infix|tailrec|external|const|lateinit)\s+)*(?:fun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_.<>?]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(|(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*))",
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
        internal_names = internal_type_declaration.findall(source)
        for function_name, property_name in internal_callable_declaration.findall(source):
            internal_names.append(function_name or property_name)
        for name in internal_names:
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
        import_records = re.findall(
            r"^import\s+([A-Za-z0-9_.*]+)(?:\s+as\s+([A-Za-z0-9_]+))?\s*$",
            source,
            re.MULTILINE,
        )
        imported_symbols = {alias or imported.rsplit(".", 1)[-1] for imported, alias in import_records}
        source_without_imports = re.sub(r"^import\s+[^\n]*$", "", source, flags=re.MULTILINE)
        package_match = re.search(r"^package\s+([A-Za-z0-9_.]+)", source, re.MULTILINE)
        if package_match:
            consumer_package = package_match.group(1)
            for internal_fqn, owner in internal_fqns.items():
                internal_package, symbol = internal_fqn.rsplit(".", 1)
                if owner != consumer and internal_package == consumer_package and symbol not in imported_symbols:
                    reference = re.search(rf"\b{re.escape(symbol)}\b", source_without_imports)
                    if reference:
                        line = source_without_imports.count("\n", 0, reference.start()) + 1
                        fail(f"cross-module same-package internal API reference {internal_fqn}: :{consumer} -> :{owner} at {path.relative_to(ROOT)}:{line}")
        for imported, alias in import_records:
            if imported.endswith(".*"):
                imported_package = imported[:-2]
                for internal_fqn, owner in internal_fqns.items():
                    internal_package, symbol = internal_fqn.rsplit(".", 1)
                    if owner != consumer and internal_package == imported_package:
                        if re.search(rf"\b{re.escape(symbol)}\b", source_without_imports):
                            reference = re.search(rf"\b{re.escape(symbol)}\b", source_without_imports)
                            line = source_without_imports.count("\n", 0, reference.start()) + 1
                            fail(f"cross-module wildcard import references internal API {internal_fqn}: :{consumer} -> :{owner} at {path.relative_to(ROOT)}:{line}")
                continue

            owner = internal_fqns.get(imported)
            import_match = re.search(rf"^import\s+{re.escape(imported)}", source, re.MULTILINE)
            import_line = source.count("\n", 0, import_match.start()) + 1 if import_match else 1
            require(owner is None or owner == consumer,
                    f"cross-module internal API import {imported}: :{consumer} -> :{owner} at {path.relative_to(ROOT)}:{import_line}")
            owner_path = fqns.get(imported)
            if owner_path is not None:
                owner_module = owner_path.relative_to(ROOT).parts[0]
                require(owner_module in visible_modules,
                        f"cross-module import without Gradle dependency {imported}: :{consumer} -> :{owner_module} at {path.relative_to(ROOT)}:{import_line}")

        for internal_fqn, owner in internal_fqns.items():
            if owner != consumer:
                reference = re.search(
                    rf"\b{re.escape(internal_fqn)}\b",
                    source_without_imports,
                )
                if reference:
                    line = source_without_imports.count("\n", 0, reference.start()) + 1
                    fail(f"cross-module fully-qualified internal API reference {internal_fqn}: :{consumer} -> :{owner} at {path.relative_to(ROOT)}:{line}")


# Compile-time API checks for cross-module calls that use extension/member syntax.
# These are easy to miss when auditing only explicit imports, but Kotlin enforces
# their visibility at the consuming module boundary.
session_store = (ROOT / "recognition-ui/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealSessionStore.kt").read_text(encoding="utf-8")
require(re.search(r"^fun RecognitionRevealSessionSnapshot\.matches\(", session_store, re.MULTILINE) is not None,
        "RecognitionRevealSessionSnapshot.matches must be public for :app")
remote_image = (ROOT / "recognition-ui/src/main/java/com/yujian/ai/ui/components/RemoteImage.kt").read_text(encoding="utf-8")
require(re.search(r"^\s*fun recordLoadStatus\(", remote_image, re.MULTILINE) is not None,
        "RemoteImageRuntimeProbe.recordLoadStatus must be public for :app")
guide_reference = (ROOT / "app/src/main/java/com/yujian/ai/ui/identify/RecognitionFirstCaptureGuideReferenceSpace.kt").read_text(encoding="utf-8")
guide_policy = (ROOT / "app/src/main/java/com/yujian/ai/ui/identify/RecognitionFirstCaptureGuidePolicy.kt").read_text(encoding="utf-8")
mapping_is_internal = re.search(r"^internal fun mapRecognitionGuideReferenceSpace\(", guide_reference, re.MULTILINE) is not None
guide_page_is_public = re.search(r"^public enum class RecognitionFirstCaptureGuidePage\b", guide_policy, re.MULTILINE) is not None
require(mapping_is_internal or guide_page_is_public,
        "public guide mapping must not expose internal RecognitionFirstCaptureGuidePage")

failure_test_app = ROOT / "app/src/test/java/com/yujian/ai/ui/screens/RecognitionFailurePresentationTest.kt"
failure_test_owner = ROOT / "recognition-ui/src/test/java/com/yujian/ai/ui/screens/RecognitionFailurePresentationTest.kt"
require(failure_test_owner.is_file() and not failure_test_app.exists(),
        "RecognitionFailurePresentationTest must stay with the :recognition-ui implementation")

multi_contour_test_app = ROOT / "app/src/androidTest/java/com/yujian/ai/ui/recognition/MultiContourRevealTest.kt"
multi_contour_test_owner = ROOT / "recognition-ui/src/androidTest/java/com/yujian/ai/ui/recognition/MultiContourRevealTest.kt"
require(multi_contour_test_owner.is_file() and not multi_contour_test_app.exists(),
        "MultiContourRevealTest must stay with the :recognition-ui implementation")

fish_photo_renderer_test_app = ROOT / "app/src/androidTest/java/com/yujian/ai/ui/components/FishPhotoRendererRuntimeTest.kt"
fish_photo_renderer_test_owner = ROOT / "recognition-ui/src/androidTest/java/com/yujian/ai/ui/components/FishPhotoRendererRuntimeTest.kt"
require(fish_photo_renderer_test_owner.is_file() and not fish_photo_renderer_test_app.exists(),
        "FishPhotoRendererRuntimeTest must stay with the :recognition-ui implementation because it exercises internal renderer types")

home_evidence_bitmap_test_app = ROOT / "app/src/androidTest/java/com/yujian/ai/ui/components/HomeEvidenceBitmapTest.kt"
home_evidence_bitmap_test_owner = ROOT / "recognition-ui/src/androidTest/java/com/yujian/ai/ui/components/HomeEvidenceBitmapTest.kt"
require(home_evidence_bitmap_test_owner.is_file() and not home_evidence_bitmap_test_app.exists(),
        "HomeEvidenceBitmapTest must stay with the :recognition-ui bitmap implementation")

lake_glass_agsl_test_app = ROOT / "app/src/androidTest/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassAgslRuntimeTest.kt"
lake_glass_agsl_test_owner = ROOT / "recognition-ui/src/androidTest/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassAgslRuntimeTest.kt"
require(lake_glass_agsl_test_owner.is_file() and not lake_glass_agsl_test_app.exists(),
        "LakeGlassAgslRuntimeTest must stay with the :recognition-ui LakeGlass implementation")

lake_glass_static_test_app = ROOT / "app/src/androidTest/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassStaticRuntimeTest.kt"
lake_glass_static_test_owner = ROOT / "recognition-ui/src/androidTest/java/com/yujian/ai/ui/designsystem/lakeglass/LakeGlassStaticRuntimeTest.kt"
require(lake_glass_static_test_owner.is_file() and not lake_glass_static_test_app.exists(),
        "LakeGlassStaticRuntimeTest must stay with the :recognition-ui LakeGlass implementation")

if violations:
    print(f"FAIL: {len(violations)} Android module-boundary violation(s):", file=sys.stderr)
    for index, message in enumerate(violations, start=1):
        print(f"  {index}. {message}", file=sys.stderr)
    raise SystemExit(1)

print(f"PASS: module graph is acyclic; {len(MODULES)} module boundaries, {len(MOVED_RESOURCES)} unchanged resources, {len(fqns)} production types verified")
