package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.*;
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

public class DinerNoTableWork {
    public static final String ID = "dining_no_table";

    public static final int BLOCK_STATE_NEED_EAT = 0;
    public static final int BLOCK_STATE_CONSUME_FOOD = 1;
    public static final int BLOCK_STATE_DONE = 2;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_CONSUME_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_CONSUME_FOOD, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            // Food is listed as a "tool" so the villager will render it in hand while they eat
            BLOCK_STATE_NEED_EAT, Ingredient.of(TagsInit.Items.VILLAGER_FOOD),
            BLOCK_STATE_CONSUME_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD)
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
            EffectMetaItem.withConsumableEffect(EffectMetaItem.ConsumableEffects.FILL_HUNGER),
            EffectMetaItem.withLastingEffect(EffectMetaItem.MoodEffects.UNCOMFORTABLE_EATING, Config.MOOD_EFFECT_DURATION_ATE_UNCOMFORTABLY.get())
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
                        (block) -> block instanceof TownFlagBlock,
                        SpecialQuests.TOWN_FLAG
                ),
                new WorkStates(
                        MAX_STATE,
                        Util.constant(INGREDIENTS_REQUIRED_AT_STATES),
                        Util.constant(INGREDIENT_QTY_REQUIRED_AT_STATES),
                        Util.constant(TOOLS_REQUIRED_AT_STATES),
                        Util.constant(WORK_REQUIRED_AT_STATES),
                        Util.constant(TIME_REQUIRED_AT_STATES),
                        WorksBehaviour.standardPriority()
                ),
                new WorkWorldInteractions(
                        PAUSE_FOR_ACTION,
                        (lvl, hand) -> MCHeldItem.fromMCItemStacks(RESULTS)
                ),
                new WorkSpecialRules(
                        ImmutableMap.of(), // No stage rules
                        ImmutableList.of(
                                SpecialRules.WORK_IN_EVENING
                        )
                ),
                SoundEvents.GENERIC_EAT.getLocation(),
                new ExpirationRules(
                        () -> Long.MAX_VALUE,
                        jobId -> jobId,
                        // TODO: Change this to "max ticks without food" and switch to "town abandoner" job
                        () -> Long.MAX_VALUE,
                        jobId -> jobId
                )
        );
    }

    public static JobID getIdForRoot(String rootId) {
        return new JobID(rootId, ID);
    }

    public static boolean isDining(JobID jobName) {
        return ID.equals(jobName.jobId());
    }
}
