package ca.bradj.questown.town.containers;

import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import org.jetbrains.annotations.NotNull;

public class ContainerTargetV2<ROOM extends Room, C extends ContainerTarget.Container<I>, I extends Item<I>> extends
        ContainerTarget<C, I> implements RoomAssociated<ROOM> {
    public final ROOM room;

    public ContainerTargetV2(
            ROOM room,
            Position position,
            int yPosition,
            Position interactionPosition,
            @NotNull Container<I> container,
            ValidCheck check
    ) {
        super(position, yPosition, interactionPosition, container, check);
        this.room = room;
    }

    @Override
    public ROOM getRoom() {
        return room;
    }
}
