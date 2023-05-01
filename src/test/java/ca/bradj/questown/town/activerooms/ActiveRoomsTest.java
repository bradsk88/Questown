package ca.bradj.questown.town.activerooms;

import static org.junit.jupiter.api.Assertions.*;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

class ActiveRoomsTest {

    private ActiveRooms activeRooms;
    private ActiveRooms.ChangeListener changeListenerMock;

    @BeforeEach
    void setUp() {
        activeRooms = new ActiveRooms();
        changeListenerMock = mock(ActiveRooms.ChangeListener.class);
        activeRooms.addChangeListener(changeListenerMock);
    }

    @Test
    void testUpdate_AddNewRoom() {
        Room newRoom = mock(Room.class);
        Position newPosition = new Position(1, 2);
        Map<Position, Optional<Room>> updatedRooms = new HashMap<>();
        updatedRooms.put(newPosition, Optional.of(newRoom));

        activeRooms.update(updatedRooms);

        verify(changeListenerMock).roomAdded(newPosition, newRoom);
        verifyNoMoreInteractions(changeListenerMock);
    }

    @Test
    void testUpdate_RemoveRoom() {
        Room existingRoom = mock(Room.class);
        Position existingPosition = new Position(1, 2);
        activeRooms.update(Map.of(existingPosition, Optional.of(existingRoom)));
        verify(changeListenerMock, Mockito.times(1)).roomAdded(existingPosition, existingRoom);

        activeRooms.update(Map.of(existingPosition, Optional.empty()));

        verify(changeListenerMock, Mockito.times(1)).roomDestroyed(existingPosition, existingRoom);
        verifyNoMoreInteractions(changeListenerMock);
    }

    @Test
    void testUpdate_ReplaceRoom() {
        Room existingRoom = mock(Room.class);
        Position existingPosition = new Position(1, 2);
        activeRooms.update(Map.of(existingPosition, Optional.of(existingRoom)));
        verify(changeListenerMock, Mockito.times(1)).roomAdded(existingPosition, existingRoom);

        Room newRoom = mock(Room.class);
        activeRooms.update(Map.of(existingPosition, Optional.of(newRoom)));

        verify(changeListenerMock, Mockito.times(1)).roomResized(existingPosition, existingRoom, newRoom);
        verifyNoMoreInteractions(changeListenerMock);
    }

    @Test
    void testUpdate_UpdateWithEmptyMap() {
        Room existingRoom = mock(Room.class);
        Position existingPosition = new Position(1, 2);
        activeRooms.update(Map.of(existingPosition, Optional.of(existingRoom)));
        verify(changeListenerMock, Mockito.times(1)).roomAdded(existingPosition, existingRoom);

        activeRooms.update(new HashMap<>());
        verify(changeListenerMock, Mockito.times(1)).roomDestroyed(existingPosition, existingRoom);

        verifyNoMoreInteractions(changeListenerMock);
    }
}
