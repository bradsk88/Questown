package ca.bradj.questown.town.entity;

import ca.bradj.questown.blocks.FlagPhase;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.items.RelocationDeedItem;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.questown.town.rooms.TownRoomsMapSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Moves a dormant town to a new flag: copies its data, re-anchors its doors and gates, and removes the original. Same dimension only. */
public final class TownRelocation {

    private TownRelocation() {
    }

    public enum RelocationResult {
        OK,
        CROSS_DIMENSION,
        MALFORMED_REFERENCE,
        ORIGINAL_UNREACHABLE
    }

    /** What to do with doors and gates that land outside the new flag's reach: bring them all, or leave them behind. */
    public enum FarFixturePolicy {
        BRING_ALL,
        LEAVE_BEHIND
    }

    /** Places a relocation deed: copies the town to a new flag at {@code targetPos} and removes the original. Checks everything first, so a failure changes nothing. */
    public static RelocationResult place(
            ServerLevel level,
            ItemStack deedStack,
            BlockPos targetPos
    ) {
        return place(level, deedStack, targetPos, FarFixturePolicy.BRING_ALL);
    }

    /** As above, with a policy for fixtures outside the new flag's reach, using the configured tick radius. */
    public static RelocationResult place(
            ServerLevel level,
            ItemStack deedStack,
            BlockPos targetPos,
            FarFixturePolicy policy
    ) {
        return place(level, deedStack, targetPos, policy, Config.TOWN_TICK_RADIUS.get());
    }

    /** As above, with the radius passed in. */
    public static RelocationResult place(
            ServerLevel level,
            ItemStack deedStack,
            BlockPos targetPos,
            FarFixturePolicy policy,
            long tickRadiusSq
    ) {
        UUID townUuid = RelocationDeedItem.getTownUuid(deedStack);
        BlockPos originalPos = RelocationDeedItem.getFlagPos(deedStack);
        ResourceLocation deedDimension = RelocationDeedItem.getDimension(deedStack);

        RelocationResult validation = validate(
                townUuid, originalPos, deedDimension, level.dimension().location()
        );
        if (validation != RelocationResult.OK) {
            return validation;
        }

        TownFlagBlockEntity original = TownFlagBlockEntity.getFromPos(level, originalPos);
        if (original == null || !isDormant(level, originalPos)) {
            return RelocationResult.ORIGINAL_UNREACHABLE;
        }

        // Copy the whole town; only the doors' and gates' heights change.
        CompoundTag carried = freshTownData(original);
        rebaseFixturesInTag(carried, originalPos.getY(), targetPos.getY());
        if (policy == FarFixturePolicy.LEAVE_BEHIND) {
            dropFarFixturesInTag(carried, targetPos, tickRadiusSq);
        }

        TownFlagBlockEntity relocated = placeActiveFlag(level, targetPos);
        if (relocated == null) {
            return RelocationResult.ORIGINAL_UNREACHABLE;
        }
        hydrateFrom(relocated, carried, townUuid);

        destroyOriginal(level, originalPos, original);
        return RelocationResult.OK;
    }

    private static boolean isDormant(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(TownFlagBlock.PHASE)
                && state.getValue(TownFlagBlock.PHASE) == FlagPhase.DORMANT;
    }

    /** The flag's town data, saved fresh. */
    private static CompoundTag freshTownData(TownFlagBlockEntity flag) {
        CompoundTag persistent = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(persistent);
        return persistent.copy();
    }

    private static void rebaseFixturesInTag(
            CompoundTag townData,
            int oldFlagY,
            int newFlagY
    ) {
        if (oldFlagY == newFlagY || !townData.contains(TownFlagTileData.NBT_ROOMS)) {
            return;
        }
        CompoundTag rooms = townData.getCompound(TownFlagTileData.NBT_ROOMS);
        TownRoomsMapSerializer.INSTANCE.rebaseFixtureY(rooms, oldFlagY, newFlagY);
        townData.put(TownFlagTileData.NBT_ROOMS, rooms);
    }

    /** Drops carried doors and gates beyond the new flag's reach. */
    private static void dropFarFixturesInTag(
            CompoundTag townData,
            BlockPos targetPos,
            long tickRadiusSq
    ) {
        if (!townData.contains(TownFlagTileData.NBT_ROOMS)) {
            return;
        }
        CompoundTag rooms = townData.getCompound(TownFlagTileData.NBT_ROOMS);
        TownRoomsMapSerializer.INSTANCE.dropFixtures(
                rooms, targetPos.getY(), pos -> isBeyondRadius(targetPos, pos, tickRadiusSq)
        );
        townData.put(TownFlagTileData.NBT_ROOMS, rooms);
    }

    /** The town's doors and gates that would land outside the new flag's reach, for the confirmation screen. Empty when the deed can't be read. */
    public static List<TownPosition> farFixturesFor(
            ServerLevel level,
            ItemStack deedStack,
            BlockPos targetPos
    ) {
        UUID townUuid = RelocationDeedItem.getTownUuid(deedStack);
        BlockPos originalPos = RelocationDeedItem.getFlagPos(deedStack);
        ResourceLocation deedDimension = RelocationDeedItem.getDimension(deedStack);
        if (validate(townUuid, originalPos, deedDimension, level.dimension().location()) != RelocationResult.OK) {
            return List.of();
        }
        TownFlagBlockEntity original = TownFlagBlockEntity.getFromPos(level, originalPos);
        if (original == null) {
            return List.of();
        }
        return fixturesBeyondRadius(
                targetPos, originalPos.getY(), registeredFixtures(original), Config.TOWN_TICK_RADIUS.get()
        );
    }

    /** Registered doors and fence gates. */
    private static Set<TownPosition> registeredFixtures(TownFlagBlockEntity flag) {
        Set<TownPosition> all = new HashSet<>(flag.getRoomHandle().getAllRegisteredDoors());
        if (flag.getRoomHandle() instanceof TownRoomsHandle rh) {
            all.addAll(rh.getRegisteredRooms().getRegisteredGates());
        }
        return all;
    }

    /** The fixtures that would be too far from a flag at {@code targetPos}. */
    public static List<TownPosition> fixturesBeyondRadius(
            BlockPos targetPos,
            int flagY,
            Iterable<TownPosition> fixtures,
            long tickRadiusSq
    ) {
        List<TownPosition> beyond = new ArrayList<>();
        for (TownPosition fixture : fixtures) {
            BlockPos abs = new BlockPos(fixture.x, fixture.getY(flagY), fixture.z);
            if (isBeyondRadius(targetPos, abs, tickRadiusSq)) {
                beyond.add(fixture);
            }
        }
        return beyond;
    }

    /** Whether {@code fixturePos} is beyond the tick radius from {@code targetPos}, as the ticker measures it. */
    public static boolean isBeyondRadius(
            BlockPos targetPos,
            BlockPos fixturePos,
            long tickRadiusSq
    ) {
        return (long) targetPos.distSqr(fixturePos) > tickRadiusSq;
    }

    private static TownFlagBlockEntity placeActiveFlag(
            ServerLevel level,
            BlockPos targetPos
    ) {
        BlockState state = BlocksInit.COBBLESTONE_TOWN_FLAG.get().defaultBlockState()
                .setValue(TownFlagBlock.PHASE, FlagPhase.ACTIVE)
                .setValue(TownFlagBlock.INACTIVE, false);
        level.setBlockAndUpdate(targetPos, state);
        return TownFlagBlockEntity.getFromPos(level, targetPos);
    }

    /** Loads the carried town into the new flag, as a chunk reload does, then brings the villagers back. */
    private static void hydrateFrom(
            TownFlagBlockEntity relocated,
            CompoundTag carried,
            UUID townUuid
    ) {
        relocated.initializers.clear();
        CompoundTag target = Compat.getBlockStoredTagData(relocated);
        for (String key : carried.getAllKeys()) {
            target.put(key, carried.get(key));
        }
        AbsorbedJournals.adoptTownState(target, TownFlagState.NBT_TOWN_STATE);
        relocated.adoptRelocatedIdentity(townUuid);
        relocated.initializeFreshFlag(true);
        relocated.initializers.add(respawnRosterTask());
    }

    private static Function<TownFlagBlockEntity, Boolean> respawnRosterTask() {
        return flag -> {
            // Bring each villager back under its own UUID.
            for (UUID villager : flag.villagerHandle.getFullness().keySet()) {
                flag.addImmediateReward(new SpawnVisitorReward(flag, persistedVillagerUuid(villager)));
            }
            return true;
        };
    }

    /** A saved UUID as a {@link VillagerUUID}. */
    static VillagerUUID persistedVillagerUuid(UUID uuid) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", uuid);
        return VillagerUUID.fromNBT(tag, "uuid");
    }

    private static void destroyOriginal(
            ServerLevel level,
            BlockPos pos,
            TownFlagBlockEntity original
    ) {
        original.getVillagerHandle().entities().forEach(LivingEntity::kill);
        level.removeBlockEntity(pos);
        level.removeBlock(pos, false);
    }

    /** Re-anchors every fixture to a flag at {@code newFlagY}, keeping its world height. */
    public static List<TownPosition> planFixtureRebase(
            int oldFlagY,
            int newFlagY,
            Iterable<TownPosition> fixtures
    ) {
        List<TownPosition> rebased = new ArrayList<>();
        for (TownPosition fixture : fixtures) {
            rebased.add(fixture.rebasedTo(oldFlagY, newFlagY));
        }
        return rebased;
    }

    /** A relocation needs a complete deed (town, flag position, dimension) and a target in the same dimension. */
    public static RelocationResult validate(
            @Nullable UUID townUuid,
            @Nullable BlockPos flagPos,
            @Nullable ResourceLocation deedDimension,
            ResourceLocation targetDimension
    ) {
        if (townUuid == null || flagPos == null || deedDimension == null) {
            return RelocationResult.MALFORMED_REFERENCE;
        }
        if (!deedDimension.equals(targetDimension)) {
            return RelocationResult.CROSS_DIMENSION;
        }
        return RelocationResult.OK;
    }
}
