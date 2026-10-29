# NH01 M1.1 Authority Resolution

**Status:** `REVIEW_REQUIRED`
**Scope:** Resolve sources that affect importing and checking the NH01 first visual frame without changing Frozen authority.

## Frozen background and brand

| Node | Authority | Exact GitHub source | Source SHA-256 | Current state |
|---|---|---|---|---|
| 41:32 | NH01 environment background source | `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png` | `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7` | Import blocked by Figma browser access. Preserve node and source unchanged. |
| 41:34 | Frozen brand Artwork | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png` | `2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc` | Figma readback confirms IMAGE / FIT. Fresh export is unavailable, so visual placement is not yet accepted. |
| 38:27 | Shared Capture Button artwork source | `design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png` | `fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2` | Not imported; target is component-source slot, not a new overlay on the page. |

## Avatar conflict

Current project sources do not establish one unambiguous avatar for this Frozen sample:

- `Visual_Anchor_Asset_Resolution_V1.md` names the Home-specific V2 bitmap (`fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528`) as the current signed-in missing/loading/failure fallback.
- The NH07 README presents NH07 V1 as a local visual proposal that supersedes V2 only after full-page real-device visual review; that review is not recorded as complete.
- `Normal_Home_Resource_Binding_V1.json` points `avatar_aliases.default` to the NH07 3x export (`b79dc7eaa6da78722cedb240b35985871c422531b3d452efaa5fba9aa3eeb7c2`) and the asset registry marks it FROZEN, conflicting with the staged-supersession wording.
- The main Frozen NH01 sample shows a remote personal avatar photo, whose original asset and reuse authorization are not independently registered. It must not be cropped from the Frozen screenshot or used as an asset.
- Guest avatar is a separate runtime source and remains separate from the signed-in fallback.

Therefore node 41:35 remains unfilled and `NH01-A07_AVATAR_AUTHORITY = REVIEW_REQUIRED`. No silent substitution or new avatar authority is introduced by this handoff.

## Frozen page

`design/system/core_visual_v1/reference/normal_home_v1.png` remains the only full-page visual reference (1080×1920, SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`). It is an external comparison source only, not an imported Figma child.
