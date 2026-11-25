# Recognition Semantic Invariants V1

Status: **FROZEN**

Scope: **semantic boundary for all Recognition degradation and accessibility modes**.

## 1. Principle

> Degradation may change expression cost, never Recognition meaning.

Quality Level, Reduce Motion and D0→D4 are implementation-expression controls. They are not product states.

## 2. Product Semantics

The following never change:

1. Processing remains:
   `图片识别中 → 已定位到鱼体 → 鱼种识别中`.
2. RESOLVE is a Result handoff, not a fourth Processing state.
3. High / Medium / Low retain the same confidence semantics.
4. No Fish / Image Quality / Technical Failure retain the same Issue semantics.
5. User Back remains a user exit, not Failure.
6. Degradation may not invent fake progress, extra loading states or replacement copy.

## 3. Visual Identity

The following never change:

- Original Photo is the visual foundation and first subject.
- AI Edge Field retains four Primary Energy Islands.
- Mandatory Quiet Gaps remain open.
- Island priority remains `B_UR > G_UL > G_LL > B_LR`.
- Fish Focus appears only after a real fish location exists.
- Fish Focus A/B/C remain spatially derived from the real detector bbox.
- Status / Back remain high Z-order but low visual weight.

## 4. Motion Semantics

`SegmentOffset` always means:

> location of the local luminous segment on the frozen path.

`StateStrength` always means:

> visual weight of AI Edge Field for the current Processing state.

`ResolveStrength` always means:

> shared disappearance strength during Result handoff.

Therefore:

- Quality switching never restarts the Motion Clock.
- Quality switching never changes the current SegmentOffset.
- Reduce Motion freezes continuous movement but does not change state sequence.
- Reduce Motion does not change StateStrength targets.
- Fish Focus breathing is an optional expression layer, not recognition semantics.

## 5. Degradation Boundaries

The following are frozen:

- FULL / BALANCED / LITE are complexity budgets, not themes.
- D0→D4 is a performance degradation ladder, not user-visible UI.
- Edge Field degrades FULL→BALANCED→LITE before Fish Focus A→B→C.
- D4 is the design floor.
- Reduce Motion is independent from Quality Level and D-level.
- Degradation never changes detector / crop / classifier / confidence / quality semantics.
- Platforms select from the frozen system; they do not create an unapproved visual mode.

## 6. Immutable relationships

These relationships survive every profile:

```text
Global visual priority:
Photo > AI Edge Field
```

Before real fish location:

```text
Edge Field = primary AI cue
Fish Focus = OFF
```

After real fish location:

```text
Fish Focus = primary AI cue
Edge Field = supporting cue
```

And always:

```text
Result semantics > visual fidelity
Accessibility preference > decorative continuous motion
```

## 7. What may change

The following may vary inside frozen contracts:

- Hairline / Node / Particle visibility;
- Glow / Receiving Light strength;
- Fish Focus A/B/C fidelity;
- continuous motion behavior under Reduce Motion;
- platform renderer implementation;
- platform-owned frame-time / GPU / thermal thresholds.

These changes are legal only if all invariants remain true.

## 8. What never changes

Never change:

- three-state product meaning;
- real-result gating;
- confidence semantics;
- issue semantics;
- photo dominance;
- four-island topology;
- Quiet Gaps;
- real bbox as Fish Focus source;
- Result routing;
- Back behavior.

## 9. Cross-mode equivalence test

Compare:

```text
FULL + normal motion + Fish Focus A
```

against:

```text
LITE + Reduce Motion + Fish Focus C
```

They may differ greatly in visual richness.

They must still communicate:

- the same current Recognition state;
- the same fish-location truth;
- the same confidence/result meaning;
- the same next user action.

If not, degradation has crossed the semantic boundary.

## 10. Hard failures

FAIL if any quality/accessibility/performance mode:

1. creates a fourth Processing state;
2. changes confidence or Result meaning;
3. changes Issue routing meaning;
4. shows Fish Focus before real location;
5. moves Fish Focus away from the real bbox;
6. removes the four-island identity;
7. closes Quiet Gaps into a frame;
8. turns Back into an error action;
9. restarts product progress because quality changed;
10. presents performance mode as a user-visible Recognition state;
11. changes copy to simulate progress;
12. changes detector/classifier behavior for visual reasons.

## 11. Acceptance shorthand

> Same truth. Same state. Same next action. Different visual cost only.
