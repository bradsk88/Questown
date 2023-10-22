package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.Room;

import java.util.Collection;
import java.util.Map;

public interface JobTownProvider<STATUS, ROOM extends Room> extends TownProvider {
    Collection<ROOM> roomsWithCompletedProduct();

    Map<STATUS, ? extends Collection<ROOM>> roomsNeedingIngredients();
}
