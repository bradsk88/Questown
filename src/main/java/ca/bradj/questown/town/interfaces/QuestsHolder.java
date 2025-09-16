package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuestsHolder {
    void requestRemovalOfQuestAtIndex(
            UUID batchUUID,
            ServerPlayer sender,
            boolean promptUser
    );

    ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards();

    void showQuestsUI(ServerPlayer player);

    List<AbstractMap.SimpleEntry<MCQuest, MCReward>> getQuestsWithRewardsForVillager(UUID uuid);

    ImmutableSet<VillagerUUID> getVillagersWithQuests();

    Collection<MCQuest> getQuestsForVillager(UUID uuid);

    void addBatchOfRandomQuestsForVisitor(@Nullable VillagerUUID visitorUUID);

    Collection<MCQuestBatch> getAllBatchesForVillager(UUID uuid);

    void addRandomUpgradeQuestForVisitor(VillagerUUID visitorUUID);

    void addItemQuest(
            ResourceLocation itemId,
            int count
    );
}
