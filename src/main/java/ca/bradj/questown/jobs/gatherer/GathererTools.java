package ca.bradj.questown.jobs.gatherer;

public class GathererTools {

    public record LootTableParameters(LootTablePrefix prefix, LootTablePath fallback) {}
    public record LootTablePrefix(String value) {}
    public record LootTablePath(String path) {}

    public static final LootTablePrefix NO_TOOL_TABLE_PREFIX = new LootTablePrefix("jobs/gatherer_notool");
    public static final LootTablePath NO_TOOL_LOOT_TABLE_DEFAULT = new LootTablePath("jobs/gatherer_notool/default");
    public static final LootTablePrefix AXE_LOOT_TABLE_PREFIX = new LootTablePrefix("jobs/gatherer_axe");
    public static final LootTablePath AXE_LOOT_TABLE_DEFAULT = new LootTablePath("jobs/gatherer_axe/default");
    public static final LootTablePrefix PICKAXE_LOOT_TABLE_PREFIX = new LootTablePrefix("jobs/gatherer_pickaxe");
    public static final LootTablePath PICKAXE_LOOT_TABLE_DEFAULT = new LootTablePath("jobs/gatherer_pickaxe/default");
    public static final LootTablePrefix SHOVEL_LOOT_TABLE_PREFIX = new LootTablePrefix("jobs/gatherer_shovel");
    public static final LootTablePath SHOVEL_LOOT_TABLE_DEFAULT = new LootTablePath("jobs/gatherer_shovel/default");
    public static final LootTablePrefix FISHING_LOOT_TABLE_PREFIX = new LootTablePrefix("jobs/gatherer_fishing");
    public static final LootTablePath FISHING_LOOT_TABLE_DEFAULT = new LootTablePath("jobs/gatherer_fishing/default");

}
