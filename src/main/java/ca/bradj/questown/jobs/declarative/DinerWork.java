package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.blocks.PlateBlock;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class DinerWork {
    public static final String ID = "dining";

    public static final int BLOCK_STATE_NEED_FOOD = 0;
    public static final int BLOCK_STATE_NEED_EAT = 1;
    public static final int BLOCK_STATE_DONE = 2;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, Ingredient.of(TagsInit.Items.VILLAGER_FOOD)
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 0,
            BLOCK_STATE_NEED_EAT, 100,
            BLOCK_STATE_DONE, 0
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FOOD, 0,
            BLOCK_STATE_NEED_EAT, 0,
            BLOCK_STATE_DONE, 0
    );

    public static final ItemStack RESULT = EffectMetaItem.applyEffect(
            ItemsInit.EFFECT.get().getDefaultInstance(), EffectMetaItem.Effects.FILL_HUNGER
    );
    public static final int PAUSE_FOR_ACTION = 10;

    public static Work asWork(
            String rootId
    ) {
        return productionWork(
                new JobID(rootId, ID),
                WorksBehaviour.standardDescription(() -> RESULT),
                new WorkLocation(
                        (block) -> block instanceof PlateBlock,
                        SpecialQuests.DINING_ROOM
                ),
                new WorkStates(
                        MAX_STATE,
                        INGREDIENTS_REQUIRED_AT_STATES,
                        INGREDIENT_QTY_REQUIRED_AT_STATES,
                        TOOLS_REQUIRED_AT_STATES,
                        WORK_REQUIRED_AT_STATES,
                        TIME_REQUIRED_AT_STATES
                ),
                WorksBehaviour.standardWorldInteractions(
                        PAUSE_FOR_ACTION, () -> RESULT
                ),
                new WorkSpecialRules(
                        ImmutableMap.of(), // No stage rules
                        ImmutableList.of(
                                SpecialRules.SHARED_WORK_STATUS,
                                SpecialRules.CLAIM_SPOT
                        )
                ),
                SoundEvents.GENERIC_EAT.getLocation(),
                new ExpirationRules(
                        // TODO[ASAP]: Fall back to eating without table
                        //  (currently just keeps trying to dine)
                        Integer.MAX_VALUE,
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
