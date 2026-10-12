package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JSON {@code available_at_level} gate (skills-tab ruling, 2026-10-13): a job is only
 * visible/unlockable in the skills tab once the townie's level reaches the value. The field
 * lives on {@link Work} with a default of {@link Work#DEFAULT_AVAILABLE_AT_LEVEL} (2 - the
 * first level at which the tab appears; townies start at level 1).
 */
class WorkAvailableAtLevelTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Work sampleJob() {
        return new Work(
                new JobID("crafter", "test_tool"),
                null,
                ItemStack.EMPTY,
                null,
                null,
                ctx -> true,
                (info, pos) -> true,
                new net.minecraft.resources.ResourceLocation("questown", "test_room"),
                null,
                td -> com.google.common.collect.ImmutableSet.of(),
                sl -> null,
                items -> List.of(),
                input -> ca.bradj.questown.town.NoOpWarper.INSTANCE,
                0,
                false,
                ImmutableList.of()
        );
    }

    @Test
    void default_availableAtLevel_is2() {
        assertEquals(Work.DEFAULT_AVAILABLE_AT_LEVEL, sampleJob().getAvailableAtLevel(),
                "a job without available_at_level is available from the moment the tab exists");
    }

    @Test
    void withAvailableAtLevel_chainsTheJsonValue() {
        assertEquals(7, sampleJob().withAvailableAtLevel(7).getAvailableAtLevel());
    }
}
