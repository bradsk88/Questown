---
title: Visitor batch that should guarantee a cook spawned an arborist instead
status: done
created: 2026-10-12
---

## Context

Playtest chat, 2026-10-12 19:13:21 (`/tmp/dev-server-7.log`):

> I was expecting a guaranteed cook at this point, but I got an arborist.

The player's mental model at the cook/kitchen quest stage: a cook should be
guaranteed in town. The batch that ran produced an arborist.

## Notes

Open questions (needs a live catch / log re-read):
- Which batch fired at 19:13 (visitor batch? kitchen-quest reward? the
  `Top-need batch posted: unlock baker + room breadmaker` at 19:14:12 is the
  adjacent event)?
- Is "guaranteed cook" a product expectation to encode (a guarantee entry in
  the batch, cf. `FirstHunterLootGuarantee`) or a misread of an existing
  reward?
- Check the 19:13 `SpawnNewVillager`/batch reward lines in the log and the
  batch data before designing the guarantee.

## Ruling (user, 2026-10-13) — D: guarantee like the hunter

"The player is still in onboarding. Through the same mechanism that we
guaranteed a hunter, we should guarantee a cook."

## Shipped (2026-10-13)

The hunter was guaranteed by the **guarantee slot** at BOP spend: the town's
first BOP was blessed with the hunter root (BOPs are blessed with the job
that earned them), and spending it on the change-job tab forced that root
(`guaranteedRoot` → `guaranteedRoots`, applied at morning; the `hunter/sword`
JOB_CHANGE quest completed at the spend moment via E1's
`markJobChangeCompleteAtSpend`). The cook quest had no such guarantee: no cook
existed to earn a cook-blessed BOP, so a plain/differently-blessed spend
pre-rolled an arbitrary root (arborist, in the 19:13 incident) and the
`cook/simple_furnace_food` quest never matched.

Fix: `TownQuests.onboardingJobChangeGuarantee(target)` — while an onboarding
JOB_CHANGE quest is open (owner null or this townie; oldest posted wins), its
wanted root **fills the same guarantee slot**, beating the BOP's own blessing
and the pre-roll. `TownFlagBlockEntity.requestJobRootChange` resolves
`onboarding quest root > BOP blessing > pre-roll` and passes the effective
guarantee to `scheduleJobRootChange`, so the promised root is stored in
`guaranteedRoots` and applied at morning exactly like a blessed BOP's.
Post-onboarding behavior is unchanged (blessing > pre-roll; the guarantee
returns empty once no onboarding job-change quest is open). The spend still
completes the matching quest at the spend moment (E1), so one spend = one
promised job; the next spend falls back to the normal rules.

Tests: 6 new in `TownQuestsJobChangeSpendTest` (unowned/owned/completed/
non-onboarding/oldest-wins/spend-completes). Full suite 1188/0/51skipped.
In-game confirmation pending (fresh playtest).

## Resolved (audit 2026-10-22)

Ruling D shipped: `TownQuests.onboardingJobChangeGuarantee` fills the BOP-spend guarantee slot with the open onboarding JOB_CHANGE quest's root (`TownQuests.java:1092` comment; 6 tests in `TownQuestsJobChangeSpendTest`). Live (partial): the 2026-10-22 agent playtests show a cook in r3, r5 and r7 (`.scratch/agent-playtest-2026-10-22-r3/findings.md` lines 58, 167, 278); those findings file no arborist-instead-of-cook recurrence. Not checked per round that the cook came from the onboarding spend.
