# YuJian Core Visual System V1

## 1. Core philosophy
Visual priority is fixed:

```
Nature
↓
Real Catch
↓
Memory
↓
Information
↓
Interaction
↓
Data
↓
Achievement
```

YuJian is a natural-memory product, not a fishing dashboard or collection game.

## 2. Reference canvas
- 1080 × 1920 px
- 9:16
- full bleed
- no device frame
- top-left design coordinate origin
- runtime must use adaptive layout; reference pixels are not literal Android pixels

## 3. Background
### BG_ENV_HERO
Use: Empty Home / Normal Home. Environment participates in the story: morning sky, distant mountain, thin mist, lake, restrained warm-gold light.

### BG_CONTENT
Use: Recognition Result / FishRecordDetail. Same visual world, with background salience reduced to ~70–80% of Home.

### BG_DATA
Use: My Catches / Fish Guide. Same visual world, reduced further to protect dense content.

Forbidden: neon, night HUD, strong HDR, orange-red sunset treatment, tourism-poster sunlight, page-by-page unrelated lake worlds.

## 4. Color
See `tokens/color_tokens.json`.
Gold is scarce. It is reserved for brand, important moments, and meaningful records.

## 5. Typography
Three roles only:
- BRAND: brand/emotional calligraphic copy
- DISPLAY: editorial/page/fish titles
- UI: data, metadata, controls

## 6. Glass
Three levels only:
- GLASS_A primary content
- GLASS_B secondary/story
- GLASS_C overlay/pill

Glass exists for readability and atmosphere, not as a visual effect demo.

## 7. Spacing / radius
Use shared spacing and radius tokens. Allow vertical scrolling rather than compressing a detail/list screen to fit one 9:16 viewport.

## 8. Icons
Rounded outline family, restrained line weight, DeepLakeBlue by default. Utility icons must remain below page title / Hero in visual weight.

## 9. Image treatment
Priority:
```
Fish
↓
Catch moment
↓
Human
↓
Landscape
```
Images should feel captured, not staged. Avoid commercial studio treatment, strong CGI, oversaturation and metallic fish rendering.

## 10. Shared components
- YuJianCatchHeroCard
- FishRecordRowCard
- FishGuideCard
- YuJianPrimaryCaptureButton
- AchievementAnnotation
- TopNavigation
- MistGlassCard

## 11. Achievement semantics
Achievement annotations answer “why this catch is worth remembering”, not “what reward did I unlock”.

## 12. Motion
Motion should make the environment feel alive without making UI animation itself noticeable.

## 13. Cross-page acceptance
See `cross_page_rules.md`.

## 14. Visual authority
When a page-level design conflicts with the global atmosphere:
1. Frozen page behavior remains authoritative for information architecture.
2. Empty Home + Normal Home remain authoritative for YuJian atmosphere.
3. Shared tokens/components remain authoritative for implementation consistency.
4. Any true product conflict must be escalated rather than silently resolved in code.
