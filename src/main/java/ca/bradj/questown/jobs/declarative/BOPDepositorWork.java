package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;

import static ca.bradj.questown.jobs.WorksBehaviour.productionWork;

public class BOPDepositorWork {
    public static final String ID = "bop_depositing";

    public static final int BLOCK_STATE_NEED_FLAG = 0;
    public static final int BLOCK_STATE_NEED_DEPOSIT = 1;
    public static final int BLOCK_STATE_DONE = 2;

    public static final int MAX_STATE = BLOCK_STATE_DONE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_DEPOSIT, Ingredient.of(ItemsInit.BLOCK_OF_PROGRESS.get())
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_DEPOSIT, 1
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FLAG, Ingredient.of(ItemsInit.BLOCK_OF_PROGRESS.get())
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NEED_FLAG, 5
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
    );

    private static final Collection<ItemStack> RESULTS = ImmutableList.of(
            Items.AIR.getDefaultInstance()
    );
    public static final int PAUSE_FOR_ACTION = 1;

    public static Work asWork(
            String rootId
    ) {
        return productionWork(
                null,
                ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance(),
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
                        new ResultGenerator<>() {
                            @Override
                            public Iterable<MCHeldItem> generate(
                                    ServerLevel level,
                                    Collection<MCHeldItem> heldItems
                            ) {
                                return MCHeldItem.fromMCItemStacks(RESULTS);
                            }

                            @Override
                            public boolean isResultAlwaysEmpty() {
                                return true;
                            }
                        }
                ),
                new WorkSpecialRules(
                        ImmutableMap.of(
                                ProductionStatus.fromJobBlockStatus(BLOCK_STATE_NEED_DEPOSIT),
                                ImmutableList.of(SpecialRules.CLEAR_BOP_FOR_VILLAGER)
                        ), // No stage rules
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

    public static boolean matches(JobID jobName) {
        return ID.equals(jobName.jobId());
    }
}
