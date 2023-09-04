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

    static {
        BUILDER.push("Questown.Config");
        DOOR_SEARCH_RADIUS = BUILDER.comment(
                "The radius (around the town flag) where this mod will search for doors for room detection"
        ).define("DoorSearchRadius", 100);
        CAMPFIRE_SEARCH_RADIUS = BUILDER.comment(
                "The radius (around the town flag) where this mod will search for campfires which attract visitors"
        ).define("DoorSearchRadius", 10);
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
        FARM_ACTION_INTERVAL = BUILDER.comment(
                "The number of ticks that farmers will wait between actions"
        ).define("DefaultItemWeight", 100);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
