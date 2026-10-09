#!/usr/bin/env python3
"""Verify Normal Home V1 independent runtime assets and source wiring."""

from __future__ import annotations
import json
import hashlib
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app/src/main/assets/normal_home_runtime_v1"
MANIFEST = ASSET_ROOT / "config/runtime_manifest.json"
BACKGROUND_SOURCE = ROOT / "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png"
EMPTY_SCENE = ROOT / "app/src/main/assets/empty_home_runtime_v2/static/scene_base.webp"
DEFAULT_AVATAR = ROOT / "app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png"
DEFAULT_AVATAR_MANIFEST = ROOT / "design/pages/home/normal_home/assets/normal_home_default_avatar_v2_manifest.json"
BACKGROUND_SHA256 = "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7"
DEFAULT_AVATAR_SHA256 = "fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528"

EXPECTED = {
    "static/scene_base.png": "188593ed13b0fd6b9164aaba8632e783b8534d68",
    "camera/camera_button_base.png": "cc653b4478a1749cfa3fc74eb7b830d183db4ee4",
    "camera/camera_gold_rim_mask.png": "2d10713e4ec0a458e88396fdd162102c87239755",
    "camera/camera_breath_glow.png": "cc8ae4ae614072ed00d035eebbb65a40e8af12cf",
    "avatar/guest_avatar.png": "f58549babb41aaedb5ee2590e6d48bceda19d05a",
    "fish_card/fish_card_gradient.png": "b17776098b76ecc6ce7488fef70c6ad319d58a21",
    "fish_card/fish_card_outline.png": "6ca9464b44b5aadda2819bda74f1dc3244055815",
    "fish_card/fish_card_shadow.png": "2a782f76a7880cf957b71ec718a0d260099e5f65",
}
FROZEN_SHA = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"

def git_blob(path: Path) -> str:
    result = subprocess.run(
        ["git", "hash-object", str(path)],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()

def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def kotlin_function_body(source: str, signature: str) -> str | None:
    """Return one Kotlin function body using balanced braces, or None."""
    signature_start = source.find(signature)
    if signature_start < 0:
        return None
    body_start = source.find("{", signature_start)
    if body_start < 0:
        return None
    depth = 0
    for index in range(body_start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[body_start + 1:index]
    return None

def kotlin_block(source: str, opening_brace: int) -> tuple[str, int] | None:
    """Return a balanced Kotlin block body and the closing-brace index."""
    if opening_brace < 0 or source[opening_brace] != "{":
        return None
    depth = 0
    for index in range(opening_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening_brace + 1:index], index
    return None

def main() -> int:
    failures: list[str] = []
    try:
        data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"INVALID Normal Home runtime manifest: {exc}", file=sys.stderr)
        return 1

    if data.get("asset_root") != "normal_home_runtime_v1":
        failures.append("asset_root is not normal_home_runtime_v1")
    if data.get("asset_revision") != "NORMAL_HOME_RUNTIME_V1.1":
        failures.append("asset_revision is not NORMAL_HOME_RUNTIME_V1.1")
    if data.get("frozen_authority_sha256") != FROZEN_SHA:
        failures.append("Frozen authority SHA mismatch")

    blobs = data.get("git_blobs", {})
    for rel, expected_blob in EXPECTED.items():
        path = ASSET_ROOT / rel
        if not path.is_file():
            failures.append(f"MISSING {rel}")
            continue
        actual = git_blob(path)
        if actual != expected_blob:
            failures.append(f"BLOB_MISMATCH {rel}: {actual} != {expected_blob}")
        if blobs.get(rel) != expected_blob:
            failures.append(f"MANIFEST_BLOB_MISMATCH {rel}")

    provenance = data.get("background_provenance")
    if not isinstance(provenance, dict):
        failures.append("runtime manifest background_provenance is missing")
        provenance = {}
    expected_provenance = {
        "source_path": "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png",
        "source_sha256": BACKGROUND_SHA256,
        "source_dimensions": [941, 1672],
        "runtime_path": "app/src/main/assets/normal_home_runtime_v1/static/scene_base.png",
        "runtime_sha256": BACKGROUND_SHA256,
        "runtime_dimensions": [941, 1672],
        "transform_policy": "byte-identical copy; Android ContentScale.Crop applies centered viewport crop without changing the source bitmap",
    }
    for key, expected in expected_provenance.items():
        if provenance.get(key) != expected:
            failures.append(f"runtime background provenance mismatch: {key}")

    scene_path = ASSET_ROOT / "static/scene_base.png"
    if not BACKGROUND_SOURCE.is_file():
        failures.append("Morning_Lake_Master_V1 source is missing")
    elif sha256(BACKGROUND_SOURCE) != BACKGROUND_SHA256:
        failures.append(f"Morning_Lake_Master_V1 source SHA-256 mismatch: {sha256(BACKGROUND_SOURCE)}")
    elif Image.open(BACKGROUND_SOURCE).size != (941, 1672):
        failures.append(f"Morning_Lake_Master_V1 dimensions mismatch: {Image.open(BACKGROUND_SOURCE).size}")
    if scene_path.is_file() and sha256(scene_path) != BACKGROUND_SHA256:
        failures.append(f"Normal Home runtime background SHA-256 mismatch: {sha256(scene_path)}")
    if scene_path.is_file() and Image.open(scene_path).size != (941, 1672):
        failures.append(f"Normal Home runtime background dimensions mismatch: {Image.open(scene_path).size}")
    try:
        design_assets = json.loads(
            (ROOT / "design/pages/home/normal_home/assets/asset_manifest.json").read_text(encoding="utf-8")
        )
        design_background = design_assets.get("background", {})
        if design_background.get("source") != "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png":
            failures.append("Design Package background source is not Morning_Lake_Master_V1")
        if design_background.get("source_sha256") != BACKGROUND_SHA256:
            failures.append("Design Package background source SHA-256 mismatch")
        if design_background.get("runtime_output_sha256") != BACKGROUND_SHA256:
            failures.append("Design Package runtime background provenance SHA-256 mismatch")
    except Exception as exc:
        failures.append(f"Design Package asset manifest is invalid: {exc}")

    # Keep the old runtime asset registered and byte-verified as historical
    # provenance, while validating the V2 drawable used by the current UI.
    if "avatar/guest_avatar.png" not in data.get("avatar", []):
        failures.append("historical guest avatar provenance is missing from runtime manifest")
    if not DEFAULT_AVATAR.is_file():
        failures.append("MISSING normal_home_default_avatar_v2 drawable")
    else:
        actual_avatar_sha = sha256(DEFAULT_AVATAR)
        if actual_avatar_sha != DEFAULT_AVATAR_SHA256:
            failures.append(f"normal_home_default_avatar_v2 SHA-256 mismatch: {actual_avatar_sha}")
        try:
            with Image.open(DEFAULT_AVATAR) as avatar_image:
                avatar_image.verify()
            with Image.open(DEFAULT_AVATAR) as avatar_image:
                if avatar_image.format != "PNG" or avatar_image.mode != "RGBA":
                    failures.append(
                        f"normal_home_default_avatar_v2 format/mode mismatch: {avatar_image.format}/{avatar_image.mode}"
                    )
                if avatar_image.size != (1254, 1254):
                    failures.append(f"normal_home_default_avatar_v2 dimensions mismatch: {avatar_image.size}")
        except Exception as exc:
            failures.append(f"normal_home_default_avatar_v2 PNG is invalid: {exc}")
    try:
        avatar_manifest = json.loads(DEFAULT_AVATAR_MANIFEST.read_text(encoding="utf-8"))
        avatar_source = avatar_manifest.get("source", {})
        avatar_resource = avatar_manifest.get("android_resource", {})
        avatar_states = avatar_manifest.get("display_states", {})
        expected_avatar_states = {
            "logged_in_with_loadable_avatar": "remote_avatar",
            "logged_in_without_avatar": "normal_home_default_avatar_v2",
            "logged_in_avatar_loading": "normal_home_default_avatar_v2_placeholder",
            "logged_in_avatar_load_failed": "normal_home_default_avatar_v2",
            "guest": "normal_home_default_avatar_v2",
        }
        if avatar_manifest.get("asset_id") != "normal_home_default_avatar_v2":
            failures.append("default avatar manifest asset_id mismatch")
        if avatar_manifest.get("scope") != "normal_home_only":
            failures.append("default avatar manifest is not Normal Home scoped")
        if avatar_source.get("sha256") != DEFAULT_AVATAR_SHA256 or avatar_source.get("width_px") != 1254 or avatar_source.get("height_px") != 1254:
            failures.append("default avatar source provenance does not match the frozen PNG")
        if avatar_resource.get("path") != "app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png":
            failures.append("default avatar Android resource path mismatch")
        if avatar_resource.get("sha256") != DEFAULT_AVATAR_SHA256 or avatar_resource.get("derivative") is not False:
            failures.append("default avatar Android resource provenance mismatch")
        if avatar_states != expected_avatar_states:
            failures.append("default avatar display-state contract mismatch")
    except Exception as exc:
        failures.append(f"default avatar manifest is invalid: {exc}")

    if not EMPTY_SCENE.is_file():
        failures.append("Empty Home scene missing; cannot perform negative-control comparison")
    elif scene_path.is_file() and git_blob(scene_path) == git_blob(EMPTY_SCENE):
        failures.append("Normal Home runtime background blob equals Empty Home scene blob")

    source_files = [
        ROOT / "app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt",
        ROOT / "app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt",
        ROOT / "app/src/main/java/com/yujian/ai/ui/home/RecentFishCard.kt",
        ROOT / "app/src/main/java/com/yujian/ai/ui/home/NormalHomeRuntimeAssets.kt",
    ]
    source_by_path = {path.name: path.read_text(encoding="utf-8") for path in source_files}
    source = "\n".join(source_by_path.values())
    for forbidden in ("home_empty_v1_3", "home_normal_v1_2"):
        if forbidden in source:
            failures.append(f"LEGACY_RUNTIME_REFERENCE {forbidden}")
    for required in (
        "normal_home_runtime_v1/static/scene_base.png",
        "fish_card/fish_card_gradient.png",
        "rememberNormalHomeRuntimeAssets",
    ):
        if required not in source:
            failures.append(f"MISSING_RUNTIME_REFERENCE {required}")

    home_content = source_by_path["NormalHomeContent.kt"]
    runtime_loader = source_by_path["NormalHomeRuntimeAssets.kt"]
    avatar_contract_path = ROOT / "app/src/main/java/com/yujian/ai/ui/home/NormalHomeAvatarContract.kt"
    try:
        avatar_contract = avatar_contract_path.read_text(encoding="utf-8")
    except Exception as exc:
        failures.append(f"Normal Home avatar state contract is missing: {exc}")
        avatar_contract = ""

    header_body = kotlin_function_body(home_content, "private fun NormalHomeHeader(")
    default_avatar_body = kotlin_function_body(home_content, "private fun NormalHomeDefaultAvatar(")
    if header_body is None or default_avatar_body is None:
        failures.append("Normal Home avatar composable bodies could not be verified")
    else:
        login_branch_start = header_body.find("if (isLoggedIn)")
        login_branch_open = header_body.find("{", login_branch_start)
        login_branch = kotlin_block(header_body, login_branch_open)
        guest_branch = None
        if login_branch is not None:
            login_branch_body, login_branch_end = login_branch
            next_code = login_branch_end + 1
            while next_code < len(header_body) and header_body[next_code].isspace():
                next_code += 1
            if header_body.startswith("else", next_code):
                guest_branch_open = header_body.find("{", next_code)
                guest_branch = kotlin_block(header_body, guest_branch_open)
        if login_branch is None or guest_branch is None:
            failures.append("Normal Home logged-in and guest avatar branches could not be verified")
            logged_in_body = ""
            guest_body = ""
        else:
            logged_in_body = login_branch[0]
            guest_body = guest_branch[0]
        if "NormalHomeDefaultAvatar(Modifier.fillMaxSize())" not in logged_in_body:
            failures.append("logged-in avatar does not keep the V2 default drawable underneath remote loading")
        if "Image(" not in guest_body or "painterResource(R.drawable.normal_home_default_avatar_v2)" not in guest_body or "ContentScale.Fit" not in guest_body:
            failures.append("guest branch does not render the V2 drawable with Fit")
        if "R.drawable.normal_home_default_avatar_v2" not in default_avatar_body or "ContentScale.Fit" not in default_avatar_body:
            failures.append("logged-in default/failure avatar does not use the V2 drawable with Fit")
        remote_guard = logged_in_body.find("if (avatarState == NormalHomeAvatarState.PROFILE_LOADING ||")
        remote_image = logged_in_body.find("RemoteImage(")
        fallback = logged_in_body.find("NormalHomeDefaultAvatar(Modifier.fillMaxSize())")
        if fallback < 0 or remote_guard < fallback or remote_image < remote_guard:
            failures.append("remote avatar is not layered over the default V2 fallback behind the load-state guard")

    expected_state_routes = (
        "if (!isLoggedIn) return NormalHomeAvatarState.GUEST_DEFAULT",
        "if (avatarUrl.isNullOrBlank()) return NormalHomeAvatarState.PROFILE_DEFAULT",
        "true -> NormalHomeAvatarState.PROFILE_IMAGE",
        "false -> NormalHomeAvatarState.PROFILE_DEFAULT",
        "null -> NormalHomeAvatarState.PROFILE_LOADING",
    )
    for route in expected_state_routes:
        if route not in avatar_contract:
            failures.append(f"Normal Home avatar state route is missing: {route}")

    for path, text in (("NormalHomeContent.kt", home_content), ("NormalHomeRuntimeAssets.kt", runtime_loader)):
        if "normal_home_runtime_v1/avatar/guest_avatar.png" in text or "guestAvatar" in text:
            failures.append(f"legacy guest avatar is still wired as a runtime default in {path}")

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        print(f"FAIL: {len(failures)} Normal Home runtime closure issue(s)", file=sys.stderr)
        return 1
    print("PASS: Normal Home runtime assets are independent and source wiring is closed")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
