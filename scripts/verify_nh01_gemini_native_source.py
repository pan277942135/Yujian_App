#!/usr/bin/env python3
"""Verify NH01 Gemini V3 page routing, structure, carousel, and approved image provenance."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
V2_AVATAR_SHA = "fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528"
ASSETS = {
    "app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png": V2_AVATAR_SHA,
    "app/src/main/assets/normal_home_runtime_v1/static/scene_base.png": "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7",
    "app/src/main/res/drawable-nodpi/brand_title_primary.png": "2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc",
    "app/src/main/res/drawable-nodpi/normal_home_camera_primary_v1.png": "fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2",
}
LAYERS = (
    "gemini-layer-bg-scenic-group",
    "gemini-layer-bg-mountain-mist",
    "gemini-home-top-section",
    "gemini-home-header-bar",
    "gemini-home-brand-title",
    "gemini-home-avatar-button",
    "gemini-home-stats-bar",
    "gemini-home-middle-section",
    "gemini-home-section-header",
    "gemini-home-carousel-track",
    "gemini-home-card-peek-left",
    "gemini-home-card-main-active",
    "gemini-home-card-peek-right",
    "gemini-home-footer-cta",
    "gemini-home-cta-label",
    "gemini-home-camera",
)
FORBIDDEN_NEW_PAGE_REFERENCES = (
    "NormalHomeReferenceWidth",
    "NormalHomeHeaderY",
    "NormalHomeCardY",
    "NormalHomeCtaY",
    "recordAtVirtualPage",
    "normal_home_runtime_v1/avatar/guest_avatar.png",
    "nh07_default_avatar_3x_192",
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    home_screen = (ROOT / "app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt").read_text()
    page = (ROOT / "app/src/main/java/com/yujian/ai/ui/home/GeminiNormalHomeContent.kt").read_text()
    runtime = (ROOT / "app/src/main/java/com/yujian/ai/ui/home/NormalHomeRuntimeAssets.kt").read_text()
    avatar_contract_path = ROOT / "design/pages/home/normal_home/assets/normal_home_default_avatar_v2_manifest.json"
    avatar_contract = json.loads(avatar_contract_path.read_text(encoding="utf-8"))
    gemini_contract_path = ROOT / "design/pages/home/normal_home/engineering/Gemini_Native_Implementation_Contract_V3.md"
    gemini_contract = gemini_contract_path.read_text(encoding="utf-8")
    remote_image = (ROOT / "app/src/main/java/com/yujian/ai/ui/components/RemoteImage.kt").read_text(encoding="utf-8")
    gemini_loader = runtime.split("private fun loadGeminiNormalHomeRuntimeAssets", 1)[-1].split("\n@Composable", 1)[0]

    checks: list[tuple[str, bool, str]] = []
    checks.append(("normal_home_entry_routes_to_gemini", "GeminiNormalHomeContent(" in home_screen and not re.search(r"(?<!Gemini)NormalHomeContent\s*\(", home_screen), "HomeScreen.kt"))
    checks.append(("gemini_entry_uses_gemini_runtime_assets", "rememberGeminiNormalHomeRuntimeAssets" in home_screen, "HomeScreen.kt"))
    checks.append(("new_page_contains_no_legacy_anchors", all(token not in page for token in FORBIDDEN_NEW_PAGE_REFERENCES), "GeminiNormalHomeContent.kt"))
    checks.append(("new_page_uses_registered_brand", "R.drawable.brand_title_primary" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("new_page_uses_registered_camera", "R.drawable.normal_home_camera_primary_v1" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("new_page_preserves_fish_photo_with_fit_backdrop", "preserveEvidenceWithFitBackdrop = true" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("home_hero_uses_bounded_gemini_aspect", "GeminiHomeCardAspectRatio = 247f / 293f" in page and "GeminiHomeCardMaxHeight = 400.dp" in page and "availableCardHeight" in page and "GeminiHomeMinimumReadableCardHeight" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("section_title_and_main_card_have_bounded_gap", "13f - titleBottomInset" in page and "visible title-to-card gap at 13dp" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("font_scaled_title_gap_uses_measured_text_bounds", "measuredTitleHeightPx" in page and "onTitleMeasuredHeight" in page and "measuredTitleHeightDp" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("neighbor_cards_keep_top_offset_and_height", "GeminiHomeNeighborHeightFraction = 0.667f" in page and "GeminiHomePeekTopOffset = 15.dp" in page and "translationY = if (active) 0f else neighborTranslationY" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("official_morning_lake_uses_top_center_crop", page.count("alignment = Alignment.TopCenter") == 2, "GeminiNormalHomeContent.kt"))
    checks.append(("stats_dividers_are_capped_at_26dp", "dividerHeight = 26.dp" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("gemini_page_tree_tags_present", all(tag in page for tag in LAYERS), "GeminiNormalHomeContent.kt"))
    checks.append(("gemini_runtime_bundle_excludes_guest_and_nh07_images", "guest_avatar.png" not in gemini_loader and "nh07_default_avatar_3x_192" not in gemini_loader, "loadGeminiNormalHomeRuntimeAssets"))
    checks.append(("gemini_page_uses_exact_v2_avatar_drawable", "painterResource(R.drawable.normal_home_default_avatar_v2)" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("global_avatar_contract_keeps_existing_guest_mapping", avatar_contract["display_states"].get("guest") == "normal_home_runtime_v1/avatar/guest_avatar.png", str(avatar_contract_path.relative_to(ROOT))))
    checks.append(("gemini_page_uses_finite_real_catch_pages", "val pageCount = records.size" in page and "key = { page -> records[page].id }" in page and "recordAtVirtualPage" not in page, "GeminiNormalHomeContent.kt"))
    checks.append(("gemini_page_exposes_carousel_collection_semantics", "collectionInfo = CollectionInfo(rowCount = 1, columnCount = pageCount)" in page and "stateDescription = activeId?.let" in page, "GeminiNormalHomeContent.kt"))
    checks.append(("evidence_fit_fills_bands_with_same_source_crop_then_blur", "home-evidence-backdrop-crop-fallback" in remote_image and "home-evidence-backdrop-ready" in remote_image and "contentScale = ContentScale.Fit" in remote_image and "contentScale = ContentScale.Crop" in remote_image, "RemoteImage.kt"))
    checks.append(("remote_bitmap_cache_is_bounded_and_does_not_keep_token", "MAX_BYTES = 24 * 1024 * 1024" in remote_image and "sha256Hex" in remote_image, "RemoteImage.kt"))
    checks.append(("gemini_page_contract_records_local_override_and_hash", "this NH01 Gemini V3 validation page only" in gemini_contract and V2_AVATAR_SHA in gemini_contract and "local visual-policy override" in gemini_contract, str(gemini_contract_path.relative_to(ROOT))))
    checks.append(("responsive_contract_documents_card_and_photo_policy", "247:293" in gemini_contract and "15dp below" in gemini_contract and "unconditional `centerCrop`" in gemini_contract, str(gemini_contract_path.relative_to(ROOT))))

    for relative, expected in ASSETS.items():
        actual = sha256(ROOT / relative)
        checks.append((f"asset_sha_{Path(relative).name}", actual == expected, actual))

    failures = [check for check in checks if not check[1]]
    for name, passed, detail in checks:
        print(f"{'PASS' if passed else 'FAIL'} {name}: {detail}")
    if failures:
        raise SystemExit(f"NH01_GEMINI_SOURCE_AUDIT=FAIL count={len(failures)}")
    print(f"NH01_GEMINI_SOURCE_AUDIT=PASS checks={len(checks)}")


if __name__ == "__main__":
    main()
