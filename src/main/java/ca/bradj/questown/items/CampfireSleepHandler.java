package ca.bradj.questown.items;

import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;
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
    private static final Map<UUID, BlockPos> campfirePositions = new ConcurrentHashMap<>();
    // The flag each sleeper's campfire belongs to.
    private static final Map<UUID, BlockPos> sleepingFlagPositions = new ConcurrentHashMap<>();

    public static void beginCampfireSleep(
            ServerPlayer player,
            Level level,
            BlockPos campfirePos,
            TownFlagBlockEntity parent
    ) {
        // A double-click lands a second use before the sleep starts; ignore it.
        if (player.isSleeping()) {
            return;
        }
        BlockState campfireState = level.getBlockState(campfirePos);
        if (!campfireState.hasProperty(CampfireBlock.LIT) || !campfireState.getValue(CampfireBlock.LIT)) {
            Util.onScreenText(() -> player, "message.wand.campfire.not_lit");
            return;
        }

        BlockPos headPos = findSafeSleepPosition(level, campfirePos, player.position());
        if (headPos == null) {
            Util.onScreenText(() -> player, "message.wand.campfire.no_safe_position");
            return;
        }

        Direction facing = findBedFacing(headPos, campfirePos);
        if (facing == null) {
            Util.onScreenText(() -> player, "message.wand.campfire.no_safe_position");
            return;
        }

        UUID uuid = player.getUUID();
        campfireSleepers.add(uuid);
        tempBedPositions.put(uuid, headPos);
        tempBedFacings.put(uuid, facing);
        campfirePositions.put(uuid, campfirePos);        sleepingFlagPositions.put(uuid, parent.getBlockPos());

        placeTempBed(level, headPos, facing);

        ca.bradj.questown.QT.JOB_LOGGER.dev(
                "[campfire-sleep] beginCampfireSleep player={} campfire={} bedHead={} facing={} dayTime={} isDay={} isNight={}",
                player.getGameProfile().getName(), campfirePos, headPos, facing,
                level.getDayTime() % 24000L, level.isDay(), level.dimensionType().hasFixedTime() ? "fixed" : !level.isDay()
        );
        player.startSleepInBed(headPos).ifLeft(problem -> {
            ca.bradj.questown.QT.JOB_LOGGER.dev(
                    "[campfire-sleep] startSleepInBed returned LEFT for player={} problem={}",
                    player.getGameProfile().getName(), problem
            );
            if (problem != null && problem.getMessage() != null) {
                player.displayClientMessage(problem.getMessage(), true);
            }
            campfireSleepers.remove(uuid);
            tempBedPositions.remove(uuid);
            tempBedFacings.remove(uuid);
            campfirePositions.remove(uuid);
            sleepingFlagPositions.remove(uuid);
            removeTempBed(level, headPos, facing);
        }).ifRight(unit -> ca.bradj.questown.QT.JOB_LOGGER.dev(
                "[campfire-sleep] startSleepInBed RIGHT (sleeping started) player={} isSleeping={}",
                player.getGameProfile().getName(), player.isSleeping()
        ));
    }

    static @Nullable BlockPos findSafeSleepPosition(Level level, BlockPos campfirePos) {
        return findSafeSleepPosition(level, campfirePos, Vec3.atCenterOf(campfirePos));
    }

    /** The safe bed spot closest to the player, so the bed is close enough to sleep in. */
    static @Nullable BlockPos findSafeSleepPosition(Level level, BlockPos campfirePos, Vec3 playerPos) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos footCandidate = campfirePos.relative(dir);
            BlockPos headCandidate = footCandidate.relative(dir);
            if (!isSafeToLieOn(level, footCandidate) || !isSafeToLieOn(level, headCandidate)) {
                continue;
            }
            double distSq = playerPos.distanceToSqr(Vec3.atCenterOf(headCandidate));
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = headCandidate;
            }
        }
        return best;
    }

    private static boolean isSafeToLieOn(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        return isClearForSleep(level, pos) && isClearForSleep(level, pos.above());
    }

    private static boolean isClearForSleep(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // The bed replaces whatever is there, so a solid block (the flag) is not a free spot.
        if (ca.bradj.questown.mc.Compat.isSolid(state)) {
            return false;
        }
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
            return false;
        }
        FluidState fluidState = state.getFluidState();
        return !fluidState.is(FluidTags.WATER) && !fluidState.is(FluidTags.LAVA) && !state.is(Blocks.FIRE);
    }

    private static @Nullable Direction findBedFacing(BlockPos headPos, BlockPos campfirePos) {
        int dx = Integer.signum(headPos.getX() - campfirePos.getX());
        int dz = Integer.signum(headPos.getZ() - campfirePos.getZ());
        return Direction.fromNormal(dx, 0, dz);
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

    /** For autotests: picks and places the temp bed as {@link #beginCampfireSleep} does, without sleeping. Returns the bed head, or null. */
    public static @Nullable BlockPos placeTempBedForTest(Level level, BlockPos campfirePos) {
        BlockPos headPos = findSafeSleepPosition(level, campfirePos);
        if (headPos == null) {
            return null;
        }
        Direction facing = findBedFacing(headPos, campfirePos);
        if (facing == null) {
            return null;
        }
        placeTempBed(level, headPos, facing);
        return headPos;
    }

    public static boolean isCampfireSleeper(UUID uuid) {
        return campfireSleepers.contains(uuid);
    }

    public static int getCampfireSleeperCount() {
        return campfireSleepers.size();
    }

    public static void onWake(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ca.bradj.questown.QT.JOB_LOGGER.dev(
                "[campfire-sleep] onWake fired player={} dayTime={} isDay={}",
                player.getGameProfile().getName(),
                player.getLevel().getDayTime() % 24000L, player.getLevel().isDay()
        );
        campfireSleepers.remove(uuid);
        BlockPos headPos = tempBedPositions.remove(uuid);
        Direction facing = tempBedFacings.remove(uuid);
        if (headPos != null && facing != null) {
            removeTempBed(player.getLevel(), headPos, facing);
        }
        // The campfire stays lit: it guides the player back, and sleeping needs it lit.
        campfirePositions.remove(uuid);
        sleepingFlagPositions.remove(uuid);
        // TODO: Waking counted for the helper chicken's sleep lesson at this flag. For its implementation see #409, #410, #411 and #412.
    }

}
