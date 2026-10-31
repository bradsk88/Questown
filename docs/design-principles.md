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

---

## Day-1 player experience (2026-10-11 playtest distillation)

Distilled from the 25 day-1 playtest items (`.scratch/day1-playtest-2026-10-11/`).
These are heuristics for judging *new* feedback and for making calls on open
design items without re-litigating the underlying principle. The through-line:
**a day-1 player has no tribal knowledge — every state, request, and mechanic
must be legible, complete, and actionable from what the game itself surfaces.**
When a new feedback item lands, find the principle below it; the fix follows
from the principle, not from the specific symptom.

### Show the *why*, not just the *what*
Any state or request the game presents carries a cause or a satisfying
condition — surface it. The BOP block needs an "Earned by: Baker x2" line; a
job-unlock names the producer; a villager's tool request names the gate.
*Heuristic: if the player can ask "why is this here / why is it asking for
that," the game should answer without leaving the screen.*
(`bop-deposit-reason`, `job-unlock-asks-baker`, `villager-asks-for-unlocked-job-tool`)

### The UI reflects the world — hide empty, badge ready
A tab, section, or menu earns its place only when it has something to offer;
otherwise hide it, or badge it. The villager tab hides until the first
villager; economics hides until the first batch; the free-food tab red-dots
when there's something to claim.
*Heuristic: progressive disclosure — the chrome tracks the world's actual
state, and attention cues (red-dots, "!" bubbles) mark the ready-but-unseen.*
(`flag-menu-hide-villager/economics`, `free-food-tab-red-dot`,
`flag-incomplete-quests-indicator`, `job-board-no-requests-bubble`)

### Design the empty state as a teaching moment
An empty collection is not "no UI" — it's the moment to explain the affordance
and the next step. The empty job board says what requests do; the
first-villager arrival gets its own copy.
*Heuristic: never ship a pane whose empty case is a blank area.*
(`job-board-empty-state`, `villager-menu-first-villager-text`)

### Nothing raw reaches the player
Anything the player reads is a localized, human-readable string that describes
what actually happened. Bare resource IDs, i18n keys, and "Created Air" are
bugs.
*Heuristic: the last thing the player sees (a name, a chat line, a toast) is a
quality gate — verify it's localized and accurate before calling a feature
done.* (`quest-shows-raw-i18n-key`, `welcome-mat-craft-chat-says-created-air`,
`campfire-sleep-hint`)

### Pace notifications — no blasts, no churn
Coalesce events and keep the UI stable. A quest-complete chat blast and a
demand-quest that constantly replaces itself both erode trust and
readability.
*Heuristic: two changes to the same UI within a few seconds is a smell;
coalesce or throttle.* (`quest-complete-chat-blast-pacing`,
`demand-quest-constantly-replaced`)

### Make hidden mechanics visible
A mechanic that only works if you know about it needs an in-world or on-screen
cue: a diegetic "!" bubble, a card, a one-line hint. The free-food economy,
the job-board requests, and campfire sleep all need this.
*Heuristic: if a mechanic's value is invisible to a first-time player, add the
cue.* (`free-food-economics-card`, `job-board-no-requests-bubble`,
`campfire-sleep-hint`)

### Give orientation
Day-1 players get lost and forget where they are. Provide wayfinding (a flag
map / back-to-flag) and an at-a-glance "what do I need to do."
*Heuristic: any mechanic that sends the player off the flag owes them a way
back and a reminder of the goal.* (`flag-map-when-lost`)

### Scaffold day one: don't gate on scarce or distant resources
Day-1 progression shouldn't require resources that are far away or rare, and
the core loop (build a room → register it → it works) should be taught
explicitly and early.
*Heuristic: if day 1 stalls on "find a tree 200 blocks away" or "what even is
a room," the onboarding is gating on the wrong thing.*
(`onboarding-starting-trees-or-logs`, `empty-room-as-early-quest`)

### Click goes to the place you can act (deep-link)
When the player clicks a quest or card, take them where they can *act* on it,
not just where they can read about it. The welcome-mat quest click opens the
crafting tab.
*Heuristic: an on-screen object the player can click should, on click, move
them toward completing it.* (`quest-click-opens-relevant-tab`)

### Equivalent paths make equivalent objects
The same object is identical regardless of how it was created. A menu-crafted
welcome mat carries the same registration NBT as a world-crafted one.
*Heuristic: every creation path for an object is a test surface — a bug in one
path is a bug in the object, not the path.* (`menu-crafted-welcome-mat`)

### UI hygiene is a quality gate, not polish
Pixel-level correctness is correctness: z-order (crosshair not over menus),
hover affordances on every status icon, one-line hints.
*Heuristic: a status icon with no tooltip, or a HUD element drawing over a
menu, is a defect the same way a mislabeled string is.*
(`crosshair-renders-over-flag-menu`, `villager-menu-status-icons-no-tooltips`,
`hover-text-missing-question-mark-status`)

---

## Second round + walkthroughs (2026-10-12 playtest + design-walkthrough
rulings)

Distilled from the 2026-10-12 pm playtest (8 new tickets), the evening
from-zero run, and the C/E/F/G walkthrough rulings. Same shape as above:
the feedback below the specific symptom.

### A granted role can do its named work
The moment a townie holds a role, it must be able to actually *do* the work
the role is named for — not merely auxiliary loops. A cook with only
`extract` + `fuel` "had no cook jobs" (2026-10-12 playtest). The mirror
ruling still binds: never bulk-grant the full recipe tree
(`cook-skill-unlock` — recipes ARE the progression). The two together:
the initial set is a **working core** (enough to perform the named work),
with the rest gated on progression — never the working core *alone*,
never the whole tree.
*Heuristic: a role name the townie can't yet perform is a broken promise;
"starter set" means working core + gated extras.*
(`new-cook-has-no-cook-works`, `.scratch/cook-skill-unlock/`)

### Onboarding knowledge must survive being skipped
The chicken arc is one way to learn the game, not the only one. Anything a
tutorial beat grants or teaches (the flag map, the "what is a room" lesson),
the no-chicken path must also get, on its own schedule and in its own
place. "…even if they don't use the chicken onboarding"; "As a fresh
player without a chicken, I might not understand the first two quests."
*Heuristic: when a tutorial gate holds knowledge or a quality-of-life
grant, ask "what does the skipper get?" — "nothing" is a design hole, not
a feature of the chicken.* (`flag-map-when-lost`,
`empty-room-as-early-quest`)

### Quests complete on the player's action, not the system's effect
When the player's act *is* the quest (spending the BOP for a job change),
the quest closes at the act, not when the system's deferred effect lands
(the villager's job actually changing later). "I think it's more satisfying
for the player." The reward clock runs on player time, not game time — a
quest that closes when the backend finishes is a delay, and it feels like
one. (`change-job-quest-completes-on-bop-consumption`)

### A player action must wake the town
A posted request, a placed block, a completed quest — each owes the town a
visible change in behaviour on a human timescale. A request on the board
that never wakes an idle seeker breaks the board's promise that it is
where work gets started ("I tried adding a wheat seeds request. Waiting to
see if they wake up"). Nothing crashed; the *reactivity* is what was
missing.
*Heuristic: after any player action that should have an effect, ask "who
notices, and when?" — no answer is a bug even with a green server.*
(`seeking-work-leaver-sees-empty-board-room`)

### Never ask the world for what it already did
Quests and requests must track the world's actual state. The game knows
which villager already changed jobs and which need is already satisfied;
re-asking for either tells the player the game isn't paying attention.
Distinct from *pace notifications / no churn* (that is presentation
rhythm); this is **state accuracy** — a quest that targets a completed
fact is wrong, whatever its timing. (`change-job-quest-targets-specific-
villager`)

### Status names the true blocker, not the first-checked one
A generic fallback status ("can't find anywhere to work / no tools") that
masks a known specific blocker ("needs coal") is a lie by omission.
"I would have expected the cook to be asking for coal in their bubble and
nothing else." This sharpens *Show the why, not just the what*: surfacing
a cause is only honest when the **most specific known binding constraint**
beats the generic fallback.
*Heuristic: if the specific blocker is computable, the generic status is
the bug.* (`cook-status-says-nowhere-to-work-when-blocked-on-fuel`,
`econ-tab-top-blocker-missing-coal`)

### Onboarding anticipates the next bottleneck
The mirror of *Trim onboarding aids that don't change the player's next
action*: a batch should carry the upgrade the town is about to need, not
just the lesson of the current step — "this is about the point that
storage becomes an issue" (the cook/kitchen beat should add the storage
upgrade).
*Heuristic: a batch that leaves the town about to stall on the next beat
was one step behind; the queue stays one step ahead.*
(`cook-kitchen-batch-should-include-storage-upgrade`)

### Tease, don't CTA — but specific; quests are the native CTA
(The "P10" that `job-board-organic-introduction` cites from this doc — it
was referenced but never written; restored here.) In-game copy (chicken
hints, board empty-state) teases and invites, in the character's voice;
it never commands. The **quest** is the game's native "do this now".
But a tease must still be specific: the vague "maybe this will do
something soon" empty state was rejected — guide to the concrete next
action (the top requestable need).
*Heuristic: copy teases in character; quests command; and a tease must
name what it points at.* (`job-board-organic-introduction`,
`job-board-empty-state`, `ChickenArcHints` copy)

### Watch-list (emerging, awaiting the player's ruling — not yet law)
- **Onboarding guarantees are a contract.** "I was expecting a guaranteed
  cook at this point, but I got an arborist" — the player builds a mental
  model of who arrives when; randomizing a beat they read as guaranteed
  breaks it. Codify once the guarantee is actually decided
  (`expected-guaranteed-cook-got-arborist`).
