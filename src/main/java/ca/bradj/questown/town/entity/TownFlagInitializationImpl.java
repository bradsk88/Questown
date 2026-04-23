package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.blocks.WelcomeMatBlock;
import ca.bradj.questown.town.*;
import ca.bradj.questown.town.econ.TownEconomicsSerializer;
import ca.bradj.questown.town.quests.MCMorningRewards;
import ca.bradj.questown.town.quests.MCQuestBatches;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.List;

public class TownFlagInitializationImpl implements TownFlagInitialization {
    private final TownFlagBlockEntity flag;

    public TownFlagInitializationImpl(TownFlagBlockEntity flag) {
        this.flag = flag;
    }

    @Override
    public TownRoomsHandle getRoomsHandle() {
        return flag.roomsHandle;
    }

    @Override
    public void setUpQuestsForNewlyPlacedFlag() {
        flag.setUpQuestsForNewlyPlacedFlag();
    }

    @Override
    public void setInitializedQuests(boolean b) {
        flag.isInitializedQuests = b;
    }

    @Override
    public TownQuestsHandle getQuests() {
        return flag.questsHandle;
    }

    @Override
    public MCMorningRewards getMorningRewards() {
        return flag.morningRewards;
    }

    @Override
    public TownPois getPOIs() {
        return flag.pois;
    }

    @Override
    public TownKnowledgeStore getKnowledge() {
        return flag.knowledgeHandle;
    }

    @Override
    public TownVillagerHandle getVillagers() {
        return flag.villagerHandle;
    }

    @Override
    public TownHealingHandle getHealing() {
        return flag.healing;
    }

    @Override
    public MCQuestBatches getQuestBatches() {
        return flag.quests.questBatches;
    }

    @Override
    public TownWorkHandle getWorkHandle() {
        return flag.workHandle;
    }

    @Override
    public CompoundTag serializeBOP() {
        CompoundTag t = new CompoundTag();
        t.putInt("count", flag.bopCount);
        t.putBoolean("tutorial_bop_granted", flag.tutorialBopGranted);
        t.putBoolean("flagpole_built", flag.isFlagpoleBuilt());
        t.putInt("completed_procedural_batches", flag.completedProceduralBatches);
        return t;
    }

    @Override
    public void initializeBOP(CompoundTag tag) {
        flag.bopCount = tag.getInt("count");
        if (tag.contains("tutorial_bop_granted")) {
            flag.tutorialBopGranted = tag.getBoolean("tutorial_bop_granted");
        }
        if (tag.contains("flagpole_built")) {
            flag.setFlagpoleBuilt(tag.getBoolean("flagpole_built"));
        }
        if (tag.contains("completed_procedural_batches")) {
            flag.completedProceduralBatches = tag.getInt("completed_procedural_batches");
        }
    }

    @Override
    public CompoundTag serializeBlockRooms() {
        List<BlockPos> allRooms = flag.roomsHandle.blockRooms();
        ServerLevel sl = flag.getServerLevel();
        allRooms = allRooms.stream().filter(v -> !isLegacy(v, sl)).toList();
        return WelcomeMatsSerializer.INSTANCE.serializeNBT(allRooms, "pos");
    }

    private static final ImmutableList<Class<?>> LEGACY_BLOCKS = ImmutableList.of(
            WelcomeMatBlock.class
    );

    // TODO: Switch welcome mats (etc) to be stored in this data instead of their current location
    private static boolean isLegacy(
            BlockPos v,
            ServerLevel sl
    ) {
        Block block = sl.getBlockState(v).getBlock();
        return LEGACY_BLOCKS.stream().anyMatch(c -> c.isInstance(block));
    }

    @Override
    public boolean initBlockRooms(
            CompoundTag tag,
            TownFlagBlockEntity t
    ) {
        ServerLevel sl = t.getServerLevel();
        if (sl == null) {
            QT.FLAG_LOGGER.error("Cannot initialize block rooms, server level is null.");
            return false;
        }
        Collection<BlockPos> l = WelcomeMatsSerializer.INSTANCE.deserializeNBT(tag, "pos");
        for (BlockPos blockPos : l) {
            if (isLegacy(blockPos, sl)) {
                continue;
            }
            BlockState current = sl.getBlockState(blockPos);
            if (current.isAir()) {
                QT.FLAG_LOGGER.warn("Block no longer exists. Lost block-room at {}", blockPos);
                continue;
            }
            if (!(current.getBlock() instanceof RoomBlock rb)) {
                QT.FLAG_LOGGER.warn("Block is not a room block. Lost block-room at {}", blockPos);
                continue;
            }

            t.initializer().getRoomsHandle().registerBlockAsRoom(RoomBlock.getRoomId(rb), blockPos);
        }
        QT.FLAG_LOGGER.debug("Initialized block rooms from {}", tag);
        return true;
    }

    @Override
    public CompoundTag serializeBonusGiven() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putBoolean("value", flag.givenBonusFood);
        return compoundTag;
    }

    @Override
    public boolean initEconomics(CompoundTag tag) {
        TownEconomicsSerializer.INSTANCE.deserialize(flag.getEconomicsHandle(), tag);
        return true;
    }

    @Override
    public void initEconomicsForNewFlag() {
        // No action required
    }

    @Override
    public CompoundTag serializeEconomics() {
        return TownEconomicsSerializer.INSTANCE.serialize(flag.getEconomicsHandle());
    }

    @Override
    public CompoundTag serializeChickenEverSpawned() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("value", flag.getChickenEverSpawned());
        return t;
    }

    @Override
    public CompoundTag serializeChickenBeatState() {
        CompoundTag t = new CompoundTag();
        t.putString("value", flag.getChickenBeatState().name());
        return t;
    }

    @Override
    public CompoundTag serializeChickenFirstGatherWorldlySeedsFired() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("value", flag.getChickenFirstGatherWorldlySeedsFired());
        return t;
    }

    @Override
    public CompoundTag serializeChickenArcForfeit() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("value", flag.getChickenArcForfeit());
        return t;
    }

    @Override
    public CompoundTag serializeChickenStructureRotation() {
        CompoundTag t = new CompoundTag();
        t.putString("value", flag.getChickenStructureRotation().name());
        return t;
    }

    @Override
    public CompoundTag serializeChickenRotationDetected() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("value", flag.getChickenRotationDetected());
        return t;
    }
}
