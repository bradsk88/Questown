package ca.bradj.questown.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * r9 findings #2 (dev ruling 2026-10-23): the change-job panel said "Insert a Block of Progress ...
 * (starting tomorrow morning)" with the Block of Progress already in the slot, and in creative,
 * where the change is instant. The line names the next action (insert, or click Unlock) and the
 * real timing.
 */
class JobChangeTextTest {

    @Test
    void empty_slot_asks_for_a_block_of_progress() {
        JobChangeText t = JobChangeText.of(false, false, Optional.empty(), false);
        Assertions.assertEquals("menu.unlock_root.insert_bop", t.key());
        Assertions.assertEquals("menu.unlock_root.when_morning", t.whenKey());
    }

    @Test
    void inserted_says_click_unlock() {
        Assertions.assertEquals("menu.unlock_root.click_unlock",
                JobChangeText.of(true, false, Optional.empty(), false).key());
        JobChangeText promised = JobChangeText.of(true, false, Optional.of("hunter"), false);
        Assertions.assertEquals("menu.unlock_root.click_unlock_promised", promised.key());
        Assertions.assertEquals(Optional.of("hunter"), promised.root());
    }

    @Test
    void promised_before_insertion_names_the_job() {
        JobChangeText t = JobChangeText.of(false, false, Optional.of("hunter"), false);
        Assertions.assertEquals("menu.unlock_root.insert_bop_promised", t.key());
        Assertions.assertEquals(Optional.of("hunter"), t.root());
    }

    @Test
    void blessed_keeps_its_line() {
        Assertions.assertEquals("menu.unlock_root.insert_bop_blessed",
                JobChangeText.of(true, false, Optional.empty(), true).key());
    }

    @Test
    void creative_is_now() {
        Assertions.assertEquals("menu.unlock_root.when_now",
                JobChangeText.of(false, true, Optional.empty(), false).whenKey());
        Assertions.assertEquals("menu.unlock_root.when_now",
                JobChangeText.of(true, true, Optional.of("hunter"), false).whenKey());
    }
}
