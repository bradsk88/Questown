package ca.bradj.questown.mc;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.WithReason;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Util {

    public static long getTick(ServerLevel level) {
        return level.getGameTime();
    }

    public static Signals.DayTime getDayTime(Level serverLevel) {
        return new Signals.DayTime(serverLevel.getDayTime());
    }

    public static <X> ImmutableMap<Integer, Supplier<X>> constant(ImmutableMap<Integer, X> constant) {
        ImmutableMap.Builder<Integer, Supplier<X>> b = ImmutableMap.builder();
        constant.forEach((k, v) -> b.put(k, () -> v));
        return b.build();
    }

    public static <X> ImmutableMap<Integer, X> realize(ImmutableMap<Integer, Supplier<X>> theoretical) {
        ImmutableMap.Builder<Integer, X> b = ImmutableMap.builder();
        theoretical.forEach((k, v) -> b.put(k, v.get()));
        return b.build();
    }
    public static <X> ImmutableList<X> realize(ImmutableList<Supplier<X>> theoretical) {
        ImmutableList.Builder<X> b = ImmutableList.builder();
        theoretical.forEach((v) -> b.add(v.get()));
        return b.build();
    }

    public static <X> ImmutableList<X> realize(ImmutableList<Supplier<X>> theoretical) {
        ImmutableList.Builder<X> b = ImmutableList.builder();
        theoretical.forEach((v) -> b.add(v.get()));
        return b.build();
    }

    public static <K, X, Y extends X> X getOrDefault(
            Map<K, X> map,
            K key,
            Y fallback
    ) {
        X x = map.get(key);
        if (x == null) {
            return fallback;
        }
        return x;
    }

    public static <K, X> Collection<X> getOrDefaultCollection(
            ImmutableMap<K, ? extends Collection<? extends X>> map,
            K key,
            Collection<X> fallback
    ) {
        Collection<? extends X> x = map.get(key);
        if (x == null) {
            return fallback;
        }
        ImmutableList.Builder<X> b = ImmutableList.builder();
        x.forEach(b::add);
        return b.build();
    }

    public static <K, V> Map<K, ArrayList<V>> makeMutable(Map<K, ? extends Collection<V>> inMap) {
        HashMap<K, ArrayList<V>> kArrayListHashMap = new HashMap<>();
        inMap.forEach((k, v) -> kArrayListHashMap.put(k, new ArrayList<>(v)));
        return kArrayListHashMap;
    }

    public static <X, Y> Y applyOrDefault(
            Function<X, Y> fn,
            X param,
            Y defaultt
    ) {
        Y v = fn.apply(param);
        if (v == null) {
            return defaultt;
        }
        return v;
    }

    public static <K, V> ImmutableMap<K, ImmutableList<V>> immutify(
            Map<K, ? extends Collection<V>> roomsNeedingIngredientsByState
    ) {
        ImmutableMap.Builder<K, ImmutableList<V>> b = ImmutableMap.builder();
        roomsNeedingIngredientsByState.forEach((k, v) -> b.put(k, ImmutableList.copyOf(v)));
        return b.build();
    }

    public static <K, V> Map<K, V> only(
            Map<K, V> map,
            K s
    ) {
        if (map.containsKey(s)) {
            return ImmutableMap.of(
                    s, map.get(s)
            );
        }
        return ImmutableMap.of();
    }

    public static <ITEM> Predicate<ITEM> funcToPredNullable(Function<ITEM, Boolean> ingredient) {
        if (ingredient == null) {
            return null;
        }
        return ingredient::apply;
    }

    public static <I extends Item<I>> WithReason<Boolean> anyMatch(
            Stream<I> stream,
            NoisyPredicate<I> check
    ) {
        List<WithReason<Boolean>> tests = stream.map(check::test).toList();
        for (WithReason<Boolean> test : tests) {
            if (test.value) {
                return test;
            }
        }
        String joined = String.join(", ", tests.stream().map(v -> v.reason).toList());
        return new WithReason<>(false, "All failed: [%s]", joined);
    }

    /**
     * @deprecated Probably shouldn't suppress noise
     */
    public static <ITEM> Predicate<ITEM> toQuiet(NoisyPredicate<ITEM> check) {
        return i -> check.test(i).value;
    }

    public static <TOWN_ITEM extends Item<TOWN_ITEM>> Collection<? extends NoisyBiPredicate<AmountHeld, TOWN_ITEM>> sameNoise(
            Collection<BiPredicate<AmountHeld, TOWN_ITEM>> all,
            String messageIfPass,
            String messageIfFail
    ) {
        ImmutableList.Builder<NoisyBiPredicate<AmountHeld, TOWN_ITEM>> b = ImmutableList.builder();
        all.forEach(bp -> {
            b.add((amountHeld, townItem) -> WithReason.bool(
                    bp.test(amountHeld, townItem),
                    messageIfPass,
                    messageIfFail,
                    townItem.getShortName()
            ));
        });
        return b.build();
    }
}
