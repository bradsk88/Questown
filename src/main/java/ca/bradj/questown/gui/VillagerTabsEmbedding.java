package ca.bradj.questown.gui;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.UUID;

public interface VillagerTabsEmbedding {
    Collection<String> getEnabledTabs();

    BlockPos getFlagPos();

    UUID getVillagerUUID();

    boolean showBlockOfProgressTab();

    /** Hidden until the villager's level unlocks a job. */
    default boolean showSkillsTab() {
        return true;
    }

    /** Opened from the flag, the villager menu has no inventory tab. */
    default boolean showInventoryTab() {
        return true;
    }
}
