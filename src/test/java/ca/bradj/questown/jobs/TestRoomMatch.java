package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;

import java.util.Objects;


public class TestRoomMatch extends RoomWithBlocks<Room, Position, String> {

    public final String match;

    public TestRoomMatch(
            String shop
    ) {
        super(
                new Room(new Position(0, 0), new InclusiveSpace(new Position(-1, -1), new Position(1, 1))),
                ImmutableList.of()
        );
        this.match = shop;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TestRoomMatch that = (TestRoomMatch) o;
        return Objects.equals(match, that.match);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), match);
    }
}
