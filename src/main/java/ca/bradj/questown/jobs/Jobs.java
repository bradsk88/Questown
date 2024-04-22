package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TakeFn;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Jobs {
    public static ImmutableList<ItemStack> getItems(Job<MCHeldItem, ?, ?> job) {
        ImmutableList.Builder<ItemStack> b = ImmutableList.builder();
        Container inventory = job.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            b.add(inventory.getItem(i));
        }
        return b.build();
    }

    public static boolean isUnchanged(
            Container container,
            ImmutableList<MCHeldItem> items
    ) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).is(items.get(i).get().get())) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCloseTo(
            @NotNull BlockPos entityPos,
            @NotNull BlockPos targetPos
    ) {
        double d = targetPos.distToCenterSqr(entityPos.getX(), entityPos.getY(), entityPos.getZ());
        return d < 5;
    }

    public static boolean isVeryCloseTo(
            Vec3 entityPos,
            @NotNull BlockPos targetPos
    ) {
        double d = targetPos.distToCenterSqr(entityPos.x, entityPos.y, entityPos.z);
        return d < 0.5;
    }

    public static void handleItemChanges(
            Container inventory,
            ImmutableList<MCHeldItem> items
    ) {
        if (Jobs.isUnchanged(inventory, items)) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            net.minecraft.world.item.Item curItem = inventory.getItem(i).getItem();
            net.minecraft.world.item.Item newItem = items.get(i).get().get();
            if (newItem.equals(curItem)) {
                continue;
            }
            inventory.setItem(i, items.get(i).get().toItemStack());
        }
    }

    public static <H extends HeldItem<H, ?>> boolean addItemIfSlotAvailable(
            JournalItemList<H> inventory,
            DefaultInventoryStateProvider<H> invState,
            H item
    ) {
        if (invState.inventoryIsFull()) {
            return false;
        }
        H emptySlot = inventory.stream().filter(Item::isEmpty).findFirst().get();
        inventory.set(inventory.indexOf(emptySlot), item);
        return true;
    }

    public static @Nullable ContainerTarget<MCContainer, MCTownItem> setupForDropLoot(
            TownInterface town,
            ContainerTarget<MCContainer, MCTownItem> currentTarget
    ) {
        if (currentTarget != null) {
            if (!currentTarget.hasItem(MCTownItem::isEmpty)) {
                currentTarget = town.findMatchingContainer(MCTownItem::isEmpty);
            }
        } else {
            currentTarget = town.findMatchingContainer(MCTownItem::isEmpty);
        }
        if (currentTarget != null) {
            return currentTarget;
        } else {
            return null;
        }
    }

    public static boolean openInventoryAndStatusScreen(
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        e.getTown().getVillagerHandle().showUI(sp, OpenVillagerMenuMessage.INVENTORY, e.getUUID());
        return true; // Different jobs might have screens or not
    }

    public static void getOrCreateItemFromBlock(
            ServerLevel level,
            BlockPos b,
            TakeFn takeFn,
            MCHeldItem is,
            boolean nullifyExcess
    ) {
        for (int i = 0; i < Config.BASE_MAX_LOOP.get(); i++) {
            if (is.isEmpty()) {
                break;
            }
            MCHeldItem oneOf = is.unit();
            if (takeFn == null || !takeFn.Take(oneOf)) {
                if (nullifyExcess) {
                    return;
                }
                level.addFreshEntity(new ItemEntity(level, b.getX(), b.getY(), b.getZ(), oneOf.toItem().toItemStack()));
            }
            is = is.shrink();
        }
    }

    public static boolean townHasSpace(TownInterface town) {
        return town.findMatchingContainer(MCTownItem::isEmpty) != null;
    }

    public static RoomRecipeMatch<MCRoom> getEntityCurrentJobSite(
            TownInterface town,
            ResourceLocation id,
            BlockPos entityBlockPos
    ) {
        // TODO: Support multiple tiers of job site (i.e. more than one resource location)
        return town.getRoomHandle().getRoomsMatching(id).stream()
                   .filter(v -> v.room.yCoord > entityBlockPos.getY() - 5)
                   .filter(v -> v.room.yCoord < entityBlockPos.getY() + 5)
                   .filter(v -> {
                       boolean isInRoom = InclusiveSpaces.contains(
                               v.room.getSpaces(),
                               Positions.FromBlockPos(entityBlockPos)
                       ) || (
                               InclusiveSpaces.calculateArea(v.room.getSpaces()) == 9 &&
                                       v.room.getDoorPos().equals(Positions.FromBlockPos(entityBlockPos))
                       );
                       return isInRoom;
                   })
                   .findFirst()
                   .orElse(null);
    }

    public static boolean hasNonSupplyItems(
            ItemsHolder<MCHeldItem> journal,
            ImmutableList<JobsClean.TestFn<MCTownItem>> recipe
    ) {
        return journal.getItems().stream()
                      .filter(Predicates.not(Item::isEmpty))
                      .anyMatch(Predicates.not(v -> recipe.stream().anyMatch(z -> z.test(v.get()))));
    }

    public static boolean isUnfinishedTimeWorkPresent(
            RoomsHolder town,
            ResourceLocation workRoomId,
            Function<BlockPos, @Nullable Integer> ticksSource
    ) {
        Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomsMatching(workRoomId);
        return rooms.stream()
                    .anyMatch(v -> {
                        for (Map.Entry<BlockPos, Block> e : v.getContainedBlocks().entrySet()) {
                            @Nullable Integer apply = ticksSource.apply(e.getKey());
                            if (apply != null && apply > 0) {
                                return true;
                            }
                        }
                        return false;
                    });
    }

    public static Collection<Integer> getStatesWithUnfinishedWork(
            Supplier<Collection<? extends Supplier<Collection<BlockPos>>>> town,
            Function<BlockPos, AbstractWorkStatusStore.State> ticksSource,
            Predicate<BlockPos> canClaim
    ) {
        Collection<? extends Supplier<Collection<BlockPos>>> rooms = town.get();
        HashSet<Integer> b = new HashSet<>();
        rooms.forEach(v -> {
            for (BlockPos e : v.get()) {
                if (!canClaim.test(e)) {
                    continue;
                }
                @Nullable AbstractWorkStatusStore.State apply = ticksSource.apply(e);
                if (apply != null && apply.workLeft() > 0) {
                    b.add(apply.processingState());
                    return;
                }
            }
        });
        ArrayList<Integer> b2 = new ArrayList<>(b);
        Collections.sort(b2);
        return ImmutableList.copyOf(b2);
    }

    public static ImmutableMap<Integer, Function<MCTownItem, Boolean>> unMC(
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<Integer, Function<MCTownItem, Boolean>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach(
                (k, v) -> b.put(k, (MCTownItem item) -> v.test(item.toItemStack()))
        );
        return b.build();
    }

    public static <K> ImmutableMap<K, Predicate<MCTownItem>> unMC2(
            ImmutableMap<K, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<K, Predicate<MCTownItem>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach(
                (k, v) -> b.put(k, (MCTownItem item) -> v.test(item.toItemStack()))
        );
        return b.build();
    }

    public static <K> ImmutableMap<K, PredicateCollection<MCTownItem>> unMC3(
            ImmutableMap<K, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<K, PredicateCollection<MCTownItem>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach(
                (k, v) -> b.put(k, new PredicateCollection<>() {
                            @Override
                            public boolean test(MCTownItem mcTownItem) {
                                return v.test(mcTownItem.toItemStack());
                            }

                            @Override
                            public boolean isEmpty() {
                                return v.isEmpty();
                            }
                        }
                )
        );
        return b.build();
    }

    public static <K> ImmutableMap<K, Function<MCHeldItem, Boolean>> unMCHeld(
            ImmutableMap<K, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<K, Function<MCHeldItem, Boolean>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach(
                (k, v) -> b.put(k, (MCHeldItem item) -> v.test(item.get().toItemStack()))
        );
        return b.build();
    }

    public static <K> ImmutableMap<K, Predicate<MCHeldItem>> unMCHeld2(
            ImmutableMap<K, Ingredient> input
    ) {
        return unFn(unMCHeld(input));
    }

    public static <K> ImmutableMap<K, Predicate<MCHeldItem>> unFn(
            Map<K, Function<MCHeldItem, Boolean>> input
    ) {
        ImmutableMap.Builder<K, Predicate<MCHeldItem>> b = ImmutableMap.builder();
        input.forEach((k, v) -> b.put(k, v::apply));
        return b.build();
    }

    public static ImmutableMap<Integer, Predicate<MCTownItem>> unFn2(
            Map<Integer, Function<MCTownItem, Boolean>> input
    ) {
        ImmutableMap.Builder<Integer, Predicate<MCTownItem>> b = ImmutableMap.builder();
        input.forEach((k, v) -> b.put(k, v::apply));
        return b.build();
    }

    public static ImmutableMap<Integer, Predicate<MCHeldItem>> unHeld(
            ImmutableMap<Integer, Function<MCTownItem, Boolean>> input
    ) {
        ImmutableMap.Builder<Integer, Predicate<MCHeldItem>> b = ImmutableMap.builder();
        input.forEach((k, v) -> b.put(k, z -> v.apply(z.get())));
        return b.build();
    }

    public static ImmutableMap<Integer, Predicate<MCHeldItem>> unFn3(ImmutableMap<Integer, Function<MCTownItem, Boolean>> input) {
        ImmutableMap.Builder<Integer, Predicate<MCHeldItem>> b = ImmutableMap.builder();
        input.forEach((k, v) -> b.put(k, z -> v.apply(z.get())));
        return b.build();
    }

    public interface StateCheck {
        boolean Check(
                ServerLevel sl,
                BlockPos bp
        );
    }

    public static Collection<RoomRecipeMatch<MCRoom>> roomsWithState(
            TownInterface town,
            ResourceLocation roomType,
            StateCheck check
    ) {
        Collection<RoomWithBlocks<MCRoom, BlockPos, Block>> roomWithBlocks = JobsClean.roomsWithState(
                town.getRoomHandle().getRoomsMatching(roomType),
                pos -> check.Check(town.getServerLevel(), pos)
        );
        return roomWithBlocks.stream()
                             .map(v -> new RoomRecipeMatch<>(v.room, roomType, v.containedBlocks.entrySet()))
                             .toList();
    }

    public interface LootDropper<I> {

        UUID UUID();

        boolean hasAnyLootToDrop();

        Iterable<I> getItems();

        boolean removeItem(I mct);

    }

    public static boolean tryDropLoot(
            LootDropper<MCHeldItem> dropper,
            BlockPos entityPos,
            ContainerTarget<? extends ContainerTarget.Container<MCTownItem>, MCTownItem> target
    ) {
        UUID ownerUUID = dropper.UUID();
        if (!dropper.hasAnyLootToDrop()) {
            QT.JOB_LOGGER.trace("{} is not dropping because they only have food", ownerUUID);
            return false;
        }


        BlockPos targetPos = Positions.ToBlock(target.getPosition(), target.getYPosition());
        boolean farFromChest = !isCloseTo(entityPos, targetPos);
        List<MCHeldItem> snapshot = Lists.reverse(ImmutableList.copyOf(dropper.getItems()));
        for (MCHeldItem mct : snapshot) {
            if (mct.isEmpty()) {
                continue;
            }
            // TODO: Unit tests of this logic!
            if (mct.isLocked()) {
                QT.JOB_LOGGER.trace("Villager is not putting away {} because it is locked", mct.toShortString());
                continue;
            }

            if (ItemsInit.KNOWLEDGE.get().equals(mct.get().get())) {
                dropper.removeItem(mct);
                continue;
            }

            if (farFromChest) {
                continue;
            }

            QT.JOB_LOGGER.debug(
                    "Gatherer {} is putting {} in {}",
                    ownerUUID,
                    mct.toShortString(),
                    targetPos
            );
            boolean added = false;
            for (int i = 0; i < target.size(); i++) {
                if (added) {
                    break;
                }
                if (target.getItem(i).isEmpty()) {
                    if (dropper.removeItem(mct)) {
                        target.setItem(i, mct.toItem());
                    }
                    added = true;
                }
            }
            if (!added) {
                QT.JOB_LOGGER.debug("Nope. No space for {}", mct.toShortString());
            }
        }
        return true;
    }
}
