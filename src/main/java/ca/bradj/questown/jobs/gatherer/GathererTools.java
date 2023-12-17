package ca.bradj.questown.jobs.gatherer;

public class GathererTools {

    public record LootTableParameters(LootTablePrefix prefix, LootTablePath path) {}
    public record LootTablePrefix(String value) {}
    public record LootTablePath(String path) {}

    public static final LootTablePrefix NO_TOOL_TABLE_PREFIX = new LootTablePrefix("jobs/gatherer_notools");
    public static final LootTablePrefix AXE_LOOT_TABLE_PREFIX = new LootTablePrefix("jobs/gatherer_axe");
    public static final LootTablePath AXE_LOOT_TABLE_DEFAULT = new LootTablePath("jobs/gatherer_axe/default");

}
