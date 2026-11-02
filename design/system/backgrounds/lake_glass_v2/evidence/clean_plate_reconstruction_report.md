# Clean plate reconstruction audit

- Candidate 02 SHA-256: d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12; canvas 1440x3120 RGB.
- Separated grass blades and three droplet supports are repaired with a center-water row profile or local 14px Gaussian fill, each restricted to explicit masks.
- Lower grass/root beds that overlap caustics remain baked in the Base; the grass RGBA files contain only the safer upper/leaf-body regions.
- Lower caustic positive high-pass detail is removed from the base and delivered separately as an RGBA texture using Canvas PLUS / PorterDuff ADD.
- Broad upper-right morning illumination and water-surface reflections remain baked. No unlit clean plate exists in the supplied inputs, so this clean plate is incomplete.
- Reference images include original/repaired crops at 100% and 200% and the full repair mask.
