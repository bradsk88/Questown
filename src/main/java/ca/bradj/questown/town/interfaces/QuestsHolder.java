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

    /**
     * Posts the day-one "empty room" batch (D#2: teach room building before the
     * specific rooms). Called by {@link ca.bradj.questown.town.rewards.AddEmptyRoomBatchReward}
     * when the welcome-mat gate quest completes; its reward posts the basics batch.
     */
    void addEmptyRoomBatch();

    /**
     * Posts the day-one "basics" batch (job board + store room). Called by
     * {@link ca.bradj.questown.town.rewards.AddBasicsBatchReward} when the
     * empty-room quest completes (D#2 re-gated it on the room-building beat).
     */
    void addBasicsBatch();

    /**
     * Posts the day-2 "post your first work request" quest (F2). Posted when the
     * basics batch completes (the board and store room exist by then); completes
     * on the town's first posted work request while active.
     */
    void addFirstWorkRequestBatch();

    Collection<MCQuestBatch> getAllBatchesForVillager(UUID uuid);

    /**
     * Deposit progress for open ITEM quests: wantedId -> matching items already in the
     * town containers (the "(n/target)" numerator rendered on the quest card). Empty
     * when no open ITEM quest.
     */
    Map<ResourceLocation, Integer> openItemQuestProgress();

    void addRandomUpgradeQuestForVisitor(VillagerUUID visitorUUID);

    void addItemQuest(
            ResourceLocation itemId,
            int count
    );

    // Post a LOOT-type quest (production-satisfiable, like the 2.5 raw-food gate). Unlike
    // addItemQuest's ITEM quest, LOOT quests are matched by Quests.processLootQuests when a
    // townie extracts loot; the caller chooses the quest type to match the completion path.
    void addLootQuest(
            ResourceLocation itemId,
            int count
    );

    // LOOT quests complete on production: complete any open LOOT quest whose wanted item or
    // tag matches the item a townie just extracted. The extracted item is not consumed.
    void processLootQuests(MCTownItem extracted);

    Optional<JobID> overnightJobOverride();
}
