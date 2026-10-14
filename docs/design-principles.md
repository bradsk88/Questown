# Questown design principles

Standing design rules distilled from real design conversations. These constrain
*how we build and test*, not how one feature works. Prefer citing one of these
over re-litigating a principle that already caused a stall or a rework.

Grounded in the 2026 cook-storm postmortem: the reselect loop looked like a
"cook has one job" bug, but the 2-job unlock was necessary and *orthogonal* to
the actual cause — the town was **starved of coal**. Separating the two is the
whole lesson.

---

## Questown

### Unlock ≠ workability

A townie *knowing* a job (`unlocked`) is separate from that job being
*workable* (having its inputs). They are distinct stuck states:

- `NO_SUPPLIES` — has the job, lacks the item (e.g. cook cannot fuel).
- `NO_JOBSITE` — no workable block at all (e.g. no loaded furnace to extract).
- `NO_SPACE` — can't deposit the product (store full).

When diagnosing a stalled townie, ask **both** *"does it know this job?"* and
*"does the town have the inputs?"* Fixing one does not fix the other. A
single-job reselect is *not* necessarily an unlock problem.

### Quests are division-of-labor pedagogy

The *kind* of quest — **player supplies** vs **town produces** — is how
Questown tells the player *who* is responsible for a resource. They carry
different lessons and must stay distinct:

- **Coal** = player supplies ("your job, until the miner exists").
- **Mutton** = town produces ("townies can make that").

Do not collapse the two into one generic "resource request." The lesson —
*who does the work* — is the point.

### Job independence

The behaviour of job selection and unmet-need surfacing must not depend on
the *specific set* of jobs a root currently has: any individual job should be
removable (or unworkable) without meaningfully affecting the other jobs'
ability to be selected and to run. Consequences:

- No cross-job priority ordering — no "fuel before extract", no hardcoded
  fuel→cook tie-break, no per-job-set scoring bonus.
- The unmet-need hint is derived from the **union of live named needs**
  across the root's candidate jobs — a property of the world state — not from
  whichever job best-scored this recompute pass.
- A no-op today is not a no-op forever: an `always_consider` helper job (the
  cook's fuel/extract) is not a defect when its furnace is idle; the fix for
  a flapping hint is per-key liveness of the *hint*, not deleting or
  re-ordering the job.

Source: 2026-10-15 playtest discussion — the cook root's hint flapped between
coal / raw food / nothing because it named the pass's best-scoring job's need
and the near-tie cook jobs (including idle no-ops) rotated argmax.
Implemented as the union of live needs in `TownPossibleWork.refreshUnmetNeeds`
+ `UnmetNeedTracker.applyPass`/`primaryKey` (glossary: **Job independence
principle** in `CONTEXT.md`).

---

## The self-sufficiency test

### Test the town's conversion, not the player's resource-finding

The self-sufficiency run asserts *"can the town run on its own."* Anything a
*player* would bring in a real run (coal, starter loot) is a **fixture the test
seeds**, not something the test measures. We assert the town **converts** inputs
into progress (cooks, builds, completes quests) — not that it *finds* them.

### A stall from a missing player input is not a bug

The test has by design no player, so every "player's job" must be seeded. A
stall caused by a contribution the *player* would have made is **expected** and
must not be misread as a game bug or a reason to "fix" the game.

First question on any stall: *"in a real run, who supplies this? If it's the
player, the test just hasn't."*

---

## The onboarding flow

### Onboarding communicates roles, not just needs

Every beat tells the player *who's responsible for what, and why* — coal is
*theirs* (until the miner), mutton is *the town's*. Onboarding is a
division-of-labor lesson; beats are chosen to teach that, not merely to gate
progress.

### Trim onboarding aids that don't change the player's next action

The proposed "free 4 coal" starter was dropped: with **16 coal required before
the cook can even start**, 4 extra changed nothing about what the player does
next. A seed earns its place only if it **unblocks a real step or moves the
player forward**; otherwise it's clutter and should be cut.
