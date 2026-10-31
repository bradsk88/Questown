package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Registry-bound half of the canFit gate ({@code .scratch/cook-soft-lock/DIAGNOSIS.md}).
 *
 * <p>{@code ServerJobsRegistry.canFit} (L710-723) is the production gate that rejects
 * a board ask with "not enough time left in the day". Its window arithmetic is pinned
 * by {@link DayWindowTest}; what is NOT unit-testable is the method's first lines:
 *
 * <pre>
 *   Work w = getWorkSupplier(p).get();
 *   long jobDuration = w.jobFunc.apply(villagerID).getTotalDuration();
 * </pre>
 *
 * which require the Forge mod init to populate the per-job {@code Work} suppliers
 * (a plain unit test only has {@code Bootstrap.bootStrap()}, which does not register
 * Questown jobs) — the same class of registry-bound code the chicken-arc suite marks
 * as a permanent {@code todo_} failure. Per the repo doctrine (AGENTS.md "When writing
 * unit tests"): note it, add a failing assertion, do not simulate the code.
 */
class ServerJobsRegistryCanFitTest {

    @Test
    @Disabled("registry-bound: the per-job Work suppliers need Forge mod init; see Javadoc. Window arithmetic is pinned by DayWindowTest.")
    void todo_canFit_isRegistryBoundAndNotUnitLoadable() {
        // ServerJobsRegistry references Forge registries in its static init path, so
        // loading it (or calling the real canFit) from a plain unit test throws
        // ExceptionInInitializerError. Do not "fix" this by re-implementing the job
        // supplier lookup here — DayWindowTest already pins the window arithmetic
        // from the JSON-known duration (hunter/sword: D=2000).
        fail("Untested registry-bound half of canFit (needs Forge mod-loading harness); "
                + "window arithmetic is pinned by DayWindowTest");
    }
}
