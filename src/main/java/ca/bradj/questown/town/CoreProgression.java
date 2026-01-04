package ca.bradj.questown.town;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.ItemEconomicsData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;

public class CoreProgression<JOB, ROOM> {

    private final ImmutableList<ItemEconomicsData> needs;
    private final Predicate<JOB> isGatherer;
    private final Function<Collection<JOB>, JOB> chooseRandom;
    private final Predicate<JOB> generatesVillagerFood;
    private final EconData<JOB> econData;

    public record TotalTimesNeeded(int value) {
    }

    public interface EconData<JOB> {
        Collection<ItemEconomicsData> getForResults(JOB job, ImmutableList<ItemEconomicsData> allData);
    }

    public CoreProgression(
            Collection<ItemEconomicsData> needs,
            Predicate<JOB> isGatherer,
            Function<Collection<JOB>, JOB> chooseRandom,
            Predicate<JOB> generatesVillagerFood,
            EconData<JOB> econData
    ) {
        this.needs = ImmutableList.copyOf(needs);
        this.isGatherer = isGatherer;
        this.chooseRandom = chooseRandom;
        this.generatesVillagerFood = generatesVillagerFood;
        this.econData = econData;
    }

    public @Nullable JOB getFirstJobChange(
            Collection<JOB> allRootJobs,
            Function<JOB, ROOM> getRoom,
            Predicate<ROOM> isQuestExisting
    ) {
        // TODO: Check if allRoots empty

        // Grab all the jobs which are not "gatherer"
        List<JOB> allRoots = ImmutableList.copyOf(allRootJobs);
        Collection<JOB> firstLayerJobs = allRoots
                .stream()
                .filter(Predicate.not(isGatherer))
                .toList();

        if (firstLayerJobs.isEmpty()) {
            return null;
        }

        // Prefer jobs with simple rooms that don't yet have a quest
        ImmutableMap<JOB, ROOM> roomsNeeded = UtilClean.toMap(firstLayerJobs, getRoom);

        Predicate<ROOM> isQuestAddable = Predicate.not(isQuestExisting);
        ImmutableMap<JOB, ROOM> roomsWithoutQuest = UtilClean.filterByValue(roomsNeeded, isQuestAddable);

        if (roomsWithoutQuest.isEmpty()) {
            return chooseRandom.apply(firstLayerJobs);
        }

        // Prefer food-producing jobs (during early progression)
        // TODO: At what point should we stop prioritizing food?
        List<JOB> foodProducing = roomsWithoutQuest.keySet().stream().filter(generatesVillagerFood).toList();
        if (!foodProducing.isEmpty()) {
            return chooseRandom.apply(foodProducing);
        }

        // Prefer jobs that best meet the needs of the town
        Map<JOB, Collection<ItemEconomicsData>> most = UtilClean.toMap(
                roomsWithoutQuest.keySet(),
                z -> econData.getForResults(z, needs)
        );
        ImmutableMap.Builder<JOB, TotalTimesNeeded> b = ImmutableMap.builder();
        for (Map.Entry<JOB, Collection<ItemEconomicsData>> re : most.entrySet()) {
            int times = re.getValue().stream()
                             .mapToInt(ItemEconomicsData::timesNeeded)
                             .sum();
            b.put(re.getKey(), new TotalTimesNeeded(times));
        }

        ImmutableMap<JOB, TotalTimesNeeded> times = b.build();
        OptionalInt maxTimesNeeded = times.values().stream().mapToInt(TotalTimesNeeded::value).max();
        if (maxTimesNeeded.isPresent()) {
            List<JOB> mostNeeded = times.entrySet()
                                       .stream()
                                       .filter(v -> v.getValue().value == maxTimesNeeded.getAsInt())
                                       .map(Map.Entry::getKey)
                                       .toList();
            return chooseRandom.apply(mostNeeded);
        }

        return chooseRandom.apply(roomsWithoutQuest.keySet());
    }

    public CoreProgression<JOB, ROOM> withFoodCheck(Predicate<JOB> o) {
        return new CoreProgression<>(needs, isGatherer, chooseRandom, o, econData);
    }

    public CoreProgression<JOB, ROOM> withEconomics(EconData<JOB> o) {
        return new CoreProgression<>(needs, isGatherer, chooseRandom, generatesVillagerFood, o);
    }
}
