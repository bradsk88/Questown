package ca.bradj.questown.commands.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code -Dquestown.autotest.only=<name>} selection surface: the exact scenario names
 * that {@code TestBlueprintRegistry.allScenarioNames()} must report, so the {@code _qtdev test list}
 * command (and any run script / doc that quotes a name) cannot drift silently from the registries
 * that define them.
 *
 * <p><b>Not unit-testable (registry-bound):</b> {@code TestBlueprintRegistry.getTestableJobs()}
 * builds the arena blueprints, and several of them (baker → {@code BreadOvenBlock}, …)
 * instantiate a Questown {@code Block}, which registers into the vanilla registry via
 * {@code TestLoader.Lazy}. In a plain JUnit JVM the vanilla registry is already frozen
 * post-{@code Bootstrap.bootStrap()}, so that {@code Block.<init>} throws
 * {@code IllegalStateException: Registry is already frozen}. The live surface is a real server:
 * {@code _qtdev test list} (verified 2026-10-09) or the autotest runner's own
 * {@code reportNoMatchAndShutdown} available-scenerios dump. This class asserts the invariants
 * and then fails by design, exactly like the other registry-bound {@code todo_} markers, so the
 * red is a documented gap, not a silent one.
 */
class TestScenarioNamesTodoTest {

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void todo_scenarioName_surface_needsForgeModLoadingHarness() {
        Assertions.fail("Untested registry-bound scenario-name surface (getTestableJobs() builds "
                + "blueprints whose arena Blocks register into a frozen vanilla registry in a plain JVM): "
                + "see the class Javadoc; the live check is `_qtdev test list`. "
                + "When a Forge mod-loading test harness exists, this test becomes the pin for: "
                + "flag/bop_boundary_63, flag/bop_full_64, village/growth, village/self_sufficiency, "
                + "F1_stick_to_campfire, distinct non-blank names, count >= 30.");
        // Deliberately unreachable; documents the intended assertions for when the harness exists.
        List<String> names = TestBlueprintRegistry.allScenarioNames();
        assertNotNull(names);
        assertTrue(names.contains("flag/bop_boundary_63"));
        assertTrue(names.contains("flag/bop_full_64"));
        assertTrue(names.contains("village/growth"));
        assertTrue(names.contains("village/self_sufficiency"));
        assertTrue(names.contains("F1_stick_to_campfire"));
        assertTrue(names.size() >= 30);
        for (String name : names) {
            assertNotNull(name);
            assertFalse(name.isBlank());
        }
        assertEquals(names.size(), names.stream().distinct().count());
    }
}
