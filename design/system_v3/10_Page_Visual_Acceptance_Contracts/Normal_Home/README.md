# Normal Home · Visual Acceptance V1

**YuJian Design System V3.1 Audit / Freeze**  
**Contract:** `normal_home_v1` · `READY_FOR_VISUAL_IMPLEMENTATION`  
**Static visual gate:** `NORMAL_HOME_VISUAL_CONTRACT_READY = PASS`  
**Runtime:** `NOT_RUN`

This package binds existing Frozen Home art and engineering rules to testable visual acceptance. It creates no replacement art and does not freeze runtime implementation.

## NH coverage areas

| Area | Covers | Primary evidence |
|---|---|---|
| NH01 | Empty Home V2.2 | Frozen artwork + measured anchor and asset contracts |
| NH02 | Normal Home data: one and multiple catches | Normal Home V1 + NH02 First Catch Frozen |
| NH03 | Hero and real-photo evidence fit | Existing Hero contract and NH04 content board |
| NH04 | Statistics, data formatting and recent catch | Normal Home Frozen + NH04 component board |
| NH05 | CTA and camera | Source raster masks + Empty Home camera anchor + shared capture asset |
| NH06 | Background inheritance | Empty Sunrise and Morning Lake authorities remain separate |
| NH07 | Avatar states | Existing specification + registered master and 64/128/192 exports |

The NH01–NH07 keys here are contract-local coverage IDs. The source package's original Normal Home NH01 means the multiple-catch page; see the authority mapping in `Normal_Home_Visual_Contract_V1.md`.

## Authority and validation

- [Visual Contract](Normal_Home_Visual_Contract_V1.md)
- [State Visual Matrix](Normal_Home_State_Visual_Matrix_V1.json)
- [Pixel Targets](Normal_Home_Pixel_Targets_V1.json)
- [Component Anchor Map](Normal_Home_Component_Anchor_Map_V1.json)
- [Responsive Contract](Normal_Home_Responsive_Contract_V1.md)
- [Hero EvidenceFit Contract](Normal_Home_Hero_EvidenceFit_Contract_V1.md)
- [Motion Contract](Normal_Home_Motion_Contract_V1.md)
- [Accessibility Contract](Normal_Home_Accessibility_Contract_V1.md)
- [Runtime Gate](Normal_Home_Runtime_Gate_V1.md)
- [Visual Contract Registry](Normal_Home_Visual_Contract_Registry_V1.json)

Static contract validation does not change the existing Normal Home runtime matrix: its device, real-photo, accessibility and screenshot acceptance cases remain `NOT_RUN` until evidence is captured on a known APK/device profile.
