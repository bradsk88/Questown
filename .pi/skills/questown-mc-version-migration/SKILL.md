---
name: questown-mc-version-migration
license: MIT
metadata:
  version: 0.1.0
  author: Brad Johnson
description: Migrates Questown (and its RoomRecipes dependency) to a new Minecraft version via the strangler pattern. TRIGGER when the user asks to port/migrate the mod to a new MC version ("migrate to 1.20", "port to 1.21", "make it build on Forge 47/49"). SKIP for a single-file fix, a bug, or a feature that doesn't change the MC API. Walks the ordered loop (probe deps -> RoomRecipes -> publish -> Questown -> compile loop), applies the strangler so version-specific changes live in Compat, and uses the compiler + jar tf as the API oracle.
---

# Questown / RoomRecipes MC version migration

Port the two repos to a new Minecraft version without scattering the change.
The whole migration is a **compile loop**, not a memory exercise: change
`build.gradle`, build, and let the compiler name each change.

## 0. Gate: is the target even buildable here?

Before choosing an MC target, probe the **reachable** maven (only
`maven.minecraftforge.net`, `maven.blamejared.com`, `dvs1.progwml6.com` resolve;
`maven.modrinth.com`, `maven.terraform.io`, modmaven.dev are NOT reachable).
Check every dep (forge, JEI, Patchouli) with a `curl -o /dev/null -w "%{http_code}"`.

A missing dep is a **hard gate**, not a preference. For 1.20.x this bit us:
Forge + JEI resolve for base 1.20, but **Patchouli only exists as 1.20.1+**
(nowhere reachable for base 1.20), so **1.20.1 is the only buildable 1.20.x
target**. If a dep can't be resolved, stop and report it — do not port to a
version that can't compile.

## 1. The ordered loop (RoomRecipes before Questown)

Questown cannot resolve its `RoomRecipes` dep until a matching jar exists, so:

1. **RoomRecipes first.** It is a pure Forge mod (dep block has only `log4j-core`
   + `minecraft`, no JEI/Patchouli) and has **no Compat/strangler** — its migration
   is direct code changes, driven by the compiler.
2. **`./gradlew build`** it, then **`./gradlew publishToMavenLocal`** — Questown
   resolves it via `mavenLocal()` (`~/.m2/repository/ca/bradj/RoomRecipes/<ver>`).
   Confirm the new version landed in `~/.m2` before touching Questown.
3. **Then Questown.** Create the branch, change `build.gradle` (forge + JEI +
   Patchouli + RoomRecipes + `mappings`), build, and fix.

## 2. The strangler (this is the point)

`ca.bradj.questown.mc.Compat` exists so forward/back-porting is cheap. When a
migration changes a vanilla **function that several call sites use**, route it
through `Compat` (add the wrapper) rather than editing every call site:

- `GuiComponent.fill`/`blit` (removed) -> `Compat.fill`/`Compat.blit`.
- A changed `Item.Properties.tab(...)`, `Recipe` signature, etc. -> `Compat`.

Mechanical-only edits are fine inline: a moved import (joml
`com.mojang.math.*` -> `org.joml.*`), or `Entity.level` field -> `level()`.
The strangler applies to **functions that changed or disappeared between
versions**, so the next port touches one place. The user has flagged this
repeatedly — do not skip it because a fix "looks mechanical."

## 3. The build environment (this machine)

- **Gradle 7.2 runs on JDK 17, not the system JDK 21.** JDK 21 makes Gradle 7.2
  fail compiling `build.gradle` with `Unsupported class file major version 65`.
  Ensure `gradle.properties` has
  `org.gradle.java.home=/home/retro/.gradle/jdks/jdk-17.0.20+8`
  (RoomRecipes has it; Questown may not).
- **`unzip` is not installed.** Locate/verify a class in a jar with
  `jar tf <jar> | grep ...`, not `unzip -l`. The ForgeGradle mapped jar is at
  `build/fg_cache/net/minecraftforge/forge/<ver>_mapped_official_<mc>/...jar`.
- **`javap -classpath <jar> FQN` is unreliable for inner classes (`X$Y`) and for
  classes removed in the target version** (returns nothing for both a tool quirk
  and a genuinely-gone class). When javap says "not found", confirm the class's
  real location with `jar tf` before concluding it moved — e.g. in 1.20.1
  `Block.Properties` is `BlockBehaviour$Properties`, block `Material` and
  `MaterialColor` are gone (`MaterialColor` -> `MapColor`, same member names),
  `LootTables` -> `BuiltInLootTables`, `DyeColor.getMaterialColor()` ->
  `getMapColor()`, `Entity.level` field -> `level()`.
- **`javap` the Forge `…_mapped_official_<mc>.jar` for API names, not the
  `~/.gradle/caches/forge_gradle/mcp_repo/…/rename/output.jar`.** The mcp_config
  `rename/output.jar` has `m_XXXXX_` names; the `…_mapped_official_<mc>.jar` has
  the pretty names the compiler compiles against (`title`, `icon`,
  `BlockBehaviour.Properties.of()`). Do not infer the API from the mcp_config jar
  or from a jar found by filename — confirm the real target by querying the
  `compileJava` classpath (init script printing `t.classpath.files`) or
  `find build/fg_cache -name 'forge-*mapped_official*.jar'`.
- **NeoForge 1.20.6 uses a different toolchain than the Forge rows above:**
  Gradle **8.14** (the migration branch's wrapper), **Java 21**
  (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`), NeoGradle 7.1.39. The
  JDK 17 / "Unsupported class file major version 65" rules apply to the
  Forge 1.19.2/1.20.1 branch, not this one.
- **Which classes live in which artifact** (verified `jar tf`/`javap`): vanilla
  Minecraft classes → the NeoForm build output, `javap -cp
  build/neoForm/neoFormJoined1.20.6-*/steps/recompile/classes <FQN>`, or the
  decompiled sources under `.../steps/transformSource/transformed/` (grep by
  name; Neo classes are there too). Neo's own classes →
  `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/20.6.141/*/neoforge-20.6.141-universal.jar`.
  `SubscribeEvent` → `net.neoforged/bus/8.0.1/*/bus-8.0.1.jar`. `@Mod`,
  `@EventBusSubscriber`, `FMLCommonSetupEvent` →
  `net.neoforged.fancymodloader/loader/3.0.45/*/loader-3.0.45.jar`. The
  universal jar contains **zero vanilla classes** — a "not found" there proves
  nothing about the class's existence; look in `recompile/classes`.
- **The compiler is the oracle.** Don't pre-decide a 1.20 API change from
  memory; change the version, build, fix per category, re-run.
- **Apply a mechanical mapping to every file the compiler reported for that
  class in that run, not a hand-picked subset.** Files you leave in the batch
  resurface in the next pass and each costs a compile cycle (this session
  scoped "events" as 11 files; ~10 more of the same — renderers, event bridges,
  straggler imports — surfaced in the next one).

## 4. Known 1.19.2 -> 1.20.1 changes (verified)

- joml: `com.mojang.math.{Matrix4f,Matrix3f,Vector3f}` -> `org.joml.*`;
  `com.mojang.math.Quaternion` -> `org.joml.Quaternionf`/`Quaterniond`
  (no bare `Quaternion` in `org.joml`).
- `Recipe`: `getResultItem(RegistryAccess)`, `assemble(Container, RegistryAccess)`;
  `canCraftInDimensions` still exists. `CreativeModeTab` -> builder
  (`.title().icon(Supplier).build()`; `makeIcon()`/`String` ctor gone).
- `Block`: `Properties.of(Material, MaterialColor)` ->
  `BlockBehaviour.Properties.of().mapColor(MapColor)`; `Material` gone.
- `Item.Properties.tab(CreativeModeTab)` signature changed (check javap).
- `EntityPredicate.Composite`, `RenderType.CompositeState` moved (check javap).

### GUI screen migration (re-derived 3x this session — the traps)

- **`Component` no longer extends `FormattedCharSequence` in 1.20.1** (it did in
  1.19.2; now `Component extends FormattedText`, and has `getVisualOrderText()`
  returning `FormattedCharSequence`). So `setTooltipForNextRenderPass(
  List<Component>)` does NOT compile. Single `setTooltipForNextRenderPass(
  Component)` works (there is a `Component` overload). For a multi-line tooltip
  convert first: `List.of(a.getVisualOrderText(), b.getVisualOrderText())`.
  This bit 3 screens. Verify the `Component`/`FormattedCharSequence` relationship
  before passing a component list anywhere.
- **`renderTooltip(...)` is GONE** (all variants). An `@Override protected void
  renderTooltip(PoseStack/GuiGraphics, ...)` must be DELETED (it no longer
  overrides anything). Fold its custom tooltip logic into `render()`, setting
  via `setTooltipForNextRenderPass(...)`. A leftover `this.renderTooltip(gui,
  mx, my)` call (a "tooltip lambda" error) is removed; the vanilla item tooltip
  renders automatically from `setTooltipForNextRenderPass`.
- **`javap` "class not found" is a loading quirk, not absence** (hit for
  `ItemRenderer`, `Screen`, `RecipeManager`, `Vector3f`). It cost a wrong-path
  round-trip: `ItemRenderer` is at
  `net.minecraft.client.renderer.entity.ItemRenderer`, NOT
  `net.minecraft.client.renderer.ItemRenderer`. Trust the compiler, or `jar tf`
  to confirm the location; never trust a bare `javap` "not found" as the path.
- **The `compileJava` 100-error cap hides the rest.** `grep -c error:` hits 100
  = the cap, not the true count. After each batch, rebuild and re-run the
  by-file breakdown (`grep -B1 error: … | sed | sort | uniq -c | sort -rn`);
  fixing files shifts the cap and reveals the next batch. This is the whole
  point of the compile loop — do not assume the file list is complete.
- Corrected API facts (verified, not from a stale javap): `ItemStack.sameItem`/
  `sameAs` both gone -> static `ItemStack.isSameItem(a, b)`. `CraftingContainer`
  is an interface (cannot `new`) -> concrete `TransientCraftingContainer(
  AbstractContainerMenu, int, int)`. `RecipeManager` has no `getRegistryAccess()`
  -> thread `RegistryAccess` from the level (`level.registryAccess()`; `Level`
  has `registryAccess()`, not `getRegistryAccess()`; `Player.getLevel()` ->
  `level()`). `ItemRenderer.blitOffset` field is gone (private); `renderStatic`
  8-arg is `(ItemStack, ItemDisplayContext, int, int, PoseStack,
  MultiBufferSource, Level, int)`. `BedBlock.findStandUpPosition` is 5-arg
  `(EntityType, CollisionGetter, BlockPos, Direction, float)` (was 4-arg, float
  last). `LevelReader.enabledFeatures()` returns `FeatureFlagSet` (default
  `FeatureFlags.DEFAULT_FLAGS`). `Button` ctor is protected ->
  `Button.builder(msg, onPress).bounds(x,y,w,h).build()`. `GuiComponent` is gone
  (`extends GuiComponent` must migrate). `org.joml.Vector3f` has no `YP`/`Y`
  static -> `new Vector3f(0,1,0)`. `fillGradient`/`drawCenteredString` are on
  `GuiGraphics` (6-arg `fillGradient(x1,y1,x2,y2,c1,c2)`;
  `drawCenteredString(Font, FormattedCharSequence|String|Component, x, y,
  color)`).

## 5. Strangler probe (report-only round trip, no port)

When the mission question is "is the strangler actually applied?", do not port — probe. On a throwaway branch, swap only the build config (from the golden repo `/workspace/IdeaProjects/Questown-119`: its `build.gradle`, `gradle.properties`, wrapper properties; the 1.19.2 `RoomRecipes` version already sits in `~/.m2`), compile, and classify each error as seam (`mc/`, `gui/RenderUtil`, `loader/`) vs leak. Then switch the branch back and report. Verified flow: 1.19.2 config + wip sources → `compileJava` (the 100-error cap applies) → marker sweep → report at `docs/plans/strangler-1.19.2-probe-report.md` (145 of 772 main files carry a version marker; ~120 non-seam, in 7 clusters; the bar is not met).

For the report you need the *complete* file set, so a marker sweep beats the fix-pass loop here. Traps:

- A sweep pattern passed through a shell variable to `grep -E` must use bare `|` alternation — `\|` becomes an escaped pipe in ERE and matches *nothing* (a `DataComponents\|CustomData` sweep silently reported 0 files; the cross-check against the javac log caught it).
- Cross-check both directions: every javac `symbol:` should land in a sweep file; every sweep marker should have a javac or read-file confirmation.
- Appending a pin to a repo `gradle.properties` (the golden's has no trailing newline) can merge onto the last line and never apply — read the file back after the append. Two failed compiles on "Unsupported class file major version 65" during the settings build masked a plain merged-line typo.

## 6. When blocked

If a dep can't resolve or a change can't be settled, report it (a dep gate is a
verified blocker, not a preference). Don't substitute a prior or a general-page
value for a missing figure.

**Report state unambiguously:** "compiles" vs "clean tree at a boundary, still
N errors in M files". A mid-migration tree that is only *git*-clean is not
"green" — a reader will take "green" for a passing build.
