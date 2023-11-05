package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Supplier;

public class JobSeekerJob extends DeclarativeJob {

    public static final String ID = "job_seeker";

    public static final int BLOCK_STATE_NO_JOBS = 0;
    public static final int BLOCK_STATE_JOBS_AVAIlABLE = 1;

    public static final int MAX_STATE = BLOCK_STATE_JOBS_AVAIlABLE;

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of(
    );
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NO_JOBS, 0,
            BLOCK_STATE_JOBS_AVAIlABLE, 1
    );

    public JobSeekerJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                ID,
                SpecialQuests.JOB_BOARD,
                MAX_STATE,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                Items.BOWL::getDefaultInstance
        );
    }

    @Override
    protected @NotNull WorldInteraction initWorldInteraction(
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            Supplier<ItemStack> workResult
    ) {
        return new WorldInteraction(
                inventory,
                journal,
                maxState,
                ingredientsRequiredAtStates,
                ingredientQtyRequiredAtStates,
                workRequiredAtStates,
                toolsRequiredAtStates,
                workResult
        ) {

            @Override
            protected boolean tryExtractOre(
                    TownInterface town,
                    BlockPos oldPos
            ) {
                town.changeJobForVisitorFromBoard(ownerUUID);
                return true;
            }
        };
    }

    @Override
    public String getId() {
        return ID;
    }
}
