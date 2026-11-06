package ca.bradj.questown.town.rooms;

import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Optional;

public final class RoomDoors {

    private RoomDoors() {
    }

    /** The doorway of the room whose floor holds {@code pos}. */
    public static Optional<BlockPos> doorOfRoomContaining(
            Collection<MCRoom> rooms,
            BlockPos pos
    ) {
        Position flat = new Position(pos.getX(), pos.getZ());
        return rooms.stream()
                    .filter(r -> r.yCoord == pos.getY())
                    .filter(r -> InclusiveSpaces.contains(r.getSpaces(), flat))
                    .findFirst()
                    .map(r -> Positions.ToBlock(r.doorPos, r.yCoord));
    }

    /** The room holding {@code pos}, from floor to head height, doorway included. */
    public static Optional<MCRoom> roomHolding(
            Collection<MCRoom> rooms,
            BlockPos pos
    ) {
        Position flat = new Position(pos.getX(), pos.getZ());
        return rooms.stream()
                    .filter(r -> pos.getY() >= r.yCoord && pos.getY() <= r.yCoord + 1)
                    .filter(r -> r.doorPos.equals(flat) || InclusiveSpaces.contains(r.getSpaces(), flat))
                    .findFirst();
    }
}
