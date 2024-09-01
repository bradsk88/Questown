package ca.bradj.questown.jobs.declarative.meta;

import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.SoundInfo;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class DinerRawFoodWork {
    public static final String ID = "dining_raw_food";

    public static final int BLOCK_STATE_NEED_EAT = 0;
    public static final int BLOCK_STATE_CONSUME_FOOD = 1;
    public static final int BLOCK_STATE_DONE = 2;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    private static final Ingredient INGREDIENTS = Ingredient.of(TagsInit.Items.VILLAGER_RAW_FOOD);

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_CONSUME_FOOD, INGREDIENTS
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_CONSUME_FOOD, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            // Food is listed as a "tool" so the villager will render it in hand while they eat
            BLOCK_STATE_NEED_EAT, Ingredient.of(TagsInit.Items.VILLAGER_RAW_FOOD),
            BLOCK_STATE_CONSUME_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_RAW_FOOD)
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_EAT, 25,
            BLOCK_STATE_CONSUME_FOOD, 0,
            BLOCK_STATE_DONE, 0
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_EAT, 0,
            BLOCK_STATE_CONSUME_FOOD, 0,
            BLOCK_STATE_DONE, 0
    );

    private static final Collection<ItemStack> RESULTS = ImmutableList.of(
            EffectMetaItem.withLastingEffect(EffectMetaItem.MoodEffects.UNCOMFORTABLE_EATING, Compat.configGet(Config.MOOD_EFFECT_DURATION_ATE_UNCOMFORTABLY).get()),
            EffectMetaItem.withLastingEffect(EffectMetaItem.MoodEffects.ATE_RAW_FOOD, Compat.configGet(Config.MOOD_EFFECT_DURATION_ATE_UNCOMFORTABLY).get())
    );
    public static final int PAUSE_FOR_ACTION = 10;

    public static Work asWork(
            String rootId
    ) {
        return productionWork(
                null,
                Items.APPLE.getDefaultInstance(),
                new JobID(rootId, ID),
                WorksBehaviour.noResultDescription(),
                new WorkLocation(
                        WorkLocation.isBlock(TownFlagBlock.class),
                        SpecialQuests.TOWN_FLAG
                ),
                new WorkStates(
                        MAX_STATE,
                        Util.constant(INGREDIENTS_REQUIRED_AT_STATES),
                        Util.constant(INGREDIENT_QTY_REQUIRED_AT_STATES),
                        Util.constant(TOOLS_REQUIRED_AT_STATES),
                        Util.constant(WORK_REQUIRED_AT_STATES),
                        Util.constant(TIME_REQUIRED_AT_STATES)
                ),
                new WorkWorldInteractions(
                        PAUSE_FOR_ACTION,
                        (lvl, hand) -> MCHeldItem.fromMCItemStacks(RESULTS)
                ),
                new WorkSpecialRules(
                        ImmutableMap.of(
                                ProductionStatus.EXTRACTING_PRODUCT,
                                ImmutableList.of(SpecialRules.HUNGER_FILL_HALF)
                        ), // No stage rules
                        ImmutableList.of(
                                SpecialRules.WORK_IN_EVENING
                        )
                ),
                new SoundInfo(SoundEvents.GENERIC_EAT.getLocation(), 10, null),
                new ExpirationRules(
                        () -> Long.MAX_VALUE,
                        () -> Long.MAX_VALUE,
                        jobId -> jobId,
                        () -> Long.MAX_VALUE,
                        jobId -> jobId
                )
        ).withNeeds(s -> ImmutableList.of(INGREDIENTS));
    }

    public static JobID getIdForRoot(String rootId) {
        return new JobID(rootId, ID);
    }

    public static boolean isDining(JobID jobName) {
        return ID.equals(jobName.jobId());
    }
}
