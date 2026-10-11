---
title: "NBT had no town state. Town state will reset" logged at flag placement (potential mass state-loss)
status: needs-info
created: 2026-10-11
---

## Context

Observed live in the 2026-10-11 evening from-zero run (server log `/tmp/dev-server.log`),
not a chat item:

```
[23:04:39] [Questown:Flag/]: [findflag] flag 44f4ab85-... registered at BlockPos{x=-193, y=63, z=40}
[23:04:39] [ca.br.qu.QT/ERROR]: NBT had no town state. That's probably a bug. Town state will reset
[23:04:39] [Questown:Flag/]: Nightfall: 0 of 0 townies have no bed for the night
```

`TownFlagState` (line ~161) logs this via `QT.logBug` when the flag's block NBT has no
`NBT_TOWN_STATE` compound on load; it then builds a fresh empty `MCTownState` (a reset).

In this instance it fired **once, exactly at flag placement** (the player placed the flag
at 23:04:39, ~10 min after joining), when the flag had **0 townies and 0 BOPs** — so no
data was lost. But the mechanism is a mass state-loss vector: if the NBT town state is
ever missing on a flag that *has* townies/BOPs/quests, the whole town resets.

## Acceptance criteria

- A freshly placed flag does NOT log "NBT had no town state" (the fresh path should
  initialize the state, not hit the missing-NBT branch).
- If the town state is genuinely missing on an established flag, that is a distinct,
  louder bug (data-loss risk) and should be investigated (chunk unload/reload? save
  ordering? a save that drops the compound?).

## Notes

Two interpretations to settle:
1. **Benign timing:** a just-placed flag has its state in memory but not yet serialized to
   NBT, so the first warp/night load sees no compound → logs the bug + "resets" to an empty
   state (harmless, matches memory). Fix: distinguish "fresh flag, expected to have no NBT
   yet" from "established flag lost its NBT" and only `logBug` in the latter case.
2. **Real corruption:** the flag's NBT lost `town_state` after being live (chunk
   unload/reload, save failure, or a code path that writes the block without the compound).
   This is the dangerous version. Check whether `getBlockStoredTagData` is the right NBT
   source on 1.20.6 (the block entity tag vs. the block-state tag) — a version-port NBT
   location mismatch would read an empty/missing compound even though the state is saved
   elsewhere.

To settle: reproduce on a fresh flag and check whether the compound is written to the NBT
source the loader reads (`Compat.getBlockStoredTagData`) on the first save. Grep for where
`NBT_TOWN_STATE` is *written* vs. *read* and confirm they use the same tag location.
