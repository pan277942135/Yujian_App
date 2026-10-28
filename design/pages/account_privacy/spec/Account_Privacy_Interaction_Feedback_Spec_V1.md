# Account & Privacy · Interaction Feedback Spec V1

Status: **FROZEN**

Scope:
- account/privacy pages
- profile/account settings
- legal/about pages
- edit-profile state feedback

This spec freezes the absence of feature-specific motion, haptic and sound for Account & Privacy.

## 1. Principle

Account & Privacy is a settings / form / legal-reading surface.

It must feel:
- quiet;
- predictable;
- utility-first;
- non-gamified.

Do not introduce branded animation, celebratory feedback, ambient motion or decorative audio.

## 2. Motion

Status: **FROZEN**

No page-private motion system is required.

Account & Privacy inherits only Shared Design System component behavior.

### Allowed

Action Button V1.1:
- pressed scale: 1.00 → 0.985
- press-in: 90ms
- release: 120ms
- no overshoot
- standard loading indicator
- no shimmer / pulse

Icon Action V1:
- color / alpha / support-surface pressed feedback
- 90ms
- no scale
- no rotation
- no bounce

Text Action V1:
- color / alpha pressed feedback
- 90ms
- no scale
- no bounce

Platform/shared transitions:
- normal page navigation
- standard Bottom Sheet / dialog transition
- standard progress indicator where already defined

### Forbidden

- ambient page animation
- breathing cards
- parallax
- gold sweep
- particle effects
- success celebration
- error shake
- bounce
- auto-scrolling animation
- attention-seeking pulse
- motion that exists only to make settings feel “alive”

## 3. Reduce Motion

Shared Reduce Motion rules apply.

For Account & Privacy specifically:
- do not add spatial motion beyond platform navigation/sheet transitions;
- pressed scale on Action Button is removed under Reduce Motion;
- color/alpha pressed feedback remains;
- loading indicator remains functional.

No feature-specific Reduce Motion asset or menu is required.

## 4. Haptic

Status: **FROZEN**

Feature-specific haptic:
**NONE**

Component-level rules:
- Action Button automatic tap haptic = none
- Icon Action haptic = none
- Text Action haptic = none

Do not add custom haptic for:
- login/register tap
- profile save success
- profile save failure
- nickname validation
- avatar selection
- password validation
- AI consent enable/disable
- location information
- logout
- legal-document navigation

If the operating system or platform component produces built-in tactile feedback outside YuJian control, this spec does not require suppressing it.

## 5. Sound

Status: **FROZEN**

Feature-specific product sound:
**NONE**

Do not play:
- click sounds
- save success sound
- error sound
- consent sound
- logout sound
- page-enter sound
- ambient music
- legal-page audio

Shared component contracts already define sound disabled.

No sound asset is required.

## 6. Success / error feedback

Feedback is visual/textual, not sensory.

### Success

Use restrained inline feedback such as:
- `已保存`
- `密码已修改`

No:
- confetti
- toast sound
- celebratory vibration
- card animation

### Error

Use explicit text close to the affected field or page feedback region.

No:
- shake animation
- red flashing
- error vibration
- error sound

## 7. Sheets and confirmations

AI Consent, unsaved-change confirmation and other sheets use standard shared/platform sheet transition only.

No custom:
- spring entrance
- overshoot
- dim-layer animation variant
- haptic on open/close
- sound on confirm

## 8. Legal / About

About, Privacy Policy and User Agreement use no decorative motion, haptic or sound.

Long-form legal reading is static aside from user-controlled scrolling.

## 9. Accessibility

Absence of custom haptic/sound must not remove required semantic feedback.

Required:
- loading announced semantically;
- success/error announced by accessibility services;
- disabled/loading state exposed;
- validation not communicated only by color.

## 10. Runtime parity

Runtime may currently include default Compose/Android transitions.

That is acceptable if they remain within platform/shared behavior and do not introduce feature-specific branded feedback.

Any future request for custom motion/haptic/sound requires a versioned design revision. It must not be added ad hoc in runtime.

## 11. Authority dependencies

Shared authorities:
- `design/system/components/action_button/motion_contract.json`
- `design/system/components/icon_action/motion_contract.json`
- `design/system/components/text_action/motion_contract.json`
- `design/system/motion/README.md`
- `design/system/haptic/README.md`
- `design/system/sound/README.md`

This contract records the feature-level decision that Account & Privacy adds no custom feedback layer above those authorities.
