package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/** Whether a flag crafting row shows: always, or once the quest that unlocks it has been posted. */
public final class FlagCraftGates {

    private FlagCraftGates() {
    }

    public static boolean visible(@Nullable ResourceLocation unlockedByQuest, Collection<ResourceLocation> postedQuests) {
        if (unlockedByQuest == null) {
            return true;
        }
        return postedQuests.contains(unlockedByQuest);
    }
}
