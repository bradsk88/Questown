# Plan: the helper chicken as its own mod

**Goal.** The helper chicken ships as a separate mod that depends on Questown, and Questown's
`ca.bradj.questown` holds next to no chicken code. Players who install both get today's
first-day experience.

**Where things stand (2026-11-07).** In the 1.19 stack the chicken is slices 34b–34e, at the end,
so the stack still ends at the playtested code. Measured on the target (`845817d8`):

- 45 chicken files, about 5,900 lines: 25 in `mobs/helperchicken`, plus the statue
  (`blocks/StoneChickenStatue`, `blocks/entity/ChickenStatue*`), `town/HelperChickenSpawnController`,
  `town/HelperChickenRotationDetector`, `devtools/ChickenScaffoldingNbtEditor`, two commands
  (`QtDevSpawnChickenCommand`, `ChickenRemoveCommand`), 7 files under `commands/test`, and the statue's
  blockstate, models and loot table.
- 17 unit test files (`ChickenArc*Test`, `BeatPhaseTest`, `ChickenScaffolding*Test`,
  `HelperChicken*Test`, ...).
- About 35 other Questown files that know about the chicken. That coupling is what this plan removes.

## What Questown knows about the chicken today

| Coupling | Where | What it needs from Questown |
|---|---|---|
| State saved on the flag: ever spawned, beat state, Worldly Seeds fired, arc forfeit, structure rotation, rotation detected, sunset chest spawned (`NBT_CHICKEN_*`) | `TownFlagBlockEntity` (fields + ~30 getters/setters), `TownFlagTileData`, `TownFlagInitialization`/`Impl` | A place on the flag where an add-on stores its own data |
| Per-session observations: slept since sunset, villager UI opened, flag UI opened, seeds given | `TownFlagBlockEntity` (transient fields), `TownFlagBlock`, `CampfireSleepHandler`, `TownVillagerUIs`, `WorldlySeeds` | Events Questown fires for those moments |
| The Worldly Seeds loot guarantee | `AbstractWorldInteraction.withLootGuarantees` → `ChickenArcLootGuarantee` (already has a TODO: guarantees as special rules) | A loot-guarantee hook (the special rule the TODO describes) |
| "This town can't run the chicken arc" | `TownFlagBlockEntity.markChickenArcIneligible`, `FlagCommand` | An event or flag query the add-on reacts to |
| The chicken's speech bubble | `render/BubbleRenderer`, `loader/forge/NeedBubbleClientEvents` | Bubble drawing as a public client utility |
| Empty-town terrain the chicken builds on | `devtools/EmptyTownGrassPruner`, `EmptyTownGrassThinner`, `EmptyTownTerrainShaper` | Probably nothing: these are dev tools that can move with it |
| Registration: entity, statue block, block entity, item, renderers | `EntitiesInit`, `BlocksInit`, `TilesInit`, `ItemsInit`, `QuestownClient` | Nothing: the add-on registers its own |
| Dev commands and autotests: `_qtdev` inspect/help, beat guidance, chicken-arc scenarios, arena prep, growth/self-sufficiency executors | `commands/*`, `commands/test/*`, `loader/forge/CommandInit` | A way for an add-on to register `_qtdev` subcommands and autotest scenarios |
| Text | `lang/en_us.json` (about 49 chicken keys) | Nothing: the keys move to the add-on's lang file |

## The Questown API (phase 1)

1. **Flag attachments.** An add-on registers a key (`ResourceLocation`) and a serializer, and reads and
   writes its own value on a flag. The flag saves every attachment under its key and keeps keys it doesn't
   recognize, so removing the add-on doesn't lose the data.
2. **Town events** on the Forge event bus: flag placed/initialized, flag UI opened, villager UI opened,
   a villager slept, an item given to a villager, a quest completed, the town marked ineligible for
   onboarding helpers. Each one is a small event class plus one `post` at today's call site.
3. **Loot guarantees as special rules.** A job's results pass through registered guarantees, and a
   guarantee declares which job/rule it applies to. The first-hunter guarantee moves onto it too. This
   is the existing TODO on `withLootGuarantees`.
4. **Public client utilities**, starting with drawing a bubble over an entity.
5. **Extension points for dev tools**: register `_qtdev` subcommands and autotest scenarios.

Each piece lands in Questown with its own tests, and the chicken moves onto it while still inside
Questown, so the game doesn't change and the chicken-arc autotests still pass.

## Phases

1. **API in Questown, chicken moved onto it.** Done when `mobs/helperchicken` uses only the API and
   nothing in Questown imports a chicken class except the registration and the chicken's own package.
2. **New mod.** A new Gradle project, like RoomRecipes: depends on Questown through `mavenLocal()`. The
   chicken code, registration, renderers, lang keys, statue data, commands, unit tests and autotest
   scenarios all move over.
3. **Remove the chicken from Questown.** Old saves: on load, the flag moves `NBT_CHICKEN_*` into the
   add-on's attachment key.
4. **Publish.** Its own Modrinth project, listed as a recommended companion in Questown's description.

## Test ledger

| Area | Today | After |
|---|---|---|
| Chicken logic (beats, conditions, hints, scaffolding, statue, rotation, loot guarantee) | 17 unit test files in Questown | Move to the new mod unchanged |
| The arc end to end | `chicken-arc/*` autotest scenarios in Questown's runner | Run from the new mod through the scenario extension point |
| Flag attachments: save, load, unknown keys kept | none | New Questown unit tests |
| Events fire at the right moments | none | New Questown unit tests per event |
| Loot-guarantee rule | `ChickenArcLootGuaranteeTest` | Plus a Questown test for the rule hook |

## Open decisions

1. Mod id and name (for example `questown_chicken`, "Questown: Helper Chicken").
2. What day 1 looks like **without** the chicken. Onboarding currently assumes it in places (the Worldly
   Seeds guarantee, the empty-town terrain). Needs a playtest.
3. Whether old saves need migration (both mods are alpha).
4. Which line first: 1.19.2, then 1.20.1 and 1.20.6.
5. Whether the autotest scenarios stay in Questown's runner (through the extension point) or the new mod
   gets its own.
