package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.quests.MCDelayedReward;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.UUID;

public interface TownInterface {
    Level getLevel();

    BlockPos getBlockPos();

    void generateRandomQuest(ServerLevel sl);

    void addBatchOfRandomQuestsForVisitor(UUID visitorUUID);

    Vec3 getVisitorJoinPos();

    BlockPos getRandomWanderTarget();

    void addTimedReward(MCDelayedReward timedReward);

    Collection<MCQuest> getQuestsForVillager(UUID uuid);

    void addBatchOfQuests(
            MCQuestBatch batch
    );
}
