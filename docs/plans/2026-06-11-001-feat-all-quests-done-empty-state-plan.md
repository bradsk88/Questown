---
title: "All Quests Done" Empty-State on the Town Flag Quest Screen
type: feat
status: ready
date: 2026-06-11
issue: 236
origin: GitHub issue #236 ("Add 'all quests done' indication to town flag")
---

# "All Quests Done" Empty-State on the Town Flag Quest Screen

## Overview

When every quest on a town has been completed, the town-flag **quests tab**
currently shows a wall of green "completed" cards with no summary — the player
gets no signal that they are caught up or that a reward is coming. Issue #236
asks for an indication; its suggested text is:

> "You have finished all the quests, you will be rewarded in the morning"

This plan implements that as a **full-screen empty-state** on the town quests
screen (`QuestsScreen.forTown`): when all quests are complete, the card grid is
replaced by a centered message plus a one-way **"View completed quests →"** text
link that reveals the green cards. The reward sentence is shown only when a
morning reward is actually queued; otherwise a neutral "caught up" line keeps the
message honest.

Scope is deliberately the **town** quests tab only — not the per-villager
quests screen — because the morning reward is a town-level fact.

## Issue text (full)

The board item carries only the title; the GitHub issue body is one line:

> "At the very least, we should add a header like: 'You have finished all the
> quests, you will be rewarded in the morning'"

No labels, no comments. "At the very least" is read as a floor, not a ceiling —
the empty-state with a click-through is a deliberate step up from the bare
header, chosen during design review.

## Design decisions (resolved during grilling)

| # | Decision | Choice | Why |
|---|----------|--------|-----|
| 1 | What the indication *means* | Transient "all current quests done", **not** a latching "tutorial graduated" state | The issue is a UI header about the caught-up lull; quests regenerate continuously (tutorial phases + procedural garden + reward-spawned batches), so there is no natural terminal. |
| 2 | Surface | **Town** quests tab only; **not** the per-villager screen | "Rewarded in the morning" is a town-level fact (`morningRewards` lives on the flag BE). |
| 3 | Where the trigger is computed | **Server-side**, synced as booleans, computed from the exact `uiQuests` list that is serialized | Guarantees the empty-state can never disagree with the cards it hides. |
| 4 | What triggers the empty-state view | `allComplete` **alone** (non-empty && every quest `COMPLETED`) | The view's job is "no active quests", so its trigger is "no active quests" — not gated on a reward being queued. |
| 5 | The morning sentence | **Conditional** on `pendingMorningReward` | Keeps the promise truthful: show the morning line only when a morning reward is actually queued; otherwise a neutral line. |
| 6 | Click affordance | **Text link** ("View completed quests →"), hand-rolled hit-box in `mouseClicked()` | The screen already hand-rolls all its clickable regions (remove `x`, head hover, card clicks); a vanilla `Button` widget would be the odd one out. |
| 7 | Return path | **None** (one-way). Switching tabs / reopening resets to the empty-state | No value in a back link; the client `showingCompleted` flag is re-created on open. |
| 8 | Empty-list edge | Zero quests ⇒ **not** "all done" (`!quests.isEmpty()` guard) | `[].allMatch(...)` is vacuously true; would falsely claim "you finished all the quests" when there were none. |
| 9 | Testing | Unit-test the **pure predicates**; document the client-render gap | The autotest suite is server-driven gameplay and never opens a GUI, so it structurally cannot drive the click or read the rendered text. The real logic is fully extracted and tested; only pixels/clicks are uncovered. |

No ADR: this is a localized UI feature, not a hard-to-reverse architectural
trade-off, so it does not meet the ADR bar (hard-to-reverse + surprising + real
alternatives).

## Data flow

Two server-computed booleans ride along the existing town-quests sync path. No
new packet, no new sync channel.

```
TownFlagMenus.showUI  (flagEntity + uiQuests in scope)
  ├─ allComplete       = QuestCompletion.allComplete(statuses(uiQuests))
  └─ pendingReward     = flagEntity.hasPendingMorningReward()
        │
        ▼  thread both through ↓
  openMenu(...) ──► FlagMenus.writeAndLink(...) ──► TownQuestsContainer.write(buf, quests, pos, allComplete, pendingReward)
        │
        ▼  client
  FlagMenus.fromNetwork (read order must match write) ──► TownQuestsContainer{allComplete, pendingReward}
        │
        ▼
  QuestsScreen reads container.isAllComplete() / isMorningRewardPending()
   + client-only showingCompleted (default false)
```

`UIQuest.status` (`Quest.QuestStatus`, incl. `COMPLETED`) is **already**
serialized (`UIQuest.Serializer`, `UIQuest.java:240/264`), so the client already
knows per-quest completion; the new booleans are the server's authoritative
summary computed from the same list.

## Implementation steps

### Server — pending-morning-reward signal

1. **`town/quests/MCMorningRewards.java`** — add
   `public boolean hasPendingMorningReward() { return !getChildren().isEmpty(); }`.
   This is the generic "any morning reward queued" query; the existing
   `hasPendingSpawnVisitor()` is visitor-specific and must **not** be reused
   (it would suppress the morning line whenever the queued reward is items/quests
   rather than a new villager).

2. **`town/entity/TownFlagBlockEntity.java`** — add
   `public boolean hasPendingMorningReward() { return morningRewards.hasPendingMorningReward(); }`,
   mirroring `hasVillagerArrivingInMorning()` (`:500`). Needed because
   `morningRewards` is package-private and `TownFlagMenus` is in a different
   package.

### Server — pure completion predicate (the testable seam)

3. **New `gui/QuestCompletion.java`** — pure, no MC-registry deps:
   ```java
   public static boolean allComplete(Collection<Quest.QuestStatus> statuses) {
       return !statuses.isEmpty()
           && statuses.stream().allMatch(Quest.QuestStatus.COMPLETED::equals);
   }
   ```
   Operates on `QuestStatus` (a plain enum), **not** `UIQuest`, so unit tests
   need no `Bootstrap`/`Ingredient` construction. Call site maps:
   `allComplete(uiQuests.stream().map(q -> q.status).toList())`.

### Server — compute + thread the two booleans

4. **`town/TownFlagMenus.java`** (`showUI`, around the QUESTS shower at `:66`) —
   compute once before the showers:
   - `boolean allComplete = QuestCompletion.allComplete(statuses(uiQuests));`
   - `boolean pendingReward = flagEntity.hasPendingMorningReward();`
   Pass both into `openMenu(...)`. Prefer a tiny record
   `record QuestScreenSummary(boolean allComplete, boolean pendingReward)` over
   two loose booleans to keep the threaded signatures readable. (All five menu
   types route through `writeAndLink`, which always writes the quests-container
   data, so the summary is computed/written regardless of which tab opened — it
   is only *rendered* by the quests screen.)

5. **`gui/FlagMenus.java`** (`writeAndLink`, `:67`) — accept the summary and pass
   it to `TownQuestsContainer.write`.

6. **`gui/TownQuestsContainer.java`**
   - `write(buf, quests, pos, summary)` — after `writeQuests` + `writeFlagPos`,
     `buf.writeBoolean(summary.allComplete()); buf.writeBoolean(summary.pendingReward());`
   - Client read path (`ForClient` → `FlagMenus.fromNetwork`, whose read order
     **must match** write — see the `:45` comment) — read the two booleans and
     store them on the container; expose `isAllComplete()` /
     `isMorningRewardPending()`.

### Client — empty-state render + one-way toggle

7. **`gui/QuestsScreen.java`**
   - New fields from the container: `allComplete`, `morningRewardPending`; plus
     client-only `boolean showingCompleted = false`.
   - Override `cardsData()`: return `ImmutableList.of()` when
     `allComplete && !showingCompleted`, else the real `quests` list. (Live
     `Supplier` — `AbstractPagedCardScreen` re-reads it every frame at `:86`, so
     the toggle switches the grid with no re-init.)
   - In `render()` (after `super.render`): when `allComplete && !showingCompleted`,
     draw the centered empty-state in the empty title strip / content area —
     `getDisplayName()` is `""`, so the top strip is free:
     - title line: `menu.quests.all_done_title`
     - second line: `menu.quests.all_done_morning` if `morningRewardPending`,
       else `menu.quests.all_done_caught_up`
     - link line: `menu.quests.view_completed` ("View completed quests →"),
       highlighted on hover (reuse the `highlightAndTooltip` idiom), hit-box
       stored like the existing `removes` regions.
   - In `mouseClicked()`: early-return branch — if `allComplete && !showingCompleted`
     and the click hits the link region, set `showingCompleted = true` and return
     true (do not fall through to card/tab handling).
   - Paging buttons in the empty-state: cards are empty so the page reads 1/1 and
     the buttons no-op — acceptable. (Optional polish: suppress them while in the
     empty-state; not required for v1.)

   Keep `render()`/`mouseClicked()` additions as small extracted helpers
   (`renderAllDoneState(...)`, `clickedViewCompleted(...)`) — early returns, no
   nesting, per project style.

### Lang

8. **`assets/questown/lang/en_us.json`** — add:
   - `menu.quests.all_done_title` = "You have finished all the quests"
   - `menu.quests.all_done_morning` = "You will be rewarded in the morning"
   - `menu.quests.all_done_caught_up` = "You're all caught up — new quests will appear soon"
   - `menu.quests.view_completed` = "View completed quests →"

   (Exact key names to be confirmed against existing `menu.quests.*` conventions
   when editing the file.)

### Test

9. **New `src/test/java/.../gui/QuestCompletionTest.java`**
   - `allComplete([])` → false (empty guard)
   - `allComplete([ACTIVE, COMPLETED])` → false
   - `allComplete([COMPLETED, COMPLETED])` → true
10. **`MCMorningRewards` test** — `hasPendingMorningReward()` is false on an empty
    container, true after `add(child)`. (Construct with a stub `MCReward` child;
    add `Bootstrap` in `@BeforeAll` only if reward construction touches registries.)

Document in the test file header that the empty-state **render and click-through
are not covered** by the autotest suite (client GUI; the suite never opens a
menu), and that the extracted predicates are the regression-bearing logic.

## Out of scope / future

- A **chat message** at the true end of the tutorial (the friction documented in
  `docs/onboarding-player-experience.md` Step 8 — "the shift is invisible"). That
  is a distinct concern from this screen header; track separately. It also
  overlaps the known bug that `TutorialComplete` fires at *Phase 4 start* rather
  than the real end.
- Any **block-state / texture / particle** indication on the physical flag block.
- The per-villager quests screen.

## File touch list

| File | Change |
|------|--------|
| `town/quests/MCMorningRewards.java` | + `hasPendingMorningReward()` |
| `town/entity/TownFlagBlockEntity.java` | + `hasPendingMorningReward()` delegate |
| `gui/QuestCompletion.java` | **new** — pure `allComplete(statuses)` |
| `town/TownFlagMenus.java` | compute summary; thread into `openMenu` |
| `gui/FlagMenus.java` | `writeAndLink` accepts + forwards summary |
| `gui/TownQuestsContainer.java` | write/read 2 booleans; expose getters |
| `gui/QuestsScreen.java` | empty-state render + link + one-way toggle |
| `assets/questown/lang/en_us.json` | 4 lang keys |
| `test/.../gui/QuestCompletionTest.java` | **new** — predicate unit tests |
| `test/.../MCMorningRewards*Test.java` | pending-reward unit test |
