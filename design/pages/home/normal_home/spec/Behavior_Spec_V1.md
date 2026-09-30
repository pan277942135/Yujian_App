# Normal Home Behavior Spec V1

## 1. State selection

Normal Home is active when the resolved FishRecord collection contains one or more valid records.

Authentication, loading and server-statistics availability do not switch the page to Empty Home.

## 2. Recent-catch ordering

Recent catches are ordered descending by the sanitized resolved timestamp:

1. capturedAt when valid
2. createdAt fallback when valid
3. records without a valid timestamp sort after timestamped records

No separate editorial ranking is allowed.

## 3. Recent-catch pager

### One record

- one centered HOME hero card
- no fake neighboring card
- no automatic motion to another record

### Multiple records

- horizontal user-driven pager
- adjacent cards may be partially visible
- current page remains the primary card
- there is no automatic page advance

Selecting a catch opens that FishRecord detail by record ID.

## 4. Summary statistics

Three values are shown:

- 鱼种
- 鱼获
- 记录天数

Data precedence follows current product behavior:

- positive server species/catch totals are authoritative when available
- otherwise local record data provides the fallback
- record days are distinct sanitized catch dates from local records

Interactions:

- 鱼种 → Fish Guide
- 鱼获 → My Catches
- 记录天数 → informational only in V1; no navigation is authorized

The statistics row must not become the page's primary visual focus.

## 5. Section actions

- 最近鱼获 / 全部 → My Catches
- catch card → FishRecordDetail(recordId)
- primary capture button → existing Identify flow

There is no Normal Home “从相册选择” affordance in V1.

## 6. Account affordance

Authenticated:
- show the user's real avatar when valid avatar media is available;
- when avatar media is absent or fails to load, show **YuJian Default Profile Avatar V1**;
- default-avatar contract: `design/pages/home/normal_home/02_first_catch/default_avatar_contract.json`;
- tap either real avatar or default avatar → My / profile entry.

Guest:
- show approved guest/account affordance;
- tap → login/registration entry;
- Guest affordance is not the logged-in default avatar.

Account state must not replace Normal Home with Empty Home.

## 7. Media failure

The page remains Normal Home when a valid FishRecord exists but its image is temporarily unavailable. Use the existing approved media fallback/placeholder behavior; do not replace the page with Empty Home and do not introduce an error-dashboard state.

## 8. Refresh behavior

When record data changes, page state and values update from the resolved record collection. After deleting the final valid FishRecord, Home resolves to Empty Home. After adding the first valid FishRecord, Home resolves to Normal Home.

## 9. Interaction guardrails

- no auto-carousel
- no tap on decorative background
- no automatic haptic/audio on entry
- no hidden navigation attached to 记录天数
- no duplicate capture flow
