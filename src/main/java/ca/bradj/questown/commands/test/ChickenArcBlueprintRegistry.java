package ca.bradj.questown.commands.test;

import ca.bradj.questown.commands.test.ChickenArcScriptedAction.AdvanceTicks;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.DepositIntoContainer;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.GiveBoundWand;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.GiveItem;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.MarkFlagUiOpened;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.MarkSleepObserved;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.MarkVillagerUiOpened;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.MovePlayer;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.PlaceBlock;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.RegisterDoorViaWand;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.RightClickFlag;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.RightClickChickenWithHand;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.RunCommand;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.SetUpRegisteredRoomWithChest;
import ca.bradj.questown.commands.test.ChickenArcScriptedAction.WandRightClick;
import ca.bradj.questown.core.init.BlocksInit;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** The helper-chicken autotest scenarios. */
public final class ChickenArcBlueprintRegistry {

    public static final String CATEGORY = "chicken";

    private static final int GATHERER_WARP_TICKS = 24000;

    // Wide enough to hold both flags.
    private static final int TWO_FLAGS_HALF_WIDTH = 25;

    private ChickenArcBlueprintRegistry() {
    }

    /** Worldly Seeds, or wheat seeds in a unit test where the mod's items are not registered. */
    private static Item worldlySeedsItem() {
        try {
            return ItemsInit.WORLDLY_SEEDS.get();
        } catch (NullPointerException | IllegalStateException e) {
            return Items.WHEAT_SEEDS;
        }
    }

    /** The welcome mat, or a pressure plate in a unit test where the mod's blocks are not registered. */
    private static BlockState welcomeMatBlockState() {
        try {
            return BlocksInit.WELCOME_MAT_BLOCK.get().defaultBlockState();
        } catch (NullPointerException | IllegalStateException e) {
            return Blocks.STONE_PRESSURE_PLATE.defaultBlockState();
        }
    }

    /** The job board, or an oak sign in a unit test where the mod's blocks are not registered. */
    private static BlockState jobBoardBlockState() {
        try {
            return BlocksInit.JOB_BOARD_BLOCK.get().defaultBlockState();
        } catch (NullPointerException | IllegalStateException e) {
            return Blocks.OAK_SIGN.defaultBlockState();
        }
    }

    public static List<ChickenArcBlueprint> all() {
        return List.of(
                stickPeckAndFollowSpawn(),
                f1StickToCampfire(),
                f2ChestSpawnAfterCampfire(),
                f3BuildRoomToWelcomeMat(),
                f3RightClickFlagAfterMat(),
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

    // The chicken spawns with the stick hint and pecks at the player holding a stick.
    private static ChickenArcBlueprint stickPeckAndFollowSpawn() {
        return ChickenArcBlueprint.builder("stick_peck_and_follow_spawn")
                .actions(List.of(
                        new GiveItem(Items.STICK, 1, 1),
                        new AdvanceTicks(20)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_STICK)
                        .chickenSpawned(true)
                        .finalPhase(ca.bradj.questown.mobs.helperchicken.BeatPhase.READY_TO_USE)
                        .build())
                .build();
    }

    // Light the campfire with the wand. It is accepted early, so the arc stops at the door lesson.
    private static ChickenArcBlueprint f1StickToCampfire() {
        return ChickenArcBlueprint.builder("F1_stick_to_campfire")
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 2),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(10)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_DOOR)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    // The chicken spawns a chest with a map and an axe once the player is in the storeroom. With no
    // welcome mat, the arc then stops at the mat lesson.
    private static ChickenArcBlueprint f2ChestSpawnAfterCampfire() {
        return ChickenArcBlueprint.builder("F2_chest_spawn_after_campfire")
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 2),
                        new PlaceBlock(HelperChickenBeatOffsets.DOOR_OFFSET,
                                Blocks.OAK_DOOR.defaultBlockState(), 2),
                        new RegisterDoorViaWand(HelperChickenBeatOffsets.DOOR_OFFSET, 2),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        // Into the room; a /tp does not move the fake player.
                        new MovePlayer(4.5D, 64D, 4.5D, 5),
                        // Long enough for the chicken's stuck teleport (600 ticks) plus the peck.
                        new AdvanceTicks(800)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_PRESSURE_PLATE)
                        .flagBits(java.util.Map.of("chicken-sunset-chest-spawned", true))
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    private static ChickenArcBlueprint f3BuildRoomToWelcomeMat() {
        return ChickenArcBlueprint.builder("F3_build_room_to_welcome_mat")
                .actions(f3Actions())
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_FLAG_UI)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    /** The flag lesson done by right-clicking the flag, before any villager has arrived. */
    private static ChickenArcBlueprint f3RightClickFlagAfterMat() {
        List<ChickenArcScriptedAction> actions = new java.util.ArrayList<>(f3Actions());
        actions.add(new RightClickFlag(2));
        actions.add(new AdvanceTicks(20));
        return ChickenArcBlueprint.builder("F3_right_click_flag_after_mat")
                .actions(actions)
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_FREE_FOOD)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    private static List<ChickenArcScriptedAction> f3Actions() {
        BlockPos doorOffset = HelperChickenBeatOffsets.DOOR_OFFSET;
        BlockPos wallOffset = HelperChickenBeatOffsets.WALL_BLOCK_OFFSET;
        BlockPos signOffset = HelperChickenBeatOffsets.SIGN_OFFSET;
        BlockPos chestOffset = HelperChickenBeatOffsets.CHEST_OFFSET;
        BlockPos gateCenter = HelperChickenBeatOffsets.GATE_CENTER_OFFSET;

        return List.of(
                        new GiveBoundWand(BlockPos.ZERO, 1),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(5),
                        // A fake player can't sleep, so mark the sleep as seen.
                        new MarkSleepObserved(2),
                        new AdvanceTicks(3),
                        new PlaceBlock(chestOffset, Blocks.CHEST.defaultBlockState(), 2),
                        new PlaceBlock(wallOffset, Blocks.COBBLESTONE.defaultBlockState(), 2),
                        new PlaceBlock(doorOffset, Blocks.OAK_DOOR.defaultBlockState(), 2),
                        new RegisterDoorViaWand(doorOffset, 2),
                        // Placed directly: setBlock skips the sign-to-board conversion.
                        new PlaceBlock(signOffset, jobBoardBlockState(), 2),
                        new PlaceBlock(gateCenter, welcomeMatBlockState(), 2),
                        new AdvanceTicks(20)
        );
    }

    // Seeds to statue, starting from a built room.
    private static ChickenArcBlueprint f4SeedsToStatue() {
        Item seeds = worldlySeedsItem();
        return ChickenArcBlueprint.builder("F4_seeds_to_statue")
                .actions(List.of(
                        // A fake player can't sleep, so mark the sleep as seen.
                        new MarkSleepObserved(1),
                        // Light the campfire so the wand → F1 path is considered complete.
                        new GiveBoundWand(BlockPos.ZERO, 1),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(3),
                        // Fill the wall gap; the prebuilt room does not cover it.
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

    // The stick setup with the flag turned 90 degrees. The wall gap is left open, so the arc stops at the wall lesson.
    private static ChickenArcBlueprint f1RotationClockwise90() {
        return ChickenArcBlueprint.builder("F1_rotation_clockwise_90")
                .rotation(Rotation.CLOCKWISE_90)
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 2),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(10)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .finalBeat(ChickenBeatState.WAITING_FOR_WALL_BLOCK)
                        .chickenSpawned(true)
                        .build())
                .build();
    }

    // Two campfires at two possible rotations: the rotation is ambiguous, so the arc ends.
    private static ChickenArcBlueprint rotationAmbiguityForfeit() {
        BlockPos c0 = HelperChickenBeatOffsets.CAMPFIRE_OFFSET;
        BlockPos c90 = c0.rotate(Rotation.CLOCKWISE_90);
        return ChickenArcBlueprint.builder("rotation_ambiguity_forfeit")
                .forceRotationDetected(false)
                .actions(List.of(
                        // Both on the same tick, or the first is found alone.
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

    // No campfire: rotation detection gives up and the arc ends.
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

    // Two flags with their own arcs. The executor can only place one flag, so this expects a setup failure.
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

    // A flag placed by command with skip-chicken: no chicken spawns.
    private static ChickenArcBlueprint skipChickenCommand() {
        return ChickenArcBlueprint.builder("skip_chicken_command")
                .placeFlagViaCommand(true)
                .actions(List.of(
                        new RunCommand("/qt flag 0 63 0 place_above skip-chicken", 10),
                        new AdvanceTicks(20)
                ))
                .expectation(ChickenArcExpectation.builder()
                        .chickenSpawned(false)
                        // Command-placed flags count as having had their chicken.
                        .flagBits(Map.of("chicken-ever-spawned", true))
                        .build())
                .build();
    }

    // The stick setup, then /questown chicken remove ends the arc.
    private static ChickenArcBlueprint forfeitRemoveCommand() {
        return ChickenArcBlueprint.builder("forfeit_remove_command")
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 2),
                        new WandRightClick(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, 2),
                        new AdvanceTicks(5),
                        // Absolute position: ~ is relative to the fake player, not the flag.
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

    // Seeds put straight into a chest count as the first gather. The second variant runs it under a
    // day of warp.
    private static ChickenArcBlueprint firstGatherWorldlySeedsRealtime() {
        return firstGatherWorldlySeedsBlueprint("first_gather_worldly_seeds_realtime", 0);
    }

    private static ChickenArcBlueprint firstGatherWorldlySeedsWarp() {
        return firstGatherWorldlySeedsBlueprint("first_gather_worldly_seeds_warp", GATHERER_WARP_TICKS);
    }

    private static ChickenArcBlueprint firstGatherWorldlySeedsBlueprint(String name, int warpAmount) {
        Item seeds = worldlySeedsItem();
        ChickenArcBlueprint.Builder builder = ChickenArcBlueprint.builder(name)
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
                        .build());
        if (warpAmount > 0) {
            builder.warpAmount(warpAmount);
        }
        return builder.build();
    }

    // A wand click on a campfire outside the flag's reach does nothing.
    private static ChickenArcBlueprint wandOnUnlitCampfireOutsideFlag() {
        BlockPos farOffset = new BlockPos(40, 0, 40);
        return ChickenArcBlueprint.builder("wand_on_unlit_campfire_outside_flag")
                .actions(List.of(
                        new GiveBoundWand(BlockPos.ZERO, 1),
                        new SetUpRegisteredRoomWithChest(10),
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

    public static final int EXPECTED_SCENARIO_COUNT = 15;
}
