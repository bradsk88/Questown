package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Pure-function observers that read world state for the helper-chicken arc (U4).
 *
 * <p>Each {@code isX(flag)} method samples one aspect of the world — a player
 * inventory, a block state, a town container — and returns a boolean. The
 * controller assembles these into a {@link ChickenArcTransitions.Observed}
 * snapshot before calling {@link ChickenArcTransitions#advance}.
 *
 * <p>None of these methods mutate state. They are safe to call at any time.
 * Most take only the flag BE and derive everything else from it (level,
 * rotation, nearest player). UI-open observations are stubbed out in v1 (U4);
 * U6 will fill them in by hooking the UI open events.
 */
public final class ChickenArcConditions {

    /** Radius within which "nearest player" is searched for inventory checks. */
    private static final double PLAYER_SEARCH_RADIUS = 16.0D;

    private ChickenArcConditions() {
    }

    /**
     * Builds the complete {@link ChickenArcTransitions.Observed} snapshot.
     *
     * <p>UI-opened and seeds-given flags come from ephemeral observation state
     * on the flag BE. The Worldly-Seeds-in-container observation additionally
     * flips the {@code first-gather-worldly-seeds-fired} bit at its rising
     * edge — this is U4's responsibility per the plan (the U7 loot wrappers
     * only read the bit; they don't write it).
     */
    public static ChickenArcTransitions.Observed observe(TownFlagBlockEntity flag) {
        ServerLevel level = flag.getServerLevel();
        if (level == null) {
            return emptyObservation();
        }
        BlockPos flagPos = flag.getTownFlagBasePos();
        Rotation rotation = flag.getChickenStructureRotation();
        Player nearestPlayer = findNearestPlayer(level, flagPos);

        boolean seedsInContainer = areWorldlySeedsInAnyContainer(flag);
        maybeFlipFirstGatherBit(flag, seedsInContainer);

        return new ChickenArcTransitions.Observed(
                hasWandInInventory(nearestPlayer),
                isCampfireLit(level, flagPos, rotation),
                flag.getChickenObservedSleepSinceSunset(),
                isWallBlockPlaced(level, flagPos, rotation),
                isDoorPlaced(level, flagPos, rotation),
                isRoomRegistered(flag),
                isSignConvertedToJobBoard(level, flagPos, rotation),
                isChestPlaced(level, flagPos, rotation),
                isWelcomeMatPlaced(flag),
                flag.getChickenObservedVillagerUiOpen(),
                flag.getChickenObservedFlagUiOpen(),
                seedsInContainer,
                flag.getChickenObservedSeedsGiven()
        );
    }

    private static ChickenArcTransitions.Observed emptyObservation() {
        return new ChickenArcTransitions.Observed(
                false, false, false, false, false, false, false, false, false,
                false, false, false, false
        );
    }

    public static boolean hasWandInInventory(Player player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ItemsInit.TOWN_WAND.get())) {
                return true;
            }
        }
        return player.getOffhandItem().is(ItemsInit.TOWN_WAND.get());
    }

    public static boolean isCampfireLit(
            ServerLevel level,
            BlockPos flagPos,
            Rotation rotation
    ) {
        BlockPos pos = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE, flagPos, rotation
        );
        if (pos == null) {
            return false;
        }
        BlockState bs = level.getBlockState(pos);
        if (!bs.is(Blocks.CAMPFIRE)) {
            return false;
        }
        return bs.hasProperty(CampfireBlock.LIT) && bs.getValue(CampfireBlock.LIT);
    }

    public static boolean isWallBlockPlaced(
            ServerLevel level,
            BlockPos flagPos,
            Rotation rotation
    ) {
        BlockPos pos = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_WALL_BLOCK, flagPos, rotation
        );
        if (pos == null) {
            return false;
        }
        return level.getBlockState(pos).isSolidRender(level, pos);
    }

    public static boolean isDoorPlaced(
            ServerLevel level,
            BlockPos flagPos,
            Rotation rotation
    ) {
        BlockPos pos = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_DOOR, flagPos, rotation
        );
        if (pos == null) {
            return false;
        }
        return level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
    }

    public static boolean isRoomRegistered(TownFlagBlockEntity flag) {
        return !flag.getRoomHandle().getMatches(x -> true).isEmpty();
    }

    public static boolean isSignConvertedToJobBoard(
            ServerLevel level,
            BlockPos flagPos,
            Rotation rotation
    ) {
        BlockPos pos = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_SIGN, flagPos, rotation
        );
        if (pos == null) {
            return false;
        }
        return level.getBlockState(pos).is(BlocksInit.JOB_BOARD_BLOCK.get());
    }

    public static boolean isChestPlaced(
            ServerLevel level,
            BlockPos flagPos,
            Rotation rotation
    ) {
        BlockPos pos = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_CHEST, flagPos, rotation
        );
        if (pos == null) {
            return false;
        }
        return level.getBlockEntity(pos, BlockEntityType.CHEST).isPresent();
    }

    public static boolean isWelcomeMatPlaced(TownFlagBlockEntity flag) {
        return !flag.getWelcomeMats().isEmpty();
    }

    public static boolean areWorldlySeedsInAnyContainer(TownFlagBlockEntity flag) {
        ServerLevel level = flag.getServerLevel();
        if (level == null) {
            return false;
        }
        List<ContainerTarget<MCContainer, MCTownItem>> all = TownContainers.getAllContainers(flag, level);
        for (ContainerTarget<MCContainer, MCTownItem> ct : all) {
            if (ct.hasItem(item -> item.get() == ItemsInit.WORLDLY_SEEDS.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flips {@code first-gather-worldly-seeds-fired} on the rising edge of
     * seeds-in-container. Idempotent: once the bit is set, nothing to do.
     *
     * <p>Per the plan, the bit's write lives here rather than in U7's loot
     * wrappers — the wrappers at {@code RealtimeWorldInteraction} and
     * {@code TimeWarpWorldInteraction} check the bit but never write it, so we
     * avoid a duplicate-write race across the two call sites.
     */
    private static void maybeFlipFirstGatherBit(
            TownFlagBlockEntity flag,
            boolean seedsInContainer
    ) {
        if (!seedsInContainer) {
            return;
        }
        if (flag.getChickenFirstGatherWorldlySeedsFired()) {
            return;
        }
        flag.setChickenFirstGatherWorldlySeedsFired(true);
        flag.writeTownData(ca.bradj.questown.mc.Compat.getBlockStoredTagData(flag));
        flag.setChanged();
    }

    private static Player findNearestPlayer(
            ServerLevel level,
            BlockPos flagPos
    ) {
        return level.getNearestPlayer(
                flagPos.getX() + 0.5D,
                flagPos.getY() + 0.5D,
                flagPos.getZ() + 0.5D,
                PLAYER_SEARCH_RADIUS,
                false
        );
    }
}
