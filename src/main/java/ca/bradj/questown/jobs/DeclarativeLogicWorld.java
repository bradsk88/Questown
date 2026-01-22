package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.declarative.MCExtra;
import ca.bradj.questown.jobs.declarative.PostDropHook;
import ca.bradj.questown.jobs.declarative.PreMaxTicksJobChangeHook;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static ca.bradj.questown.mc.Util.info;

public class DeclarativeLogicWorld<EXTRA, TOWN, POS> implements JobLogic.JLWorld<EXTRA, TOWN, POS> {


    private final TOWN town;

    public DeclarativeLogicWorld(TOWN town) {
        this.town = town;
    }

    //
//    private JobLogic.JLWorld<MCExtra, Boolean, POS> asLogicWorld(
//            MCExtra extra,
//            WorkStatusHandle<POS, MCHeldItem> work,
//            VisitorMobEntity entity,
//            EntityCurrentJobSite<MCRoom> entityCurrentJobSite,
//            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, POS> roomsNeedingIngredientsOrTools
//    ) {
//        DeclarativeJob self = this;
//        TownInterface town = extra.town();
//        return new JobLogic.JLWorld<>() {
    @Override
    public void changeJob(JobID id) {
        UnsafeVillagerData data = town.getVillagerHandle().getUnprotectedDataHandle(entity.getVUID());
        PreMaxTicksJobChangeHook.run(specialGlobalRules, data);
        town.getVillagerHandle().changeJobForVillager(ownerUUID, id, false);
    }

    @Override
    public void changeToNextJob() {
        town.changeJobForVisitorFromBoard(ownerUUID, getId());
    }

    @Override
    public boolean setWorkLeftAtFreshState(int workRequiredAtFirstState) {
        ServerLevel sl = town.getServerLevel();
        boolean didIt = false;
        for (RoomRecipeMatch<MCRoom> room : town.getRoomHandle().getRoomsMatching(SpecialQuests.CLINIC)) {
            Map<Integer, Collection<WorkPosition<POS>>> spots = DeclarativeJob.this.listAllWorkSpots(
                    work::getJobBlockState,
                    new EntityCurrentJobSite<>(room.room, false),
                    bp -> isValidWalkTarget(town, bp),
                    bp -> location.shouldInitializeWorkState().test(info(sl), bp),
                    bp -> {
                        town.getDebugLogger(QT.JOB_LOGGER, DebugLogArgument.VILLAGER_NAVIGATION).log(
                                "choosing to approach job block from random side"
                        );
                        return bp.relative(Compat.getRandomHorizontal(sl));
                    }
            );
            for (WorkPosition<POS> p : UtilClean.getOrDefault(spots, 0, ImmutableList.of())) {
                work.setJobBlockState(p.jobBlock(), State.fresh().setWorkLeft(workRequiredAtFirstState));
                didIt = true;
            }
        }
        return didIt;
    }

    @Override
    public WorkPosition<POS> getWorkSpot() {
        return world.getWorkSpot();
    }

    @Override
    public Map<Integer, Collection<WorkPosition<POS>>> listAllWorkSpots() {
        ServerLevel sl = town.getServerLevel();
        if (sl == null) {
            return ImmutableMap.of();
        }
        return self.listAllWorkSpots(
                work::getJobBlockState,
                entityCurrentJobSite,
                bp -> isValidWalkTarget(town, bp),
                bp -> isJobBlock(bp),
                bp -> bp.relative(Compat.getRandomHorizontal(sl))
        );
    }

    @Override
    public boolean tryGrabbingInsertedSupplies() {
        return world.tryGrabbingInsertedSupplies(extra);
    }

    @Override
    public void clearInsertedSupplies() {
        world.clearInsertedSupplies(extra);
    }

    @Override
    public void registerUnmetNeeds(
            ProductionStatus status,
            @Nullable POS workspot,
            int timesInserted
    ) {
        world.registerUnmetNeeds(extra, workspot, timesInserted);
    }

    @Override
    public void registerUnmetRooms() {
        world.registerUnmetRooms(extra);
    }

    @Override
    public int timesInserted() {
        return world.timesInserted(extra);
    }

    @Override
    public AbstractWorldInteraction<MCExtra, POS, ?, ?, Boolean> getHandle() {
        return world;
    }

    @Override
    public boolean tryDropLoot() {
        ImmutableList<MCHeldItem> itemsBeforeDrop = journal.getItems();
        boolean result = self.tryDropLoot(Util.getTick(town.getServerLevel()), entity.blockPosition());
        ImmutableList<MCHeldItem> itemsAfterDrop = journal.getItems();
        if (result) {
            PostDropHook.run(
                    town,
                    specialGlobalRules,
                    town.getServerLevel(),
                    successTarget.getPOS(),
                    itemsBeforeDrop,
                    itemsAfterDrop,
                    work::clearState
            );
        }
        return result;
    }

    @Override
    public void tryGetSupplies() {
        if (logic.isWrappingUp()) {
            return;
        }
        self.tryGetSupplies(
                extra.town(),
                roomsNeedingIngredientsOrTools,
                entity.blockPosition(),
                Util.getTick(town.getServerLevel())
        );
    }

    @Override
    public void setLookTarget(POS position) {
        self.setLookTarget(position);
    }

    @Override
    public void registerHeldItemsAsFoundLoot() {
        town.getKnowledgeHandle().registerFoundLoots(journal.getItems());
    }
}
