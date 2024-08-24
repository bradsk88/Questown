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

public class LootDropperWork implements
        Job<MCHeldItem, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionStatus> {
    private final DeclarativeProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>> delegate;

    public LootDropperWork(
            UUID ownerUUID,
            int invSize,
            String rootId
    ) {
        JobID id = LootDropperJob.newIDForRoot(rootId);
        delegate
                = new DeclarativeProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>>(
                        ownerUUID,
                invSize,

        ) {
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
                // Cannot work (can only drop). There is never a jobsite.
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
                // Everything the dropper needs gets calculated in getTarget()
            }

            @Override
            public JobName getJobName() {
                return new JobName("dropper");
            }

            @Override
            public void initializeStatusFromEntityData(@Nullable String s) {
                journal.initializeStatus(ProductionStatus.DROPPING_LOOT);
            }

            @Override
            public JobID getId() {
                return id;
            }

            @Override
            public boolean shouldStandStill() {
                return false;
            }

            @Override
            public Function<Void, Void> addItemInsertionListener(BiConsumer<BlockPos, MCHeldItem> listener) {
                return (v) -> null;
            }

            @Override
            public Function<Void, Void> addJobCompletionListener(Runnable listener) {
                return (v) -> null;
            }

            @Override
            public long getTotalDuration() {
                return 0;
            }
        };
    }

}
