package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.Work;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link SpecialRules#CAN_INSTANTLY_SWITCH_TO_EATING} opt-in for the
 * hungry-to-dining short-circuit. Only {@link DowntimeWork} opts in; every other job
 * must not, so a hungry working townie is routed through the work board (which deposits
 * its carried work output) instead of snapping straight to dining.
 *
 * {@link Work#getSpecialGlobalRules()} is exactly the list a {@link DeclarativeJob}
 * surfaces via {@code getGlobalSpecialRules()}, which is what
 * {@code Job.canInstantlySwitchToEating()} checks, so asserting on the work's rules is
 * a faithful proxy for the job-level behavior.
 *
 * The negative case (a non-DowntimeWork job is false) is the trivial default: the rule
 * is only ever added to the list in {@code DowntimeWork}, so {@code contains} is false
 * for every other job. Building an arbitrary non-downtime work here (e.g. DinerWork)
 * is not possible in a plain unit test — it references Questown-registered items
 * (ItemsInit) that vanilla Bootstrap does not register — so the behavioral consequence
 * (a hungry non-downtime townie routes through the board, not straight to dining) is
 * covered by the held {@code village/self_sufficiency} run.
 */
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
