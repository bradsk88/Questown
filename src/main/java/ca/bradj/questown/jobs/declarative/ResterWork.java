package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.blocks.HospitalBedBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.Collection;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class ResterWork {
    public static final String ID = "resting";

    public static final int BLOCK_STATE_NEED_BED = 0;
    public static final int BLOCK_STATE_NEED_REST = 1;
    public static final int BLOCK_STATE_DONE = 2;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_BED, 1
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_REST, 2000
    );

    private static final Collection<ItemStack> RESULTS = ImmutableList.of(
            Items.AIR.getDefaultInstance()
    );
    public static final int PAUSE_FOR_ACTION = 10;

    public static Work asWork(
            String rootId
    ) {
        return productionWork(
                null,
                Blocks.BLACK_BED.asItem().getDefaultInstance(),
                new JobID(rootId, ID),
                WorksBehaviour.noResultDescription(),
                new WorkLocation(
                        (bs, bp) -> WorkLocation.isBlock(HospitalBedBlock.class).test(bs, bp) && bs.apply(bp).getValue(
                                BedBlock.PART).equals(BedPart.HEAD),
                        SpecialQuests.CLINIC
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
                        ImmutableMap.of(), // No stage rules
                        ImmutableList.of(
                                SpecialRules.CLAIM_SPOT,
                                SpecialRules.WORK_IN_EVENING
                        )
                ),
                null,
                new ExpirationRules(
                        () -> Long.MAX_VALUE,
                        () -> Long.MAX_VALUE,
                        jobId -> jobId,
                        Compat.configGet(Config.MAX_TICKS_WITHOUT_DINING_TABLE),
                        WorkSeekerJob::getIDForRoot
                )
        );
    }

    public static JobID getIdForRoot(String rootId) {
        return new JobID(rootId, ID);
    }

    public static boolean isResting(JobID jobName) {
        return ID.equals(jobName.jobId());
    }
}
