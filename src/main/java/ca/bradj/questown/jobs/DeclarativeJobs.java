package ca.bradj.questown.jobs;

import ca.bradj.questown.blocks.BreadOvenBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.production.ProductionJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatuses;
import ca.bradj.questown.roomrecipes.Spaces;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Warper;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public class DeclarativeJobs {
    public static <INGREDIENT, ITEM extends Item<ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, ITEM>> Map<Integer, Boolean> getSupplyItemStatus(
            Collection<HELD_ITEM> journalItems,
            ImmutableMap<Integer, INGREDIENT> ingredientsRequiredAtStates,
            ImmutableMap<Integer, INGREDIENT> toolsRequiredAtStates,
            BiPredicate<INGREDIENT, HELD_ITEM> matchFn
    ) {
        HashMap<Integer, Boolean> b = new HashMap<>();
        BiConsumer<Integer, INGREDIENT> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean has = journalItems.stream().anyMatch(v -> matchFn.test(ingr, v));
            if (!b.getOrDefault(state, false)) {
                b.put(state, has);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        return ImmutableMap.copyOf(b);
    }

    public static Warper<MCTownState> warper(
            MCTownStateWorldInteraction wi,
            boolean prioritizeExtraction
    ) {
        return (inState, currentTick, ticksPassed, villagerNum) -> {
            BlockPos fakePos = new BlockPos(villagerNum, villagerNum, villagerNum);

            MCTownState outState = inState;

            AbstractWorkStatusStore.State state = outState.workStates.get(fakePos);
            if (state == null) {
                outState = outState.setJobBlockState(fakePos, AbstractWorkStatusStore.State.fresh());
            }

            long start = currentTick;
            long max = currentTick + ticksPassed;

            ProductionStatus status = ProductionStatus.FACTORY.idle();

            // TODO[ASAP]: Factor in timers and "walk time"
            for (long i = start; i <= max; i += wi.interval) {
                state = outState.workStates.get(fakePos);
                wi.injectTicks(wi.interval);
                MCRoom fakeRoom = Spaces.metaRoomAround(fakePos, 1);
                status = ProductionStatuses.getNewStatusFromSignal(
                        status, Signals.fromGameTime(i),
                        wi.asInventory(outState, villagerNum),
                        wi.asTownJobs(
                                outState.workStates.get(fakePos),
                                fakeRoom,
                                outState.containers
                        ),
                        DeclarativeJobs.alwaysInRoom(fakeRoom),
                        DeclarativeJob.STATUS_FACTORY, prioritizeExtraction
                );
                MCTownState affectedState = null;
                if (status != null && status.isWorkingOnProduction()) {
                    affectedState = wi.tryWorking(outState, new WorkSpot<>(fakePos, state.processingState(), 1));
                } else if (status.isDroppingLoot()) {
                    affectedState = wi.simulateDropLoot(outState, status);
                } else if (status.isCollectingSupplies()) {
                    affectedState = wi.simulateCollectSupplies();
                }
                // FIXME: Handle the other statuses

                if (affectedState != null) {
                    outState = affectedState;
                }
            }

            return outState;
        };
    }

    private static EntityLocStateProvider<MCRoom> alwaysInRoom(
            MCRoom fakeRoom
    ) {
        return new EntityLocStateProvider<MCRoom>() {
            @Override
            public @Nullable MCRoom getEntityCurrentJobSite() {
                return fakeRoom;
            }
        };
    }
}
