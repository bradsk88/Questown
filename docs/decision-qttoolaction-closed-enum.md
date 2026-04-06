# Decision: QTToolAction is a Closed Enum

**Date:** 2026-02-12
**Status:** Active
**Revisit when:** A Questown job needs a non-vanilla tool action

---

## Context

Questown's `QTWorldAccess` interface abstracts Minecraft's
world so that job rules can run in tests and during time warp
without a live server. Tool-block interactions (tilling,
stripping, etc.) go through `canToolTransformBlock(pos, action)`
and `applyToolTransformation(pos, action)` — Minecraft functions.

Forge represents tool actions as `ToolAction` — a stringly-typed
open registry where any mod can create new instances via
`ToolAction.get("name")`. We needed to decide whether
`QTWorldAccess` should accept arbitrary strings/ToolActions or
a controlled set.

---

## Decision

`QTToolAction` is a **closed Java enum** with four values:
`HOE_TILL`, `AXE_STRIP`, `AXE_SCRAPE`, `SHOVEL_FLATTEN`.

`MinecraftWorldAccess` maps each enum value to the corresponding
Forge constant in a single `switch` expression. `TestWorldAccess`
never touches Forge at all.

---

## Why

1. **Compile-time safety.** Forge's ToolAction names don't match
   their constant names (`ToolActions.HOE_TILL` maps to the
   string `"till"`, not `"hoe_till"`). Early code that guessed
   strings silently failed. The enum makes the mapping explicit
   and reviewable.

2. **Testability.** Tests don't need Forge on the classpath to
   express tool actions.

3. **Simplicity.** Four values cover every tool-block interaction
   Questown currently uses.

---

## Risks

A third-party mod writing a Questown job that needs
`canToolTransformBlock` with a custom tool action would be
locked out by the closed enum. Forge's API fully supports
custom actions — `ToolAction.get("mymod:custom")` works and
is documented.

---

## Current status

We surveyed the Forge modding ecosystem to understand how
custom ToolActions are actually used in practice.

**Custom ToolActions exist but are rare.** Mekanism defines
`paxel_dig` for its combined pickaxe/axe/shovel. Tinkers'
Construct defines `light_fire` for its Firestarter modifier.
These are the most prominent examples across major mods.

**They're used to advertise item capabilities, not to define
new block transformations.** Mekanism's `paxel_dig` lets other
mods query "is this item a paxel?" — no block overrides
`getToolModifiedState` to respond to it. The vanilla set
(`strip`, `till`, `flatten`, `scrape`, `wax_off`) covers
nearly all real block-transformation usage.

**Most mods use other patterns entirely.** Farmer's Delight's
knife uses `Item.useOn()` overrides and item tags
(`forge:tools/knives`). This is the dominant pattern for
custom tool-block interactions: direct method overrides on
the item or block side, not the ToolAction system.

---

## Sources

- **Forge source:** `ToolAction.get()` is an open
  `ConcurrentHashMap`-backed factory. `ToolActions` defines
  vanilla constants using the same API.
  ([MinecraftForge/ToolAction.java, 1.19.x branch](https://github.com/MinecraftForge/MinecraftForge/blob/1.19.x/src/main/java/net/minecraftforge/common/ToolAction.java))

- **Mekanism:** `ItemMekanismPaxel` defines `PAXEL_DIG` via
  `ToolAction.get("paxel_dig")` and declares support for all
  axe/pickaxe/shovel actions.
  ([mekanism/Mekanism, 1.18.x branch](https://github.com/mekanism/Mekanism/blob/1.18.x/src/tools/java/mekanism/tools/common/item/ItemMekanismPaxel.java))

- **Tinkers' Construct 3:** Defines `light_fire` for the
  Firestarter modifier.
  ([SlimeKnights/TinkersConstruct](https://github.com/SlimeKnights/TinkersConstruct))

- **Farmer's Delight:** KnifeItem does NOT use custom
  ToolActions. Uses `Item.useOn()` + item tags instead.
  ([vectorwing/FarmersDelight](https://github.com/vectorwing/FarmersDelight))

- **Forge docs:** Explicitly encourage `ToolAction.get()` for
  custom actions and `canPerformAction()` overrides on items.
  ([Forge Community Wiki](https://forge.gemwire.uk/wiki/Making_Tools),
  [gigaherz's tool system guide](https://gist.github.com/gigaherz/691f528a61f631af90c9426c076a298a))

- **NeoForge 1.21+:** Renamed `ToolAction` to `ItemAbility`
  (and `ToolActions` to `ItemAbilities`). Same API pattern.
  ([NeoForge docs](https://docs.neoforged.net/docs/items/tools/))

---

## Migration Path (When Needed)

When the first non-vanilla tool action is required, the likely
fix is one of:

- **String-wrapper with constants:** Replace the enum with a
  `QTToolAction` class wrapping a `String`, with static
  constants for the known values. Preserves readability for
  common cases while allowing arbitrary values.

- **Accept String in the interface:** Change
  `QTWorldAccess.canToolTransformBlock(pos, String)` and
  validate the string against `ToolAction.get()` at job-load
  time. Simpler but loses type distinction.
