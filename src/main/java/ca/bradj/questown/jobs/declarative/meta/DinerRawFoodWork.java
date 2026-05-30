package ca.bradj.questown.jobs.declarative.meta;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.SoundInfo;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
            BLOCK_STATE_NEED_EAT, Ingredient.of(TagsInit.Items.VILLAGER_RAW_FOOD)
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

    public static final int PAUSE_FOR_ACTION = 10;

    public static Work asWork(
            String rootId
    ) {
        return productionWork(
                null,
                Items.APPLE.getDefaultInstance(),
                new JobID(rootId, ID),
                WorksBehaviour.noResultDescription(),
                SpecialQuests.TOWN_FLAG_LOCATION, // TODO: Allow villagers to eat raw food in dining room
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
                        new ResultGenerator<>() {
                            @Override
                            public Iterable<MCHeldItem> generate(
                                    ServerLevel level,
                                    Collection<MCHeldItem> heldItems
                            ) {
                                return ImmutableList.of();
                            }

                            @Override
                            public boolean isResultAlwaysEmpty() {
                                return true;
                            }
                        }
                ),
                new WorkSpecialRules(
                        ImmutableMap.of(
                                ProductionStatus.EXTRACTING_PRODUCT,
                                ImmutableList.of(
                                        SpecialRules.HUNGER_FILL_HALF,
                                        SpecialRules.APPLY_UNCOMFORTABLE_EATING,
                                        SpecialRules.APPLY_ATE_RAW_FOOD
                                )
                        ), // No stage rules
                        ImmutableList.of(
                                SpecialRules.WORK_IN_EVENING,
                                SpecialRules.NO_EXPERIENCE_GAINED
                        )
                ),
                new SoundInfo(SoundEvents.GENERIC_EAT.getLocation(), 10, null),
                new ExpirationRules(
                        Compat.configGet(Config.MAX_TICKS_WITHOUT_DINING_TABLE),
                        Compat.configGet(Config.MAX_TICKS_WITHOUT_DINING_TABLE),
                        jobId -> WorkSeekerJob.getIDForRoot(new JobID(rootId, ID)),
                        Compat.configGet(Config.MAX_TICKS_WITHOUT_FOOD),
                        jobId -> WorkSeekerJob.getIDForRoot(new JobID(rootId, ID))
                )
        ).withNeeds((items) -> ImmutableList.of(INGREDIENTS));
    }

    public static JobID getIdForRoot(String rootId) {
        return new JobID(rootId, ID);
    }

    public static boolean isDining(JobID jobName) {
        return ID.equals(jobName.jobId());
    }
}
