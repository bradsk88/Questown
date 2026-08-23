---
title: BlockClaimsTickLimit is dead config — claims never expire on a timer
status: ready-for-human
created: 2026-07-28
updated: 2026-08-23
priority: p3
---

## Status: implemented (make-real), 2026-08-23 — pending human review

Decision (Brad): **make-real**, not delete. Rationale: these servers rarely restart, so
"self-heals on the next restart" is not a real safety net, and there is too much async chaos
on an MC server to trust a few specific clear-on-death/unload/job-change sites — a missed path
leaves an orphan forever. A TTL is the robust catch-all: it releases *every* orphaned claim
regardless of which abnormal-exit path produced it.

Implemented:
- `Claim.ticked()` → `Claim.ticked(long delta)` returns the decremented claim, or `null` once
  the TTL runs out so the caller drops it.
- `AbstractWorkStatusStore` now calls `decayClaims(ticksSinceLast)` (was
  `claims.replaceAll(… v.ticked())`). It decrements by the **game-tick delta** `ticksSinceLast`
  (the same value `timeJobStatuses` uses), so `BLOCK_CLAIMS_TICK_LIMIT` means *game ticks*, not
  *flag ticks* — its real duration no longer moves with `FlagTickIntervalV2`. A claim that hits
  0 is removed and logged (`"Claim at {} expired (TTL ran out) and was released"`).
- Because a townie re-claims its spot on every item insert (refreshing `ticksLeft`), the timer
  in practice only fires for *abandoned* claims; active workers refresh before it reaches 0.

Tests: 4 added to `WorkStatusStoreTest` (expiry releases the spot, TTL is in game-ticks not
flag-ticks, holds until TTL, re-claim refreshes). `WorkStatusStoreTest` 10/10 green.

Note on the residual trade-off (accepted): a single work step that runs longer than
`BLOCK_CLAIMS_TICK_LIMIT` game-ticks without re-inserting could lose its claim mid-work and be
re-claimed by another townie. Default 1000 game-ticks (~80 s at `FlagTickIntervalV2=10`) is long
enough that an active worker re-inserts well before it.

## What

`Claim` carries a `ticksLeft`, and `AbstractWorkStatusStore` faithfully decrements it once per
flag tick (`claims.replaceAll((k, v) -> v.ticked())`). **Nothing ever reads it.** A repo-wide
search for `ticksLeft` returns exactly two hits, both inside `Claim.java` itself: the record
component and the `- 1` in `ticked()`.

So `Config.BLOCK_CLAIMS_TICK_LIMIT` ("If a job claims a block. It will hold that claim for this
many ticks. (Or until they finish their work, whatever happens first)") describes behaviour that
does not exist. A claim is released only by `clearClaim(...)` or by the same owner re-claiming the
spot (`AbstractWorkStatusStore.doClaimSpot`).

Found while auditing what actually depends on `FlagTickInterval` (see [[HANDOFF]]). It was
initially mistaken for a pace bug — the decrement *is* denominated in flag ticks — but it is
inert, so the interval change does not affect it either.

## Why it is not just deleted

"Claims never expire" may be load-bearing. If a townie claims a work spot and then dies, is
unloaded, has its job changed, or gets stuck, the spot could be held forever — unless some other
path calls `clearClaim` for all of those. That is the question to answer before choosing:

- **Make it real** — have the store drop claims whose `ticksLeft` reaches 0. This is what the
  config promises, and it self-heals orphaned claims. Risk: a slow job legitimately holding a spot
  longer than the limit loses it mid-work.
- **Delete it** — remove `ticksLeft`, `ticked()`, and the config, and rely on `clearClaim`. Honest
  about current behaviour, but leaves orphaned claims unrecoverable if any exist.

## Deciding evidence to gather

- Every caller of `clearClaim` — is it reached on death, unload, job change, and job abandonment?
- Whether `CLAIM_SPOT` jobs in a long-running world accumulate stale claims (the per-owner
  `jobHandles` stores make this observable: claims map size vs live villager count).

## Note

If it is made real, the decrement must be changed to subtract the flag-tick interval rather than
`1`, for the same reason `tickDamage` was: the current `-1` per flag tick would make the limit
mean "flag ticks", not ticks, and its real duration would move with `FlagTickIntervalV2`.
