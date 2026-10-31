package ca.bradj.questown.commands.test;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * Collects the log lines an autotest should fail on but that the game survives: a work spot the
 * stuck-block self-heal had to reseed, and any Questown ERROR. village/day1 passed 31/31 while
 * its log carried both (2026-10-17: the flag eating spot reseeded after the diner stall that the
 * player later hit as the "carrot take/put loop"). Also two quest announcements within one
 * broadcast slot (2026-10-17: "New quests are available!" then, a second later, "New quest is
 * available: post your first work request…").
 */
final class LogTripwire {

    enum Kind { NONE, STUCK_RESEED, ERROR, QUEST_ANNOUNCEMENT }

    private static final String RESEED_MARKER = "reseeding to fresh";
    private static final String QUEST_ANNOUNCEMENT_PREFIX = "New quest";
    private static final List<String> BROADCAST_MARKERS = List.of("Broadcasting message: ", "Queued broadcast (");
    /** TownMessages' broadcast spacing: two announcements inside it reach the player as one burst. */
    static final long ANNOUNCEMENT_BURST_TICKS = 100;

    private final List<String> reseeds = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final List<String> doubleAnnouncements = new CopyOnWriteArrayList<>();
    private volatile @Nullable String lastQuestAnnouncement = null;
    private volatile long lastQuestAnnouncementTick = Long.MIN_VALUE;
    private final LongSupplier gameTime;
    private final AbstractAppender appender;

    private LogTripwire(LongSupplier gameTime) {
        this.gameTime = gameTime;
        this.appender = new AbstractAppender("questown-autotest-tripwire", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                record(event.getLevel(), event.getLoggerName(), event.getMessage().getFormattedMessage());
            }
        };
    }

    static LogTripwire attach(LongSupplier gameTime) {
        LogTripwire t = new LogTripwire(gameTime);
        t.appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().addAppender(t.appender, Level.INFO, null);
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

    List<String> doubleAnnouncements() {
        return List.copyOf(doubleAnnouncements);
    }

    private void record(Level level, String loggerName, String message) {
        switch (classify(level, loggerName, message)) {
            case STUCK_RESEED -> reseeds.add(message);
            case ERROR -> errors.add(loggerName + ": " + message);
            case QUEST_ANNOUNCEMENT -> recordQuestAnnouncement(message);
            case NONE -> { }
        }
    }

    private void recordQuestAnnouncement(String message) {
        long now = gameTime.getAsLong();
        if (sameBurst(lastQuestAnnouncementTick, now)) {
            doubleAnnouncements.add(lastQuestAnnouncement + " | then | " + message);
        }
        lastQuestAnnouncement = message;
        lastQuestAnnouncementTick = now;
    }

    /** Pure: whether an announcement at {@code now} lands in the same burst as one at {@code previous}. */
    static boolean sameBurst(long previous, long now) {
        return previous != Long.MIN_VALUE && now - previous < ANNOUNCEMENT_BURST_TICKS;
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
        String payload = broadcastPayload(message);
        if (payload == null) {
            return Kind.NONE;
        }
        return payload.startsWith(QUEST_ANNOUNCEMENT_PREFIX) ? Kind.QUEST_ANNOUNCEMENT : Kind.NONE;
    }

    /** The town message a TownMessages broadcast/queue log line carries, or null for other lines. */
    private static @Nullable String broadcastPayload(@Nullable String message) {
        if (message == null) {
            return null;
        }
        for (String marker : BROADCAST_MARKERS) {
            if (!message.startsWith(marker)) {
                continue;
            }
            int queuedSuffix = message.indexOf("): ");
            if (marker.startsWith("Queued") && queuedSuffix >= 0) {
                return message.substring(queuedSuffix + 3);
            }
            return message.substring(marker.length());
        }
        return null;
    }

    private static boolean isQuestownLogger(String loggerName) {
        return loggerName != null
                && (loggerName.startsWith("Questown") || loggerName.startsWith("ca.bradj.questown"));
    }
}
