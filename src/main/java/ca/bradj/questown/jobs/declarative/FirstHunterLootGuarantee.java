package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.MetaJobs;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.QuestsHolder;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

/** Adds a mutton to the first hunt's results once per town, so the raw-food quest closes on the first delivery. */
public final class FirstHunterLootGuarantee {

    private FirstHunterLootGuarantee() {
    }

    public static final Item ITEM = Items.MUTTON;

    /** True for any raw meat in {@code #questown:villager_simple_furnace_food_raw}; any of them closes the raw-food quest. */
    public static boolean matchesFirstHunterItem(MCHeldItem item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        return item.get().toMCItemStack()                .is(TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW);
    }

    public static Iterable<MCHeldItem> maybePrepend(
            TownFlagBlockEntity flag,
            JobID jobId,
            Iterable<MCHeldItem> base
    ) {
        if (!shouldPrepend(flag, jobId)) {
            return base;
        }
        MCHeldItem meat = MCHeldItem.fromMCItemStack(new ItemStack(ITEM));
        List<MCHeldItem> out = new ArrayList<>();
        out.add(meat);
        base.forEach(out::add);
        return out;
    }

    public static boolean shouldPrepend(TownFlagBlockEntity flag, JobID jobId) {
        if (flag == null) {
            return false;
        }
        if (flag.isFirstHunterLootGuaranteeFired()) {
            return false;
        }
        if (jobId == null || !"hunter".equals(jobId.rootId())) {
            return false;
        }
        // A meal is not a hunt.
        if (MetaJobs.isMeta(jobId)) {
            return false;
        }
        return rawFoodLootQuestActive(flag);
    }

    /** True while the raw-food LOOT quest is open. */
    public static boolean rawFoodLootQuestActive(TownFlagBlockEntity flag) {
        QuestsHolder qh = flag.getQuestHandle();
        if (qh == null) {
            return false;
        }
        ResourceLocation wanted = TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location();
        for (AbstractMap.SimpleEntry<MCQuest, ?> entry : qh.getAllQuestsWithRewards()) {
            MCQuest q = entry.getKey();
            if (q.getType() == Quest.QuestType.LOOT
                    && !q.isComplete()
                    && wanted.equals(q.getWantedId())) {
                return true;
            }
        }
        return false;
    }

    /** Marks the guarantee as used. */
    public static void flipOnceBit(TownFlagBlockEntity flag) {
        if (flag == null || flag.isFirstHunterLootGuaranteeFired()) {
            return;
        }
        flag.setFirstHunterLootGuaranteeFired(true);
        flag.writeTownData(ca.bradj.questown.mc.Compat.getBlockStoredTagData(flag));
        flag.setChanged();
    }
}
