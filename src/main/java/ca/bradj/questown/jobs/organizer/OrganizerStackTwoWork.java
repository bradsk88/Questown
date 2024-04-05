package ca.bradj.questown.jobs.organizer;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.WorksBehaviour.CurrentlyPossibleResults;
import ca.bradj.questown.jobs.WorksBehaviour.ParentID;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;

public class OrganizerStackTwoWork {
    public static final JobID ID = new JobID("organizer", "stack_two_work");
    public static final ItemStack RESULT = Items.AIR.getDefaultInstance();
    public static final int MAX_STATE = 1;
    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS = ImmutableMap.of();
    private static final ImmutableMap<Integer, Integer> INGREDIENTS_QTY = ImmutableMap.of(
            0, 2
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS = ImmutableMap.of(
            0, Ingredient.of(TagsInit.Items.SHOVELS) // TODO: Change to "trays" (a new item)
    );
    private static final ImmutableMap<Integer, Integer> WORK = ImmutableMap.of(
            0, 10
    );
    private static final ImmutableMap<Integer, Integer> TIME = ImmutableMap.of();

    public static Work asWork() {
        return WorksBehaviour.productionWork(
                ParentID.none().id(),
                Items.CHEST.getDefaultInstance(),
                ID,
                new WorkDescription(
                        CurrentlyPossibleResults.constant(RESULT),
                        RESULT
                ),
                new WorkLocation(
                        (Block block) -> block instanceof ChestBlock,
                        Questown.ResourceLocation("store_room") // TODO: Support for any room with a chest in it
                ),
                new WorkStates(
                        MAX_STATE,
                        Util.constant(INGREDIENTS),
                        Util.constant(INGREDIENTS_QTY),
                        Util.constant(TOOLS),
                        Util.constant(WORK),
                        Util.constant(TIME)
                ),
                new WorkWorldInteractions(
                        5,
                        WorksBehaviour.singleItemOutput(RESULT::copy)
                ),
                new WorkSpecialRules(
                        ImmutableMap.of(
                                ProductionStatus.fromJobBlockStatus(0), ImmutableList.of(
                                        SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT,
                                        SpecialRules.INGREDIENTS_MUST_BE_SAME,
                                        SpecialRules.TAKE_ONLY_LESS_THAN_QUANTITY
                                ),
                                ProductionStatus.fromJobBlockStatus(MAX_STATE), ImmutableList.of(
                                        SpecialRules.FORCE_DROP_LOOT,
                                        SpecialRules.DROP_LOOT_AS_STACK
                                )
                        ),
                        ImmutableList.of(
                                SpecialRules.PRIORITIZE_EXTRACTION
                        )
                ),
                SoundEvents.CHEST_OPEN.getLocation(),
                new ExpirationRules(
                        () -> Long.MAX_VALUE,
                        jobId -> jobId,
                        () -> Long.MAX_VALUE,
                        jobId -> jobId
                )
        );
    }
}
