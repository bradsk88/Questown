package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

// TODO: Optimize Performance
//  This processes every villager, and every bed, every tick.
//  This could easily be distributed across ticks or otherwise tuned up.

public class VillagerBedsHandle<POS, ENT, TOWN> {

    private final Map<POS, ENT> claimedBeds = new HashMap<>();
    // TODO: Add keyify function so we're not keying on entire entities
    private final Map<ENT, POS> assignedBeds = new HashMap<>();
    private final Function<TOWN, Collection<POS>> getAllBedHeads;
    private final BiFunction<TOWN, POS, Double> getHealingFactor;
    private final BiFunction<TOWN, ENT, Integer> getDamageTicksLeft;
    private final BiFunction<ENT, POS, Double> getDistance;

    public VillagerBedsHandle(
            Function<TOWN, Collection<POS>> getAllBedHeads,
            BiFunction<TOWN, POS, Double> getHealingFactor,
            BiFunction<TOWN, ENT, Integer> getDamageTicksLeft,
            BiFunction<ENT, POS, Double> getDistance
    ) {
        this.getAllBedHeads = getAllBedHeads;
        this.getHealingFactor = getHealingFactor;
        this.getDamageTicksLeft = getDamageTicksLeft;
        this.getDistance = getDistance;
    }

    public void claim(
            ENT uuid,
            @NotNull TOWN town
    ) {
        Collection<POS> beds = getAllBedHeads.apply(town);
        for (POS bed : beds) {
            if (claimedBeds.containsKey(bed) && uuid.equals(claimedBeds.get(bed))) {
                continue;
            }
            claimedBeds.put(bed, uuid);
            break;
        }
    }

    public void tick(
            TOWN town,
            Collection<ENT> villagers
    ) {
        List<HealingBed<POS>> bedsLeftToAssign = getBedHeadsSortedByHealingDesc(town);
        Iterable<ENT> villagersByMostDamaged = copyVillagersAndSortByDamagedDesc(town, villagers);
        villagersByMostDamaged.forEach(v -> {
            Iterable<POS> bestBedsForVillager = getBestBedsForVillager(v, ImmutableList.copyOf(bedsLeftToAssign));
            // TODO: Is this loop helping anyone?
            for (POS p : bestBedsForVillager) {
                //   TODO: If villager's claimed bed has same score as smallest number, use it instead
                assignedBeds.put(v, p);
                bedsLeftToAssign.removeIf(z -> z.pos.equals(p));
                return;
            }
        });

    }

    private ImmutableList<POS> getBestBedsForVillager(
            ENT entity,
            Collection<HealingBed<POS>> all
    ) {
        List<HealingBed<POS>> bedsLeftToAssign = new ArrayList<>(all);
        bedsLeftToAssign.sort(Comparator.comparingDouble(a -> getDistance.apply(entity, a.pos) / a.factor));
        return ImmutableList.copyOf(bedsLeftToAssign.stream().map(v -> v.pos).toList());
    }

    private ImmutableList<ENT> copyVillagersAndSortByDamagedDesc(
            TOWN town,
            Collection<ENT> villagers
    ) {
        ArrayList<ENT> out = new ArrayList<>(villagers);
        out.sort(Comparator.comparingDouble(v -> getDamageTicksLeft.apply(town, v)));
        return ImmutableList.copyOf(out);
    }

    private List<HealingBed<POS>> getBedHeadsSortedByHealingDesc(
            TOWN town
    ) {
        Collection<POS> all = getAllBedHeads.apply(town);
        Function<POS, HealingBed<POS>> hf = (v) -> new HealingBed<>(v, getHealingFactor.apply(town, v));
        List<HealingBed<POS>> bedsLeftToAssign = new ArrayList<>(all.stream().map(hf).toList());
        bedsLeftToAssign.sort(Comparator.comparingDouble(a -> a.factor));
        return bedsLeftToAssign;
    }

    private record HealingBed<POS>(POS pos, Double factor) {
    }

    public POS getBestBed(ENT villager1) {
        return assignedBeds.get(villager1);
    }
}
