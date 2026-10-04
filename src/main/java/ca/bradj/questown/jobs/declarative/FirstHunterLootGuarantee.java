package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobID;
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

/**
 * Prepends a raw-meat drop (mutton) to a hunter result set once per town —
 * the hunter-equivalent of {@link ca.bradj.questown.mobs.helperchicken.ChickenArcLootGuarantee}
 * (ADR-0014 — Worldly Seeds reach the player through the first gatherer, not a recipe).
 *
 * <p>Goal: close the raw-food LOOT quest deterministically on the first hunter delivery
 * so the tutorial's cook unlock is not seed-dependent. Without this, the
 * {@code self_sufficiency} autotest runs into a ~50% "cook-never-unlocks" rate —
 * the town's work-seeker scores the sword job as available, but
 * {@code canFitInDay}/the townie's day budget gates the transition on a
 * window that never opens in most seeds.
 *
 * <p>Idempotent: the guard reads {@code firstHunterLootGuaranteeFired} on the
 * flag BE, which flips at the first raw-food LOOT extraction. The two wrap
 * sites ({@code RealtimeWorldInteraction.getResults} and
 * {@code TimeWarpWorldInteraction.getResults}) therefore share this helper
 * without double-counting — the bit guarantees exactly-one-prepend per town.
 *
 * <p>Unlike the chicken-arc prepend, this is not tied to the arc — it fires
 * whenever the raw-food LOOT is still open, the job's root is {@code hunter},
 * and the once-bit has not fired.
 */
public final class FirstHunterLootGuarantee {

    private FirstHunterLootGuarantee() {
    }

    /** The item prepended. Mutton — the hunter/sword job's canonical food. */
    public static final Item ITEM = Items.MUTTON;

    /**
     * True if the extracted item is a raw-food in the {<code>#questown:villager_simple_furnace_food_raw</code>}
     * tag (rabbit, porkchop, beef, mutton, chicken, ...). Any of them closes the raw-food LOOT — so
     * the once-bit flip should fire on any of them, not just mutton.
     */
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
        return rawFoodLootQuestActive(flag);
    }

    /**
     * True when the raw-food LOOT quest ({@code #questown:villager_simple_furnace_food_raw})
     * is still open, i.e. the town has not yet produced enough raw food to unlock the
     * cook. Once it closes, the guarantee is over.
     */
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

    /** The once-bit flip on the flag. Called by the LOOT-extract hook after {@code processLootQuests}. */
    public static void flipOnceBit(TownFlagBlockEntity flag) {
        if (flag == null || flag.isFirstHunterLootGuaranteeFired()) {
            return;
        }
        flag.setFirstHunterLootGuaranteeFired(true);
        flag.writeTownData(ca.bradj.questown.mc.Compat.getBlockStoredTagData(flag));
        flag.setChanged();
    }
}
