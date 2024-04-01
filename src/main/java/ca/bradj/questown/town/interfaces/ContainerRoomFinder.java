package ca.bradj.questown.town.interfaces;

import java.util.List;
import java.util.function.Predicate;

public interface ContainerRoomFinder<ROOM, TOWN_ITEM> {

    List<ROOM> getRoomsWithContainersOfItem(Predicate<TOWN_ITEM> isWorkResult);
}
