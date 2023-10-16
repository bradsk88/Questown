package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.BreadOvenBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class BakerJob implements Job<MCHeldItem, BakerJournal.Snapshot<MCHeldItem>>, LockSlotHaver, ContainerListener, GathererJournal.ItemsListener<MCHeldItem>, Jobs.LootDropper<MCHeldItem>, Jobs.ContainerItemTaker {

    private final Marker marker = MarkerManager.getMarker("Baker");

    private final ArrayList<DataSlot> locks = new ArrayList<>();
    private final Container inventory;
    private Signals signal;
    private final BakerJournal<MCTownItem, MCHeldItem> journal;
    private int ticksSinceLastFarmAction;
    private RoomRecipeMatch<MCRoom> selectedBakery;
    private ContainerTarget<MCContainer, MCTownItem> successTarget;
    private ContainerTarget<MCContainer, MCTownItem> suppliesTarget;
    private boolean dropping;

    private final ImmutableSet<Item> holdItems = ImmutableSet.of(
            Items.WHEAT,
            Items.COAL
    );

    private final UUID ownerUUID;

    public BakerJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        // TODO: This is copy pasted. Reduce duplication.
        SimpleContainer sc = new SimpleContainer(inventoryCapacity) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.ownerUUID = ownerUUID;
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        this.journal = new BakerJournal<>(
                () -> this.signal, inventoryCapacity,
                (status, item) -> holdItems.contains(item.get().get()),
                MCHeldItem::Air
        );
        this.journal.addItemListener(this);
    }

    @Override
    public void addStatusListener(StatusListener o) {
        this.journal.addStatusListener(o);
    }

    @Override
    public GathererJournal.Status getStatus() {
        return journal.getStatus();
    }

    @Override
    public void initializeStatus(GathererJournal.Status s) {
        Questown.LOGGER.debug("Initialized journal to state {}", s);
        this.journal.initializeStatus(s);
    }

    @Override
    public void itemsChanged(ImmutableList<MCHeldItem> items) {
        Jobs.handleItemChanges(inventory, items);
    }

    @Override
    public UUID UUID() {
        return ownerUUID;
    }

    @Override
    public boolean hasAnyLootToDrop() {
        return journal.hasAnyLootToDrop();
    }

    @Override
    public Iterable<MCHeldItem> getItems() {
        return journal.getItems();
    }

    @Override
    public boolean removeItem(MCHeldItem mct) {
        return journal.removeItem(mct);
    }

    @Override
    public void addItem(MCHeldItem mcHeldItem) {
        journal.addItem(mcHeldItem);
    }

    @Override
    public boolean isInventoryFull() {
        return journal.isInventoryFull();
    }

    @Override
    public void tick(
            TownInterface town,
            BlockPos entityBlockPos,
            Vec3 entityPos,
            Direction facingPos
    ) {
        ServerLevel sl = town.getServerLevel();
        if (town == null || sl == null) {
            return;
        }

        Function<RoomRecipeMatch<MCRoom>, Boolean> _isBakeryFull = (RoomRecipeMatch<MCRoom> mcRoom) -> {
            ImmutableMap<BlockPos, Block> containedBlocks = mcRoom.getContainedBlocks();
            return containedBlocks.entrySet().stream()
                    .filter(v -> v.getValue().equals(BlocksInit.BREAD_OVEN_BLOCK.get()))
                    .map(v -> sl.getBlockState(v.getKey()))
                    .noneMatch(BreadOvenBlock::canAddIngredients);
        };

        processSignal(sl, this, new BakerStatuses.TownStateProvider<MCRoom>() {

            @Override
            public boolean hasSupplies() {
                // TODO: Use tags?
                return town.findMatchingContainer(v -> v.get().equals(Items.WHEAT)) != null;
            }

            @Override
            public boolean isBakeryFull(RoomRecipeMatch<MCRoom> mcRoom) {
                return _isBakeryFull.apply(mcRoom);
            }

            @Override
            public boolean hasBakerySpace() {
                // TODO: Use tags to match different bakery tiers?
                ResourceLocation id = new ResourceLocation(Questown.MODID, "bakery");
                Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomsMatching(id);
                return rooms.stream().anyMatch(v -> !_isBakeryFull.apply(v));
            }

            @Override
            public Collection<MCRoom> bakeriesWithBread() {
                ResourceLocation id = new ResourceLocation(Questown.MODID, "bakery");
                Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomsMatching(id);
                return rooms.stream()
                        .filter(v -> {
                            for (Map.Entry<BlockPos, Block> e : v.getContainedBlocks().entrySet()) {
                                if (!(e.getValue() instanceof BreadOvenBlock)) {
                                    continue;
                                }
                                if (BreadOvenBlock.hasBread(sl.getBlockState(e.getKey()))) {
                                    return true;
                                }
                            }
                            return false;
                        })
                        .map(v -> v.room)
                        .toList();
            }
        }, new BakerStatuses.EntityStateProvider<>() {
            @Override
            public @Nullable RoomRecipeMatch<MCRoom> getEntityBakeryLocation() {
                // TODO: Use tags to match different bakery tiers?
                ResourceLocation id = new ResourceLocation(Questown.MODID, "bakery");
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
        }, new EntityStateProvider() {
            @Override
            public boolean inventoryFull() {
                return journal.isInventoryFull();
            }

            @Override
            public boolean hasNonSupplyItems() {
                return journal.getItems().stream()
                        .filter(Predicates.not(GathererJournal.Item::isEmpty))
                        .anyMatch(Predicates.not(v -> holdItems.contains(v.get().get())));
            }

            @Override
            public Map<GathererJournal.Status, Boolean> getSupplyItemStatus() {
                boolean hasWheat = town.findMatchingContainer(v -> Items.WHEAT.equals(v.get().asItem())) != null;
                return ImmutableMap.of(
                        GathererJournal.Status.BAKING, hasWheat,
                        GathererJournal.Status.GOING_TO_BAKERY, hasWheat
                );
            }

            @Override
            public boolean hasItems() {
                return false;
            }
        });

        tryBaking(town, entityBlockPos);
        tryDropLoot(entityBlockPos);
        tryGetSupplies(entityBlockPos);

        // TODO: Implement all of this for cook
//        // TODO: Take cooked food to chests
//        if (successTarget != null && !successTarget.isStillValid()) {
//            successTarget = null;
//        }
//
//        // TODO: Get food for self to eat
//        if (foodTarget != null && !foodTarget.isStillValid()) {
//            foodTarget = null;
//        }
//
//        if (jobBlockTarget != null) {
//            if (brain.getOrSetKitchenLocation(entityPos) == null) {
//                jobBlockTarget = null;
//            }
//        }
//        tryDropLoot(entityPos);
//        tryTakeFood(entityPos);
    }

    private void tryDropLoot(
            BlockPos entityPos
    ) {
        // TODO: Introduce this status for farmer
//        if (journal.getStatus() != GathererJournal.Status.DROPPING_LOOT) {
//            return;
//        }
        if (this.dropping) {
            Questown.LOGGER.debug("Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(this, entityPos, successTarget);
    }

    private void tryGetSupplies(
            BlockPos entityPos
    ) {
        // TODO: Introduce this status for farmer
//        if (journal.getStatus() != GathererJournal.Status.DROPPING_LOOT) {
//            return;
//        }
        Jobs.tryTakeContainerItems(this, entityPos, suppliesTarget, (item) -> holdItems.contains(item.get()));
    }

    @Override
    public @Nullable BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        @Nullable ServerLevel sl = town.getServerLevel();
        if (sl == null) {
            return null;
        }

        Collection<RoomRecipeMatch<MCRoom>> bakeries = town.getRoomsMatching(
                // TODO: Use tags to support more tiers of bakery
                new ResourceLocation(Questown.MODID, "bakery")
        );

        for (RoomRecipeMatch<MCRoom> match : bakeries) {
            for (Map.Entry<BlockPos, Block> blocks : match.getContainedBlocks().entrySet()) {
                BlockState blockState = sl.getBlockState(blocks.getKey());
                if (BreadOvenBlock.canAddIngredients(blockState) || BreadOvenBlock.hasBread(blockState)) {
                    selectedBakery = match;
                    break;
                }
            }
        }
        if (selectedBakery == null) {
            return null;
        }

        switch (journal.getStatus()) {
            case GOING_TO_BAKERY, BAKING, COLLECTING_BREAD -> {
                WorkSpot ovenInteractionSpot = getOvenInteractionSpot(town, selectedBakery);
                if (ovenInteractionSpot != null) {
                    return ovenInteractionSpot.interactionSpot;
                }
            }
            case DROPPING_LOOT -> {
                successTarget = Jobs.setupForDropLoot(town, this.successTarget);
                if (successTarget != null) {
                    return successTarget.getBlockPos();
                }
            }
            case COLLECTING_SUPPLIES, NO_SUPPLIES -> {
                setupForGetSupplies(town, holdItems);
                if (suppliesTarget != null) {
                    return suppliesTarget.getBlockPos();
                }
            }
        }
        return null;
    }

    private void setupForGetSupplies(
            TownInterface town,
            Set<Item> suppliesNeeded
    ) {
        Questown.LOGGER.debug("Baker is searching for supplies");
        if (this.suppliesTarget != null) {
            if (!this.suppliesTarget.hasItem(item -> suppliesNeeded.contains(item.get()))) {
                this.suppliesTarget = town.findMatchingContainer(item -> suppliesNeeded.contains(item.get()));
            }
        } else {
            this.suppliesTarget = town.findMatchingContainer(item -> suppliesNeeded.contains(item.get()));
        }
        if (this.suppliesTarget != null) {
            Questown.LOGGER.debug("Baker located supplies at {}", this.suppliesTarget.getPosition());
        }
    }

    private boolean tryBaking(
            TownInterface town,
            BlockPos entityPos
    ) {
        if (town.getServerLevel() == null) {
            return false;
        }
        ServerLevel sl = town.getServerLevel();

        ticksSinceLastFarmAction++;
        if (ticksSinceLastFarmAction < Config.FARM_ACTION_INTERVAL.get()) {
            return false;
        }
        ticksSinceLastFarmAction = 0;

        if (selectedBakery == null) {
            return false;
        }

        WorkSpot oven = getOvenInteractionSpot(town, selectedBakery);
        if (oven == null) {
            return false;
        }

        if (!Jobs.isCloseTo(entityPos, oven.block)) {
            return false;
        }

        BlockState oldState = sl.getBlockState(oven.block);

        if (BreadOvenBlock.hasBread(oldState)) {
            BlockState blockState = BreadOvenBlock.extractBread(oldState, sl, oven.block);
            return !oldState.equals(blockState);
        }

        if (!BreadOvenBlock.canAddIngredients(oldState)) {
            return false;
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (BreadOvenBlock.canAcceptWheat(oldState) && Items.WHEAT.equals(item.getItem())) {
                BlockState blockstate = BreadOvenBlock.insertItem(sl, oldState, item);
                if (item.getCount() > 0) {
                    // didn't insert successfully
                    return false;
                }
                sl.setBlockAndUpdate(oven.block, blockstate);
                QT.JOB_LOGGER.debug(marker, "{} is removing {} from {}", this.getJobName(), item, inventory);
                inventory.setChanged();
                return true;
            }
            if (BreadOvenBlock.canAcceptCoal(oldState) && Items.COAL.equals(item.getItem())) {
                BlockState blockstate = BreadOvenBlock.insertItem(sl, oldState, item);
                if (item.getCount() > 0) {
                    // didn't insert successfully
                    return false;
                }
                sl.setBlockAndUpdate(oven.block, blockstate);
                QT.JOB_LOGGER.debug(marker, "{} is removing {} from {}", this.getJobName(), item, inventory);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    private static void processSignal(
            Level level,
            BakerJob e,
            BakerStatuses.TownStateProvider<MCRoom> townState,
            BakerStatuses.EntityStateProvider<MCRoom> entityState,
            EntityStateProvider inventoryState
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

        e.signal = Signals.fromGameTime(level.getDayTime());
        e.journal.tick(townState, entityState, inventoryState);
    }

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    ) {
        // Since cooks don't leave town. They don't need to disappear.
        return false;
    }

    @Override
    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        return Jobs.openInventoryAndStatusScreen(journal.getCapacity(), journal.getItems(), sp, e);
    }

    @Override
    public Container getInventory() {
        return inventory;
    }

    @Override
    public BakerJournal.Snapshot<MCHeldItem> getJournalSnapshot() {
        return journal.getSnapshot(MCHeldItem::Air);
    }

    @Override
    public void initialize(BakerJournal.Snapshot<MCHeldItem> journal) {
        this.journal.initialize(journal);
    }

    @Override
    public ImmutableList<Boolean> getSlotLockStatuses() {
        return this.journal.getSlotLockStatuses();
    }

    @Override
    public void lockSlot(int slotIndex) {

    }

    @Override
    public void unlockSlot(int slotIndex) {

    }

    @Override
    public DataSlot getLockSlot(int i) {
        return locks.get(i);
    }

    public static class WorkSpot {
        final BlockPos block;
        final BlockPos interactionSpot;

        public WorkSpot(
                BlockPos block,
                BlockPos interactionSpot
        ) {
            this.block = block;
            this.interactionSpot = interactionSpot;
        }
    }

    @Nullable
    private static WorkSpot getOvenInteractionSpot(
            TownInterface town,
            @Nullable RoomRecipeMatch<MCRoom> foundRoom
    ) {
        if (foundRoom == null) {
            return null;
        }

        Optional<Map.Entry<BlockPos, Block>> fillableOven = foundRoom.getContainedBlocks().entrySet().stream()
                .filter(v -> v.getValue().equals(BlocksInit.BREAD_OVEN_BLOCK.get()))
                .filter(v -> {
                    BlockState bs = town.getServerLevel().getBlockState(v.getKey());
                    return BreadOvenBlock.canAddIngredients(bs);
                })
                .findFirst();
        Optional<Map.Entry<BlockPos, Block>> ovenWithBread = foundRoom.getContainedBlocks().entrySet().stream()
                .filter(v -> v.getValue().equals(BlocksInit.BREAD_OVEN_BLOCK.get()))
                .filter(v -> {
                    BlockState bs = town.getServerLevel().getBlockState(v.getKey());
                    return BreadOvenBlock.hasBread(bs);
                })
                .findFirst();
        if (fillableOven.isEmpty() && ovenWithBread.isEmpty()) {
            return null;
        }

        if (ovenWithBread.isPresent()) {
            BlockPos ovenPos = ovenWithBread.get().getKey();

            // TODO: Make oven horizontal block and get direction property

            return new WorkSpot(
                    ovenPos,
                    ovenPos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(town.getServerLevel().getRandom()))
            );
        }
        BlockPos ovenPos = fillableOven.get().getKey();

        // TODO: Make oven horizontal block and get direction property

        return new WorkSpot(
                ovenPos,
                ovenPos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(town.getServerLevel().getRandom()))
        );
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    @Override
    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos blockPos
    ) {
        return false;
    }

    @Override
    public Component getJobName() {
        return new TranslatableComponent("jobs.farmer");
    }

    @Override
    public boolean addToEmptySlot(MCTownItem mcTownItem) {
        boolean isAllowedToPickUp = ImmutableList.of(
                Items.COAL,
                Items.WHEAT,
                Items.BREAD
        ).contains(mcTownItem.get());
        if (!isAllowedToPickUp) {
            return false;
        }
        return journal.addItemIfSlotAvailable(new MCHeldItem(mcTownItem));
    }

    @Override
    public void containerChanged(Container p_18983_) {
        if (Jobs.isUnchanged(p_18983_, journal.getItems())) {
            return;
        }

        ImmutableList.Builder<MCHeldItem> b = ImmutableList.builder();

        for (int i = 0; i < p_18983_.getContainerSize(); i++) {
            ItemStack item = p_18983_.getItem(i);
            b.add(new MCHeldItem(MCTownItem.fromMCItemStack(item), locks.get(i).get() == 1));
        }
        journal.setItemsNoUpdateNoCheck(b.build());
    }

}
