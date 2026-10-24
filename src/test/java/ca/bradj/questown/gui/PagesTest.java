package ca.bradj.questown.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    /** r16 #2: the request picker computed a page's range and drew the whole list on every page. */
    @Test
    void slice_returnsOnlyThatPagesItems() {
        List<String> items = List.of("raw_food", "wheat_seeds", "leather", "raw_mutton");

        Assertions.assertEquals(List.of("raw_food", "wheat_seeds", "leather"), Pages.slice(items, 0, 3));
        Assertions.assertEquals(List.of("raw_mutton"), Pages.slice(items, 1, 3));
    }

    @Test
    void slice_pastTheLastPage_isEmpty() {
        Assertions.assertEquals(List.of(), Pages.slice(List.of("a", "b"), 3, 3));
    }
}
