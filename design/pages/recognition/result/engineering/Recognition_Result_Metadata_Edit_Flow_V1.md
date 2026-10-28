# Recognition Result Metadata Edit Flow V1.1

Status: **FROZEN**

Machine-readable authority: `metadata_edit_flow_contract.json`.

All edits remain on the Result page and use the Frozen Bottom Sheet board. No
new page navigation is introduced.

## Entry points

- Length row opens a numeric sheet titled `鱼获长度`;
- Weight row opens a numeric sheet titled `鱼获重量`;
- Location row opens a place sheet titled `鱼获地点`.

## Numeric sheets

- decimal IME opens automatically;
- valid commit returns to Result;
- invalid input stays in the sheet and shows the field-specific error;
- clear is supported;
- empty remains valid because metadata is optional.

## Location sheet

- search is debounced and does not commit while typing;
- current location is an explicit action and the only location permission entry;
- recent locations are local, deduplicated, and capped at three;
- selecting a place commits immediately and returns to Result;
- clearing is supported.

Low Result may edit these fields before species selection. Those values remain
draft-only until a resolved species is selected and a real save is requested.
