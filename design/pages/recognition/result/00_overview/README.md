# Recognition Result Overview V1.1

Status: DESIGN CLOSED — FROZEN ASSET AUTHORITY

The five state-specific Frozen Result PNGs plus the Frozen Species Selector and Metadata Edit boards are authoritative. Runtime defaults never override them.

## States

- High: usable species result;
- Medium: plausible candidates requiring explicit confirmation;
- Low: unresolved species with editable draft metadata/story;
- No Fish: no usable fish subject;
- Image Quality: source quality blocks reliable recognition.

## Shared Result rules

- Result, Recovery, and Low pages use centered-title BACK_CENTER_TITLE;
- Species Selector remains left-title BACK_TITLE;
- Hero geometry is state-specific, not universal;
- High / Medium / Low use original oriented photo with bbox-protected Subject Crop Fill when safe;
- No Fish / Image Quality use full-source Evidence Fit;
- all Result panels use shared MistGlass with restrained Result radius;
- no Processing edge/focus effects survive into Result.

## Composition

- High: Hero → Species Identity → vertical Metadata → Story → Dual CTA;
- Medium: Hero → unified Candidate Glass Panel → resolved record flow;
- Low: Hero → message/manual/retake → Metadata → Story → Dual CTA;
- Recovery: Hero → one unified Recovery Glass Panel.

## Low pending save

Low CTA is visible before species resolution. Tapping it remembers Continue Memory or Save Catch, opens Species Selector with LOW_MANUAL, and resumes only after a species is selected. No unknown-species FishRecord is ever created. Returning unconfirmed preserves draft fields and clears the pending destination.

## Shared copy

Story title: 写下这次鱼获的故事
Placeholder: 记录这一刻的感受……
Limit: 300 Unicode code points with {count}/300 live counter.

Final physical/visual acceptance belongs to the User.
