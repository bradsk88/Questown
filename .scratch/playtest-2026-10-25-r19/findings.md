# Dev playtest 2026-10-25 r19 (fresh world, worktree qt-wt-work at 6b97d52a)

Goal: first dev playtest of the miner batch's SKILL_UNLOCK quest ("Unlock a Cook skill") and the
canClaim crash fix (7c07e945).

## Setup problems (fixed live)

1. **Started in creative.** The worktree's `run/server.properties` had `gamemode=creative`, left
   over from agent playtests. The dev was set to survival over the console at 19:20 and the file
   changed to `gamemode=survival` (untracked, nothing to commit).
2. **`/qt flag` refused.** `run/ops.json` was empty, so Dev was not an operator. Opped over the
   console at 19:26. Why ops.json was empty is unknown: was it wiped with the world, or never set
   in this checkout?
3. **The agent's log watch missed both dev chat lines.** First, the pattern `\]: <` did not allow
   for `[Not Secure] `. Second, a trailing `cut` buffered the stream, so a 30-minute watch
   delivered nothing (memory: monitor-pipeline-buffering). Matching `<Dev>` and dropping `cut` was
   still silent: it missed the 20:01 cook job change, although the same `tail -F | grep` worked when
   run from Bash. Replaced it with an offset-polling watcher (scratchpad `logwatch.sh`, 5 s), which
   was confirmed live with a console `say qt-watch-test`.

## Findings

1. **Nothing to do while the hunter makes the first trips (design, open).** Dev, 19:40: "We've
   reached a point where I have nothing left to do but wait. Maybe should be asking me to build an
   empty room?" Today `tryPhase2_4_emptyRooms` posts the empty-room batch only after
   `hunterGathererBatchComplete()`, which includes the raw-food LOOT quest that only the hunter's
   extractions fill. Proposal: post it once the hunter JOB_CHANGE completes. The cook batch keeps
   waiting for both, and the villager-2 arrival keeps its raw-food gate. Awaiting the dev's
   go-ahead; applying it needs a server restart.

2. **The cook idles: the player's iron jams the kitchen furnace (bug/design, open).** Dev, 20:46:
   "Why isn't my cook working?" After its first mutton (20:01) the cook cycled seeking_work ->
   downtime. `_qtdev work_score ... cook` scored every furnace job ~0: "Town lacks required job site
   ... none have job blocks at state 1 - a smelter is present but unlit; the missing input is fuel".
   The dev pointed out there was coal in the chest, which the log confirms (8 coal at (-176, 200)). The
   furnace (-171, 72, 208, read with `data get block`) held mutton in the input, coal in the fuel
   slot, and **3 iron_ingot in the output**; `RecipesUsed` names raw-iron smelting. Vanilla will not
   burn while the output can't take the result, and cook/extract only extracts food. So the cook
   waits forever, and the diagnosis wrongly blames fuel. Fix now: take the ingots out. Options for
   the dev: (a) the cook clears non-food output to the chest, (b) a status/diagnosis that says
   "output full", or both.

3. **The first card on every paged screen is darker (GUI bug, open; r17 #15 was a mis-scope).**
   Dev, 20:5x: "Did we ever figure out why the first card on every paged UI is darker than the
   rest?" r17 #15 assumed it meant only the request picker and removed `AddWorkScreen`'s category
   tint. That was never checked live, and the dev now says every paged UI. Ruled out by code read:
   `PagedCardScreen.renderCards` matches the 1.19.2 golden; colour is reset to white before card 1;
   JEI `DrawableNineSliceTexture` doesn't touch blending; per-card colour hooks only tint by status.
   Likely 1.20.6 render state left over before card 1 (after the page fill and buttons). Next: a
   capture probe (`tools/porttest`, `QtDiagClient.shot`) on an agent client after the playtest.

4. **Move "Unlock a Cook skill" beside Cooked Food x8 (dev ruling, code edited, NOT built).** The
   dev, mid-playtest: the skill quest should sit in the cook batch, next to the food x8 quest, so
   it helps fill it. Code moved out of `addMiner` into `addQuestsForJobChangeAndFood`. While
   moving it, a collision turned up: the skill quest's wanted id (`cook_qt_extract`) equalled the
   cook JOB_CHANGE quest's, and `QuestBatch.markRecipeAsComplete` matches by id alone, so either
   quest could close the other. It now has its own id, `questown:skill_unlock/cook`, and
   `MCQuest.skillTreeRoot` also reads the old form. Not compiled or tested yet: gradle would
   rebuild classes under the dev's live server. This r19 world runs the old build (quest in the
   miner batch).

5. **Hunters bring too much wool (dev ruling, data edited, NOT built).** Dev, 20:59: "The rate of
   wool is too high for the hunter. It should be about 50% lower." r17 #9 set white wool to about
   1/5 of every `loot_tables/jobs/hunter_sword` table. Now about 1/10: each table's wool weight is
   a ninth of its other entries (9.6-10.1% per table). `HunterWoolDataTest` band changed to 8-12%.
   Applies on the next build or restart.

## Not reached

- The miner batch never posted in r19 (the run ended in the cook batch), so no player has yet
  unlocked a skill through the real skills tab and its confirm screen. Only the growth driver's
  flag-BOP path (`spendBopToUnlockSkill`) has completed SKILL_UNLOCK. This is the top check for
  the next run, where the quest sits in the cook batch.

## Fine (checked)

- Onboarding up to the hunter job change: empty-room, basics and first-work-request batches posted;
  first work request completed; gatherer -> hunter/sword at 19:39; gatherer -> cook/extract at 20:01.

Run ended 2026-10-25 21:00 at the dev's request (fresh run next). World + logs: `~/questown-worlds/r19-dev-playtest-2026-10-25.tgz`.
