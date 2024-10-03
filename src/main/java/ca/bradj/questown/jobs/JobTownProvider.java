package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.RoomsNeedingIngredientsOrTools;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;

import java.util.Collection;
import java.util.Map;

public interface JobTownProvider<ROOM> extends TownProvider {
    Collection<ROOM> roomsWithCompletedProduct();

    RoomsNeedingIngredientsOrTools<ROOM, ?> roomsNeedingIngredientsByState();

    Map<Integer, LZCD.Dependency<Void>> roomsNeedingIngredientsByStateV2();
    LZCD.Dependency<Void> hasSuppliesV2();

    boolean isUnfinishedTimeWorkPresent();

    Collection<Integer> getStatesWithUnfinishedItemlessWork();

    Collection<ROOM> roomsAtState(Integer state);
}
