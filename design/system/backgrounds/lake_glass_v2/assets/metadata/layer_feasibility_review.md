# Layer feasibility review

## Decision

No independent visible feature layer can be recovered from the supplied flattened RGB image with defensible alpha and clean-plate fidelity. Keep the source master intact as the only visible layer. The masks, cutout candidates and SVG guides in this package are diagnostic/procedural references only. Do not enable them in a visible stack until a native layered source or reviewed manual extraction plus clean plate is provided.

## Candidate limitations

The grass RGBA candidates use green-channel dominance. Their alpha is an estimate and mixes the edge water tint; black/white/checker previews expose these risks. They must not be used as runtime overlays. The droplet region masks locate the original drops but are not drop cutouts.
