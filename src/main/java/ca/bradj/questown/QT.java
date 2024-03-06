package ca.bradj.questown;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
}
