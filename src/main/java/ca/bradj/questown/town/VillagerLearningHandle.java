package ca.bradj.questown.town;

import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.*;

public class VillagerLearningHandle<JOB_ID> {

    private final Collection<JOB_ID> townKnownJobs = new ArrayList<>();
    private final BiPredicate<JOB_ID, JOB_ID> isParentOf;
    private final Supplier<ImmutableList<JOB_ID>> allShuffled;
    private final int maxAttemptsToLearn;
    private final Predicate<JOB_ID> isParentUnlocked;
    private ImmutableMap<JOB_ID, ImmutableList<JOB_ID>> precalculated;
    private LinkedBlockingQueue<Pair<Collection<JOB_ID>, Consumer<ImmutableList<Pair<JOB_ID, JOB_ID>>>>> requesters = new LinkedBlockingQueue<>();

    public VillagerLearningHandle(
            Supplier<ImmutableSet<JOB_ID>> allJobs,
            Function<ImmutableSet<JOB_ID>, ImmutableList<JOB_ID>> shuffle,
            BiPredicate<JOB_ID, JOB_ID> isParentOf,
            Predicate<JOB_ID> isParentUnlocked,
            int maxAttemptsToLearn
    ) {
        this.allShuffled = () -> shuffle.apply(allJobs.get());
        this.isParentOf = isParentOf;
        this.maxAttemptsToLearn = maxAttemptsToLearn;
        this.isParentUnlocked = isParentUnlocked;
    }

    public void init(Collection<JOB_ID> townKnownJobs) {
        this.townKnownJobs.addAll(townKnownJobs);
    }

    public boolean tick(
            Collection<JOB_ID> villagerJobs
    ) {
        if (precalculated != null && !requesters.isEmpty()) {
            Pair<Collection<JOB_ID>, Consumer<ImmutableList<Pair<JOB_ID, JOB_ID>>>> next = requesters.remove();
            ImmutableList.Builder<Pair<JOB_ID, JOB_ID>> b = ImmutableList.builder();
            for (JOB_ID jobId : next.a()) {
                b.addAll(getNextJobAwareness(jobId).stream().map(v -> new Pair<>(jobId, v)).toList());
            }
            next.b().accept(b.build());
            precalculated = ImmutableMap.of();
            return false;
        }

        ImmutableList<JOB_ID> shuffled = this.allShuffled.get();
        ArrayList<JOB_ID> uniqueRoots = new ArrayList<>(villagerJobs);
        Map<JOB_ID, List<JOB_ID>> precalculated = new HashMap<>();

        boolean changed = false;

        for (JOB_ID uniqueRoot : uniqueRoots) {
            if (this.precalculated != null && this.precalculated.keySet()
                                                                .stream()
                                                                .anyMatch(v -> v.equals(uniqueRoot))) {
                ImmutableList<JOB_ID> already = getNextJobAwareness(uniqueRoot);
                if (!already.isEmpty()) {
                    precalculated.put(uniqueRoot, new ArrayList<>(already));
                    continue;
                }
            }

            int attemptsUsed = 0;
            for (JOB_ID jobId : shuffled) {
                if (attemptsUsed >= maxAttemptsToLearn) {
                    break;
                }
                if (uniqueRoot.equals(jobId)) {
                    continue; // TODO: Test for this condition missing
                }
                if (isParentOf.test(uniqueRoot, jobId)) {
                    attemptsUsed++;
                } else {
                    continue;
                }
                if (townKnownJobs.contains(jobId)) {
                    continue;
                }
                List<JOB_ID> cur = UtilClean.getOrDefault(precalculated, uniqueRoot, new ArrayList<>());
                if (cur.size() >= maxAttemptsToLearn) {
                    break;
                }
                if (cur.contains(jobId)) {
                    continue;
                }
                UtilClean.addAllOrInitializeList(precalculated, uniqueRoot, ImmutableList.of(jobId));
                changed = true;
            }
        }

        ImmutableMap.Builder<JOB_ID, ImmutableList<JOB_ID>> b = ImmutableMap.builder();
        for (Map.Entry<JOB_ID, List<JOB_ID>> e : precalculated.entrySet()) {
            b.put(e.getKey(), ImmutableList.copyOf(e.getValue()));
        }
        this.precalculated = b.build();

        return changed;
    }

    public ImmutableList<JOB_ID> getNextJobAwareness(JOB_ID jobForRoot) {
        return UtilClean.getOrDefaultCollectionByKeyPredicate(
                precalculated,
                k -> k.equals(jobForRoot),
                ImmutableList.of()
        );
    }

    public ImmutableMap<JOB_ID, ImmutableList<JOB_ID>> getNextJobAwarenesses() {
        return precalculated;
    }

    public void requestKnowledge(
            Collection<JOB_ID> jobID,
            Consumer<ImmutableList<Pair<JOB_ID, JOB_ID>>> o
    ) {
        requesters.add(new Pair<>(jobID, o));
    }
}
