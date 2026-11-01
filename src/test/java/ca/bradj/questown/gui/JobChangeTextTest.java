package ca.bradj.questown.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * The change-job panel names the next action (insert a Block of Progress, or click Unlock) and the
 * real timing (tomorrow morning in survival, at once in creative).
 */
class JobChangeTextTest {

    @Test
    void empty_slot_asks_for_a_block_of_progress() {
        JobChangeText t = JobChangeText.of(false, false, Optional.empty());
        Assertions.assertEquals("menu.unlock_root.insert_bop", t.key());
        Assertions.assertEquals("menu.unlock_root.when_morning", t.whenKey());
    }

    @Test
    void inserted_says_click_unlock() {
        Assertions.assertEquals("menu.unlock_root.click_unlock",
                JobChangeText.of(true, false, Optional.empty()).key());
        JobChangeText promised = JobChangeText.of(true, false, Optional.of("hunter"));
        Assertions.assertEquals("menu.unlock_root.click_unlock_promised", promised.key());
        Assertions.assertEquals(Optional.of("hunter"), promised.root());
    }

    @Test
    void promised_before_insertion_names_the_job() {
        JobChangeText t = JobChangeText.of(false, false, Optional.of("hunter"));
        Assertions.assertEquals("menu.unlock_root.insert_bop_promised", t.key());
        Assertions.assertEquals(Optional.of("hunter"), t.root());
    }


    @Test
    void creative_is_now() {
        Assertions.assertEquals("menu.unlock_root.when_now",
                JobChangeText.of(false, true, Optional.empty()).whenKey());
        Assertions.assertEquals("menu.unlock_root.when_now",
                JobChangeText.of(true, true, Optional.of("hunter")).whenKey());
    }

    /**
     * A quest-promised change applies at the spend ({@code TownFlagBlockEntity.requestJobRootChange}),
     * in survival too, so the text says it changes now.
     */
    @Test
    void promised_is_now_in_survival() {
        Assertions.assertEquals("menu.unlock_root.when_now",
                JobChangeText.of(false, false, Optional.of("hunter")).whenKey());
        Assertions.assertEquals("menu.unlock_root.when_now",
                JobChangeText.of(true, false, Optional.of("hunter")).whenKey());
    }
}
