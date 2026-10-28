# Recognition Result Visual Spec V1.2

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

## Hero media — V1.2 media policy

High / Medium / Low use the frozen BBox sequence: anchored expansion → one-sided
edge compensation → margin profiles 10%/12%, 8%/8%, 4%/4%, 0%/0% → same-source
ambient fallback only if no safe crop window exists. Missing/untrusted BBox uses
the complete oriented source as foreground plus same-source blurred background.

No Fish / Image Quality do not run fish-BBox crop. Keep the complete oriented
source visible in the foreground and fill remaining container space with a
same-source blurred background. Generated fill, outpainting, stretch, and
detector/classifier crops remain prohibited.

Container width responds to available screen content width. Each result state
retains its existing aspect ratio; photo orientation must not switch that ratio.
At the 360dp reference layout, Hero targets remain High 322×210, Medium/Low
322×178, No Fish 322×245, and Image Quality 322×214. These are reference
geometries, not hard-coded pixel sizes for every device.

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
