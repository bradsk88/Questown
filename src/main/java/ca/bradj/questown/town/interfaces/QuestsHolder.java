package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

    void addBatchOfQuestsForVisitor(@Nullable VillagerUUID visitorUUID);

    void addEmptyRoomBatch();

    /** Discards a batch as the remove button does. False when there is no such open batch. */
    boolean removeBatch(UUID batchID);

    /** Posts the basics batch (job board and storeroom), when the empty-room quest completes. */
    void addBasicsBatch();

    /** Posts day 2's first-work-request quest, when the basics batch completes. */
    void addFirstWorkRequestBatch();

    Collection<MCQuestBatch> getAllBatchesForVillager(UUID uuid);

    /** Progress of each open ITEM or LOOT quest, for the "(n/m)" on its card. */
    Map<ResourceLocation, Integer> openQuestProgress();

    void addRandomUpgradeQuestForVisitor(VillagerUUID visitorUUID, Set<ResourceLocation> declinedFrom);

    void addItemQuest(
            ResourceLocation itemId,
            int count
    );

    // A LOOT quest completes on items villagers bring back.
    void addLootQuest(
            ResourceLocation itemId,
            int count
    );

    // Credits LOOT quests matching an item a villager just brought back.
    void processLootQuests(MCTownItem extracted);

    Optional<JobID> overnightJobOverride();
}
