# Empty Home — Spec Closure V1

Status: **FROZEN**  
Closure marker: **`EMPTY HOME · FROZEN`**  
Closed: **2026-09-29**

## 0. Authority model

Empty Home current authority is:

1. **V2 base visual** — `design/system/core_visual_v1/reference/empty_home_v2.png`
2. **V2.2 approved delta** — `spec/Empty_Home_Frozen_Visual_Revision_V2_2.md`
3. This closure document defines the missing cross-cutting contracts only; it does **not** redesign the page.

V2.2 overrides V2 only for the capture CTA clarity/spacing and the rod-line-bobber-water-contact composition. Everything else remains V2 authority. The historical V3 visual-fidelity closure document is deprecated and is not current authority.

---

## 1. State Relationship

Empty Home and Normal Home are **two content states of one Home product surface / HomeScreen**, not two independent pages or navigation destinations.

| Condition | Home state |
| --- | --- |
| Valid FishRecord count = 0 | EMPTY / Empty Home |
| Valid FishRecord count ≥ 1 | CONTENT / Normal Home |

Frozen rules:

- Authentication/session state is an **orthogonal axis**. Guest vs logged-in must never choose Empty vs Normal Home.
- The state is derived from the authoritative record collection; saving the first valid catch naturally moves Home to Normal, and deleting the last valid catch naturally returns it to Empty.
- Empty Home reuses the production capture/identify callback, album picker callback and account/login callback.
- No duplicate Home route, CameraX flow, picker flow, or parallel navigation stack is permitted.
- Empty Home and Normal Home may share components and interaction plumbing while keeping different page-state compositions.

---

## 2. Component Ownership

### 2.1 Shared system

| Element | Ownership | Frozen authority |
| --- | --- | --- |
| Lake background treatment | Shared · Background System V1 | `BG_ENV_HERO` |
| Empty Home lake master | Shared system selection | `Morning_Lake_Sunrise_Hero_V1` |
| Primary capture control | Shared · Primary Capture Button | `home_primary_capture` |
| Color / typography roles | Shared | `color_typography_v1` |
| Spacing / radius | Shared | `spacing_radius_v1` |
| Capture / album / account behavior callbacks | Shared product flow | Existing production callbacks |

### 2.2 Empty Home page-owned composition

The following are **not** free-floating shared components and must not be silently changed by unrelated shared-system work:

- brand header composition: logo + subtitle + account/login slot;
- Hero copy composition and gold brush ending;
- Empty-specific use of the Sunrise Hero scene;
- rod, fishing line, bobber, water-contact seam and ripple composition;
- Empty-specific environmental composition and phase relationship;
- placement relationship among Hero, fishing scene, prompt, Camera CTA and album action.

### 2.3 Top-navigation boundary

Empty Home's brand header is **not** `TopNavigation V1`.  
`TopNavigation V1` remains the shared navigation shell for title/back/action page structures. The Empty Home header is a page-owned brand/environment composition.

Login/account and album actions reuse the existing product behavior paths; their exact placement on Empty Home is frozen by the page contract.

---

## 3. Layer / Z-order

The following is a **semantic compositing order**, bottom → top. Logical layers may be baked into a shared raster asset; this contract defines visual occlusion, not a requirement to create one bitmap per layer.

1. scene base — sky / mountains / mist / lake and any baked foreground scene portions;
2. cloud atmosphere;
3. sun ambient / particles;
4. rod;
5. fishing line;
6. bobber underwater portion;
7. water-surface contact / occlusion plane;
8. ripple / weak reflection treatment;
9. bobber above-water portion;
10. foreground scene occlusion when separately composited;
11. native content UI;
12. Camera CTA / actionable capture focus.

Frozen contact rules:

- rod-tip anchor = **(335, 1180)**;
- fishing line end = **(560, 1328)**;
- bobber water contact = **(560, 1320)**;
- line endpoint must remain visually tucked behind/below the bobber contact area;
- water surface must occlude the submerged bobber portion and may apply weak refraction / perspective change;
- the complete bobber must never appear pasted on top of the water;
- exactly one ripple group is allowed;
- ripple is a water-surface effect and must remain below the exposed bobber;
- Camera CTA is the highest actionable layer and must not be obscured by environmental effects.

The machine-readable form is `shared/contracts/layer_contract.json`.

---

## 4. Design Manager Runtime Evidence

Latest required revalidation:

| Field | Value |
| --- | --- |
| Main SHA | `38ba0e5c02731b99dcea825f88d4da9b48e11b81` |
| Android CI run | `36514649405` |
| Runtime job | `109235222108` |
| Gate | `empty-home-v2` |
| API | `28` |
| Result | **PASS** |
| Artifact | `11011440570` |
| Artifact digest | `sha256:a63234258d02fd0487e01f49e78d9299f14971419be49f76cac04f5bad97969a` |

Evidence contract still requires:

- `static_reference.png`
- `runtime_static.png`
- `runtime_4s.png`
- `anchor_overlay.png`
- `bobber_ripple_crop.mp4`
- `environment_motion.mp4`
- `camera_motion.mp4`
- `full_runtime_15s.mp4`
- `runtime_debug.json`
- `visual_parity_report.json`

### Validation model

After V2.2, evidence is intentionally hybrid:

- V2 base/full-frame and unchanged areas continue using pixel-parity checks where valid;
- V2.2 CTA and fishing-composition regions use their frozen geometry contracts plus evidence from the real APK runtime;
- this prevents an older V2 pixel reference from rejecting an approved V2.2 delta.

Runtime evidence validates implementation fidelity. It does not create or change design authority.

---

## 5. Closure / Change rule

All four closure gates are complete:

- State Relationship — **CONFIRMED**
- Component Ownership — **CONFIRMED**
- Layer / Z-order — **CONFIRMED**
- Runtime Evidence — **PASS**

There are no open design items in Empty Home Spec Closure V1.

# `EMPTY HOME · FROZEN`

From this point:

- Runtime parity defects may be fixed without reopening design.
- Shared-system refactors must preserve the frozen Empty Home appearance and behavior.
- Any intentional visual, interaction, motion, haptic, sound or state-model change requires a new explicit authority version; it must not be smuggled into a parity fix.
