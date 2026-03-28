package ca.bradj.questown.items;

import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CampfireSleepHandler {

    private static final Set<UUID> campfireSleepers = Collections.synchronizedSet(new HashSet<>());
    private static final Map<UUID, BlockPos> tempBedPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, Direction> tempBedFacings = new ConcurrentHashMap<>();

    public static void beginCampfireSleep(
            ServerPlayer player,
            Level level,
            BlockPos campfirePos,
            TownFlagBlockEntity parent
    ) {
        boolean campfireRegistered = TownCycle.findCampfire(parent.getBlockPos(), level)
                                              .filter(campfirePos::equals)
                                              .isPresent();
        if (!campfireRegistered) {
            Util.onScreenText(() -> player, "message.wand.campfire.not_registered");
            return;
        }

        BlockPos headPos = findSafeSleepPosition(level, campfirePos);
        if (headPos == null) {
            Util.onScreenText(() -> player, "message.wand.campfire.no_safe_position");
            return;
        }

        Direction facing = findBedFacing(level, headPos);
        if (facing == null) {
            Util.onScreenText(() -> player, "message.wand.campfire.no_safe_position");
            return;
        }

        UUID uuid = player.getUUID();
        campfireSleepers.add(uuid);
        tempBedPositions.put(uuid, headPos);
        tempBedFacings.put(uuid, facing);

        placeTempBed(level, headPos, facing);

        player.startSleepInBed(headPos).ifLeft(problem -> {
            if (problem != null) {
                player.displayClientMessage(problem.getMessage(), true);
            }
            campfireSleepers.remove(uuid);
            tempBedPositions.remove(uuid);
            tempBedFacings.remove(uuid);
            removeTempBed(level, headPos, facing);
        });
    }

    static @Nullable BlockPos findSafeSleepPosition(Level level, BlockPos campfirePos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = campfirePos.relative(dir);
            BlockPos below = candidate.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                continue;
            }
            if (!isClearForSleep(level, candidate) || !isClearForSleep(level, candidate.above())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean isClearForSleep(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
            return false;
        }
        Material mat = state.getMaterial();
        return !mat.isLiquid() && mat != Material.FIRE;
    }

    private static @Nullable Direction findBedFacing(Level level, BlockPos headPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos footPos = headPos.relative(dir.getOpposite());
            if (level.getBlockState(footPos).getMaterial().isReplaceable()) {
                return dir;
            }
        }
        return null;
    }

    private static void placeTempBed(Level level, BlockPos headPos, Direction facing) {
        BlockPos footPos = headPos.relative(facing.getOpposite());
        BlockState headState = Blocks.RED_BED.defaultBlockState()
                                             .setValue(BedBlock.FACING, facing)
                                             .setValue(BedBlock.PART, BedPart.HEAD)
                                             .setValue(BedBlock.OCCUPIED, false);
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                                             .setValue(BedBlock.FACING, facing)
                                             .setValue(BedBlock.PART, BedPart.FOOT)
                                             .setValue(BedBlock.OCCUPIED, false);
        level.setBlock(headPos, headState, 3);
        level.setBlock(footPos, footState, 3);
    }

    private static void removeTempBed(Level level, BlockPos headPos, Direction facing) {
        BlockPos footPos = headPos.relative(facing.getOpposite());
        level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(footPos, Blocks.AIR.defaultBlockState(), 3);
    }

    public static boolean isCampfireSleeper(UUID uuid) {
        return campfireSleepers.contains(uuid);
    }

    public static void onWake(ServerPlayer player) {
        UUID uuid = player.getUUID();
        campfireSleepers.remove(uuid);
        BlockPos headPos = tempBedPositions.remove(uuid);
        Direction facing = tempBedFacings.remove(uuid);
        if (headPos != null && facing != null) {
            removeTempBed(player.level, headPos, facing);
        }
    }
}
