package ca.bradj.questown.gui;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.UUID;

public interface VillagerTabsEmbedding {
    Collection<String> getEnabledTabs();

    BlockPos getFlagPos();

    UUID getVillagerUUID();

    boolean showBlockOfProgressTab();

    /** Opened from the flag, the villager menu has no inventory tab. */
    default boolean showInventoryTab() {
        return true;
    }
}
