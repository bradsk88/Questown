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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

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
                isSignConvertedToJobBoard(flag),
                isChestPlaced(flag),
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
        // WAITING_FOR_WAND_ON_DOOR advances on this condition. At that beat the
        // player has wand-clicked the door but hasn't yet placed a sign (→ job
        // board), chest, or any other recipe-matching block — so no entry
        // exists in getMatches() (which filters to recipe-matched rooms). The
        // semantic the beat machine wants is "a door has been wand-registered",
        // not "a furnished room exists". Checking registered doors avoids the
        // deadlock and lets later beats (WAITING_FOR_SIGN, WAITING_FOR_CHEST)
        // actually add recipe blocks inside the registered perimeter.
        if (!flag.getRoomHandle().getMatches(x -> true).isEmpty()) {
            return true;
        }
        return !flag.getRoomHandle().getAllRegisteredDoors().isEmpty();
    }

    public static boolean isSignConvertedToJobBoard(TownFlagBlockEntity flag) {
        return anyMatchedRoomContains(flag,
                b -> b == BlocksInit.JOB_BOARD_BLOCK.get());
    }

    public static boolean isChestPlaced(TownFlagBlockEntity flag) {
        return anyMatchedRoomContains(flag,
                b -> b instanceof net.minecraft.world.level.block.ChestBlock);
    }

    /**
     * Whether any room recipe currently matches a room that contains a block
     * satisfying the predicate. Delegating to the recipe system means the
     * beat advances regardless of which interior cell the player picked, as
     * long as the room is recipe-active and contains the required block.
     */
    private static boolean anyMatchedRoomContains(
            TownFlagBlockEntity flag,
            java.util.function.Predicate<net.minecraft.world.level.block.Block> predicate
    ) {
        var matches = flag.getRoomHandle().getMatches(m -> true);
        for (var match : matches) {
            for (var b : match.getContainedBlocks().values()) {
                if (predicate.test(b)) {
                    return true;
                }
            }
        }
        return false;
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

    public static Player findNearestPlayerForFlag(TownFlagBlockEntity flag) {
        ServerLevel level = flag.getServerLevel();
        if (level == null) {
            return null;
        }
        return findNearestPlayer(level, flag.getTownFlagBasePos());
    }

    /**
     * Whether the chicken's beat-peck goal should currently run. Returns true
     * for beats with no held-item requirement (UI beats, seeds-delivery) so the
     * chicken pecks the location regardless; for beats with a required item
     * (placement beats too — the chicken can't usefully indicate where to put
     * something the player isn't carrying) returns true only when the player
     * is holding it. When false, the lower-priority follow-near-flag goal
     * picks up and the chicken trails the player until they fetch the item.
     */
    public static boolean shouldPeckRun(@Nullable Player player, ChickenBeatState state) {
        Predicate<ItemStack> required = requiredItemPredicate(state);
        if (required == null) {
            return true;
        }
        return playerHoldsMatching(player, required);
    }

    /**
     * Whether the player holds the item the chicken's bubble is asking for, used
     * to flip the bubble between single-item (item only) and alternating
     * (item ↔ target block) modes. Returns false for beats with no held-item
     * requirement so those bubbles stay in their authored single/alternating shape.
     */
    public static boolean playerHoldsRequiredItem(@Nullable Player player, ChickenBeatState state) {
        Predicate<ItemStack> required = requiredItemPredicate(state);
        if (required == null) {
            return false;
        }
        return playerHoldsMatching(player, required);
    }

    private static boolean playerHoldsMatching(@Nullable Player player, Predicate<ItemStack> match) {
        if (player == null) {
            return false;
        }
        return match.test(player.getMainHandItem()) || match.test(player.getOffhandItem());
    }

    /**
     * Maps each beat to the predicate matching items the player must hold for
     * the chicken to peck the target location. Returns null when the beat has
     * no held-item requirement (UI beats, seeds delivery, terminals, sunset).
     *
     * <p>For "any X" beats (wall, door, sign) the predicate matches by Block
     * type so the player can use any vanilla variant — any solid block, any
     * door, any sign — not just the cobblestone/oak versions the bubble shows.
     */
    @Nullable
    private static Predicate<ItemStack> requiredItemPredicate(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK -> s -> s.is(Items.STICK);
            case WAITING_FOR_WAND_ON_CAMPFIRE, WAITING_FOR_WAND_ON_DOOR, SUNSET_AND_MAP ->
                    s -> s.is(ItemsInit.TOWN_WAND.get());
            case WAITING_FOR_WALL_BLOCK -> ChickenArcConditions::isSolidBlockItem;
            case WAITING_FOR_DOOR -> s -> blockItemBlock(s) instanceof DoorBlock;
            case WAITING_FOR_SIGN -> s -> blockItemBlock(s) instanceof SignBlock;
            case WAITING_FOR_CHEST -> s -> s.is(Items.CHEST);
            case WAITING_FOR_PRESSURE_PLATE -> s -> s.is(ItemsInit.WELCOME_MAT_BLOCK.get());
            case WAITING_FOR_VILLAGER_UI, WAITING_FOR_FLAG_UI,
                 AWAITING_WORLDLY_SEEDS_DELIVERY, COMPLETE, FORFEIT -> null;
        };
    }

    private static boolean isSolidBlockItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) {
            return false;
        }
        BlockState defaultState = bi.getBlock().defaultBlockState();
        return defaultState.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    @Nullable
    private static net.minecraft.world.level.block.Block blockItemBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
    }
}
