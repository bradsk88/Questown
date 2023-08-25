package ca.bradj.questown.town;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;

public class MCRoom {
    Room room;
    int y;

    public MCRoom(
            Room room,
            int y
    ) {
        this.room = room;
        this.y = y;
    }

    public InclusiveSpace getSpace() {
        return room.getSpace();
    }

    public int getY() {
        return y;
    }
}
