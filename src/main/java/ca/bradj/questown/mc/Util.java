package ca.bradj.questown.mc;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.*;
import java.util.function.*;
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

    /**
     * Handles potentially infinite iterables, applying the BASE_MAX_LOOP
     * to make them finite by truncating.
     */
    public static <X> void iterate(Iterable<X> source, Consumer<X> sink) {
        Iterator<X> iter = source.iterator();
        for (int i = 0; i < Config.BASE_MAX_LOOP.get(); i++) {
            if (iter.hasNext()) {
                sink.accept(iter.next());
            } else {
                return;
            }
        }
    }

    public static <X> X getOrDefault(
            JsonObject object,
            String key,
            Function<JsonElement, X> puller,
            X fallback
    ) {
        if (!object.has(key)) {
            return fallback;
        }
        if (object.get(key).isJsonNull()) {
            return fallback;
        }
        return puller.apply(object.get(key));
    }

    public static void setProcessingStateOnProperty(
            ServerLevel level,
            IntegerProperty prop,
            State state,
            BlockPos pos
    ) {
        Integer maxPropVal = prop.getPossibleValues().stream().max(Integer::compare).orElse(0);
        int limited = Math.min(state.processingState(), maxPropVal);
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(prop, limited));
    }

    public static <X, Y> void addOrInitialize(
            Map<X, Collection<Y>> map,
            X key,
            Y value
    ) {
        Collection<Y> cur = Util.getOrDefault(map, key, new ArrayList<>());
        cur.add(value);
        map.put(key, cur);
    }
}
