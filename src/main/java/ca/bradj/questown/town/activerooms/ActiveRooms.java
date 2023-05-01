package ca.bradj.questown.town.activerooms;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ActiveRooms {

    private final Map<Position, Room> rooms = new HashMap<>();

    // TODO: Support multiple?
    private ChangeListener changeListener = new ChangeListener() {
        @Override
        public void roomAdded(
                Position doorPos,
                Room room
        ) {
            // Do nothing by default
        }

        @Override
        public void roomResized(
                Position doorPos,
                Room oldRoom,
                Room newRoom
        ) {
            // Do nothing by default
        }

        @Override
        public void roomDestroyed(
                Position doorPos,
                Room room
        ) {
            // Do nothing by default
        }
    };

    public Iterable<Room> getAll() {
        return rooms.values();
    }

    public interface ChangeListener {
        void roomAdded(
                Position doorPos,
                Room room
        );

        void roomResized(
                Position doorPos,
                Room oldRoom,
                Room newRoom
        );

        void roomDestroyed(
                Position doorPos,
                Room room
        );
    }

    public void addChangeListener(ChangeListener cl) {
        this.changeListener = cl;
    }

    public void update(Map<Position, Optional<Room>> rooms) {
        Map<Position, Optional<Room>> asOptional = this.rooms.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Optional.of(e.getValue())));
        MapDifference<Position, Optional<Room>> diff = Maps.difference(asOptional, rooms);
        if (diff.areEqual()) {
            return;
        }

        Map<Position, Optional<Room>> oldEntries = diff.entriesOnlyOnLeft();
        for (Map.Entry<Position, Optional<Room>> removed : oldEntries.entrySet()) {
            if (removed.getValue().isEmpty()) {
                throw new IllegalStateException("Removing non-existent room");
            }
            this.changeListener.roomDestroyed(removed.getKey(), removed.getValue().get());
        }
        Map<Position, Optional<Room>> newEntries = diff.entriesOnlyOnRight();
        for (Map.Entry<Position, Optional<Room>> added : newEntries.entrySet()) {
            if (added.getValue().isEmpty()) {
                continue;
            }
            this.changeListener.roomAdded(added.getKey(), added.getValue().get());
        }
        Map<Position, MapDifference.ValueDifference<Optional<Room>>> changedEntries = diff.entriesDiffering();
        for (Map.Entry<Position, MapDifference.ValueDifference<Optional<Room>>> e : changedEntries.entrySet()) {
            MapDifference.ValueDifference<Optional<Room>> v = e.getValue();
            if (v.leftValue().isPresent() && v.rightValue().isEmpty()) {
                this.changeListener.roomDestroyed(e.getKey(), v.leftValue().get());
                continue;
            }
            if (v.leftValue().isEmpty() && v.rightValue().isPresent()) {
                this.changeListener.roomAdded(e.getKey(), v.rightValue().get());
                continue;
            }
            if (v.leftValue().isPresent() && v.rightValue().isPresent()) {
                this.changeListener.roomResized(e.getKey(), v.leftValue().get(), v.rightValue().get());
            }
        }
        this.rooms.clear();
        rooms.entrySet().stream().filter(
                v -> v.getValue().isPresent()
        ).forEach(
                v -> this.rooms.put(v.getKey(), v.getValue().get())
        );
    }
}
