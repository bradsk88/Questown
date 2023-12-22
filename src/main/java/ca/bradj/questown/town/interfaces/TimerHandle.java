package ca.bradj.questown.town.interfaces;

import java.util.Collection;

public interface TimerHandle<ROOM, TICK_SOURCE> {
    void tick(
            TICK_SOURCE tickSource,
            Collection<ROOM> roomsToScanForChanges
    );
}
