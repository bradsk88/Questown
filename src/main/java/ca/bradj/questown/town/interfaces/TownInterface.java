package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.WorkHandle;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.QuestBatches;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public interface TownInterface extends QuestBatches.VillagerProvider<MCRoom> {
    @Nullable ServerLevel getServerLevel();

    BlockPos getTownFlagBasePos();

    void addImmediateReward(MCReward child);

    void addMorningReward(MCReward ev);

    /**
     * @deprecated Use getQuestHandle
     */
    void addBatchOfRandomQuestsForVisitor(@Nullable UUID visitorUUID);

    Vec3 getVisitorJoinPos();

    BlockPos getRandomWanderTarget(BlockPos avoiding);

    /**
     * @deprecated Use getQuestHandle
     */
    Collection<MCQuest> getQuestsForVillager(UUID uuid);

    /**
     * @deprecated Use getQuestHandle
     */
    void addBatchOfQuests(
            MCQuestBatch batch
    );

    /**
     * @deprecated Use getQuestHandle
     */
    ImmutableSet<UUID> getVillagersWithQuests();

    /**
     * @deprecated Use getVillageHandle
     */
    ImmutableSet<UUID> getVillagers();

    @Nullable ContainerTarget<MCContainer, MCTownItem> findMatchingContainer(ContainerTarget.CheckFn<MCTownItem> c);

    void registerEntity(VisitorMobEntity vEntity);

    BlockPos getEnterExitPos();

    @Nullable BlockPos getClosestWelcomeMatPos(BlockPos reference);

    void addRandomUpgradeQuestForVisitor(UUID visitorUUID);

    @Nullable UUID getRandomVillager();

    @Override
    boolean isVillagerMissing(UUID uuid);

    void validateEntity(VisitorMobEntity visitorMobEntity);

    Collection<UUID> getUnemployedVillagers();

    void addRandomJobQuestForVisitor(UUID visitorUUID);

    /**
     * @deprecated Use getVillagerHandle
     */
    void changeJobForVisitor(
            UUID visitorUUID,
            JobID jobID
    );

    boolean changeJobForVisitorFromBoard(UUID ownerUUID);

    Collection<String> getAvailableRootJobs();

    boolean hasEnoughBeds();

    ResourceLocation getRandomNearbyBiome();

    boolean isInitialized();

    /**
     * @deprecated Use getQuestHandle
     */
    ImmutableList<HashMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards();

    /**
     * @deprecated Use getQuestHandle
     */
    List<HashMap.SimpleEntry<MCQuest, MCReward>> getQuestsWithRewardsForVillager(UUID uuid);

    UUID getUUID();

    void markBlockWeeded(BlockPos p);

    WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle(@Nullable UUID ownerIDOrNullForGlobal);

    WorkHandle getWorkHandle();

    /**
     * @deprecated Use getQuestHandle
     */
    boolean alreadyHasQuest(ResourceLocation resourceLocation);

    KnowledgeHolder<ResourceLocation, MCHeldItem, MCTownItem> getKnowledgeHandle();

    QuestsHolder getQuestHandle();

    RoomsHolder getRoomHandle();

    VillagerHolder getVillagerHandle();

    void removeEntity(VisitorMobEntity visitorMobEntity);

    interface MatchRecipe {
        boolean doesMatch(Block item);
    }
}
