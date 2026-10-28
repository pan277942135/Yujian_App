# NH01 Design Contract Inventory

Contract sources and SHA values are generated from the repository. Runtime status is kept separate from contract status.

| Contract | SHA-256 | Role |
|---|---|---|
| design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Component_Anchor_Map_V1.json | 72905e1be060f33028ded095d58a54bad21df3fc39ad5277609ce1e3c79c2350 | component anchor authority |
| design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Pixel_Targets_V1.json | e0b2d8f84b3a1f719417e8a1bff3784252b01cd375c8fb92a7a20b5db4d852f0 | pixel/ROI thresholds and responsive profile authority |
| design/system_v3/09_Android_Implementation_Contracts/Normal_Home/Normal_Home_Resource_Binding_V1.json | 959b753875d6c43aa5bc440995836e7345814e3c0737d169de4f3a79f4206209 | Android resource binding authority |
| design/pages/home/normal_home/engineering/Layout_Responsive_Contract_V1.md | 837f5aada51ad5d7de3cf9a7129235828027f27d8f4a73159515f6e6935cb307 | responsive geometry authority |
| design/pages/home/normal_home/engineering/Quantitative_Visual_Acceptance_Contract_V1.md | 318adfb3dacde1384acacf424cb1e5f3cd41958b89f01d1fa7a74c25db91b44e | visual comparison tolerance authority |
| design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Hero_EvidenceFit_Contract_V1.md | 6579d1156e3202e84a3e79a74cc4d66707dfa9097fcb0f49c8364abf0c1f4509 | hero photo crop and evidence-fit authority |
| design/pages/home/normal_home/engineering/hero_real_photo_fixture_register_v1.json | 959b292c2d9b27403834456dcce90ef372cee9638e28e41e0ba5537f1aeaa88d | photo source and usage-rights status |

## Geometry anchors (Frozen pixel space)

| Component | Geometry | Evidence class |
|---|---|---|
| Header | 88, 104, 904 × 104 | Contract anchor |
| Statistics | 150, 304, 780 × 116 | Contract anchor |
| Recent Catch Header | 108, 494, 864 × 70 | Contract anchor |
| Selected Hero | 170, 596, 740 × 880; radius 32 | Frozen frame contract |
| CTA container | 330, 1495, 420 × 58 | Compose container fact; glyph is a separate ROI |
| Camera artwork | 435.5, 1583, 208 × 208 | Existing Compose mapping; conflicts with touch frame at y=1564 |
| Camera touch target | 430.5, 1564, 216 × 216 | Responsive contract; runtime bounds NOT_RUN |

These anchors are not API28 runtime measurements.
