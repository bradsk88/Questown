package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Work;
import ca.bradj.questown.jobs.Works;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownVillagerHandles;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class PossibilitySources {
    public static PossibilitySource from(TownFlagBlockEntity t) {
        Stream<String> roots = TownVillagerHandles.getJobs(t.getVillagersHandle()).stream().map(JobID::rootId);
        ImmutableSet<Map.Entry<JobID, Supplier<Work>>> rjs = Works.regularJobs();
        return new PossibilitySource() {
            @Override
            public State getWorkState(BlockPos bp) {
                return t.getWorkStatusHandle(null).getJobBlockState(bp);
            }

            @Override
            public Collection<RoomRecipeMatch<MCRoom>> getRoomsForJob(DeclarativeJob dj) {
                return t.getRoomHandle().getRoomsMatching(dj.location().baseRoom());
            }

            @Override
            public Collection<RoomRecipeMatch<MCRoom>> getAllRooms() {
                return t.getRoomHandle().getMatches(r -> true);
            }

            @Override
            public boolean townHasTool(IPredicateCollection<MCTownItem> tool) {
                boolean townHasTool = false;
                @Nullable ContainerTarget<MCContainer, MCTownItem> toolCont = t.findMatchingContainer(tool::test);
                if (toolCont != null) {
                    townHasTool = true;
                }
                return townHasTool;
            }

            @Override
            public Collection<Item> uniqueItems() {
                return TownContainers.getUniqueItems(t);
            }

            @Override
            public Collection<Map.Entry<JobID, Supplier<Work>>> allJobs() {
                return rjs;
            }

            @Override
            public Stream<String> roots() {
                return roots;
            }

            @Override
            public TownInterface.DebugLogger getDebugLogger(
                    QT.QTLogger flagLogger,
                    String jobPossibilitiesCompute
            ) {
                return t.getDebugLogger(flagLogger, jobPossibilitiesCompute);
            }
        };
    }
}
