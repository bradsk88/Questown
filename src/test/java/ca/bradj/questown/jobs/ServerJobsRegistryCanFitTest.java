package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Registry-bound half of the canFit gate. {@code ServerJobsRegistry.canFit}'s window arithmetic is
 * pinned by {@link DayWindowTest}; its first lines ({@code getWorkSupplier(p).get()} and the job
 * duration) need the mod init to populate the per-job {@code Work} suppliers.
 */
class ServerJobsRegistryCanFitTest {

    @Test
    @Disabled("registry-bound: the per-job Work suppliers need Forge mod init; see Javadoc. Window arithmetic is pinned by DayWindowTest.")
    void todo_canFit_isRegistryBoundAndNotUnitLoadable() {
        // ServerJobsRegistry references loader registries in its static init path, so loading it
        // from a plain unit test throws ExceptionInInitializerError.
        fail("Untested registry-bound half of canFit (needs Forge mod-loading harness); "
                + "window arithmetic is pinned by DayWindowTest");
    }
}
