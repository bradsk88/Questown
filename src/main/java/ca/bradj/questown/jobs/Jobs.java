package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.InventoryAndStatusMenu;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class Jobs {
    public static ImmutableList<ItemStack> getItems(Job<MCHeldItem, ?> job) {
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
            if (items.get(i).get().equals(inventory.getItem(i).getItem())) {
                continue;
            }
            inventory.setItem(i, new ItemStack(items.get(i).get().get(), 1));
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
        H emptySlot = inventory.stream().filter(GathererJournal.Item::isEmpty).findFirst().get();
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
            Collection<MCHeldItem> items,
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        List<UIQuest> quests = UIQuest.fromLevel(sp.getLevel(), e.getQuestsWithRewards());
        NetworkHooks.openGui(sp, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return TextComponent.EMPTY;
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                TownQuestsContainer questsMenu = new TownQuestsContainer(windowId, quests);
                return new InventoryAndStatusMenu(
                        windowId, e.getInventory(), p.getInventory(), e.getSlotLocks(), e, questsMenu
                );
            }
        }, data -> {
            data.writeInt(capacity);
            data.writeInt(e.getId());
            UIQuest.Serializer ser = new UIQuest.Serializer();
            data.writeInt(quests.size());
            data.writeCollection(quests, (buf, recipe) -> {
                ResourceLocation id;
                if (recipe == null) {
                    id = SpecialQuests.BROKEN;
                    recipe = new UIQuest(SpecialQuests.SPECIAL_QUESTS.get(id), Quest.QuestStatus.ACTIVE, null, null, null);
                } else {
                    id = recipe.getRecipeId();
                }
                buf.writeResourceLocation(id);
                ser.toNetwork(buf, recipe);
            });
        });
        return true; // Different jobs might have screens or not
    }

    public static ResourceLocation getRoomForJob(String job) {
        return switch (job) {
            case "baker" -> new ResourceLocation(Questown.MODID, "bakery");
            case "farmer" -> SpecialQuests.FARM;
            default -> throw new IllegalArgumentException("Unhandled job type: '" + job + "'");
        };
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
            Questown.LOGGER.trace("{} is not dropping because they only have food", ownerUUID);
            return false;
        }
        if (!isCloseToChest(entityPos, target)) {
            Questown.LOGGER.trace("{} is not dropping because they are not close to an empty chest", ownerUUID);
            return false;
        }
        List<MCHeldItem> snapshot = Lists.reverse(ImmutableList.copyOf(dropper.getItems()));
        for (MCHeldItem mct : snapshot) {
            if (mct.isEmpty()) {
                continue;
            }
            // TODO: Unit tests of this logic!
            if (mct.isLocked()) {
                Questown.LOGGER.trace("Gatherer is not putting away {} because it is locked", mct);
                continue;
            }
            Questown.LOGGER.debug("Gatherer {} is putting {} in {}", ownerUUID, mct, target.getBlockPos());
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
                Questown.LOGGER.debug("Nope. No space for {}", mct);
            }
        }
        return true;
    }

    public interface ContainerItemTaker {

        void addItem(MCHeldItem mcHeldItem);

        boolean isInventoryFull();
    }

    public static void tryTakeContainerItems(
            ContainerItemTaker farmerJob,
            BlockPos entityPos,
            ContainerTarget<MCContainer, MCTownItem> suppliesTarget,
            ContainerTarget.CheckFn<MCTownItem> check
    ) {
        if (!isCloseToChest(entityPos, suppliesTarget)) {
            return;
        }
        if (farmerJob.isInventoryFull()) {
            return;
        }
        for (int i = 0; i < suppliesTarget.size(); i++) {
            MCTownItem mcTownItem = suppliesTarget.getItem(i);
            if (check.Matches(mcTownItem)) {
                Questown.LOGGER.debug("Villager is taking {} from {}", mcTownItem, suppliesTarget);
                farmerJob.addItem(new MCHeldItem(mcTownItem));
                suppliesTarget.getContainer().removeItem(i, 1);
                break;
            }
        }
    }

    private static boolean isCloseToChest(
            BlockPos entityPos,
            ContainerTarget<?, ? extends GathererJournal.Item<?>> chest
    ) {
        if (chest == null) {
            return false;
        }
        return Jobs.isCloseTo(entityPos, chest.getBlockPos());
    }
}
