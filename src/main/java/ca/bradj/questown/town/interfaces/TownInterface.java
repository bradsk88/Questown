package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
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

    void addBatchOfRandomQuestsForVisitor(@Nullable UUID visitorUUID);

    Vec3 getVisitorJoinPos();

    BlockPos getRandomWanderTarget(BlockPos avoiding);

    Collection<MCQuest> getQuestsForVillager(UUID uuid);

    void addBatchOfQuests(
            MCQuestBatch batch
    );

    ImmutableSet<UUID> getVillagersWithQuests();

    ImmutableSet<UUID> getVillagers();

    @Nullable ContainerTarget<MCContainer, MCTownItem> findMatchingContainer(ContainerTarget.CheckFn<MCTownItem> c);

    void registerEntity(VisitorMobEntity vEntity);

    BlockPos getEnterExitPos();

    @Nullable BlockPos getClosestWelcomeMatPos(BlockPos reference);

    void addRandomUpgradeQuestForVisitor(UUID visitorUUID);

    @Nullable UUID getRandomVillager();

    @Override
    boolean isVillagerMissing(UUID uuid);

    Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation recipeId);

    Collection<MCRoom> getFarms();

    void registerFenceGate(BlockPos above);

    void validateEntity(VisitorMobEntity visitorMobEntity);

    Collection<UUID> getUnemployedVillagers();

    void addRandomJobQuestForVisitor(UUID visitorUUID);

    void changeJobForVisitor(
            UUID visitorUUID,
            String jobName
    );

    Collection<BlockPos> findMatchedRecipeBlocks(MatchRecipe mr);

    Collection<String> getAvailableJobs();

    boolean hasEnoughBeds();

    ResourceLocation getRandomNearbyBiome();

    boolean isInitialized();

    ImmutableList<HashMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards();

    List<HashMap.SimpleEntry<MCQuest, MCReward>> getQuestsWithRewardsForVillager(UUID uuid);

    UUID getUUID();

    interface MatchRecipe {
        boolean doesMatch(Block item);
    }
}
