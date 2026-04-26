package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import ca.bradj.questown.town.*;
import net.minecraft.world.level.block.Rotation;
import ca.bradj.questown.town.quests.MCQuestBatches;
import ca.bradj.questown.town.rooms.TownRoomsMap;
import ca.bradj.questown.town.rooms.TownRoomsMapSerializer;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
    private static final String NBT_BLOCKS_OF_PROGRESS_STORED = String.format("%s_bops_stored", Questown.MODID);
    private static final String NBT_BLOCK_ROOMS = QTNBT.keyify("block_rooms");
    private static final String NBT_BONUS_GIVEN = QTNBT.keyify("bonus_given");
    private static final String NBT_ECONOMICS = QTNBT.keyify("economics");
    private static final String NBT_CHICKEN_EVER_SPAWNED = QTNBT.keyify("chicken_ever_spawned");
    private static final String NBT_CHICKEN_BEAT_STATE = QTNBT.keyify("chicken_beat_state");
    private static final String NBT_CHICKEN_FIRST_GATHER_WORLDLY_SEEDS_FIRED = QTNBT.keyify(
            "chicken_first_gather_worldly_seeds_fired"
    );
    private static final String NBT_CHICKEN_ARC_FORFEIT = QTNBT.keyify("chicken_arc_forfeit");
    private static final String NBT_CHICKEN_STRUCTURE_ROTATION = QTNBT.keyify("chicken_structure_rotation");
    private static final String NBT_CHICKEN_ROTATION_DETECTED = QTNBT.keyify("chicken_rotation_detected");
    private static final String NBT_CHICKEN_SUNSET_CHEST_SPAWNED = QTNBT.keyify("chicken_sunset_chest_spawned");

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
        b.put(NBT_BLOCK_ROOMS, initBlockRooms());
        b.put(NBT_BLOCKS_OF_PROGRESS_STORED, initBlocksOfProgress());
        b.put(NBT_BONUS_GIVEN, initBonusGiven());
        b.put(NBT_ECONOMICS, initEconomics());
        b.put(NBT_CHICKEN_EVER_SPAWNED, initChickenEverSpawned());
        b.put(NBT_CHICKEN_BEAT_STATE, initChickenBeatState());
        b.put(NBT_CHICKEN_FIRST_GATHER_WORLDLY_SEEDS_FIRED, initChickenFirstGatherWorldlySeedsFired());
        b.put(NBT_CHICKEN_ARC_FORFEIT, initChickenArcForfeit());
        b.put(NBT_CHICKEN_STRUCTURE_ROTATION, initChickenStructureRotation());
        b.put(NBT_CHICKEN_ROTATION_DETECTED, initChickenRotationDetected());
        b.put(NBT_CHICKEN_SUNSET_CHEST_SPAWNED, initChickenSunsetChestSpawned());
        return b.build();
    }

    private static InitPair initChickenEverSpawned() {
        return new InitPair(
                (tag, flag) -> {
                    flag.chickenEverSpawned = tag.getBoolean("value");
                    return true;
                },
                flag -> flag.chickenEverSpawned = false
        );
    }

    private static InitPair initChickenBeatState() {
        return new InitPair(
                (tag, flag) -> {
                    flag.chickenBeatState = ChickenBeatState.fromNameSafe(tag.getString("value"));
                    return true;
                },
                flag -> flag.chickenBeatState = ChickenBeatState.WAITING_FOR_STICK
        );
    }

    private static InitPair initChickenFirstGatherWorldlySeedsFired() {
        return new InitPair(
                (tag, flag) -> {
                    flag.chickenFirstGatherWorldlySeedsFired = tag.getBoolean("value");
                    return true;
                },
                flag -> flag.chickenFirstGatherWorldlySeedsFired = false
        );
    }

    private static InitPair initChickenArcForfeit() {
        return new InitPair(
                (tag, flag) -> {
                    flag.chickenArcForfeit = tag.getBoolean("value");
                    return true;
                },
                flag -> flag.chickenArcForfeit = false
        );
    }

    private static InitPair initChickenStructureRotation() {
        return new InitPair(
                (tag, flag) -> {
                    flag.chickenStructureRotation = safeRotation(tag.getString("value"));
                    return true;
                },
                flag -> flag.chickenStructureRotation = Rotation.NONE
        );
    }

    private static InitPair initChickenRotationDetected() {
        return new InitPair(
                (tag, flag) -> {
                    flag.chickenRotationDetected = tag.getBoolean("value");
                    return true;
                },
                flag -> flag.chickenRotationDetected = false
        );
    }

    private static InitPair initChickenSunsetChestSpawned() {
        return new InitPair(
                (tag, flag) -> {
                    flag.chickenSunsetChestSpawned = tag.getBoolean("value");
                    return true;
                },
                flag -> flag.chickenSunsetChestSpawned = false
        );
    }

    public static Rotation safeRotation(String name) {
        if (name == null) {
            return Rotation.NONE;
        }
        try {
            return Rotation.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Rotation.NONE;
        }
    }

    private static InitPair initEconomics() {
        return new InitPair(
                (tag, flag) -> flag.initializer().initEconomics(tag),
                flag -> flag.initializer().initEconomicsForNewFlag()
        );
    }

    private static InitPair initBonusGiven() {
        return new InitPair(
                (tag, flag) -> {
                    flag.givenBonusFood = tag.getBoolean("value");
                    return true;
                },
                flag -> {
                    flag.givenBonusFood = false;
                }
        );
    }

    private static @NotNull InitPair initRooms() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            TownRoomsMap registeredRooms = t.initializer().getRoomsHandle().getRegisteredRooms();
            TownRoomsMapSerializer.INSTANCE.deserialize(tag, t, registeredRooms);
            logInit("Initialized rooms from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onPlace = t -> {
            t.roomsHandle.initializeNew(t);
            logInit("Initialized rooms for new flag");
        };
        return new InitPair(fromTag, onPlace);
    }

    private static void logInit(
            String s,
            Object... tag
    ) {
        QT.FLAG_LOGGER.unwrap().debug(s, tag);
    }

    private static @NotNull InitPair initQuestBatches() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            t.quests.initialize(t);
            boolean inited = MCQuestBatches.SERIALIZER.deserializeNBT(t, tag, t.quests.questBatches);
            if (!inited) {
                t.initializer().setUpQuestsForNewlyPlacedFlag();
            }
            t.initializer().setInitializedQuests(true);
            logInit("Initialized quests from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onPlace = t -> {
            t.quests.initialize(t);
            t.initializer().getQuests().initialize(t);
            logInit("Initialized quests for new flag");
        };
        return new InitPair(fromTag, onPlace);
    }

    private static @NotNull InitPair initMorningRewards() {
        return new InitPair(
                (tag, t) -> {
                    t.initializer().getMorningRewards().deserializeNbt(t, tag);
                    logInit("Initialized morning rewards from {}", tag);
                    return true;
                }, t -> {
            logInit("Initialized morning rewards for new flag");
        }
        );
    }

    private static @NotNull InitPair initWelcomeMats() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            Collection<BlockPos> l = WelcomeMatsSerializer.INSTANCE.deserializeNBT(tag, "mats");
            l.forEach(t.initializer().getPOIs()::registerWelcomeMat);
            logInit("Initialized welcome mats from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onPlace = t -> logInit("Initialized welcome mats for new flag");
        return new InitPair(fromTag, onPlace);
    }

    private static @NotNull InitPair initBlockRooms() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> t.initializer().initBlockRooms(tag, t);
        Consumer<TownFlagBlockEntity> onPlace = t -> logInit("Initialized block rooms for new flag");
        return new InitPair(fromTag, onPlace);
    }

    private static @NotNull InitPair initJobs() {
        return new InitPair(
                (tag, t) -> {
                    TownWorkHandleSerializer.INSTANCE.deserializeNBT(tag, t.workHandle);
                    logInit("Initialized jobs from {}", tag);
                    return true;
                }, t -> logInit("Initialized jobs for new flag")
        );
    }

    private static @NotNull InitPair initKnowledge() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            TownKnowledgeStore knowledge = t.initializer().getKnowledge();
            if (!knowledge.isInitialized()) {
                return false;
            }
            TownKnowledgeStoreSerializer.INSTANCE.deserializeNBT(tag, knowledge);
            logInit("Initialized knowledge from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onFlagPlace = t -> {
            t.initializer().getKnowledge().initialize(t);
            logInit("Initialized knowledge for new flag");
        };
        return new InitPair(fromTag, onFlagPlace);
    }

    private static @NotNull InitPair initVillagers() {
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag = (tag, t) -> {
            long currentTick = Util.getTick(t.getServerLevel());
            TownVillagerHandle.SERIALIZER.deserialize(tag, t.initializer().getVillagers(), currentTick);
            logInit("Initialized villagers from {}", tag);
            return true;
        };
        Consumer<TownFlagBlockEntity> onPlace = t -> {
            TownVillagerHandle villagerHandle = t.initializer().getVillagers();
            villagerHandle.associate(t);
            villagerHandle.addHungryListener(e -> {
                if (t.villagerHandle.isDining(e.getUUID())) {
                    return;
                }
                if (!t.villagerHandle.canDine(e.getUUID())) {
                    return;
                }
                if (t.villagerHandle.gaveUpDiningRecently(e.getVUID())) {
                    return;
                }
                String rid = e.getJobId().rootId();
                ResourceLocation diningRoom = DinerWork.asWork(rid).baseRoom;
                Collection<RoomRecipeMatch<MCRoom>> diningRooms = t.roomsHandle.getRoomsMatching(diningRoom);
                if (diningRooms.isEmpty()) {
                    t.villagerHandle.changeJobForVillager(e.getVUID(), DinerNoTableWork.getIdForRoot(rid), false);
                } else {
                    t.villagerHandle.changeJobForVillager(e.getVUID(), DinerWork.getIdForRoot(rid), false);
                }
            });
            villagerHandle.addStatsListener(s -> t.setChanged());
            logInit("Villager handle associated on new flag");
        };
        return new InitPair(fromTag, onPlace);
    }

    private static InitPair initHealSpots() {
        return new InitPair(
                (tag, town) -> {
                    TownHealingHandle.SERIALIZER.deserialize(tag, town.initializer().getHealing());
                    logInit("Initialized healing spots from {}", tag);
                    return true;
                }, (town) -> {
            town.initializer().getHealing().initialize(town);
        }
        );
    }
    private static InitPair initBlocksOfProgress() {
        return new InitPair(
                (tag, town) -> {
                    town.initializer().initializeBOP(tag);
                    return true;
                }, (town) -> {}
        );
    }

    public static void write(
            Long currentTick,
            CompoundTag t,
            TownFlagInitialization flag
    ) {
        write(t, NBT_QUEST_BATCHES, MCQuestBatches.SERIALIZER.serializeNBT(flag.getQuestBatches()));
        write(t, NBT_MORNING_REWARDS, flag.getMorningRewards().serializeNbt());
        write(t, NBT_WELCOME_MATS, WelcomeMatsSerializer.INSTANCE.serializeNBT(flag.getPOIs().getWelcomeMats(), "mats"));
        write(t, NBT_ROOMS, TownRoomsMapSerializer.INSTANCE.serializeNBT(flag.getRoomsHandle().getRegisteredRooms()));
        write(t, NBT_JOBS, TownWorkHandleSerializer.INSTANCE.serializeNBT(flag.getWorkHandle()));
        write(t, NBT_KNOWLEDGE, TownKnowledgeStoreSerializer.INSTANCE.serializeNBT(flag.getKnowledge()));
        write(t, NBT_VILLAGERS, TownVillagerHandle.SERIALIZER.serialize(flag.getVillagers(), currentTick));
        write(t, NBT_HEALSPOTS, TownHealingHandle.SERIALIZER.serialize(flag.getVillagers(), currentTick));
        write(t, NBT_BLOCK_ROOMS, flag.serializeBlockRooms());
        write(t, NBT_BLOCKS_OF_PROGRESS_STORED, flag.serializeBOP());
        write(t, NBT_BONUS_GIVEN, flag.serializeBonusGiven());
        write(t, NBT_ECONOMICS, flag.serializeEconomics());
        write(t, NBT_CHICKEN_EVER_SPAWNED, flag.serializeChickenEverSpawned());
        write(t, NBT_CHICKEN_BEAT_STATE, flag.serializeChickenBeatState());
        write(t, NBT_CHICKEN_FIRST_GATHER_WORLDLY_SEEDS_FIRED, flag.serializeChickenFirstGatherWorldlySeedsFired());
        write(t, NBT_CHICKEN_ARC_FORFEIT, flag.serializeChickenArcForfeit());
        write(t, NBT_CHICKEN_STRUCTURE_ROTATION, flag.serializeChickenStructureRotation());
        write(t, NBT_CHICKEN_ROTATION_DETECTED, flag.serializeChickenRotationDetected());
        write(t, NBT_CHICKEN_SUNSET_CHEST_SPAWNED, flag.serializeChickenSunsetChestSpawned());
    }

    private static void write(
            CompoundTag target,
            String key,
            CompoundTag value
    ) {
        target.put(key, value);
    }
}
