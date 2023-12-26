package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.interfaces.TimerHandle;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class MCTownState extends TownState<MCContainer, MCTownItem, MCHeldItem, BlockPos> {


    public MCTownState(
            @NotNull List<VillagerData<MCHeldItem>> villagers,
            @NotNull List<ContainerTarget<MCContainer, MCTownItem>> containers,
            @NotNull ImmutableMap<BlockPos, AbstractWorkStatusStore.State> workStates,
            @NotNull List<BlockPos> gates,
            long worldTimeAtSleep
    ) {
        super(villagers, containers, workStates, gates, worldTimeAtSleep);
    }

    public TimerHandle<MCRoom, ServerLevel> asTimerHandle() {
        return new TimerHandle<MCRoom, ServerLevel>() {
            @Override
            public void tick(ServerLevel serverLevel, Collection<MCRoom> roomsToScanForChanges) {
                // FIXME: Store and manage timers in TownState
            }
        };
    }
}
