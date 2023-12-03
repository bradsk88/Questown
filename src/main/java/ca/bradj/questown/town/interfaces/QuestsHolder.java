package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerPlayer;

import java.util.AbstractMap;

public interface QuestsHolder {
    void requestRemovalOfQuestAtIndex(int questIndex,
                                      ServerPlayer sender
    );

    ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards();
}
