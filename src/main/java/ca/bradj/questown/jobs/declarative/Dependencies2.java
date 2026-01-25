package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.ContainersClean;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.WorkPosition;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;

import java.util.function.Predicate;

public interface Dependencies2<ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>, POS, HELD_ITEM, TOWN_ITEM extends Item<TOWN_ITEM>> {
    boolean isJobBlock(POS pos);

    ImmutableList<MATCH> getJobSites();

    ImmutableList<MATCH> getRoomsForSupplyCheck();

    Predicate<MATCH> isJobSitePredicate();

    ContainersClean.Block<ContainerTarget<?,TOWN_ITEM>> toBlock(
            ROOM room,
            POS pos
    );

    boolean canClaim(POS pos);

    PredicateCollection<HELD_ITEM,HELD_ITEM> item(Integer integer);

    PredicateCollection<TOWN_ITEM,TOWN_ITEM> tools(Integer integer);

    String stringify(POS pos);

    HELD_ITEM convert(TOWN_ITEM townItem);

    boolean hasSpace();

    WorkPosition<POS> getWorkSpot();

    Signals.DayTime getDayTime();
}
