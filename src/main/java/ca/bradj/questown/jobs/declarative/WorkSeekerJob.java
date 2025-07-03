package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class WorkSeekerJob extends DeclarativeJob {

    public static final ImmutableMap<Integer, Ingredient> INGREDIENTS_REQUIRED_AT_STATES = ImmutableMap.of();
    public static final ImmutableMap<Integer, Integer> INGREDIENT_QTY_REQUIRED_AT_STATES = ImmutableMap.of();
    public static final ImmutableMap<Integer, Ingredient> TOOLS_REQUIRED_AT_STATES = ImmutableMap.of();
    public static final ImmutableMap<Integer, Integer> WORK_REQUIRED_AT_STATES = ImmutableMap.of(
            ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.BLOCK_STATE_NO_JOBS,
            0,
            ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.BLOCK_STATE_JOBS_AVAIlABLE,
            1
    );
    public static final ImmutableMap<Integer, Integer> TIME_REQUIRED_AT_STATES = ImmutableMap.of(
            ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.BLOCK_STATE_NO_JOBS,
            0,
            ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.BLOCK_STATE_JOBS_AVAIlABLE,
            0
    );
    private static final String WORK_ID = ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.WORK_ID;

    private boolean registeredUnmet = false;

    public WorkSeekerJob(
            UUID ownerUUID,
            int inventoryCapacity,
            String rootId
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                new JobID(rootId, WORK_ID),
                new WorkLocation(
                        (ctx) -> true,
                        (i, p) -> true,
                        SpecialQuests.JOB_BOARD
                ),
                ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.MAX_STATE,
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
                SoundInfo.guaranteed(SoundEvents.BOOK_PAGE_TURN.getLocation(), 100)
        );
    }

    @Override
    protected @NotNull RealtimeWorldInteraction initWorldInteraction(
            int maxState,
            DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Map<ProductionStatus, Collection<String>> specialRules,
            Function<MCExtra, Claim> claimSpots,
            BiFunction<MCExtra, NeedsRegistrations.Need, String> needs,
            Supplier<String> location,
            int interval,
            @Nullable SoundInfo sound
    ) {
        return new RealtimeWorldInteraction(
                t -> t.town().getTownFlagBasePos(),
                journal, maxState, checks, specialRules, resultGenerator, claimSpots, (x, need) -> {
            Work w = ServerJobsRegistry.getRandomWork(
                    x.town().getServerLevel(),
                    getId().rootId(),
                    x.town().getVillagerHandle()::isUnlocked
            );
            Job<?, ?, ?> job = w.jobFunc.apply(ownerUUID);
            if (job instanceof DeclarativeJob dj) {
                String ingredient = dj.getIngredient(0);
                if (ingredient == null) {
                    return dj.getTool(0);
                }
                return ingredient;
            }
            return null;
        }, location, interval, sound
        ) {

            private long getWorkCooldown = 0;

            @Override
            protected Boolean tryExtractProduct(
                    @NotNull MCExtra extra,
                    BlockPos position
            ) {
                getWorkCooldown--;
                if (getWorkCooldown > 0) {
                    return false;
                }
                if (!extra.town().changeJobForVisitorFromBoard(WorkSeekerJob.this.ownerUUID, getId())) {
                    this.getWorkCooldown = Config.WORK_SEEKER_COOLDOWN.get();
                }
                return true;
            }
        };
    }

    @Override
    protected @NotNull Supplier<ProductionStatus> getStateComputer(
            TownInterface town,
            IProductionStatusFactory<ProductionStatus> statusFactory,
            JobTownProvider<MCRoom> jtp,
            EntityLocStateProvider<MCRoom> elp
    ) {
        return () -> {
            Supplier<ProductionStatus> sc = super.getStateComputer(town, statusFactory, jtp, elp);
            return switch (super.getSignal()) {
                case MORNING, NOON, UNDEFINED -> {
                    if (town.getVillagerHandle().hasBlockOfProgress(ownerUUID)) {
                        town.changeJobForVisitorFromBoard(ownerUUID, getId());
                    }
                    if (town.getPossibleWork().getFor(getId()).isEmpty()) {
                        if (!registeredUnmet && !statusFactory.noWorkPossible().equals(journal.getStatus())) {
                            journal.changeStatus(statusFactory.noWorkPossible());
                            town.getPossibleWork().invalidate();
                            registeredUnmet = true;
                        }
                        yield statusFactory.noWorkPossible();
                    }
                    yield sc.get();
                }
                case EVENING, NIGHT -> sc.get();
            };
        };
    }
}
