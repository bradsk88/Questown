package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.QT;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The storeroom lesson: the chicken walks into the room, waits for the player to be inside, then drops a chest with a wooden axe. */
public class HelperChickenSunsetChestGoal extends Goal {

    private static final double ARRIVAL_DISTANCE_SQR = 2.0D;
    /** The path stops about a block and a half short; once it is done, that counts as arrived. */
    private static final double NAV_SETTLE_ARRIVAL_SQR = 9.0D;
    private static final int STUCK_TELEPORT_TICKS = 600;
    private static final int PECK_TICKS = 20;
    private static final double SPEED_MODIFIER = 1.0D;

    private final HelperChickenEntity chicken;
    private final Supplier<BlockPos> flagPosSupplier;

    private BlockPos targetPos;
    private int ticksSincePathProgress;
    private int peckCountdown;
    private boolean spawned;

    public HelperChickenSunsetChestGoal(
            HelperChickenEntity chicken,
            Supplier<BlockPos> flagPosSupplier
    ) {
        this.chicken = chicken;
        this.flagPosSupplier = flagPosSupplier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return false;
        }
        if (flag.getChickenBeatState() != ChickenBeatState.WAITING_FOR_CHEST) {
            return false;
        }
        if (flag.getChickenSunsetChestSpawned()) {
            return false;
        }
        Player player = ChickenArcConditions.findNearestPlayerForFlag(flag);
        if (player == null) {
            logCanUseFailure("no nearby player");
            return false;
        }
        BlockPos picked = pickStoreShellChestPos(flag);
        if (picked == null) {
            logCanUseFailure("no passable cell inside the registered store shell");
            return false;
        }
        this.targetPos = picked;
        return true;
    }

    private static long lastCanUseFailureLogTick = -1L;

    private void logCanUseFailure(String reason) {
        if (!(this.chicken.level() instanceof ServerLevel sl)) {
            return;
        }
        long tick = sl.getGameTime();
        if (tick - lastCanUseFailureLogTick < 100L) {
            return;
        }
        lastCanUseFailureLogTick = tick;
        QT.JOB_LOGGER.dev("[chicken-arc] storeroom chest goal stood down: {}", reason);
    }

    @Override
    public boolean canContinueToUse() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return false;
        }
        if (flag.getChickenBeatState() != ChickenBeatState.WAITING_FOR_CHEST) {
            return false;
        }
        if (flag.getChickenSunsetChestSpawned()) {
            return false;
        }
        return this.targetPos != null && !this.spawned;
    }

    @Override
    public void start() {
        this.ticksSincePathProgress = 0;
        this.peckCountdown = 0;
        this.spawned = false;
        if (this.targetPos != null) {
            PathNavigation nav = this.chicken.getNavigation();
            Path path = nav.createPath(this.targetPos, 1);
            if (path != null) {
                nav.moveTo(path, SPEED_MODIFIER);
            }
        }
    }

    @Override
    public void stop() {
        this.chicken.getNavigation().stop();
        this.targetPos = null;
        this.ticksSincePathProgress = 0;
        this.peckCountdown = 0;
        this.spawned = false;
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }
        this.chicken.getLookControl().setLookAt(
                this.targetPos.getX() + 0.5D,
                this.targetPos.getY() + 0.5D,
                this.targetPos.getZ() + 0.5D
        );

        double distSqr = this.chicken.distanceToSqr(
                this.targetPos.getX() + 0.5D,
                this.targetPos.getY() + 0.5D,
                this.targetPos.getZ() + 0.5D
        );

        if (hasArrived(distSqr, this.chicken.getNavigation().isDone())) {
            // Drop the chest only with the player in the room.
            if (playerInStoreShell()) {
                peckAndSpawn();
            }
            return;
        }

        tickPathing();
    }

    /** Close enough to peck: within {@link #ARRIVAL_DISTANCE_SQR}, or within {@link #NAV_SETTLE_ARRIVAL_SQR} once the path is done. */
    static boolean hasArrived(double distSqr, boolean navDone) {
        if (distSqr <= ARRIVAL_DISTANCE_SQR) {
            return true;
        }
        return navDone && distSqr <= NAV_SETTLE_ARRIVAL_SQR;
    }

    private void tickPathing() {
        PathNavigation nav = this.chicken.getNavigation();
        if (nav.isDone() || nav.getPath() == null) {
            Path path = nav.createPath(this.targetPos, 1);
            if (path != null) {
                nav.moveTo(path, SPEED_MODIFIER);
            }
        }
        this.ticksSincePathProgress++;
        if (this.ticksSincePathProgress >= STUCK_TELEPORT_TICKS) {
            teleportToTargetWithPoof();
            this.ticksSincePathProgress = 0;
        }
    }

    private void peckAndSpawn() {
        this.chicken.getNavigation().stop();
        if (this.peckCountdown == 0) {
            this.peckCountdown = PECK_TICKS;
            this.chicken.swing(InteractionHand.MAIN_HAND);
            return;
        }
        this.peckCountdown--;
        if (this.peckCountdown == 0) {
            spawnChest();
        }
    }

    private void spawnChest() {
        if (this.spawned) {
            return;
        }
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return;
        }
        if (!(this.chicken.level() instanceof ServerLevel sl)) {
            return;
        }
        BlockPos chestPos = findChestPlacementPos(sl);
        if (chestPos == null) {
            // Couldn't place it: count it as done so the lesson doesn't stall.
            this.spawned = true;
            persistChestSpawned(flag);
            QT.JOB_LOGGER.dev(
                    "[chicken-arc] chest: no valid chest position in {}, advancing without chest",
                    this.targetPos
            );
            return;
        }
        sl.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (sl.getBlockEntity(chestPos) instanceof ChestBlockEntity chestBe) {
            // No map: the player already got one when they placed the flag.
            chestBe.setItem(0, new ItemStack(Items.WOODEN_AXE));
        }
        sl.sendParticles(
                ParticleTypes.POOF,
                chestPos.getX() + 0.5D, chestPos.getY() + 0.5D, chestPos.getZ() + 0.5D,
                10, 0.3D, 0.3D, 0.3D, 0.02D
        );
        this.spawned = true;
        persistChestSpawned(flag);
        QT.JOB_LOGGER.dev(
                "[chicken-arc] chest: spawned map+axe chest at {}", chestPos
        );
    }

    private void persistChestSpawned(TownFlagBlockEntity flag) {
        flag.setChickenSunsetChestSpawned(true);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
    }

    /** The chest's cell: the open cell inside the storeroom farthest from the door, or null. */
    private BlockPos pickStoreShellChestPos(TownFlagBlockEntity flag) {
        if (!(this.chicken.level() instanceof ServerLevel sl)) {
            return null;
        }
        Collection<MCRoom> rooms = flag.getRoomHandle().getRegisteredRooms().getAllRooms();
        if (rooms.isEmpty()) {
            QT.JOB_LOGGER.dev("[chicken-arc] storeroom chest: no registered rooms");
            return null;
        }
        MCRoom shell = largestRoom(rooms);
        int chickenY = this.chicken.blockPosition().getY();
        // Try the room's floor levels first, then the chicken's own.
        int[] levels = { shell.yCoord, shell.yCoord + 1, chickenY };
        QT.JOB_LOGGER.dev(
                "[chicken-arc] storeroom chest: shell yCoord={} spaces={} chickenY={}",
                shell.yCoord, shell.getSpaces().size(), chickenY
        );
        for (Position p : chestCandidates(shell.getSpaces(), shell.getDoorPos())) {
            for (int y : levels) {
                BlockPos c = new BlockPos(p.x, y, p.z);
                if (isPassableForChest(sl, c)) {
                    return c;
                }
            }
        }
        QT.JOB_LOGGER.dev(
                "[chicken-arc] storeroom chest: no passable cell for shell yCoord={} spaces={}",
                shell.yCoord, shell.getSpaces().size()
        );
        return null;
    }

    /** The room's inside cells, farthest from the door first, so the chest doesn't block the doorway. */
    static List<Position> chestCandidates(Collection<InclusiveSpace> spaces, Position door) {
        List<Position> cells = new ArrayList<>();
        for (InclusiveSpace space : spaces) {
            cells.addAll(InclusiveSpaces.getPositions(space, InclusiveSpaces.PositionType.INTERIOR_ONLY));
        }
        cells.sort(Comparator.comparingInt((Position p) -> distSq(p, door)).reversed()
                             .thenComparing(Comparator.naturalOrder()));
        return cells;
    }

    private static int distSq(Position a, Position b) {
        int dx = a.x - b.x;
        int dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static MCRoom largestRoom(Collection<MCRoom> rooms) {
        MCRoom best = null;
        double bestArea = -1;
        for (MCRoom r : rooms) {
            double area = InclusiveSpaces.calculateArea(r.getSpaces());
            if (area > bestArea) {
                bestArea = area;
                best = r;
            }
        }
        return best;
    }

    /** Whether the nearest player is inside a registered room. */
    private boolean playerInStoreShell() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return false;
        }
        Player player = ChickenArcConditions.findNearestPlayerForFlag(flag);
        if (player == null) {
            return false;
        }
        Collection<MCRoom> rooms = flag.getRoomHandle().getRegisteredRooms().getAllRooms();
        for (MCRoom r : rooms) {
            Position pp = new Position(
                    player.blockPosition().getX(), player.blockPosition().getZ()
            );
            if (InclusiveSpaces.contains(r.getSpaces(), pp)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a newly placed block would replace this one (snow, grass, fluid). */
    static boolean isReplaceable(net.minecraft.world.level.block.state.BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean isPassableForChest(ServerLevel sl, BlockPos c) {
        // Snow, grass and fluids count as empty.
        return isReplaceable(sl.getBlockState(c)) && isReplaceable(sl.getBlockState(c.above()));
    }

    /** A spot at or next to the target where a chest fits. */
    private BlockPos findChestPlacementPos(ServerLevel sl) {
        if (isReplaceable(sl.getBlockState(this.targetPos))) {
            return this.targetPos;
        }
        BlockPos[] candidates = new BlockPos[]{
                this.targetPos.north(),
                this.targetPos.east(),
                this.targetPos.south(),
                this.targetPos.west()
        };
        for (BlockPos c : candidates) {
            if (isReplaceable(sl.getBlockState(c))) {
                return c;
            }
        }
        return null;
    }

    private void teleportToTargetWithPoof() {
        if (!(this.chicken.level() instanceof ServerLevel sl)) {
            return;
        }
        double tx = this.targetPos.getX() + 0.5D;
        double ty = this.targetPos.getY();
        double tz = this.targetPos.getZ() + 0.5D;
        sl.sendParticles(ParticleTypes.POOF, tx, ty + 0.5D, tz, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        this.chicken.teleportTo(tx, ty, tz);
        sl.sendParticles(ParticleTypes.POOF, tx, ty + 0.5D, tz, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        this.chicken.getNavigation().stop();
    }

    /** The farthest open cell around a position, at the chicken's height, else the chicken's own cell if open. */
    static Optional<BlockPos> pickChestPos(
            BlockPos base,
            BlockPos chickenAt,
            int maxDistance,
            Predicate<BlockPos> isPassable
    ) {
        int groundY = chickenAt.getY();
        for (int distance = maxDistance; distance >= 1; distance--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos c = new BlockPos(
                            base.getX() + dx * distance,
                            groundY,
                            base.getZ() + dz * distance
                    );
                    if (isPassable.test(c)) {
                        return Optional.of(c);
                    }
                }
            }
        }
        if (isPassable.test(chickenAt)) {
            return Optional.of(chickenAt);
        }
        return Optional.empty();
    }

    @org.jetbrains.annotations.Nullable
    private TownFlagBlockEntity resolveFlag() {
        BlockPos flagPos = flagPosSupplier.get();
        if (flagPos == null) {
            return null;
        }
        if (this.chicken.level().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag) {
            return flag;
        }
        return null;
    }
}
