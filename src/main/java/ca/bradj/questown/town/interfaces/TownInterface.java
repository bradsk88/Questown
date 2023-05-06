package ca.bradj.questown.town.interfaces;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;

public interface TownInterface {
    Level getLevel();

    BlockPos getBlockPos();

    void generateRandomQuest(ServerLevel sl);

    void addBatchOfRandomQuestsForVisitor(UUID visitorUUID);
}
