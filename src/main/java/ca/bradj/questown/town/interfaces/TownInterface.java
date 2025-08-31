package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.*;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.QuestBatches;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface TownInterface extends QuestBatches.VillagerProvider<MCRoom> {
    TownPossibleWork getPossibleWork();

    @Nullable
    ServerLevel getServerLevel();

    BlockPos getTownFlagBasePos();

    void addImmediateReward(MCReward child);

    void addMorningReward(MCReward ev);

    Vec3 getVisitorJoinPos();

    BlockPos getRandomWanderTarget(BlockPos avoiding);

    @Nullable
    ContainerTarget<MCContainer, MCTownItem> findMatchingContainer(ContainerTarget.CheckFn<MCTownItem> c);

    void addRandomJobQuestForVisitor(UUID visitorUUID);

    /**
     * @deprecated Use getVillagerHandle().getJobsHandle()
     */
    @Deprecated(forRemoval = true, since="0.0.9")
    boolean changeJobForVisitorFromBoard(
            UUID ownerUUID,
            JobID currentJob
    );

    Collection<String> getAvailableRootJobs();

    boolean hasEnoughBeds();

    boolean isInitialized();

    UUID getUUID();

    WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle(@Nullable UUID ownerIDOrNullForGlobal);

    WorkHandle getWorkHandle();

    KnowledgeHolder<ResourceLocation, MCHeldItem, MCTownItem> getKnowledgeHandle();

    QuestsHolder getQuestHandle();

    RoomsHolder getRoomHandle();

    VillagerHolder getVillagerHandle();

    TownHealingHandle getHealHandle();

    HealingStore<BlockPos> getHealingHandle();

    NoMCEconomics getEconomicsHandle();

    int getBlocksOfProgress();

    interface MatchRecipe {
        boolean doesMatch(Block item);
    }
}
