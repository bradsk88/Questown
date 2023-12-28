package ca.bradj.questown.town;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public abstract class TownState<
        C extends ContainerTarget.Container<I>,
        I extends Item<I>,
        H extends HeldItem<H, I> & Item<H>,
        P, SELF
        > implements ProductionTimeWarper.FoodRemover<I>, ProductionTimeWarper.Town<I, H>, ImmutableWorkStateContainer<P, SELF> {
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
    public AbstractWorkStatusStore.@Nullable State getJobBlockState(P bp) {
        return workStates.get(bp);
    }

    @Override
    public ImmutableMap<P, AbstractWorkStatusStore.State> getAll() {
        return ImmutableMap.copyOf(workStates);
    }

    @Override
    public SELF setJobBlockState(P bp, AbstractWorkStatusStore.State bs) {
        HashMap<P, AbstractWorkStatusStore.State> m = new HashMap<>(workStates);
        m.put(bp, bs);
        return newTownState(
                villagers, containers, ImmutableMap.copyOf(m), gates, worldTimeAtSleep
        );
    }

    protected abstract SELF newTownState(ImmutableList<VillagerData<H>> villagers, ImmutableList<ContainerTarget<C, I>> containers, ImmutableMap<P, AbstractWorkStatusStore.State> pStateImmutableMap, ImmutableList<BlockPos> gates, long worldTimeAtSleep);

    @Override
    public SELF setJobBlockStateWithTimer(P bp, AbstractWorkStatusStore.State bs, int ticksToNextState) {
        // FIXME: Implement
        return newTownState(villagers, containers, workStates, gates, worldTimeAtSleep);
    }

    @Override
    public SELF clearState(P bp) {
        HashMap<P, AbstractWorkStatusStore.State> m = new HashMap<>(workStates);
        m.remove(bp);
        return newTownState(
                villagers, containers, ImmutableMap.copyOf(m), gates, worldTimeAtSleep
        );
    }


    public SELF withVillagerData(
            int index, VillagerData<H> data
    ) {
        ArrayList<VillagerData<H>> vilz = new ArrayList<>(villagers);
        vilz.set(index, data);
        return newTownState(
                ImmutableList.copyOf(vilz),
                containers,
                workStates,
                gates,
                worldTimeAtSleep
        );
    }

    public static final class VillagerData<I extends HeldItem<I, ? extends Item<?>>> {
        public final UUID uuid;
        public final double xPosition, yPosition, zPosition;
        public final ImmutableSnapshot<I, ?> journal;

        public VillagerData(
                double xPosition,
                double yPosition,
                double zPosition,
                ImmutableSnapshot<I, ?> journal,
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

        public VillagerData<I> withSetItem(int itemIndex, I item) {

            return new VillagerData<>(
                    xPosition, yPosition, zPosition,
                    (ImmutableSnapshot) journal.withSetItem(itemIndex, item), uuid
            );
        }
    }
}
