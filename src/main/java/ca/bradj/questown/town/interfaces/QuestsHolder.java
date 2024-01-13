package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerPlayer;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.UUID;

public interface QuestsHolder {
    void requestRemovalOfQuestAtIndex(
            UUID batchUUID,
            ServerPlayer sender,
            boolean promptUser
    );

    ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards();

    void showQuestsUI(ServerPlayer player);

    Collection<MCQuestBatch> getAllBatchesForVillager(UUID uuid);
}
