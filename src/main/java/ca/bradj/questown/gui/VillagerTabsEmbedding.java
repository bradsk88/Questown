package ca.bradj.questown.gui;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.UUID;

public interface VillagerTabsEmbedding {
    Collection<String> getEnabledTabs();

    BlockPos getFlagPos();

    UUID getVillagerUUID();

    boolean showBlockOfProgressTab();

    /**
     * The skills-tab gate: when the townie's level has not yet unlocked any job
     * ({@code Work.getAvailableAtLevel()} vs the level), the server hides the skills tab on
     * every villager menu. Default on: the flag menus (not tabbed on a per-townie basis) keep
     * the tab available.
     */
    default boolean showSkillsTab() {
        return true;
    }

    /**
     * Management-only flag click-through: the villager menu reached from the flag's
     * villager cards hides the inventory tab (the player manages jobs/status there, not
     * the townie's item inventory). Default on: the right-click villager menu keeps it.
     */
    default boolean showInventoryTab() {
        return true;
    }
}
