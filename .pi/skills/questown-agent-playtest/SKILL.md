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

## Setup (worktree `build/wt`, port 25585; leaves the dev's checkout alone)

1. Ask before stopping the dev's own session; if they say they aren't playing, stop it (client
   JVM by pid, then `run/ffeed.sh feed stop`). Two clients + servers fit in RAM, but builds
   contend.
2. **An autotest launch in this worktree wipes `run/world`.** `build.gradle` deletes it before any
   `runServer -Dquestown.autotest=true`, and `tools/autotest-parallel.sh` captures its command
   that way. Since 2026-10-22 the script sets the world aside during the capture. A hand-run
   autotest in the worktree still wipes it, so tar the world first if you want to keep it
   (2026-10-22: the saved day-7 world was lost to a suite run).
3. Fresh world: `rm -rf run/world run/logs run-client/diags run-client/.qtdiag-cmd` in the
   worktree. `run/server.properties`: `difficulty=normal`, `gamemode=creative` (creative players
   aren't attacked, so no death-screen captures), `server-port=25585`.
4. `run-client/options.txt` (edit only while the client is **down**; a running client rewrites it
   on exit and your edit is lost): `guiScale:3`, `pauseOnLostFocus:false` (otherwise the pause
   menu covers every screenshot).
5. Console: copy `run/ffeed.sh` and `run/pace-guard.sh` from the main checkout if missing, then
   `run/ffeed.sh setup`. Server: `LANG=C.UTF-8 setsid nohup ./gradlew runServer
   -Dquestown.tp.fifo=true > <log> 2>&1 &`. Client after the server says `Done (`:
   `LANG=C.UTF-8 setsid nohup ./gradlew runClient -Pautojoin -Pautoport=25585 > <log> 2>&1 &`.
6. Speed: `run/ffeed.sh feed "tick rate 40"` (dev: "I believe you can observe well at 40tps").
   A Minecraft day is then 10 real minutes.

## Observing

- `tools/agent-playtest/look.sh <name> [maxlines]`: screenshot plus new server log lines since
  the last look. Then `Read` the PNG. It waits for a complete PNG (reading a half-written file
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
  **Screenshot before clicking**: screens resize as the town grows (the crafting tab gained a
  "Begin moving this town" section on day 3 and every Craft button moved up).
- Placing like a player: hold the item, then `_qtdev clickheld <x y z>` right-clicks the top face
  of that block. Item ids: `questown:flag_base`, `questown:welcome_mat_block`,
  `questown:town_wand`, `questown:job_board_block`, `questown:mineshaft`. Survival mats, wands,
  boards and mineshafts come from the flag's crafting tab (they carry the flag link); a `give`n
  mat has no link and silently fails to place.
- `item replace entity Dev weapon.mainhand with X` **overwrites** whatever was there (it ate the
  wand twice). Put tools in other hotbar slots (`hotbar.N`) and swap with `item replace ...
  from entity Dev hotbar.N`.
- Rooms: build with `fill ... hollow`, then wand the door. Probe ground height first (`execute if
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
- Stop the server with `run/ffeed.sh feed stop` after the client is gone, and wait for its pid.
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
- Commit findings as you go; the job's temp dir is deleted with the job.
