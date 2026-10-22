---
title: Replace the "dirt on flag" free-food trick with a one-time card on the economics page
status: done
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:40:16 (`run/client2.log`):

> Rather than using the "dirt on flag" trick to get food. We should put
> a one-time card on the top of the economics page that catches the
> players eye (maybe red?) that says "click to get some free food to get
> started" (or similar)

## Notes

Replace the current free-food mechanic (placing dirt on the flag) with
an explicit one-time UI card at the top of the economics tab. Same
one-time boolean; different presentation.

## Update (2026-10-11 evening, from-zero run)

Re-asked in chat 23:13:01: "In my last batch I had requested an option in the
menu to learn about and get the free food onboarding thing." So this is still
open and the player is actively looking for it — a one-time card on the
economics page ("click to get some free food to get started") is the requested
shape. Pairs with `free-food-tab-red-dot.md` (the red-dot indicator).

## Triage notes

Design item: surface the free-food economy on a card. Needs a product decision.

## Decision (2026-10-13, design walkthrough C#2)

**Modify the existing `#questown:villager_food` unmet-need card instead of
adding a new card** (the food need is almost certainly on the economics page
day one). While `!givenBonusFood`:

- The food card carries a short, eye-catching (red) claim affordance on the
  card itself (must fit the card width — e.g. "Free food →"); the full
  sentence lives in the tooltip.
- The economics tab carries a red dot (pairs with
  `free-food-tab-red-dot.md`).
- Clicking sends a new `ClaimFreeFoodMessage(flagPos)`; the server's
  `TownFlagBlockEntity.claimBonusFood(ServerPlayer)` (new, owner holds the
  action) deposits **20 carrots** into the first registered container (the
  `FreeFoodCommand` path), falls back to the player's inventory when no
  registered container exists yet, flips `givenBonusFood` only after the
  food actually landed, and sends a **clear chat message of what happened**
  ("the town's storeroom received 20 free carrots" vs. "you received 20").
- **The dirt-on-flag trigger is removed** (`TownFlagBlock` use-item branch).
  `FreeFoodCommand` becomes a thin wrapper over the same owner method
  (10 → 20 for consistency).
- The one-time bit already rides NBT; the flag-menu payload gains a
  `bonusFoodAvailable` boolean for the client.

**Chicken arc note:** the arc's `WAITING_FOR_FREE_FOOD` beat transitions on
the bit flip (`ChickenArcTransitions` → `o.bonusFoodGiven()`), not on the
dirt trigger — replacement is structurally safe. Its hint copy
(`message.questown.chicken.hint.free_food`) must be re-pointed at the new
card.

Legacy flags with `givenBonusFood=true`: no card, no dot.

## 2026-10-12 follow-up: CTA lingers after the click (chat 17:55:32)

> The "free food" CTA should hide the instant it is clicked. Easiest way to do
> this would be by invoking the hide menu command.

After clicking the claim, the card's "Free food →" line stays visible until
the next flag-menu refresh (the `FlagInfo.bonusFoodAvailable` wire value only
updates on the next open). Client-side fix: optimistically clear the local
availability on the claim click (the server is the source of truth for the
once-only bit; a failed claim re-shows it on the next refresh). Alternative
per the user: close-and-reopen the menu after the claim.

## Resolved (audit 2026-10-22)

C#2 shipped: the food card is the claim affordance while `bonusFoodAvailable` (`TownEconomicsScreen` lines 109/153/211) and sends `ClaimFreeFoodMessage`. The 17:55 follow-up (CTA lingers after the click) is not addressed in code: the click sends the message and returns with no local clear (`TownEconomicsScreen.java:214`). Re-opened as a small agent item.
