# Recognition Result Visual Acceptance Map V1

Status: **FROZEN**

Machine-readable authority: `visual_acceptance_map.json`.

This contract makes visual acceptance deterministic while accounting for dynamic user photos and text.

## 1. Capture baseline

Primary visual gate:
- Android API28 runtime gate;
- same APK / test APK build SHA;
- 360dp canonical content viewport for reference capture;
- system status/navigation bars removed or consistently canonicalized;
- screenshot PNG, lossless.

Reference Frozen images:
- 05 High
- 06 Medium
- 07 Low
- 08 No Fish
- 09 Image Quality

## 2. Canonical normalization

Geometry comparison canvas:
- **360 × 640dp**
- Frozen: 941×1672 → canonical using independent X/Y scale
- Runtime: crop system chrome/insets consistently, then map content viewport to canonical dimensions

Do not compare arbitrary full-device screenshots without canonicalization.

## 3. Dynamic masks

Pixel-level parity must mask content that is legitimately runtime-variable:

Mask:
- Hero photo interior, but **not Hero frame/clip edge**
- species name glyphs when fixture species differs
- user-entered metadata values
- location value
- note text
- candidate species artwork when fixture media differs

Do not mask:
- component geometry
- surfaces
- borders
- spacing
- button shapes/colors
- prompt/recovery static copy
- fixed labels
- Hero frame radius/position

## 4. Geometry gates

Hard:
- Top Nav frame: **±4dp**
- Hero frame: X/Y/W/H **±4dp**
- Hero aspect ratio: **<=1.5% deviation**
- Identity/Prompt/Recovery anchor: **±6dp**
- Metadata/Note block: **±6dp**
- Candidate card W/H/gap: **±3dp**
- CTA frame: **±4dp**
- Action height: exact **56dp**
- Action pair gap: exact **12dp**
- High pair ratio: **44/56**
- Low pair ratio: **58/42**

## 5. Pixel MAE gates

After dynamic masks and normalization, mean absolute RGB error per channel on 0–255 scale:

| ROI | PASS |
| --- | ---: |
| Full static shell | <=28 |
| Navigation | <=22 |
| Hero frame/edge only | <=24 |
| Identity / Prompt | <=26 |
| Metadata / Note surfaces | <=24 |
| Candidate surfaces/states | <=24 |
| Recovery panel | <=24 |
| CTA group | <=20 |

A global PASS cannot hide a failed critical ROI.

## 6. Color/token gate

Exact token usage is preferred over screenshot color sampling.

Hard fail:
- wrong shared component variant;
- gold used for normal Result actions;
- red recovery chrome not present in Frozen;
- non-BG_CONTENT page treatment;
- blurred user-photo full-screen background.

## 7. State-specific ROI map

### High
- NAV: [0,0,360,56]
- HERO_FRAME: [20,64,320,248]
- IDENTITY: [20,328,320,44]
- METADATA: [20,384,320,72]
- NOTE: [20,468,320,80]
- CTA: [24,564,312,56]

### Medium unresolved
- NAV
- HERO_FRAME
- PROMPT: [20,328,320,28]
- CANDIDATES: [20,368,320,112]
- OTHER_SPECIES: [20,488,320,44]

### Medium resolved
Capture two frames:
1. top confirmation region;
2. lower scrolled frame containing Metadata / Note / CTA.

### Low
- NAV
- HERO_FRAME
- MESSAGE: [20,328,320,34]
- CTA: [24,378,312,56]

### No Fish / Image Quality
- NAV
- HERO_FRAME
- RECOVERY_PANEL: [20,332,320,128]
- PRIMARY: [24,480,312,56]
- SECONDARY: [24,548,312,56]

## 8. Behavioral visual evidence

Required:
- High idle
- High metadata populated
- High saving/loading
- High save error retained-values state
- Medium suggested/no-selection
- Medium selected
- Medium resolved lower scroll
- Low unresolved
- Low after manual species resolution
- No Fish
- Image Quality
- 320dp width
- 393dp width
- 411dp width
- compact height <600dp
- large font scale
- Hero Media test matrix representative frames

## 9. Hero Media gate

Hero interior uses its own contract and is not judged against the Frozen fish pixels.

Must pass:
- original source authority
- FishSafeRect coverage
- Fit/Crop decision
- SOURCE_CLIPPED protection
- Evidence Fit on Recovery
- no post-entry crop jump
- no blurred-photo support fill

## 10. Failure classification

- wrong product state/copy/action → FAIL_BEHAVIOR
- geometry outside hard tolerance → FAIL_GEOMETRY
- wrong shared component/token → FAIL_COMPONENT
- ROI MAE over threshold → FAIL_VISUAL
- dynamic Hero contract violation → FAIL_HERO_MEDIA
- screenshot/tooling/environment failure → BLOCKED_INFRA / FAIL_EVIDENCE as appropriate

Do not lower thresholds to turn a failure into PASS.


## 11. Metadata Edit Flow evidence

Bottom Sheets are interaction overlays and are not compared against the five full-page Frozen PNGs as page ROIs.

Required deterministic evidence:
- Length sheet · empty;
- Length sheet · existing value;
- Length sheet · invalid zero/error;
- Weight sheet · existing value;
- Location · Recent state;
- Location · search results;
- Location · current-location resolving;
- Location · permission denied;
- Location · search empty/error;
- reopening and clearing committed values.

Hard interaction gates:
- numeric field autofocuses and decimal IME opens without a second tap;
- invalid value cannot commit;
- scrim/back dismissal does not commit draft;
- search query does not mutate Result field;
- search/current/recent selection commits and dismisses immediately;
- permission is never requested before explicit Use Current Location;
- Recent clear does not clear current Result location;
- all metadata remains optional.

Geometry tolerance:
- sheet outer horizontal edge: ±4dp;
- sheet top radius: ±2dp;
- numeric field height: ±3dp;
- search/current/recent row geometry: ±4dp;
- action hit targets meet shared-component minimums.

The page beneath the sheet must preserve the same Result geometry and must not navigate into a separate editor screen.
