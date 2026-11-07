package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A job shows in the skills tab once the villager reaches its {@code available_at_level}; the default is 2. */
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
