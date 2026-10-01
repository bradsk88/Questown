---
name: questown-lag-repro
license: MIT
metadata:
  version: 0.1.0
  author: Brad Johnson
description: >-
  Profiles the Questown real world for a lag incident (the "Can't keep up" / stutter) by enabling the dev-only TickProfile on a plain server and reading the whole-server-tick vs flag-tick split. TRIGGER when the user reports Questown lag/stutter/perf on the real world ("grinds to a halt", "Can't keep up", "half-waiting for server", "reproduce the lag", "profile the town"), or asks to profile the real town's flag tick. SKIP for a synthetic perf autotest (use `perf/*`), a single-file fix, or a MC version migration. Walks the ordered loop (plain server, console-to-file, FIFO, enable tickprofile, wait, dump, reset, re-dump) and reads the stall attribution. The load-bearing trap: the perf autotest wipes run/world, so the real world must be profiled on a plain server; and a reported number ("flag tick 0.6ms") is not verified until the command that produces it is run.
---

# Questown lag reproduction / real-world profiling

Profile the **real** world for a lag incident. The `perf/*` autotest is the
wrong tool here: it wipes `run/world` on boot (gradle `doFirst`), so it cannot
profile the real town, and its synthetic town is too small to reproduce the
incident. Profile on a plain server with the dev-only `TickProfile`.

## Read first

`references/questown-dev-launch.md` — the launch traps (flaky client daemon,
console-to-file, FIFO command injection, the `java`-process pgrep trap,
`pgrep -af java` classpath flooding). This skill is the *workflow*; that
reference is the *launch mechanics*. Read it before relaunching.

## The one rule that gates everything

**A reported number is not a verified number.** The handoff or another doc may
say "the flag tick stayed at 0.6ms" or "the room-detection O(n²) is the cause."
That is rough/inferred until you run the command that produces it. Do not build a
"ruled out / not the cause" conclusion on a reported number — a wrong "ruled out"
closes a live suspect. The phase split can change between two windows
(`biomes` 47%→16%, `roomsHandle` 33%→45% was observed); profile it, don't
re-report it.

## The loop

1. **Boot a plain server** (no `-Dquestown.autotest=true` — that wipes
   `run/world`), console redirected to a fresh log:
   `JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8 nohup ./gradlew runServer -PperfFifo > run/serverN.log 2>&1 &`
   (a bare `nohup` + `echo`; verify in a separate call; `serverN` = fresh name so
   prior crash/log evidence is preserved).
2. **Keep the FIFO writer open** before launch:
   `nohup sh -c 'sleep 999999 > run/server-cmd.fifo' &`. RCON is broken in this
   build; commands go through the FIFO.
3. **Launch the client** (`runClient -Pautojoin`, bare `nohup` + `echo`). The
   Gradle daemon for the client may "disappear unexpectedly" — that is not a
   crash and not OOM; re-launch. `Dev` auto-joins at the flag.
4. **Enable profiling**: `echo 'qt tickprofile' > run/server-cmd.fifo` (first call
   enables; it logs "enabled").
5. **Wait** a few minutes, then **dump**: `echo 'qt tickprofile' > run/server-cmd.fifo`
   (later calls dump `snapshotServer()` + `snapshot()` + `describePhases()` +
   stall attribution to the log).
6. **Read the dump** (see below). To test whether a stall **recurs** vs was a
   one-time spike: `echo 'qt tickprofile reset' > run/server-cmd.fifo`, wait,
   re-dump. A one-time spike at flag registration will not recur; a sustained
   cost will.

## Reading the dump

- **`server tick` line** = whole-server-tick. The "Can't keep up" stall lives
  here and is invisible in the flag-tick bucket.
- **`flag tick` line** = the flag-tick total (one part of the server tick).
- **phase split** (`biomes`, `roomsHandle`, `updateStoredData`, …) = where the
  flag-tick time goes. The `max` column is the worst single phase; a phase can be
  ~1us normally and spike (bimodal), so read `max`, not `avg`.
- **stall attribution** = the line that answers "is the server-tick max explained
  by the flag tick, or is there an unaccounted remainder?" A 1195ms server-tick
  with a 271ms flag-tick means ~924ms is outside the flag tick (chunk save / GC /
  another block entity) — that is the part to look for elsewhere, not the flag.

## `Can't keep up` = 0 is not "no lag"

Minecraft's "Can't keep up" is a *cumulative* threshold (ticks behind), so a
single ~1s server tick can be well under it. Do not report "no lag" from a zero
count; read the `server tick` max and the stall-attribution line instead.

## When the incident is a crash, not a stall

If the flag-load crashes the server (e.g. `IllegalStateException` out of a flag
tick), that is a separate bug — fix it first (it blocks the profiling) — then
re-profile. Confirm the fix live on the plain server (flag registers, no crash)
before attributing the stall.

## Do not commit throwaway diagnosis code without a decision

The diagnosis code (`TickProfileCommand`, `ServerTickProfiler`, `FindFlagCommand`,
`TownFlags` logging) is throwaway investigation tooling. Commit a real fix on its
own; do not bundle the diagnosis code without a decision (repo has a prep-commit
gate).
