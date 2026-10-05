# Recognition Result Visual Spec V1.1

Status: **FROZEN — SEVEN PNG AUTHORITIES**

Recognition Result is the moment where Processing recedes and the user's catch
becomes the subject again. Runtime must follow the state-specific Frozen PNG,
not a universal component default.

## Shared language

- original oriented photo remains the dominant visual object;
- Result, Recovery, and Low use centered-title BACK_CENTER_TITLE;
- Species Selector remains left-title BACK_TITLE;
- Result Glass is shared MistGlass with restrained Result radius;
- no Processing edge field, Fish Focus, detector box, debug metadata, or
  continuous AI animation survives into Result.

## Hero media

High / Medium / Low use bbox-guided FishSafeRect and prefer Subject Crop Fill;
unsafe Fill falls back to Subject Safe Fit. No Fish / Image Quality use complete
source Evidence Fit. No stretch, generated fill, blurred duplicate, or
detector/classifier crop is allowed.

State Hero targets at 360dp are High 322×210, Medium/Low 322×178, No Fish
322×245, and Image Quality 322×214.

## High

Hero → Species Identity → vertical Length / Weight / Location Metadata → Story
Card → equal-width `继续记忆` / `保存本次鱼获` actions.

## Medium

Hero → one Candidate Glass Panel containing prompt, candidates, and other-species
action → resolved Metadata / Story / CTA. Suggested is not selected. Medium
Selected uses restrained Gold; Selector Selected remains Teal.

## Low

Hero → `无法确认是什么鱼` → manual/retake actions → vertical Metadata → Story
→ Dual CTA. Metadata and Story remain editable before species resolution. An
unresolved CTA enters LOW_MANUAL and resumes only after species selection; no
unknown-species record is persisted.

## Recovery

No Fish and Image Quality each use one unified Recovery Glass Panel containing
title, guidance, Retake, and Gallery. Recovery buttons are not teal-solid.

Story uses `写下这次鱼获的故事`, placeholder `记录这一刻的感受……`, a 300
Unicode code-point limit, and a live `{count}/300` counter. Voice is omitted
unless it can perform real speech recognition and write a transcript.
