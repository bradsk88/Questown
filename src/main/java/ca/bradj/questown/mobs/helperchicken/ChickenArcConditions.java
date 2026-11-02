package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.logic.TownCycle;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.gatherer.GathererUnmappedNoToolWorkQtrDay;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** Reads the world for the helper chicken's lessons. None of these change anything. */
public final class ChickenArcConditions {

    private static final double PLAYER_SEARCH_RADIUS = 16.0D;

    private ChickenArcConditions() {
    }

    /** Everything the lessons look at, in one snapshot. Also marks the first Worldly Seeds gather. */
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
                isCampfireLit(level, flagPos),
                flag.getChickenObservedSleepSinceSunset(),
                isWallBlockPlaced(level, flagPos, rotation),
                isDoorPlaced(level, flagPos, rotation),
                isRoomRegistered(flag),
                isRoomBuilt(flag),
                isSignConvertedToJobBoard(flag),
                isChestPlaced(flag),
                isWelcomeMatPlaced(flag),
                flag.getGivenBonusFood(),
                flag.getChickenObservedVillagerUiOpen(),
                flag.getChickenObservedFlagUiOpen(),
                seedsInContainer,
                flag.getChickenObservedSeedsGiven(),
                rotation == Rotation.NONE
        );
    }

    private static ChickenArcTransitions.Observed emptyObservation() {
        return new ChickenArcTransitions.Observed(
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false
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

    /** Whether a lit campfire is within the flag's campfire range, as the campfire quest checks it. */
    public static boolean isCampfireLit(
            ServerLevel level,
            BlockPos flagPos
    ) {
        return TownCycle.findCampfire(
                flagPos, level,
                bs -> bs.hasProperty(CampfireBlock.LIT) && bs.getValue(CampfireBlock.LIT)
        ).isPresent();
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
        // A door registered with the wand is enough; the room isn't furnished yet.
        if (!flag.getRoomHandle().getMatches(x -> true).isEmpty()) {
            return true;
        }
        return !flag.getRoomHandle().getAllRegisteredDoors().isEmpty();
    }

    public static boolean doorHasRoom(TownFlagBlockEntity flag) {
        Collection<MCRoom> rooms = flag.getRoomHandle().getRegisteredRooms().getAllRooms();
        return !rooms.isEmpty();
    }

    /** Whether a room exists around a registered door, built by the chicken or by the player. */
    public static boolean isRoomBuilt(TownFlagBlockEntity flag) {
        return doorHasRoom(flag);
    }

    public static boolean isSignConvertedToJobBoard(TownFlagBlockEntity flag) {
        // A placed job board registers with the flag; check that, since the board is its own room.
        if (flag.getWorkHandle().hasAtLeastOneBoard()) {
            return true;
        }
        return anyMatchedRoomContains(flag,
                b -> b == BlocksInit.JOB_BOARD_BLOCK.get());
    }

    public static boolean isChestPlaced(TownFlagBlockEntity flag) {
        return anyMatchedRoomContains(flag,
                b -> b instanceof net.minecraft.world.level.block.ChestBlock);
    }

    /** Whether any matched room holds a block that passes the predicate. */
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

    /** Whether the gatherer has gone out. False when no gatherer is loaded. */
    public static boolean isGathererLeft(TownFlagBlockEntity flag) {
        for (LivingEntity e : flag.getVillagerHandle().entities()) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            if (!GathererUnmappedNoToolWorkQtrDay.ID.rootId().equals(vme.getJobId().rootId())) {
                continue;
            }
            if (vme.getStatusForServer() instanceof IProductionStatus<?> ps) {
                return ps.isBusy();
            }
        }
        return false;
    }

    /** Marks the first Worldly Seeds gather once the seeds reach a container. */
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

    /** Whether the chicken pecks at its target: always for lessons that need no item, otherwise while the player holds the item. The pressure-plate lesson accepts a plate or a mat. */
    public static boolean shouldPeckRun(@Nullable Player player, ChickenBeatState state) {
        if (state == ChickenBeatState.WAITING_FOR_ROOM_BUILD) {
            // The room-build goal does this lesson's work.
            return false;
        }
        if (state == ChickenBeatState.WAITING_FOR_PRESSURE_PLATE) {
            return playerHoldsPressurePlate(player) || playerHoldsRequiredItem(player, state);
        }
        Predicate<ItemStack> required = requiredItemPredicate(state);
        if (required == null) {
            return true;
        }
        return playerHoldsMatching(player, required);
    }

    /** Whether the player holds the item the bubble asks for. */
    public static boolean playerHoldsRequiredItem(@Nullable Player player, ChickenBeatState state) {
        Predicate<ItemStack> required = requiredItemPredicate(state);
        if (required == null) {
            return false;
        }
        return playerHoldsMatching(player, required);
    }

    /** Whether the player holds a wooden pressure plate, the item the flag turns into a welcome mat. */
    public static boolean playerHoldsPressurePlate(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        return Ingredient.of(ItemTags.WOODEN_PRESSURE_PLATES).test(player.getMainHandItem())
                || Ingredient.of(ItemTags.WOODEN_PRESSURE_PLATES).test(player.getOffhandItem());
    }

    private static boolean playerHoldsMatching(@Nullable Player player, Predicate<ItemStack> match) {
        if (player == null) {
            return false;
        }
        return match.test(player.getMainHandItem()) || match.test(player.getOffhandItem());
    }

    /** The items the player must hold for the chicken to peck, or null when the lesson needs none. Any variant counts (any door, any sign). */
    @Nullable
    private static Predicate<ItemStack> requiredItemPredicate(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK -> s -> s.is(Items.STICK);
            case WAITING_FOR_WAND_ON_CAMPFIRE, WAITING_FOR_WAND_ON_DOOR, SUNSET_AND_MAP ->
                    s -> s.is(ItemsInit.TOWN_WAND.get());
            case WAITING_FOR_WALL_BLOCK -> ChickenArcConditions::isSolidBlockItem;
            case WAITING_FOR_DOOR -> s -> blockItemBlock(s) instanceof DoorBlock;
            case WAITING_FOR_SIGN -> null;
            case WAITING_FOR_CHEST -> s -> s.is(Items.CHEST);
            case WAITING_FOR_ROOM_BUILD -> null;
            case WAITING_FOR_PRESSURE_PLATE -> s -> s.is(ItemsInit.WELCOME_MAT_BLOCK.get());
            case WAITING_FOR_FREE_FOOD,
                 WAITING_FOR_VILLAGER_UI, WAITING_FOR_FLAG_UI,
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
