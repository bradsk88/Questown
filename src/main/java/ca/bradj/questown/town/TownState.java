package ca.bradj.questown.town;

import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.jobs.GathererTimeWarper;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TownState<C extends ContainerTarget.Container<I>, I extends GathererJournal.Item<I>> implements GathererTimeWarper.FoodRemover<I>, GathererTimeWarper.Town<I> {
    public final @NotNull ImmutableList<VillagerData<I>> villagers;
    public final @NotNull ImmutableList<ContainerTarget<C, I>> containers;
    public final @NotNull ImmutableList<BlockPos> gates;
    public final long worldTimeAtSleep;

    public TownState(
            @NotNull List<VillagerData<I>> villagers,
            @NotNull List<ContainerTarget<C, I>> containers,
            @NotNull List<BlockPos> gates,
            long worldTimeAtSleep
    ) {
        this.villagers = ImmutableList.copyOf(villagers);
        this.containers = ImmutableList.copyOf(containers);
        this.gates = ImmutableList.copyOf(gates);
        this.worldTimeAtSleep = worldTimeAtSleep;
    }

    @Override
    public String toString() {
        return "TownState{" +
                "villagers=" + villagers +
                ", containers=" + containers +
                ", worldTimeAtSleep=" + worldTimeAtSleep +
                '}';
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
    public ImmutableList<I> depositItems(ImmutableList<I> itemsToDeposit) {
        ImmutableList.Builder<I> notDepositedItems = ImmutableList.builder();
        boolean allFull = false;
        for (I item : itemsToDeposit) {
            boolean deposited = false;
            if (!allFull) {
                for (int i = 0; i < containers.size(); i++) {
                    ContainerTarget<C, I> container = containers.get(i);
                    for (int j = 0; j < container.size(); j++) {
                        I containerItem = container.getItem(j);
                        if (containerItem.isEmpty()) {
                            container.setItem(j, item);
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

    public static final class VillagerData<I extends GathererJournal.Item<I>> {
        public final UUID uuid;
        public final double xPosition, yPosition, zPosition;
        public final GathererJournal.Snapshot<I> journal; // TODO: Immutable journal

        public VillagerData(
                double xPosition,
                double yPosition,
                double zPosition,
                GathererJournal.Snapshot<I> journal,
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
                    "uuid=" + uuid +
                    ", xPosition=" + xPosition +
                    ", yPosition=" + yPosition +
                    ", zPosition=" + zPosition +
                    ", journal=" + journal +
                    '}';
        }

        public int getCapacity() {
            return 6; // TODO: Be smarter about this?
        }
    }
}
