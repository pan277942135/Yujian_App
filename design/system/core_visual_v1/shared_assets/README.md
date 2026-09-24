# Core UI V1 Shared Image Assets

Status: **NO_BITMAP_REQUIRED**

There are no extracted shared PNG assets in Core UI V1. The available image files are
full-page frozen references, not independent production layers. Cropping a lake, a card,
an icon, or a fish from a frozen page would create an incomplete and non-reusable asset.

The shared visual system is implemented with tokens, native Compose structure, and vector
icons. Environment treatments and real fish media remain page-specific or runtime data.
The authoritative decision record is in validation/shared_asset_manifest.json.
