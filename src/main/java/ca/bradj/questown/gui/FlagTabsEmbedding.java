package ca.bradj.questown.gui;

import net.minecraft.core.BlockPos;

import java.util.Collection;

public interface FlagTabsEmbedding {
    Collection<String> getEnabledTabs();

    BlockPos getFlagPos();
}
