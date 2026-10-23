---
name: questown-agent-playtest
description: >-
  The agent plays Questown itself in a fresh world, like a player: places a flag, does the
  onboarding through the real screens, and every ~10 s narrates what it can see (screenshot +
  server log) and judges whether it makes sense, logging oddities as findings. TRIGGER when
  asked to "play the game from a fresh world", "scrutinize the statuses/menus/behaviours",
  "narrate what you see", or run an exploratory/stochastic playtest the agent drives. SKIP for a
  scripted held autotest (`questown-held-run`) or when the dev plays and the agent watches
  (`questown-playtest-watch`).
---

# Questown agent playtest (agent plays, judges, files findings)

First run: 2026-10-21 (findings `.scratch/agent-playtest-2026-10-21/findings.md`, 42 items, five
fixed the same day). The value is stochastic: the agent stumbles into states no fixture sets up.

## The loop does not stop at a checkpoint

The dev asked for a loop ("every 10 seconds narrate everything you can see and make a
determination on whether that makes sense") and later asked **"Why did you stop"** when the agent
reported after the first onboarding pass. Keep playing until blocked, until the dev redirects, or
until context is getting long (then commit findings and hand off). Report progress in short lines
as you go; do not end the turn to summarise.

**After a context compaction, re-read this whole skill before the next action.** The summary
keeps what you did, not this file's traps: r9 spent 20 minutes re-deriving "the first wand use
relights the campfire", which the Acting section already said.

## Setup (worktree `build/wt`, port 25585; leaves the dev's checkout alone)

1. Ask before stopping the dev's own session; if they say they aren't playing, stop it (client
   JVM by pid, then `tools/ffeed.sh feed stop`). Two clients + servers fit in RAM, but builds
   contend.
2. **An autotest launch in this worktree wipes `run/world`.** `build.gradle` deletes it before any
   `runServer -Dquestown.autotest=true`, and `tools/autotest-parallel.sh` captures its command
   that way. Since 2026-10-22 the script sets the world aside during the capture. A hand-run
   autotest in the worktree still wipes it, so tar the world first if you want to keep it
   (2026-10-22: the saved day-7 world was lost to a suite run). Tar only after the server has
   stopped and check the archive (`tar tzf <tgz> | grep -c region/` > 0): a tar that raced the
   final save came out 4.8 KB for an 18 MB world (r12).
3. Fresh world: `rm -rf run/world run/logs run-client/diags run-client/.qtdiag-cmd` in the
   worktree. `run/server.properties`: `difficulty=normal`, `gamemode=creative` (creative players
   aren't attacked, so no death-screen captures), `server-port=25585`.
4. `run-client/options.txt` (edit only while the client is **down**; a running client rewrites it
   on exit and your edit is lost): `guiScale:3`, `pauseOnLostFocus:false` (otherwise the pause
   menu covers every screenshot).
5. Console: copy `tools/ffeed.sh` and `run/pace-guard.sh` from the main checkout if missing, then
   `tools/ffeed.sh setup`. Server: `LANG=C.UTF-8 setsid nohup ./gradlew runServer
   -Dquestown.tp.fifo=true > <log> 2>&1 &`. Client after the server says `Done (`:
   `LANG=C.UTF-8 setsid nohup ./gradlew runClient -Pautojoin -Pautoport=25585 > <log> 2>&1 &`.
6. Speed: `tools/ffeed.sh feed "tick rate 40"` (dev: "I believe you can observe well at 40tps").
   A Minecraft day is then 10 real minutes.

## Observing

- Helpers (all from the repo root): `tools/agent-playtest/restart.sh <name>` restarts server +
  client and waits for the join (rebuilds; feeds `stop` only to a live server);
  `ground.sh <x> <z> [top=72] [bottom=58]` prints the column's top non-air y (the block to
  `clickheld`); on hills pass the range (`ground.sh x z 90 60`): r12 read "72" everywhere on a
  slope that rose to 100. `probe-block.sh <block-id> x y z [x y z ...]` (flat, unquoted triples)
  says which positions hold a block. `sweep.sh <flag x y z> [from-line]` summarizes the log
  window since `build/agent-playtest/mark` (job-change counts, extracts, quest broadcasts,
  arrivals) plus every townie's stats and the clock, then moves the mark;
  `sleep.sh <campfire x y z> <stand x y z> [wand slot=3]` uses the wand until the night skips
  (exit 1 with the likely cause if not).
- `_qtdev villager_stats <flag x y z>` logs each townie's server-side level, xp, fullness, mood
  and effects: check a screen against it before calling a number wrong.
- Read a command's output with `tools/ffeed.sh feed "<cmd>" --capture run/logs/latest.log
  "<pattern>"` (only lines written after the write; exit 1 when none match; the pattern is an
  extended regex since 2026-10-23, so `"a|b"` alternates: before that `|` was literal and every
  alternation pattern came back empty), never "the newest
  `entity data` line": r10 read another command's line and filed a false "BOPs consumed" bug.
- Which path a townie's job change took: `grep -a '\[board\]' run/logs/debug.log` names the
  board-cascade branch per change (`via work-roll`, `via starving-diner-stage`, `via
  ready-for-downtime`, `via idle-escape`, `via diner-ate`, ... or `stays: no branch applied`).
- `tools/agent-playtest/look.sh <name> [maxlines]`: screenshot plus new server log lines since
  the last look; `PARK="x y"` parks the cursor and waits a frame first (a screen opens with the
  cursor at the window centre, so a card there shows a stale tooltip). Then `Read` the PNG. It waits for a complete PNG (reading a half-written file
  fails `crop.py`'s `assert d[:8] == b'\x89PNG...'`).
- `tools/agent-playtest/zoom.py <png> <out> x0 y0 x1 y1 [factor]`: crop and upscale small GUI
  text (no PIL on this box). Use it for quest cards, tooltips, bars and bubbles.
- Chat history is in the client log (`grep '\[CHAT\]'`); on screen only the newest line shows.
- Status truth is the server log: `[WHY] job=<root>:<job>` dumps (gatherer, hunter and miner
  only, `JobStatuses.maybeLogWhy`; throttled to one per 4 s, so a flip can fall between dumps),
  `Job changed to`, `[Experience] +N` (one per finished trip), `[PROBE:supplies] TOOK`.
  Console `data get entity @e[type=questown:visitor,name="Villager 1",limit=1] Pos` and `data get
  block <x y z> Items` read world state. `_qtdev villagers` is **not** registered on a plain
  (non-autotest) server: "Unknown or incomplete command".
- Bubbles are focus-based (`NeedBubbleFocus`): one bubble at a time, nearest the crosshair
  (cone 0.9). A villager standing by the flag hides the flag's bubble; aim from an angle that
  leaves the villager out of the cone.

## Acting (QtDiagClient, `run-client/.qtdiag-cmd`, one command per line)

- `flagtab <type> <flag x y z>` opens a flag tab. Types: `quests`, `villagers`, `economics`,
  `crafting`, `blocks_of_progress` (not `bop`: the server throws `Unexpected menu type`).
- `click <x> <y>` (window pixels = the screenshot's own pixels on this box, 1x: read the target
  straight off the PNG and do not scale it; 2026-10-22 a 2x guess hovered nothing and produced a
  false "hover isn't capturable" note; same space as `cursor`) presses buttons and, as two clicks,
  moves an item between slots. `close` = Escape. `cursor 1 1` before a clean shot.
  **Screenshot before clicking**: screens resize as the town grows. The crafting tab's Craft
  buttons (x 345) sit at y 271/301/331/361/391 (stick, plate, sign, stairs, rod) while the flag
  shows 2 tabs. Once the first villager arrives it shows 4 tabs plus "Begin moving this town",
  and the rows move up to y 228/258/288/318/348. r13 clicked the old y, and the "wand" it used
  on the door was a plain stick.
- Known positions (guiScale 3, verified r13-r15): BOP tab "Take Block of Progress" (302, 354);
  change-job panel: hotbar slot 1 (231, 391) -> insert slot (230, 256) -> Unlock (264, 305).
  `vopen <type>` only opens the *first* synced villager. To give another villager a job use
  `qt jobs set @e[type=questown:visitor,name="Villager N",limit=1] <root>/<job>` (since 1fb248d1
  it is the Block of Progress path) and log it as an intervention. `inquire <flag x y z> <item>`
  (the flag's position; the board's opens nothing) opens the work-request confirm, Yes at
  (263, 385).
- Placing like a player: hold the item, then `_qtdev clickheld <x y z>` right-clicks the top face
  of that block. Item ids: `questown:flag_base`, `questown:welcome_mat_block`,
  `questown:town_wand`, `questown:job_board_block`, `questown:mineshaft`. Survival mats, wands,
  boards and mineshafts come from the flag's crafting tab (they carry the flag link); a `give`n
  mat has no link and silently fails to place. The placed block lands one *above* the clicked
  block: clicking a y75 floor puts a chest at y76, so address its container at y76 (r12 wrote
  swords into the floor). `clickheld` with an empty hand does nothing ("Dev has nothing in the
  main hand"). A campfire may sit up to 2 blocks above or below the flag (dd064f85). The wand's
  temp bed needs floor at the campfire's own level: on a lone pillar the wand says "No safe place
  to sleep near this campfire".
- With no player online the town's chunks unload and console `setblock`/`data` answer "That
  position is not loaded" (r12 read an earlier "Changed the block" line as success). Run
  `forceload add <x1> <z1> <x2> <z2>` first and `forceload remove` after.
- `item replace entity Dev weapon.mainhand with X` **overwrites** whatever was there (it ate the
  wand twice). Put tools in other hotbar slots (`hotbar.N`) and swap with `item replace ...
  from entity Dev hotbar.N`. **A crafting-tab Craft lands the result in your hand**: park it
  (`item replace entity Dev hotbar.N from entity Dev weapon.mainhand`) before the next hand
  replace, or the flag-linked item is gone (r10 ate a crafted mineshaft this way).
- An `item replace` on the hand while a flag/villager menu is open does not reach the client
  (vanilla only syncs the open menu's slots): Craft buttons stay greyed. `close`, replace, reopen.
- Rooms: 2-high bedrooms are fine since eeba8a2d (townies stand up beside the bed; verified
  r15). Furniture other than beds can still wedge a townie in a 2-high room (r9's cook on its
  furnace), so default to 3-high interiors (`fill x1 F z1 x2 F+4 z2 ... hollow`, F = floor). Build with `fill ... hollow`, then wand the door. Probe ground height first (`execute if
  block x y z minecraft:air run say ...`). `hollow` also closes the bottom face, so start the fill
  one block *below* the door's bottom half (the bottom face becomes the floor). With the floor
  level with the door's bottom, villagers stand stuck in the doorway: they can't jump the step
  under the wall above the door (2026-10-22: a hunter sat in COLLECTING_SUPPLIES for 2 minutes). Don't put the campfire in front of a door: sleeping via
  the wand relights it, and villagers path around lit campfires, so the door becomes a wall.
- Sleep: wand on the campfire. If the fire is out (after a night), the first use relights it and
  the second sleeps. Use once, then check the log for `startSleepInBed RIGHT` before using again:
  a use while asleep used to strand the temp bed and skip the campfire-sleep advancement
  (fixed 2026-10-22, but don't rely on it).
- Moving far: `spreadplayers <x> <z> 0 1 false Dev` lands on the surface; a plain `tp` can land
  inside blocks.
- `_qtdev build_room <door x y z>` (the chicken's shell) is 4x4 *including* walls: a 2x2 interior,
  on the side of the door with more open space (north on a flat tie). Read the side off the
  build log's room-shape dump before furnishing; a bed or block set on the wall row breaks the
  room ("Bedroom destroyed" 0.6 s after "Room became Bedroom", r9).
- Keep the cell right inside any door empty. A furnace or chest there makes villagers climb onto
  it: r9's cook stood on its furnace for 13 min without extracting (moved -> six extracts in 50 s).
- Clock: `time add N`, never `time set`: `time set 14000` reset the day count to day 0 mid-run (r9).
- Containers: `data get block x y z Items` first and write into an empty slot;
  `item replace block ... container.N` overwrites (it ate the 20 free carrots, r9).
- `[LOOTTRACE] ... -> false (extracted N/M)` prints a would-be count; read the quest card for the
  real progress (r9 showed 8/8 in the log while the card said 7/8).
- Looking at a villager (nameplate, bubble): villagers move between the tp and the shot, and a
  console `tp ... facing <x y z>` pointed the camera at the sky. Freeze the world, read the
  villager's `Pos`, stand ~3 blocks away on open ground and give the rotation explicitly:
  `tick freeze`, `tp Dev <x> <y> <z> <yaw> <pitch>` (yaw 90 faces -x), shot, `tick unfreeze`.
  Bubbles are focus-based, so only the villager under the crosshair shows one.

## Process hygiene (2026-10-22)

- Restart the client by killing the `java` whose cwd is `run-client` (`readlink /proc/$p/cwd`),
  then the `GradleWrapperMain` java whose cmdline has `runClient`. Never `pkill -f`/`pgrep -f` a
  pattern like `runClient -Pautojoin`: it matches your own bash command and kills the tool
  call (exit 144, twice this session).
- Stop the server with `tools/ffeed.sh feed stop` after the client is gone, and wait for its pid.
- Write launch logs under `build/agent-playtest/`: the session scratchpad disappeared mid-session
  and took the client log with it.
- `run/logs/debug.log` has the DEBUG lines `latest.log` lacks (`hunger_updates`, "gave up waiting
  for ingredients", "Reached max ticks ... Falling back to"): trace job loops there.
- A world with a huge flag (the 2026-10-22 cascade) once broke joining: "Tried to read NBT tag
  that was too big" in the client log is a server-side payload bug, not a client problem (fixed
  5422b91f).

## Judging and filing

- One findings file per session under `.scratch/agent-playtest-<date>/findings.md`; number the
  items, mark `(judge)` for taste calls the dev should make, and mark fixes `[FIXED date]`.
- **Before calling a behaviour a bug, grep all of `docs/` and `CONTEXT.md` for its keywords, not
  just `docs/adr/`.** 2026-10-21: "villager deposits never stack" was ticketed as a bug; it was a
  documented design rule in `docs/project-qa.md` (now CONTEXT.md "Unstacked deposit").
- Villagers are invisible only while away on a trip; at night they are home and visible. When you
  ask the dev what they see, ask about the same time of day you're asking about.
- A log line naming a UUID right after a reload ("Job changed ... for <random> [from X]") is the
  freshly built entity before its saved UUID loads: noise.
- "Verified live" for a fix needs a log line from the branch you changed (the `[board]` trace for
  the board cascade), not just the outcome.
  r10 marked the starving-diner guard verified when the hunter reached raw food through the
  diner's own overnight expiry (the starving flag was false, so the guard never ran).
- Before narrowing a guard keyed on a job root or special rule (a loot guarantee, a bypass), grep
  `**/COMPLEX.md` for the class name: r10 coded, tested and reverted a narrowing of
  `FirstHunterLootGuarantee` that `jobs/declarative/COMPLEX.md` documents as deliberate.
- Head the findings file with every intervention: server restarts, console item seeding,
  creative placements. After r10's narrated "story" the dev asked "so this wasn't a continuous
  playthrough?": it hid 8 rebuild-restarts and seeded food. When the dev asks for a continuous
  run, never restart the server; log bugs instead of fixing them live.
- Before diagnosing a quest/loot anomaly, trace where the item came from (`grep -a 'Replaced a
  slot at'` = console seeding; `[LOOTTRACE] extract` = a townie). Dev, 2026-10-23: "How did raw
  food even get into the town if the hunter didn't produce it?" r10's "a meal closed Raw Food"
  was the agent's own seeded beef.
- A mechanism claim in findings ("the flag is never set because ...") enumerates every writer of
  that state first (`grep -n '<field>.put\|set<Field>('` over the owning class). r12 committed a
  "`% 10` broadcast gate" cause for the starving flag; dawn's `makeAllTotallyHungry` also wrote
  it, and the real cause was a shadowed parameter (`e6debc70`).
- **A finding about the player's experience is tested on a player-made state and read on every
  surface.** r15 told the dev "the coal-less cook never mentions coal" and had to retract it. The
  cook was console-made (the old `qt jobs set` unlocked one job, not the root's default work), so
  its coal need could never register. And only the flag-tab tooltip had been read. Before such a
  claim, check that the intervention is the owner's path, and read all three surfaces: `_qtdev
  inspect villagers <flag>` (`need=` / `needs item:`), the in-world bubble (a shot facing the
  townie; one bubble at a time, nearest the crosshair), and the flag villagers-tab tooltip.
- `Fullness changed from X to Y` names no villager. Attribute meals from `_qtdev villager_stats`
  readings, not from those lines (r15 nearly credited the hunter's meal to the cook).
- Commit findings as you go; the job's temp dir is deleted with the job.
