# Normal Home Android Implementation Contract V1

**Page ID:** normal_home_v1  
**Contract version:** V1  
**Status:** READY_FOR_IMPLEMENTATION  
**Static contract validation:** PASS  
**Runtime validation:** NOT_RUN

This contract binds the frozen Normal Home Visual Acceptance Contract to Android implementation owners. It defines implementation ownership and references. It does not add or alter visual rules, tokens, components, artwork, page states, services, or data fields.

## Authority Chain

Effective Authority Registry V3.1  
↓  
Normal Home Visual Contract V1  
↓  
Normal Home Android Contract V1  
↓  
Compose implementation  
↓  
Runtime evidence

- Effective authority registry: design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json
- Visual authority: design/system_v3/10_Page_Visual_Acceptance_Contracts/Normal_Home/Normal_Home_Visual_Contract_V1.md and its linked registry/artifacts.
- Shared component authority: design/system_v3/05_Component_System/Component_Registry_V3_FINAL.json; a small number of existing page/shared contracts remain explicitly referenced as legacy authority in the mapping.
- Shared token authorities: the effective Typography, Color, and Geometry files named by the Effective Authority Registry. No page-private token is introduced.
- Resource authority: Normal_Home_Resource_Binding_V1.json, which resolves asset identities through the Asset Registry, background identities through the Background Registry, and SVG identity through the Icon Registry.

Android implementation must consume this contract and its registered dependencies. Frozen PNGs, Figma, and Design Manager are not independent implementation inputs and must not be inspected to infer dimensions, colors, text style, crop, or behavior. The Visual Contract and its Pixel Targets/Anchor Map are the sole page-level design interpretation.

The Visual Contract V1 text records that Home had no Android Registry entry at the time that visual contract was frozen. This contract and the current Page/Android/Effective Authority Registry entries supersede that registration fact only; all visual rules and Frozen sources remain unchanged.


## Screen Ownership

The Android page owner is Normal Home route composition.

It owns:
- Selecting the existing Empty Home sibling route or Normal Home data presentation from the resolved valid-record count.
- Rendering the contract states and binding registered components/assets.
- Emitting navigation events defined by the existing Normal Home behavior authority.
- Preserving the selected stable record ID across refresh/reorder and retaining a valid snapshot during refresh failure.

It does not own:
- New visual rules, tokens, components, or states.
- Record persistence, recognition, account, or API behavior.
- A replacement Empty Home design.
- A page-local avatar fallback or substitute icon set.

EMPTY_HOME is a route-level state that hands off to the separately authorized Empty Home V2.2 surface. It is not a claim that Empty Home has become an in-page Normal Home variant. DATA_HOME renders the single-record or multiple-record Normal Home visual state.

The Page Contract Registry continues to report its Phase 3-A gate for the three Recognition pages only. This Android binding is separately registered as a direct derivative of the Normal Home Visual Contract; it does not claim that a Normal Home V3.1 Page Contract has been frozen.

## Component Mapping

The complete mapping is Normal_Home_Android_Component_Mapping_V1.json.

| Design element | Android owner | Registered authority |
|---|---|---|
| Empty scene background / rod / line / bobber / ripple | BackgroundLayer / DecorativeLayer / CanvasLayer / AnimatedObject / AnimationLayer | NH01 anchor map and exact Asset Registry identities |
| Capture CTA visual | PrimaryCaptureButton | camera.capture_surface plus existing primary_capture_button manifest and registered master |
| Capture CTA layout | CaptureActionGroup | NH05 layout anchor; separate from visible art and hit target |
| Capture CTA touch | CaptureSemanticsTarget | NH05 touch anchor; measured independently, minimum 48×48dp |
| Avatar | AvatarStateComponent | Existing yujian_default_profile_avatar_v1 shared contract and NH07 registry |
| Greeting | Registered text primitive | Typography token from effective Typography Registry; no new design component |
| Statistics | StatisticCard | data.statistic_card, NH04 variant |
| Recent Catch | CatchCard / TextAction | surface.card and action.text_action |
| Hero media | HeroMedia | media.hero and media.image; existing Hero EvidenceFit contract |

Page-owned decorative layers are not promoted to shared components. Every shared component ID in the mapping resolves to its canonical registry or an explicitly identified, already-existing shared legacy contract; no component is created by this contract.

## State Binding

The sole state binding is Normal_Home_State_Binding_V1.json. It maps visual state IDs and source conditions without creating Android-private state names.

- Successful resolution with 0 valid records routes to EMPTY_HOME / Empty Home V2.2.
- Successful resolution with 1 valid record routes to DATA_HOME with HAS_CATCH and the NH02 first-catch composition.
- Successful resolution with 2+ valid records routes to DATA_HOME with MULTIPLE_CATCH and the registered user-driven Pager.
- Initial unresolved data uses the registered resolving state and must not be reported as zero.
- Refresh pending/error with a prior snapshot retains that snapshot, selected stable record ID, and page state.
- Missing/failed Hero media replaces only the media region.
- Avatar states are GUEST, DEFAULT, LOADING, ERROR, and REMOTE; see the asset alias and fallback binding in the JSON.

## Compose Guideline

Use Normal_Home_Compose_Guideline_V1.md. The implementation structure is conceptually:

Window → one inset owner → safe content / page coordinate mapping → background and page layers → registered page components and semantics.

The page geometry must be derived from the Visual Contract Pixel Targets and Component Anchor Map. Do not substitute arbitrary padding, fillMaxHeight, or weight for the registered anchor transform. The explicit Empty Home transform and Normal Home transform remain separate.

## Window Insets Contract

Normal_Home_Window_Insets_Contract_V1.md is the only Android binding for edge-to-edge, coordinate origin, safe bounds, IME measurement, and short-screen mode. Insets are consumed once. SHORT_SCREEN_MODE is an implementation label for the Visual Contract's existing SAFE_OVERFLOW response; it is not a new visual state.

## Resource and Motion Binding

- Static identity, path, SHA-256, registry source, status, and permitted role are in Normal_Home_Resource_Binding_V1.json.
- Runtime Hero photos are user/catch media; they do not have a repository asset SHA. Apply only the existing Media/Hero EvidenceFit contract. Never change stored photo bytes.
- Motion values and lifecycle/reduced-motion behavior are inherited by Normal_Home_Motion_Runtime_Binding_V1.md; no timing fork is allowed.
- Existing Asset Registry REVIEW rows and the unregistered Guest image are reported as inherited warnings. This contract does not promote an asset or generate a replacement.

## Runtime and Acceptance Boundary

Normal_Home_Runtime_Gate_V1.md defines the future evidence record and verdicts. The gate covers canonical and responsive widths, font scales, Window Insets, data/avatar/catch states, accessibility and media errors. Contract validation is static only; no Android project, APK, emulator, device, or runtime screenshot has been inspected or tested.

The scoped gate is NORMAL_HOME_ANDROID_CONTRACT_READY = PASS when the registry, authorities, components, tokens, states, resources, and runtime criteria resolve. It does not rewrite the Phase 3-A PAGE_CONTRACT_READY scope, certify runtime behavior, or close the separate NH07 full-page review / Hero fixture provenance risks.
