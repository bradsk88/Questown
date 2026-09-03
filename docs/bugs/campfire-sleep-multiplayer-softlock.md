# Campfire-sleep multiplayer soft-lock (morning never comes)

**Status:** Root-caused from 1.19.2 bytecode; fix approach pending decision.

## Symptom (log.txt, 2026-09-03)

Two players both used the town wand on the lit campfire at night
(`beginCampfireSleep` → `startSleepInBed RIGHT` for `bradsk88` at dayTime 13092
and `othersk88` at dayTime 13212). Night never advanced; the game soft-locked.

## Root cause (verified against 1.19.2 vanilla bytecode)

Vanilla `ServerLevel.tick()` advances past the night only when
`SleepStatus.areEnoughDeepSleeping(percentage, players)` is true, i.e. when the
count of players passing `isSleepingLongEnough()` meets the
`playerSleepingPercentage` requirement. `isSleepingLongEnough()` is
`isSleeping() && sleepCounter >= 100` (~5s).

`sleepCounter` is **reset to 0** in two relevant places:

- `Player.stopSleepInBed(wake=false)` sets it to **100**; `wake=true` sets it to **0**.
- `Player.startSleepInBed` → `LivingEntity.startSleeping(pos)` calls
  `Player.b(pos)`, which sets `sleepCounter = 0` each time a player (re)enters a bed.

The campfire-sleep path is driven by **re-clickable wand use** on a shared
campfire with **per-player temporary beds**. With two players, any re-click (or
either player waking early) resets that player's counter. For the night to skip,
*both* players must hold an unbroken 100-tick sleep window at the same time. In
practice the shared campfire makes the windows overlap poorly, so the condition
is never satisfied → night never skips → soft-lock.

Single-player (log2.txt) skips instantly because one player's counter passes 100
with no second player to wait on.

## Constraint (from REQUIREMENTS.md §9.3)

The fix must **not** force `setDayTime` server-side (the mod runs in
multiplayer too), and for shared MP it must respect
`playerSleepingPercentage`.

## Fix options

1. **Keep counters from resetting on re-click.** In
   `CampfireSleepHandler.beginCampfireSleep`, if the player is already a
   campfire-sleeper and still sleeping, don't call `startSleepInBed` again
   (no-op), so a stray re-click doesn't reset `sleepCounter`.
2. **Nudge toward skip via the vanilla lever.** When all *campfire* sleepers are
   down, treat that as "enough sleeping" by briefly lowering the effective
   requirement — not by setting time, but by waking the non-participants is
   wrong; instead rely on option 1 + the fact that once both counters pass 100
   vanilla skips. The real bug is the reset; fix that first.
3. Reuse vanilla's own gate: only start the temp bed when the player isn't
   already sleeping, and never re-place it on re-click.

Option 1/3 (the same change) is the minimal correct fix: stop resetting the
deep-sleep counter on re-click.
