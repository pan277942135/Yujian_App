# YuJianCatchHeroCard Media Edge States V1

Status: **FROZEN VISUAL AUTHORITY**  
Owner: **Shared Hero Card design system**  
Raster: `design/system/core_visual_v1/components/assets/YuJianCatchHeroCard_Media_Edge_States_V1_Frozen.png`

This is one shared `YuJianCatchHeroCard` authority for HOME and DETAIL. It extends the existing content and geometry rules in `Hero_Card.md`; it does not create a page-specific duplicate or alter either page's outer layout.

The board covers Landscape, Portrait, Extreme Portrait, and Embedded Letterbox media in both variants. It uses the existing sample catch photograph as its visual fixture. The source media path and SHA-256 are recorded in the raster manifest; all crop, fit, and backdrop treatment is presentation-only.

## Frozen media rules

- Keep source media bytes unchanged. Cropping, scaling, and backdrop treatment are presentation-only.
- Keep the fish as the first visual subject. Crop only while the fish remains clearly legible; otherwise use Safe Fit.
- No hard black bars may remain inside the final Hero. A verified uniform solid band may be trimmed in the presentation view only.
- Portrait and extreme portrait media may use a restrained ambient backdrop derived from the same source. The actual photo remains sharper and visually dominant.
- No synthetic replacement fish, screenshot reconstruction, content in-painting, or solid black information strip.
- Preserve existing Hero outer geometry and radius for both variants. Keep the readability gradient light.
- HOME retains its existing species / measurement / time / location content rules. DETAIL retains measurement / location and low-weight `编辑 >`, without time.
