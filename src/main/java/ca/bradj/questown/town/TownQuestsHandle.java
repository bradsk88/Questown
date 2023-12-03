package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.town.interfaces.QuestsHolder;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.UUID;

public class TownQuestsHandle implements QuestsHolder {
    @Nullable
    private TownFlagBlockEntity town;

    public void initialize(TownFlagBlockEntity t) {
        this.town = t;
    }

    @Override
    public void requestRemovalOfQuestAtIndex(
            UUID batchID,
            ServerPlayer sender
    ) {
        for (MCQuestBatch b : town.quests.getBatches()) {
            if (batchID.equals(b.getBatchUUID())) {
                // TODO[ASAP]: Prompt the user to confirm
                if (town.quests.questBatches.decline(b)) {
                    QT.QUESTS_LOGGER.debug("Quest batch removed: {}", b);
                    town.addMorningReward(new AddBatchOfRandomQuestsForVisitorReward(town, b.getOwner()));
                    town.setChanged();
                    TownFlagBlock.openQuestsUI(
                            town.getServerLevel(),
                            town.getBlockPos(),
                            sender,
                            town.getQuestHandle()
                    );
                    town.broadcastMessage(new TranslatableComponent("messages.town_flag.quest_batch_removed_1"));
                    town.broadcastMessage(new TranslatableComponent("messages.town_flag.quest_batch_removed_2"));
                }
                return;
            }
        }
    }

    @Override
    public ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards() {
        return town.getAllQuestsWithRewards();
    }
}
