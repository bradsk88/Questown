---
name: questown-playtest-watch
description: >-
  Watches a live from-zero play-test the USER drives while the agent works, and
  turns the user's in-game chat feedback into tickets + fixes. TRIGGER when asked
  to "put me on a fresh server and I'll play", "watch the log while I play",
  "log what I say in chat", or to run a from-zero run where the user is the main
  driver and the agent observes + fixes between polls. This is the reverse of
  `questown-held-run` (agent drives, dev observes) — here the user drives and the
  agent is a secondary observer. SKIP when a unit test or the autotest suite
  settles the question (use `questown-unit-tdd-fix`), for a perf/lag incident
  (`questown-lag-repro`), or an MC version migration
  (`questown-mc-version-migration`). Consolidates the fresh-server launch, the
  honest polling model, the on-the-fly ticket-logging loop, live root-causing,
  and the isolated-worktree build discipline so a session does not re-derive the
  OOM / log-trap / state-loss traps.
---

# Questown play-test watch (user drives, agent watches + fixes)

The user is doing a from-zero play-test and is the **main driver**; the agent is
a **secondary observer** that (a) watches the live server log, (b) logs the
user's in-game chat feedback into tickets as it comes in, (c) root-causes bugs
as they reproduce live, and (d) works `ready-for-agent` items between polls.
Do NOT drive the run (do not place blocks, do not open menus for the user) —
that is the user's job. This is the inverse of `questown-held-run`.

## Watching: a proven Monitor, then a full-log re-scan each turn

Load this skill when the user says "watch the logs" (2026-10-25: it was skipped, and the user's
chat sat unseen twice before they said "Doesn't seem like you're watching").

1. Arm a Monitor on the offset-polling watcher, with `qt-watch-test` in the regex:
   `tools/agent-playtest/logwatch.sh run/logs/latest.log '<Dev>|qt-watch-test|<other patterns>'`
   (absolute paths). Not `tail -F | grep --line-buffered`: inside Monitor that delivered nothing
   for 30 min on 2026-10-25, missing a job change, though the same pipeline worked from Bash.
   Never end it in `cut` or `awk` (both buffer).
2. **Prove it before saying "watching":** `tools/ffeed.sh feed "say qt-watch-test"` and wait for
   that event. No event means no watch.
3. Chat lines read `]: [Not Secure] <Dev> ...`: match `<Dev>`, never `\]: <`.
4. Monitors expire after 30 min. On expiry, re-scan the log since the last event before
   re-arming. The log is the record; the watch only makes it timely.

## Launching the fresh from-zero server + client for the user

Before any gradle invocation, check for a live session (`pgrep -af '[r]unServer|[r]unClient'`).

1. **Gamemode first:** `run/server.properties` is shared with agent playtests, which set
   `gamemode=creative` (`questown-agent-playtest`). For the user's run, set
   `gamemode=survival` before launch (2026-10-25: "Why am I in creative mode?").
2. Wipe and launch: `rm -rf run/world run/logs run-client/diags run-client/.qtdiag-cmd`, then
   `ENABLE_DEV_COMMANDS=1 tools/agent-playtest/restart.sh <run-name>` (without the env var
   `_qtdev debug <flag> toggle_log <log>` is unregistered: `Unknown or incomplete command`, r20;
   client down, server stop via FIFO, server
   first, then client, waits for the join; launches with `-Dquestown.tp.fifo=true`, so
   `tools/ffeed.sh feed "<console command>"` reaches the server on 1.20.6).
3. **Op the user:** `tools/ffeed.sh feed "op Dev"` and see `Made Dev a server operator` in
   `run/logs/latest.log` (2026-10-25: `run/ops.json` was empty, and `/qt flag` was refused).
4. Arm and prove the watch (section above).
5. **Tell the user which earlier items still need their eyes.** Grep the last findings files for
   "needs the dev's eyes" / "unseen live" and list them (2026-10-25: r17 #15, the darker first
   row, was marked that way, never re-checked, and turned out mis-scoped).
6. **If the user's machine rebooted / lost power**, it is NOT a clean kill: the world was not
   saved-on-shutdown. Bound the loss window (`run/world/level.dat` mtime vs their last play)
   and do a clean server-first relaunch (dev-launch ref, "Hard machine reboot / power loss").

## The loop (each turn)

1. **Poll the log** (full re-scan of `run/logs/latest.log`, runtime lines only:
   `[Server thread`): new user chat, new errors/exceptions, BOP events
   (`took_bop` / `bop.earned`), townie registrations (`Registered entity with
   town`), menu navigation (`Opening flag menu`), campfire-sleep transitions.
   The Monitor's events are the timely copy; this re-scan is the record.
2. **Log new user chat feedback on the fly** → a ticket in
   `.scratch/<feature>/<slug>.md` (see `docs/agents/issue-tracker.md`). The exact
   capture (verified 2026-10-12 — this is how the day-1 playtest's 13 chat lines
   were collected in one pass):
   `grep -a '\[Not Secure\] <Dev>' run/logs/latest.log | sed -E 's/^\[([0-9A-Za-z]+ )?([0-9:]{8})[^]]*\].*<Dev>/\2:/'`
   (re-verified 2026-10-25 on both `run/logs/latest.log` and restart.sh's `build/agent-playtest/<name>-server.log`)
   **Run it at the end of EVERY turn, not only when it looks like one** —
   batching to session end is the exact failure that makes the user have to ask
   "capture my chat messages". YAML frontmatter discipline: single-quote any
   `title:` that contains a colon or starts with a quote. Mark status from the five
   canonical roles (`needs-triage` / `needs-info` / `ready-for-agent` /
   `ready-for-human` / `wontfix`) — `needs-info` when you need a live catch or more
   context, `ready-for-agent` when it's scoped + fixable.
3. **Root-cause bugs that reproduce live** — the log is the oracle. When an
   exception fires (e.g. the 1.20.6 `took_bop` `TranslatableContents` throw),
   trace it to the exact line and record the root cause in the ticket (that is
   the finding; a plausible hypothesis without a second evidence line is not).
   **A dev probe must read the SAME object at the SAME moment the production
   code does.** A probe that constructs its own copy, or reads after the
   consuming call, samples the probe, not the system: the Created-Air saga
   (2026-10-12/13) — `ItemStack.getItem()` returns `Items.AIR` for an EMPTY
   stack, so the post-`add()` `[craft-give]` probe logged "Air" and the
   hand-reproduction probe reading a fresh stack logged "Welcome Mat"; both
   were wrong moments, and both verdicts ("not reproducible", "registry
   corruption") fell the moment the production read-time was sampled. State-
   dependent getters are the trap class: empty/empty-consumed/initial/final all
   read differently.
4. **Work `ready-for-agent` items between polls in an isolated worktree** —
   NEVER run gradle in the main project dir while the user's session is live
   (it contends for `build/` + the daemon and can OOM-kill their server). Use
   `git worktree add --detach /tmp/<name> HEAD` → copy changed files → build. For an autotest
   there, first copy `run/server.properties` and `run/eula.txt` into it: without them
   `tools/autotest-parallel.sh` prints `sed: can't read .../run/server.properties`, exits 0 and
   runs nothing (r20) →
   (`compileJava` / `:test` / `importCheck` / `stranglerCheck`) →
   `git worktree remove --force /tmp/<name>` → commit in the main tree.

## Summon the user for a fresh server on a strong signal

The user is mid-play-test and will keep the current world going. Do not ask for
a restart on a whim. **Summon (`~/.local/bin/summon "<3-6 word context>"`) only
on a strong signal that a fresh from-zero run is the right move**, e.g.:
- A committed, build-verified fix changes behavior the user is actively hitting,
  AND the current world is already contaminated by the old (buggy) code (e.g.
  BOPs lost, state reset fired) — a clean world is how the fix gets tested.
- A state-corrupting bug has fired on the live world (mass reset, lost
  registrations) such that continuing layers bugs on bugs.
- The user has clearly finished the beat they wanted to test and the next beat
  is cleaner from zero.
When you summon, the context phrase names the concrete reason + what the fresh
server would let them verify.

## Before the server goes down (restart / teardown / world wipe)

The live log is about to become a static artifact, and any user report not yet
ticketed loses its evidence chain. **A restart to apply a committed fix counts
too** — the user queues more chat while they watch the fix work. Before
killing the server for ANY reason:

1. Run the chat capture NOW (do not jump to the loop section):
   `grep -a '\[Not Secure\] <Dev>' run/logs/latest.log | sed -E 's/^\[([0-9A-Za-z]+ )?([0-9:]{8})[^]]*\].*<Dev>/\2:/'`
   (re-verified 2026-10-25 on both `run/logs/latest.log` and restart.sh's `build/agent-playtest/<name>-server.log`)
   Then diff against `.scratch/<playtest-dir>/` — any user line without a
   ticket gets one now. (2026-10-14: the 04:34 fix-restart skipped this and
   10 server-19 lines + all 11 server-20 lines went unticketed until the
   user had to ask "capture my chat feedback as todo items first" — the
   fix-restart case is exactly when the sweep is needed, because the user
   is actively watching and keeps dictating.)
2. For each unresolved bug report ("X wasn't working"), read the decision /
   evidence lines while the log is warm — the log is the oracle (the
   `questown-status-why` loop: dump the decision, not the status).
3. File or update the tickets with the log lines quoted, THEN tear down.

(2026-10-12: the user's gate was "before you take the server down, check if
there is any indication of why the gatherer was not working" — the sweep found
a 15-minute `seeking_work` stall with full `[WHY]` dump evidence, and that
ticket is what survived the restart.)

## Traps (verified this session)

- **Dev log is ~90% compile output** — a generic `grep 'word'` hits echoed Java
  source. Anchor runtime greps on `^\[HH:MM:SS\]` / `[Server thread`.
- **`pkill -f` / `pgrep -f` self-match** — bracket the pattern (`[r]unServer`);
  a bare token in your own `bash -c` line kills your shell (exit 143).
- **pkill teardown can report "Command aborted" while SUCCEEDING (2026-10-14)** —
  the client→server pkill sequence came back "Command aborted" with both
  processes actually dead. Never read that as a failed kill and re-pkill in the
  same breath: verify process state with a SEPARATE call
  (`ps -eo pid,args | grep -E 'forgeserveruser|GradleWrapperMain' | grep -v grep | wc -l`).
- **`pgrep -f` phantom** — it can return your own bash pid; recheck.
- **FIFO writer pid (2026-10-13)**: `pgrep -f 'sleep 999999 <> run/server-cmd.fifo'` self-matches your own `bash -c` launcher (its cmdline contains the literal string) and hands back the wrong pid. The real writer is the `sh -c` wrapper — confirm with `ps -o comm=` (it says `sh`, not `bash`), then verify with `ls -la /proc/<pid>/fd` for `0 -> .../server-cmd.fifo` (a **pipe** link, not a regular file — `grep -c regular` will read 0 and look like a failure). Full procedure: the `questown-held-run` skill's FIFO section; the server must be launched with `-Dquestown.tp.fifo=true` (build.gradle gate) or the console feeds are silently dropped.
- **Fresh from-zero world: `rm -rf run/world run-client/saves`** before launch,
  and verify the new server's boot timestamp beats the wipe.
- **Containers and furnaces: read the block, not a log dump.** Container lists in the log are
  snapshots from whenever a job last dumped them. `tools/ffeed.sh feed "data get block x y z"`
  shows the live slots (2026-10-25: a stale dump made "no coal" reach the user; `data get block`
  showed iron ingots jamming the furnace output).
- **Command shapes: grep before feeding.** `grep -n 'literal(\|argument(' <Command>.java`.
  Shapes verified 2026-10-25: `_qtdev work_score <flag x y z> <root>` (the work seeker's per-job
  scores and gate reasons, the first move for "why isn't my X working?"), `_qtdev look_room <door
  x y z>` (room grid with furniture letters), `_qtdev unlock_skill <villager uuid> <root/job>`,
  `_qtdev nav <villager uuid>` (the live walk target, the path's target and end, canReach: the
  first move for "a villager is stuck / faces a wall". `walkTarget` != `pathTarget` means vanilla
  moved the target; see `docs/solutions/best-practices/vanilla-lifts-solid-path-targets-2026-10-25.md`.
  r20 spent three code readings guessing the target before this existed).
- **"Registered entity with town <uuid>": the UUID is the villager's**, not the town's. The same
  UUID again with a new entity id is a chunk reload, not an arrival (r20: told the dev a second
  villager had joined; it was the first one reloading).
- **Villagers vanish from probes, or "That position is not loaded": check whether the player is
  dead.** `data get entity Dev Health` = 0.0 means the respawn screen, and the town unloads (r20
  22:42 zombie, r21 23:17 spider).
- **`_qtdev` output: grep the output marker, not the command text** — the log
  lines are prefixed `[_qtdev ...]` (e.g. `grep -a '\[_qtdev inspect\]'`); the
  command you fed is never echoed. (2026-10-13: grepping `inspect economy`
  found nothing; `[_qtdev inspect]` found the tab dump.)
- **`Fullness changed from N to N` no-op spam** buries inspect output (many
  lines per second). Filter it: `grep -av 'Fullness'`.
