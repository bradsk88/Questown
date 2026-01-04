package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.gui.StatusArt;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

// DowntimeWork represents a job where a villager takes downtime, typically walking around town randomly,
// with a preference for visiting other villagers. This job has ten states, giving us the ability to
// trigger the "random workspot" special rule multiple times. But the villager might change back to
// working before completing all ten states - for example, if they have an "introvert" trait (although
// traits don't exist yet).
public class DowntimeWork {
    public static final String ID = "downtime";

    public static final int BLOCK_STATE_ZERO = 0;
    public static final int BLOCK_STATE_ONE = 1;
    public static final int BLOCK_STATE_TWO = 2;
    public static final int BLOCK_STATE_THREE = 3;
    public static final int BLOCK_STATE_FOUR = 4;
    public static final int BLOCK_STATE_FIVE = 5;
    public static final int BLOCK_STATE_SIX = 6;
    public static final int BLOCK_STATE_SEVEN = 7;
    public static final int BLOCK_STATE_EIGHT = 8;
    public static final int BLOCK_STATE_DONE = 9;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of();
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of();
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of();
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_ZERO, 1,
            BLOCK_STATE_ONE, 1,
            BLOCK_STATE_TWO, 1,
            BLOCK_STATE_THREE, 1,
            BLOCK_STATE_FOUR, 1,
            BLOCK_STATE_FIVE, 1,
            BLOCK_STATE_SIX, 1,
            BLOCK_STATE_SEVEN, 1,
            BLOCK_STATE_EIGHT, 1

    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of();
    public static final int PAUSE_FOR_ACTION = 100;

    // This is only used for work_state storage because of RANDOM_WORKSPOT_PREFER_SOCIAL
    public static final WorkLocation ARBITRARY_LOCATION = new WorkLocation(
            (ctx) -> isFlag(ctx.blockInfo(), ctx.blockPos()),
            DowntimeWork::isFlag,
            SpecialQuests.TOWN_FLAG
    );

    public static Work asWork(
            String rootId
    ) {
        return productionWork(
                null,
                Blocks.BLACK_BED.asItem().getDefaultInstance(),
                new JobID(rootId, ID),
                WorksBehaviour.noResultDescription(),
                ARBITRARY_LOCATION,
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
                        WorkWorldInteractions.ALWAYS_EMPTY_RESULT_GENERATOR
                ),
                new WorkSpecialRules(
                        ImmutableMap.of(), // No stage rules
                        ImmutableList.of(
                                SpecialRules.NO_EXPERIENCE_GAINED,
                                SpecialRules.SLOW_WALK,
                                SpecialRules.ALWAYS_CONSIDER,
                                SpecialRules.ALWAYS_POPULATE_JOBSITE,
                                SpecialRules.RANDOM_SHORT_LIVED_WORKSPOT_PREFER_SOCIAL,
                                SpecialRules.RANDOM_DOWNTIME_POSE,
                                SpecialRules.WORK_IN_EVENING
                        )
                ),
                null,
                new ExpirationRules(
                        () -> Long.MAX_VALUE,
                        () -> Long.MAX_VALUE,
                        jobId -> jobId,
                        Compat.configGet(Config.MAX_DOWNTIME_TICKS),
                        WorkSeekerJob::getIDForRoot
                )
        ).withOverrides(new Overrides(
                ImmutableMap.of(
                        ProductionStatus.NO_JOBSITE,
                        StatusArt.getTexture(getIdForRoot("___"), ProductionStatus.IDLE)
                ),
                ImmutableMap.of(ProductionStatus.NO_JOBSITE, new Pair<>(
                        "questown.tooltips.villagers.job_common.status_1.DOWNTIME",
                        "questown.tooltips.villagers.job_common.click_to_learn"
                ))
        ));
    }

    public static JobID getIdFor(JobID rootId) {
        return new JobID(rootId.rootId(), ID);
    }

    public static JobID getIdForRoot(String rootId) {
        return new JobID(rootId, ID);
    }

    public static boolean matches(JobID jobName) {
        return ID.equals(jobName.jobId());
    }

    private static boolean isFlag(
            WorkLocation.BlockInfo i,
            BlockPos p
    ) {
        return WorkLocation.isBlock(TownFlagBlock.class).test(i, p);
    }
}
