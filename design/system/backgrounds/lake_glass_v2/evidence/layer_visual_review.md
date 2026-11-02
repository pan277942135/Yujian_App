# Layer visual review

- Grass RGBA layers use a source-over delta solve against the repaired base; edge strips show black, white and checker backgrounds.
- SVG paths are manually estimated control guides, not recovered source vectors. Motion remains disabled.
- Droplets each have shape, normal, height, displacement and specular maps plus shaped static compatibility RGBA.
- Caustics are lower-water positive detail and are not tileable. No tile was created.
- Sunlight is a procedural right-origin field for inspection only; it is excluded from runtime because light remains baked.
