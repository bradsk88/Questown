package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;

public interface Dependencies2<ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>, POS, HELD_ITEM, TOWN_ITEM extends Item<TOWN_ITEM>> {
    ImmutableList<MATCH> getRoomsWithCompletedProduct();

    boolean isJobBlock(POS pos);

    ImmutableList<MATCH> getJobSites();

    ContainersClean.Block<ContainerTarget<?,TOWN_ITEM>> toBlock(
            ROOM room,
            POS pos
    );

    boolean canClaim(POS pos);

    PredicateCollection<HELD_ITEM,HELD_ITEM> item(Integer integer);

    PredicateCollection<TOWN_ITEM,TOWN_ITEM> tools(Integer integer);

    String stringify(POS pos);

    HELD_ITEM convert(TOWN_ITEM townItem);
}
