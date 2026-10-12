---
title: Need an organic way to introduce the player to the job board
status: ready-for-human
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:46:32 + 22:46:47:

> We'll need to thin kabout how to organically introduce the player to the job
> board.
> As yet, I have not encountered anythign that would have driven me to check on
> it.

The player never had a reason to open the job board (the work-request board).
There's no in-game signal, quest, or moment that drives them to it. We need an
organic onboarding moment that makes the player discover the job board.

## Acceptance criteria

- A design that introduces the job board organically (a quest, a need, a villager
  prompting it, or a first-arrival beat) so a new player naturally opens it.
- The empty-state copy work (`job-board-empty-state.md`) supports this but a
  teaser alone won't drive discovery.

## Notes

Design discussion. Candidates: a day-1/day-2 quest that asks the player to
post a request; a villager who can't start because the board is empty ("I need
someone to request my first job"); a toast/teaser tied to the first-villager
arrival; the job-unlock quests already point at the board. Decide which beats
the onboarding and where it fires. Keep the "tease, don't CTA" copy principle
(`docs/design-principles.md` P10).

## Decision (2026-10-13, walkthrough F2)

**A day-2 quest — "post your first work request"** on the job board
(completes on any posted request; small reward; tooltip points at the board).
Quests are the game's native CTA; P10 tease copy stays for the board's
in-menu copy only. The onboarding quest (craft + place the board, shipped
2026-10-13 `75e8b081`) already handles object discovery.

Related re-scope (same walkthrough): `auto-request-root-initial-request` is
NOT a town-wide default — it becomes an **OPTION on the job board**: a
toggle checkbox in the board UI. When on, acquiring a root job whose
`initial_request` is set auto-posts that request. Default state: ON (the
board reads alive; the player can silence it) — confirm.

## Implementation notes (2026-10-12, lunch session)

**Correction to the assumption in `auto-request-root-initial-request`: the
job-change path ALREADY auto-posts the root's `initial_request`.**
`ChangeJobReward.getApplier` unlocks `defaultWork` and then loops
`ServerJobsRegistry.getDefaultWorkForNewWorker(town, work)` →
`work.get().initialRequest.apply(sl)` → `town.getWorkHandle().requestWork(item)`
for each. So "acquire a root → post its initial_request" is already live on
the BOP/job-change path; it is unconditional today.

What the toggle actually adds, then, is bounded:
1. **Persist a `boolean` on the flag NBT** (default `true`), say
   `board-auto-request`.
2. **A checkbox in the board menu** (`WorkRequestConfirmScreen` / the
   board `AbstractContainerMenu`), a new network message to flip it. This
   is the only GUI surface — needs `gui-lint` in a dev client before ship
   (AGENTS GUI convention).
3. **Gate the existing auto-post on the toggle** in `ChangeJobReward` (and
   wire the register/spawn path if it should honor it too — today the
   register path does NOT auto-post, only the job-change path does, so the
   toggle's "default ON" behavior is already correct on the only path that
   posts).
4. **Idempotency**: `requestWork` should dedupe against
   `getRequestedResults()` so re-acquiring a root doesn't stack duplicate
   requests. Verify the existing call already avoids dupes; if not, add the
   guard here, not at every call site.

**The day-2 quest is the larger half** and is a new `QuestType`:
- Add `FIRST_WORK_REQUEST` to `Quest.QuestType` + `MCQuest` factory +
  `SpecialQuests` id.
- Complete it from `TownWorkHandle.requestWork` (the single entry point for
  all posted requests) — mark the active `FIRST_WORK_REQUEST` quest complete
  the first time any request posts while it is ACTIVE. "Any posted request"
  per the decision; note the auto-post from item 3 above would satisfy it,
  so the quest only reads as player-driven when the toggle is OFF or the
  player posts manually. That's acceptable (quest completes on first
  request) but the player-facing "you discovered the board" moment is
  weakest exactly when auto-post is ON — worth re-checking against P10.
- Post the quest on day 2 (the morning after onboarding) — the onboarding
  batch (`addBasicsBatch`) is the model; a small day-2 batch that adds this
  single quest, reward = a small item grant.

**Scheduling open question (needs the user / a live server to feel out):**
when exactly does the day-2 batch fire? The quest only makes sense once the
board exists and a villager is present. Simplest: post it the morning after
the basics batch completes (board + store room done). Verify on a held run
rather than guessing the tick.

Both halves are design-locked; implementation is a full feature + GUI
surface, not a quick fix. Left as ready-for-human for the next pass.

## Shipped (2026-10-13)

Both halves are in:

- **Day-2 quest** — `FIRST_WORK_REQUEST` (`QuestType` + `MCQuest.firstWorkRequest`
  + `SpecialQuests.FIRST_WORK_REQUEST`). Posted from the basics batch's
  completion reward (`PostFirstWorkRequestQuestReward` inside the batch's new
  `MCRewardList`, alongside the tutorial kickoff reward) — i.e. the moment the
  board + store room land, not "the next morning": the quest is only doable
  once the board exists, so posting at build-completion is the earliest honest
  moment. Completes from the flag's work-handle listener on the first **add**
  (`TownQuests.markFirstWorkRequestCompleteIfActive`). Reward: 1 Block of
  Progress (`GrantBlockOfProgressReward`).
- **Auto-request toggle** — `TownFlagBlockEntity.boardAutoRequest` (default ON,
  NBT-persisted), gating the `ChangeJobReward` auto-post; the board screen
  carries an `[x] Auto-post new requests` row (`WorkScreen` bottom band) that
  flips the `TownWorkContainer` copy and sends `ToggleBoardAutoRequestMessage`;
  the server applies + re-opens the board to resync. `requestWork` is now
  idempotent (`TownWorkHandleRequestWorkTest`).

Remaining: in-game confirmation (quest appears, completes on first post,
toggle round-trips). Full docs: `town/entity/COMPLEX.md` "Day-2 first work
request quest + the board's auto-request toggle (F2)".
