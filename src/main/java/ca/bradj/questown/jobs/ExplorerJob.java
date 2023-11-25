package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class ExplorerJob implements Job<MCHeldItem, GathererJournal.Snapshot<MCHeldItem>, GathererJournal.Status>, SignalSource, GathererJournal.LootProvider<MCTownItem>, ContainerListener, JournalItemsListener<MCHeldItem>, LockSlotHaver, Jobs.LootDropper<MCHeldItem> {

    public static final JobID ID = new JobID("gatherer", "explore");
    private @Nullable TownInterface town;
    private final Container inventory;
    private final UUID ownerUUID;
    @Nullable ContainerTarget<MCContainer, MCTownItem> foodTarget;
    @Nullable ContainerTarget<MCContainer, MCTownItem> successTarget;
    @Nullable BlockPos gateTarget;
    // TODO: Logic for changing jobs
    private final GathererJournal<MCTownItem, MCHeldItem> journal;
    private Signals signal;
    private boolean dropping;

    private final List<LockSlot> locks = new ArrayList<>();
    private Signals passedThroughGate = Signals.UNDEFINED;
    private boolean closeToGate;

    public ExplorerJob(
            TownInterface town,
            // null on client side
            int inventoryCapacity,
            UUID ownerUUID
    ) {
        if (town != null && !town.getServerLevel().isClientSide()) {
            this.town = town;
        }
        this.ownerUUID = ownerUUID;
        SimpleContainer sc = new SimpleContainer(inventoryCapacity) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        GathererStatuses.TownStateProvider tsp = new GathererStatuses.TownStateProvider() {
            @Override
            public boolean IsStorageAvailable() {
                return successTarget != null && successTarget.isStillValid();
            }

            @Override
            public boolean hasGate() {
                return gateTarget != null;
            }
        };
        journal = new ExplorerJournal<MCTownItem, MCHeldItem>(
                this, MCHeldItem::Air, MCHeldItem::new,
                tsp, inventoryCapacity
        ) {
            @Override
            protected void changeStatus(GathererJournal.Status s) {
                super.changeStatus(s);
                Questown.LOGGER.debug("Changed status to {}", s);
            }
        };
        journal.addItemsListener(this);
    }

    private static void processSignal(
            Level level,
            ExplorerJob e
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
        e.journal.tick(e);
    }

    @NotNull
    private static BlockPos getEnterExitPos(TownInterface town) {
        return town.getEnterExitPos();
    }

    public void tick(
            TownInterface town,
            LivingEntity entity,
            Direction relative
    ) {
        if (town == null || town.getServerLevel() == null) {
            return;
        }
        processSignal(town.getServerLevel(), this);

        this.closeToGate = false;
        BlockPos entityPos = entity.blockPosition();
        BlockPos welcomeMat = town.getClosestWelcomeMatPos(entityPos);
        if (signal == Signals.MORNING && welcomeMat != null) {
            this.closeToGate = Jobs.isCloseTo(entityPos, welcomeMat);
        }

        if (successTarget != null && !successTarget.isStillValid()) {
            successTarget = null;
        }
        if (foodTarget != null && !foodTarget.isStillValid()) {
            foodTarget = null;
        }
        if (gateTarget != null) {
            if (welcomeMat == null) {
                gateTarget = null;
            }
        }
        tryDropLoot(entityPos);
        tryTakeFood(entityPos);
    }

    public Signals getSignal() {
        return this.signal;
    }

    public Collection<MCTownItem> getLoot(GathererJournal.Tools tools) {
        return getLootFromLevel(town);
    }

    public static Collection<MCTownItem> getLootFromLevel(
            TownInterface town
    ) {
        if (town == null || town.getServerLevel() == null) {
            return ImmutableList.of();
        }
        ServerLevel level = town.getServerLevel();

        ItemStack map = ItemsInit.GATHERER_MAP.get().getDefaultInstance();

        // TODO: Get from JSON files so mod can be extended
        ImmutableList<ResourceLocation> biomes = ImmutableList.of(
                new ResourceLocation("dark_forest"),
                new ResourceLocation("desert"),
                new ResourceLocation("jungle")
        );

        ResourceLocation biome = biomes.get(level.getRandom().nextInt(biomes.size()));

        QTNBT.putString(map, "biome", biome.toString());

        ImmutableList<MCTownItem> list = ImmutableList.of(MCTownItem.fromMCItemStack(map));

        QT.JOB_LOGGER.debug("Presenting items to explorer: {}", list);

        return list;
    }

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {
        GathererJournal.Status z = GathererJournal.getStatusFromEntityData(s);
        this.journal.initializeStatus(z);
    }

    @Override
    public String getStatusToSyncToClient() {
        return getStatus().name();
    }

    public BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        BlockPos enterExitPos = getEnterExitPos(town); // TODO: Smarter logic? Town gate?
        return switch (journal.getStatus()) {
            case NO_FOOD -> handleNoFoodStatus(entityBlockPos, town);
            case NO_GATE -> handleNoGateStatus(entityBlockPos, town);
            case UNSET, IDLE, STAYING, RELAXING -> null;
            case GATHERING, GATHERING_EATING, GATHERING_HUNGRY, RETURNING, RETURNING_AT_NIGHT, CAPTURED -> enterExitPos;
            case DROPPING_LOOT, RETURNED_SUCCESS, NO_SPACE -> setupForDropLoot(entityBlockPos, town);
            case RETURNED_FAILURE -> new BlockPos(town.getVisitorJoinPos());
            case GOING_TO_JOBSITE -> throw new IllegalArgumentException("Gatherer was job status");
            case LEAVING_FARM, FARMING_HARVESTING, FARMING_RANDOM_TEND,
                    FARMING_TILLING, FARMING_PLANTING, FARMING_BONING,
                    FARMING_COMPOSTING, FARMING_WEEDING ->
                    throw new IllegalArgumentException("Gatherer was given farmer status");
            case COLLECTING_SUPPLIES, NO_SUPPLIES, BAKING, BAKING_FUELING, COLLECTING_BREAD ->
                    throw new IllegalArgumentException("Gatherer was given baker status");
        };
    }

    private BlockPos handleNoGateStatus(
            BlockPos entityPos,
            TownInterface town
    ) {
        if (journal.hasAnyLootToDrop()) {
            return setupForDropLoot(entityPos, town);
        }

        Questown.LOGGER.debug("Visitor is searching for gate");
        if (this.gateTarget != null) {
            Questown.LOGGER.debug("Located gate at {}", this.gateTarget);
            return this.gateTarget;
        } else {
            this.gateTarget = town.getClosestWelcomeMatPos(entityPos);
        }
        if (this.gateTarget != null) {
            Questown.LOGGER.debug("Located gate at {}", this.gateTarget);
            return this.gateTarget;
        } else {
            Questown.LOGGER.debug("No gate exists in town");
            return town.getRandomWanderTarget(entityPos);
        }
    }

    private BlockPos handleNoFoodStatus(
            BlockPos entityBlockPos,
            TownInterface town
    ) {
        if (journal.hasAnyLootToDrop()) {
            return setupForDropLoot(entityBlockPos, town);
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
            return town.getRandomWanderTarget(entityBlockPos);
        }
    }

    private BlockPos setupForDropLoot(
            BlockPos entityPos,
            TownInterface town
    ) {
        this.successTarget = Jobs.setupForDropLoot(town, this.successTarget);
        if (this.successTarget != null) {
            return Positions.ToBlock(successTarget.getInteractPosition(), successTarget.getYPosition());
        }
        return town.getRandomWanderTarget(entityPos);
    }

    private BlockPos setupForLeaveTown(TownInterface town) {
        Questown.LOGGER.debug("Visitor is searching for a town gate");
        // TODO: Get the CLOSEST gate?
        return town.getEnterExitPos();
    }

    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPos
    ) {
        if (passedThroughGate != Signals.UNDEFINED && passedThroughGate.equals(signal)) {
            return true;
        }
        passedThroughGate = Signals.UNDEFINED;
        if (journal.getStatus() == GathererJournal.Status.GATHERING) {
            boolean veryCloseTo = Jobs.isVeryCloseTo(entityPos, getEnterExitPos(town));
            if (veryCloseTo) {
                this.passedThroughGate = signal;
                return true;
            }
            return false;
        }
        return journal.getStatus().isReturning();
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
        return Jobs.isCloseTo(entityPos, Positions.ToBlock(foodTarget.getPosition(), foodTarget.yPosition));
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
                journal.addItem(new MCHeldItem(mcTownItem));
                foodTarget.container.removeItem(i, 1);
                break;
            }
        }
    }

    public void tryDropLoot(
            BlockPos entityPos
    ) {
        // TODO: move to journal?
        if (journal.getStatus() != GathererJournal.Status.DROPPING_LOOT) {
            return;
        }
        if (this.dropping) {
            Questown.LOGGER.debug("Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(this, entityPos, successTarget);
    }

    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        return Jobs.openInventoryAndStatusScreen(journal.getCapacity(), sp, e, ID);
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

    @Override
    public void itemsChanged(ImmutableList<MCHeldItem> items) {
        Jobs.handleItemChanges(inventory, items);
    }

    public Container getInventory() {
        return inventory;
    }

    public GathererJournal.Status getStatus() {
        return journal.getStatus();
    }

    @Override
    public Function<Void, Void> addStatusListener(StatusListener l) {
        return journal.addStatusListener(l);
    }

    @Override
    public boolean isJumpingAllowed(BlockState onBlock) {
        return true;
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    public GathererJournal.Snapshot<MCHeldItem> getJournalSnapshot() {
        return journal.getSnapshot(MCHeldItem::Air);
    }

    public void initialize(Snapshot<MCHeldItem> journal) {
        this.journal.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
    }

    @Override
    public boolean isInitialized() {
        return journal.isInitialized();
    }

    @Override
    public JobID getId() {
        return ID;
    }

    public void lockSlot(int slot) {
        this.journal.lockSlot(slot);
    }

    public void unlockSlot(int slotIndex) {
        this.journal.unlockSlot(slotIndex);
    }

    @Override
    public ImmutableList<Boolean> getSlotLockStatuses() {
        return this.journal.getSlotLockStatuses();
    }

    public DataSlot getLockSlot(int i) {
        return this.locks.get(i);
    }

    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos position
    ) {
        return this.closeToGate;
    }

    @Override
    public TranslatableComponent getJobName() {
        return new TranslatableComponent("jobs.gatherer");
    }

    @Override
    public boolean addToEmptySlot(MCTownItem mcTownItem) {
        return journal.addItemIfSlotAvailable(new MCHeldItem(mcTownItem));
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
}
