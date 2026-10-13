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

## The one trap that defines the mode: the polling model

**You only check the log when you run a command in a turn. You do NOT poll
between turns.** Do not claim "watch mode" / "continuously monitoring." Say
plainly: "I check on each turn; nothing's lost because the log is a complete
record." The full re-scan each turn is the source of truth; a background watcher
is a bonus. The log-composition trap (dev log is ~90% gradle compile output,
incl. echoed Java source) and the exact watcher command live in
`references/questown-dev-launch.md` → "Reading the dev log while the user
plays". Read that section before the first poll.

## Launching the fresh from-zero server + client for the user

Before any gradle invocation, check for a live session (`pgrep -af '[r]unServer|[r]unClient'`).
To hand the user a clean from-zero world:

1. **Wipe the world**: `rm -rf run/world run-client/saves`.
2. **Kill the old server + client FIRST** (bracket patterns; `pkill -f` with a
   bare token self-kills — see the dev-launch ref). Kill order: client, then
   server, then any gradle wrappers.
3. **Launch the server first, then the client** (so a compile doesn't OOM-kill
   the server). Server: `nohup ./gradlew runServer > /tmp/dev-server.log 2>&1 &`
   (fresh log name). Verify generation: the `Flag block entity live` / boot
   timestamp must be AFTER your launch, and `pgrep -af 'forgeserveruserde[v]'`
   must show the server. Then the client: `nohup ./gradlew runClient -Pautojoin
   > /tmp/dev-client.log 2>&1 &`.
4. **On 1.20.6 the forked server's stdin is /dev/null** — FIFO command injection
   does not connect. Drive in-world state with in-game chat commands
   (`qt` / `_qtdev`), not the console.
5. **If the user's machine rebooted / lost power**, it is NOT a clean kill: the
   world was not saved-on-shutdown and *everything* is dead. Bound the loss
   window (check `run/world/level.dat` mtime vs their last play) and do a
   clean server-first relaunch — dev-launch ref, "Hard machine reboot / power
   loss".

Full launch discipline (kill-order, env, port check) is in
`references/questown-dev-launch.md` and the `questown-held-run` skill.

## The loop (each turn)

1. **Poll the log** (full re-scan, anchored to runtime lines `^\[HH:MM:SS\]` /
   `[Server thread`): new user chat, new errors/exceptions, BOP events
   (`took_bop` / `bop.earned`), townie registrations (`Registered entity with
   town`), menu navigation (`Opening flag menu`), campfire-sleep transitions.
   Also read `/tmp/dev-watch.log` (the background watcher's capture) as a
   cross-check.
2. **Log new user chat feedback on the fly** → a ticket in
   `.scratch/<feature>/<slug>.md` (see `docs/agents/issue-tracker.md`). The exact
   capture (verified 2026-10-12 — this is how the day-1 playtest's 13 chat lines
   were collected in one pass):
   `grep -a '^\[' /tmp/dev-server-N.log | grep -a '\[Not Secure\] <Dev>' | sed 's/^\[\([0-9:]*\)\].*<Dev>/\1: /'`
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
   `git worktree add --detach /tmp/<name> HEAD` → copy changed files → build
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
ticketed loses its evidence chain. Before killing the server for a restart or
tear-down:

1. Re-run the chat capture above; any user line without a ticket gets one now.
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
- **`pgrep -f` phantom** — it can return your own bash pid; recheck.
- **FIFO writer pid (2026-10-13)**: `pgrep -f 'sleep 999999 <> run/server-cmd.fifo'` self-matches your own `bash -c` launcher (its cmdline contains the literal string) and hands back the wrong pid. The real writer is the `sh -c` wrapper — confirm with `ps -o comm=` (it says `sh`, not `bash`), then verify with `ls -la /proc/<pid>/fd` for `0 -> .../server-cmd.fifo` (a **pipe** link, not a regular file — `grep -c regular` will read 0 and look like a failure). Full procedure: the `questown-held-run` skill's FIFO section; the server must be launched with `-Dquestown.tp.fifo=true` (build.gradle gate) or the console feeds are silently dropped.
- **Fresh from-zero world: `rm -rf run/world run-client/saves`** before launch,
  and verify the new server's boot timestamp beats the wipe.
- **`_qtdev` output: grep the output marker, not the command text** — the log
  lines are prefixed `[_qtdev ...]` (e.g. `grep -a '\[_qtdev inspect\]'`); the
  command you fed is never echoed. (2026-10-13: grepping `inspect economy`
  found nothing; `[_qtdev inspect]` found the tab dump.)
- **`Fullness changed from N to N` no-op spam** buries inspect output (many
  lines per second). Filter it: `grep -av 'Fullness'`.
