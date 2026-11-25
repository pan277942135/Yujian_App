# RR06 · 内容修改

Status: **FROZEN — AUTHORITY INDEX**

## Scope

RR06 is the Design Manager entry that groups two existing frozen Recognition Result content-edit authorities. It is an authority index only: it does not introduce a new Result state, alter the 3+2 state model, duplicate canonical visual assets, or merge the two behavior contracts.

## Species Selector V1 · 鱼种选择

Status: **FROZEN**

- Visual authority: `design/pages/recognition/result/species_selector/frozen/Recognition_Result_Species_Selector_V1_Frozen.png`
- Frozen spec: `design/pages/recognition/result/species_selector/Species_Selector_Spec_V1.md`
- Frozen contract: `design/pages/recognition/result/species_selector/species_selector_contract.json`
- Manifest: `design/pages/recognition/result/species_selector/frozen/manifest.json`

Boundary: owns the shared species selector interaction and its media presentation. It does not redefine the Frozen Result page composition or the state references 05–09.

## Metadata Edit Flow V1 · 长度 / 重量 / 地点修改

Status: **FROZEN**

- Visual authority: `design/pages/recognition/result/metadata_edit/frozen/Recognition_Result_Metadata_Edit_Flow_V1_Frozen.png`
- Frozen behavior spec: `design/pages/recognition/result/engineering/Recognition_Result_Metadata_Edit_Flow_V1.md`
- Frozen contract: `design/pages/recognition/result/engineering/metadata_edit_flow_contract.json`
- Manifest: `design/pages/recognition/result/metadata_edit/frozen/manifest.json`

Boundary: owns Length, Weight and Location editing overlays, keyboard/focus, validation, clear/re-edit, and location search/current/recent interactions. The board does not authorize underlying Result page composition, confidence badges, legacy Result CTA, or legacy Metadata List.

## Authority rule

Each authority keeps its own canonical PNG, spec, machine-readable contract and manifest. RR06 links to those frozen files; it does not replace or combine them. Changes to either authority require its own versioned update and review.
