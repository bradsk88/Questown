package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static ca.bradj.questown.mc.Util.info;

public class TownPossibleWork {

    private final UnsafeTown town = new UnsafeTown(getClass());

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
        recomputeNow(t.getServerLevel(), PossibilitySources.from(t));
    }

    public void recomputeNow(ServerLevel sl, PossibilitySource src) {
        src.roots().forEach(root -> {
            List<JobPossibility> unfilteredJobs = getJobsSortedByPossibility(sl, root, src);
            bigLog(src, root, unfilteredJobs);

            List<JobPossibility> jobs = unfilteredJobs.stream().filter(
                    v -> v.score.value > Config.PREFERRED_JOB_ACCEPTANCE.get()
            ).toList();
            if (jobs.isEmpty()) {
                jobs = unfilteredJobs.stream().filter(
                        v -> v.score.value > Config.MIN_JOB_ACCEPTANCE.get()
                ).toList();
            }
            ImmutableList<JobID> preselected = jobs.stream().map(v -> v.jobID).collect(ImmutableList.toImmutableList());
            preselectedJobs.put(root, preselected);
            if (jobs.isEmpty()) {
                registerUnmetNeeds(root);
            }
            src.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.JOB_POSSIBILITIES_COMPUTE).log(
                    "Prepared for {}: [{}]",
                    root,
                    Strings.join(preselected.stream().map(JobID::jobId).toList(), ",")
            );
        });
        shouldRecompute = false;
    }

    private static void bigLog(
            PossibilitySource t,
            String root,
            List<JobPossibility> unfilteredJobs
    ) {
        t.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.JOB_POSSIBILITIES_COMPUTE).log(
                "Possible jobs for root {}: [\n{}\n]", root,
                Strings.join(
                        unfilteredJobs.stream()
                                      .map(JobPossibility::toString)
                                      .toList(),
                        "\n"
                )
        );
    }

    private void registerUnmetNeeds(String root) {
        try {
            ServerLevel sl = town.getServerLevelUnsafe();
            long tick = Util.getTick(sl);
            VillagerHolder vh = town.getUnsafe().getVillagerHandle();
            Work work = ServerJobsRegistry.getRandomWork(sl, root, vh::isUnlocked);
            NoMCEconomics econ = town.getUnsafe().getEconomicsHandle();
            vh.entities().stream().map(v -> (VisitorMobEntity) v).filter(v -> root.equals(v.getJobId().rootId()))
              .forEach(villager -> this.registerUnmetNed(work, econ, tick, villager.getUUID()));
        } catch (Exception e) {
            QT.FLAG_LOGGER.error("Failed to register unmet needs for root: {}", root, e);
        }
    }

    private void registerUnmetNed(
            Work work,
            NoMCEconomics econ,
            long tick,
            UUID uuid
    ) {
        DeclarativeJob x = (DeclarativeJob) work.jobFunc.apply(uuid);
        Ingredient xx = x.initialIngredients.get(0);
        if (xx == null) {
            xx = x.initialTools.get(0);
            if (xx == null) {
                return;
            }
        }
        econ.registerUnmetNeed(tick, uuid, Ingredients.toString(xx));
    }

    private record JobPossibility(
            JobID jobID,
            WithReason<Double> score
    ) {
        @Override
        public String toString() {
            return "JobPossibility{" +
                    "jobID=" + jobID +
                    ", score=" + score.map(v -> NumberFormat.getNumberInstance()
                                                            .format(Math.round(v * 1000.0) / 1000.0)) +
                    '}';
        }
    }

    private static ImmutableList<JobPossibility> getJobsSortedByPossibility(
            ServerLevel sl,
            String root,
            PossibilitySource src
    ) {
        // FIXME: Only include jobs that are known by the villagers
        List<Map.Entry<JobID, Supplier<Work>>> e = src.allJobs().stream().filter(v -> root.equals(v.getKey().rootId()))
                                                          .toList();
        ImmutableList.Builder<JobPossibility> b = ImmutableList.builder();
        for (Map.Entry<JobID, Supplier<Work>> w : e) {
            b.add(new JobPossibility(w.getKey(), getWorkPercentPossible(sl, w, src)));
        }
        return b.build();
    }

    private static WithReason<Double> getWorkPercentPossible(
            ServerLevel sl,
            Map.Entry<JobID, Supplier<Work>> w,
            PossibilitySource src
    ) {
        Work work = w.getValue().get();
        Job<?, ?, ?> j = work.jobFunc.apply(UUID.randomUUID());
        if (!(j instanceof DeclarativeJob dj)) {
            return WithReason.always(0.0, "Unsupported job class " + j.getClass().getName());
        }

        if (!ServerJobsRegistry.canFit(null, j.getId(), Util.getDayTime(sl))) {
            return WithReason.always(0.0, "Not enough time left in the day for ", j.getId().toNiceString());
        }

        WithReason<Integer> hps = getHighestPossibleState(
                sl, dj, src,
                (bp) -> dj.location().isJobBlock().test(
                        new JobBlockTestContext(sl, info(sl), bp, ImmutableList::of, src::uniqueItems, false, false)
                )
        );
        double v = (double) hps.value / dj.getMaxState();
        float shuffler = Compat.nextRandomInt(sl, 100) / 10000f;
        return WithReason.always(
                v + shuffler,
                "Highest possible job state: " + hps + " (out of " + dj.getMaxState() + ", with randomizer " + shuffler + ")"
        );
    }

    private static WithReason<Integer> getHighestPossibleState(
            ServerLevel sl,
            DeclarativeJob dj,
            PossibilitySource src,
            Predicate<BlockPos> isJobBlock
    ) {
        if (dj.specialGlobalRules.contains(SpecialRules.ALWAYS_CONSIDER)) {
            return WithReason.always(dj.getMaxState(), "Special rule ALWAYS_CONSIDER present");
        }
        boolean townHasJobSite = false;
        for (int i = 0; i < dj.getMaxState(); i++) {
            int ii = i;
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(ii);
            if (!UtilClean.getOrDefaultCollection(dj.specialRules, s, ImmutableList.of())
                          .contains(SpecialRules.CLAIM_SPOT)) {
                Collection<RoomRecipeMatch<MCRoom>> roomsWS = Jobs.roomsWithState(
                        src.getRoomsForJob(dj),
                        isJobBlock,
                        (bp) -> Integer.valueOf(ii).equals(JobBlock.getState(src::getWorkState, bp))
                );
                if (!roomsWS.isEmpty()) {
                    townHasJobSite = true;
                    break;
                }
            }
        }
        if (!townHasJobSite) {
            return WithReason.always(0, "Town lacks required job site (or descendant) of: " + dj.location().baseRoom());
        }
        for (int i = 0; i < dj.getMaxState(); i++) {
            int ii = i;
            boolean townHasIngredient = true;
            final IPredicateCollection<MCHeldItem> ing = dj.getChecks().getIngredientsForStep(ii);
            if (ing != null) {
                townHasIngredient = false;
                List<ContainerTarget<MCContainer, MCTownItem>> foundContainer = Containers.get2(
                        sl,
                        src::getAllRooms,
                        isJobBlock,
                        js -> dj.location().baseRoom().equals(js),
                        false
                );
                for (ContainerTarget<MCContainer, MCTownItem> tg : foundContainer) {
                    if (tg.hasItem(zzz -> ing.test(MCHeldItem.fromTown(zzz)))) {
                        townHasIngredient = true;
                        break;
                    }
                }
            }

            boolean townHasTool = true;
            final IPredicateCollection<MCTownItem> tool = dj.getChecks().getToolsForStep(ii);
            if (tool != null) {
                townHasTool = src.townHasTool(tool);
            }

            if (!townHasIngredient || !townHasTool) {
                int max = Math.max(0, i - 1);
                return WithReason.always(
                        max,
                        "Lacking " +
                                (townHasIngredient ? "" : "ingredients ") +
                                (townHasTool ? "" : "tools ") +
                                "for job state " + ii + "/" + dj.getMaxState()
                );
            }
        }
        return WithReason.always(
                dj.getMaxState(),
                "All ingredients and tools available for all job states"
        );
    }

    private static boolean isJobBlock(
            TownFlagBlockEntity t,
            DeclarativeJob dj,
            BlockPos bp,
            ServerLevel sl
    ) {
        return dj.location().isJobBlock()
                 .test(new JobBlockTestContext(sl, info(sl), bp, ImmutableList::of, unique(t), false, false));
    }

    private static Supplier<? extends Collection<Item>> unique(TownFlagBlockEntity t) {
        return () -> TownContainers.getUniqueItems(t);
    }

    public ImmutableList<JobID> getFor(JobID jobId) {
        return UtilClean.getOrDefaultCollection(preselectedJobs, jobId.rootId(), ImmutableList.of());
    }

    public void invalidate() {
        shouldRecompute = true;
    }
}
