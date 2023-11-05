package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.town.interfaces.JobHandle;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class TaskFinderJob extends DeclarativeJob {

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
    private final JobChanger jobChanger;

    public interface JobChanger {
        void changeJob(String jobId);
    }

    public TaskFinderJob(
            UUID ownerUUID,
            int inventoryCapacity,
            JobChanger jobChanger
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                "finder",
                SpecialQuests.JOB_BOARD,
                MAX_STATE,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                Items.BOWL.getDefaultInstance()
        );
        this.jobChanger = jobChanger;
    }

    @Override
    protected @NotNull WorldInteraction initWorldInteraction(
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ItemStack workResult
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
                    ServerLevel sl,
                    JobHandle jh,
                    BlockPos oldPos
            ) {
                // TODO: Actually pull jobs from the job board
                String crafter = chooseBestJob(ImmutableList.of(
                        "crafter"
                ));
                if (crafter == null) {
                    return false;
                }
                jobChanger.changeJob(crafter);
                return true;
            }
        };
    }

    protected abstract @Nullable String chooseBestJob(ImmutableList<String> crafter);
}
