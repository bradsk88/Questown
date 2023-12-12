package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.Room;

import java.util.Collection;
import java.util.Map;

public interface JobTownProvider<ROOM extends Room> extends TownProvider {
    Collection<ROOM> roomsWithCompletedProduct();

    Map<Integer, ? extends Collection<ROOM>> roomsNeedingIngredientsByState();

    boolean isUnfinishedTimeWorkPresent();
}
