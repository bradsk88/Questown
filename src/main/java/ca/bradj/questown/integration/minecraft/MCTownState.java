package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MCTownState extends TownState<MCContainer, MCTownItem, MCHeldItem> {

    public MCTownState(
            @NotNull List<VillagerData<MCHeldItem>> villagers,
            @NotNull List<ContainerTarget<MCContainer, MCTownItem>> containers,
            @NotNull List<BlockPos> gates,
            long worldTimeAtSleep
    ) {
        super(villagers, containers, gates, worldTimeAtSleep);
    }
}
