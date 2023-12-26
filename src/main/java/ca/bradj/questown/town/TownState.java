package ca.bradj.questown.town;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class TownState<
        C extends ContainerTarget.Container<I>,
        I extends Item<I>,
        H extends HeldItem<H, I> & Item<H>,
        P
        > implements ProductionTimeWarper.FoodRemover<I>, ProductionTimeWarper.Town<I, H>, ImmutableWorkStateContainer<P, TownState<C, I, H, P>> {
    public final @NotNull ImmutableList<VillagerData<H>> villagers;
    public final @NotNull ImmutableList<ContainerTarget<C, I>> containers;
    public final @NotNull ImmutableList<BlockPos> gates;
    public final long worldTimeAtSleep;
    public final ImmutableMap<P, AbstractWorkStatusStore.State> workStates;

    public TownState(
            @NotNull List<VillagerData<H>> villagers,
            @NotNull List<ContainerTarget<C, I>> containers,
            @NotNull ImmutableMap<P, AbstractWorkStatusStore.State> workStates,
            @NotNull List<BlockPos> gates,
            long worldTimeAtSleep
    ) {
        this.villagers = ImmutableList.copyOf(villagers);
        this.containers = ImmutableList.copyOf(containers);
        this.gates = ImmutableList.copyOf(gates);
        this.worldTimeAtSleep = worldTimeAtSleep;
        this.workStates = workStates;
    }

    @Override
    public String toString() {
        return "TownState{" +
                "\n\tvillagers=" + villagers +
                ",\n\tcontainers=" + containers +
                ",\n\tworldTimeAtSleep=" + worldTimeAtSleep +
                "\n}";
    }

    // TODO: TownState should be immutable. Should this be "withFoodRemoved"?
    @Override
    public @Nullable I removeFood() {
        ImmutableList<ContainerTarget<C, I>> containerTargets = ImmutableList.copyOf(containers);
        I out = null;
        for (int i = 0; i < containerTargets.size(); i++) {
            ImmutableList<I> items = containerTargets.get(i).getItems();
            for (int j = 0; j < items.size(); j++) {
                if (items.get(j).isFood()) {
                    ArrayList<I> newItems = new ArrayList<>(items);
                    newItems.set(j, items.get(j).shrink());
                    containers.get(i).setItems(newItems);
                    out = items.get(j);
                    break;
                }
            }
        }
        return out;
    }

    // TODO: TownState should be immutable. Should this be "withItemsDeposited"?
    @Override
    public ImmutableList<H> depositItems(ImmutableList<H> itemsToDeposit) {
        ImmutableList.Builder<H> notDepositedItems = ImmutableList.builder();
        boolean allFull = false;
        for (H item : itemsToDeposit) {
            boolean deposited = false;
            if (!allFull) {
                for (int i = 0; i < containers.size(); i++) {
                    ContainerTarget<C, I> container = containers.get(i);
                    for (int j = 0; j < container.size(); j++) {
                        I containerItem = container.getItem(j);
                        if (containerItem.isEmpty()) {
                            container.setItem(j, item.get());
                            deposited = true;
                            break;
                        }
                    }
                    if (deposited) {
                        break;
                    }
                }
            }
            if (!deposited) {
                allFull = true;
                notDepositedItems.add(item);
            }
        }
        return notDepositedItems.build();
    }

    @Override
    public boolean IsStorageAvailable() {
        for (ContainerTarget<C, I> container : containers) {
            if (container.isFull()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasGate() {
        return !gates.isEmpty();
    }

    @Override
    public TownState<C, I, H, P> setJobBlockState(P bp, AbstractWorkStatusStore.State bs) {
        HashMap<P, AbstractWorkStatusStore.State> m = new HashMap<>(workStates);
        m.put(bp, bs);
        return new TownState<>(
                villagers, containers, ImmutableMap.copyOf(m), gates, worldTimeAtSleep
        );
    }

    @Override
    public TownState<C, I, H, P> setJobBlockStateWithTimer(P bp, AbstractWorkStatusStore.State bs, int ticksToNextState) {
        // FIXME: Implement
        return this;
    }

    @Override
    public TownState<C, I, H, P> clearState(P bp) {
        HashMap<P, AbstractWorkStatusStore.State> m = new HashMap<>(workStates);
        m.remove(bp);
        return new TownState<>(
                villagers, containers, ImmutableMap.copyOf(m), gates, worldTimeAtSleep
        );
    }

    public static final class VillagerData<I extends HeldItem<I, ? extends Item<?>>> {
        public final UUID uuid;
        public final double xPosition, yPosition, zPosition;
        public final Snapshot<I> journal;

        public VillagerData(
                double xPosition,
                double yPosition,
                double zPosition,
                Snapshot<I> journal,
                UUID uuid
        ) {
            this.xPosition = xPosition;
            this.yPosition = yPosition;
            this.zPosition = zPosition;
            this.journal = journal;
            this.uuid = uuid;
        }

        @Override
        public String toString() {
            return "VillagerData{" +
                    "\n\tuuid=" + uuid +
                    ",\n\txPosition=" + xPosition +
                    ",\n\tyPosition=" + yPosition +
                    ",\n\tzPosition=" + zPosition +
                    ",\n\tjournal=" + journal +
                    "\n}";
        }

        public int getCapacity() {
            return 6; // TODO: Be smarter about this?
        }
    }
}
