package ca.bradj.questown.town.entity;

import ca.bradj.questown.blocks.FlagPhase;
import ca.bradj.questown.blocks.TownFlagBlock;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Town-flag relocation (ADR-0009, #199): copy a dormant town to a new flag, re-anchor its
 * registered fixtures to the new origin, and destroy the original — atomically and same-dimension
 * only. Mirrors {@link TownShutdownController} as the home of the relocation ritual.
 *
 * <p>The pure decisions — fixture re-anchoring ({@link #planFixtureRebase}) and precondition
 * validation ({@link #validate}) — are split out so they can be unit-tested without a server
 * (see {@code TownRelocationTest}); the world-bound copy/destroy/re-activate path in {@link #place}
 * is exercised by the {@code flag/relocate_nearby} autotest.
 */
public final class TownRelocation {

    private TownRelocation() {
    }

    /** The outcome of attempting (or pre-checking) a relocation. */
    public enum RelocationResult {
        OK,
        CROSS_DIMENSION,
        MALFORMED_REFERENCE,
        ORIGINAL_UNREACHABLE
    }

    /**
     * Place a relocation deed: load the dormant flag it references, copy the town to a new flag at
     * {@code targetPos} with fixtures re-anchored, then destroy the original — atomically and
     * same-dimension only. Validates fully before mutating anything, so a non-{@link
     * RelocationResult#OK} result leaves the world (and the deed) untouched ("fail loudly, consume
     * nothing").
     */
    public static RelocationResult place(
            ServerLevel level,
            ItemStack deedStack,
            BlockPos targetPos
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

        // Whole-blob copy of the authoritative town data, re-anchoring only the origin-derived bits
        // (door/gate scanLevels). Everything else — roster, knowledge, economics, chicken arc —
        // rides along verbatim (ADR-0009 copy fidelity).
        CompoundTag carried = freshTownData(original);
        rebaseFixturesInTag(carried, originalPos.getY(), targetPos.getY());

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

    /**
     * The persistent tag is normally written on the tick; force a fresh serialize so the copy
     * reflects the current roster/knowledge, then snapshot it.
     */
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

    /**
     * Seed the new flag with the carried town data and re-run the chunk-reload init order
     * (deserialize, then common init) against it, so the town hydrates instead of starting empty.
     * {@code onLoad} already queued the fresh-flag path; discard it first. The roster respawn runs
     * last, after the villager data has been deserialized.
     */
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
        relocated.adoptRelocatedIdentity(townUuid);
        relocated.initializeFreshFlag(true);
        relocated.initializers.add(respawnRosterTask());
    }

    private static Function<TownFlagBlockEntity, Boolean> respawnRosterTask() {
        return flag -> {
            // The roster persists as villager stats keyed by UUID, independent of live entities;
            // spawn each member back under its original UUID so per-villager jobs/knowledge re-attach.
            for (UUID villager : flag.villagerHandle.getFullness().keySet()) {
                flag.addImmediateReward(new SpawnVisitorReward(flag, persistedVillagerUuid(villager)));
            }
            return true;
        };
    }

    /**
     * Wrap a persisted roster UUID as a {@link VillagerUUID} via the NBT round-trip — the sanctioned
     * persisted-identity constructor — rather than the deprecated {@code VillagerUUID.from}. Fitting
     * here: relocation rehydrates a roster that was itself persisted as raw UUIDs.
     */
    private static VillagerUUID persistedVillagerUuid(UUID uuid) {
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

    /**
     * Re-anchor every registered fixture to a flag at {@code newFlagY} while preserving each
     * fixture's absolute world Y (X/Z are already absolute). Pure aggregate of
     * {@link TownPosition#rebasedTo(int, int)} — the core invariant of relocation.
     */
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

    /**
     * The precondition decision: a relocation is allowed only when the deed carries a well-formed
     * reference (town UUID, original flag pos, origin dimension) and the target is in the same
     * dimension. Pure — the world-bound "original unreachable" check happens in {@link #place}.
     */
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
