package ca.bradj.questown;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class QT {

    /**
     * @deprecated Use another QT._LOGGER
     */
    public static final Logger LOGGER = LogManager.getLogger();
    public static final Logger VILLAGER_LOGGER = LogManager.getLogger("Questown:Villagers");
    public static final Logger JOB_LOGGER = LogManager.getLogger("Questown:Jobs");
    public static final Logger BLOCK_LOGGER = LogManager.getLogger("Questown:Blocks");
    public static final Logger ITEM_LOGGER = LogManager.getLogger("Questown:Items");
    public static final Logger INIT_LOGGER = LogManager.getLogger("Questown:Init");
    public static final Logger FLAG_LOGGER = LogManager.getLogger("Questown:Flag");
    public static final Logger QUESTS_LOGGER = LogManager.getLogger("Questown:Quests");
    public static final Logger PROFILE_LOGGER = LogManager.getLogger("Questown:Profiling");
    public static final Logger GUI_LOGGER = LogManager.getLogger("Questown:GUI");

    public static void logBug(
            String s,
            Object... flagPos
    ) {
        // Add all of flagPos to c
        List<Object> c = new ArrayList<>(ImmutableList.of(flagPos));
        c.add("https://github.com/bradsk88/questown/issues");
        LOGGER.error(s + " (This is a bug; please report it to the mod author) at {}", c.toArray());
    }
}
