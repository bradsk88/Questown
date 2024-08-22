package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.quests.MCQuestBatches;
import ca.bradj.questown.town.rooms.TownRoomsMap;
import ca.bradj.questown.town.rooms.TownRoomsMapSerializer;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class TownFlagTileData {

    private static final String NBT_QUEST_BATCHES = String.format("%s_quest_batches", Questown.MODID);
    private static final String NBT_MORNING_REWARDS = String.format("%s_morning_rewards", Questown.MODID);
    private static final String NBT_WELCOME_MATS = String.format("%s_welcome_mats", Questown.MODID);
    private static final String NBT_ROOMS = String.format("%s_rooms", Questown.MODID);
    private static final String NBT_JOBS = String.format("%s_jobs", Questown.MODID);
    private static final String NBT_KNOWLEDGE = String.format("%s_knowledge", Questown.MODID);
    private static final String NBT_VILLAGERS = String.format("%s_villagers", Questown.MODID);
    private static final String NBT_HEALSPOTS = String.format("%s_heal_spots", Questown.MODID);

    public static Map<String, InitPair> initialize() {

        ImmutableMap.Builder<String, InitPair> b = ImmutableMap.builder();
        b.put(NBT_ROOMS, initRooms());
        b.put(NBT_QUEST_BATCHES, initQuestBatches());
        b.put(NBT_MORNING_REWARDS, initMorningRewards());
        b.put(NBT_WELCOME_MATS, initWelcomeMats());
        b.put(NBT_JOBS, initJobs());
        b.put(NBT_KNOWLEDGE, initKnowledge());
        b.put(NBT_VILLAGERS, initVillagers());
        b.put(NBT_HEALSPOTS, initHealSpots());
        return b.build();
    }

    private static @NotNull InitPair initRooms() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            TownRoomsMap registeredRooms = t.initializer().getRoomsHandle().getRegisteredRooms();
            TownRoomsMapSerializer.INSTANCE.deserialize(tag, t, registeredRooms);
            QT.FLAG_LOGGER.debug("Initialized rooms from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onPlace = t -> {
            t.roomsHandle.initializeNew(t);
            QT.FLAG_LOGGER.debug("Initialized rooms for new flag");
        };
        return new InitPair(fromTag, onPlace);
    }

    private static @NotNull InitPair initQuestBatches() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            CompoundTag data = tag.getCompound(NBT_QUEST_BATCHES);
            boolean inited = MCQuestBatches.SERIALIZER.deserializeNBT(t, data, t.quests.questBatches);
            if (!inited) {
                t.initializer().setUpQuestsForNewlyPlacedFlag();
            }
            t.initializer().setInitializedQuests(true);
            QT.FLAG_LOGGER.debug("Initialized quests from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onPlace = t -> {
            t.initializer().getQuests().initialize(t);
            QT.FLAG_LOGGER.debug("Initialized quests for new flag");
        };
        return new InitPair(fromTag, onPlace);
    }

    private static @NotNull InitPair initMorningRewards() {
        return new InitPair((tag, t) -> {
            CompoundTag data = tag.getCompound(NBT_MORNING_REWARDS);
            t.initializer().getMorningRewards().deserializeNbt(t, data);
            QT.FLAG_LOGGER.debug("Initialized morning rewards from {}", tag);
            return true;
        }, t -> {
            QT.FLAG_LOGGER.debug("Initialized morning rewards for new flag");
        });
    }

    private static @NotNull InitPair initWelcomeMats() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            Collection<BlockPos> l = WelcomeMatsSerializer.INSTANCE.deserializeNBT(tag);
            l.forEach(t.initializer().getPOIs()::registerWelcomeMat);
            QT.FLAG_LOGGER.debug("Initialized welcome mats from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onPlace = t -> QT.FLAG_LOGGER.debug("Initialized welcome mats for new flag");
        return new InitPair(fromTag, onPlace);
    }

    private static @NotNull InitPair initJobs() {
        return new InitPair((tag, t) -> {
            TownWorkHandleSerializer.INSTANCE.deserializeNBT(tag, t.workHandle);
            QT.FLAG_LOGGER.debug("Initialized jobs from {}", tag);
            return true;
        }, t -> QT.FLAG_LOGGER.debug("Initialized jobs for new flag"));
    }

    private static @NotNull InitPair initKnowledge() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            TownKnowledgeStore knowledge = t.initializer().getKnowledge();
            if (!knowledge.isInitialized()) {
                return false;
            }
            TownKnowledgeStoreSerializer.INSTANCE.deserializeNBT(QTNBT.getCompound(tag, NBT_KNOWLEDGE), knowledge);
            QT.FLAG_LOGGER.debug("Initialized knowledge from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onFlagPlace = t -> {
            t.initializer().getKnowledge().initialize(t);
            QT.FLAG_LOGGER.debug("Initialized knowledge for new flag");
        };
        return new InitPair(fromTag, onFlagPlace);
    }

    private static @NotNull InitPair initVillagers() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            long currentTick = Util.getTick(t.getServerLevel());
            TownVillagerHandle.SERIALIZER.deserialize(tag, t.initializer().getVillagers(), currentTick);
            QT.FLAG_LOGGER.debug("Initialized villagers from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onPlace = t -> {
            TownVillagerHandle villagerHandle = t.initializer().getVillagers();
            villagerHandle.associate(t);
            villagerHandle.addHungryListener(e -> {
                if (t.getVillagerHandle().isDining(e.getUUID())) {
                    return;
                }
                if (!t.getVillagerHandle().canDine(e.getUUID())) {
                    return;
                }
                String rid = e.getJobId().rootId();
                ResourceLocation diningRoom = DinerWork.asWork(rid).baseRoom();
                Collection<RoomRecipeMatch<MCRoom>> diningRooms = t.roomsHandle.getRoomsMatching(diningRoom);
                if (diningRooms.isEmpty()) {
                    t.changeJobForVisitor(e.getUUID(), DinerNoTableWork.getIdForRoot(rid));
                } else {
                    t.changeJobForVisitor(e.getUUID(), DinerWork.getIdForRoot(rid));
                }
            });
            villagerHandle.addStatsListener(s -> t.setChanged());
            QT.FLAG_LOGGER.debug("Initialized villagers for new flag");
        };
        return new InitPair(fromTag, onPlace);
    }

    private static InitPair initHealSpots() {
        return new InitPair((tag, town) -> {
            TownHealingHandle.SERIALIZER.deserialize(tag, town.initializer().getHealing());
            QT.FLAG_LOGGER.debug("Initialized healing spots from {}", tag);
            return true;
        }, (town) -> {
            town.initializer().getHealing().initialize(town);
        });
    }

    public static void write(
            Long currentTick,
            CompoundTag t,
            TownFlagInitialization flag
    ) {
        write(t, NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(flag.getQuestBatches()));
        write(t, NBT_MORNING_REWARDS, flag.getMorningRewards().serializeNbt());
        write(t, NBT_WELCOME_MATS, WelcomeMatsSerializer.INSTANCE.serializeNBT(flag.getPOIs().getWelcomeMats()));
        write(t, NBT_ROOMS, TownRoomsMapSerializer.INSTANCE.serializeNBT(flag.getRoomsHandle().getRegisteredRooms()));
        write(t, NBT_JOBS, TownWorkHandleSerializer.INSTANCE.serializeNBT(flag.getWorkHandle()));
        write(t, NBT_KNOWLEDGE, TownKnowledgeStoreSerializer.INSTANCE.serializeNBT(flag.getKnowledge()));
        write(t, NBT_VILLAGERS, TownVillagerHandle.SERIALIZER.serialize(flag.getVillagers(), currentTick));
        write(t, NBT_HEALSPOTS, TownHealingHandle.SERIALIZER.serialize(flag.getVillagers(), currentTick));
    }

    private static void write(
            CompoundTag target,
            String key,
            CompoundTag value
    ) {
        target.put(key, value);
    }
}
