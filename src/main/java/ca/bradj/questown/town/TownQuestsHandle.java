package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.town.interfaces.QuestsHolder;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;

public class TownQuestsHandle implements QuestsHolder {
    @Nullable private TownFlagBlockEntity town;

    public void initialize(TownFlagBlockEntity t) {
        this.town = t;
    }

    @Override
    public void requestRemovalOfQuestAtIndex(
            int questIndex,
            ServerPlayer sender
    ) {
        int i = 0;
        for (MCQuestBatch b : town.quests.getBatches()) {
            for (MCQuest q : b.getAll()) {
                if (i == questIndex) {
                    // TODO[ASAP]: Prompt the user to confirm
                    if (town.quests.questBatches.decline(b)) {
                        QT.QUESTS_LOGGER.debug("Quest batch removed: {}", b);
                        town.setChanged();
                        TownFlagBlock.openQuestsUI(town.getServerLevel(), town.getBlockPos(), sender, town.getQuestHandle());
                    }
                    return;
                }
                i++;
            }
        }
    }

    @Override
    public ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards() {
        return town.getAllQuestsWithRewards();
    }
}
