# NH01 Structure Report

PR #164 mapping snapshot: 77a9aabc2648bda2942575d6600b74c9fe7d3ad7 / SHA-256 0a9880d1250969d85104641686efe1b1f85c965b8d9b2f7954d387d3b72c0b6a.
Mapped top-level and component rows: 11.
Compose source symbols resolved: 13/13.

| Component ID | Figma node | Compose source | Geometry evidence | Mapping status |
|---|---|---|---|---|
| environment | 41:31 | NormalHomeContent background layer | full-window background contract | NOT_MAPPED |
| header | 41:33 | NormalHomeHeader in NormalHomeContent.kt | home.header | PASS |
| statistics | 41:37 | HomeStats.kt | home.statistics; Figma map additionally records rowHeight=116 | PASS |
| recent_header | 41:49 | RecentCatchSectionHeader in NormalHomeContent.kt | home.recent_header | PASS |
| fish_pager | 41:56 | RecentCatchPager + RecentFishCard + YuJianHeroCard | runtime pager viewport in Compose; page instance positions follow fixed 740px card + 16px gap | PASS |
| hero_selected | 41:67 | RecentFishCard → YuJianHeroCard(variant=HOME) | home.hero.frame | PASS |
| hero_neighbor_left | 41:58 | RecentCatchPager neighbor page; runtime transform varies with pager offset | DERIVED_FROM_PAGER_FIXED_PAGE_SIZE_AND_16PX_GAP; visual transform is a separate runtime state | PASS |
| hero_neighbor_right | 41:76 | RecentCatchPager neighbor page; runtime transform varies with pager offset | DERIVED_FROM_PAGER_FIXED_PAGE_SIZE_AND_16PX_GAP; visual transform is a separate runtime state | PASS |
| capture_action_group | 41:85 | CTA in NormalHomeContent.kt + HomeCameraButton.kt + YuJianCaptureButton.kt | aggregate from CTA container and current camera action frames; camera authority conflict remains | PASS |
| cta | 41:85 | NormalHomeContent.kt / normal-home-capture-cta | current Compose container fact; not visible glyph bounds | PASS |
| camera | 41:86 | HomeCameraButton.kt → YuJianCaptureButton.kt | current Compose artwork frame (source mapping); REVIEW_REQUIRED against contract | PASS |

Figma mapping is a preserved PR #164 snapshot, not live re-readback. Runtime bounds, photo rights, current Figma export, and visual parity remain separate gates.
