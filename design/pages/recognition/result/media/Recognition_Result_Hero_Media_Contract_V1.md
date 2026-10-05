# Recognition Result Hero Media Contract V1.1

Status: **FROZEN**

Machine-readable authority: `hero_media_contract.json`.

The Hero always renders the original oriented user photo. Frozen Result state
geometry controls the viewport; media policy controls the source rectangle.

## High / Medium / Low

1. start from the oriented original photo;
2. derive FishSafeRect from the real detector bbox;
3. prefer `SUBJECT_CROP_FILL` when the complete FishSafeRect and a 12dp visual
   safety inset fit in the source;
4. use `SUBJECT_SAFE_FIT` only when Fill would clip the safe region or the bbox
   is missing/invalid;
5. never crop a source edge further when the fish touches that edge.

No detector box, classifier crop, stretch, generated fill, or blurred duplicate
photo is rendered. Candidate selection never changes the source rectangle.

## No Fish / Image Quality

Use `EVIDENCE_FIT` with the complete oriented source visible. Bbox crop,
generated enhancement, blurred support, and outpaint are prohibited.

## Shared invariants

- no post-entry crop jump;
- orientation remains correct;
- the Hero is independent of the Processing edge field and Fish Focus;
- Result uses the state-specific geometry resolver rather than a universal
  aspect ratio.
