package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.DeclarativeJob;
import ca.bradj.questown.jobs.ExpirationRules;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

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
                0,
                INGREDIENTS_REQUIRED_AT_STATES,
                INGREDIENT_QTY_REQUIRED_AT_STATES,
                TOOLS_REQUIRED_AT_STATES,
                WORK_REQUIRED_AT_STATES,
                TIME_REQUIRED_AT_STATES,
                ImmutableMap.of(),
                WorksBehaviour.standardProductionRules().specialGlobalRules(),
                ExpirationRules.never(),
                WorksBehaviour.noOutput(),
                SoundEvents.BOOK_PAGE_TURN.getLocation()
        );
    }

    public static boolean isSeekingWork(JobID s) {
        return WORK_ID.equals(s.jobId());
    }

    public static JobID getIDForRoot(JobID j) {
        if (Config.JOB_BOARD_ENABLED.get()) {
            return newIDForRoot(j.rootId());
        }
        return j;
    }

    public static JobID newIDForRoot(String jobName) {
        return new JobID(jobName, WORK_ID);
    }

    @Override
    protected @NotNull RealtimeWorldInteraction initWorldInteraction(
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Function<MCExtra, Claim> claimSpots,
            int interval,
            @Nullable ResourceLocation sound
            ) {
        return new RealtimeWorldInteraction(
                journal,
                maxState,
                ingredientsRequiredAtStates,
                ingredientsQtyRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                toolsRequiredAtStates,
                resultGenerator,
                claimSpots,
                interval,
                sound
        ) {

            private long getWorkCooldown = 0;

            @Override
            protected Boolean tryExtractProduct(
                    MCExtra extra,
                    BlockPos position
            ) {
                getWorkCooldown--;
                if (getWorkCooldown > 0) {
                    return false;
                }
                if (!extra.town().changeJobForVisitorFromBoard(WorkSeekerJob.this.ownerUUID)) {
                    this.getWorkCooldown = Config.WORK_SEEKER_COOLDOWN.get();
                }
                return true;
            }
        };
    }
}
