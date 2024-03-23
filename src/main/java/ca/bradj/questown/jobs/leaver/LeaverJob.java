package ca.bradj.questown.jobs.leaver;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public abstract class LeaverJob implements Job<MCHeldItem, GathererJournal.Snapshot<MCHeldItem>, GathererJournal.Status> {
    private final List<LockSlot> locks = new ArrayList<>();
    protected final @Nullable TownInterface town;
    protected final UUID ownerUUID;
    protected final SimpleContainer inventory;
    protected final GathererJournal<MCTownItem, MCHeldItem> journal;

    @Nullable BlockPos gateTarget;
    @Nullable ContainerTarget<MCContainer, MCTownItem> foodTarget;
    @Nullable ContainerTarget<MCContainer, MCTownItem> successTarget;

    private boolean closeToGate;
    private Signals signal;
    private Signals passedThroughGate = Signals.UNDEFINED;

    private boolean dropping;
    private final Jobs.LootDropper<MCHeldItem> dropper = new LeaverLootDropper(this);
    private boolean lootRegistered = true;

    public LeaverJob(
            TownInterface town,
            int inventoryCapacity,
            UUID ownerUUID
    ) {
        if (town != null && !town.getServerLevel().isClientSide()) {
            this.town = town;
        } else {
            this.town = null;
        }
        this.ownerUUID = ownerUUID;

        SimpleContainer sc = new SimpleContainer(inventoryCapacity) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.inventory = sc;
        sc.addListener(new LeaverContainerListener(this));

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

        this.journal = constructorJournal(() -> this.signal, tsp, inventoryCapacity);
        journal.addItemsListener(new LeaverJournalListener(this));

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, journal));
        }
    }

    protected abstract GathererJournal<MCTownItem, MCHeldItem> constructorJournal(
            SignalSource signalSource,
            GathererStatuses.TownStateProvider tsp,
            int inventoryCapacity
    );

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {
        GathererJournal.Status z = GathererJournal.getStatusFromEntityData(s);
        this.journal.initializeStatus(z);
    }

    @Override
    public void tick(
            TownInterface town,
            LivingEntity entity,
            Direction relative
    ) {
        if (town == null || town.getServerLevel() == null) {
            return;
        }
        processSignal(town.getServerLevel(), this, this::getLoot);

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
        town.getKnowledgeHandle().registerFoundLoots(journal.getItems());
        tryDropLoot(entityPos);
        tryTakeFood(entityPos);
    }

    protected abstract Collection<MCHeldItem> getLoot(GathererJournal.Tools tools);

    private void tryDropLoot(
            BlockPos entityPos
    ) {
        // TODO: move to journal?
        if (journal.getStatus() != GathererJournal.Status.DROPPING_LOOT) {
            return;
        }
        if (this.dropping) {
            QT.JOB_LOGGER.debug("Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(dropper, entityPos, successTarget);

        if (this.dropping && !journal.hasAnyLootToDrop() && town != null) {
            town.changeJobForVisitor(ownerUUID, WorkSeekerJob.getIDForRoot(getId()));
        }
    }

    private void tryTakeFood(BlockPos entityPos) {
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
                QT.JOB_LOGGER.debug("Gatherer is taking {} from {}", mcTownItem, foodTarget);
                journal.addItem(MCHeldItem.fromTown(mcTownItem));
                foodTarget.container.removeItem(i, 1);
                break;
            }
        }
    }

    private boolean isCloseToFood(
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

    private static void processSignal(
            Level level,
            LeaverJob e,
            GathererJournal.LootProvider<MCTownItem, MCHeldItem> loot
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

        e.signal = Signals.fromDayTime(Util.getDayTime(level));
        e.journal.tick((tools) -> {
            e.lootRegistered = false;
            return loot.getLoot(tools);
        });
    }

    @Override
    public BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        BlockPos enterExitPos = town.getEnterExitPos(); // TODO: Smarter logic? Town gate?
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

        QT.JOB_LOGGER.debug("Visitor is searching for gate");
        if (this.gateTarget != null) {
            QT.JOB_LOGGER.debug("Located gate at {}", this.gateTarget);
            return this.gateTarget;
        } else {
            this.gateTarget = town.getClosestWelcomeMatPos(entityPos);
        }
        if (this.gateTarget != null) {
            QT.JOB_LOGGER.debug("Located gate at {}", this.gateTarget);
            return this.gateTarget;
        } else {
            QT.JOB_LOGGER.debug("No gate exists in town");
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

        QT.JOB_LOGGER.debug("Visitor is searching for food");
        if (this.foodTarget != null) {
            if (!this.foodTarget.hasItem(MCTownItem::isFood)) {
                this.foodTarget = town.findMatchingContainer(MCTownItem::isFood);
            }
        } else {
            this.foodTarget = town.findMatchingContainer(MCTownItem::isFood);
        }
        if (this.foodTarget != null) {
            QT.JOB_LOGGER.debug("Located food at {}", this.foodTarget.getPosition());
            return Positions.ToBlock(this.foodTarget.getInteractPosition(), this.foodTarget.getYPosition());
        } else {
            QT.JOB_LOGGER.debug("No food exists in town");
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

    @Override
    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos position
    ) {
        return this.closeToGate;
    }

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPos
    ) {
        if (passedThroughGate != Signals.UNDEFINED && passedThroughGate.equals(signal)) {
            return true;
        }
        passedThroughGate = Signals.UNDEFINED;
        if (journal.getStatus() == GathererJournal.Status.GATHERING) {
            boolean veryCloseTo = Jobs.isVeryCloseTo(entityPos, town.getEnterExitPos());
            if (veryCloseTo) {
                this.passedThroughGate = signal;
                return true;
            }
            return false;
        }
        return journal.getStatus().isReturning();
    }

    @Override
    public String getStatusToSyncToClient() {
        return getStatus().name();
    }

    @Override
    public List<Boolean> getSlotLockStatuses() {
        return journal.getSlotLockStatuses();
    }

    public DataSlot getLockSlot(int i) {
        return this.locks.get(i);
    }
}
