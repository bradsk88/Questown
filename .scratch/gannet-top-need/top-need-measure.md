---
title: Measure the town's top economic need (gannet experiment runs 1-2)
status: needs-triage
created: 2026-10-03
---

# Measuring the town's top economic need (gannet mission)

Question (from the `gannet` handoff): after cook, would
"unlock the job that produces the town's most-needed resource + build its base
room" organically surface **coal/miner**? The mechanism to measure against
(re-pinned in this session, 2026-10-03):

- Need ranking: `NoMCEconomics` (unmet-need queue, `timesNeeded` aggregate);
  the self_suff daily report prints the live ranking as `top needs:` lines.
- The need-driven batch already exists: `TownQuests.tryPhase2_75_topNeedUnlock`
  (**uncommitted** working-tree code) — gated on `jobChangesComplete() >= 2`,
  picks the top `ItemEconomicsData` need, maps it to producer roots
  (`producerRootsForItem`), filters out already-unlocked roots
  (`chooseProducerRoot`), posts `JOB_UNLOCK(producer)` + the producer's base
  room (`view.getRoomForJob` — for coal/miner that is the `questown:block_room/block.questown.mineshaft`
  room per `miner_short.json`).
- **`CoreProgression.getFirstJobChange` is dead in production** — only
  `CoreProgressionTest` exercises it. It is not the live ranking path for quest
  generation (the live path is the tutorial phases above).

## Run 1 — `run/server6.log` (intended coal=16, days=3; ran under defaults — see forwarding trap)

**Forwarding trap found while running:** `build.gradle`'s `server` block forwards
only a *whitelist* of `-D` properties to the forked server JVM; the
selfsuff knobs were not on it, so `days=3` and `coal=0` never landed. Run 1 ran
on `DEFAULT_DAYS=5` (I truncated it between day 4 and 5) and with the seed
default (16 coal — its log line says so). Fixed by adding `selfsuff.days` /
`selfsuff.coal` to the whitelist (uncommitted build.gradle edit); run 2 was
relaunched after the fix.

- Coal seeded: `[seed] seeded 144 opening food + 16 coal (player-input stand-in)`.
- Cook came up (`cookUnlocked=true`, roster 5: cook/gatherer×2/hunter×2),
  kitchen_small built, 8× cooked food completed.
- **Top need, every report (days 1–4): `#questown:villager_food=70`, then
  `#questown:swords` (16 day 2 → 26 day 4, rising).** Coal never appeared in
  the ranking and never ran short (no `NO_SUPPLIES` lock, no coal unmet).
- **The batch after cook never posted.** Gate `jobChangesComplete() >= 2` never
  passed: `JOB_CHANGE/ACTIVE: cook_qt_simple_furnace_food` stayed unclosed for
  the whole run. The cook townie cycled `cook/extract ↔ cook/fuel` every ~5s
  (the re-select/supply-starvation pattern from `cook-storm`); nobody ever
  held `cook/simple_furnace_food`, which is the only state that closes the
  quest. That job's parent is `cook/extract` and its input side was starved
  (raw food depleted; `LOOTTRACE extract hook: null=true empty=true`), so the
  work selector never picked it.
- Adoption is **exact-job match**: `TownQuests.processJobChanges` line
  `filter(v -> v.getValue().equals(wantedJobId))` — code-verified, not inferred.
- Read: with coal stock present, the town's top need is *cooked food* (and it
  wants *a cook who can actually cook*), not coal. The suspected
  "coal → miner" emergent beat requires coal to be in fact scarce *and* the
  cook chain settled.

## Run 2 (control) — coal=0 — DONE, `run/server7.log`

`[seed] seeded 144 opening food + 0 coal` — the coal=0 control held (the
build.gradle forwarding fix is verified by this line). 3-day window (7 days of
slept nights inside it), ended `07:56:47 WATCH complete`.

- **The cook never came up** (`cookUnlocked=false`) — the raw-food loot quest
  (the hunter's leaver run) never closed in this world's draw, so batch 2.5
  never even posted. This is a stochastic pre-cook bottleneck, independent of
  the coal setting (run 1's identical seed closed it in ~8 min).
- **Top needs over the run:** `villager_food` led early (70→77→81→73→58),
  then `swords` overtook (16→16→19→27→42→61) — **a mislabel, not a sword
  deficit** (see "What the line counts" below); the hunters demonstrably had
  a sword (see that section). Coal: **absent from every report** — with no
  cook, no fuel work ever registered a coal need.
- `[lock] NO_FORWARD_PROGRESS` fired at 2 days (roster 5, 1 quest open, stock
  130 flat).

## What the `top needs:` line actually counts (correction, user-caught)

The line is **not** a stock deficit. It is a **work-attempt-failure count per
label key**:

- `TownPossibleWork.applyScores` — when a root has no work above
  `MIN_JOB_ACCEPTANCE`, `registerUnmetNeeds(root)` fires for every townie in
  that root (`TownPossibleWork.java:188-192`).
- The label (`registerUnmetNed`, `TownPossibleWork.java:245-260`): **first
  initial ingredient of a random work in the root — falling back to its first
  *tool* when the input side is empty** (`x.initialIngredients.get(0)` or
  else `x.initialTools.get(0)`).

Per the runs:

- **`#questown:swords`**: the hunter job's input side is empty — it is a
  *leaver* job whose off-town work is an abstract **biome loot-table** roll,
  not entity collection (`CONTEXT.md`, Leaver; the loot comes from
  `loot_tables/jobs/hunter_sword/<biome>.json`) — so every failed work select
  labels the job's first tool.
  Run 2's 61 = ~61 failed work selects on that leaver job, not 61 missing
  swords. One durable sword serves all of it; the log shows the same
  leaver reusing it (repeated `[Experience] +200 from hunter:sword`).
  **No mobs are required for this job to run** (leavers run even with no
  animals in the world; `CONTEXT.md`).
- **`#questown:villager_food`** *is* a real input deficit — worker-sustenance
  ingredient (gatherer/hunter works declare it as an ingredient); the counts
  = failed worker feedings against an empty cooked-food stock.
- **`#minecraft:coals`** would register when the cook's fuel work gates on
  coal (its states declare `#minecraft:coals` as both tool and ingredient) —
  the one label that is a true coal signal; neither run held a cook in a
  coal-starved fuel state long enough to see it.
- Cross-category ranks are not comparable: a standing gate on an output-side
  job pins its tool label at the top (run 2 day 7: `swords 61` shadowing the
  real food deficit `39`).
- **Design impact on the emergent batch**: `tryPhase2_75_topNeedUnlock` reads
  `needs.get(0)` only and dies on no-producer labels (`chooseProducerRoot`
  returns null, batch skipped) — a `swords` at #1 would block the batch even
  if a real need with a producer sat at #2. It should walk the ranking to the
  first need with an unlockable producer, or the registration should stop
  tool-fallback labels for output-side jobs.

## Combined read (the measurement, corrected)

1. In both worlds the **real consumable deficit** is cooked food
   (`#questown:villager_food`). The `swords` entries are mislabeled
   work-select failures on a leaver job, not a weapon deficit.
2. **Coal never surfaced** because neither run held a cook in a sustained
   coal-starved fuel state (run 1 stocked 16; run 2 never reached a cook) —
   and because the batch reader only looks at `needs.get(0)`, which in run 2
   was a no-producer tool label that would *block* the batch even if coal had
   ranked #2.
3. The batch itself (`tryPhase2_75_topNeedUnlock`) was **never triggered in
   either run**: its gate is `jobChangesComplete() >= 2`, and the cook's
   JOB_CHANGE quest (exact-job `cook/simple_furnace_food`) never closed (run
   1) or never posted (run 2).
4. Before this ranking can steer a top-need → producer beat, two defects to
   fix (independent design calls): (a) the registration mislabels
   output-side failures as "missing first tool";
   (b) the batch reads only rank #1 and dies on no-producer labels.

## Which jobs produce an item — verified route (added 2026-10-03)

`scripts/job-producers.py <item>` answers producer/consumer from the job data
(schema: OUTPUT = `initial_request`+`result`; biome_loot output space = the
union of the biome tables; INPUT = `work_states[].ingredients`; TOOLS = held,
durable, not the product; BASE ROOM = `room`/`block`). Skill:
`.pi/skills/questown-item-producers`.

- **Swords: no job producer — verified, not inferred.** Full data scan (all
  60+ job files' `initial_request`/`result`, all 8 hunter biome tables,
  gatherer tables, recipes, job archive): the only sword references are the
  `hunter/sword*` family's **tool** (`#questown:swords`) and the
  player-crafted ITEM quests (wooden → stone, satisfied by
  `SupplySwordCommand` in held runs). The engine's own mapping
  (`TownQuests.producerRootsForItem` ← `ServerJobsRegistry.getResults` ←
  `work.results`) would also return ∅. Note: user held that swords *do* have
  an unlockable producer — this scan contradicts; confirm which producer was
  meant before the batch treats "swords" as producible.
- **Coal: producer `miner/coal` (room `block_room/block.questown.mineshaft`)**;
  consumers (input side): `cook/fuel` (`#minecraft:coals`), `baker/bread`
  (`#minecraft:coals`), `baker/stock_coal`. So the coal chain in data is:
  cook/baker demand → miner/coal produces → miner needs pickaxes + the
  mineshaft room built.

## Open findings for the next session


1. **Cook quest adoption gap** (design decision). Batch 2.5 closes only on the
   exact job `cook/simple_furnace_food` (`processJobChanges`, exact-match
   `filter(v -> v.getValue().equals(wantedJobId))`, code-verified), but under
   the 2-job starter set the work selector can leave the cook on extract/fuel
   indefinitely → batch 2.5 cannot complete → the emergent top-need batch never
   evaluates. Options: (a) let a cook-*root* townie satisfy the batch's
   JOB_CHANGE; (b) make simple_furnace_food selectable once its input (raw
   food) is present; (c) gate the top-need batch on the roster having a cook
   rather than on the cook quest closing.
2. **Coal demand is only measurable once a cook is live and fuel is demanded.**
   To actually test "coal emerges as a top need", a world needs: enough raw
   food to settle the cook + a cook holding the fuel job + a low/no coal stock.
   Run 2 removed coal *and* the cook's chance to register it; run 1 kept coal
   in stock. A third run (cook-settled world, coal=0 after cook is live) is the
   missing cell.
3. The daily report's `top needs:` line is the working measurement channel,
   but only for **input-side** (consumable) failures; tool-fallback labels
   (output-side jobs) are noise for the "unlock a producer" purpose.
4. **Sword mislabel** (user-caught, 2026-10-03): see "What the line counts"
   above. The two design defects the emergent batch must fix first: the
   registration tool-fallback (`TownPossibleWork.registerUnmetNed`) and the
   `needs.get(0)`-only batch read.
5. **build.gradle forwarding** (verified by run 2's `+ 0 coal` seed line):
   the `server` block only forwards a whitelist of `-D` properties to the
   forked server JVM; `questown.village.selfsuff.days` / `.coal` were missing
   and silently fell back to defaults. Added to the whitelist —
   **committed as b3953906** (with the AUTOTESTS.md note and this doc).
6. DevLootFire/DevWorkScore (`_qtdev loot_fire`, `_qtdev workscore`, DEV-DIAG)
   cannot be injected in hold mode (the autotest server does not poll the
   console/FIFO). Run controls for held self_suff must be scenario code or
   system properties (now forwarded).

## Run 3/3b — 4-wooden-sword quest (A/B against run 2)

Change: `TownQuests` sword quest qty 1 to 4 (uncommitted, user hypothesis).

- Run 3a (`run/server8.log`): world-specific nav deadlock: all 3 townies
  `CANT_REACH` for 10+ min, zero hunts. Not the sword variable. Killed, reseeded.
- Run 3b (`run/server9.log`): both hunters + gatherer working by ~2.5 min
  (run 2's second hunter sat out for the stone-sword delay) — **4x hypothesis
  confirmed for the second-hunter idle**. But: 8+ hunts, 25 dining events,
  zero LOOTTRACE meat matches; `[lock] NO_FORWARD_PROGRESS` at 3 days
  (roster 5, meat quest still ACTIVE, cook never unlocked).
- **Pre-cook gate refined:** not sword count, not meat-draw RNG alone — it is
  the **dine-vs-chest race**: each hunter eats its own drawn meat
  (`hunter:dining_raw_food` / `dining_no_table`), so the 1-meat loot quest
  closes only when a drawn meat survives deposit (run 1's 13-minute luck;
  runs 2 and 3b: never within the window).
- **Deposit quirk (bug, uncommitted):** the watch loop re-deposits the sword
  every 10s even after the quest is satisfied (4x x4 runs = ~16 wooden swords
  in the chest; quest close lags the deposit). Idempotency fix: check quest
  state before depositing (see handoff for the build list).

## Run 13 — new typed registration (post a817854c), healthy world — `/tmp/gannet-run13.log`

First run on the **new** unmet-need mechanism (typed `GateReason` + idempotent
`UnmetNeedTracker`, commit `a817854c`). World drawn healthy (hunting by ~90s);
same knobs as runs 1/3b (days=3, coal=16).

- **Labels are concrete, not random-tool fallback (the run-2 defect is fixed
  where it matters):** days 1–2 `top needs:` =
  `minecraft:wheat_seeds=14 minecraft:cod=12 minecraft:oak_wood=12
  minecraft:cobblestone=10 minecraft:white_wool=2` — actual item keys from
  `GateReason.classify` naming, matching the real gather/hunt/fish gaps. The
  old `#questown:swords` tool-fallback noise (runs 1/2) does not appear.
- **`#questown:villager_food` now appears only as a genuine input deficit:**
  day-3 report day 3: `#questown:villager_food=21` (3 townies × 7) leads while
  the gather-side needs persist — i.e. the cooked-food wall (cook never
  unlocked; the dine-vs-chest race of run 3b still holds), and *only then*.
- **Demand is windowed and bounded:** report `demand=` = registrations fired
  *during that day* (`NoMCEconomics.getUnmetNeedsWindowTotal`): 51 → 6 → 17
  (day 1 → day 2 → day 3). Day-2 decay (51→6, `consumed=37`) shows enter/exit
  clearing as needs are met — the run-2 unbounded growth (16→61 decay-less
  swords) is not reproducible: the per-(townie, item-key) rows cannot
  re-accumulate while a townie is hunting (root has viable work →
  `clearUnmetItemNeeds`), they only re-enter on genuine gaps.
- **Milestones all three days:** `cookUnlocked=false farmerUnlocked=false`,
  `locks fired: none` (3b's `NO_FORWARD_PROGRESS` did not fire this world).
- **Phase-2.75 batch still never triggered:** gate `jobChangesComplete() >= 2`
  never passed (only 1 job-change quest claimed — the hunter); same structural
  gap as runs 1/2 (open finding #1), now measured on the *new* registration.
- The one-shot `scoreDump` stall diagnostic added to this run (DEV-DIAG,
  executor) never fired — the healthy world never sat in `seeking_work` for
  3 heartbeat passes; the 60s stall it targets was absent.

**World-health note (this session):** three fresh seeds (runs 10, 11, 12/12b)
stalled in `seeking_work` 3–6 min with zero hunts before run 13 drew healthy
on the identical tree/knobs. Consistent with the run-3a reseed doctrine
(world-gen variance), but 1-in-4 healthy is a low hit rate; the stall shows
up as `townie/seeking_work` heartbeat lines with no `Job changed to hunter/sword`
follow-up — if it is a code path, that transition is where to start (this run's
diagnostic dumps `scoreDump(root)` on the same signature).

**FIFO verdict (asked to use):** `build.gradle`'s FIFO gate (238, gated on
`-Dquestown.tp.fifo`, independent of autotest) is wired at task config
(verified via init-script probe: `standardInput = FileInputStream(run/server-cmd.fifo)`),
but the forked server's `fd 0` is `/dev/null` regardless (checked run 12 and
12b, with and without `--no-daemon`) — the runServer fork does not carry the
stream. Combined with hold mode not polling the console, dev-command
injection on this branch remains **scenario code / forwarded system
properties only** (open finding #6, re-confirmed by direct fd inspection).

## Working-tree state (for the committer)

- ~~`build.gradle` — selfsuff forwarding~~ — **committed (b3953906)**.
- `TownQuests.java` — uncommitted: 2-job starter unlock + `tryPhase2_75_topNeedUnlock`
  (the emergent top-need batch) + the stuck-root `JOB_UNLOCK` path helpers.
- `VillageSelfSufficiencyExecutor.java` — uncommitted: `coalSeed()` knob +
  `COAL_SEED_DEFAULT` + the `questown.village.selfsuff.coal` read.
- `DevLootFireCommand.java` / `DevWorkScoreCommand.java` — untracked DEV-DIAG
  (raw_food hunt investigation, 2026-10-02); keep/remove per handoff.
- None of the above committed this session. Re-pin before committing.
