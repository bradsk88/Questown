package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.Room;

import java.util.Collection;
import java.util.Map;

public record Rooms<POS, ROOM extends Room>(Map<POS, Integer> spotStatuses,
                                            Map<ROOM, ? extends Collection<Integer>> roomStatuses,
                                            Map<POS, Boolean> spotJobBlocks) {
}
