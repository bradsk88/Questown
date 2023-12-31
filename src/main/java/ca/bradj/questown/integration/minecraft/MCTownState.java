package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.interfaces.TimerHandle;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class MCTownState extends TownState<MCContainer, MCTownItem, MCHeldItem, BlockPos, MCTownState> {


    public MCTownState(
            @NotNull List<VillagerData<MCHeldItem>> villagers,
            @NotNull List<ContainerTarget<MCContainer, MCTownItem>> containers,
            @NotNull ImmutableMap<BlockPos, AbstractWorkStatusStore.State> workStates,
            @NotNull ImmutableMap<BlockPos, Integer> workTimers,
            @NotNull List<BlockPos> gates,
            long worldTimeAtSleep
    ) {
        super(villagers, containers, workStates, workTimers, gates, worldTimeAtSleep);
    }

    @Override
    protected MCTownState newTownState(
            ImmutableList<VillagerData<MCHeldItem>> villagers,
            ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers,
            ImmutableMap<BlockPos, AbstractWorkStatusStore.State> workStates,
            ImmutableMap<BlockPos, Integer> workTimers,
            ImmutableList<BlockPos> gates,
            long worldTimeAtSleep
    ) {
        return new MCTownState(
                villagers, containers, workStates, workTimers, gates, worldTimeAtSleep
        );
    }
}
