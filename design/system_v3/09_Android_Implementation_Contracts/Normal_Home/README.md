# Normal Home Android Implementation Contract V1

**Contract status:** READY_FOR_IMPLEMENTATION  
**Gate:** NORMAL_HOME_ANDROID_CONTRACT_READY = PASS  
**Runtime:** NOT_RUN

## Authority and use

Use Normal_Home_Android_Contract_V1.md as the Android implementation entry. It derives from the frozen Normal Home Visual Contract through Effective Authority Registry V3.1. It does not replace the Visual Contract, modify the Phase 3-A Page Contract gate, or claim runtime acceptance.

Android owners must not derive design values from Frozen PNGs, Figma, or Design Manager. Use the linked Contract, Pixel Targets, Anchor Map, shared tokens/components and registered resource identities.

## Contract files

- Android contract: Normal_Home_Android_Contract_V1.md
- Component mapping: Normal_Home_Android_Component_Mapping_V1.json
- State binding: Normal_Home_State_Binding_V1.json
- Compose implementation guidance: Normal_Home_Compose_Guideline_V1.md
- Window Insets: Normal_Home_Window_Insets_Contract_V1.md
- Resource binding: Normal_Home_Resource_Binding_V1.json
- Motion runtime binding: Normal_Home_Motion_Runtime_Binding_V1.md
- Runtime gate: Normal_Home_Runtime_Gate_V1.md

## Registered dependencies

- Visual Contract: design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Visual_Contract_V1.md
- Effective Authority Registry: design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json
- Page Contract Registry: design/system_v3/08_Page_Contracts/Page_Registry_V3_1.json (implementation binding is separate from the Phase 3-A frozen-page list)
- Android Contract Registry: design/system_v3/09_Android_Implementation_Contracts/Android_Implementation_Contract_Registry_V1.json
- Asset, Background, Icon, Component, Typography, Color and Geometry registries as referenced by the authority/resource bindings.

## Gate and known limits

Static contract gate: NORMAL_HOME_ANDROID_CONTRACT_READY = PASS. Runtime: NOT_RUN.

The NH07 full-page integration review and Hero fixture provenance remain open from the Visual Contract. The Guest avatar has a verified path/SHA but no Asset Registry row (warning, non-blocking for this static contract gate). Some Empty Home layer assets and Icon files carry inherited REVIEW status in Asset Registry; their identities are preserved without promotion. The separate Normal Home V3.1 Page Contract remains deferred and the existing Phase 3-A Page Contract gate remains scoped to Recognition Capture / Processing / Result.
