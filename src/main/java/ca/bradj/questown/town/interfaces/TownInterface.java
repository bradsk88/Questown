package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public interface TownInterface {
    @Nullable ServerLevel getServerLevel();

    BlockPos getTownFlagBasePos();

    void addMorningReward(MCReward ev);

    void addBatchOfRandomQuestsForVisitor(UUID visitorUUID);

    Vec3 getVisitorJoinPos();

    BlockPos getRandomWanderTarget();

    Collection<MCQuest> getQuestsForVillager(UUID uuid);

    void addBatchOfQuests(
            MCQuestBatch batch
    );

    Set<UUID> getVillagers();

    ContainerTarget<MCContainer, MCTownItem> findMatchingContainer(ContainerTarget.CheckFn<MCTownItem> c);

    void registerEntity(VisitorMobEntity vEntity);

    BlockPos getEnterExitPos();

    @Nullable BlockPos getClosestWelcomeMatPos(BlockPos reference);

    void addRandomUpgradeQuestForVisitor(UUID visitorUUID);

    UUID getRandomVillager(Random random);

    Collection<MCRoom> getRoomsMatching(ResourceLocation recipeId);

    interface MatchRecipe {
        boolean doesMatch(Block item);
    }
    Collection<BlockPos> findMatchedRecipeBlocks(MatchRecipe mr);
}
