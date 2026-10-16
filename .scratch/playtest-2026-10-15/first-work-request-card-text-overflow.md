---
title: '"Post your first work request" quest text bleeds past the card boundaries'
status: ready-for-agent
origin: 2026-10-15 playtest — user chat 02:58:51
---

# First-work-request quest card: text overflows the card

User report (in-game chat, 02:58:51):

> "Post your first work request" quest text is bleeding past the card
> boundaries

The first-work-request batch was posted 02:57:57 (`First-work-request batch
posted (day-2 board introduction)`), so the card is the one that appeared
right after the job-board quest completed.

## To find

- The card's title/flavor source (the `PostFirstWorkRequestQuestReward` /
  first-work-request batch construction in `TownQuests` + its lang keys).
- Whether this is (a) text too long for the card box (wrap/truncation missing)
  or (b) a card-width/layout bug shared by other cards.
- gui-lint coverage: is the flag-menu quest-card screen under the flow-layout
  linter, and does `[gui-lint]` report it, or is this an un-linted text-fit
  case (the sentinel's class-B "truncated hints / text overflow" bucket)?

## Finding + fix (2026-10-15, same session)

(a), not (b): the card box is fine; the title is too long and was drawn
unfitted.

- `QuestsScreen.renderCardContent` (line ~206) draws the card title as a
  single line: `ctx.text(recipeName.getString(), idX, card.coords().topYPadded(),
  TEXT_COLOR, false)` — no width check, no wrap (the card is fixed-height,
  `UNSCALED_CARD_HEIGHT = 42`, so wrapping would collide with the head row).
- Budget: `PagedCardScreen.CARD_WIDTH = 156` px; card padding is
  `BIG_PADDING = 10` per side; the top-right removal button claims the last
  13 px of the top row → title usable width ≈ 121 px.
- The FIRST_WORK_REQUEST title `menu.quests.first_work_request` =
  "Post your first work request on the job board" (45 chars ≈ 215 px in the
  8px font) — the only card title that long, hence the first visible bleed.
- The full sentence was already on the card's hover tooltip (the quest flavor,
  "Open the job board and post your first work request - the town needs to
  know what to make.", set in `MCQuest.firstWorkRequest`, persisted via
  `NBT_FLAVOR_TEXT` and synced in `UIQuest.Serializer`), and in the post-time
  chat broadcast. The title was the redundant overflow.

**Fix:** shortened the card title lang key to "Post a work request" (~95 px,
fits). `en_us.json`: `menu.quests.first_work_request`. Client-side only — a
client restart is enough; no server/world impact. The
`room.special_quest.first_work_request` key (identical text) is unreferenced
(legacy) and was left as-is.

**Follow-up candidate (not done):** no generic fit guard at the title draw
site — data-driven titles (`RoomRecipes.getName` room names, `quests.upgrade`
`"%s -> %s"`) could overflow the same way. A small ellipsis-fit helper in
`renderCardContent` would close the class; deferred (unverifiable headless,
playtest scope discipline).

