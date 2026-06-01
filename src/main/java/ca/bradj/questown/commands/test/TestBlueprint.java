package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
        @Nullable Integer minKnowledgeGrowth
) {
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
             false, false, null, null, null, null, null, false, null);
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
             false, false, null, null, null, null, null, false, null);
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
             extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, null);
    }

    public TestBlueprint withMinKnowledgeGrowth(int n) {
        return new TestBlueprint(
                roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
                roomId, expectation, supplyDoorOffset, warpAmountOverride, startTimeTick,
                villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
                realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
                extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, n);
    }

    public TestBlueprint withExpectation(TestExpectation e) {
        return new TestBlueprint(
                roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
                roomId, e, supplyDoorOffset, warpAmountOverride, startTimeTick,
                villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
                realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
                extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, minKnowledgeGrowth);
    }

    public TestBlueprint withWarpAmountOverride(int n) {
        return new TestBlueprint(
                roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
                roomId, expectation, supplyDoorOffset, n, startTimeTick,
                villagerCount, realtimePhase, realtimeTicks, drainHungerBeforeTest, skipWarp,
                realtimeExpectation, minExpectedFullnessAfter, extraBlockRoomOffset,
                extraBlockRoomId, expectedVillagerHeld, useNaturalWarp, minKnowledgeGrowth);
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
