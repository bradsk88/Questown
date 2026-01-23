package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class DeclarativeLogicWorld<POS, HELD_ITEM, ROOM extends Room> implements JobLogic.JLWorld<Void, Void, POS> {

    /**
     * Interface for all dependencies needed by DeclarativeLogicWorld.
     * Implementations provide MC-specific or test implementations.
     */
    public interface WorldDeps<POS, HELD_ITEM, ROOM extends Room> {
        // Entity operations
        POS getEntityBlockPosition();
        Object getEntityVUID();

        // Owner info
        UUID getOwnerUUID();
        JobID getJobId();

        // Special rules
        ImmutableList<String> getSpecialGlobalRules();

        // Town operations - villager handle
        void changeJobForVillager(UUID ownerUUID, JobID id, boolean flag);
        void changeJobForVisitorFromBoard(UUID ownerUUID, JobID currentJobId);
        Object getVillagerData(Object vuid);
        void runPreMaxTicksJobChangeHook(ImmutableList<String> rules, Object villagerData);

        // Town operations - room handle
        Collection<ROOM> getRoomsMatchingClinic();

        // Town operations - knowledge handle
        void registerFoundLoots(ImmutableList<HELD_ITEM> items);

        // Town operations - server level
        boolean hasServerLevel();
        POS getRandomHorizontalFrom(POS bp);
        void logDebug(String message);

        // Work status operations
        State getJobBlockState(POS pos);
        void setJobBlockState(POS pos, State state);
        void clearWorkState(POS pos);

        // World interaction operations
        WorkPosition<POS> getWorldWorkSpot();
        boolean tryGrabbingInsertedSupplies();
        void clearInsertedSupplies();
        void registerUnmetNeeds(POS workspot, int timesInserted);
        void registerUnmetRooms();
        int timesInserted();
        AbstractWorldInteraction<?, POS, ?, ?, ?> getWorldHandle();

        // Job operations
        Map<Integer, Collection<WorkPosition<POS>>> listAllWorkSpots(
                Function<POS, State> getBlockState,
                @Nullable EntityCurrentJobSite<ROOM> jobSite,
                Predicate<POS> isValidWalkTarget,
                Predicate<POS> isJobBlock,
                Function<POS, POS> getRandomAdjacent
        );
        boolean tryDropLoot(long tick, POS entityBlockPos);
        void tryGetSupplies(
                RoomsNeedingVillagerInput<ROOM, ?, POS> roomsNeedingInput,
                POS entityBlockPos,
                long currentTick
        );
        void setLookTarget(POS position);

        // Location operations
        boolean shouldInitializeWorkState(POS bp);
        boolean isValidWalkTarget(POS bp);
        boolean isJobBlock(POS bp);

        // Logic state
        boolean isLogicWrappingUp();

        // Journal operations
        ImmutableList<HELD_ITEM> getJournalItems();

        // Current job site
        @Nullable EntityCurrentJobSite<ROOM> getEntityCurrentJobSite();

        // Rooms needing input
        RoomsNeedingVillagerInput<ROOM, ?, POS> getRoomsNeedingInput();

        // Success target
        @Nullable POS getSuccessTargetPOS();

        // Post drop hook
        void runPostDropHook(
                POS successTargetPos,
                ImmutableList<HELD_ITEM> itemsBeforeDrop,
                ImmutableList<HELD_ITEM> itemsAfterDrop,
                Consumer<POS> clearState
        );

        // Get current tick
        long getCurrentTick();
    }

    private final WorldDeps<POS, HELD_ITEM, ROOM> deps;

    public DeclarativeLogicWorld(WorldDeps<POS, HELD_ITEM, ROOM> deps) {
        this.deps = deps;
    }

    @Override
    public void changeJob(JobID id) {
        Object data = deps.getVillagerData(deps.getEntityVUID());
        deps.runPreMaxTicksJobChangeHook(deps.getSpecialGlobalRules(), data);
        deps.changeJobForVillager(deps.getOwnerUUID(), id, false);
    }

    @Override
    public void changeToNextJob() {
        deps.changeJobForVisitorFromBoard(deps.getOwnerUUID(), deps.getJobId());
    }

    @Override
    public boolean setWorkLeftAtFreshState(int workRequiredAtFirstState) {
        if (!deps.hasServerLevel()) {
            return false;
        }
        boolean didIt = false;
        for (ROOM room : deps.getRoomsMatchingClinic()) {
            Map<Integer, Collection<WorkPosition<POS>>> spots = deps.listAllWorkSpots(
                    deps::getJobBlockState,
                    new EntityCurrentJobSite<>(room, false),
                    deps::isValidWalkTarget,
                    deps::shouldInitializeWorkState,
                    bp -> {
                        deps.logDebug("choosing to approach job block from random side");
                        return deps.getRandomHorizontalFrom(bp);
                    }
            );
            for (WorkPosition<POS> p : spots.getOrDefault(0, ImmutableList.of())) {
                deps.setJobBlockState(p.jobBlock(), State.fresh().setWorkLeft(workRequiredAtFirstState));
                didIt = true;
            }
        }
        return didIt;
    }

    @Override
    public WorkPosition<POS> getWorkSpot() {
        return deps.getWorldWorkSpot();
    }

    @Override
    public Map<Integer, Collection<WorkPosition<POS>>> listAllWorkSpots() {
        if (!deps.hasServerLevel()) {
            return Map.of();
        }
        return deps.listAllWorkSpots(
                deps::getJobBlockState,
                deps.getEntityCurrentJobSite(),
                deps::isValidWalkTarget,
                deps::isJobBlock,
                deps::getRandomHorizontalFrom
        );
    }

    @Override
    public boolean tryGrabbingInsertedSupplies() {
        return deps.tryGrabbingInsertedSupplies();
    }

    @Override
    public void clearInsertedSupplies() {
        deps.clearInsertedSupplies();
    }

    @Override
    public void registerUnmetNeeds(
            ProductionStatus status,
            @Nullable POS workspot,
            int timesInserted
    ) {
        deps.registerUnmetNeeds(workspot, timesInserted);
    }

    @Override
    public void registerUnmetRooms() {
        deps.registerUnmetRooms();
    }

    @Override
    public int timesInserted() {
        return deps.timesInserted();
    }

    @SuppressWarnings("unchecked")
    @Override
    public AbstractWorldInteraction<Void, POS, ?, ?, Void> getHandle() {
        return (AbstractWorldInteraction<Void, POS, ?, ?, Void>) deps.getWorldHandle();
    }

    @Override
    public boolean tryDropLoot() {
        ImmutableList<HELD_ITEM> itemsBeforeDrop = deps.getJournalItems();
        boolean result = deps.tryDropLoot(deps.getCurrentTick(), deps.getEntityBlockPosition());
        ImmutableList<HELD_ITEM> itemsAfterDrop = deps.getJournalItems();
        if (result) {
            POS successTargetPos = deps.getSuccessTargetPOS();
            if (successTargetPos != null) {
                deps.runPostDropHook(
                        successTargetPos,
                        itemsBeforeDrop,
                        itemsAfterDrop,
                        deps::clearWorkState
                );
            }
        }
        return result;
    }

    @Override
    public void tryGetSupplies() {
        if (deps.isLogicWrappingUp()) {
            return;
        }
        deps.tryGetSupplies(
                deps.getRoomsNeedingInput(),
                deps.getEntityBlockPosition(),
                deps.getCurrentTick()
        );
    }

    @Override
    public void setLookTarget(POS position) {
        deps.setLookTarget(position);
    }

    @Override
    public void registerHeldItemsAsFoundLoot() {
        deps.registerFoundLoots(deps.getJournalItems());
    }
}
