---
status: accepted
---

# The job board offers only fulfillable products

The **job board** (and the clipboard's **stock request**) let the player request products from townies. Until now both listed `ServerJobsRegistry.getAllOutputs(td)` — the outputs of *every* job in the registry, regardless of whether the town could make them. So a one-villager town's board advertised iron pickaxes, armor, and cooked food it had no way to produce. Roadmap #182 ("Job board should only show results that are currently possible") asks the board to reflect the town's actual capability.

The town already has a capability signal: a job is **unlocked** (`VillagerHolder.isUnlocked(JobID)`) when a townie has it — **starter jobs** unlock for free when a villager takes their root; **progression-unlocked** tiers cost a BOP spend in the skill tree. Crucially, the **work-seeker** already gates the work a townie will pick up by this same `isUnlocked` predicate (`WorkSeekerJob`), and unlocks are never revoked (the learning handle has no removal path).

**Decision:** `getAllOutputs` takes a `Predicate<JobID> jobIsUnlocked` and filters `Works.values()` by `work.id`. Both request surfaces pass `parent.getVillagerHandle()::isUnlocked`, so the board offers **only the outputs of currently-unlocked jobs**. The gate is **exact-tier**: a progression-locked product (an iron pickaxe, an armor piece) does not appear until *that tier* is unlocked, not merely because the town has a crafter. Locked products are **hidden**, not shown greyed-out. Gatherer-style outputs remain narrowed to **known loot** upstream (plus the always-known floor, e.g. wheat seeds), still behind the job's unlock.

The governing invariant: **what the player can request == what a townie will build.** Because the request screen and the work-seeker read the same `isUnlocked`, the board can never offer a request no townie will satisfy.

## Considered and rejected

- **Per-root gating** (show every `crafter/*` output because the town has *a* crafter). Friendlier — the board grows the moment you have a crafter — but it offers products no villager can yet make (the crafter hasn't unlocked the iron-pickaxe tier), breaking the fulfillable-only invariant and producing dead requests the work-seeker silently ignores.
- **`jobsKnownToExist` (awareness) gating.** The learning handle tracks jobs a town knows *exist* but hasn't unlocked. Using it would surface things the town is merely aware of — same dead-request problem, since awareness ≠ capability.
- **Aspirational / teaser board** (show locked-but-known products greyed-out to pull the player toward unlocking them). A legitimate *future* surface, but it needs a distinct non-requestable visual state and belongs to the discovery/progression features (#262 "Discover New Profession", #269 "Job Proficiency") — not this gate. Folding it in here would muddy the "everything shown is fulfillable" contract.
- **Gate the job board but leave the stock-request clipboard ungated.** The clipboard is fulfilled by the deprecated fetcher (`organizer/fetch`), not the work-seeker, so the work-seeker-consistency argument doesn't directly transfer. Rejected anyway: both screens draw from the same job-product set, the fetcher is vestigial/hardcoded, and a board that hides item X while the clipboard still offers X would be incoherent. (Revisit if the fetcher/`organizer` is ever redesigned to source items the town can't itself produce.)
- **Exempt the always-known floor (wheat seeds) so it is always requestable.** `TownKnowledgeStore` baseKnowledge always contains wheat seeds, so they are unconditionally in *gather* knowledge. But they reach the board only through a **leaver job**'s results; with no gather job unlocked, no one can fetch them, so a wheat-seed request would be un-fulfillable. The floor is a floor on a gatherer's *results*, not a board-always rule — keep it gated.

## Consequences

- `getAllOutputs(td)` becomes `getAllOutputs(td, jobIsUnlocked)`; the two callers (`TownWorkHandle.openMenuRequested`, `StockRequestClipboardItem.use`) pass `getVillagerHandle()::isUnlocked`. Filtering happens server-side before serialization; clients only read the filtered list.
- **A young / jobless town shows an empty board.** This is correct — nothing is fulfillable yet — and onboarding never exposes it: the first visitor is initialized as `gatherer/gather` (a starter), so the always-known wheat-seeds floor keeps the board non-empty by the tutorial's "make a request" step. The floor is therefore **load-bearing for onboarding**.
- No new orphaned-request states: unlocks are never revoked, and the work-seeker already gated fulfillment on `isUnlocked` before this change, so the already-queued request list needs no re-filtering.
- The `// FIXME: Only include jobs that are known by the villagers` in `TownPossibleWork.getJobsSortedByPossibility` is a *different* concern (the villager job-selection scorer, not the player UI) and is deliberately untouched.
- **Verification:** JUnit is infeasible (`getAllOutputs` needs the `Works` registry's server-boot init and a real `ServerLevel` for `initialRequest.apply`; simulating core logic is disallowed). Covered instead by the autotest scenario `ui/job_board_knowledge_gating` — a `LevelCheck` (a self-reporting `ServerLevel→boolean`, renamed from `WorldgenCheck`) that exercises the real registry with controlled predicates and the wheat-seeds floor: predicate-`false` ⇒ empty (the floor is suppressed when nothing is unlocked — encoding the floor decision above), predicate-`true` ⇒ the floor surfaces, and a single-root predicate ⇒ a non-empty strict subset of the full set. Green: `RESULT: 1/1 passed`.
