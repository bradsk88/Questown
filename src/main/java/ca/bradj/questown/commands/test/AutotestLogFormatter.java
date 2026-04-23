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
}
