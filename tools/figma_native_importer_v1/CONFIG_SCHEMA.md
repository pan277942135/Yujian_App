# Native Importer configuration and message protocol

## Configuration, schema version 1

The builder reads `importer.config.json` from the repository root. Asset paths are repository-relative and are copied into the package without changing the source bytes.

```json
{
  "schemaVersion": 1,
  "plugin": { "id": "YUJIAN_FIGMA_NATIVE_IMPORTER_V1", "displayName": "YuJian Native Importer" },
  "document": {
    "expectedName": "Recognition Main Journey Master Board V1",
    "alternateExpectedName": "渔见 · Recognition Main Journey Master Board V1"
  },
  "page": { "expectedName": "Target page title" },
  "assets": [{
    "id": "stable.asset.id",
    "displayName": "Asset label",
    "sourceFile": "repo/path/to/original.png",
    "packageFile": "assets/original.png",
    "sourceSha256": "lowercase SHA-256 of original bytes",
    "mimeType": "image/png",
    "width": 941,
    "height": 1672,
    "targetNodeId": "41:32",
    "targetNodeTypes": ["FRAME", "SLOT"],
    "scaleMode": "FIT"
  }],
  "verificationFrame": {
    "nodeId": "41:30",
    "expectedNodeTypes": ["FRAME"],
    "width": 1080,
    "height": 1920,
    "fileName": "NH01_Figma_1080x1920.png"
  }
}
```

Valid scale modes are `FIT`, `FILL`, `CROP`, and `TILE`. V1’s NH01 configuration uses `FIT` as declared by the existing node mapping. The runtime accepts only the two exact document names shown above; it does not use prefix, substring, wildcard, or fuzzy matching. The builder rejects a configuration that changes this NH01 allowlist. A target is resolved only in an allowed document and the active named page. Nodes are never created. An empty fill list may be filled; one existing identical IMAGE/FIT fill is treated as already installed; unrelated fills cause a no-write failure.

## Figma UI ⇄ plugin main messages

Messages are plain objects over `figma.ui`’s Plugin API bridge.

| Direction | `type` | Payload |
|---|---|---|
| UI → Main | `SOURCE_BYTES` | `{ assetId, bytes: Uint8Array }`; Main computes SHA-256 and PNG dimensions |
| Main → UI | `SOURCE_VERIFIED` | `{ assetId, sourceSha256, byteLength, width, height }` |
| Main → UI | `TARGET_STATUS` | `{ ready, nodeId?, nodeType?, nodeName?, fillState?, error? }` |
| UI → Main | `IMPORT_ASSET` | `{ assetId }`; validated bytes stay in the Main process cache |
| Main → UI | `IMPORT_RESULT` | `{ ok, alreadyPresent?, nodeId?, imageHash?, scaleMode?, sourceSha256?, error? }` |
| UI → Main | `EXPORT_FRAME` | `{}` |
| Main → UI | `FRAME_EXPORT` | `{ ok, fileName?, width?, height?, bytes?: Uint8Array, error? }` |
| Main → UI | `ERROR` | `{ error }` |

The transport hash is the original source-file SHA-256. `imageHash` is a Figma-internal image-fill identifier and must be recorded as a different field.
