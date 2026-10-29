# Shared Foundation → Page Contract Mapping V3.1

| Page | Shared background | Shared component IDs | Token source | Page visual asset refs |
|---|---|---|---|---|
| Recognition Capture | BG_CAPTURE · current user image is dynamic | None mapped; CAP-002 blocks public V3 component mapping | Effective Color/Geometry registry entries listed in Layout Contract | Existing capture-button master only; no camera-page PNG authority |
| Recognition Processing | BG_CAPTURE · current user photo | media.image, navigation.back | Effective Typography/Color/Geometry token IDs listed in Layout Contract | Processing PNG 01–04 with exact Asset Registry identities |
| Recognition Result | BG_CONTENT · Morning Lake master | navigation.back, navigation.top_title, action.primary_button, action.text_action, action.icon_action, surface.glass_surface, surface.card, media.hero, media.image, data.metadata_row, input.selector, input.search, input.bottom_sheet | Effective Typography/Color/Geometry token IDs listed in Layout Contract | Result PNG 05–09 plus selector and metadata edit authority PNGs |

Only the component ID/token/asset mappings inside each machine-readable Layout Contract are effective. Page-scoped visual composition is not promoted into Shared Foundation. The Asset Registry status is preserved as recorded; Frozen visual authority is determined by its page authority/manifest, not by inference from the asset filename.
