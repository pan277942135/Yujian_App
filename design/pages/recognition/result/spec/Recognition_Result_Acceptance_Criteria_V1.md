# Recognition Result Acceptance Criteria V1.1

Status: **FROZEN — IMPLEMENTATION REGRESSION CONTRACT**

The seven Frozen PNG assets remain the final visual authority. This contract
defines the minimum engineering assertions; it does not authorize threshold
changes or replace User physical acceptance.

## Navigation and geometry

- High / Medium / Low / No Fish / Image Quality use centered-title Result Top
  Navigation with Back at left;
- Species Selector remains left-title `BACK_TITLE`;
- Hero geometry is state-specific: High 322×210, Medium/Low 322×178, No Fish
  322×245, Image Quality 322×214 at the canonical 360dp viewport;
- Hero media remains original oriented photo with state-appropriate crop policy.

## Result content

- High has Species Identity, vertical three-row Metadata, Story, and equal Dual CTA;
- Medium has one Candidate Glass Panel; suggestion is not selection; explicit
  selection uses restrained Gold and then exposes Metadata / Story / CTA;
- Low initially shows message, manual/retake, vertical Metadata, Story, and
  Dual CTA;
- Low metadata/story are editable before species resolution;
- unresolved Low save never creates a FishRecord, remembers destination, opens
  `LOW_MANUAL`, and resumes after species selection;
- returning unconfirmed preserves draft input and clears pending destination.

## Recovery

- No Fish and Image Quality each have one unified Recovery Glass Panel with
  title, guidance, Retake, and Gallery;
- Recovery actions are not teal-solid;
- recovery media uses full-source Evidence Fit.

## Copy and input

- Story title: `写下这次鱼获的故事`;
- Story placeholder: `记录这一刻的感受……`;
- Story max: 300 Unicode code points with live `{count}/300` counter;
- Length / Weight / Location titles and validation follow the Metadata Input and
  Metadata Edit contracts;
- voice is shown only when a real transcript capability is available.

## Prohibited

- universal Hero aspect ratio;
- fake page-private Glass backgrounds;
- no-op voice;
- unknown-species persistence;
- detector/classifier semantics changes;
- Runner, AVD, workflow, or final visual-evidence changes in this implementation task.
