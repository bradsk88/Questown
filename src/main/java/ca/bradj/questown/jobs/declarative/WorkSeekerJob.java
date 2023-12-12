package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.BiFunction;

public class WorkSeekerJob extends DeclarativeJob {

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
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            BLOCK_STATE_NO_JOBS, 0,
            BLOCK_STATE_JOBS_AVAIlABLE, 0
    );
    private static final boolean SHARED_TIMERS_NOT_APPLICABLE = false;

    private static final String WORK_ID = "seeking_work";

    public WorkSeekerJob(
            UUID ownerUUID,
            int inventoryCapacity,
            String rootId
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                new JobID(rootId, WORK_ID),
                SpecialQuests.JOB_BOARD,
                MAX_STATE,
                false,
                0,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                SHARED_TIMERS_NOT_APPLICABLE,
                ImmutableMap.of(),
                (a, b) -> ImmutableSet.of()
        );
    }

    public static boolean isSeekingWork(JobID s) {
        return WORK_ID.equals(s.jobId());
    }

    public static JobID getIDForRoot(JobID j) {
        return newIDForRoot(j.rootId());
    }

    public static JobID newIDForRoot(String jobName) {
        return new JobID(jobName, WORK_ID);
    }

    @Override
    protected @NotNull WorldInteraction initWorldInteraction(
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            BiFunction<ServerLevel, ProductionJournal<MCTownItem, MCHeldItem>, Iterable<ItemStack>> workResult,
            int interval
    ) {
        return new WorldInteraction(
                inventory,
                journal,
                maxState,
                ingredientsRequiredAtStates,
                ingredientQtyRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                toolsRequiredAtStates,
                workResult,
                interval
        ) {

            @Override
            protected boolean tryExtractOre(
                    TownInterface town,
                    WorkStatusHandle<BlockPos, ItemStack> jh,
                    BlockPos oldPos
            ) {
                town.changeJobForVisitorFromBoard(ownerUUID);
                return true;
            }
        };
    }
}
