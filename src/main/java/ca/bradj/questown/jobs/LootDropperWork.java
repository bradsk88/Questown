package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.declarative.nomc.LootDropperJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class LootDropperWork implements Job<MCHeldItem, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionStatus> {
    private final DeclarativeProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>> delegate = new DeclarativeProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>>() {
        @Override
        public Signals getSignal() {
            // Always drop loot - no matter the time of day
            return Signals.NOON;
        }

        @Override
        protected Map<Integer, Boolean> getSupplyItemStatus() {
            // Cannot work (can only drop). Never has supplies.
            return Map.of();
        }

        @Override
        protected @Nullable WorkPosition<BlockPos> findProductionSpot(ServerLevel level) {
            // Cannot work (can only drop). There is never a work spot.
            return null;
        }

        @Override
        protected WithReason<@Nullable BlockPos> findJobSite(
                RoomsHolder town,
                Function<BlockPos, State> work,
                Predicate<BlockPos> isEmpty,
                Predicate<BlockPos> isJobBlock,
                Random rand
        ) {
            return null;
        }

        @Override
        public Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools(
                TownInterface town,
                Function<BlockPos, State> work,
                Predicate<BlockPos> canClaim
        ) {
            return Map.of();
        }

        @Override
        protected void tick(
                TownInterface town,
                WorkStatusHandle<BlockPos, MCHeldItem> workStatus,
                LivingEntity entity,
                Direction facingPos,
                Supplier<Map<Integer, Collection<MCRoom>>> roomsNeedingIngredientsOrTools,
                IProductionStatusFactory<ProductionStatus> statusFactory
        ) {

        }

        @Override
        public boolean openScreen(
                ServerPlayer sp,
                VisitorMobEntity visitorMobEntity
        ) {
            return false;
        }

        @Override
        public JobName getJobName() {
            return null;
        }

        @Override
        public void initializeStatusFromEntityData(@Nullable String s) {

        }

        @Override
        public JobID getId() {
            return null;
        }

        @Override
        public boolean shouldStandStill() {
            return false;
        }

        @Override
        public Function<Void, Void> addItemInsertionListener(BiConsumer<BlockPos, MCHeldItem> listener) {
            return null;
        }

        @Override
        public Function<Void, Void> addJobCompletionListener(Runnable listener) {
            return null;
        }

        @Override
        public long getTotalDuration() {
            return 0;
        }
    };

    public LootDropperWork(
            UUID ownerUUID,
            int invSize,
            String rootId
    ) {
        this.journal = DeclarativeJobs.journalInitializer(LootDropperJob.newIDForRoot(rootId)).apply(
                invSize, () -> Signals.NOON
        );
    }

    @Override
    public Function<Void, Void> addStatusListener(StatusListener o) {
        return null;
    }

    @Override
    public ProductionStatus getStatus() {
        return ProductionStatus.DROPPING_LOOT;
    }

    @Override
    public void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos
    ) {

    }

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    ) {
        return false;
    }

    @Override
    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity visitorMobEntity
    ) {
        return false;
    }

    @Override
    public Container getInventory() {
        return null;
    }

    @Override
    public SimpleSnapshot<ProductionStatus, MCHeldItem> getJournalSnapshot() {
        return journal.getSnapshot();
    }

    @Override
    public void initialize(Snapshot<MCHeldItem> journal) {

    }

    @Override
    public List<Boolean> getSlotLockStatuses() {
        return List.of();
    }

    @Override
    public DataSlot getLockSlot(int i) {
        return null;
    }

    @Override
    public @Nullable BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        return null;
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> itemz) {

    }

    @Override
    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos blockPos
    ) {
        return false;
    }

    @Override
    public JobName getJobName() {
        return null;
    }

    @Override
    public boolean addToEmptySlot(MCHeldItem mcTownItem) {
        return false;
    }

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {

    }

    @Override
    public String getStatusToSyncToClient() {
        return "";
    }

    @Override
    public boolean isJumpingAllowed(BlockState onBlock) {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return false;
    }

    @Override
    public JobID getId() {
        return null;
    }

    @Override
    public void removeStatusListener(StatusListener l) {

    }

    @Override
    public boolean shouldStandStill() {
        return false;
    }

    @Override
    public boolean canStopWorkingAtAnyTime() {
        return false;
    }

    @Override
    public Function<Void, Void> addItemInsertionListener(BiConsumer<BlockPos, MCHeldItem> listener) {
        return null;
    }

    @Override
    public Function<Void, Void> addJobCompletionListener(Runnable listener) {
        return null;
    }

    @Override
    public long getTotalDuration() {
        return 0;
    }

    @Override
    public BlockPos getLook() {
        return null;
    }

    @Override
    public boolean isWorking() {
        return false;
    }
}
