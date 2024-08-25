package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.serialization.MCRoom;

public interface CustomRoomChecker {
    boolean isReadyForExtract(MCRoom room);
}
