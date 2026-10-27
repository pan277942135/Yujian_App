# Page Resource Binding V1

This shared interface resolves page-owned visual resources from the existing Experience Registry, page manifests, asset registry, and background registry. It does not add a second global asset registry. Each page keeps its own `Page_Resource_Binding_V1.json`, and the schema is owned by W2. W1 may reference these bindings without copying or redefining the schema.

## Registered page bindings

| Page | Canonical page ID | Binding file | Authority version |
|---|---|---|---|
| NH01 / 有数据首页 | `home_normal_v1` | `design/pages/home/normal_home/Page_Resource_Binding_V1.json` | `2.0` (NH01 responsive contract) |
| Empty Home / 空首页 | `home_empty_v2` | `design/pages/home/empty_home/Page_Resource_Binding_V1.json` | `V2.2` |
| Login V2.1 / 登录 | `auth_login_v2` | `design/pages/account_privacy/Login/Page_Resource_Binding_V1.json` | `V2.1` |

NH01 retains `normal_home_v1` as an evidenced legacy alias from its existing resource binding. Empty Home and Login do not alias another page ID. The binding records source-authority file SHA values and compares each resource path, role metadata, lifecycle, media type, hash, and dimensions against its existing authorities and actual bytes.

## Resolve one resource

```sh
python3 scripts/design/validate_page_resource_binding.py \
  --page-id home_empty_v2 \
  --asset-role page_visual_authority_v2_2_delta \
  --authority-version V2.2 \
  --source-sha 3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec
```

The result includes the binding path and SHA, resolved `source_path`, asset identity, source authorities, verification details, lifecycle and publication permission. It returns `MISSING` for an absent role, `NOT_VERIFIED` for metadata-only resources, `DEGRADED` for optional missing resources with a recorded degradation policy, and `BLOCKED_INTEGRITY` when a source or integrity check fails. Required resources fail closed.

Python callers can use the same resolver:

```python
from scripts.design.validate_page_resource_binding import resolvePageAsset

descriptor = resolvePageAsset(
    "home_normal_v1",
    "frozen_page_visual_authority",
    "2.0",
    source_sha="6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377",
)
```

## Validate the three page bindings and the published bundle

```sh
python3 scripts/design/validate_page_resource_binding.py \
  --all-pages \
  --required-page-id home_normal_v1 \
  --required-page-id home_empty_v2 \
  --required-page-id auth_login_v2 \
  --site-root _site \
  --source-sha "$(git rev-parse HEAD)" \
  --run-id "$GITHUB_RUN_ID" \
  --report page-resource-binding-report.json
```

The Pages workflow runs this gate against the final `_site` directory immediately before `upload-pages-artifact`. Public resources must cite a marker present in the existing Pages workflow. Private, out-of-scope, and unconfirmed resources are checked by path and SHA against the actual site tree; known private reference names and photo hashes are scanned across every file in the full bundle regardless of extension. Symbolic links in the published tree fail closed so files cannot be hidden behind linked directories or files. The source SHA must match the clean checked-out commit.

## Page-owned notes

- NH01 binds the original frozen composition and M01–M05 anchors to their existing responsive and static Hi-Fi manifests. FishRecord photos and real account avatars stay metadata-only private runtime sources. The three preview images remain `ILLUSTRATIVE_ONLY`, optional, and never become real catch media.
- Empty Home binds `empty_home_v2.png`, its approved V2.2 visual delta, its sunrise page override, and the 13 entries in its page-owned asset manifest. Its recorded `REVIEW` Asset Registry statuses remain unchanged even where the page authority separately marks the visual frozen.
- Login binds `Login_V2_1_Frozen_Final.png` and its BG_CONTENT master. The original `160939.png` upload SHA is represented as missing metadata and remains separate from the repository frozen PNG SHA. Footer grass remains semantic metadata because no standalone path is registered.

Run the focused negative and cross-page tests with:

```sh
python3 -m unittest discover -s scripts/design/tests -p 'test_page_resource_binding.py' -v
```
