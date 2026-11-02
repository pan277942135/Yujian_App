# Master visual inventory

The supplied 1440x3120 image is flattened RGB with no alpha and no editable native layer source. The top-right sunlight, three refractive droplets, grass groups, lower caustics and central low-detail water are visibly identifiable. Their optical interaction is already mixed into neighboring water pixels. The only defensible full fidelity visible layer is the original master-as-base.

- Lake Base: exact whole-image static base.
- Sunlight: warm luminance and soft masks are analysis-only; no separate light layer enters the visible stack.
- Caustics: lower-field high-pass and region masks are analysis-only; no repeatable tile was recovered.
- Grass left/right: RGB dominance masks produce diagnostic alpha candidates. Faded blade edges and water tint contaminate the cutouts; they are not runtime assets.
- Droplets 01-03: individual region masks and anchors only. Refracted scene pixels cannot be separated into independent RGBA drops without inventing the background behind them.
- Vector paths: hand-estimated motion guides for audit only; they are not pixel-accurate leaf skeletons and are disabled for runtime.

The static recomposition is exact because it renders the original master once as the visible base. This does not constitute a recovered layered reconstruction. The truthful pack status is ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS.
