package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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
}
