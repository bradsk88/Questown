package ca.bradj.questown.core;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final String VILLAGE_START_QUESTS = "Village start quests";

    public static final String FILENAME = "questown-server.toml";

    public static final ForgeConfigSpec.ConfigValue<Integer> DOOR_SEARCH_RADIUS;

    public static final ForgeConfigSpec.ConfigValue<Integer> CAMPFIRE_SEARCH_RADIUS;

    public static final ForgeConfigSpec.ConfigValue<Integer> IDEAL_QUEST_THRESHOLD_TICKS;

    public static final ForgeConfigSpec.ConfigValue<Integer> QUEST_GENERATION_MAX_TICKS;

    public static final ForgeConfigSpec.ConfigValue<Integer> DEFAULT_ITEM_WEIGHT;

    public static final ForgeConfigSpec.ConfigValue<Integer> FARM_ACTION_INTERVAL;

    public static final ForgeConfigSpec.ConfigValue<Integer> BAKING_TIME;

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

    static {
        // Scanning Config
        BUILDER.push("Questown.Config.Scanning");
        DOOR_SEARCH_RADIUS = BUILDER.comment(
                "The radius (around the town flag) where this mod will search for doors for room detection"
        ).define("DoorSearchRadius", 100);
        CAMPFIRE_SEARCH_RADIUS = BUILDER.comment(
                "The radius (around the town flag) where this mod will search for campfires which attract visitors"
        ).define("CampfireSearchRadius", 10);
        SCAN_FOR_DOORS = BUILDER.comment(
                "RISKY: Set true to scan for vanilla doors in a radius around the town flag during room detection. " +
                        "This may be prone to crashes if rooms get too complex."
        ).define("ScanForDoors", false);
        BIOME_SCAN_RADIUS = BUILDER.comment(
                "The radius of chunks that will be scanned outward (in a plus shape) from the flag for the purpose of populating gatherer loot"
        ).defineInRange("BiomeScanRadius", 20, 0, 100);
        BUILDER.pop();

        // Quests Config
        BUILDER.push("Quests");
        IDEAL_QUEST_THRESHOLD_TICKS = BUILDER.comment(
                "When a new batch of quests is added, the mod makes several attempts to find a large quest to add" +
                        " to the batch randomly. This determines how many ticks it will try before giving up and " +
                        "filling the empty space with simpler quests."
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
        FARM_ACTION_INTERVAL = BUILDER.comment(
                "The number of ticks that farmers will wait between actions"
        ).define("FarmActionInterval", 100);
        BAKING_TIME = BUILDER.comment(
                "The number of ticks it takes for a villager to bake bread (will be rounded down to nearest 1000)"
        ).defineInRange("BakingTime", 6000, 1000, 24000);
        FARMER_WEEDS_RARITY = BUILDER.comment(
                "The chance that a farmer will find weeds (actually grass, for composting) on a still-growing crop block. 1 means \"constantly\"."
        ).defineInRange("FarmerWeedsRarity", 10, 1, 9999);
        SMELTER_WORK_REQUIRED = BUILDER.comment(
                "The number of times a smelter must work on a block of ore to extract the raw materials inside"
        ).defineInRange("SmelterWorkRequired", 10, 1, 10);
        BUILDER.pop();

        // Villagers Config
        BUILDER.push("Villagers");
        WANDER_GIVEUP_TICKS = BUILDER.comment(
                "The limit of time that villagers will spend trying to reach their next destination"
        ).defineInRange("WanderGiveUpTicks", 2000, 1, 24000);
        BUILDER.pop();

        // Advanced Config
        BUILDER.push("Advanced");
        TICK_SAMPLING_RATE = BUILDER.comment(
                "For profiling minecraft server performance. 0 means OFF and may reduce log bloat."
        ).defineInRange("TickSamplingRate", 0, 0, 24000);
        FLAG_SUB_BLOCK_RETENTION_TICKS = BUILDER.comment(
                "To prevent confusion due to orphaning, blocks like job boards and welcome mats are removed when their flag is destroyed."
        ).defineInRange("FlagSubBlockRetentionTicks", 20, 1, 1000);
        BUILDER.pop(); // Yep, really twice. Getting out of config
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
