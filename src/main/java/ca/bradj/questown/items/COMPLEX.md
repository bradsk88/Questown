# Campfire sleep — temporary bed lifecycle

`CampfireSleepHandler` lets a player "sleep" at a lit campfire before beds exist
(early tutorial). It fabricates a **temporary red bed**, sleeps the player in it,
and removes it on wake. The subtlety: it mutates world blocks, so it must never
place the bed on top of a real block, or removing the bed deletes that block.

```mermaid
flowchart TD
    A[beginCampfireSleep] --> B{campfire LIT?}
    B -- no --> Bx[message: not_lit / return]
    B -- yes --> C[findSafeSleepPosition]
    C --> D{"scan 4 cardinal lanes:\nfoot & head each isSafeToLieOn?"}
    D -- none --> Dx[message: no_safe_position / return]
    D -- first valid lane --> E[placeTempBed:\nsetBlock head+foot = RED_BED]
    E --> F[startSleepInBed]
    F -- LEFT problem --> G[removeTempBed: head+foot = AIR]
    F -- RIGHT sleeping --> H[/player sleeps.../]
    H --> I[onWake] --> G

    subgraph guard [isSafeToLieOn → isClearForSleep]
      J["below isFaceSturdy?"] --> K["isClearForSleep(pos) &&\nisClearForSleep(pos.above)"]
      K --> L{"mat.isReplaceable()?\n(THE GUARD)"}
      L -- no --> Lx[reject spot]
      L -- yes --> M{"unobstructed &&\nnot liquid/fire?"}
    end
    D -.uses.-> guard
```

## Why the replaceable guard exists (bug 2026-07-08)

`placeTempBed` and `removeTempBed` are **unconditional** `setBlock`s — place writes
`RED_BED`, teardown writes `AIR`. So a lane is only safe if its head/foot blocks are
air or otherwise replaceable. `isClearForSleep` originally checked only
entity-obstruction + liquid/fire, so a **solid** block (the town flag,
`Material.STONE`) counted as "clear": the bed overwrote the flag — destroying its
`TownFlagBlockEntity` and all town data — and wake-teardown then left `AIR`. The
`!mat.isReplaceable()` early-return closes it: the bed now only ever seats where the
teardown-to-air is correct.

Two conditions must both hold for the bug to bite, which is why it hid in a flat
test arena (see the `flag/campfire_sleep_preserves_flag` autotest):
- The flag lane must be the **first valid** one — an open arena lets the scan pick
  bare ground and dodge the flag.
- The flag must be **entity-free** — a townie standing on it makes `isUnobstructed`
  false, so the buggy check already rejected it. The real bug happened pre-villager.

## Autotest seam

`placeTempBedForTest(level, campfirePos)` runs the real `findSafeSleepPosition` +
`placeTempBed` without the `ServerPlayer`/sleep state machine (headless has no
player). It is the destructive core, so it catches a regression that re-lets the
bed land on a non-replaceable block.

# Relocation deed — placement and consumption

Collecting the deed lives in `town/entity/COMPLEX.md`. This is the other half:
**placing** it, and the non-obvious reason the deed is cleared from the hand
rather than shrunk. A deed that outlives its own placement is a duplication
hazard — it still references the flag that placement just destroyed, so placing
it again mints a second flag for a town that no longer exists.

```mermaid
flowchart TD
    Use["RelocationDeedItem.useOn\n(client side → sidedSuccess, no-op)"] --> Far{"farFixturesFor(target)\n> 0?"}
    Far -- yes --> Screen["send OpenRelocationConfirmMessage\nreturn SUCCESS — deed STAYS in hand"]
    Screen --> Choice["player picks bring / leave\n→ RelocationChoiceMessage.handle"]
    Choice --> PlaceB["TownRelocation.place(policy)"]
    Far -- no --> PlaceA["TownRelocation.place(BRING_ALL)"]

    PlaceA --> OkA{"result == OK?"}
    PlaceB --> OkB{"result == OK?"}
    OkA -- no --> FailA["displayClientMessage(messageFor)\nreturn FAIL — deed retained"]
    OkB -- no --> FailB["displayClientMessage\nreturn — deed retained"]
    OkA -- yes --> Consume
    OkB -- yes --> Consume["RelocationDeedItem.consumeFrom(player, hand)\nsetItemInHand(hand, EMPTY)"]
    Consume --> Done["useOn returns CONSUME"]

    subgraph creative ["why EMPTY, not shrink(1)"]
      G1["ServerPlayerGameMode.useItemOn\n(creative branch)"] --> G2["int i = stack.getCount()"]
      G2 --> G3["stack.useOn(ctx)"]
      G3 --> G4["stack.setCount(i)\n← undoes any shrink"]
    end
    Consume -.immune to.-> creative
```

- **Cancel sends no message at all.** The confirm screen just closes, so the
  "deed stays in hand" branch is also the cancel path — nothing to undo.
- **`setItemInHand(hand, EMPTY)` survives the creative restore** because it
  replaces the *slot*; the game mode's `setCount(i)` then mutates a stack that is
  no longer in the inventory. `shrink(1)` mutates the very stack the game mode is
  about to restore, so in creative the deed comes straight back (the 2026-07-14
  playtest bug). Both placement paths share `consumeFrom` so neither can drift
  back to the shrink idiom.
- **Consumption is strictly after an OK result** — "fail loudly, consume
  nothing". `place` validates fully before mutating, so a non-OK result leaves
  both the world and the deed untouched.

## Autotest coverage

`flag/deed_consumed_on_place` drives the whole real path —
`ServerPlayerGameMode.useItemOn` → `useOn` — with a **creative** Forge
FakePlayer, and asserts deed 1 → 0 plus "nothing dropped on the ground instead".
Creative is essential: in survival the naive shrink passes, so a survival-only
test would have been green against the bug. The other relocate scenarios call
`TownRelocation.place` directly and never touch the item layer.

# Door placement — register on the parent flag

`FalseDoorItem` and `TownDoorItem` place a door, then register it on the flag
the item's NBT names. Both resolve the parent with
`TownFlagBlock.GetParentFromNBT` and hand the door position to that flag's room
handle.

```mermaid
flowchart TD
    A[useOn, server level] --> B["GetParentFromNBT\n(item NBT → flag)"]
    B --> N{parent null?}
    N -- no --> Place[place the door block]
    Place --> Reg["parent.getRoomHandle()\n.registerDoor(pos)"]
    Reg --> C[return CONSUME]
    N -- yes --> Log[log error]
    Log --> FD["FalseDoor: FAIL,\nno block placed"]
    Log --> TD["TownDoor: block placed,\nregistration skipped"]
```

- **The null-parent edge case** — `GetParentFromNBT` returns null when the flag
  is not resolvable (its chunk is unloaded). The unguarded code NPEs; the guard
  logs and bails.
- **Placement and registration differ** — `FalseDoor` resolves the parent
  *before* `setBlock`, so a null parent aborts the whole placement (returns
  FAIL, the item is not consumed). `TownDoor` places via the block's own `useOn`
  first, so the door block is set regardless and only registration is skipped.

## A use while asleep does nothing (2026-10-22 agent playtest)

`beginCampfireSleep` returns at once when the player is already sleeping. A second wand use
that lands before the sleep screen opens (a double-click; the agent's two `clickheld` calls)
used to run the whole path again: it overwrote the sleeper's records with a second temp bed,
`startSleepInBed` failed with `OTHER_PROBLEM`, and the failure path cleared the records. The
first temp bed then stayed in the world, the campfire stayed lit, and `onWake` no longer saw a
campfire sleeper, so `first_campfire_sleep` was never granted and the "Your camp is ready" toast
came back every evening (`TownFlagBlockEntity.showCampReadyToastIfDue`). Pinned by
`CampfireSleepReentryTest`.

## Each night's first wand click only lights the fire (2026-10-22, r9)

`onWake` extinguishes the campfire, so the next night `TownWand.handleCampfireClick` sees it unlit,
lights it (flint sound) and returns: the first click lights, the second sleeps. The crosshair hint
(`CampfireSleepClientEvents`) is what tells the player: unlit -> `message.wand.campfire.not_lit`,
lit at night -> `sleep_hint`. The unlit copy said "The campfire must be lit", which reads as "go
find flint and steel" while the wand itself lights it; it now says "Right-click to light the
campfire". (An agent driving `_qtdev clickheld` without aiming sees only a silent PASS on that
first click: it is the lighting, not a failure.)

## The campfire stays lit after a campfire sleep (r17, dev 2026-10-25)

`CampfireSleepHandler.onWake` used to put the sleeper's campfire out (`extinguishCampfire`,
0eb1191c, 2026-04-08: "requires lit state to sleep and extinguishes on wake"), so the player
relit it before every night. Dev: it should stay lit, because the smoke is how the player finds
the way back to the village. `onWake` now only forgets the fire's position. Sleeping still
requires a lit campfire.
