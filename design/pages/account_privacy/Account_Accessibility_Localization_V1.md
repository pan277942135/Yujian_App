# Account & Privacy · Accessibility & Localization V1

Status: **FROZEN**
Version: **V1.0 FINAL**
Date: 2026-10-01

## 1. Purpose

This document freezes accessibility and localization requirements for Account & Privacy.

Accessibility is part of the design contract, not a post-implementation polish step.

## 2. Touch targets

Minimum intended interactive target:

**44dp × 44dp**

Applies to:

- Back;
- chevrons/rows;
- switches;
- password visibility;
- nickname clear action;
- avatar edit/source actions;
- buttons;
- legal links.

Visual icon size may be smaller than 44dp if the semantic hit target remains at least 44dp.

## 3. Screen reader / TalkBack

Every screen exposes a meaningful page title first.

Interactive controls require semantic labels.

Examples:

- Back: `返回`
- password visibility:
  - `显示密码`
  - `隐藏密码`
- nickname clear: `清除昵称`
- avatar edit: `修改头像`
- AI Consent switch/state: include current enabled/disabled semantics
- legal rows: identify them as navigation links/rows

Do not use icon shape alone as the accessible name.

## 4. Reading and focus order

Focus order follows visual/task order.

Typical page order:

1. TopNav title/back;
2. current identity/state;
3. primary content;
4. actions;
5. supporting/legal text.

Do not jump focus to decorative/background elements.

When a modal opens, focus moves into the modal and returns to the initiating control when the modal closes where platform behavior permits.

## 5. State semantics

The following must be announced semantically:

- Disabled;
- Loading;
- Selected;
- Switch ON/OFF;
- Error;
- Save success;
- permission/consent state.

Do not encode state only through:

- color;
- opacity;
- icon shape;
- position.

## 6. Error accessibility

Error feedback includes text.

Field errors are associated with the field.

After submit failure:

- screen reader announces the relevant error;
- focus remains predictable;
- if multiple errors exist, move/focus only when necessary to make the blocking error discoverable.

Do not repeatedly announce the same error on every recomposition/state refresh.

## 7. Password fields

Password input remains obscured by default.

Visibility action:

- has explicit accessible label;
- exposes current action, not only current icon;
- does not clear or modify the field value.

Password values are not spoken as ordinary body text outside the platform-secure input behavior.

## 8. Font scaling

Target support:

**up to 200% system font scale**

Requirements:

- no clipped critical text;
- no hidden primary/destructive actions;
- surfaces grow vertically;
- body/label text may wrap;
- one outer vertical scroll keeps all content reachable;
- fixed screenshot height does not override readability.

When perfect pixel parity conflicts with readable 200% text, readability wins.

## 9. Text wrapping / truncation

Do not truncate:

- error explanations;
- consent consequences;
- permission explanations;
- destructive-action labels;
- legal headings;
- primary action labels when wrapping can solve it.

May truncate carefully:

- secondary account identity;
- long non-critical metadata;

but the user must retain access to the essential value where required.

## 10. Contrast

Target minimum contrast:

- normal text: **4.5:1**
- large text: **3:1**
- meaningful UI boundaries/icons: **3:1** against adjacent color where the element is required to understand/control the UI

Disabled-state styling is exempt only from ordinary contrast expectations where platform conventions permit, but disabled meaning must remain understandable.

Shared color tokens must be used consistently; if a token fails accessibility in a specific context, the accessibility requirement takes priority and requires a versioned token correction rather than a local arbitrary color.

## 11. Color independence

Examples:

Error:
- color + error text.

Consent:
- switch state + semantic label/state.

Deferred:
- readable `即将推出` / equivalent status, not gray-only treatment.

Location denied:
- explicit state text, not red-only indication.

## 12. Reduce Motion

Account & Privacy follows the frozen no-custom-motion policy.

Under Reduce Motion:

- remove Action Button pressed scale;
- keep color/alpha pressed feedback;
- keep functional loading indicator;
- no decorative spatial motion is introduced.

Accessibility never depends on haptic or sound because Account & Privacy defines no custom haptic/sound layer.

## 13. Safe area and keyboard

All pages respect:

- status/navigation insets;
- gesture/navigation bottom inset;
- IME inset;
- display cutout/inset where applicable.

Focused fields and their errors remain reachable above the keyboard.

## 14. Localization principles

Source language may be Simplified Chinese, but layouts must not assume fixed Chinese string length.

All user-visible strings must be localizable resources in implementation.

Do not construct sentences by concatenating translated fragments.

Use placeholders for dynamic values.

## 15. Text expansion

For width-constrained controls, reserve at least **30% text-growth tolerance** where practical.

This tolerance is a planning baseline, not permission to clip longer translations.

If translated text exceeds the reserve:

- allow wrapping;
- grow the container;
- move secondary metadata;
- preserve action meaning.

Do not shrink critical text below the shared typography minimum merely to fit.

## 16. Button localization

Buttons:

- may grow vertically;
- may wrap only when the shared button authority permits and readability requires it;
- must keep semantic action wording;
- must not shorten into ambiguous labels solely to preserve screenshot width.

Examples of semantics that must remain explicit after localization:

- Save;
- Continue Editing;
- Discard Changes;
- Allow Model Improvement;
- Keep Enabled;
- Close/Disable where context requires.

## 17. Settings rows

Settings-row labels and trailing state text must coexist safely.

Priority:

1. setting label;
2. current state;
3. chevron/switch affordance.

If space is insufficient:

- allow supporting/state text to wrap or move to a second line;
- do not remove the state;
- do not overlap the control.

## 18. Legal text localization

Privacy Policy and User Agreement use the Shared Legal Document Shell.

Requirements:

- long paragraphs wrap naturally;
- headings remain semantic;
- links remain distinguishable and accessible;
- version/effective-date metadata localizes cleanly;
- body length may grow substantially without fixed-height clipping.

Final translated legal copy requires appropriate legal review for the target jurisdiction/language.

## 19. Dates, numbers and account identifiers

Use locale-aware formatting for:

- dates/effective dates;
- numeric separators where applicable.

Usernames/account identifiers that are literal identifiers are not translated.

Do not localize internal IDs because internal IDs are not displayed.

## 20. RTL readiness

Current launch scope does not require a separate RTL visual authority.

Implementation should prefer logical Start/End layout semantics where shared components already support them.

A future RTL launch that materially changes composition may require a versioned visual review.

## 21. Accessibility vs localization precedence

If localization or accessibility requires extra vertical space, text wrap or scrolling:

- preserve semantics/readability;
- do not force the frozen screenshot's exact text geometry.

The frozen visual authority defines hierarchy/composition, not text clipping.

## 22. Acceptance checklist

A current Account & Privacy design is conformant only if:

- all controls have semantic labels;
- 44dp targets are preserved;
- 200% font scale remains usable;
- error/success/loading are semantically exposed;
- color is not the sole state carrier;
- strings can expand without hiding critical action/state;
- legal pages support long localized content;
- no custom sound/haptic is required for comprehension.
