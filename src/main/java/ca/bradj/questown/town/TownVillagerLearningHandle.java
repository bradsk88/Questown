package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

public class TownVillagerLearningHandle {

    final Map<UUID, HashSet<JobID>> unlockedJobs = new HashMap<>();
    final Map<UUID, Map<JobID, Set<JobID>>> jobsKnownToExist = new HashMap<>();
    private final UnsafeTown town = new UnsafeTown(TownVillagerLearningHandle.class);

    private final VillagerLearningHandle<JobID> delegate = new VillagerLearningHandle<>(
            ServerJobsRegistry::getAllJobs,
            v -> Compat.shuffle(v, town.getServerLevelUnsafe()),
            (p, c) -> {
                if (ServerJobsRegistry.isParentOf(p, c)) {
                    return true;
                }
                return false;
            },
            j -> true,
            Config.JOB_TREE_GROWTH.get()
    );
    private long lastTicked;
    private long lastComputed;


    public TownVillagerLearningHandle() {
    }

    public void associate(TownFlagBlockEntity t) {
        town.initialize(t);
    }

    public void tick(
            ImmutableList<LivingEntity> livingEntities,
            long currentTick
    ) {
        this.lastTicked = currentTick;
        if (currentTick % 20 != 0) { // TODO: Config value
            return;
        }
        this.lastComputed = currentTick;
        ImmutableSet<JobID> representativeJobs = ServerJobsRegistry.getAllJobs();
        boolean changed = delegate.tick(representativeJobs);
        if (!changed)
            return;
        for (Map.Entry<JobID, ImmutableList<JobID>> pc : delegate.getNextJobAwarenesses().entrySet()) {
            QT.FLAG_LOGGER.debug("Computed next awareness for {}: {}", pc.getKey().toNiceString(), Jobs.getNiceString(pc.getValue()));
        }
    }

    public void initialize(
            Map<UUID, ? extends ImmutableCollection<JobID>> unlockedJobs,
            Map<UUID, ? extends Map<JobID, ? extends ImmutableCollection<JobID>>> jobsKnownToExist
    ) {
        for (Map.Entry<UUID, ? extends ImmutableCollection<JobID>> uuidEntry : unlockedJobs.entrySet()) {
            UtilClean.addAllOrInitialize(this.unlockedJobs, uuidEntry.getKey(), new HashSet<>(uuidEntry.getValue()));
        }
        for (Map.Entry<UUID, ? extends Map<JobID, ? extends ImmutableCollection<JobID>>> ujs : jobsKnownToExist.entrySet()) {
            HashMap<JobID, Set<JobID>> m = new HashMap<>();
            for (Map.Entry<JobID, ? extends ImmutableCollection<JobID>> uj : ujs.getValue().entrySet()) {
                m.put(uj.getKey(), new HashSet<>(uj.getValue()));
            }
            this.jobsKnownToExist.put(ujs.getKey(), m);
        }
    }

    public ImmutableMap<UUID, ImmutableSet<JobID>> getUnlockedJobs() {
        return UtilClean.copyMapOfSets(unlockedJobs);
    }

    public ImmutableSet<JobID> getChildJobsKnownToExist(JobID parent) {
        ImmutableSet.Builder<JobID> b = ImmutableSet.builder();
        for (Map.Entry<UUID, Map<JobID, Set<JobID>>> kjs : jobsKnownToExist.entrySet()) {
            Map<JobID, Set<JobID>> villagerKnown = kjs.getValue();
            ImmutableList<JobID> known = UtilClean.getOrDefaultCollectionByKeyPredicate(
                    villagerKnown, parent::sameRoot, ImmutableList.of()
            );
            b.addAll(known);
        }
        return b.build();
    }

    public ImmutableSet<JobID> getPrecomputedNextKnowledge(JobID parent) {
        return ImmutableSet.copyOf(delegate.getNextJobAwareness(parent));
    }

    public ImmutableMap<UUID, ImmutableMap<JobID, ImmutableSet<JobID>>> getChildJobsKnownToExist() {
        ImmutableMap.Builder<UUID, ImmutableMap<JobID, ImmutableSet<JobID>>> b3 = ImmutableMap.builder();
        for (Map.Entry<UUID, Map<JobID, Set<JobID>>> kjs : jobsKnownToExist.entrySet()) {
            ImmutableSet.Builder<JobID> b = ImmutableSet.builder();
            Map<JobID, Set<JobID>> villagerKnown = kjs.getValue();

            ImmutableMap.Builder<JobID, ImmutableSet<JobID>> b2 = ImmutableMap.builder();
            for (Map.Entry<JobID, Set<JobID>> eee : villagerKnown.entrySet()) {
                b2.put(eee.getKey(), ImmutableSet.copyOf(eee.getValue()));
            }
            b3.put(kjs.getKey(), b2.build());
        }
        return b3.build();
    }

    public void unlockJob(
            UUID villagerUUID,
            JobID id
    ) {
        UtilClean.addOrInitialize(unlockedJobs, villagerUUID, id);
    }

    public void registerAsKnown(
            UUID contributingVillager,
            JobID parentJob,
            Collection<JobID> jobsKnownToExist
    ) {
        for (JobID jobID : jobsKnownToExist) {
            registerAsKnown(contributingVillager, parentJob, jobID);
        }
    }

    public void registerAsKnown(
            UUID contributingVillager,
            JobID parentJob,
            JobID jobsKnownToExist
    ) {
        Map<JobID, Set<JobID>> villagerKnown = new HashMap<>(UtilClean.getOrDefault(
                this.jobsKnownToExist,
                contributingVillager,
                ImmutableMap.of()
        ));
        UtilClean.addOrInitialize(villagerKnown, parentJob, jobsKnownToExist);
        this.jobsKnownToExist.put(contributingVillager, villagerKnown);
        QT.FLAG_LOGGER.debug(
                "Registering as known: {}->{} via {}",
                parentJob.toNiceString(),
                jobsKnownToExist.toNiceString(),
                contributingVillager
        );
    }

    public void requestKnowledge(
            UUID uuid,
            ImmutableList<JobID> defaultWork
    ) {
        ImmutableList.Builder<JobID> b = ImmutableList.builder();
        for (JobID jobID : defaultWork) {
            Map<JobID, Set<JobID>> jk2e = UtilClean.getOrDefault(jobsKnownToExist, uuid, ImmutableMap.of());
            if (jk2e.containsKey(jobID)) {
                continue;
            }
            b.add(jobID);
        }
        delegate.requestKnowledge(
                b.build(), knowledge -> {
                    for (Pair<JobID, JobID> jobIDJobIDPair : knowledge) {
                        registerAsKnown(uuid, jobIDJobIDPair.a(), jobIDJobIDPair.b());
                    }
                }
        );
    }
}
