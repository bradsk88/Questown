package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.Work;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Only {@link DowntimeWork} lets a hungry villager switch straight to eating; other jobs go through the board, which stores their output first. */
class DowntimeWorkEatingRuleTest {

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void downtimeWorkOptInToInstantlySwitchToEating() {
        Work work = DowntimeWork.asWork("hunter");
        Assertions.assertTrue(
                work.getSpecialGlobalRules().contains(SpecialRules.CAN_INSTANTLY_SWITCH_TO_EATING),
                "DowntimeWork must opt in to the hungry-to-dining short-circuit"
        );
    }
}
