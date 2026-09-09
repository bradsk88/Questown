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

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Storeroom-chest goal for the {@link ChickenBeatState#WAITING_FOR_CHEST} beat.
 *
 * <p>Walks the chicken into the registered store shell, waits for the player
 * to be in the room, then drops a chest holding a filled map and a wooden axe.
 * The chest is a contained block of the shell, so the shell matches the
 * {@code store_room} recipe and {@code isChestPlaced} goes true, completing
 * the beat. After the chest is dropped the persisted
 * {@code chickenSunsetChestSpawned} bit flips to true and this goal stops
 * self-selecting. The arrival / stuck-teleport logic is unchanged from the old
 * sunset chest goal; only the target (a cell inside the registered store shell
 * instead of a cell near the player), the "wait for the player to be in the
 * room" gate, and the beat have moved. The player watches a chest placed *in
 * a room* (the worked example), never a chest in the open.
 */
public class HelperChickenSunsetChestGoal extends Goal {

    private static final double ARRIVAL_DISTANCE_SQR = 2.0D;
    /**
     * Looser arrival radius applied once the navigation reports done. The path
     * follower (accuracy 1) stops roughly a block-and-a-half short of the
     * target's centre — measured ~2.4 sqr in the flat onboarding arena — which
     * is past {@link #ARRIVAL_DISTANCE_SQR}. Without this, the chicken re-paths
     * to the same unreachable centre every tick, never pecks, and the chest
     * never spawns. When the pathfinder has gotten as close as it can, that
     * counts as arrival; the cap still rejects a fully-failed path that left the
     * chicken far away (the stuck-teleport handles that case).
     */
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
        if (!(this.chicken.level instanceof ServerLevel sl)) {
            return;
        }
        long tick = sl.getGameTime();
        if (tick - lastCanUseFailureLogTick < 100L) {
            return;
        }
        lastCanUseFailureLogTick = tick;
        QT.JOB_LOGGER.info("[chicken-arc] storeroom chest goal stood down: {}", reason);
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
            // The chicken stands in the room and waits for the player to be in
            // it before dropping the chest, so the player watches a chest placed
            // *in a room* (the worked example), not a chest in the open.
            if (playerInStoreShell()) {
                peckAndSpawn();
            }
            return;
        }

        tickPathing();
    }

    /**
     * Whether the chicken is close enough to the chest target to peck. Within
     * {@link #ARRIVAL_DISTANCE_SQR} is an unconditional arrival. Otherwise, once
     * the navigation has finished (the pathfinder can get no closer) a looser
     * {@link #NAV_SETTLE_ARRIVAL_SQR} radius applies — the follower stops a
     * block-and-a-half short of the centre, which would otherwise leave the
     * chicken re-pathing forever and never spawning the chest. Pure so it can
     * be unit-tested without a live navigation.
     */
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
        if (!(this.chicken.level instanceof ServerLevel sl)) {
            return;
        }
        BlockPos chestPos = findChestPlacementPos(sl);
        if (chestPos == null) {
            // Couldn't place — mark spawned anyway so we don't loop forever; the
            // beat still completes via sleep/night and the player can advance.
            this.spawned = true;
            persistChestSpawned(flag);
            QT.JOB_LOGGER.info(
                    "[chicken-arc] chest: no valid chest position in {}, advancing without chest",
                    this.targetPos
            );
            return;
        }
        sl.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (sl.getBlockEntity(chestPos) instanceof ChestBlockEntity chestBe) {
            // Pre-fill the map for THIS area: the player may walk away before
            // ever right-clicking it, in which case an empty map would be
            // useless. A filled map already shows the village location.
            ItemStack filledMap = net.minecraft.world.item.MapItem.create(
                    sl,
                    chestPos.getX(),
                    chestPos.getZ(),
                    (byte) 1,
                    true,
                    false
            );
            net.minecraft.world.item.MapItem.renderBiomePreviewMap(sl, filledMap);
            net.minecraft.world.level.saveddata.maps.MapItemSavedData.addTargetDecoration(
                    filledMap,
                    flag.getTownFlagBasePos(),
                    "+",
                    net.minecraft.world.level.saveddata.maps.MapDecoration.Type.TARGET_X
            );
            chestBe.setItem(0, filledMap);
            chestBe.setItem(1, new ItemStack(Items.WOODEN_AXE));
        }
        sl.sendParticles(
                ParticleTypes.POOF,
                chestPos.getX() + 0.5D, chestPos.getY() + 0.5D, chestPos.getZ() + 0.5D,
                10, 0.3D, 0.3D, 0.3D, 0.02D
        );
        this.spawned = true;
        persistChestSpawned(flag);
        QT.JOB_LOGGER.info(
                "[chicken-arc] chest: spawned map+axe chest at {}", chestPos
        );
    }

    private void persistChestSpawned(TownFlagBlockEntity flag) {
        flag.setChickenSunsetChestSpawned(true);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
    }

    /**
     * Pick a target cell inside the registered store shell for the chest: the
     * first passable interior cell of the shell's largest space, at the
     * chicken's ground Y. The chicken walks there, waits for the player to be
     * in the room, then drops the chest on that cell (see {@link #tick}).
     * Falls back to {@code null} when no registered shell or no passable
     * interior cell exists, so the goal stands down instead of deadlocking the
     * beat.
     */
    private BlockPos pickStoreShellChestPos(TownFlagBlockEntity flag) {
        if (!(this.chicken.level instanceof ServerLevel sl)) {
            return null;
        }
        Collection<MCRoom> rooms = flag.getRoomHandle().getRegisteredRooms().getAllRooms();
        if (rooms.isEmpty()) {
            QT.JOB_LOGGER.info("[chicken-arc] storeroom chest: no registered rooms");
            return null;
        }
        MCRoom shell = largestRoom(rooms);
        int chickenY = this.chicken.blockPosition().getY();
        // The chest must sit on the room's own floor level (a contained block is
        // at yCoord or yCoord+1); the chicken's ground Y may not match it (the
        // chicken is standing near the player, not in the room), so try the
        // room's floor levels first, then the chicken's Y as a fallback.
        int[] levels = { shell.yCoord, shell.yCoord + 1, chickenY };
        QT.JOB_LOGGER.info(
                "[chicken-arc] storeroom chest: shell yCoord={} spaces={} chickenY={}",
                shell.yCoord, shell.getSpaces().size(), chickenY
        );
        for (InclusiveSpace space : shell.getSpaces()) {
            for (Position p : InclusiveSpaces.getPositions(
                    space, InclusiveSpaces.PositionType.INTERIOR_ONLY
            )) {
                for (int y : levels) {
                    BlockPos c = new BlockPos(p.x, y, p.z);
                    if (isPassableForChest(sl, c)) {
                        return c;
                    }
                }
            }
        }
        QT.JOB_LOGGER.info(
                "[chicken-arc] storeroom chest: no passable cell for shell yCoord={} spaces={}",
                shell.yCoord, shell.getSpaces().size()
        );
        return null;
    }

    /** The registered room with the largest footprint — the store shell. */
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

    /**
     * Whether the nearest player is standing inside any registered room's
     * interior. The chicken holds at the shell until the player enters it, so
     * the chest drop is witnessed from inside the room (the worked example).
     */
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

    /**
     * Whether a block state can be replaced by a freshly-placed block (chest,
     * snow layer, grass plant, fluid, etc.). Mirrors the vanilla
     * {@code Material.isReplaceable()} contract — exposed as a static helper
     * so unit tests can build {@code Blocks.X.defaultBlockState()} directly
     * without a {@code BlockGetter}.
     */
    static boolean isReplaceable(net.minecraft.world.level.block.state.BlockState state) {
        return state.isAir() || state.getMaterial().isReplaceable();
    }

    private static boolean isPassableForChest(ServerLevel sl, BlockPos c) {
        // Treat replaceable blocks (snow layers, grass plants, tall_grass,
        // fluids) the same as air — placing a chest into them overwrites them
        // the way a player's right-click would. The narrower air-only check
        // deadlocked the chest beat in snowy biomes.
        return isReplaceable(sl.getBlockState(c)) && isReplaceable(sl.getBlockState(c.above()));
    }

    /**
     * Find a position at or adjacent to {@code targetPos} where a chest can sit:
     * an air block with air above. Falls back to a cardinal neighbour if the
     * target itself is not clear.
     */
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
        if (!(this.chicken.level instanceof ServerLevel sl)) {
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

    /**
     * Pure search: walks outward from a base horizontal position in distance
     * order (farthest first), trying all 8 N/E/S/W + diagonals at each
     * distance. Candidates sit on {@code chickenAt}'s Y — the ground plane the
     * chicken can actually walk to and stand on. Returns the first passable
     * cell. If every candidate fails, falls back to {@code chickenAt} when
     * passable, else empty. Retained as a unit-tested pure helper.
     */
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
        if (this.chicken.level.getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag) {
            return flag;
        }
        return null;
    }
}
