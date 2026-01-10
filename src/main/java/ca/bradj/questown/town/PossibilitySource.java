package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Work;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface PossibilitySource {
    State getWorkState(BlockPos bp);

    Collection<RoomRecipeMatch<MCRoom>> getRoomsForJob(DeclarativeJob dj);

    Collection<RoomRecipeMatch<MCRoom>> getAllRooms();

    boolean townHasTool(IPredicateCollection<MCTownItem> tool);

    Collection<Item> uniqueItems();

    Collection<Map.Entry<JobID, Supplier<Work>>> allJobs();

    Stream<String> roots();

    TownInterface.DebugLogger getDebugLogger(
            QT.QTLogger flagLogger,
            String jobPossibilitiesCompute
    );
}
