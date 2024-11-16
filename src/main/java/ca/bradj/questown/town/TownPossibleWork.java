package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import joptsimple.internal.Strings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TownPossibleWork {

    private final UnsafeTown town = new UnsafeTown();

    private final Map<String, List<JobID>> preselectedJobs = new HashMap<>();
    private boolean shouldRecompute = true;
    private int buffer;

    public TownPossibleWork() {
    }

    public void initialize(TownFlagBlockEntity serverLevel) {
        town.initialize(serverLevel);
    }

    public void tick() {
        if (!shouldRecompute) {
            return;
        }
        buffer++;
        int freq = Config.WORK_PRECOMPUTE_FREQUENCY.get().intValue();
        buffer = buffer % freq;
        if (buffer != 0) {
            return;
        }
        TownFlagBlockEntity t = town.getUnsafe();
        Stream<String> roots = t.getVillagerHandle().getJobs().stream().map(JobID::rootId);
        ImmutableSet<Map.Entry<JobID, Supplier<Work>>> rjs = Works.regularJobs();
        roots.forEach(root -> {
            List<JobID> jobs = getJobsSortedByPossibility(root, rjs, t);
            preselectedJobs.put(root, jobs);
            QT.FLAG_LOGGER.debug(
                    "Prepared for {}: [{}]",
                    root,
                    Strings.join(jobs.stream().map(JobID::toNiceString).toList(), ",")
            );
        });
        shouldRecompute = false;
    }

    private static List<JobID> getJobsSortedByPossibility(
            String root,
            ImmutableSet<Map.Entry<JobID, Supplier<Work>>> allJobs,
            TownFlagBlockEntity t
    ) {
        Stream<Map.Entry<JobID, Supplier<Work>>> e = allJobs.stream().filter(v -> root.equals(v.getKey().rootId()));
        ImmutableMap.Builder<JobID, Double> b = ImmutableMap.builder();
        e.forEach(w -> b.put(w.getKey(), getWorkPercentPossible(t, w)));
        return b.build().entrySet().stream()
                .filter(v -> v.getValue() > Config.MIN_JOB_ACCEPTANCE.get())
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey).toList();
    }

    private static double getWorkPercentPossible(
            TownFlagBlockEntity t,
            Map.Entry<JobID, Supplier<Work>> w
    ) {
        Work work = w.getValue().get();
        Job<?, ?, ?> j = work.jobFunc()
                             .apply(UUID.randomUUID());
        if (!(j instanceof DeclarativeJob dj)) {
            return 0.0;
        }

        if (!JobsRegistry.canFit(null, j.getId(), Util.getDayTime(t.getServerLevel()))) {
            QT.FLAG_LOGGER.trace(
                    "Villager will not do {} because there is not enough time left in the day",
                    j.getId().toNiceString()
            );
            return 0.0;
        }

        int hps = getHighestPossibleState(t, dj);
        double v = (double) hps / dj.getMaxState();
        return v;
    }

    private static int getHighestPossibleState(
            TownFlagBlockEntity t,
            DeclarativeJob dj
    ) {
        if (dj.specialGlobalRules.contains(SpecialRules.ALWAYS_CONSIDER)) {
            return dj.getMaxState();
        }
        boolean townHasJobSite = false;
        for (int i = 0; i < dj.getMaxState(); i++) {
            int ii = i;
            WorkStatusHandle<BlockPos, MCHeldItem> ws = t.getWorkStatusHandle(null); // TODO: Nest
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(ii);
            if (!UtilClean.getOrDefaultCollection(dj.specialRules, s, ImmutableList.of())
                          .contains(SpecialRules.CLAIM_SPOT)) {
                Collection<RoomRecipeMatch<MCRoom>> rooms = t.getRoomHandle()
                                                             .getRoomsMatching(dj.location().baseRoom());
                Collection<RoomRecipeMatch<MCRoom>> roomsWS = Jobs.roomsWithState(
                        rooms,
                        (bp) -> dj.location().isJobBlock().test(t.getServerLevel()::getBlockState, bp),
                        (bp) -> Integer.valueOf(ii).equals(JobBlock.getState(ws::getJobBlockState, bp))
                );
                if (!roomsWS.isEmpty()) {
                    townHasJobSite = true;
                    break;
                }
            }
        }
        if (!townHasJobSite) {
            return 0;
        }
        for (int i = 0; i < dj.getMaxState(); i++) {
            int ii = i;
            boolean townHasIngredient = true;
            final IPredicateCollection<MCHeldItem> ing = dj.getChecks().getIngredientsForStep(ii);
            if (ing != null) {
                townHasIngredient = false;
                @Nullable ContainerTarget<MCContainer, MCTownItem> ingCont = t.findMatchingContainer(
                        v -> ing.test(MCHeldItem.fromTown(v))
                );
                if (ingCont != null) {
                    townHasIngredient = true;
                }
            }

            boolean townHasTool = true;
            final IPredicateCollection<MCTownItem> tool = dj.getChecks().getToolsForStep(ii);
            if (tool != null) {
                townHasTool = false;
                @Nullable ContainerTarget<MCContainer, MCTownItem> toolCont = t.findMatchingContainer(tool::test);
                if (toolCont != null) {
                    townHasTool = true;
                }
            }

            if (!townHasIngredient || !townHasTool) {
                return Math.max(0, i - 1);
            }
        }
        return dj.getMaxState();
    }

    public ImmutableList<JobID> getFor(JobID jobId) {
        return UtilClean.getOrDefaultCollection(preselectedJobs, jobId.rootId(), ImmutableList.of());
    }

    public void invalidate() {
        shouldRecompute = true;
    }
}
