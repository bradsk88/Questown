package ca.bradj.questown.town;

import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.ImmutableSnapshot;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.ProductionTimeWarper;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public abstract class TownState<
        C extends ContainerTarget.Container<I>,
        I extends Item<I>,
        H extends HeldItem<H, I> & Item<H>,
        P, SELF
        > implements
        ProductionTimeWarper.Town<I, H>,
        ImmutableWorkStateContainer<P, SELF>,
        VillagerDataCollectionHolder<H>
{
    public final @NotNull ImmutableList<VillagerData<H>> villagers;
    public final @NotNull ImmutableList<ContainerTarget<C, I>> containers;
    public final @NotNull ImmutableList<P> gates;
    public final long worldTimeAtSleep;
    public final ImmutableMap<P, AbstractWorkStatusStore.State> workStates;
    public final ImmutableMap<P, Integer> workTimers;

    public TownState(
            @NotNull List<VillagerData<H>> villagers,
            @NotNull List<ContainerTarget<C, I>> containers,
            @NotNull ImmutableMap<P, AbstractWorkStatusStore.State> workStates,
            @NotNull ImmutableMap<P, Integer> workTimers,
            @NotNull List<P> gates,
            long worldTimeAtSleep
    ) {
        this.villagers = ImmutableList.copyOf(villagers);
        this.containers = ImmutableList.copyOf(containers);
        this.gates = ImmutableList.copyOf(gates);
        this.worldTimeAtSleep = worldTimeAtSleep;
        this.workStates = workStates;
        this.workTimers = workTimers;
    }

    @Override
    public VillagerData<H> getVillager(int villagerIndex) {
        return villagers.get(villagerIndex);
    }

    @Override
    public String toString() {
        return "TownState{" +
                "\n\tvillagers=" + villagers +
                ",\n\tcontainers=" + containers +
                ",\n\tstates=" + workStates +
                ",\n\ttimers=" + workTimers +
                ",\n\tworldTimeAtSleep=" + worldTimeAtSleep +
                "\n}";
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
                villagers, containers, ImmutableMap.copyOf(m), workTimers, gates, worldTimeAtSleep
        );
    }

    protected abstract SELF newTownState(
            ImmutableList<VillagerData<H>> villagers,
            ImmutableList<ContainerTarget<C, I>> containers,
            ImmutableMap<P, AbstractWorkStatusStore.State> workStates,
            ImmutableMap<P, Integer> workTimers,
            ImmutableList<P> gates,
            long worldTimeAtSleep
    );

    @Override
    public SELF setJobBlockStateWithTimer(P bp, AbstractWorkStatusStore.State bs, int ticksToNextState) {
        HashMap<P, AbstractWorkStatusStore.State> m = new HashMap<>(workStates);
        m.put(bp, bs);
        HashMap<P, Integer> m2 = new HashMap<>(workTimers);
        m2.put(bp, ticksToNextState);
        return newTownState(
                villagers, containers, ImmutableMap.copyOf(m), ImmutableMap.copyOf(m2), gates, worldTimeAtSleep
        );
    }

    @Override
    public SELF clearState(P bp) {
        HashMap<P, AbstractWorkStatusStore.State> m = new HashMap<>(workStates);
        m.remove(bp);
        return newTownState(
                villagers, containers, ImmutableMap.copyOf(m), workTimers, gates, worldTimeAtSleep
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
                workTimers,
                gates,
                worldTimeAtSleep
        );
    }

    public Map.Entry<SELF, I> withContainerItemRemoved(Predicate<I> itemCheck) {
        ImmutableList.Builder<ContainerTarget<C, I>> b = ImmutableList.builder();
        I removed = null;
        for (ContainerTarget<C, I> container : containers) {
            @Nullable Map.Entry<ContainerTarget<C, I>, I> wRem = container.withItemRemoved(itemCheck);
            if (wRem != null) {
                b.add(wRem.getKey());
                removed = wRem.getValue();
            } else {
                b.add(container);
            }
        }
        return new AbstractMap.SimpleEntry<>(newTownState(
                villagers, b.build(), workStates, workTimers, gates, worldTimeAtSleep
        ), removed);
    }

    public SELF withTimerReducedBy(P bp, int stepInterval) {
        // TODO: Take "next step work" and "next step time" as inputs
        if (workTimers.get(bp) == null || workTimers.get(bp) == 0) {
            return unchanged();
        }
        HashMap<P, AbstractWorkStatusStore.State> m = new HashMap<>(workStates);
        HashMap<P, Integer> m2 = new HashMap<>(workTimers);
        m2.compute(bp, (k, v) -> v == null ? 0 : Math.max(0, v - stepInterval));
        if (m2.get(bp) <= 0) {
            m.compute(bp, (k, v) -> (v == null ? AbstractWorkStatusStore.State.fresh() : v).incrProcessing());
        }
        return newTownState(
                villagers, containers, ImmutableMap.copyOf(m), ImmutableMap.copyOf(m2), gates, worldTimeAtSleep
        );
    }

    protected final SELF unchanged() {
        return newTownState(villagers, containers, workStates, workTimers, gates, worldTimeAtSleep);
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

        public @Nullable VillagerData<I> withAddedItem(I value) {
            Optional<I> first = journal.items().stream().filter(Item::isEmpty).findFirst();
            if (first.isEmpty()) {
                return null;
            }
            int idx = journal.items().indexOf(first.get());
            return new VillagerData<>(
                    xPosition, yPosition, zPosition,
                    (ImmutableSnapshot) journal.withSetItem(idx, value), uuid
            );
        }

        public VillagerData<I> withItems(ImmutableList<I> items) {
            return new VillagerData<>(
                    xPosition, yPosition, zPosition,
                    (ImmutableSnapshot) journal.withItems(items), uuid
            );
        }

        public VillagerData<I> withEffect(ResourceLocation effect) {
            //FIXME: Add hunger to time travel
            return this;
        }
    }
}
