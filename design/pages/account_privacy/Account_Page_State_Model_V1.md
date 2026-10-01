# Account & Privacy · Page State Model V1

Status: **FROZEN**
Version: **V1.0 FINAL**
Date: 2026-10-01

## 1. Purpose

This document freezes the shared state vocabulary for Account & Privacy.

It does not force every page into one generic state machine. Each page uses the smallest applicable state family.

## 2. State-family rule

Use one of the following state families:

1. read-only / hub;
2. editable form;
3. transactional setting;
4. permission;
5. deferred capability;
6. legal-reading page.

Do not invent extra states in runtime without a versioned design change.

## 3. Read-only / hub state family

Applies to:

- My
- Account & Security
- Data & Privacy shell
- About YuJian

States:

```
RESOLVING
  ├─ READY
  └─ LOAD_ERROR
```

### RESOLVING

Use while required current state is unknown.

Rules:

- keep page structure stable when possible;
- use restrained inline progress or placeholder;
- no shimmer;
- no breathing/pulse;
- do not display a guessed setting value.

### READY

Show the confirmed state.

Zero values are valid values where defined, for example My statistics.

### LOAD_ERROR

Preserve unaffected navigation and already-known/stale content when safe.

Do not replace a known account/privacy page with a false empty state.

If a setting value cannot be read, show an explicit unknown/error state rather than assuming ON or OFF.

## 4. Editable-form state family

Applies to:

- Edit Profile
- Nickname
- Change Password

Canonical states:

```
PRISTINE
  ↓ edit
DIRTY_VALID ───────→ SAVING ───────→ SAVED
     ↑                  │
     │                  ├──────────→ FAILURE_RETRYABLE
     │                  ├──────────→ FAILURE_SESSION
     │                  └──────────→ PARTIAL_SUCCESS
     │
DIRTY_INVALID
```

### PRISTINE

- persisted values are shown;
- no pending change;
- Save disabled where a Save action exists;
- no stale success/error message.

### DIRTY_VALID

- at least one value differs from persisted state;
- local validation passes;
- Save enabled;
- draft preserved.

### DIRTY_INVALID

- local validation fails;
- Save disabled;
- nearest useful field/source error shown;
- valid parts of the draft preserved.

### SAVING

- one submission in flight;
- duplicate submission blocked;
- draft remains visible;
- do not claim success before confirmed result;
- controls that would invalidate reconciliation are disabled while necessary.

### SAVED

- server-confirmed state becomes the new persisted state;
- dirty state clears;
- pending avatar state clears when applicable;
- restrained inline success may be shown;
- success clears on the next edit or page exit.

### FAILURE_RETRYABLE

Examples:

- network error;
- temporary service failure;
- upload failure.

Rules:

- draft preserved;
- retry uses the same primary action when appropriate;
- do not silently revert the visible draft while implying it was saved.

### FAILURE_SESSION

Use when the authenticated session is invalid.

Rules:

- stop retry loop;
- show `登录状态已失效，请重新登录`;
- do not claim save success;
- re-auth navigation is owned by Auth flow.

### PARTIAL_SUCCESS

Use only when the backend can persist part of one logical edit while another part fails.

Rules:

- successful values must reflect confirmed server truth;
- failed draft remains editable/retryable;
- feedback explicitly says only part succeeded;
- do not claim atomic rollback unless the backend guarantees it.

## 5. Transactional-setting state family

Applies to AI Model Improvement consent.

Canonical states:

```
RESOLVING
   ↓
OFF / ON
   ↓ user intent
CONFIRMING_TARGET
   ↓ confirm
SAVING_TARGET
   ├─ CONFIRMED_OFF / CONFIRMED_ON
   └─ SAVE_ERROR → previous confirmed state
```

Rules:

- switch displays confirmed state;
- no silent instant toggle;
- explicit confirmation required by the consent spec;
- failed save preserves the previous confirmed state;
- loading state must not appear as changed consent;
- no pre-checked or persuasive consent.

AI Consent is transactional, not a dirty form. Unsaved-change Back confirmation does not apply before a confirmed setting change.

## 6. Permission state family

Applies to Location.

### Display states in Data & Privacy

Frozen user-facing labels:

- `使用期间`
- `已拒绝`
- `未授权`

The settings row is informational.

### Point-of-use request states

Only the explicit location-use context may enter:

```
CHECKING
  ├─ GRANTED → resolve current location
  └─ REQUEST_REQUIRED → REQUESTING
                          ├─ GRANTED
                          └─ DENIED
```

Rules:

- opening Account & Privacy never enters REQUESTING;
- opening the Location info state never requests Android permission;
- denial does not block recognition or FishRecord save;
- if permission is granted but location resolution fails, that is a location-resolution failure, not permission denied;
- OS-specific permanent-denial/restricted details may be mapped internally without adding a new visible Account & Privacy row state unless a future design version requires it.

## 7. Deferred capability state

Applies to:

- Forgot Password
- Export My Data
- Delete Account

State:

`DEFERRED`

Rules:

- not LOADING;
- not DISABLED due error;
- not MISSING;
- current product behavior is entry-only / Coming Soon;
- historical future-state boards are not active runtime states.

## 8. Legal-reading state family

Applies to:

- Privacy Policy
- User Agreement

Page-shell states:

```
READY
LOAD_ERROR (only if content is remotely unavailable)
```

Legal-copy approval is not a UI state.

`LEGAL_REVIEW_REQUIRED` is a governance gate outside the user-facing state machine.

## 9. Loading rules

### Button loading

Use for:

- Save profile;
- Change Password;
- confirmed consent transaction.

Rules:

- keep label semantics understandable;
- block duplicate submit;
- preserve surrounding layout.

### Page resolving

Use only when required page state is unknown.

Prefer restrained inline progress/placeholders.

### Skeleton

Not the default for Account & Privacy.

If a future list-like surface requires skeleton placeholders:

- keep them static or Shared Reduce Motion compliant;
- no shimmer/pulse;
- do not use skeleton to hide unknown privacy/consent state.

## 10. Feedback priority

Use the narrowest useful feedback surface:

1. field/source error;
2. inline section/page feedback;
3. page-level banner only when multiple regions are affected.

Critical account operations must not rely on transient Toast alone.

## 11. Error rules

Do not use:

- shake animation;
- flashing red;
- sound;
- haptic;
- full-screen destructive treatment for ordinary validation/network errors.

Errors must include text and actionable recovery when recovery exists.

## 12. Empty and zero rules

Do not confuse empty with error.

Examples:

- zero FishRecord statistics are valid;
- missing nickname falls back according to the profile contract;
- missing avatar uses Shared Default Profile Avatar;
- unavailable loaded image does not imply logged-out/Guest.

## 13. Stale-data rule

When a refresh fails and previously confirmed non-sensitive state is still safe to display:

- keep the confirmed state;
- mark refresh failure/retry as needed;
- do not replace it with guessed values.

Consent state is never guessed.

## 14. State transition accessibility

When state changes:

- loading is announced;
- error is announced;
- save success is announced;
- switch/consent state exposes semantic ON/OFF;
- focus is not moved unpredictably unless required to reach a blocking error.

## 15. Authority precedence

Page-specific frozen specs override this generic state model when they define a more precise state.

This document fills cross-page gaps; it does not reopen already frozen page contracts.
