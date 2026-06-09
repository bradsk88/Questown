package ca.bradj.questown.commands.test;

/**
 * Shared formatter for the unified {@code [autotest]} per-assertion log shape.
 *
 * <p>The contract is one line per assertion:
 * <pre>
 * [autotest] &lt;track&gt;:&lt;name&gt; [PASS|FAIL] &lt;expectation&gt;: &lt;details&gt;
 * </pre>
 *
 * <p>Both the existing jobs track and the chicken-arc track funnel through this
 * helper so the agent's log-parser regex is a single shape. The {@code [autotest]}
 * prefix itself is owned by {@link LogTestOutput} — this helper only produces the
 * part after the prefix.
 */
public final class AutotestLogFormatter {

    public static final String TRACK_JOBS = "jobs";
    public static final String TRACK_CHICKEN_ARC = "chicken-arc";

    private AutotestLogFormatter() {
    }

    public static String format(
            String track,
            String name,
            boolean pass,
            String expectation,
            String details
    ) {
        String tag = pass ? "[PASS]" : "[FAIL]";
        return track + ":" + name + " " + tag + " " + expectation + ": " + details;
    }

    /**
     * Per-scenario summary line, xfail-aware. An {@code expectedFailure} scenario reports
     * {@code [XFAIL]} when it (correctly) fails and {@code [XPASS]} when it unexpectedly passes —
     * the latter is an alarm that the underlying gap was closed and the marker should be removed.
     * The {@code passed} flag here is the suite-gate boolean (i.e. {@code getWarpPassed()}), which
     * is already inverted for expected-failure scenarios.
     */
    public static String formatScenario(
            String track,
            String name,
            boolean passed,
            boolean expectedFailure
    ) {
        String tag = scenarioTag(passed, expectedFailure);
        String details;
        if (expectedFailure) {
            details = passed
                    ? "warp failed as expected"
                    : "warp unexpectedly passed — remove expectedFailure";
        } else {
            details = passed ? "all expectations met" : "some expectations failed";
        }
        return track + ":" + name + " " + tag + " scenario: " + details;
    }

    private static String scenarioTag(boolean passed, boolean expectedFailure) {
        if (!expectedFailure) {
            return passed ? "[PASS]" : "[FAIL]";
        }
        return passed ? "[XFAIL]" : "[XPASS]";
    }
}
