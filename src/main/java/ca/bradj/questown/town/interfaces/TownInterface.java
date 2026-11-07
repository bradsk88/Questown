package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.ImmutableSnapshot;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.HealingStore;
import ca.bradj.questown.town.TownHealingHandle;
import ca.bradj.questown.town.TownPossibleWork;
import ca.bradj.questown.town.WorkHandle;
import ca.bradj.questown.town.econ.NoMCEconomics;
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
import java.util.Optional;
import java.util.UUID;

public interface TownInterface extends QuestBatches.VillagerProvider<MCRoom> {
    TownPossibleWork getPossibleWork();

    @Nullable
    ServerLevel getServerLevel();

    BlockPos getTownFlagBasePos();

    void addImmediateReward(MCReward child);

    void addMorningReward(MCReward ev);

    Vec3 getVisitorJoinPos();

    Collection<BlockPos> getTownAnchors();

    /** The saved state a returning villager respawns with, taken once; empty for a new arrival. */
    Optional<ImmutableSnapshot<MCHeldItem, ?>> takeRespawnJournal(VillagerUUID villager);

    BlockPos getRandomWanderTarget(BlockPos avoiding);

    /**
     * @deprecated Use TownContainers static function
     */
    @Nullable
    @Deprecated(forRemoval = true)
    ContainerTarget<MCContainer, MCTownItem> findMatchingContainer(ContainerTarget.CheckFn<MCTownItem> c);

    void addRandomJobQuestForVisitor(UUID visitorUUID);

    boolean changeJobForVisitorFromBoard(
            UUID ownerUUID,
            JobID currentJob
    );

    Collection<String> getAvailableRootJobs();

    boolean hasEnoughBeds();

    int missingBeds();

    int missingBedsWithArrivals(int arrivals);

    boolean isInitialized();

    UUID getUUID();

    WorkStatusHandle<BlockPos, MCHeldItem> getWorkStatusHandle(@Nullable UUID ownerIDOrNullForGlobal);

    WorkHandle getWorkHandle();

    KnowledgeHolder<ResourceLocation, MCHeldItem, MCTownItem> getKnowledgeHandle();

    QuestsHolder getQuestHandle();

    RoomsHolder getRoomHandle();

    Optional<ResourceLocation> roomRecipeHolding(BlockPos pos);

    VillagerHolder getVillagerHandle();

    TownHealingHandle getHealHandle();

    HealingStore<BlockPos> getHealingHandle();

    NoMCEconomics getEconomicsHandle();

    int getBlocksOfProgress();

    interface DebugLogger {
        void log(String message, Object... params);
    }

    DebugLogger getDebugLogger(QT.QTLogger logger, String logId);

    interface MatchRecipe {
        boolean doesMatch(Block item);
    }
}
