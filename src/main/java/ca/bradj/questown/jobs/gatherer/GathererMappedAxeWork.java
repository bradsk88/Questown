package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Journal;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GathererMappedAxeWork extends DeclarativeJob {
    public static final JobID ID = new JobID("gatherer", "mapped_axe");

    // TODO: Can we somehow allow gatherers to bring all three at once?
    //  By using discrete "states", we force the gatherer to bring food to the
    //  town gate, then go fetch a map, then an axe, before they can finally
    //  leave town.
    public static final int BLOCK_STATE_NEED_FOOD = 0;
    public static final int BLOCK_STATE_NEED_MAP = 1;
    public static final int BLOCK_STATE_NEED_WORK = 3;
    public static final int BLOCK_STATE_DONE = 3;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD),
            BLOCK_STATE_NEED_MAP, Ingredient.of(ItemsInit.GATHERER_MAP.get())
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 1,
            BLOCK_STATE_NEED_MAP, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_WORK, Ingredient.of(TagsInit.Items.AXES)
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 0,
            BLOCK_STATE_NEED_MAP, 0,
            // 16000 is the difference between EVENING and MORNING per Signals.
            // And 100 is the default
            BLOCK_STATE_NEED_WORK, 1
    );

    public GathererMappedAxeWork(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                ID,
                new ResourceLocation(Questown.MODID, "crafting_room"),
                MAX_STATE,
                true,
                16000, // The number of ticks between MORNING and EVENING (if they leave late, they get back late)
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                GathererMappedAxeWork::getFromLootTables
        );
    }

    // Note: this is still declarative. In a file, we would just specify something like:
    // - Strategy: "loot_tables"
    // - Prefix: "jobs/axe"
    // - Default "jobs/axe/default"
    private static Iterable<ItemStack> getFromLootTables(
            ServerLevel level,
            Journal<?, MCHeldItem, ?> journal
    ) {
        GathererTools.LootTablePrefix ltPrefix = GathererTools.AXE_LOOT_TABLE_PREFIX;
        GathererTools.LootTablePath defaultLT = GathererTools.AXE_LOOT_TABLE_DEFAULT;
        return Loots.getFromLootTables(level, journal, ltPrefix, defaultLT);
    }
}
