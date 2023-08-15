package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.GathererInventoryMenu;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.jobs.Statuses;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class VisitorMobJob implements GathererJournal.SignalSource, GathererJournal.LootProvider<MCTownItem>, ContainerListener, GathererJournal.ItemsListener<MCTownItem> {

    private final @Nullable ServerLevel level;
    private final Container inventory;
    ContainerTarget<MCContainer, MCTownItem> foodTarget;
    ContainerTarget<MCContainer, MCTownItem> successTarget;
    // TODO: Logic for changing jobs
    private final GathererJournal<MCTownItem> journal = new GathererJournal<>(
            this, MCTownItem::Air, new Statuses.TownStateProvider() {
        @Override
        public boolean IsStorageAvailable() {
            return successTarget != null && successTarget.isStillValid();
        }

        @Override
        public boolean HasGate() {
            return false;
        }
    }
    ) {
        @Override
        protected void changeStatus(Status s) {
            super.changeStatus(s);
            Questown.LOGGER.debug("Changed status to {}", s);
        }
    };
    private GathererJournal.Signals signal;
    private boolean dropping;

    public VisitorMobJob(@Nullable ServerLevel level) {
        this.level = level;
        SimpleContainer sc = new SimpleContainer(journal.getCapacity()) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.inventory = sc;
        sc.addListener(this);
        journal.addItemsListener(this);
    }

    private static void processSignal(
            Level level,
            VisitorMobJob e
    ) {
        if (level.isClientSide()) {
            return;
        }

        /*
         * Sunrise: 22000
         * Dawn: 0
         * Noon: 6000
         * Evening: 11500
         */

        e.signal = GathererJournal.Signals.fromGameTime(level.getDayTime());
        e.journal.tick(e);
    }

    @NotNull
    private static BlockPos getEnterExitPos(TownInterface town) {
        return town.getEnterExitPos();
    }

    public void tick(
            Level level,
            BlockPos entityPos
    ) {
        processSignal(level, this);
        if (successTarget != null && !successTarget.isStillValid()) {
            successTarget = null;
        }
        if (foodTarget != null && !foodTarget.isStillValid()) {
            foodTarget = null;
        }
    }

    public GathererJournal.Signals getSignal() {
        return this.signal;
    }

    public Collection<MCTownItem> getLoot() {
        return getLootFromLevel(level, journal.getCapacity());
    }

    public static Collection<MCTownItem> getLootFromLevel(
            ServerLevel level, int maxItems
    ) {
        if (level == null) {
            return ImmutableList.of();
        }
        LootTable lootTable = level.getServer().getLootTables().get(
                // TODO: Own loot table
                new ResourceLocation(Questown.MODID, "jobs/gatherer_vanilla")
        );
        LootContext.Builder lcb = new LootContext.Builder((ServerLevel) level);
        LootContext lc = lcb.create(LootContextParamSets.EMPTY);
        // TODO: Maybe add this once the entity is not a BlockEntity?
//        LootContext lc = lcb
//                .withParameter(LootContextParams.THIS_ENTITY, this)
//                .withParameter(LootContextParams.ORIGIN, getBlockPos())
//                .create(LootContextParamSets.ADVANCEMENT_REWARD);

        List<ItemStack> rItems = lootTable.getRandomItems(lc);
        Collections.shuffle(rItems);
        int subLen = Math.min(rItems.size(), maxItems);
        List<MCTownItem> list = rItems.stream()
                .filter(v -> !v.isEmpty())
                .map(ItemStack::getItem)
                .map(MCTownItem::new)
                .toList()
                .subList(0, subLen);

        Questown.LOGGER.debug("[VMJ] Presenting items to gatherer: {}", list);

        return list;
    }

    public void initializeStatus(GathererJournal.Status status) {
        Questown.LOGGER.debug("Initialized journal to state {}", status);
        this.journal.initializeStatus(status);
    }

    public BlockPos getTarget(TownInterface town) {
        BlockPos enterExitPos = getEnterExitPos(town); // TODO: Smarter logic? Town gate?
        switch (journal.getStatus()) {
            case NO_FOOD -> {
                return handleNoFoodStatus(town);
            }
            case UNSET, IDLE, STAYING, RELAXING, NO_GATE -> {
                return null;
            }
            case GATHERING, GATHERING_EATING, GATHERING_HUNGRY, RETURNING, RETURNING_AT_NIGHT, CAPTURED -> {
                return enterExitPos;
            }
            case DROPPING_LOOT, RETURNED_SUCCESS, NO_SPACE -> {
                return setupForDropLoot(town);
            }
            case RETURNED_FAILURE -> {
                return new BlockPos(town.getVisitorJoinPos());
            }
        }
        throw new IllegalStateException("Unhandled status: " + journal.getStatus().name());
    }

    private BlockPos handleNoFoodStatus(TownInterface town) {
        if (journal.hasAnyNonFood()) {
            return setupForDropLoot(town);
        }

        Questown.LOGGER.debug("Visitor is searching for food");
        if (this.foodTarget != null) {
            if (!this.foodTarget.hasItem(MCTownItem::isFood)) {
                this.foodTarget = town.findMatchingContainer(MCTownItem::isFood);
            }
        } else {
            this.foodTarget = town.findMatchingContainer(MCTownItem::isFood);
        }
        if (this.foodTarget != null) {
            Questown.LOGGER.debug("Located food at {}", this.foodTarget.getPosition());
            return Positions.ToBlock(this.foodTarget.getInteractPosition(), this.foodTarget.getYPosition());
        } else {
            Questown.LOGGER.debug("No food exists in town");
            return town.getRandomWanderTarget();
        }
    }

    private BlockPos setupForDropLoot(TownInterface town) {
        Questown.LOGGER.debug("Visitor is searching for chest space");
        if (this.successTarget != null) {
            if (!this.successTarget.hasItem(MCTownItem::isEmpty)) {
                this.successTarget = town.findMatchingContainer(MCTownItem::isEmpty);
            }
        } else {
            this.successTarget = town.findMatchingContainer(MCTownItem::isEmpty);
        }
        if (this.successTarget != null) {
            Questown.LOGGER.debug("Located chest at {}", this.successTarget.getPosition());
            return Positions.ToBlock(this.successTarget.getInteractPosition(), this.successTarget.getYPosition());
        } else {
            Questown.LOGGER.debug("No chests exist in town");
            return town.getRandomWanderTarget();
        }
    }

    private BlockPos setupForLeaveTown(TownInterface town) {
        Questown.LOGGER.debug("Visitor is searching for a town gate");
        // TODO: Get the CLOSEST gate?
        return town.getEnterExitPos();
    }

    public boolean shouldDisappear(
            TownInterface town,
            BlockPos entityPos
    ) {
        if (
                journal.getStatus() == GathererJournal.Status.GATHERING ||
                        journal.getStatus() == GathererJournal.Status.RETURNING ||
                        journal.getStatus() == GathererJournal.Status.CAPTURED
        ) {
            return isCloseTo(entityPos, getEnterExitPos(town));
        }
        return false;
    }

    private boolean isCloseTo(
            @NotNull BlockPos entityPos,
            @NotNull BlockPos targetPos
    ) {
        double d = targetPos.distToCenterSqr(
                entityPos.getX(), entityPos.getY(), entityPos.getZ()
        );
        return d < 5;
    }

    public boolean isCloseToFood(
            @NotNull BlockPos entityPos
    ) {
        if (foodTarget == null) {
            return false;
        }
        if (!foodTarget.hasItem(MCTownItem::isFood)) {
            return false;
        }
        return isCloseTo(entityPos, Positions.ToBlock(foodTarget.getPosition(), foodTarget.yPosition));
    }

    public boolean isCloseToChest(
            @NotNull BlockPos entityPos
    ) {
        if (successTarget == null) {
            return false;
        }
        if (!successTarget.hasItem(MCTownItem::isEmpty)) {
            return false;
        }
        return isCloseTo(entityPos, Positions.ToBlock(successTarget.getPosition(), successTarget.yPosition));
    }

    public void tryTakeFood(BlockPos entityPos) {
        if (journal.getStatus() != GathererJournal.Status.NO_FOOD) {
            return;
        }
        if (journal.hasAnyFood()) {
            return;
        }
        if (!isCloseToFood(entityPos)) {
            return;
        }
        for (int i = 0; i < foodTarget.container.size(); i++) {
            MCTownItem mcTownItem = foodTarget.container.getItem(i);
            if (mcTownItem.isFood()) {
                Questown.LOGGER.debug("Gatherer is taking {} from {}", mcTownItem, foodTarget);
                journal.addItem(mcTownItem);
                foodTarget.container.removeItem(i, 1);
                break;
            }
        }
    }

    public void tryDropLoot(UUID uuid, BlockPos entityPos) {
        // TODO: move to journal?
        if (journal.getStatus() != GathererJournal.Status.DROPPING_LOOT) {
            return;
        }
        if (this.dropping) {
            Questown.LOGGER.debug("Trying to drop too quickly");
        }
        this.dropping = true;
        if (!journal.hasAnyNonFood()) {
            Questown.LOGGER.trace("{} is not dropping because they only have food", uuid);
            this.dropping = false;
            return;
        }
        if (!isCloseToChest(entityPos)) {
            Questown.LOGGER.trace("{} is not dropping because they are not close to an empty chest", uuid);
            this.dropping = false;
            return;
        }
        for (MCTownItem mct : Lists.reverse(journal.getItems())) {
            if (mct.isEmpty()) {
                continue;
            }
            Questown.LOGGER.debug("Gatherer is putting {} in {}", mct, successTarget);
            boolean added = false;
            for (int i = 0; i < successTarget.container.size(); i++) {
                if (added) {
                    break;
                }
                // TODO: Allow stacking?
                if (successTarget.container.getItem(i).isEmpty()) {
                    if (journal.removeItem(mct)) {
                        successTarget.container.setItem(i, mct);
                    }
                    added = true;
                }
            }
            if (!added) {
                Questown.LOGGER.debug("Nope. No space for {}", mct);
            }
        }
        this.dropping = false;
    }

    public boolean openScreen(ServerPlayer sp, VisitorMobEntity e) {
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
                return new GathererInventoryMenu(windowId, e.getInventory(), p.getInventory(), e);
            }
        }, data -> {
            data.writeInt(journal.getCapacity());
            data.writeInt(e.getId());
            data.writeCollection(journal.getItems(), (buf, item) -> {
                ResourceLocation id = Items.AIR.getRegistryName();
                if (item != null) {
                    id = item.get().getRegistryName();
                }
                buf.writeResourceLocation(id);
            });
        });
        return true; // Different jobs might have screens or not
    }

    @Override
    public void containerChanged(Container p_18983_) {
        if (unchanged(p_18983_, journal.getItems())) {
            return;
        }

        ImmutableList.Builder<MCTownItem> b = ImmutableList.builder();

        for (int i = 0; i < p_18983_.getContainerSize(); i++) {
            b.add(new MCTownItem(p_18983_.getItem(i).getItem()));
        }
        journal.setItemsNoUpdateNoCheck(b.build());
    }

    @Override
    public void itemsChanged(ImmutableList<MCTownItem> items) {
        if (unchanged(inventory, items)) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).get().equals(inventory.getItem(i).getItem())) {
                continue;
            }
            inventory.setItem(i, new ItemStack(items.get(i).get(), 1));
        }
    }

    private boolean unchanged(
            Container container,
            ImmutableList<MCTownItem> items
    ) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).is(items.get(i).get())) {
                return false;
            }
        }
        return true;
    }

    public Container getInventory() {
        return inventory;
    }

    public GathererJournal.Status getStatus() {
        return journal.getStatus();
    }

    public void addStatusListener(GathererJournal.StatusListener l) {
        journal.addStatusListener(l);
    }

    public void initializeItems(Iterable<MCTownItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    public ImmutableList<ItemStack> getItems() {
        ImmutableList.Builder<ItemStack> b = ImmutableList.builder();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            b.add(inventory.getItem(i));
        }
        return b.build();
    }

    public GathererJournal.Snapshot<MCTownItem> getJournalSnapshot() {
        return journal.getSnapshot(MCTownItem::Air);
    }

    public void initialize(GathererJournal.Snapshot<MCTownItem> journal) {
        this.journal.initialize(journal);
    }
}
