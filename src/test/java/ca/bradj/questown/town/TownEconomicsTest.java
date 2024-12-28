package ca.bradj.questown.town;

import ca.bradj.questown.gui.ItemEconomicsData;
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
        ImmutableList<TownEconomics.UnmetNeed> data = ImmutableList.of(
                new TownEconomics.UnmetNeed(1, UUID1, "books"),
                new TownEconomics.UnmetNeed(2, UUID1, "food"),
                new TownEconomics.UnmetNeed(3, UUID1, "books")
        );
        ImmutableMap<UUID, ImmutableList<ItemEconomicsData>> out = TownEconomics.aggregateForUI(data);
        ImmutableList<ItemEconomicsData> vList = out.get(UUID1);
        assertNotNull(vList);
        assertEquals(2, vList.size());
        assertTrue(vList.contains(new ItemEconomicsData("books", 2)));
        assertTrue(vList.contains(new ItemEconomicsData("food", 1)));
    }

    @Test
    void aggregateForUIShouldHandleSimpleMultiVillagerList() {
        ImmutableList<TownEconomics.UnmetNeed> data = ImmutableList.of(
                // Villager 1
                new TownEconomics.UnmetNeed(1, UUID1, "books"),
                new TownEconomics.UnmetNeed(2, UUID1, "food"),
                // Villager 2
                new TownEconomics.UnmetNeed(3, UUID2, "books")
        );
        ImmutableMap<UUID, ImmutableList<ItemEconomicsData>> out = TownEconomics.aggregateForUI(data);

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
        ImmutableList<TownEconomics.UnmetNeed> data = ImmutableList.of();
        ImmutableMap<UUID, ImmutableList<ItemEconomicsData>> out = TownEconomics.aggregateForUI(data);
        assertEquals(0, out.size());
    }
}