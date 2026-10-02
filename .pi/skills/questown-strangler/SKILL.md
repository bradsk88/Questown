---
name: questown-strangler
description: Shrinks `tools/strangler-grandfather.txt` toward zero by migrating
  files off 1.20.6/NeoForge-only APIs to the Compat seam. Triggers when the
  user wants to de-grandfather a path, run the strangler gate, or continue
  the G1..G7 cluster plan in `docs/plans/strangler-1.19.2-probe-report.md`.
  Skip for a single API-call fix unrelated to the gate.
---

# Questown strangler shrink loop

The strangler gate is the source of truth for which APIs leak into
non-seam files. Never decide a de-grandfather from a subset grep — run the
gate before and after:

    sh tools/strangler-check

It fails the build on any marker from its header list (GuiGraphics,
`net.neoforged`, `Button.builder`, `MapColor`, ...) outside `SEAM_FILES`
and anything still listed in `tools/strangler-grandfather.txt`. Before
removing a path from the txt, run the gate and confirm the line is not
still needed — the gate, not the txt, is the source of truth.

## Cluster order (from the probe report)

G1 gui screens → G2 block `mapColor` → G3+G4 registries/holders → G6
neoforged → G5 renderers → G7 client events. Each cluster has a note in
`docs/plans/strangler-1.19.2-probe-report.md`; read the note for the
cluster you are opening before touching a file.

As of 2026-10-13 the txt is at 49 entries. G6 is in flight: the decision is
`loader/COMPLEX.md` "The rule" (loader/neo owns loader imports; the mechanical exception
is `net.neoforged.api.distmarker` - package-renamed inline in the golden, so the marker is
narrowed to `net.neoforged.(bus|fml|neoforge)` and the seven distmarker-only leaves
and the nine pure G6 event binders move whole into `loader/neo`
(git-mv + package line + sibling imports; TownFlags' OnWake splits into
`NeoTownWakeEvents` delegating to the owner's public `TownFlags.onSleepFinished`; the
ServerTickProfiler unregistered-wiring gap is carried forward, see the probe report
G6 note). The G5 renderer cluster is done
(`Compat.renderItemStaticGui` owns the GUI `renderStatic` call for AxeRack / FoodDisplay /
Plate / BubbleRenderer; the `org.joml` marker is off the gate as mechanical - the
`com.mojang.math` → `org.joml` rename the mission rule calls inline-fine, so joml imports
no longer grandfather anything). The G1 SAFE-tier confirm screens
(Work, AddWork, FlagCrafting, JobChange, Bop×2, CreateStock, QuestRemove), the render-
argument helpers `IngredientRenderer` / `StatusGraphic` / `Tabs`, `Ingredients`,
the `villager/advancements` widget trio, the two client event subscribers
(QuestownHudEvents' HUD-over-blur redraw + GuiLayoutCheckClientEvents' dev gui-lint,
moved into the `loader/neo` seam), and the last two `gui/` neoforged menus
(`AbstractVillagerMenu` item-handler swap to vanilla `Slot`, `LockableInventoryWrapper`
deleted as dead code) are landed (gate now ignores `.md` files so `gui/COMPLEX.md`
prose is not a leak). `gui/` is now leak-free. `gui/COMPLEX.md` is the per-screen
pattern reference - read it before the next leaf (G2/G4/G5/G7 clusters).

## Per-leaf loop (G1 confirm screens)

1. **Migrate** the screen to `CompatScreen` / `CompatContainerScreen`:
   take `RenderContext` instead of `GuiGraphics`, use the compat helpers
   for `fill`, `fillGradient`, `darkText`, `lightText`, `button`,
   `componentText`, `splitText`, `renderItemAndDecorations`. For the
   render-argument seam helpers (`Ingredients.render`, `RenderUtil.highlight`,
   `PagedCardScreen`) use `ctx.gui()` — no new RenderContext method needed.
   Hooks: `renderPrologue`, `renderScreenSurface`, `renderScreenContent`;
   leaf extras → `renderScreenExtras`; leaf tooltip → `renderTooltipContent`.
2. **Verify compile + gate green**: `./gradlew compileJava --rerun-tasks`
   then `sh tools/strangler-check`. Trust green only when the task says
   `executed`.
3. **Live gui-lint on a held run** (AGENTS.md): open the screen in a dev
   client, confirm `[gui-lint] <Screen> — OK, no layout violations` in the
   client log. Headless openers: the manifest `shot <name>` entries, `flagtab
   <type> <flag x y z>` (hook-native - raw in `.qtdiag-cmd`, **no** `cmd`
   prefix, see PORTTEST.md), and the thin `_qtdev` wrappers (`show_item_jobs`
   pattern in `QtDevClickCommand`, doc'd in `commands/COMPLEX.md`). If a
   screen has no headless opener, add the thin `_qtdev` wrapper first - one
   that delegates to the owner call the click path already makes - rather
   than driving it by state. If the FIFO is alive the `cmd` channel works;
   the `.pi/skills/questown-held-run` skill carries the launch + console traps.
4. **Vision-check before trusting the pixels**: the `capture.sh` stage
   `shot` is async — poll `run-client/diags/<name>.png` for a few seconds;
   a capture that shows "Connection Lost" means the client was mid-reconnect,
   re-capture. Run the vision recipe from
   `~/.pi/agent/references/vision-check-local-images.md` (Nova proxy,
   full-capture only, `reasoning_effort: medium`). This catches the
   "flat grey box" blit-overload bug (the 8-arg `Util.blit` assumes the
   256×256 sheet; 32×32 status art needs the 10-arg form) that gui-lint
   will not.
5. **De-grandfather** the file in `tools/strangler-grandfather.txt`
   (watch the count drop, e.g. 113→108) and **commit** per repo rules
   (explicit paths, identity preflight, prep-commit gate when the hook is
   active).

## Traps specific to this loop

- `pgrep -f` on a kill/teardown command matches its own invocation
  (phantom shell). Use bracket patterns (`[r]unServer`) for checks and
  verify via `netstat -ltnp | grep 25565` + `/proc/<pid>/fd/0` where the
  command string matters.
- The console FIFO is frequently a dead pipe on a fresh launch
  (verified 3×). `ls -l /proc/<server-pid>/fd/0` → `pipe:` means the
  console path is dead; use the diag channel or in-game chat.
- A teardown call returning empty output did not prove the kills ran —
  verify in a later call with fresh `pgrep` + `netstat`; `sweep.sh` is the
  safe sweeper for stragglers.
- A green `./gradlew build` is red by default here (`ChickenArcHintsTest`
  is a deliberate failing marker). Gate on `compileJava`/`jar` executed,
  or `./gradlew build -x test`.
- A seam-token type must not **substring-match a bare-name marker**. `MapColor` is a bare name (not a
  `Button.builder`-style call), so a leaf naming a `CompatMapColor` enum is *still* a gate hit — the
  gate's `MapColor` substring matches it (verified: it kept every migrated block flagged until the
  enum was renamed). Name the token to dodge the collision: `CompatBlockColor` + `Compat.blockProps(...)`
  as the form-owner (the enum *body* may name `MapColor`; it lives in the `mc/` seam family the gate
  admits). Second hit on G3 for the same reason: the item-NBT helpers are `itemDataTag`/`setItemDataTag`,
  not `itemCustomDataTag` (the `CustomData` marker would substring-match that in every leaf that calls it).

## Remaining G1 leaves (as of 2026-10-13)

Done: every SAFE-tier confirm screen + the paged cluster + the helper
pass-throughs above, plus `VillagerStatsScreen` / `InventoryAndStatusScreen`
(2026-10-13: `CompatContainerScreen` hooks, gui-lint OK + vision on held runs),
`VillagerEconomicsScreen` (2026-10-13: gui-lint OK + vision real-art in the
ingredient grid), `ItemJobsScreen` + `PagedCardScreen` (2026-10-13: delegate
public surface onto `RenderContext`; leaf onto `CompatScreen`, which also fixes
the 1.20.6 panel-under-second-background draw-order regression; gui-lint OK on
ItemJobsScreen + the four Abstract leaves; vision confirmed the panel/nav/buttons/
card rows; opener `_qtdev show_item_jobs` added, see commands/COMPLEX.md) and
`Ingredients` (2026-10-13: `render` takes `RenderContext` and routes the three
`Registries.ITEM` tag constructions through the new `Compat.itemTag(ResourceLocation)`
seam helper - name chosen to dodge the gate's `net.minecraft.core.registries`
package marker; callers pass `ctx`/`rc` directly; 85 → 84; gui-lint + vision +
bank drive in fixture-drift range) and the `villager/advancements` trio
(2026-10-13: screen onto `CompatScreen` - `renderBlurredBackground` in
`renderScreenSurface`, content in `renderScreenContent`; all three files take
`RenderContext`, render arg never names `GuiGraphics`; the HUD-over-blur order
`QuestownHudEvents` depends on is preserved because the surface hook is where
`Screen.renderBackground` used to paint; 84 → 81; gui-lint OK on this screen,
content gate + bank diff in fixture-drift range. Note: `renderTooltips`'s
3rd/4th params are the pane origin, not the mouse - renamed `paneX`/`paneY`
to keep the swap visible)

and the last two `gui/` neoforged menus (2026-10-13, 79 → 77 - `gui/` is now
leak-free), and the G5 renderers (2026-10-13, 77 → 69 - see the shipped note in the
probe report). `AbstractVillagerMenu`'s three neoforged imports
(`IItemHandler`/`SlotItemHandler`/`InvWrapper`) are gone - the menu holds the
original vanilla `Container`/`Inventory` directly and builds plain vanilla
`Slot(container, index, x, y)` boxes. That is provably equivalent: its custom
`quickMoveStack`/`moveItemStackTo` only use `Slot.set/get/mayPlace`, never the
handler's `insertItem`/`extractItem` (`jmembers` confirms `Slot(
Container,int,int,int)` + `Container.getContainerSize()` are the vanilla 1.20.6
API). `LockableInventoryWrapper` - a neoforged `InvWrapper` subclass with zero
references in main or test - was deleted (dead code; nothing to route through
a seam). Evidence: `compileJava`+`compileTestJava --rerun-tasks` green; 968
tests (only the deliberate `ChickenArcHintsTest.todo_` marker red);
strangler-check green at 77; gui-lint OK on every affected menu (Inventory-
AndStatus, Quests, VillagerStats, VillagerEconomics, VillagerBlockofProgress,
Work); the slot-rendering `parent` (inventory) tab pixel-diffs against the
1.19.2 golden at a 0.0447% center-rect (slot region) delta - slots render
identically to the golden branch. Note: the `gui/` class boundary is a
Java-protected design constraint (a `gui/` class may `protected`-reference
loader-neutral types, and a `loader/` type may not reverse-`protected`-extend
one) - it is NOT the reason these two were de-grandfathered; their neoforged
surface was only the item-handler, and that is what the vanilla-
`Slot`-over-`Container` swap removed. The `protected` fields the five villager
screens read (`flagPos`/`villagerUUID`/`gathererInventoryYOffset`) are untouched.

The frontier is now the non-"gui/" and non-G5 clusters: G2/G4 multi-marker files
(HospitalBedBlock's dynamic MapColor lambda, the LootParams blocks,
TownFlagBlock/BlockOfProgress data-components, MCTownItem/TestBlueprintRegistry
registries), the G6 loader/event seam (the loader/COMPLEX.md rule already decides it:
loader-neutral facade, `loader/neo` owns the imports), and G7 vanilla packet plumbing.
