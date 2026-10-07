#!/usr/bin/env python3
"""Static contract checks for the cross-journey Android navigation graph."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP = (ROOT / "app/src/main/java/com/yujian/ai/YujianApp.kt").read_text(encoding="utf-8")
DETAIL_ROUTE = (
    ROOT / "app/src/main/java/com/yujian/ai/ui/recorddetail/FishRecordDetailState.kt"
).read_text(encoding="utf-8")


def body_after(source: str, marker: str) -> str:
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"unclosed block after {marker!r}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


logout = body_after(APP, "fun logoutToHome()")
require('nav.navigate("home")' in logout, "logout must return to the Home route")
require('popUpTo("home") { inclusive = false }' in logout, "logout must clear nested routes above Home")

migration = body_after(APP, "fun adoptGuestArchive(")
failure = body_after(migration, ".onFailure {")
require("游客鱼获迁移未完成" in failure, "migration failure must be visible to the user")
require("原记录仍保留" in failure, "migration failure must confirm local preservation")
require("guestMigrationPending = false" not in failure, "failed migration must keep the local archive visible")

guide_link = body_after(APP, "onOpenFishGuide = { record ->")
require("record.speciesId.isNotBlank()" in guide_link, "Fish Guide route requires a species ID")
require('nav.navigate("species/${Uri.encode(record.speciesId)}")' in guide_link, "valid species ID must open Fish Guide")

for route in ('composable("home")', 'composable("auth/login")', 'route = "identify?openGallery={openGallery}"', 'composable("species/{key}"', 'composable("my")'):
    require(route in APP, f"navigation graph is missing {route}")

require('nav.navigate("catch/${Uri.encode(createdRecord.id)}?section=memory")' in APP,
        "saved catch must carry the memory section into Fish Record Detail")
require('nav.navigate("my_catches?speciesId=${Uri.encode(speciesId)}")' in APP,
        "Fish Guide species action must preserve its filter in My Catches")
require('const val FishRecordDetailRoute = "catch/{catchId}?section={section}"' in DETAIL_ROUTE,
        "Fish Record Detail route contract changed unexpectedly")

print("P13 Android journey integration static checks: PASS")
