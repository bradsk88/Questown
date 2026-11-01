package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A tool that is also used up is listed once, under Items Used (the furnace template uses the meat
 * as state 0's tool).
 */
class ToolsNotUsedUpTest {

    @Test
    void aToolThatIsAlsoUsedUp_isNotListedAsATool() {
        Assertions.assertEquals(
                ImmutableList.of(),
                ToolsNotUsedUp.of(ImmutableList.of("beef"), ImmutableList.of("beef", "coal"), String::equals)
        );
    }

    @Test
    void aToolThatIsKept_isStillListed() {
        Assertions.assertEquals(
                ImmutableList.of("sword"),
                ToolsNotUsedUp.of(ImmutableList.of("sword", "beef"), ImmutableList.of("beef"), String::equals)
        );
    }
}
