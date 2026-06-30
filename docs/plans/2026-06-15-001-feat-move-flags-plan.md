# Move flags (#199) — implementation plan

Companion to **ADR-0009** (town relocation via a dormant flag + reference deed)
and the `CONTEXT.md` terms: **town shutdown**, **dormant flag**, **relocation
deed**, **registered fixture**.

Build order is a sequence of tracer-bullet slices: each leaves the game
compiling and adds one observable capability, verified by the in-game autotest
suite (`docs/agents`/`questown-autotest`) and JUnit where the logic is pure.
Per CLAUDE.md: small functions, early returns over nesting, no deprecated APIs,
and never simulate core logic in tests — drive the real classes.

## Vocabulary → code map

| Concept | Where it lives today |
| --- | --- |
| Flag block / interaction / breakability | `blocks/TownFlagBlock.java` |
| Town data blob (serialize/restore) | `town/entity/TownFlagTileData.java`, `TownFlagBlockEntity.java` |
| Registered fixtures (abs X/Z, rel Y) | `town/rooms/TownPosition.java`, `TownRoomsMap.java`, POIs (`TownPois`) for mats/heal spots |
| Tick radius / config | `core/Config.java` (`TOWN_TICK_RADIUS`) |
| Roster / villagers | `getVillagerHandle()`, `getVillagers()` |
| Chicken-arc state (carried, not gated) | `TownFlagTileData` chicken-* keys |

## Phase 0 — Config + flag state scaffolding

- Add `Config.TOWN_SHUTDOWN_TICKS` (default 200) beside `TOWN_TICK_RADIUS`. Add
  the `-D`/config plumbing the same way existing values do it.
- Add a flag lifecycle state. Cleanest: a `TownFlagBlock` blockstate enum
  property `FlagPhase { ACTIVE, SHUTTING_DOWN, DORMANT }` (mirror the existing
  `INACTIVE` property pattern). Dormant + shutting-down flags do not tick the
  town (guard in `TownFlagTicker`).
- Keep the flag unbreakable in survival (already effectively true — confirm no
  `playerWillDestroy`/loot path exists, add a guard if needed).

**Verify:** `compileJava`; a dev command or unit assertion that a `DORMANT` flag
is skipped by the ticker.

## Phase 1 — Town shutdown ritual (no relocation yet)

Goal: trigger shutdown → townies recall + vanish → floor timer → flag flips to
`DORMANT`. No deed yet; just prove the ritual.

- `TownShutdownController` (new, in `town/`): owns the per-flag shutdown run —
  start, tick, cancel. Holds: start tick, absorbed set, roster snapshot.
  - **Recall:** set each townie (incl. leavers — they abort the current trip) to
    path to the flag base pos.
  - **Absorb:** when a townie reaches the flag (or its **force-absorb timeout**
    expires), remove the entity and add its VUID to `absorbed`. Roster data is
    already persisted in tile data, so nothing is lost.
  - **Completion:** `absorbed == roster` **and** `now - start >=
    TOWN_SHUTDOWN_TICKS`. Then flip the flag to `DORMANT`.
  - **Cancel:** before the last absorb, restore the town to `ACTIVE` and
    re-spawn/re-activate townies.
- Particle effect over the flag while `SHUTTING_DOWN` (client-side, driven by the
  blockstate).
- Entry point: interacting with the flag in `ACTIVE` state offers "Begin moving
  this town" (new flag-menu action or a dedicated interaction), guarded by
  *realtime + player present + not already shutting down*.

**Verify:** new autotest scenario `flag/town_shutdown` — spawn a town, trigger
shutdown, assert (a) all townies removed, (b) flag is `DORMANT` after the floor,
(c) a townie that can't path is force-absorbed on timeout. Drive the real
controller, not a stub.

## Phase 2 — Relocation deed item + dormant-flag interaction

- `RelocationDeedItem` (new, in `items/`): unstackable, non-craftable. NBT holds
  the **reference**: town UUID + original flag `BlockPos` + dimension key. No
  town snapshot.
- On `DORMANT`, completion of Phase 1 gives the player the deed.
- Dormant-flag interaction menu: **re-issue deed** (if lost) and **wake town in
  place** (abort the move → back to `ACTIVE`, re-spawn townies). This is the
  loss-protection path.

**Verify:** `flag/deed_issue_and_recover` autotest — shutdown → deed exists →
discard it → interact dormant flag → deed re-issued; and wake-in-place restores
an `ACTIVE`, ticking town with its roster.

## Phase 3 — agreed decisions (2026-06-29 grill)

Settled before implementation, via `/grill-with-docs`:

1. **Scope:** Phase 3 placement only (copy + re-anchor + destroy original + re-activate). Phase 4 far-away screen excluded (GUI blind spot).
2. **Entry point:** extract a `TownRelocation.place(level, deedStack, targetPos)` service; `RelocationDeedItem.useOn` is a thin adapter. The autotest drives the same `place(...)` directly (no `UseOnContext` mocking, no command layer).
3. **Test precondition (isolate Phase 3):** spawn an ACTIVE town, then in `PostSpawnAction` reach the dormant precondition via real sub-operations (absorb live villagers through the real `VillagerHolder`, flip `PHASE=DORMANT`, mint the deed via `RelocationDeedItem.forReference`) — skip the 200-tick floor + recall pathing (already covered by `flag/town_shutdown`). Then call `place(...)`.
4. **Success = assertions 1–8** in `flag/relocate_nearby`: (1) original flag destroyed; (2) new flag `ACTIVE` **and not `INACTIVE`** at target; (3) identity carried (same town UUID); (4) a registered door keeps its **absolute** world pos **and** its `scanLevel` changed; (5) atomicity (only end-state checked → reduces to 1+2); (6) roster respawned (count matches); (7) rooms reconstituted from the carried door; (8) knowledge/economics/known-biomes intact.
5. **TDD cadence:** (A) autotest-first integration proves it works end-to-end; (B) extract genuinely-pure functions into fast JUnit as a regression net. First red = `place(...)` present but unimplemented so the scenario compiles and fails meaningfully.
6. **B-suite (pure JUnit):** **B1** `planFixtureRebase(oldFlagY, newFlagY, fixtures)` — every fixture keeps absolute X/Z/Y across the move; **B2** precondition `validate(...)` → typed `RelocationResult` (`OK`/`CROSS_DIMENSION`/`MALFORMED_REFERENCE`). Skip B3 (too thin); world failure path (B4) stays out.
7. **Move geometry:** nearby horizontal offset **with a non-zero Y delta** (within `TOWN_TICK_RADIUS`), so the rebase is exercised; assert both absolute-pos-preserved and scanLevel-changed.
8. **Re-activation budget:** model on the shutdown blueprint (`realtimePhase=true`, `skipWarp=true`, fire `place` from `PostSpawnAction`, start ~300 ticks, bump if respawn/rescan are slow). "Not `INACTIVE`" is a `place(...)` post-condition.
9. **Failure path:** B2 (pure decision) + `place(...)` validates **before any world mutation** → "consume nothing / no half-transfer" true by construction. No world failure autotest this slice (deferred).
10. **Identity-carry mechanism:** new flag boots from the carried tile-data blob under the **same UUID**; `TownFlags` (UUID→entity map) self-heals. Exact data-injection seam discovered during impl; flagged with a failing assertion if the real code can't be driven (per CLAUDE.md). *(Found: data lives in `Compat.getBlockStoredTagData`, hydrated via `initPairs`; the BE's `final uuid = UUID.randomUUID()` must become adoptable.)*
11. **Copy fidelity:** whole-blob copy, then overwrite only origin-derived bits (fixture `scanLevel`s); audit `TownFlagTileData` for any other origin-relative fields during impl.

The heart. Placing the deed:

1. Resolve the reference; **load the original flag's chunk** (same dimension
   only — reject cross-dimension up front with a clear message). If the original
   is unreachable/gone, **fail loudly**, consume nothing.
2. Read the dormant flag's tile data.
3. **Re-anchor fixtures:** for every registered fixture (doors, fence gates,
   welcome mats, heal spots), recompute `scanLevel = oldAbsY - newFlagY` so the
   absolute world position is preserved under the new flag origin. Pure
   function — unit-test it directly (`TownPosition` rebase).
4. Create the new flag at the target with the copied + re-anchored data.
5. **Destroy the original** dormant flag (atomic with step 4 — no window where
   both exist).
6. Re-activate: let the existing room scan reconstitute rooms from carried
   doors; re-spawn the roster near the flag; resume ticking. Knowledge (incl.
   known biomes) is carried — **no biome re-scan**.

**Verify:** `flag/relocate_nearby` autotest — build a town with ≥1 room +
fixtures, relocate a short distance, assert: original flag gone, new flag
`ACTIVE`, rooms reconstituted, roster restored, jobs/knowledge/economics intact,
known-biome set unchanged. Add a JUnit test for the `TownPosition` Y-rebase.

## Phase 4 — "Far away" confirmation screen

- On placement, before committing, compute whether any carried fixture is
  outside `TOWN_TICK_RADIUS` of the target. If so, open a confirmation screen
  (reuse flag-menu GUI infra):
  - Copy: "Some of your town is too far away to come with you."
  - **[Bring it anyway]** (retain), **[Leave it behind]** (default / discard
    out-of-range fixtures), **[Cancel]** (abort, deed unconsumed).
- "Leave behind" drops only the out-of-range fixtures; in-range ones still carry.

**Verify:** `flag/relocate_far` autotest — relocate beyond `TOWN_TICK_RADIUS`;
assert the discard path drops out-of-range fixtures while keeping intangibles,
and the retain path keeps them registered (dormant until back in range). Screen
render/click is GUI-only (document as the usual server-autotest blind spot, like
#236).

## Phase 5 — Polish + edge cases

- Chicken-arc mid-move: confirm a mid-arc relocation re-derives beats at the new
  offsets (no special handling — verify it doesn't crash; document "tutorial
  follows you").
- Lang strings (`en_us.json`) for all menus/messages; deed item model/texture.
- Overlapping-town placement: out of scope for #199 (pre-existing hazard) — note
  in the issue, don't solve here.

## Test ledger

| Slice | Autotest scenario | JUnit |
| --- | --- | --- |
| Shutdown ritual | `flag/town_shutdown` ✅ | `ShutdownProgress` ✅ |
| Deed + recovery | `flag/deed_issue_and_recover` ✅ | — |
| Placement | `flag/relocate_nearby` ✅ (8/8) | `TownPosition` Y-rebase ✅ + `TownRelocation` B1/B2 ✅ |
| Far-away | `flag/relocate_far` (Phase 4) | far-away predicate (pure) |

**Phase 3 landed (2026-06-29):** `TownRelocation.place(...)` + `RelocationDeedItem.useOn`; the
`flag/relocate_nearby` autotest passes all 8 success criteria. Identity-carry required making the
flag's previously-`final` UUID adoptable (`adoptRelocatedIdentity`) — it was never persisted, so
town identity was transient per-instance. Fixtures re-anchored by rewriting door/gate `position_y`
in the copied tag before hydration (welcome mats/heal spots/block-rooms are stored absolute → no
rebase). Roster respawn is now persisted-roster-based (closing the transient-list gap noted in
Phase 2).

If a slice can't be driven through real interfaces, follow CLAUDE.md: note it,
add a failing assertion naming the missing seam, and continue with what's
testable.
