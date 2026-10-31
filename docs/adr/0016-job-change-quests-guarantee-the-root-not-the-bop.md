---
status: accepted
---

# Job-change quests guarantee a root job; Blocks of Progress don't (supersedes ADR-0015)

**Context.** ADR-0015 blessed every villager-earned BOP with the root job that earned it, and
spending one forced that root. In the 2026-10-30 evening playtest a miner's "gold" BOP turned a
villager into a second miner when the dev wanted a hunter. The dev's ruling: "I don't think
there's any reason for the flag to be outputting blessed bops, it should guarantee job changes
when job change quests are active using regular BOPs, then - when there are no change quests
active, it should be random again."

**Decision.**

- A BOP never decides a root. `JobRootChangeMessage` and the flag spend
  (`TownFlagBlockEntity.requestJobRootChange`) pass no BOP guarantee; the root is an open
  job-change quest's root (`TownQuests.onboardingJobChangeGuarantee`, applied at the spend), or
  otherwise the spend-time pre-roll.
- "Job-change quest is active" and the onboarding guarantee are the same condition today: every
  JOB_CHANGE quest the code posts (tutorial phases, quest paths, crafter phase 4) is
  onboarding-flagged by `markOnboarding`, and the save rebuild carries the flag. A future
  non-onboarding JOB_CHANGE quest has to decide whether it guarantees too.
- A deposited BOP still records the job that earned it (`BlockOfProgress.markEarnedBy`, NBT
  `earned_by`) for the flag BOP tab's "Earned by: ..." line. The item is a plain "Block of
  Progress" again (no Gold name, no "Guaranteed to change job" tooltip). `getEarnedBy` also
  reads the ADR-0015 `guaranteed_job` key, so old saves keep their "Earned by" line.
- `SimpleVillagerHandle.guaranteedRoots` (NBT `guaranteed_roots`) stays: it is now filled only
  by the quest guarantee, and a guarantee saved under ADR-0015 still applies once.

**Consequences.** The change-job panel has two lines (random, or the quest's named root); the
`insert_bop_blessed`, `unguaranteed_job`, Gold-name and guaranteed-tooltip lang keys are gone,
and `VillagerMenusPayload` drops `flagBopBlessed`/`flagBopRoot`. The flag's stack storage, FIFO
order and sparkle are unchanged.
