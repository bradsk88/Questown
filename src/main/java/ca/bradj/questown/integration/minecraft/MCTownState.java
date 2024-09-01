package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MCTownState extends TownState<MCContainer, MCTownItem, MCHeldItem, BlockPos, MCTownState> {

    // TODO: Move to the base class?
    private final ArrayList<MCHeldItem> knowledge = new ArrayList<>();

    public MCTownState(
            @NotNull List<VillagerData<MCHeldItem>> villagers,
            @NotNull List<ContainerTarget<MCContainer, MCTownItem>> containers,
            @NotNull ImmutableMap<BlockPos, State> workStates,
            @NotNull ImmutableMap<BlockPos, Integer> workTimers,
            @NotNull List<BlockPos> gates,
            @NotNull ImmutableList<MCHeldItem> knowledge,
            long worldTimeAtSleep
    ) {
        super(villagers, containers, workStates, workTimers, gates, worldTimeAtSleep);
        this.knowledge.addAll(knowledge);
    }

    @Override
    protected MCTownState newTownState(
            ImmutableList<VillagerData<MCHeldItem>> villagers,
            ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers,
            ImmutableMap<BlockPos, State> workStates,
            ImmutableMap<BlockPos, Integer> workTimers,
            ImmutableList<BlockPos> gates,
            long worldTimeAtSleep
    ) {
        MCTownState mcTownState = new MCTownState(
                villagers, containers, workStates, workTimers, gates, ImmutableList.copyOf(knowledge), worldTimeAtSleep
        );
        return mcTownState;
    }

    public MCTownState withKnowledge(MCHeldItem item) {
        MCTownState unchanged = unchanged();
        unchanged.knowledge.add(item);
        return unchanged;
    }

    public ImmutableList<MCHeldItem> knowledge() {
        return ImmutableList.copyOf(knowledge);
    }

    public MCTownState withHungerFilledBy(
            UUID uuid,
            Float up
    ) {
        // FIXME: Implement this
        return unchanged();
    }
}
