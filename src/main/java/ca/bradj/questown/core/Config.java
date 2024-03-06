package ca.bradj.questown.core;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final String VILLAGE_START_QUESTS = "Village start quests";

    public static final String FILENAME = "questown-server.toml";

    public static final ForgeConfigSpec.ConfigValue<Integer> DOOR_SEARCH_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<Integer> TOWN_TICK_RADIUS;

    public static final ForgeConfigSpec.ConfigValue<Integer> CAMPFIRE_SEARCH_RADIUS;

    public static final ForgeConfigSpec.ConfigValue<Boolean> LOG_QUEST_BATCH_GENERATION;

    public static final ForgeConfigSpec.ConfigValue<Integer> IDEAL_QUEST_THRESHOLD_TICKS;

    public static final ForgeConfigSpec.ConfigValue<Integer> QUEST_GENERATION_MAX_TICKS;

    public static final ForgeConfigSpec.ConfigValue<Integer> DEFAULT_ITEM_WEIGHT;

    public static final ForgeConfigSpec.ConfigValue<Integer> FARM_ACTION_INTERVAL;

    public static final ForgeConfigSpec.ConfigValue<Integer> BAKING_TIME_REQUIRED_BASELINE;

    public static final ForgeConfigSpec.ConfigValue<Integer> TICK_SAMPLING_RATE;

    public static final ForgeConfigSpec.ConfigValue<Integer> MIN_WEIGHT_PER_QUEST_BATCH;

    public static final ForgeConfigSpec.ConfigValue<Integer> QUEST_BATCH_VILLAGER_BOOST_FACTOR;

    public static final ForgeConfigSpec.ConfigValue<Integer> BIOME_SCAN_RADIUS;

    public static final ForgeConfigSpec.ConfigValue<Integer> WANDER_GIVEUP_TICKS;

    public static final ForgeConfigSpec.ConfigValue<Integer> FARMER_WEEDS_RARITY;

    public static final ForgeConfigSpec.ConfigValue<Integer> SMELTER_WORK_REQUIRED;

    public static final ForgeConfigSpec.ConfigValue<Boolean> SCAN_FOR_DOORS;

    public static final ForgeConfigSpec.ConfigValue<Double> DUPLICATE_QUEST_COST_FACTOR;

    public static final ForgeConfigSpec.ConfigValue<Boolean> INFINITE_TOWN_DOORS;
    public static final ForgeConfigSpec.ConfigValue<Integer> FLAG_SUB_BLOCK_RETENTION_TICKS;
    public static final ForgeConfigSpec.ConfigValue<Integer> FLAG_SUB_BLOCK_REMOVED_TICKS;
    public static final ForgeConfigSpec.ConfigValue<Integer> FLAG_SUB_BLOCK_DETECTION_TICKS;
    public static final ForgeConfigSpec.ConfigValue<Integer> META_ROOM_DIAMETER;
    public static final ForgeConfigSpec.ConfigValue<Integer> GATHERER_TIME_REQUIRED_BASELINE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> JOB_BOARD_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<Boolean> CRASH_ON_FAILED_WARP;
    public static final ForgeConfigSpec.ConfigValue<Integer> TIME_WARP_MAX_TICKS;
    public static final ForgeConfigSpec.ConfigValue<Integer> BASE_MAX_LOOP;
    public static final ForgeConfigSpec.ConfigValue<Long> MAX_TICKS_WITHOUT_SUPPLIES;
    public static final ForgeConfigSpec.ConfigValue<Integer> BASE_FULLNESS;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HUNGER_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<Long> BLOCK_CLAIMS_TICK_LIMIT;
    public static final ForgeConfigSpec.ConfigValue<Long> MAX_TICKS_WITHOUT_DINING_TABLE;
    public static final ForgeConfigSpec.ConfigValue<Long> MOOD_TICK_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<Integer> NEUTRAL_MOOD;
    public static final ForgeConfigSpec.ConfigValue<Long> MOOD_EFFECT_DURATION_ATE_UNCOMFORTABLY;
    public static final ForgeConfigSpec.ConfigValue<Long> MOOD_EFFECT_DURATION_ATE_COMFORTABLY;


    static {
        // Scanning Config
        BUILDER.push("Questown.Config.Scanning");
        SCAN_FOR_DOORS = BUILDER.comment(
                "RISKY: Set true to scan for vanilla doors in a radius around the town flag during room detection. " +
                        "This may be prone to crashes if rooms get too complex."
        ).define("ScanForDoors", false);
        DOOR_SEARCH_RADIUS = BUILDER.comment(
                "The radius (around the town flag) where this mod will search for doors for room detection"
        ).define("DoorSearchRadius", 100);
        TOWN_TICK_RADIUS = BUILDER.comment(
                "The radius (around the town flag) where this mod will search for players. If no players are found, the flag will stop ticking."
        ).define("TownTickRadius", 10000);
        CAMPFIRE_SEARCH_RADIUS = BUILDER.comment(
                "The radius (around the town flag) where this mod will search for campfires which attract visitors"
        ).define("CampfireSearchRadius", 10);
        BIOME_SCAN_RADIUS = BUILDER.comment(
                "The radius of chunks that will be scanned outward (in a plus shape) from the flag for the purpose of populating gatherer loot"
        ).defineInRange("BiomeScanRadius", 20, 0, 100);
        BUILDER.pop();

        // Quests Config
        BUILDER.push("Quests").comment(
                "Quests are pre-generated as soon as possible. When a batch of quests is consumed, another pre" +
                        "-generation begins. These options control that process."
        );
        LOG_QUEST_BATCH_GENERATION = BUILDER.comment(
                "If set to True, quest pre-generation will be printed verbosely to the debug logs. This can be quite noisy."
        ).define("LogQuestBatchGeneration", false);
        IDEAL_QUEST_THRESHOLD_TICKS = BUILDER.comment(
                "When a new batch of quests is added, the mod makes several attempts to find a random" +
                        ", complex room quest to add to the batch. This setting determines how many ticks" +
                        " it will try before giving up and filling the empty space with simpler quests."
        ).define("IdealQuestThresholdTicks", 25);
        QUEST_GENERATION_MAX_TICKS = BUILDER.comment(
                "When a new batch of quests is added, the mod makes several attempts to find quests to add" +
                        " to the batch randomly. This determines how many ticks it will try before giving up and " +
                        "leaving the remaining space empty."
        ).define("QuestGenerationMaxTicks", 50);
        DEFAULT_ITEM_WEIGHT = BUILDER.comment(
                "The default weight that will be assigned to items when choosing new quests. " +
                        "See questown-item-weights-server.toml"
        ).define("DefaultItemWeight", 100);
        MIN_WEIGHT_PER_QUEST_BATCH = BUILDER.comment(
                "Minimum weight of quests in a batch. Quests are weighted by the items in the room. See questown-item-weights-server.toml"
        ).defineInRange("MinWeightPerQuestBatch", 100, 0, 500);
        QUEST_BATCH_VILLAGER_BOOST_FACTOR = BUILDER.comment(
                "The minimum weight for a quest batch includes this factor, which is multiplied by the number of villagers. This causes a village to have an exponential difficulty curve as it grows."
        ).defineInRange("QuestBatchVillagerBoostFactor", 100, 0, 500);
        DUPLICATE_QUEST_COST_FACTOR = BUILDER.comment(
                "When new batches of quests are calculated, a request for a room to be built AGAIN is more expensive. This is done to discourage lots of repeated quests. This factor determines how much more expensive it is."
        ).defineInRange("DuplicateQuestCostFactor", 1.5, 1.0, 100.0);
        BUILDER.pop();

        // Jobs Config
        BUILDER.push("Jobs");
        MAX_TICKS_WITHOUT_SUPPLIES = BUILDER.comment(
                "If the town is missing the supplies that the villager needs to do their job, they will wait some time for those supplies to be generated/added. After these ticks, they will give up and go back to the job board"
        ).defineInRange("MaxTicksWithoutSupplies", 100L, 1L, 24000L);
        FARM_ACTION_INTERVAL = BUILDER.comment(
                "The number of ticks that farmers will wait between actions"
        ).define("FarmActionInterval", 100);
        BAKING_TIME_REQUIRED_BASELINE = BUILDER.comment(
                "The number of ticks it takes for a villager to bake bread"
        ).defineInRange("BakingTime", 1000, 1000, 24000);
        FARMER_WEEDS_RARITY = BUILDER.comment(
                "The chance that a farmer will find weeds (actually grass, for composting) on a still-growing crop block. 1 means \"constantly\"."
        ).defineInRange("FarmerWeedsRarity", 1000, 1, 9999);
        SMELTER_WORK_REQUIRED = BUILDER.comment(
                "The number of times a smelter must work on a block of ore to extract the raw materials inside"
        ).defineInRange("SmelterWorkRequired", 10, 1, 10);
        GATHERER_TIME_REQUIRED_BASELINE = BUILDER.comment(
                "The number of ticks the gatherer/explorer will spend outside of town collecting items. All villagers will start with this baseline, but it might get altered by villager or town modifiers."
        ).defineInRange("GathererTimeRequiredBaseline", 200, 1, 24000);
        JOB_BOARD_ENABLED = BUILDER.comment(
                "Experimental: Villagers will choose work based on the items the player has selected at a \"job board\" registered in the town."
        ).define("JobBoardEnabled", true);
        BUILDER.pop();

        // Villagers Config
        BUILDER.push("Villagers");
        WANDER_GIVEUP_TICKS = BUILDER.comment(
                "The limit of time that villagers will spend trying to reach their next destination"
        ).defineInRange("WanderGiveUpTicks", 2000, 1, 24000);
        BASE_FULLNESS = BUILDER.comment(
                "The amount of fullness that a typical villager starts with. Fullness ticks down throughout the day. " +
                        "When it reaches zero, the villager will seek out food."
        ).defineInRange("BaseFullness", 5000, 1, 24000);
        HUNGER_ENABLED = BUILDER.comment(
                "Enables a hunger system. Villagers will get more hungry throughout the day and, upon reaching zero, will switch their job to \"dining\" and seek out a dining room to eat in."
        ).define("HungerEnabled", false);
        MAX_TICKS_WITHOUT_DINING_TABLE = BUILDER.comment(
                "The maximum number of ticks that a hungry villager will spend trying to find a dinner plate to eat at. " +
                        "After these ticks expire, they will go to the town flag to eat - they will receive a work penalty " +
                        "for eating uncomfortably."
        ).defineInRange("MaxTicksWithoutDiningTable", 2000L, 1L, 24000L);

        // Villager Moods Config
        BUILDER.push("Moods");
        MOOD_TICK_INTERVAL = BUILDER.comment(
                "How often (in ticks) the game will assess a villagers mood effects to determine their overall \"mood\""
        ).defineInRange("MoodTickInterval", 10L, 1L, 24000L);
        NEUTRAL_MOOD = BUILDER.comment(
                "The default mood level for villagers who have no active mood effects"
        ).defineInRange("NeutralMood", 75, 0, 100);

        // Villager Mood Effect Durations
        BUILDER.push("EffectDuration");
        MOOD_EFFECT_DURATION_ATE_UNCOMFORTABLY = BUILDER.comment(
                "When villagers eat uncomfortably"
        ).defineInRange("AteUncomfortably", 3000L, 0L, 24000L);
        MOOD_EFFECT_DURATION_ATE_COMFORTABLY = BUILDER.comment(
                "When villagers eat comfortably"
        ).defineInRange("AteComfortably", 3000L, 0L, 24000L);

        // End Mood Effect Durations
        BUILDER.pop();

        // End Moods
        BUILDER.pop();

        // End Villagers
        BUILDER.pop();

        // Advanced Config
        BUILDER.push("Advanced");
        BASE_MAX_LOOP = BUILDER.comment(
                "The maximum number of attempts this mod will allow for ANY algorithm. This helps prevent bugs from causing the game to freeze."
        ).defineInRange("BaseMaxLoop", 1000, 1, Integer.MAX_VALUE);
        TICK_SAMPLING_RATE = BUILDER.comment(
                "For profiling minecraft server performance. 0 means OFF and may reduce log bloat."
        ).defineInRange("TickSamplingRate", 0, 0, 24000);
        FLAG_SUB_BLOCK_RETENTION_TICKS = BUILDER.comment(
                "To prevent confusion due to orphaning, blocks like job boards and welcome mats are removed when their flag is destroyed."
        ).defineInRange("FlagSubBlockRetentionTicks", 20, 1, 1000);
        FLAG_SUB_BLOCK_REMOVED_TICKS = BUILDER.comment(
                "The town flag handles dropping the items for sub blocks that are removed. This is the number of ticks it takes to decide that a sub block is gone."
        ).defineInRange("FlagSubBlockRemovedTicks", 2, 1, 1000);
        FLAG_SUB_BLOCK_DETECTION_TICKS = BUILDER.comment(
                "It may take a few ticks before the entity for the sub block shows up in the world. If the number exceeds this config value, the server will crash."
        ).defineInRange("FlagSubBlockDetectionTicks", 100, 1, 1000);
        META_ROOM_DIAMETER = BUILDER.comment(
                "The radius of \"Meta-Rooms\" that exist around points of interest in the town. E.g." +
                "There is a meta room around the town flag itself, and one around each welcome mat"
        ).defineInRange("MetaRoomDiameter", 2, 1, 100);

        // Time Warp
        BUILDER.push("TimeWarp").comment(
                "Villages do a 'time warp' when the player returns from away - to simulate villager activity. This is an experimental feature."
        );
        CRASH_ON_FAILED_WARP = BUILDER.comment(
                "Since this is an experimental feature that is likely to crash. By default, problems will be ignored at the cost of lost village productivity."
        ).define("CrashOnFailure", false);
        TIME_WARP_MAX_TICKS = BUILDER.comment(
                "Since the player can be gone for a very long time, we enforce a maximum warp to prevent the warp taking too long to compute."
        ).defineInRange("MaxTicks", 200000, 1, Integer.MAX_VALUE);
        BLOCK_CLAIMS_TICK_LIMIT = BUILDER.comment(
                "If a job claims a block. It will hold that claim for this many ticks. (Or until they finish their work, whatever happens first)"
        ).defineInRange("BlockClaimsTickLimit", 1000L, 1, 24000);
        BUILDER.pop(); // Yep, really thrice. Getting out of nested config
        BUILDER.pop();
        BUILDER.pop();

        // Cheats
        BUILDER.push("Cheats");
        INFINITE_TOWN_DOORS = BUILDER.comment(
                "CHEAT: To make town-building easier, town doors can be made non-consumable."
        ).define("InfiniteTownDoors", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
