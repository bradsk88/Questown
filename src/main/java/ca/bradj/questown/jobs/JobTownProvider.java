package ca.bradj.questown.jobs;

import java.util.Collection;
import java.util.Map;

public interface JobTownProvider<ROOM> extends TownProvider {
    Collection<ROOM> roomsWithCompletedProduct();

    Map<Integer, Collection<ROOM>> roomsNeedingIngredientsByState();

    Map<Integer, LZCD.Dependency<Void>> roomsNeedingIngredientsByStateV2();

    boolean isUnfinishedTimeWorkPresent();

    Collection<Integer> getStatesWithUnfinishedItemlessWork();

    Collection<ROOM> roomsAtState(Integer state);
}
