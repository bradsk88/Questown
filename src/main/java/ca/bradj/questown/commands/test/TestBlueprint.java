package ca.bradj.questown.commands.test;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public record TestBlueprint(
        RoomType roomType,
        Collection<BlockPlacement> blocks,
        Collection<ItemStack> supplyItems,
        BlockPos doorOrGateOffset,
        BlockPos chestOffset,
        ResourceLocation roomId,
        TestExpectation expectation,
        @Nullable BlockPos supplyDoorOffset,
        @Nullable Integer warpAmountOverride,
        @Nullable Long startTimeTick,
        @Nullable Integer villagerCount,
        boolean realtimePhase,
        @Nullable Integer realtimeTicks,
        // Eating test support
        boolean drainHungerBeforeTest,
        boolean skipWarp,
        @Nullable TestExpectation realtimeExpectation,
        @Nullable Float minExpectedFullnessAfter,
        @Nullable BlockPos extraBlockRoomOffset,
        @Nullable ResourceLocation extraBlockRoomId,
        @Nullable TestExpectation expectedVillagerHeld,
        boolean useNaturalWarp,
        @Nullable Integer minKnowledgeGrowth,
        // When true, the warp path is expected to FAIL: the scenario passes at the suite level
        // only if warp fails (XFAIL) and is a suite failure if warp unexpectedly passes (XPASS).
        boolean expectedFailure,
        // Optional post-placement setup: runs after rooms are registered, before the job is
        // assigned, so it can seed chests by resolved absolute position (e.g. fabricate the
        // organizer's StockRequestItem + seed the source/decoy chests).
        @Nullable PostPlacementSetup setupHook
) {

    /**
     * A post-placement setup callback. Runs once after the scenario's room(s) are registered and
     * before the job is assigned, when chest positions are known. Returning {@code false} aborts
     * the scenario (e.g. the zero-pre-existing-ingredient guard tripped); the implementation is
     * responsible for reporting why via {@code output}.
     */
    @FunctionalInterface
    public interface PostPlacementSetup {
        boolean run(ServerLevel level, BlockPos flagPos, TownFlagBlockEntity town, TestOutput output);
    }
    public TestBlueprint(
            RoomType roomType,
            Collection<BlockPlacement> blocks,
            Collection<ItemStack> supplyItems,
            BlockPos doorOrGateOffset,
            BlockPos chestOffset,
            ResourceLocation roomId,
            TestExpectation expectation
    ) {
        this(roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
             roomId, expectation, null, null, null, null, false, null,
             false, false, null, null, null, null, null, false, null, false, null);
    }

    public TestBlueprint(
            RoomType roomType,
            Collection<BlockPlacement> blocks,
            Collection<ItemStack> supplyItems,
            BlockPos doorOrGateOffset,
            BlockPos chestOffset,
            ResourceLocation roomId,
            TestExpectation expectation,
            @Nullable BlockPos supplyDoorOffset
    ) {
        this(roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
             roomId, expectation, supplyDoorOffset, null, null, null, false, null,
             false, false, null, null, null, null, null, false, null, false, null);
    }

    // Compatibility constructor: old canonical signature (without minKnowledgeGrowth).
    public TestBlueprint(
            RoomType roomType,
            Collection<BlockPlacement> blocks,
            Collection<ItemStack> supplyItems,
            BlockPos doorOrGateOffset,
            BlockPos chestOffset,
            ResourceLocation roomId,
            TestExpectation expectation,
            @Nullable BlockPos supplyDoorOffset,
            @Nullable Integer warpAmountOverride,
            @Nullable Long startTimeTick,
            @Nullable Integer villagerCount,
            boolean realtimePhase,
            @Nullable Integer realtimeTicks,
            boolean drainHungerBeforeTest,
            boolean skipWarp,
            @Nullable TestExpectation realtimeExpectation,
            @Nullable Float minExpectedFullnessAfter,
            @Nullable BlockPos extraBlockRoomOffset,
            @Nullable ResourceLocation extraBlockRoomId,
            @Nullable TestExpectation expectedVillagerHeld,
            boolean useNaturalWarp
    ) {
        this(roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
             roomId, expectation, supplyDoorOffset, warpAmountOverride, startTimeTick,
             villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
             realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
             extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, null, false, null);
    }

    public TestBlueprint withMinKnowledgeGrowth(int n) {
        return new TestBlueprint(
                roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
                roomId, expectation, supplyDoorOffset, warpAmountOverride, startTimeTick,
                villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
                realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
                extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, n, expectedFailure, setupHook);
    }

    public TestBlueprint withExpectation(TestExpectation e) {
        return new TestBlueprint(
                roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
                roomId, e, supplyDoorOffset, warpAmountOverride, startTimeTick,
                villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
                realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
                extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, minKnowledgeGrowth, expectedFailure, setupHook);
    }

    public TestBlueprint withWarpAmountOverride(int n) {
        return new TestBlueprint(
                roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
                roomId, expectation, supplyDoorOffset, n, startTimeTick,
                villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
                realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
                extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, minKnowledgeGrowth, expectedFailure, setupHook);
    }

    public TestBlueprint withExpectedFailure(boolean v) {
        return new TestBlueprint(
                roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
                roomId, expectation, supplyDoorOffset, warpAmountOverride, startTimeTick,
                villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
                realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
                extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, minKnowledgeGrowth, v, setupHook);
    }

    public TestBlueprint withSetupHook(PostPlacementSetup hook) {
        return new TestBlueprint(
                roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
                roomId, expectation, supplyDoorOffset, warpAmountOverride, startTimeTick,
                villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
                realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
                extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, minKnowledgeGrowth, expectedFailure, hook);
    }

    public int effectiveVillagerCount() {
        return villagerCount != null ? villagerCount : 1;
    }

    public int effectiveRealtimeTicks(int warpTicks) {
        return realtimeTicks != null ? realtimeTicks : warpTicks;
    }

    public enum RoomType { FARM, INDOOR, WELCOME_MAT, BLOCK_ROOM }

    public record BlockPlacement(BlockPos offset, BlockState blockState) {}
}
