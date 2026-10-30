package ca.bradj.questown.town.econ;

import ca.bradj.questown.gui.ItemEconomicsData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/** Dev playtest 2026-10-29: an "in the last 1000 ticks" economics view. */
class NoMCEconomicsRecentTest {

    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);

    @Test
    void onlyRecordsInTheWindow_countedAcrossVillagers_mostNeededFirst() {
        List<NoMCEconomics.UnmetNeed> records = List.of(
                new NoMCEconomics.UnmetNeed(100, A, "coal"),
                new NoMCEconomics.UnmetNeed(1500, A, "coal"),
                new NoMCEconomics.UnmetNeed(1600, B, "food"),
                new NoMCEconomics.UnmetNeed(1700, B, "food")
        );
        Assertions.assertEquals(
                List.of(new ItemEconomicsData("food", 2), new ItemEconomicsData("coal", 1)),
                NoMCEconomics.aggregateSince(records, 1000)
        );
    }
}
