# Normal Home Feature Spec V1

## Scope

Normal Home is the HomeScreen state displayed when at least one valid FishRecord exists.

State selection is record-driven only:

- zero valid FishRecords → Empty Home
- one or more valid FishRecords → Normal Home

Login/session state, loading state and server statistics must not select Empty vs Normal Home.

## Product purpose

Normal Home answers three questions in order:

1. What did I catch recently?
2. What has my fishing record become so far?
3. How do I record the next catch?

It is a memory-led home, not a fishing analytics dashboard.

## Required regions

- Header: 渔见 + account entry/avatar
- Summary: 鱼种 / 鱼获 / 记录天数
- Recent section: 最近鱼获 + 全部
- Recent catch hero pager
- CTA copy: 记录下一条鱼
- Shared primary capture button

## Frozen product rules

- Recent real catch media is the first content focus.
- Statistics remain secondary.
- A single record is still shown through the same hero-card family.
- Multiple records are manually swipeable; there is no auto-advance.
- Capture enters the existing identify flow.
- There is no separate Normal Home gallery entry in V1.
- Guest and authenticated users share the same Normal Home hierarchy.
- Account identity only changes the account affordance, not the Home state.

## Out of scope

This package does not authorize:

- new tabs or navigation
- new achievement economy
- XP/level/rarity systems
- automatic slideshow
- ambient audio
- a new reusable lake bitmap
- backend/statistics schema changes
- Android implementation changes
