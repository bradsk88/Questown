---
title: BlockClaimsTickLimit is dead config — claims never expire on a timer
status: needs-info
created: 2026-07-28
priority: p3
---

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
