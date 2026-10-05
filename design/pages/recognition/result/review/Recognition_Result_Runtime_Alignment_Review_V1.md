# Recognition Result Runtime Alignment Review V1.1

Status: **ENGINEERING AUTHORITY RECONSTRUCTED — BASIC VALIDATION PENDING**

The current Result implementation is aligned to the seven Frozen assets through
state-specific geometry and additive shared variants.

## Closed alignment points

- Result and Recovery use centered-title BACK_CENTER_TITLE; Species Selector
  remains BACK_TITLE;
- High uses vertical Metadata, Story, and equal Dual CTA;
- Medium uses one Candidate Glass Panel and explicit selection;
- Low keeps Metadata / Story visible before species resolution and gates save
  behind LOW_MANUAL selection without creating an unknown record;
- No Fish / Image Quality use one Recovery Glass Panel;
- Result cards use MistGlass and restrained Result radius tokens;
- Hero media uses original oriented photo with state-appropriate bbox/Evidence
  policy;
- Story is limited to 300 Unicode code points with a live counter.

## Validation scope

Run only relevant JVM/Compose compile and basic Android checks, lint, and
assembleDebug. Do not run Runner, AVD, cloud visual-evidence closure, or final
physical visual acceptance. User remains the final physical/visual authority.
