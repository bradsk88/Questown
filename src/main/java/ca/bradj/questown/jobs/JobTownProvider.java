package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;

import java.util.Collection;
import java.util.Map;

public interface JobTownProvider<ROOM> extends TownProvider {
    Collection<ROOM> roomsWithCompletedProduct();

    RoomsNeedingVillagerInput<ROOM, ?, ?> roomsNeedingIngredientsByState();

    Map<Integer, ? extends LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks();
    LZCD.Dependency<Void> hasSuppliesV2();

    boolean isUnfinishedTimeWorkPresent();

    Collection<Integer> getStatesWithUnfinishedItemlessWork();

    Collection<ROOM> roomsAtState(Integer state);

    Signals.DayTime getDayTime();
}
