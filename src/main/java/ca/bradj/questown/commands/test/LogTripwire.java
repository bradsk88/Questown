package ca.bradj.questown.commands.test;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects the log lines an autotest should fail on but that the game survives: a work spot the
 * stuck-block self-heal had to reseed, and any Questown ERROR. village/day1 passed 31/31 while
 * its log carried both (2026-10-17: the flag eating spot reseeded after the diner stall that the
 * player later hit as the "carrot take/put loop").
 */
final class LogTripwire {

    enum Kind { NONE, STUCK_RESEED, ERROR }

    private static final String RESEED_MARKER = "reseeding to fresh";

    private final List<String> reseeds = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender;

    private LogTripwire() {
        this.appender = new AbstractAppender("questown-autotest-tripwire", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                record(event.getLevel(), event.getLoggerName(), event.getMessage().getFormattedMessage());
            }
        };
    }

    static LogTripwire attach() {
        LogTripwire t = new LogTripwire();
        t.appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().addAppender(t.appender, Level.WARN, null);
        ctx.updateLoggers();
        return t;
    }

    void detach() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().removeAppender(appender.getName());
        ctx.updateLoggers();
        appender.stop();
    }

    List<String> reseeds() {
        return List.copyOf(reseeds);
    }

    List<String> errors() {
        return List.copyOf(errors);
    }

    private void record(Level level, String loggerName, String message) {
        switch (classify(level, loggerName, message)) {
            case STUCK_RESEED -> reseeds.add(message);
            case ERROR -> errors.add(loggerName + ": " + message);
            case NONE -> { }
        }
    }

    /** Pure: which tripwire, if any, a log line trips. Only Questown's own loggers count. */
    static Kind classify(Level level, String loggerName, String message) {
        if (!isQuestownLogger(loggerName)) {
            return Kind.NONE;
        }
        if (message != null && message.contains(RESEED_MARKER)) {
            return Kind.STUCK_RESEED;
        }
        if (level.isMoreSpecificThan(Level.ERROR)) {
            return Kind.ERROR;
        }
        return Kind.NONE;
    }

    private static boolean isQuestownLogger(String loggerName) {
        return loggerName != null
                && (loggerName.startsWith("Questown") || loggerName.startsWith("ca.bradj.questown"));
    }
}
