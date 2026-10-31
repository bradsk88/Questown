package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Dev ruling 2026-10-22: a count of 1 reads "needed once", not "needed 1 times".
class ItemEconomicsDataTooltipTest {

    private static String keyOf(int timesNeeded) {
        var contents = ItemEconomicsData.neededTooltip(Compat.literal("Coal"), timesNeeded).getContents();
        return ((TranslatableContents) contents).getKey();
    }

    @Test
    void aSingleNeedReadsOnce() {
        Assertions.assertEquals("questown.menu.needs_once_in_past_day", keyOf(1));
    }

    @Test
    void severalNeedsReadTimes() {
        Assertions.assertEquals("questown.menu.needs_in_past_day", keyOf(6));
    }
}
