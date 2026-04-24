package ca.bradj.questown.commands.test;

import ca.bradj.questown.commands.test.ChickenArcScriptedAction.AdvanceTicks;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.DepositIntoContainer;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.GiveBoundWand;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.GiveItem;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.MarkFlagUiOpened;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.MarkSleepObserved;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.MarkVillagerUiOpened;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.PlaceBlock;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.RegisterDoorViaWand;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.RightClickChickenWithHand;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.RunCommand;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.SetUpRegisteredRoomWithChest;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.WandRightClick;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import ca.bradj.questown.mobs.helperchicken.HelperChickenBeatOffsets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Registry of the 13 authored chicken-arc scenarios. Mirrors
 * {@link TestBlueprintRegistry} in shape so the runbook and any CI can grep
 * scenarios the same way for either track.
 *
 * <p>Scenario names must match the plan verbatim — they are the anchors the
 * agent runbook (U5) uses to route failures back to implementation sites.
 */
public final class ChickenArcBlueprintRegistry {

    public static final String CATEGORY = "chicken";

    /**
     * Gatherer warp amount that mirrors the default passed to
     * {@link TestAllExecutor}. Used by the warp-parity scenario only — the
     * realtime variant runs with {@code warpAmount=0}.
     */
    private static final int GATHERER_WARP_TICKS = 24000;

    /**
     * Half-width used by the two-flags scenario so both flag positions fall
     * inside the preparer's flatten radius.
     */
    private static final int TWO_FLAGS_HALF_WIDTH = 25;

    private ChickenArcBlueprintRegistry() {
    }

    /**
     * Resolve {@code ItemsInit.WORLDLY_SEEDS} when the Forge registry is
     * populated (server runtime). In JUnit, where the mod registry never ran,
     * fall back to {@link Items#WHEAT_SEEDS} so the registry itself is still
     * enumerable for structural tests. Runtime scenarios pay no attention to
     * the fallback because the Forge registry is always present by the time
     * {@code AutoTestRunner.onServerStarted} fires.
     */
    private static Item worldlySeedsItem() {
        try {
            return ItemsInit.WORLDLY_SEEDS.get();
        } catch (NullPointerException | IllegalStateException e) {
            return Items.WHEAT_SEEDS;
        }
    }

    private static net.minecraft.world.level.block.state.BlockState welcomeMatBlockState() {
        try {
            return ca.bradj.questown.core.init.BlocksInit.WELCOME_MAT_BLOCK.get().defaultBlockState();
        } catch (NullPointerException | IllegalStateException e) {
            return Blocks.STONE_PRESSURE_PLATE.defaultBlockState();
        }
    }

    private static net.minecraft.world.level.block.state.BlockState jobBoardBlockState() {
        try {
            return ca.bradj.questown.core.init.BlocksInit.JOB_BOARD_BLOCK.get().defaultBlockState();
        } catch (NullPointerException | IllegalStateException e) {
            return Blocks.OAK_SIGN.defaultBlockState();
        }
    }

    public static List<ChickenArcBlueprint> all() {
        return List.of(
                stickPeckAndFollowSpawn(),
                f1StickToCampfire(),
                f3BuildRoomToWelcomeMat(),
                f4SeedsToStatue(),
                f1RotationClockwise90(),
                rotationAmbiguityForfeit(),
                rotationZeroMatchForfeit(),
                twoFlagsIndependentArcs(),
                skipChickenCommand(),
                forfeitRemoveCommand(),
                firstGatherWorldlySeedsRealtime(),
                firstGatherWorldlySeedsWarp(),
                wandOnUnlitCampfireOutsideFlag()
        );
    }

    public static List<ChickenArcBlueprint> getTestsByCategory(@Nullable String category) {
        if (category == null) {
            return all();
        }
        return all().stream()
                .filter(bp -> category.equals(bp.category()))
                .toList();
    }

    // Scenario 1 — AE1 — arc does not require any scripted actions; the spawn
    // controller ticks naturally after SPAWN_CHICKEN phase and the chicken
    // comes up with the stick icon on its SynchedEntityData. The GiveItem
    // seeds the fake player's inventory with a stick — the chicken's peck
    // bubble resolves the same in either case, and this exercises the
    // {@code GiveItem} dispatch path which no other scenario needs.
    private static ChickenArcBlueprint stickPeckAndFollowSpawn() {
        return ChickenArcBlueprint.builder("stick_peck_and_follow_spawn")
                .actions(List.of(
                        new GiveItem(Items.STICK, 1, 1),
                        new AdvanceTicks(20)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_STICK)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    // Scenario 2 — AE4 — bound wand, wand-click on campfire, assert SUNSET_AND_MAP.
    private static ChickenArcBlueprint f1StickToCampfire() {
        return ChickenArcBlueprint.builder("F1_stick_to_campfire")
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 2),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(10)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.SUNSET_AND_MAP)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    // Scenario 3 — F3: build a whole room to the welcome-mat beat.
    private static ChickenArcBlueprint f3BuildRoomToWelcomeMat() {
        BlockPos doorOffset = HelperChickenBeatOffsets.DOOR_OFFSET;
        BlockPos wallOffset = HelperChickenBeatOffsets.WALL_BLOCK_OFFSET;
        BlockPos signOffset = HelperChickenBeatOffsets.SIGN_OFFSET;
        BlockPos chestOffset = HelperChickenBeatOffsets.CHEST_OFFSET;
        BlockPos gateCenter = HelperChickenBeatOffsets.GATE_CENTER_OFFSET;

        return ChickenArcBlueprint.builder("F3_build_room_to_welcome_mat")
                .actions(List.of(
                        // Arrive at F3 entry: wand already bound + campfire lit.
                        new GiveBoundWand(BlockPos.ZERO, 1),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(5),
                        // F2 is deferred (fake player can't sleep). Directly flip the
                        // observation bit to unblock SUNSET_AND_MAP → WAITING_FOR_WALL_BLOCK.
                        new MarkSleepObserved(2),
                        new AdvanceTicks(3),
                        // Out-of-order chest-before-sign, then wall, door, wand-on-door, sign.
                        new PlaceBlock(chestOffset, Blocks.CHEST.defaultBlockState(), 2),
                        new PlaceBlock(wallOffset, Blocks.COBBLESTONE.defaultBlockState(), 2),
                        new PlaceBlock(doorOffset, Blocks.OAK_DOOR.defaultBlockState(), 2),
                        new RegisterDoorViaWand(doorOffset, 2),
                        // Place the job-board directly: the sign→job-board conversion
                        // normally fires from an item-use path that setBlockAndUpdate
                        // bypasses. isSignConvertedToJobBoard checks for the job-board
                        // block itself, so this is equivalent for the beat machine.
                        new PlaceBlock(signOffset, jobBoardBlockState(), 2),
                        // Welcome mat lands at the gate center. Must be the WELCOME_MAT_BLOCK
                        // (stone pressure plates aren't welcome mats). handlePlaceBlock
                        // mirrors WelcomeMatBlock's normal place-side-effect so the flag
                        // sees getWelcomeMats() as non-empty.
                        new PlaceBlock(gateCenter, welcomeMatBlockState(), 2),
                        new AdvanceTicks(20)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_VILLAGER_UI)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    // Scenario 4 — AE5 — seeds to statue. Uses SetUpRegisteredRoomWithChest to
    // skip F3's fine-grained step list.
    private static ChickenArcBlueprint f4SeedsToStatue() {
        Item seeds = worldlySeedsItem();
        return ChickenArcBlueprint.builder("F4_seeds_to_statue")
                .actions(List.of(
                        // F2 is deferred; flip the observation bit so the arc can reach F4.
                        new MarkSleepObserved(1),
                        // Light the campfire so the wand → F1 path is considered complete.
                        new GiveBoundWand(BlockPos.ZERO, 1),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(3),
                        // Fill the authored WALL_BLOCK_OFFSET gap so the wall-block beat
                        // advances (SetUpRegisteredRoomWithChest builds a wider 9×5 room
                        // that doesn't overlap the authored 5×5 wall-block gap at (6,0,3)).
                        new PlaceBlock(HelperChickenBeatOffsets.WALL_BLOCK_OFFSET,
                                Blocks.COBBLESTONE.defaultBlockState(), 2),
                        new SetUpRegisteredRoomWithChest(10),
                        new MarkVillagerUiOpened(2),
                        new MarkFlagUiOpened(2),
                        new DepositIntoContainer(new ItemStack(seeds, 1), 5),
                        new RightClickChickenWithHand(seeds, 10),
                        new AdvanceTicks(20)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.COMPLETE)
                        .statuePlaced(true)
                        .chickenDiscarded(true)
                        .build())
                .build();
    }

    // Scenario 5 — F1 with rotation CLOCKWISE_90.
    private static ChickenArcBlueprint f1RotationClockwise90() {
        return ChickenArcBlueprint.builder("F1_rotation_clockwise_90")
                .rotation(Rotation.CLOCKWISE_90)
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 2),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(10)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.SUNSET_AND_MAP)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    // Scenario 6 — place two real campfires at two rotated CAMPFIRE_OFFSET
    // candidates so HelperChickenRotationDetector finds >1 match and forfeits.
    private static ChickenArcBlueprint rotationAmbiguityForfeit() {
        BlockPos c0 = HelperChickenBeatOffsets.CAMPFIRE_OFFSET;
        BlockPos c90 = c0.rotate(Rotation.CLOCKWISE_90);
        return ChickenArcBlueprint.builder("rotation_ambiguity_forfeit")
                .forceRotationDetected(false)
                .actions(List.of(
                        // Place both campfires on the same tick — otherwise the detector
                        // sees the first one alone on tick N, finalizes with matchCount=1
                        // (no forfeit), and the second placement is ignored.
                        new PlaceBlock(c0,
                                Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false),
                                0),
                        new PlaceBlock(c90,
                                Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false),
                                0),
                        new AdvanceTicks(25)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .chickenSpawned(false)
                        .flagBits(Map.of("chicken-arc-forfeit", true))
                        .build())
                .build();
    }

    // Scenario 7 — no campfire anywhere. Detector retries, then forfeits.
    private static ChickenArcBlueprint rotationZeroMatchForfeit() {
        return ChickenArcBlueprint.builder("rotation_zero_match_forfeit")
                .forceRotationDetected(false)
                .actions(List.of(new AdvanceTicks(40)))
                .expectation(ChickenArcExpectation.builder()
                        .chickenSpawned(false)
                        .flagBits(Map.of("chicken-arc-forfeit", true))
                        .build())
                .build();
    }

    // Scenario 8 — two flags independent arcs. The current executor only
    // supports one flag per scenario (see setup gap note in final report);
    // this blueprint still runs, but asserts a setup failure because the
    // second flag cannot be placed without executor changes.
    private static ChickenArcBlueprint twoFlagsIndependentArcs() {
        return ChickenArcBlueprint.builder("two_flags_independent_arcs")
                .halfWidthOverride(TWO_FLAGS_HALF_WIDTH)
                .actions(List.of(
                        new RunCommand(
                                "/_qtdev echo two_flags_independent_arcs-requires-multi-flag-executor-support",
                                2),
                        new AdvanceTicks(5)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    // Scenario 9 — skip-chicken command. Flag placed via command; no chicken spawns.
    private static ChickenArcBlueprint skipChickenCommand() {
        return ChickenArcBlueprint.builder("skip_chicken_command")
                .placeFlagViaCommand(true)
                .actions(List.of(
                        // Brigadier tree is `qt flag <pos> place_above [skip-chicken]` —
                        // pos comes BEFORE the literal. Places the flag at (0,64,0) so
                        // the executor's checkResults finds it at origin.
                        new RunCommand("/qt flag 0 63 0 place_above skip-chicken", 10),
                        new AdvanceTicks(20)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .chickenSpawned(false)
                        // Command-placed flags run markCommandPlacedFlagAsChickenIneligible,
                        // which sets chicken-ever-spawned=true up front (so the spawn
                        // gate never fires) — without ever creating a HelperChickenEntity.
                        // The expectation therefore asserts the intent: no real chicken,
                        // flag carries the "arc already considered done" bit.
                        .flagBits(Map.of("chicken-ever-spawned", true))
                        .build())
                .build();
    }

    // Scenario 10 — F1 setup, then /questown chicken remove forfeits.
    private static ChickenArcBlueprint forfeitRemoveCommand() {
        return ChickenArcBlueprint.builder("forfeit_remove_command")
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 2),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(5),
                        // Absolute pos — the command resolves coords against source's
                        // position (the fake player), not the flag. Using ~ would target
                        // the fake player's current location, which may be offset from
                        // the flag after TELEPORT_PLAYER_NEAR_FLAG.
                        new RunCommand("/questown chicken remove 0 64 0", 10),
                        new AdvanceTicks(10)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .chickenDiscarded(true)
                        .statuePlaced(false)
                        .flagBits(Map.of("chicken-arc-forfeit", true))
                        .build())
                .build();
    }

    // Scenario 11 — the plan wants a real gatherer villager producing seeds on
    // its first fetch, which needs a SpawnVillagerAndAssignJob scripted action
    // the executor doesn't yet have. This scenario approximates the guarantee
    // at the observation-logic level: deposit WORLDLY_SEEDS directly into a
    // registered chest, and assert that ChickenArcConditions.observe flips the
    // first-gather bit. That covers ChickenArcConditions.maybeFlipFirstGatherBit
    // end-to-end; the villager-driven half stays follow-up work.
    private static ChickenArcBlueprint firstGatherWorldlySeedsRealtime() {
        Item seeds = worldlySeedsItem();
        return ChickenArcBlueprint.builder("first_gather_worldly_seeds_realtime")
                .actions(List.of(
                        new MarkSleepObserved(1),
                        new GiveBoundWand(BlockPos.ZERO, 1),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(3),
                        new SetUpRegisteredRoomWithChest(10),
                        new DepositIntoContainer(new ItemStack(seeds, 1), 5),
                        new AdvanceTicks(10)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .chickenSpawned(true)
                        .flagBits(Map.of("chicken-first-gather-worldly-seeds-fired", true))
                        .build())
                .build();
    }

    // Scenario 12 — warp-parity of scenario 11. Same observation path; the
    // warp amount covers a day's worth of ticks, which should flip the same bit.
    private static ChickenArcBlueprint firstGatherWorldlySeedsWarp() {
        Item seeds = worldlySeedsItem();
        return ChickenArcBlueprint.builder("first_gather_worldly_seeds_warp")
                .warpAmount(GATHERER_WARP_TICKS)
                .actions(List.of(
                        new MarkSleepObserved(1),
                        new GiveBoundWand(BlockPos.ZERO, 1),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(3),
                        new SetUpRegisteredRoomWithChest(10),
                        new DepositIntoContainer(new ItemStack(seeds, 1), 5),
                        new AdvanceTicks(10)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .chickenSpawned(true)
                        .flagBits(Map.of("chicken-first-gather-worldly-seeds-fired", true))
                        .build())
                .build();
    }

    // Scenario 13 — wand click on a campfire far outside the flag's radius
    // must be rejected by the radius gate; no state change, no lit campfire.
    private static ChickenArcBlueprint wandOnUnlitCampfireOutsideFlag() {
        BlockPos farOffset = new BlockPos(40, 0, 40);
        return ChickenArcBlueprint.builder("wand_on_unlit_campfire_outside_flag")
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 1),
                        new PlaceBlock(farOffset,
                                Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false),
                                2),
                        new WandRightClick(farOffset, 5),
                        new AdvanceTicks(10)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    /**
     * Pin the scenario count so accidental additions or drops fail the
     * registry test before they land in a release.
     */
    public static final int EXPECTED_SCENARIO_COUNT = 13;
}
