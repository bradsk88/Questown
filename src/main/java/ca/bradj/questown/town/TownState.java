package ca.bradj.questown.town;

import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class TownState<I extends GathererJournal.Item> {
    public final @NotNull ImmutableList<VillagerData<I>> villagers;
    public final @NotNull ImmutableList<ContainerTarget> containers;
    public final long worldTimeAtSleep;

    public TownState(
            @NotNull List<VillagerData<I>> villagers,
            @NotNull List<ContainerTarget> containers,
            long worldTimeAtSleep
    ) {
        this.villagers = ImmutableList.copyOf(villagers);
        this.containers = ImmutableList.copyOf(containers);
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

    public static final class VillagerData<I extends GathererJournal.Item> {
        public final UUID uuid;
        public final Position position;
        public final int yPosition; // TODO: Add a 3D position to RoomRecipes?
        public final GathererJournal.Snapshot<I> journal; // TODO: Immutable journal

        public VillagerData(
                Position position,
                int yPosition,
                GathererJournal.Snapshot<I> journal,
                UUID uuid
        ) {
            this.position = position;
            this.yPosition = yPosition;
            this.journal = journal;
            this.uuid = uuid;
        }

        @Override
        public String toString() {
            return "VillagerData{" +
                    "position=" + position +
                    ", yPosition=" + yPosition +
                    ", journal=" + journal +
                    '}';
        }
    }
}
