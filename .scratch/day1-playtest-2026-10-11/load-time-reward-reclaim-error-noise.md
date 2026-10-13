---
title: 'World-load logs reward re-claims at ERROR + instant-reward WARN noise'
status: ready-for-human
---

# Load-time reward noise: 3x ERROR "Refusing to apply reward" + 3x WARN "instant-type reward loaded from disk"

Observed 2026-10-13 23:53:59, `/tmp/dev-server-10.log`, loading the
playtest world (flag at 28,69,46; campfire/gate/empty-room beats
complete, first morning not yet reached):

```
[Server thread/WARN] [Questown:Quests/]: An "instant"-type reward is being
    loaded from disk. This is probably harmless, but is also unexpected.   (x3)
[Server thread/ERROR] [ca.br.qu.QT/]: Refusing to apply reward more than
    once: Instantly{CampfireReadyToast}
[Server thread/ERROR] ... MCRewardList{children=InMorning{SpawnNewVillager[...]},
    Instantly{AddEmptyRoomBatch...}}
[Server thread/ERROR] ... Instantly{AddBasicsBatch[job_board+store_room]}
```

## Read of what happened (verify before acting)

Quest batches persist to flag NBT. On load, (a) the deserializer restores
the completed batches' instant rewards and marks them applied (the WARN is
its "unexpected but harmless" marker), and (b) the flag-initializer chain
("Running initializer (26 left)" ...) re-walks the completed onboarding
beats and claims each batch's reward again — the transient `applied`
guard in `Reward.claim()` refuses all three. So no double spawn/post;
the guards did their job. World restored correctly (user rejoined at the
flag, quests intact, per observation).

## Open questions

- Should the guard path log at INFO (it's a successful no-op) or stay
  ERROR (it's an invariant trip that ought to be rare)? If invariant
  trips are expected on *every* load of a post-onboarding world, ERROR is
  alarm noise and the real invariant is somewhere else.
- Why does the initializer re-walk already-complete beats instead of
  stopping at `completed_procedural_batches`? If that walk is deliberate
  recovery, say so in a comment; if accidental, stop it (cheaper load,
  no noise).

## Status (2026-10-14, user)

**HOLDING** — user does not yet understand the problem ("see 4"). Before
this can be discussed, the ticket needs a **plain-language explanation**:
what fires, why it fires at load, what the two options (silence vs fix the
double-apply) would each change. Agent to write that explanation in this
ticket when root-causing.

## Plain-language explanation (2026-10-14, root-caused)

**The log has two different lines, from two different places, and both are
expected on every load of a world where the early onboarding beats are
done — not a bug in the world.**

1. **The WARN ("An 'instant'-type reward is being loaded from disk...", x3).**
   When the flag's quest data is read back from the world save, every saved
   reward is restored. Rewards that have already been *applied* are saved
   with an `applied` flag (`reward_applied` NBT) and come back applied. An
   "instant" reward that was applied while the world was running gets
   persisted like that — and the deserializer's one-time constructor for
   that type was written with a comment saying a persisted instant reward
   was "unexpected". It was, back when the early beats had not completed
   yet. Now they always do, so the warning fires on every load. It is
   purely a stale comment turned log line: nothing is wrong with the
   world.

2. **The ERROR ("Refusing to apply reward more than once", x3).**
   The deserializer does not just restore the quests as they were. It
   *rebuilds* every batch from scratch and **replays each completed quest**
   (`QuestBatches.initialize` -> `filterOutDuplicateCompletion` ->
   `markRecipeAsComplete` per completed quest). Replaying the *last* quest
   of a finished batch goes through the normal completion path, which ends
   by **claiming the batch's reward**. But that reward was restored in
   step 1 already applied, so the reward's once-guard refuses the second
   claim and logs the ERROR. The guard is doing its job: no second spawn,
   no second quest-posting, no duplication. The error is just the guard
   tripping on a path that now runs on *every* load, at a level meant for
   "this should never happen". (The ticket's original read blamed the
   flag-initializer chain; it does not — the re-claim happens inside the
   quest deserializer itself, before the initializers run.)

**Why the replay exists:** it re-establishes batch structure after load
(fresh quest objects, coerced owner UUIDs) and de-duplicates a completion
that somehow sits in two batches (only the first one replays it). So the
replay must keep happening; the problem is that it goes through the
*completion* API, which bundles "mark quest complete" + "claim reward" +
"notify listeners". At load, only the first piece is wanted.

## The two options, precisely

- **A — Silence the guard (1 line).** `Reward.claim()` on an already-
  applied reward becomes a quiet no-op (or logs at INFO). The load log
  goes clean. Cost: if a *genuine* future double-apply bug appears
  (some new code path claiming a reward twice for real), it will be
  invisible instead of loud. The WARN still needs to be downgraded or
  removed separately (it is a different line).
- **B — Make the load-replay not claim (a few lines).** Give the rebuild
  a replay path that marks the quest complete *without* claiming the
  reward (the deserializer knows the reward's applied-state is already
  on disk; completion and claim are synchronous, so "quest complete on
  disk" always means "reward applied on disk"). The once-guard then
  fires only on a *real* invariant trip and stays at ERROR where it
  belongs. The WARN still needs the separate downgrade.

**Recommendation:** B for the ERROR (keep the guard honest), plus
silence/downgrade the WARN (stale comment, now-normal state).

**Drive-by finding (out of scope, note for later):**
`filterOutDuplicateCompletion` builds an `eqb` quest list it never reads —
vestigial builder, delete with the B change.

