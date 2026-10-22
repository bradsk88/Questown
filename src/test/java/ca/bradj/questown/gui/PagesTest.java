package ca.bradj.questown.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PagesTest {

    @Test
    void emptyList_isOnePage() {
        Assertions.assertEquals(1, Pages.count(0, 3));
    }

    @Test
    void partialPage_countsAsAPage() {
        Assertions.assertEquals(2, Pages.count(4, 3));
    }

    @Test
    void fullPages_addNoExtraPage() {
        Assertions.assertEquals(2, Pages.count(6, 3));
    }
}
