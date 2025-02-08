package ca.bradj.questown.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class UtilClean {

    public static <X> List<Pair<Integer, X>> enumerate(List<X> list) {
        ImmutableList.Builder<Pair<Integer, X>> b = ImmutableList.builder();
        for (int i = 0; i < list.size(); i++) {
            b.add(new Pair<>(i, list.get(i)));
        }
        return b.build();
    }

    public static Function<Void, Void> voidVoid(Runnable apply) {
        return ignored -> {
            apply.run();
            return null;
        };
    }

    public static <X, Y> Map<X, ImmutableList<Y>> deepCopy(Map<X, ? extends Collection<Y>> items) {
        ImmutableMap.Builder<X, ImmutableList<Y>> b = ImmutableMap.builder();
        items.forEach((k, v) -> {
            b.put(k, ImmutableList.copyOf(v));
        });
        return b.build();
    }

    public static boolean isCoordInBox(
            double mouseX,
            double mouseY,
            int leftX,
            int topY,
            int width,
            int height
    ) {
        return mouseX >= leftX && mouseY >= topY && mouseX < leftX + width && mouseY < topY + height;
    }

    public static boolean isCoordInBox(
            Coordinate coord,
            Coordinate boxTopLeft,
            Coordinate boxBottomRight
    ) {
        return isCoordInBox(
                coord.x(),
                coord.y(),
                boxTopLeft.x(),
                boxTopLeft.y(),
                boxBottomRight.x() - boxTopLeft.x(),
                boxBottomRight.y() - boxTopLeft.y()
        );
    }

    public static @NotNull String truncateMiddle(String vID) {
        if (vID.length() <= 8) {
            return vID;
        }
        return vID.substring(0, 4) + "..." + vID.substring(vID.length() - 4, vID.length() - 1);
    }

    public static <X> void noOpConsumer(X input) {
    }

    ;

    // If you make any changes to the returned map (when mutable), remember to PUT it back into the map.
    private static <K, I, X> Map<I, X> getOrDefaultMap(
            Map<K, ? extends Map<I, ? extends X>> map,
            K key,
            Map<I, X> fallback,
            boolean mutable
    ) {
        Map<I, ? extends X> x = map.get(key);
        if (x == null) {
            return fallback;
        }
        if (mutable) {
            return new HashMap<>(x);
        }
        ImmutableMap.Builder<I, X> b = ImmutableMap.builder();
        x.forEach(b::put);
        return b.build();
    }


    public static <X, K, Y> void putOrInitialize(
            Map<X, ? extends Map<K, Y>> map,
            X outerKey,
            K innerKey,
            Y value
    ) {
        Map unsafe = map;
        Map cur = getOrDefaultMap(map, outerKey, new HashMap<>(), true);
        cur.put(innerKey, value);
        unsafe.put(outerKey, cur);
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

    public static <K, X, Y extends X> X getOrDefault2(
            Map<K, X> map,
            K key,
            Supplier<Y> fallback
    ) {
        X x = map.get(key);
        if (x == null) {
            return fallback.get();
        }
        return x;
    }

    public static <K, X> ImmutableList<X> getOrDefaultCollection(
            Map<K, ? extends Collection<? extends X>> map,
            K key,
            ImmutableList<X> fallback
    ) {
        return (ImmutableList<X>) getOrDefaultCollection(map, key, fallback, false);
    }

    public static <K, X> Collection<X> getOrDefaultCollection(
            Map<K, ? extends Collection<? extends X>> map,
            K key,
            Collection<X> fallback,
            boolean mutable
    ) {
        Collection<? extends X> x = map.get(key);
        if (x == null) {
            return fallback;
        }
        if (mutable) {
            return new ArrayList<>(x);
        }
        ImmutableList.Builder<X> b = ImmutableList.builder();
        x.forEach(b::add);
        return b.build();
    }
}
