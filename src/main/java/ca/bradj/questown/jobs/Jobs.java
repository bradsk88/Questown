package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TakeFn;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.gui.InventoryAndStatusMenu;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

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
            int capacity,
            ServerPlayer sp,
            VisitorMobEntity e,
            JobID jobId
    ) {
        List<UIQuest> quests = UIQuest.fromLevel(sp.getLevel(), e.getQuestsWithRewards());
        NetworkHooks.openGui(sp, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return new TextComponent("");
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                TownQuestsContainer questsMenu = new TownQuestsContainer(windowId, quests, e.getFlagPos());
                return new InventoryAndStatusMenu(
                        windowId, e.getInventory(), p.getInventory(), e.getSlotLocks(), e, questsMenu, jobId
                );
            }
        }, data -> {
            data.writeInt(capacity);
            data.writeInt(e.getId());
            data.writeUtf(jobId.rootId());
            data.writeUtf(jobId.jobId());
            TownQuestsContainer.write(data, quests, e.getFlagPos());
        });
        return true; // Different jobs might have screens or not
    }

    public static void getOrCreateItemFromBlock(
            ServerLevel level,
            BlockPos b,
            TakeFn takeFn,
            MCHeldItem is,
            boolean nullifyExcess
    ) {
        while (!is.isEmpty()) {
            if (takeFn == null || !takeFn.Take(is)) {
                if (nullifyExcess) {
                    return;
                }
                level.addFreshEntity(new ItemEntity(level, b.getX(), b.getY(), b.getZ(), is.toItem().toItemStack()));
            }
            is = is.shrink();
        }
    }

    public static boolean townHasSupplies(
            TownInterface town,
            ItemsHolder<MCHeldItem> journal,
            ImmutableList<JobsClean.TestFn<MCTownItem>> recipe
    ) {
        return town.findMatchingContainer(item -> JobsClean.shouldTakeItem(
                journal.getCapacity(), recipe, journal.getItems(), item
        )) != null;
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
        return town.getRoomsMatching(id).stream()
                .filter(v -> v.room.yCoord > entityBlockPos.getY() - 5)
                .filter(v -> v.room.yCoord < entityBlockPos.getY() + 5)
                .filter(v -> InclusiveSpaces.contains(
                        v.room.getSpaces(),
                        Positions.FromBlockPos(entityBlockPos)
                ))
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
        Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomHandle().getRoomsMatching(roomType);
        return rooms.stream()
                .filter(v -> {
                    for (Map.Entry<BlockPos, Block> e : v.getContainedBlocks().entrySet()) {
                        if (check.Check(town.getServerLevel(), e.getKey())) {
                            return true;
                        }
                    }
                    return false;
                })
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


        boolean farFromChest = !isCloseTo(entityPos, target.getBlockPos());
        List<MCHeldItem> snapshot = Lists.reverse(ImmutableList.copyOf(dropper.getItems()));
        for (MCHeldItem mct : snapshot) {
            if (mct.isEmpty()) {
                continue;
            }
            // TODO: Unit tests of this logic!
            if (mct.isLocked()) {
                QT.JOB_LOGGER.trace("Villager is not putting away {} because it is locked", mct);
                continue;
            }

            if (ItemsInit.KNOWLEDGE.get().equals(mct.get().get())) {
                dropper.removeItem(mct);
                continue;
            }

            if (farFromChest) {
                continue;
            }

            QT.JOB_LOGGER.debug("Gatherer {} is putting {} in {}", ownerUUID, mct, target.getBlockPos());
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
                QT.JOB_LOGGER.debug("Nope. No space for {}", mct);
            }
        }
        return true;
    }
}
