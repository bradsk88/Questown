package ca.bradj.questown.town;

import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.town.quests.RoomNeed;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TownEconomicsTest {

    public static final UUID UUID1 = UUID.randomUUID();
    public static final UUID UUID2 = UUID.randomUUID();

    @Test
    void aggregateForUIShouldHandleSimpleList() {
        ImmutableList<NoMCEconomics.UnmetNeed> data = ImmutableList.of(
                new NoMCEconomics.UnmetNeed(1, UUID1, "books"),
                new NoMCEconomics.UnmetNeed(2, UUID1, "food"),
                new NoMCEconomics.UnmetNeed(3, UUID1, "books")
        );
        ImmutableMap<UUID, ImmutableList<ItemEconomicsData>> out = NoMCEconomics.aggregateForUI(data);
        ImmutableList<ItemEconomicsData> vList = out.get(UUID1);
        assertNotNull(vList);
        assertEquals(2, vList.size());
        assertTrue(vList.contains(new ItemEconomicsData("books", 2)));
        assertTrue(vList.contains(new ItemEconomicsData("food", 1)));
    }

    @Test
    void aggregateForUIShouldHandleSimpleMultiVillagerList() {
        ImmutableList<NoMCEconomics.UnmetNeed> data = ImmutableList.of(
                // Villager 1
                new NoMCEconomics.UnmetNeed(1, UUID1, "books"),
                new NoMCEconomics.UnmetNeed(2, UUID1, "food"),
                // Villager 2
                new NoMCEconomics.UnmetNeed(3, UUID2, "books")
        );
        ImmutableMap<UUID, ImmutableList<ItemEconomicsData>> out = NoMCEconomics.aggregateForUI(data);

        ImmutableList<ItemEconomicsData> vList = out.get(UUID1);
        assertNotNull(vList);
        assertEquals(2, vList.size());
        assertTrue(vList.contains(new ItemEconomicsData("books", 1)));
        assertTrue(vList.contains(new ItemEconomicsData("food", 1)));

        ImmutableList<ItemEconomicsData> vList2 = out.get(UUID2);
        assertNotNull(vList2);
        assertEquals(1, vList2.size());
        assertTrue(vList.contains(new ItemEconomicsData("books", 1)));
    }

    @Test
    void aggregateForUIShouldHandleEmptyList() {
        ImmutableList<NoMCEconomics.UnmetNeed> data = ImmutableList.of();
        ImmutableMap<UUID, ImmutableList<ItemEconomicsData>> out = NoMCEconomics.aggregateForUI(data);
        assertEquals(0, out.size());
    }

    @Test
    void getMostNeededRoomsShouldHandleEmptyList() {
        ImmutableList<NoMCEconomics.UnmetNeed> data = ImmutableList.of(
        );
        ImmutableList<RoomNeed<String>> out = NoMCEconomics.aggregateRooms(data);
        assertEquals(0, out.size());
    }

    @Test
    void getMostNeededRoomsShouldHandleSimpleSingleVillagerList() {
        ImmutableList<NoMCEconomics.UnmetNeed> data = ImmutableList.of(
                new NoMCEconomics.UnmetNeed(1, UUID1, "library"),
                new NoMCEconomics.UnmetNeed(2, UUID1, "diner"),
                new NoMCEconomics.UnmetNeed(3, UUID1, "library")
        );
        ImmutableList<RoomNeed<String>> out = NoMCEconomics.aggregateRooms(data);
        assertEquals(2, out.size());
        assertTrue(out.contains(new RoomNeed<>("library", 2, 1)));
        assertTrue(out.contains(new RoomNeed<>("diner", 1, 1)));
    }

    @Test
    void getMostNeededRoomsShouldHandleSimpleMultiVillagerList() {
        ImmutableList<NoMCEconomics.UnmetNeed> data = ImmutableList.of(
                // Villager 1
                new NoMCEconomics.UnmetNeed(1, UUID1, "library"),
                new NoMCEconomics.UnmetNeed(2, UUID1, "diner"),
                // Villager 2
                new NoMCEconomics.UnmetNeed(3, UUID2, "library")
        );
        ImmutableList<RoomNeed<String>> out = NoMCEconomics.aggregateRooms(data);
        assertEquals(2, out.size());
        assertTrue(out.contains(new RoomNeed<>("library", 2, 2)));
        assertTrue(out.contains(new RoomNeed<>("diner", 1, 1)));
    }
}