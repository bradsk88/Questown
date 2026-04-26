package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.QT;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
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

import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * Phase-1 goal for the {@link ChickenBeatState#SUNSET_AND_MAP} beat.
 *
 * <p>Walks the chicken a few blocks away from the nearest player, pecks the
 * ground, then spawns a chest containing an empty map and a wooden axe. After
 * the chest is placed the persisted {@code chickenSunsetChestSpawned} bit on
 * the flag flips to true and this goal stops self-selecting — the bubble
 * switches to the sunset-texture phase-2 hint, the existing
 * {@code HelperChickenBeatPeckGoal} stays inactive (SUNSET_AND_MAP has no
 * structure-local target), and we wait for the player to sleep so the
 * controller advances the beat.
 */
public class HelperChickenSunsetChestGoal extends Goal {

    private static final double ARRIVAL_DISTANCE_SQR = 2.0D;
    private static final int STUCK_TELEPORT_TICKS = 600;
    private static final int PECK_TICKS = 20;
    private static final double SPEED_MODIFIER = 1.0D;

    /** Target offset distance from the player along a cardinal direction. */
    private static final int TARGET_DISTANCE_BLOCKS = 4;

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
        if (flag.getChickenBeatState() != ChickenBeatState.SUNSET_AND_MAP) {
            return false;
        }
        if (flag.getChickenSunsetChestSpawned()) {
            return false;
        }
        Player player = ChickenArcConditions.findNearestPlayerForFlag(flag);
        if (player == null) {
            return false;
        }
        BlockPos picked = pickChestPos(player);
        if (picked == null) {
            return false;
        }
        this.targetPos = picked;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return false;
        }
        if (flag.getChickenBeatState() != ChickenBeatState.SUNSET_AND_MAP) {
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

        if (distSqr <= ARRIVAL_DISTANCE_SQR) {
            peckAndSpawn();
            return;
        }

        tickPathing();
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
            // bubble still flips to phase 2 and the player can advance via sleep.
            this.spawned = true;
            persistChestSpawned(flag);
            QT.JOB_LOGGER.info(
                    "[chicken-arc] sunset: no valid chest position near {}, advancing without chest",
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
                "[chicken-arc] sunset: spawned map+axe chest at {}", chestPos
        );
    }

    private void persistChestSpawned(TownFlagBlockEntity flag) {
        flag.setChickenSunsetChestSpawned(true);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
    }

    /**
     * Pick a target position for the peck — a cardinal-offset of the player at
     * the player's foot Y. Tries north/east/south/west in order; first one
     * passable and clear above wins. Returns null if all four candidates fail.
     */
    private BlockPos pickChestPos(Player player) {
        if (!(this.chicken.level instanceof ServerLevel sl)) {
            return null;
        }
        BlockPos base = player.blockPosition();
        BlockPos[] candidates = new BlockPos[]{
                base.north(TARGET_DISTANCE_BLOCKS),
                base.east(TARGET_DISTANCE_BLOCKS),
                base.south(TARGET_DISTANCE_BLOCKS),
                base.west(TARGET_DISTANCE_BLOCKS)
        };
        for (BlockPos c : candidates) {
            if (sl.getBlockState(c).isAir() && sl.getBlockState(c.above()).isAir()) {
                return c;
            }
        }
        return null;
    }

    /**
     * Find a position adjacent to {@code targetPos} where a chest can sit:
     * an air block with a solid floor below. Falls back to {@code targetPos}
     * itself if it's clear.
     */
    private BlockPos findChestPlacementPos(ServerLevel sl) {
        if (sl.getBlockState(this.targetPos).isAir()) {
            return this.targetPos;
        }
        BlockPos[] candidates = new BlockPos[]{
                this.targetPos.north(),
                this.targetPos.east(),
                this.targetPos.south(),
                this.targetPos.west()
        };
        for (BlockPos c : candidates) {
            if (sl.getBlockState(c).isAir()) {
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
