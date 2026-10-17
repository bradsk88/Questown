---
title: 'Villager stands on a bed trying to path over the wall ("can''t get to other room"), never gives up'
status: needs-triage
origin: 2026-10-17 playtest round 2 — user chat 05:47:36
---

"Villager is standing on bed and says 'can't get to other room' because they're
trying to go over the wall, but a bed is a half-block, so they can't jump - and
they're not giving up on that nav path."

## Log context (/tmp/dev-server-4.log)

- 05:46:39 `[WHY] job=hunter:sword IDLE -> COLLECTING_SUPPLIES` (the morning; the hunter
  heads for its sword/supplies in the store room), still COLLECTING_SUPPLIES at 05:46:43.
- No nav/stuck log line exists for this — the CANT_REACH need is the only signal.

## Two defects to separate

1. **Path choice**: A* plans bed-top → wall-top → down. The bed's collision top is
   0.5625, so the wall step that looks climbable from the bed's node isn't jumpable
   in practice. Related: round-2 door preference (`VisitorMobEntity.getPathfindingMalus`,
   DOOR_PREFERENCE_MALUS 0.5f, 3ac14aea) — a discount on door cells, not a penalty
   on wall-climb routes, so a wall route that is "shorter" still wins.
2. **No give-up**: the villager keeps re-trying the same path. Check what the
   CANT_REACH need does on repeat (re-path with a blocked-node memory, or fall back
   to the door).

Verify with a fixture before fixing: a bedroom whose bed abuts the wall shared with
the store room, door elsewhere.

## Follow-up (05:49:45)

"I can't even knock them out of it. I can't smack them" — the stuck villager does not take player knockback either, so the player has no manual unstick. Check whether VisitorMobEntity ignores player attacks/knockback (hurt/knockback overrides) — a stuck-state escape the player expects.

### Code read (2026-10-17, not yet a diagnosis)

- `VisitorMobEntity.hurt` refuses damage only when `getJob().shouldBeNoClip(...)`, which
  is `false` for `ProductionJob` (and the client throwing job) — so a hunter is hurtable.
- No `AttackEntityEvent`/`LivingIncomingDamageEvent` cancel, `setInvulnerable`, or
  knockback override for townies exists in `src/main/java`.
- Open question for the observer: does the villager flash red / play the hurt sound
  when hit? Yes → the hit lands and the knockback is absorbed (pinned bed↔wall, or
  the walk target re-pulls it each tick); No → something upstream drops the attack.
