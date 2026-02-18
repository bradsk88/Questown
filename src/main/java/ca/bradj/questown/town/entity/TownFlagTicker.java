package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.AbstractTownFlagTicker;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.Completable;
import ca.bradj.questown.town.quests.Reward;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public class TownFlagTicker extends AbstractTownFlagTicker<TownFlagTicker.TickData, VisitorMobEntity> {

    @Override
    protected void triggerUnmetNeedsAdvancement(TickData tickData) {
        AdvancementsInit.VISITOR_TRIGGER.triggerForNearestPlayer(
                tickData.level(),
                VisitorTrigger.Triggers.FirstUnmetNeeds,
                tickData.blockEntityPos()
        );
    }

    @Override
    protected ImmutableList<ItemEconomicsData> getTownLevelItemEconomics(TickData tickData) {
        return tickData.entity().getEconomicsHandle().getAggregatedItems(null);
    }

    @Override
    protected boolean applyNewEconomicsData(TickData tickData) {
        return tickData.entity().getEconomicsHandle().update();
    }

    @Override
    protected void tickVillagers(
            TickData tickData,
            Signals signals
    ) {
        TownFlagBlockEntity e = tickData.entity();
        e.villagerHandle.tick(Util.getTick(tickData.level()), signals);
    }

    @Override
    protected void triggerFirstNightAdvancement(TickData tickData) {
        AdvancementsInit.VISITOR_TRIGGER.triggerForNearestPlayer(
                tickData.level(),
                VisitorTrigger.Triggers.FirstNightFall,
                tickData.blockEntityPos()
        );
    }

    @Override
    protected void tickPOIs(TickData tickData) {
        TownFlagBlockEntity e = tickData.entity();
        ServerLevel sl = tickData.level();
        BlockPos blockEntityPos = tickData.blockEntityPos();
        e.pois.tick(sl, blockEntityPos, (int) e.villagerHandle.stream().count());
    }

    @Override
    protected void tickASAPRewards(TickData tickData) {
        tickData.entity().asapRewards.tick();
    }

    @Override
    protected void updateWorkStatuses(TickData tickData) {
        // TODO: Consider moving this logic into the base class
        TownFlagBlockEntity e = tickData.entity();
        ServerLevel sl = tickData.level();
        Collection<MCRoom> allRooms = e.roomsHandle.getAllRoomsIncludingMetaAndFarms();
        e.jobHandle.tick(sl, allRooms, Config.FLAG_TICK_INTERVAL.get());
        e.jobHandles.forEach((k, v) -> v.tick(sl, allRooms, Config.FLAG_TICK_INTERVAL.get()));
    }

    @Override
    protected boolean isFlagTick(TickData tickData) {
        long gameTime = tickData.level().getGameTime();
        long l = gameTime % Config.FLAG_TICK_INTERVAL.get();
        return l == 0;
    }

    @Override
    protected void tickRoomsHandle(TickData tickData) {
        tickData.entity().roomsHandle.tick(tickData.level(), tickData.blockEntityPos());
    }

    @Override
    protected void tickPossibleWorkHandle(TickData tickData) {
        tickData.entity().possibleWork.tick();
    }

    @Override
    protected void tickHealingHandle(TickData tickData) {
        tickData.entity().healing.tick();
    }

    @Override
    protected void tickBiomesHandle(TickData tickData) {
        tickData.entity().biomes.tick();
    }

    @Override
    protected void tickQuests(TickData tickData) {
        tickData.entity().quests.tick(tickData.entity());
    }

    @Override
    protected void tickWorkHandle(TickData tickData) {
        tickData.entity().workHandle.tick(tickData.level());
    }

    @Override
    protected void handleNewStoredData(TickData tickData) {
        TownFlagBlockEntity e = tickData.entity();
        e.possibleWork.invalidate();
        e.quests.processItemQuests(TownContainers.getAllStacks(e, e.getServerLevel()));
        e.quests.processJobChanges(e.getVillagerHandle().getVillagerJobs());
    }

    @Override
    protected boolean updateStoredData(TickData tickData) {
        TownFlagBlockEntity e = tickData.entity();
        ServerLevel sl = tickData.level();
        CompoundTag tag = Compat.getBlockStoredTagData(e);
        boolean stateChanged = e.state.tick(e, tag, sl);

        if ((stateChanged || e.changed) && everScanned) {
            e.writeTownData(tag);
            TownInterface.DebugLogger loger = e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TOWN_STATE_CHANGES);
            e.state.putStateOnTile(tag, e.getUUID(), loger);
            e.changed = false;
            BlockPos blockEntityPos = tickData.blockEntityPos();
            e.setChangedMC(sl, blockEntityPos, tickData.state());
            return true;
        }
        return false;
    }

    @Override
    protected void onMorning(
            TickData tickData,
            Long pop
    ) {
        tickData.entity().morningTick(pop);
    }

    @Override
    protected long getTick(TickData tickData) {
        return Util.getTick(tickData.level());
    }

    @Override
    protected Signals.DayTime getDayTime(TickData tickData) {
        return Util.getDayTime(tickData.level());
    }

    @Override
    protected void notifySubBlocksOfTick(TickData tickData) {
        tickData.entity().subBlocks.parentTick(tickData.level(), tickData.entity().getBlockPos());
    }

    @Override
    protected void storeInactiveState(TickData tickData) {
        BlockState bs = tickData.state();
        bs = bs.setValue(TownFlagBlock.INACTIVE, true);
        tickData.level.setBlockAndUpdate(tickData.blockEntityPos, bs);
    }

    @Override
    protected boolean allPlayersLeftArea(
            TickData tickData,
            boolean playersNearbyOnPrevTick
    ) {
        ServerLevel level = tickData.level();
        BlockPos blockEntityPos = tickData.blockEntityPos();
        Player nearestPlayer = level.getNearestPlayer(
                blockEntityPos.getX(),
                blockEntityPos.getY(),
                blockEntityPos.getZ(),
                -1,
                null
        );
        if (nearestPlayer == null) {
            logStop(blockEntityPos, "No players are found on server");
            return true;
        }

        BlockPos worldPosition = tickData.entity().getBlockPos();
        int distToPlayer = (int) nearestPlayer.blockPosition().distSqr(worldPosition);
        if (distToPlayer <= Config.TOWN_TICK_RADIUS.get()) {
            return false;
        }
        if (playersNearbyOnPrevTick) {
            String msg = "Closest player is %d blocks away";
            logStop(blockEntityPos, String.format(msg, distToPlayer));
        }
        return true;
    }

    private void logStop(
            BlockPos blockEntityPos,
            String reason
    ) {
        QT.FLAG_LOGGER.info(
                "Town flag at {} stopped ticking because: {} - Distance Limit: {}",
                blockEntityPos,
                reason,
                Config.TOWN_TICK_RADIUS.get()
        );
    }

    @Override
    protected void addQuests(
            TickData tickData,
            VisitorMobEntity v
    ) {
        QT.FLAG_LOGGER.debug("No quests found. Adding a batch for {}", v.getVUID());
        TownFlagBlockEntity e = tickData.entity();
        e.questsHandle.addBatchOfQuestsForVisitor(v.getVUID());
        e.setChanged();
    }

    @Override
    protected ImmutableList<Pair<Completable, Reward>> getAllQuestsWithRewards(TickData e) {
        return e.entity().getAllQuestsWithRewards().stream()
                .map(v -> new Pair<>((Completable) v.getKey(), (Reward) v.getValue()))
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    protected ImmutableList<Reward> getMorningRewards(TickData e) {
        return e.entity.morningRewards.children.stream().map(v -> (Reward) v).collect(ImmutableList.toImmutableList());
    }

    @Override
    protected ImmutableList<VisitorMobEntity> getVillagers(TickData o) {
        return o.entity.villagerHandle.stream().filter(Objects::nonNull).collect(ImmutableList.toImmutableList());
    }

    @Override
    protected boolean runAnyInitializers(TickData tickData) {
        TownFlagBlockEntity e = tickData.entity;
        if (e.initializers.isEmpty()) {
            return false;
        }
        int left = e.initializers.size() - 1;
        Function<TownFlagBlockEntity, Boolean> initr = e.initializers.remove();
        QT.FLAG_LOGGER.info("Running initializer ({} left) @ {}", left, e.getBlockPos());
        if (initr.apply(e)) {
            return true;
        }
        QT.FLAG_LOGGER.info("Initializer will be tried again later @ {}", e.getBlockPos());
        e.initializers.add(initr);
        return true;
    }

    public void tick(
            Level level,
            BlockPos blockEntityPos,
            BlockState state,
            TownFlagBlockEntity e
    ) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        super.tick(new TickData(sl, blockEntityPos, state, e));
    }

    public record TickData(ServerLevel level, BlockPos blockEntityPos, BlockState state, TownFlagBlockEntity entity) {
        public static TickData fromFlag(TownFlagBlockEntity e) {
            return new TickData(e.getServerLevel(), e.getBlockPos(), e.getBlockState(), e);
        }
    }
}
