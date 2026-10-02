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

As of 2026-10-13 the txt is at 79 entries. The G1 SAFE-tier confirm screens
(Work, AddWork, FlagCrafting, JobChange, Bop×2, CreateStock, QuestRemove), the render-
argument helpers `IngredientRenderer` / `StatusGraphic` / `Tabs`, `Ingredients`,
the `villager/advancements` widget trio, and the two client event subscribers
(QuestownHudEvents' HUD-over-blur redraw + GuiLayoutCheckClientEvents' dev gui-lint,
moved into the `loader/neo` seam - see below) are landed (gate now ignores `.md`
files so `gui/COMPLEX.md` prose is not a leak). `gui/COMPLEX.md` is the per-screen
pattern reference - read it before the next leaf.

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
content gate + bank diff on all seven canonical screens in fixture-drift range.
Note: `renderTooltips`'s 3rd/4th params are the pane origin, not the mouse -
renamed `paneX`/`paneY` to keep the swap visible)

and the two `gui/` client event subscribers (2026-10-13: `QuestownHudEvents`
- the HUD-over-blur redraw for the 1.19.2-parity screen family - and
`GuiLayoutCheckClientEvents` - the dev gui-lint - both `@EventBusSubscriber`
neoforged wiring, moved verbatim into `loader/neo` (the seam package; the
same home `ItemsInteract`/`NeoMouseScrollDispatch` already hold), 81 → 79;
bus registration is package-independent, so a live gui-lint line + the
porttest content gate's HUD-layering confirms both still fire). The render-
layering family is resolved by this move - there is no G1 gui file left.

Still to do in the `gui/` package: `AbstractVillagerMenu` +
`LockableInventoryWrapper` - NOT a package move (the menus subclass
`AbstractVillagerMenu` and use its protected package-local fields; five gui
screens break the moment it leaves the package). Their neoforged surface is
only the item-handler (`IItemHandler`/`SlotItemHandler`/`InvWrapper`) - route
it through the neutral `loader/ItemHandler` seam (see `loader/COMPLEX.md`
"item-handler capability") in its own leaf with the full evidence bar.

Then G2/G4 multi-marker files (HospitalBedBlock's dynamic MapColor lambda,
the LootParams blocks, TownFlagBlock/BlockOfProgress data-components,
MCTownItem/TestBlueprintRegistry registries), G6 neoforged, G5 renderers, G7 client events.
